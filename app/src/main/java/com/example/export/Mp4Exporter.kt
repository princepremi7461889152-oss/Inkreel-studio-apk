package com.example.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.audio.ProceduralAudioEngine
import com.example.canvas.StickmanRenderer
import com.example.model.ReelProject
import com.example.model.SpeakerSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

data class ExportResult(
    val shareUri: Uri,
    val displayPath: String,
    val fileSizeBytes: Long
)

object Mp4Exporter {

    private const val TAG = "Mp4Exporter"
    private const val FPS = 30
    private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC

    suspend fun savePngThumbnail(
        context: Context,
        project: ReelProject,
        playheadMs: Long,
        lipAmplitude: Float
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            val w = project.exportResolution.width
            val h = project.exportResolution.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            StickmanRenderer.renderFrame(
                canvas = canvas,
                width = w.toFloat(),
                height = h.toFloat(),
                project = project,
                playheadMs = playheadMs,
                lipAmplitude = lipAmplitude,
                showCaptions = true,
                showSpeechBubbles = true
            )

            val fileName = "InkReel_${System.currentTimeMillis()}.png"
            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cacheFile = File(cacheDir, fileName)
            FileOutputStream(cacheFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bmp.recycle()

            val displayPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/InkReel Studio")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        cacheFile.inputStream().use { it.copyTo(out) }
                    }
                    "Pictures/InkReel Studio/$fileName"
                } else {
                    cacheFile.absolutePath
                }
            } else {
                cacheFile.absolutePath
            }

            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
            Result.success(ExportResult(shareUri, displayPath, cacheFile.length()))
        } catch (e: Exception) {
            Log.e(TAG, "PNG thumbnail save failed", e)
            Result.failure(e)
        }
    }

    suspend fun exportMp4(
        context: Context,
        project: ReelProject,
        shotAudioMap: Map<String, ByteArray>,
        isMuted: Boolean,
        showCaptions: Boolean,
        showSpeechBubbles: Boolean,
        onProgress: (Float) -> Unit
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val width = project.exportResolution.width
        val height = project.exportResolution.height
        val totalDurationMs = project.totalDurationMs
        val totalFrames = ((totalDurationMs * FPS) / 1000L).toInt().coerceAtLeast(FPS)

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "InkReel_${System.currentTimeMillis()}.mp4"
        val outputFile = File(exportsDir, fileName)

        // Pre-mix full timeline PCM (24kHz 16-bit mono)
        val mixedPcm = ProceduralAudioEngine.mixReelAudio(project, shotAudioMap, isMuted)
        // Pre-compute per-frame lip sync amplitude map
        val lipMap = buildTimelineLipEnvelope(project, shotAudioMap, totalFrames)

        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, if (width >= 1080) 5_500_000 else 3_200_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                }
            }
            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME).apply {
                configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            val audioFormat = MediaFormat.createAudioFormat(
                AUDIO_MIME,
                ProceduralAudioEngine.SAMPLE_RATE,
                1
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()
            var encodedVideoFrames = 0L

            // Helper to drain encoders into muxer once both tracks have format
            fun drainEncoder(encoder: MediaCodec, info: MediaCodec.BufferInfo, isVideo: Boolean, endOfStream: Boolean) {
                if (endOfStream && isVideo) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(info, 5_000L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (isVideo) {
                                videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                            } else {
                                audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                            }
                            if (videoTrackIndex >= 0 && audioTrackIndex >= 0 && !muxerStarted) {
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            val encodedBuf = encoder.getOutputBuffer(outIdx)
                            if (encodedBuf != null && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && muxerStarted) {
                                encodedBuf.position(info.offset)
                                encodedBuf.limit(info.offset + info.size)
                                if (isVideo) {
                                    val ptsUs = (encodedVideoFrames * 1_000_000L) / FPS
                                    val fixedInfo = MediaCodec.BufferInfo().apply {
                                        set(info.offset, info.size, ptsUs, info.flags)
                                    }
                                    muxer.writeSampleData(videoTrackIndex, encodedBuf, fixedInfo)
                                    encodedVideoFrames++
                                } else {
                                    muxer.writeSampleData(audioTrackIndex, encodedBuf, info)
                                }
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            // 1. Feed all audio chunks into AAC encoder first until output format & buffers flow,
            //    and interleave video frame rendering!
            var audioBytePos = 0
            var audioSamplesFed = 0L
            var audioEosSent = false
            val audioChunkSize = 2048

            fun stepAudioFeed() {
                if (audioEosSent) return
                val inIdx = audioEncoder.dequeueInputBuffer(2_000L)
                if (inIdx >= 0) {
                    val buf = audioEncoder.getInputBuffer(inIdx) ?: return
                    buf.clear()
                    val ptsUs = (audioSamplesFed * 1_000_000L) / ProceduralAudioEngine.SAMPLE_RATE
                    if (audioBytePos >= mixedPcm.size) {
                        audioEncoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        audioEosSent = true
                    } else {
                        val toWrite = minOf(audioChunkSize, buf.capacity(), mixedPcm.size - audioBytePos)
                        buf.put(mixedPcm, audioBytePos, toWrite)
                        audioBytePos += toWrite
                        audioSamplesFed += (toWrite / 2)
                        audioEncoder.queueInputBuffer(inIdx, 0, toWrite, ptsUs, 0)
                    }
                }
            }

            // Pre-warm audio format so muxer starts quickly
            for (warm in 0 until 8) {
                stepAudioFeed()
                val outIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 2_000L)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                    break
                } else if (outIdx >= 0) {
                    audioEncoder.releaseOutputBuffer(outIdx, false)
                }
            }

            val offscreenBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val offscreenCanvas = Canvas(offscreenBmp)

            for (frame in 0 until totalFrames) {
                val playheadMs = (frame * 1000L) / FPS
                StickmanRenderer.renderFrame(
                    canvas = offscreenCanvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    project = project,
                    playheadMs = playheadMs,
                    lipAmplitude = lipMap.getOrElse(frame) { 0f },
                    showCaptions = showCaptions,
                    showSpeechBubbles = showSpeechBubbles
                )

                val surfCanvas = inputSurface.lockCanvas(null)
                try {
                    surfCanvas.drawBitmap(offscreenBmp, 0f, 0f, null)
                } finally {
                    inputSurface.unlockCanvasAndPost(surfCanvas)
                }

                stepAudioFeed()
                drainEncoder(videoEncoder, videoBufferInfo, isVideo = true, endOfStream = false)
                if (muxerStarted) {
                    drainEncoder(audioEncoder, audioBufferInfo, isVideo = false, endOfStream = false)
                }
                if (frame % 6 == 0) {
                    onProgress((frame.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.96f))
                }
            }

            offscreenBmp.recycle()

            // Flush remaining audio + video EOS
            while (!audioEosSent) {
                stepAudioFeed()
                if (muxerStarted) {
                    drainEncoder(audioEncoder, audioBufferInfo, isVideo = false, endOfStream = false)
                }
            }
            drainEncoder(videoEncoder, videoBufferInfo, isVideo = true, endOfStream = true)
            if (muxerStarted) {
                drainEncoder(audioEncoder, audioBufferInfo, isVideo = false, endOfStream = true)
            }
            onProgress(1f)

            // Save a copy into Android Downloads (API 29+) so user finds it immediately
            val displayPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/InkReel Studio")
                }
                val mediaUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (mediaUri != null) {
                    context.contentResolver.openOutputStream(mediaUri)?.use { out ->
                        outputFile.inputStream().use { it.copyTo(out) }
                    }
                    "Downloads/InkReel Studio/$fileName"
                } else {
                    outputFile.absolutePath
                }
            } else {
                outputFile.absolutePath
            }

            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
            Result.success(ExportResult(shareUri, displayPath, outputFile.length()))
        } catch (e: Exception) {
            Log.e(TAG, "MP4 export failed", e)
            Result.failure(e)
        } finally {
            runCatching { videoEncoder?.stop(); videoEncoder?.release() }
            runCatching { audioEncoder?.stop(); audioEncoder?.release() }
            runCatching { muxer?.stop(); muxer?.release() }
        }
    }

    private fun buildTimelineLipEnvelope(
        project: ReelProject,
        shotAudioMap: Map<String, ByteArray>,
        totalFrames: Int
    ): FloatArray {
        val env = FloatArray(totalFrames)
        var cursorFrame = 0
        for (shot in project.shots) {
            val shotFrames = ((shot.durationMs * FPS) / 1000L).toInt().coerceAtLeast(1)
            val pcm = shotAudioMap[shot.shotId]
            if (pcm != null && pcm.size >= 2 && shot.speaker != SpeakerSlot.SILENT) {
                val speed = project.characterFor(shot.speaker)?.voiceSpeed ?: 1.0f
                val samplesPerFrame = ((ProceduralAudioEngine.SAMPLE_RATE / FPS) * speed).toInt().coerceAtLeast(1)
                val totalSamples = pcm.size / 2
                for (f in 0 until shotFrames) {
                    val dst = cursorFrame + f
                    if (dst >= totalFrames) break
                    val startS = f * samplesPerFrame
                    val endS = (startS + samplesPerFrame).coerceAtMost(totalSamples)
                    if (startS < endS) {
                        var sumSq = 0.0
                        for (i in startS until endS) {
                            val lo = pcm[i * 2].toInt() and 0xFF
                            val hi = pcm[i * 2 + 1].toInt()
                            val v = ((hi shl 8) or lo).toShort().toFloat() / 32768f
                            sumSq += v * v
                        }
                        env[dst] = (sqrt(sumSq / (endS - startS)).toFloat() * 4.2f).coerceIn(0f, 1f)
                    }
                }
            }
            cursorFrame += shotFrames
        }
        return env
    }
}
