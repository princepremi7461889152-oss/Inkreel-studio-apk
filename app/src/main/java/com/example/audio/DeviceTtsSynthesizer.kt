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
import java.util.concurrent.ConcurrentHashMap

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
class DeviceTtsSynthesizer(
    context: Context,
    private val initTimeoutMs: Long = 8_000L,
    private val synthTimeoutMs: Long = 12_000L
) {

    private val appContext = context.applicationContext
    private val synthMutex = Mutex()
    private val cachedVoices = ConcurrentHashMap<String, android.speech.tts.Voice>()
    @Volatile
    private var voicesScanned = false
    @Volatile
    private var retryInitAfterMs = 0L
    private var tts: TextToSpeech? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null

    fun clearCooldown() {
        retryInitAfterMs = 0L
    }

    suspend fun warmUp() {
        runCatching {
            synthMutex.withLock {
                ensureInitialized()
            }
        }
    }

    private suspend fun createAndAwaitTts(enginePackage: String?): Pair<TextToSpeech, Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        val instance = withContext(Dispatchers.Main) {
            if (enginePackage != null) {
                TextToSpeech(appContext, { status ->
                    deferred.complete(status == TextToSpeech.SUCCESS)
                }, enginePackage)
            } else {
                TextToSpeech(appContext) { status ->
                    deferred.complete(status == TextToSpeech.SUCCESS)
                }
            }
        }
        val ok = withTimeoutOrNull(initTimeoutMs) { deferred.await() } ?: false
        return instance to ok
    }

    private suspend fun ensureInitialized(): TextToSpeech? {
        val existing = tts
        val existingInit = initDeferred
        if (existing != null && existingInit != null && existingInit.isCompleted) {
            return if (existingInit.await()) existing else null
        }

        val nowMs = System.currentTimeMillis()
        if (nowMs < retryInitAfterMs) {
            return null
        }

        // 1. Try system default TTS engine
        val (defaultInstance, defaultOk) = createAndAwaitTts(enginePackage = null)
        if (defaultOk) {
            retryInitAfterMs = 0L
            tts = defaultInstance
            initDeferred = CompletableDeferred(true)
            return defaultInstance
        }
        runCatching { defaultInstance.shutdown() }

        // 2. Fallback explicitly to Google Speech Services on OEM devices (Xiaomi/MIUI, etc.)
        val (googleInstance, googleOk) = createAndAwaitTts(enginePackage = "com.google.android.tts")
        if (googleOk) {
            retryInitAfterMs = 0L
            tts = googleInstance
            initDeferred = CompletableDeferred(true)
            return googleInstance
        }
        runCatching { googleInstance.shutdown() }

        retryInitAfterMs = System.currentTimeMillis() + 15_000L
        tts = null
        initDeferred = null
        return null
    }

    private fun isVoiceInstalledAndOffline(v: android.speech.tts.Voice): Boolean {
        if (v.isNetworkConnectionRequired) return false
        val features = v.features.orEmpty()
        if (features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) ||
            features.any { it.equals("notInstalled", ignoreCase = true) }
        ) {
            return false
        }
        return true
    }

    private fun resolveVoiceFor(
        engine: TextToSpeech,
        targetLocale: Locale,
        gender: CharacterGender
    ): android.speech.tts.Voice? {
        val key = "${targetLocale.language}_${gender.name}"
        cachedVoices[key]?.let { return it }
        if (!voicesScanned) {
            voicesScanned = true
            runCatching {
                val voices = engine.voices.orEmpty().filter { isVoiceInstalledAndOffline(it) }
                for (lang in listOf("hi", "en")) {
                    for (g in CharacterGender.entries) {
                        val wantFemale = g == CharacterGender.FEMALE
                        val match = voices.firstOrNull { v ->
                            val nameLower = v.name.lowercase()
                            val matchesLang = v.locale.language == lang
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
                            matchesLang && ((wantFemale && looksFemale) || (!wantFemale && looksMale))
                        }
                        if (match != null) {
                            cachedVoices["${lang}_${g.name}"] = match
                        }
                    }
                }
            }
        }
        return cachedVoices[key]
    }

    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("[?!]{2,}"), "!")
            .replace("—", ", ")
            .replace("...", ", ")
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}.,!?'\"\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private data class WavAttempt(val bytes: ByteArray?, val timedOut: Boolean)

    private suspend fun synthesizeToWavBytesOnce(
        engine: TextToSpeech,
        spokenText: String,
        outFile: File
    ): WavAttempt {
        if (outFile.exists()) outFile.delete()
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
        if (res != TextToSpeech.SUCCESS) return WavAttempt(null, timedOut = false)
        val ok = withTimeoutOrNull(synthTimeoutMs) { done.await() }
        if (ok == null) {
            return WavAttempt(null, timedOut = true)
        }
        if (!ok || !outFile.exists() || outFile.length() < 64L) return WavAttempt(null, timedOut = false)
        return WavAttempt(outFile.readBytes(), timedOut = false)
    }

    private fun hasAudibleSpeech(pcm24k: ByteArray): Boolean {
        if (pcm24k.size < 400) return false
        var peak = 0
        val sampleCount = pcm24k.size / 2
        val step = (sampleCount / 512).coerceAtLeast(1)
        var i = 0
        while (i < sampleCount) {
            val lo = pcm24k[i * 2].toInt() and 0xFF
            val hi = pcm24k[i * 2 + 1].toInt()
            val amp = kotlin.math.abs(((hi shl 8) or lo).toShort().toInt())
            if (amp > peak) peak = amp
            i += step
        }
        return peak >= 48
    }

    suspend fun synthesizeShotVoice(
        dialogue: String,
        phoneticHint: String,
        gender: CharacterGender,
        language: String,
        moodPose: MoodPose
    ): Result<SynthesizedAudioResult> {
        val rawTuned = HinglishPhoneticTuner.tune(dialogue, phoneticHint, language)
        val spokenText = sanitizeForSpeech(rawTuned).ifBlank { rawTuned.trim() }
        if (spokenText.isBlank()) {
            return Result.failure(IllegalArgumentException("Dialogue is empty."))
        }

        val pcm24k: ByteArray = synthMutex.withLock {
            withContext(Dispatchers.IO) {
                val engine = ensureInitialized()
                    ?: return@withContext Result.failure<ByteArray>(IllegalStateException("Device TextToSpeech engine unavailable."))

                val outFile = File(appContext.cacheDir, "tts_${UUID.randomUUID()}.wav")
                try {
                    val requestedLocale = when {
                        language.equals("Hindi", ignoreCase = true) -> Locale("hi", "IN")
                        language.equals("Hinglish", ignoreCase = true) -> Locale("en", "IN")
                        else -> Locale.US
                    }
                    val langStatus = engine.setLanguage(requestedLocale)
                    val activeLocale = if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.US)
                        Locale.US
                    } else {
                        requestedLocale
                    }

                    resolveVoiceFor(engine, activeLocale, gender)?.let { matched ->
                        runCatching { engine.voice = matched }
                    }

                    val basePitch = if (gender == CharacterGender.FEMALE) 1.22f else 0.92f
                    val moodPitchBoost = when (moodPose) {
                        MoodPose.SHOCKED, MoodPose.FURIOUS -> 0.08f
                        MoodPose.DEADPAN -> -0.06f
                        else -> 0f
                    }
                    engine.setPitch((basePitch + moodPitchBoost).coerceIn(0.65f, 1.55f))

                    val rate = when (moodPose) {
                        MoodPose.NERVOUS, MoodPose.SWEATING, MoodPose.FURIOUS -> 1.08f
                        MoodPose.DEADPAN -> 0.94f
                        else -> 1.0f
                    }
                    engine.setSpeechRate(rate)

                    var attempt = synthesizeToWavBytesOnce(engine, spokenText, outFile)
                    var decoded = attempt.bytes?.let { decodeWavTo24kMonoPcm(it) } ?: ByteArray(0)

                    // Retry with clean Locale.US/default voice if voice pack failed or returned silent audio
                    if (!hasAudibleSpeech(decoded) && !attempt.timedOut) {
                        runCatching {
                            engine.setLanguage(Locale.US)
                            engine.defaultVoice?.let { def -> engine.voice = def }
                        }
                        attempt = synthesizeToWavBytesOnce(engine, spokenText, outFile)
                        decoded = attempt.bytes?.let { decodeWavTo24kMonoPcm(it) } ?: ByteArray(0)
                    }

                    if (!hasAudibleSpeech(decoded) && !attempt.timedOut) {
                        runCatching { engine.setLanguage(Locale.ENGLISH) }
                        attempt = synthesizeToWavBytesOnce(engine, spokenText, outFile)
                        decoded = attempt.bytes?.let { decodeWavTo24kMonoPcm(it) } ?: ByteArray(0)
                    }

                    if (attempt.timedOut) {
                        retryInitAfterMs = System.currentTimeMillis() + 15_000L
                    }

                    if (!hasAudibleSpeech(decoded)) {
                        return@withContext Result.failure<ByteArray>(IllegalStateException("Device TTS generated empty audio."))
                    }
                    Result.success(decoded)
                } catch (e: Exception) {
                    Log.w(TAG, "Device TTS synthesis failed", e)
                    Result.failure<ByteArray>(e)
                } finally {
                    runCatching { if (outFile.exists()) outFile.delete() }
                }
            }
        }.getOrElse { return Result.failure(it) }

        val rawDurationMs = ((pcm24k.size.toDouble() / 48000.0) * 1000.0).toLong().coerceAtLeast(700L)
        return Result.success(SynthesizedAudioResult(pcmBytes = pcm24k, rawDurationMs = rawDurationMs))
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        initDeferred = null
        cachedVoices.clear()
        voicesScanned = false
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
            if (chunkLen < 0) break
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
