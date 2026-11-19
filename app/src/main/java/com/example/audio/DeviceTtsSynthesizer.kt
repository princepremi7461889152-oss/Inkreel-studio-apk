package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.ai.SynthesizedAudioResult
import com.example.model.CharacterGender
import com.example.model.MoodPose
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Normalizes Romanized Hinglish words into clearer phonetic forms for Android TTS engines
 * while keeping the original script text intact for visual captions & speech bubbles.
 */
object HinglishPhoneticTuner {
    private val wordReplacements = listOf(
        Regex("\\bkyun\\b", RegexOption.IGNORE_CASE) to "kyoon",
        Regex("\\bkyunki\\b", RegexOption.IGNORE_CASE) to "kyoon-ki",
        Regex("\\bkya\\b", RegexOption.IGNORE_CASE) to "kyaa",
        Regex("\\bnahi\\b", RegexOption.IGNORE_CASE) to "naheen",
        Regex("\\bhain\\b", RegexOption.IGNORE_CASE) to "hen",
        Regex("\\bhoon\\b", RegexOption.IGNORE_CASE) to "hoon",
        Regex("\\baccha\\b", RegexOption.IGNORE_CASE) to "ach-chaa",
        Regex("\\bacha\\b", RegexOption.IGNORE_CASE) to "ach-chaa",
        Regex("\\bmein\\b", RegexOption.IGNORE_CASE) to "mayn",
        Regex("\\bmaine\\b", RegexOption.IGNORE_CASE) to "may-nay",
        Regex("\\byaar\\b", RegexOption.IGNORE_CASE) to "yaar",
        Regex("\\bbhaiya\\b", RegexOption.IGNORE_CASE) to "bhai-yaa",
        Regex("\\bzyada\\b", RegexOption.IGNORE_CASE) to "zyaa-daa",
        Regex("\\bthoda\\b", RegexOption.IGNORE_CASE) to "tho-daa",
        Regex("\\bpaisa\\b", RegexOption.IGNORE_CASE) to "pai-saa",
        Regex("\\bpaise\\b", RegexOption.IGNORE_CASE) to "pai-say",
        Regex("\\baaye\\b", RegexOption.IGNORE_CASE) to "aa-yay",
        Regex("\\bdiya\\b", RegexOption.IGNORE_CASE) to "di-yaa",
        Regex("\\bliya\\b", RegexOption.IGNORE_CASE) to "li-yaa",
        Regex("\\bfekta\\b", RegexOption.IGNORE_CASE) to "fayk-taa",
        Regex("\\bbech\\b", RegexOption.IGNORE_CASE) to "baych",
        Regex("\\bbete\\b", RegexOption.IGNORE_CASE) to "bay-tay",
        Regex("\\bbeta\\b", RegexOption.IGNORE_CASE) to "bay-taa",
        Regex("\\bbeti\\b", RegexOption.IGNORE_CASE) to "bay-tee",
        Regex("\\bkaun\\b", RegexOption.IGNORE_CASE) to "kawn",
        Regex("\\bkaha\\b", RegexOption.IGNORE_CASE) to "ka-haa",
        Regex("\\bchalo\\b", RegexOption.IGNORE_CASE) to "cha-lo",
        Regex("\\bmatlab\\b", RegexOption.IGNORE_CASE) to "mat-lab",
        Regex("\\bsamjhe\\b", RegexOption.IGNORE_CASE) to "sam-jhay",
        Regex("\\bkaro\\b", RegexOption.IGNORE_CASE) to "ka-ro",
        Regex("\\bbatao\\b", RegexOption.IGNORE_CASE) to "ba-tao"
    )

    fun tune(rawText: String, phoneticHint: String, language: String): String {
        val base = phoneticHint.ifBlank { rawText }.trim()
        if (base.isEmpty()) return ""
        if (!language.equals("Hinglish", ignoreCase = true) && phoneticHint.isBlank()) {
            return base
        }
        var tuned = base
        for ((pattern, replacement) in wordReplacements) {
            tuned = pattern.replace(tuned, replacement)
        }
        return tuned
    }
}

