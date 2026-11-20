package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.example.ai.SynthesizedAudioResult
import com.example.model.BgmMood
import com.example.model.CharacterGender
import com.example.model.ComicSfx
import com.example.model.MoodPose
import com.example.model.ReelProject
import com.example.model.SpeakerSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure Kotlin 24kHz 16-bit mono PCM synthesizer, timeline mixer (voices + procedural comic SFX +
 * procedural BGM with automatic speech ducking), live AudioTrack playback, and Microphone dubbing.
 */
object ProceduralAudioEngine {

    const val SAMPLE_RATE = 24000

    fun synthesizeSfxSamples(sfx: ComicSfx): FloatArray {
        return when (sfx) {
            ComicSfx.NONE -> FloatArray(0)
            ComicSfx.DOOR_OPEN -> genSamples(0.45f) { t, _ ->
                val creak = sin(2.0 * PI * (180.0 + 160.0 * sin(t * 26.0)) * t) * exp(-t * 4.5)
                val latch = if (t < 0.05f) sin(2.0 * PI * 920.0 * t) * exp(-t * 60.0) else 0.0
                (creak * 0.45 + latch * 0.5).toFloat()
            }
            ComicSfx.DOOR_SLAM -> genSamples(0.48f) { t, rng ->
                val thud = sin(2.0 * PI * (110.0 * exp(-t * 14.0)) * t) * exp(-t * 11.0)
                val crack = (rng.nextFloat() * 2f - 1f) * exp(-t * 26.0)
                (thud * 0.85 + crack * 0.45).toFloat()
            }
            ComicSfx.DRAMATIC_STING -> genSamples(0.85f) { t, _ ->
                val env = exp(-t * 3.2)
                val f1 = sin(2.0 * PI * 146.83 * t)
                val f2 = sin(2.0 * PI * 155.56 * t) // tense minor 2nd
                val f3 = sin(2.0 * PI * 220.0 * t)
                ((f1 * 0.45 + f2 * 0.4 + f3 * 0.3) * env).toFloat()
            }
            ComicSfx.DESK_SLAM -> genSamples(0.42f) { t, rng ->
                val sub = sin(2.0 * PI * (135.0 * exp(-t * 18.0)) * t) * exp(-t * 10.0)
                val slap = (rng.nextFloat() * 2f - 1f) * exp(-t * 35.0)
                (sub * 0.9 + slap * 0.55).toFloat()
            }
            ComicSfx.NERVOUS_RATTLE -> genSamples(0.55f) { t, _ ->
                val pulse = if (((t * 22f).toInt() % 2) == 0) 1.0 else 0.25
                val freq = 420.0 + 90.0 * sin(t * 50.0)
                (sin(2.0 * PI * freq * t) * pulse * exp(-t * 2.5) * 0.42).toFloat()
            }
            ComicSfx.CLIPBOARD_DROP -> genSamples(0.55f) { t, rng ->
                val b1 = exp(-abs(t - 0.03) * 45.0) * sin(2.0 * PI * 540.0 * t)
                val b2 = exp(-abs(t - 0.16) * 55.0) * sin(2.0 * PI * 620.0 * t)
                val b3 = exp(-abs(t - 0.25) * 70.0) * sin(2.0 * PI * 700.0 * t)
                val clatter = (rng.nextFloat() * 2f - 1f) * exp(-t * 12.0) * 0.25
                ((b1 + b2 * 0.6 + b3 * 0.35) * 0.5 + clatter).toFloat()
            }
            ComicSfx.PUNCHLINE_BOOM -> genSamples(0.65f) { t, rng ->
                val boom = sin(2.0 * PI * (105.0 * exp(-t * 7.5)) * t) * exp(-t * 5.0)
                val rim = if (t < 0.08f) (rng.nextFloat() * 2f - 1f) * exp(-t * 35.0) else 0.0
                (boom * 0.85 + rim * 0.4).toFloat()
            }
            ComicSfx.RECORD_SCRATCH -> genSamples(0.38f) { t, rng ->
                val chirp = sin(2.0 * PI * (1100.0 - 900.0 * (t / 0.38)) * t)
                val grit = (rng.nextFloat() * 2f - 1f) * 0.45
                ((chirp * 0.5 + grit) * exp(-t * 4.0)).toFloat()
            }
            ComicSfx.SAD_VIOLIN -> genSamples(1.1f) { t, _ ->
                val baseFreq = 440.0 - 110.0 * (t / 1.1)
                val vibrato = 8.0 * sin(2.0 * PI * 6.0 * t)
                val wave = sin(2.0 * PI * (baseFreq + vibrato) * t) +
                    0.35 * sin(2.0 * PI * 2.0 * (baseFreq + vibrato) * t)
                val env = (1.0 - exp(-t * 12.0)) * exp(-t * 1.4)
                (wave * env * 0.42).toFloat()
            }
            ComicSfx.TIME_SKIP_WHOOSH -> genSamples(0.50f) { t, rng ->
                val sweep = sin(2.0 * PI * (240.0 + 700.0 * sin(PI * t / 0.5)) * t) * 0.28
                val wind = (rng.nextFloat() * 2f - 1f) * sin(PI * t / 0.5) * 0.45
                (sweep + wind).toFloat()
            }
            ComicSfx.CARTOON_RUN -> genSamples(0.65f) { t, _ ->
                val step = (t * 18.0).toInt()
                val localT = t - (step / 18.0)
                val note = 380.0 + (step % 5) * 95.0
                (sin(2.0 * PI * note * localT) * exp(-localT * 32.0) * 0.55).toFloat()
            }
            ComicSfx.CARTOON_BONK -> genSamples(0.55f) { t, _ ->
                val bonkFreq = 260.0 + 280.0 * exp(-t * 20.0)
                val bonk = sin(2.0 * PI * bonkFreq * t) * exp(-t * 9.0)
                val bird = if (t > 0.14f) sin(2.0 * PI * (1450.0 + 200.0 * sin(t * 40.0)) * t) * exp(-(t - 0.14f) * 6.0) * 0.25 else 0.0
                (bonk * 0.78 + bird).toFloat()
            }
        }
    }