/**
 * On-device Android TextToSpeech synthesizer that renders spoken dialogue to a temporary WAV
 * file and converts it into 24kHz 16-bit mono PCM bytes so stickman models always speak with
 * real voice + lip sync even offline or before Gemini TTS is run.
 */
class DeviceTtsSynthesizer(context: Context) {

    private val appContext = context.applicationContext
    private val synthMutex = Mutex()
    private var tts: TextToSpeech? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null

    private suspend fun ensureInitialized(): TextToSpeech? {
        val existing = tts
        val existingInit = initDeferred
        if (existing != null && existingInit != null && existingInit.isCompleted) {
            return if (existingInit.await()) existing else null
        }

        val deferred = CompletableDeferred<Boolean>()
        initDeferred = deferred

        val instance = withContext(Dispatchers.Main) {
            TextToSpeech(appContext) { status ->
                deferred.complete(status == TextToSpeech.SUCCESS)
            }
        }
        tts = instance
        val ok = withTimeoutOrNull(8_000L) { deferred.await() } ?: false
        return if (ok) instance else null
    }

    suspend fun synthesizeShotVoice(
        dialogue: String,
        phoneticHint: String,
        gender: CharacterGender,
        language: String,
        moodPose: MoodPose
    ): Result<SynthesizedAudioResult> = synthMutex.withLock {
        withContext(Dispatchers.IO) {
            val spokenText = HinglishPhoneticTuner.tune(dialogue, phoneticHint, language)
            if (spokenText.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Dialogue is empty."))
            }
            val engine = ensureInitialized()
                ?: return@withContext Result.failure(IllegalStateException("Device TextToSpeech engine unavailable."))

            val outFile = File(appContext.cacheDir, "tts_${UUID.randomUUID()}.wav")
            try {
                val targetLocale = when {
                    language.equals("Hindi", ignoreCase = true) -> Locale("hi", "IN")
                    language.equals("Hinglish", ignoreCase = true) -> Locale("en", "IN")
                    else -> Locale.US
                }
                val langStatus = engine.setLanguage(targetLocale)
                if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }

                // Select a device voice matching gender + language if available
                runCatching {
                    val voices = engine.voices.orEmpty()
                    val wantFemale = gender == CharacterGender.FEMALE
                    val matched = voices.firstOrNull { v ->
                        val nameLower = v.name.lowercase()
                        val matchesLang = v.locale.language == targetLocale.language
                        val looksFemale = nameLower.contains("female") ||
                            nameLower.contains("-f-") ||
                            nameLower.endsWith("-f") ||
                            nameLower.contains("sfg") ||
                            nameLower.contains("tpf") ||
                            nameLower.contains("iob")
                        val looksMale = nameLower.contains("male") ||
                            nameLower.contains("-m-") ||
                            nameLower.endsWith("-m") ||
                            nameLower.contains("iol") ||
                            nameLower.contains("iom")
                        matchesLang && !v.isNetworkConnectionRequired && ((wantFemale && looksFemale) || (!wantFemale && looksMale))
                    }
                    if (matched != null) {
                        engine.voice = matched
                    }
                }

                // Pitch & rate tuned for Male vs Female & comedy emotion
                val basePitch = if (gender == CharacterGender.FEMALE) 1.24f else 0.92f
                val moodPitchBoost = when (moodPose) {
                    MoodPose.SHOCKED, MoodPose.FURIOUS -> 0.08f
                    MoodPose.DEADPAN -> -0.06f
                    else -> 0f
                }
                engine.setPitch((basePitch + moodPitchBoost).coerceIn(0.65f, 1.55f))

                val rate = when (moodPose) {
                    MoodPose.NERVOUS, MoodPose.SWEATING, MoodPose.FURIOUS -> 1.10f
                    MoodPose.DEADPAN -> 0.93f
                    else -> 1.0f
                }
                engine.setSpeechRate(rate)

                val utteranceId = "utt_${UUID.randomUUID()}"
                val done = CompletableDeferred<Boolean>()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (id == utteranceId) done.complete(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId) done.complete(false)
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) done.complete(false)
                    }
                })

                val res = engine.synthesizeToFile(spokenText, Bundle(), outFile, utteranceId)
                if (res != TextToSpeech.SUCCESS) {
                    return@withContext Result.failure(IllegalStateException("TTS synthesizeToFile failed ($res)."))
                }

                val ok = withTimeoutOrNull(20_000L) { done.await() } ?: false
                if (!ok || !outFile.exists() || outFile.length() < 64L) {
                    return@withContext Result.failure(IllegalStateException("Device TTS generated empty audio."))
                }

                val wavBytes = outFile.readBytes()
                val pcm24k = decodeWavTo24kMonoPcm(wavBytes)
                if (pcm24k.size < 200) {
                    return@withContext Result.failure(IllegalStateException("Decoded TTS audio was empty."))
                }
                val rawDurationMs = ((pcm24k.size.toDouble() / 48000.0) * 1000.0).toLong().coerceAtLeast(700L)
                Result.success(SynthesizedAudioResult(pcmBytes = pcm24k, rawDurationMs = rawDurationMs))
            } catch (e: Exception) {
                Log.w(TAG, "Device TTS synthesis failed", e)
                Result.failure(e)
            } finally {
                runCatching { if (outFile.exists()) outFile.delete() }
            }
        }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        initDeferred = null
    }

    private fun decodeWavTo24kMonoPcm(wav: ByteArray): ByteArray {
        if (wav.size < 44) return ByteArray(0)
        var pos = 12
        var channels = 1
        var srcRate = 22050
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (pos + 8 <= wav.size) {
            val chunkId = String(wav, pos, 4, Charsets.US_ASCII)
            val chunkLen = readIntLe(wav, pos + 4)
            val bodyStart = pos + 8
            if (chunkId == "fmt " && bodyStart + 16 <= wav.size) {
                channels = readShortLe(wav, bodyStart + 2).coerceAtLeast(1)
                srcRate = readIntLe(wav, bodyStart + 4).coerceAtLeast(8000)
                bitsPerSample = readShortLe(wav, bodyStart + 14).coerceAtLeast(8)
            } else if (chunkId == "data") {
                dataOffset = bodyStart
                dataSize = chunkLen.coerceAtMost(wav.size - bodyStart)
                break
            }
            val next = bodyStart + chunkLen + (chunkLen and 1)
            if (next <= pos) break
            pos = next
        }

        if (dataOffset < 0 || dataSize <= 0) return ByteArray(0)
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val frameSize = bytesPerSample * channels
        val srcFrames = dataSize / frameSize
        if (srcFrames <= 0) return ByteArray(0)

        val monoSrc = FloatArray(srcFrames)
        for (i in 0 until srcFrames) {
            var sum = 0f
            for (c in 0 until channels) {
                val off = dataOffset + i * frameSize + c * bytesPerSample
                val sample = if (bitsPerSample == 16 && off + 1 < wav.size) {
                    val lo = wav[off].toInt() and 0xFF
                    val hi = wav[off + 1].toInt()
                    ((hi shl 8) or lo).toShort().toFloat() / 32768f
                } else {
                    ((wav[off].toInt() and 0xFF) - 128) / 128f
                }
                sum += sample
            }
            monoSrc[i] = (sum / channels).coerceIn(-1f, 1f)
        }

        val targetRate = ProceduralAudioEngine.SAMPLE_RATE
        val dstFrames = ((srcFrames.toLong() * targetRate) / srcRate).toInt().coerceAtLeast(1)
        val out = ByteArray(dstFrames * 2)
        for (j in 0 until dstFrames) {
            val srcPos = (j.toDouble() * srcRate) / targetRate
            val i0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(srcFrames - 1)
            val frac = (srcPos - i0).toFloat()
            val v = monoSrc[i0] * (1f - frac) + monoSrc[i1] * frac
            val s = (v.coerceIn(-1f, 1f) * 32767f).toInt()
            out[j * 2] = (s and 0xFF).toByte()
            out[j * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun readShortLe(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun readIntLe(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    companion object {
        private const val TAG = "DeviceTtsSynthesizer"
    }
}