    fun synthesizeBgmSample(mood: BgmMood, timeSec: Double): Float {
        return when (mood) {
            BgmMood.QUIRKY_COMEDY -> {
                val beat = (timeSec * 2.4).toInt()
                val local = timeSec - beat / 2.4
                val notes = doubleArrayOf(261.63, 329.63, 392.00, 329.63, 293.66, 349.23, 440.00, 349.23)
                val f = notes[beat % notes.size]
                val bass = if (beat % 2 == 0) 130.81 else 196.00
                val pluck = sin(2.0 * PI * f * local) * exp(-local * 11.0) * 0.35
                val bassWave = sin(2.0 * PI * bass * local) * exp(-local * 7.0) * 0.28
                (pluck + bassWave).toFloat()
            }
            BgmMood.DRAMATIC_SUSPENSE -> {
                val pulse = 0.5 + 0.5 * sin(2.0 * PI * 1.5 * timeSec)
                val drone = sin(2.0 * PI * 73.42 * timeSec) * 0.32
                val tension = sin(2.0 * PI * 155.56 * timeSec) * 0.14 * pulse
                (drone + tension).toFloat()
            }
            BgmMood.CHAOTIC_CHASE -> {
                val step = (timeSec * 6.0).toInt()
                val local = timeSec - step / 6.0
                val gallop = doubleArrayOf(196.0, 246.94, 293.66, 392.0, 349.23, 293.66)
                val f = gallop[step % gallop.size]
                val lead = sin(2.0 * PI * f * local) * exp(-local * 14.0) * 0.36
                val kick = sin(2.0 * PI * 90.0 * local) * exp(-local * 22.0) * if (step % 2 == 0) 0.35 else 0.0
                (lead + kick).toFloat()
            }
            BgmMood.WHOLESOME_CHILL -> {
                val chordIdx = (timeSec * 0.75).toInt() % 4
                val roots = doubleArrayOf(261.63, 220.00, 174.61, 196.00)
                val r = roots[chordIdx]
                val pad = (sin(2.0 * PI * r * timeSec) +
                    0.6 * sin(2.0 * PI * (r * 1.25) * timeSec) +
                    0.5 * sin(2.0 * PI * (r * 1.5) * timeSec)) * 0.14
                pad.toFloat()
            }
        }
    }

    /**
     * Mixes voiceover PCMs (resampled for character voiceSpeed), triggered comic SFX, and
     * procedural BGM with automatic volume ducking during speech into one 24kHz 16-bit mono ByteArray.
     */
    fun mixReelAudio(
        project: ReelProject,
        shotAudioMap: Map<String, ByteArray>,
        isMuted: Boolean = false
    ): ByteArray {
        val totalSamples = ((project.totalDurationMs / 1000.0) * SAMPLE_RATE).toInt().coerceAtLeast(SAMPLE_RATE)
        if (isMuted) return ByteArray(totalSamples * 2)

        val mix = FloatArray(totalSamples)
        val speechActivity = BooleanArray(totalSamples)

        var cursorSample = 0
        for (shot in project.shots) {
            val shotSamples = ((shot.durationMs / 1000.0) * SAMPLE_RATE).toInt().coerceAtLeast(1)
            val charSpeed = project.characterFor(shot.speaker)?.voiceSpeed ?: 1.0f

            // 1. Place character voiceover (if any)
            val rawVoicePcm = shotAudioMap[shot.shotId]
            if (rawVoicePcm != null && rawVoicePcm.size >= 2 && shot.speaker != SpeakerSlot.SILENT) {
                val rawCount = rawVoicePcm.size / 2
                val speed = charSpeed.coerceIn(0.85f, 1.30f)
                val resampledCount = (rawCount / speed).toInt().coerceAtMost(shotSamples)
                for (i in 0 until resampledCount) {
                    val dst = cursorSample + i
                    if (dst >= totalSamples) break
                    val srcPos = i * speed
                    val idx0 = srcPos.toInt().coerceIn(0, rawCount - 1)
                    val s0 = readPcmSample(rawVoicePcm, idx0)
                    mix[dst] += s0 * 0.95f
                    speechActivity[dst] = true
                }
            }

            // 2. Place triggered Comic SFX at shot start
            val sfxSamples = synthesizeSfxSamples(shot.sfx)
            for (i in sfxSamples.indices) {
                val dst = cursorSample + i
                if (dst >= totalSamples) break
                mix[dst] += sfxSamples[i] * 0.72f
            }

            cursorSample += shotSamples
        }

        // 2b. Optional Outro CTA Card sting/whoosh
        if (project.outroDurationMs > 0L && cursorSample < totalSamples) {
            val outroSfx = synthesizeSfxSamples(ComicSfx.TIME_SKIP_WHOOSH)
            for (i in outroSfx.indices) {
                val dst = cursorSample + i
                if (dst >= totalSamples) break
                mix[dst] += outroSfx[i] * 0.65f
            }
        }

        // 3. Add procedural BGM with smooth automatic ducking during speech
        val baseBgmGain = project.bgmVolume.coerceIn(0f, 1f) * 0.48f
        var duckEnvelope = 1.0f
        for (i in 0 until totalSamples) {
            val targetDuck = if (speechActivity[i]) 0.22f else 1.0f
            duckEnvelope += (targetDuck - duckEnvelope) * 0.004f
            val bgm = synthesizeBgmSample(project.bgmMood, i.toDouble() / SAMPLE_RATE)
            mix[i] += bgm * baseBgmGain * duckEnvelope
        }

        // 4. Soft-clip & convert to 16-bit PCM
        val out = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val clamped = softLimit(mix[i])
            val pcmVal = (clamped * 32767f).toInt().coerceIn(-32768, 32767)
            out[i * 2] = (pcmVal and 0xFF).toByte()
            out[i * 2 + 1] = ((pcmVal shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun readPcmSample(pcm: ByteArray, sampleIndex: Int): Float {
        val lo = pcm[sampleIndex * 2].toInt() and 0xFF
        val hi = pcm[sampleIndex * 2 + 1].toInt()
        return ((hi shl 8) or lo).toShort().toFloat() / 32768f
    }

    private fun softLimit(x: Float): Float {
        return when {
            x > 0.95f -> 0.95f + 0.05f * ((x - 0.95f) / (1f + (x - 0.95f)))
            x < -0.95f -> -0.95f - 0.05f * ((-x - 0.95f) / (1f + (-x - 0.95f)))
            else -> x
        }
    }

    /**
     * Last-resort expressive cartoon speech synthesizer at 24kHz 16-bit mono PCM when both
     * cloud TTS and device TextToSpeech engines return empty audio or lack offline language packs.
     * Shapes syllable pulses with vowel formants, pitch inflection, and mood pacing.
     */
    fun synthesizeCartoonVoiceFallback(
        text: String,
        gender: CharacterGender,
        moodPose: MoodPose
    ): SynthesizedAudioResult {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val durationMs = ((words.size.coerceAtLeast(1) * 320L) + 450L).coerceIn(1100L, 12000L)
        val totalSamples = ((durationMs / 1000.0) * SAMPLE_RATE).toInt().coerceAtLeast(SAMPLE_RATE)
        val out = ByteArray(totalSamples * 2)

        val baseFreq = if (gender == CharacterGender.FEMALE) 235.0 else 132.0
        val moodBoost = when (moodPose) {
            MoodPose.SHOCKED, MoodPose.FURIOUS, MoodPose.ANGRY -> 1.12
            MoodPose.DEADPAN -> 0.90
            else -> 1.0
        }
        val syllableRateHz = when (moodPose) {
            MoodPose.NERVOUS, MoodPose.SWEATING, MoodPose.FURIOUS -> 6.8
            MoodPose.DEADPAN -> 4.2
            else -> 5.4
        }

        val rng = Random(text.hashCode())
        var phase1 = 0.0
        var phase2 = 0.0
        val fadeSamples = (0.04 * SAMPLE_RATE).toInt().coerceAtLeast(1)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val syllPhase = (t * syllableRateHz) % 1.0
            val syllIndex = (t * syllableRateHz).toInt()
            // Brief consonant pauses between words/syllables give crisp lip-sync modulation
            val isPause = ((syllIndex % 5) == 4 && syllPhase > 0.55)
            val env = if (isPause) {
                0.04
            } else {
                sin(PI * syllPhase).coerceAtLeast(0.0)
            }
            val inflection = 1.0 + 0.14 * sin(t * 7.5 + (syllIndex % 3))
            val f0 = baseFreq * moodBoost * inflection
            phase1 += 2.0 * PI * f0 / SAMPLE_RATE
            phase2 += 2.0 * PI * (f0 * 2.25) / SAMPLE_RATE

            val vocal = (sin(phase1) * 0.58 + sin(phase2) * 0.24 + sin(phase1 * 3.0) * 0.12)
            val breath = (rng.nextDouble() * 2.0 - 1.0) * 0.04
            val edgeGain = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i > totalSamples - fadeSamples -> (totalSamples - i).toDouble() / fadeSamples
                else -> 1.0
            }
            val sample = ((vocal + breath) * env * edgeGain * 0.62).toFloat().coerceIn(-1f, 1f)
            val pcm = (sample * 32767f).toInt().coerceIn(-32768, 32767)
            out[i * 2] = (pcm and 0xFF).toByte()
            out[i * 2 + 1] = ((pcm shr 8) and 0xFF).toByte()
        }

        return SynthesizedAudioResult(pcmBytes = out, rawDurationMs = durationMs)
    }

    private inline fun genSamples(durationSec: Float, fn: (t: Double, rng: Random) -> Float): FloatArray {
        val n = (durationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val rng = Random(42)
        return FloatArray(n) { i -> fn(i.toDouble() / SAMPLE_RATE, rng) }
    }
}

class LiveReelAudioPlayer {
    private var track: AudioTrack? = null
    private var playbackJob: Job? = null

    fun playFromMs(
        scope: CoroutineScope,
        mixedPcm: ByteArray,
        startMs: Long
    ) {
        stop()
        if (mixedPcm.isEmpty()) return

        val byteOffset = (((startMs / 1000.0) * ProceduralAudioEngine.SAMPLE_RATE).toInt() * 2)
            .coerceIn(0, (mixedPcm.size - 2).coerceAtLeast(0))
            .let { it - (it % 2) }

        val minBuf = AudioTrack.getMinBufferSize(
            ProceduralAudioEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ProceduralAudioEngine.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                audioTrack.play()
                var pos = byteOffset
                val chunk = 4096
                while (isActive && pos < mixedPcm.size) {
                    val count = minOf(chunk, mixedPcm.size - pos)
                    val written = audioTrack.write(mixedPcm, pos, count)
                    if (written <= 0) break
                    pos += written
                }
            } catch (e: Exception) {
                Log.w("LiveReelAudioPlayer", "Playback interrupted: ${e.message}")
            }
        }
    }

    fun playSfxPreview(scope: CoroutineScope, sfx: ComicSfx) {
        val samples = ProceduralAudioEngine.synthesizeSfxSamples(sfx)
        if (samples.isEmpty()) return
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[i * 2] = (v and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        playFromMs(scope, pcm, 0L)
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        val t = track
        track = null
        if (t != null) {
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                t.release()
            }
        }
    }
}

class MicVoiceRecorder {
    private var recorder: AudioRecord? = null
    private var recordJob: Job? = null
    private val chunks = mutableListOf<ByteArray>()

    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope, onError: (String) -> Unit) {
        stopRecording()
        chunks.clear()

        val minBuf = AudioRecord.getMinBufferSize(
            ProceduralAudioEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            ProceduralAudioEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onError("Microphone could not be initialized.")
            return
        }

        recorder = audioRecord
        recordJob = scope.launch(Dispatchers.IO) {
            try {
                audioRecord.startRecording()
                val buf = ByteArray(2048)
                while (isActive) {
                    val read = audioRecord.read(buf, 0, buf.size)
                    if (read > 0) {
                        synchronized(chunks) {
                            chunks.add(buf.copyOf(read))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MicVoiceRecorder", "Recording error", e)
            }
        }
    }

    fun stopRecording(): ByteArray {
        recordJob?.cancel()
        recordJob = null
        val rec = recorder
        recorder = null
        if (rec != null) {
            runCatching {
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
                rec.release()
            }
        }
        val total = synchronized(chunks) { chunks.sumOf { it.size } }
        val result = ByteArray(total)
        var offset = 0
        synchronized(chunks) {
            for (c in chunks) {
                System.arraycopy(c, 0, result, offset, c.size)
                offset += c.size
            }
            chunks.clear()
        }
        return result
    }
}
