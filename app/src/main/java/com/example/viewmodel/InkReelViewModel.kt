package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiInkReelService
import com.example.audio.DeviceTtsSynthesizer
import com.example.audio.LiveReelAudioPlayer
import com.example.audio.MicVoiceRecorder
import com.example.audio.ProceduralAudioEngine
import com.example.data.ReelRepository
import com.example.export.ExportResult
import com.example.export.Mp4Exporter
import com.example.model.BgmMood
import com.example.model.CaptionFontStyle
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.CharacterGender
import com.example.model.CharacterProfile
import com.example.model.ClonedStyleBlueprint
import com.example.model.ComicSfx
import com.example.model.ExportResolution
import com.example.model.ReelColorTheme
import com.example.model.ReelProject
import com.example.model.ReelShot
import com.example.model.RoomSetting
import com.example.model.SpeakerSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sin

enum class StageViewMode { YOUR_REEL, REFERENCE_CLIP }

data class InkReelStudioState(
    val project: ReelProject = ReelProject(),
    val selectedShotId: String = project.shots.firstOrNull()?.shotId.orEmpty(),
    val hasGeneratedReel: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val isPlaying: Boolean = false,
    val playheadMs: Long = 0L,
    val isMuted: Boolean = false,
    val showCaptions: Boolean = true,
    val showSpeechBubbles: Boolean = true,
    val stageViewMode: StageViewMode = StageViewMode.YOUR_REEL,
    val referenceVideoUri: Uri? = null,
    val brainstormedHooks: List<String> = emptyList(),
    val isBrainstorming: Boolean = false,
    val isGeneratingReel: Boolean = false,
    val isSynthesizingAllVoices: Boolean = false,
    val voicingShotIds: Set<String> = emptySet(),
    val isAnalyzingStyle: Boolean = false,
    val clonedStyleBlueprint: ClonedStyleBlueprint? = null,
    val recordingShotId: String? = null,
    val isSavingReel: Boolean = false,
    val isExportingMp4: Boolean = false,
    val exportProgress: Float = 0f,
    val lastExportResult: ExportResult? = null,
    val lastMp4Export: ExportResult? = null,
    val statusBanner: String? = null,
    val errorBanner: String? = null
)

class InkReelViewModel(
    private val repository: ReelRepository,
    private val geminiService: GeminiInkReelService,
    private val deviceTts: DeviceTtsSynthesizer
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        InkReelStudioState(
            project = ReelProject(watermarkHandle = repository.getWatermarkHandle())
        )
    )
    val uiState: StateFlow<InkReelStudioState> = _uiState.asStateFlow()

    val apiKey: StateFlow<String> = repository.apiKeyFlow
    val savedReels: StateFlow<List<ReelProject>> = repository.savedReels

    private val shotAudioMap = ConcurrentHashMap<String, ByteArray>()
    private val shotEnvelopeMap = ConcurrentHashMap<String, FloatArray>()

    private val livePlayer = LiveReelAudioPlayer()
    private val micRecorder = MicVoiceRecorder()
    private var playLoopJob: Job? = null
    private var playbackLaunchJob: Job? = null
    private var voiceJob: Job? = null

    init {
        voiceJob = viewModelScope.launch {
            deviceTts.warmUp()
            ensureMissingVoicesInternal(preferGeminiIfKeySet = false)
        }
    }

    fun updateWatermarkUsername(rawUsername: String) {
        val cleaned = rawUsername.take(36)
        repository.saveWatermarkHandle(cleaned)
        _uiState.update { st ->
            st.copy(
                lastMp4Export = null,
                project = st.project.copy(watermarkHandle = cleaned)
            )
        }
    }

    fun openApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true) }
    }

    fun dismissApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false) }
    }

    fun saveApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(errorBanner = "API key cannot be empty.") }
            return
        }
        repository.saveApiKey(trimmed)
        _uiState.update {
            it.copy(
                showApiKeyDialog = false,
                errorBanner = null,
                statusBanner = "Gemini API key saved on this device."
            )
        }
    }

    fun clearApiKey() {
        repository.clearApiKey()
        _uiState.update {
            it.copy(
                showApiKeyDialog = false,
                statusBanner = "Gemini API key cleared."
            )
        }
    }

    private fun requireApiKey(): String? {
        val key = repository.getApiKey()
        if (key.isBlank()) {
            _uiState.update {
                it.copy(
                    showApiKeyDialog = true,
                    errorBanner = "Please enter your Gemini API key first."
                )
            }
            return null
        }
        return key
    }

    fun currentLipAmplitude(): Float {
        val state = _uiState.value
        if (!state.isPlaying) return 0f
        val proj = state.project
        if (state.playheadMs >= proj.shotsDurationMs) return 0f
        var acc = 0L
        for (shot in proj.shots) {
            val end = acc + shot.durationMs
            if (state.playheadMs < end) {
                if (shot.speaker == SpeakerSlot.SILENT) return 0f
                val localMs = (state.playheadMs - acc).coerceAtLeast(0L)
                val env = shotEnvelopeMap[shot.shotId]
                if (env != null && env.isNotEmpty()) {
                    val speed = proj.characterFor(shot.speaker)?.voiceSpeed ?: 1.0f
                    val frameIdx = ((localMs * 30L * speed) / 1000f).toInt().coerceIn(0, env.lastIndex)
                    return env[frameIdx]
                }
                return if (shot.dialogue.isNotBlank()) {
                    (0.45f + 0.45f * sin((localMs / 1000f) * 24f)).coerceIn(0f, 1f)
                } else 0f
            }
            acc = end
        }
        return 0f
    }

    fun dismissBanners() {
        _uiState.update { it.copy(statusBanner = null, errorBanner = null) }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pausePlayback() else startPlayback()
    }

    fun startPlayback() {
        playbackLaunchJob?.cancel()
        playbackLaunchJob = viewModelScope.launch {
            val hasMissingVoices = _uiState.value.project.shots.any {
                it.speaker != SpeakerSlot.SILENT && it.dialogue.isNotBlank() && !shotAudioMap.containsKey(it.shotId)
            }
            if (hasMissingVoices) {
                voiceJob?.cancel()
                ensureMissingVoicesInternal(preferGeminiIfKeySet = false)
            }

            val state = _uiState.value
            val total = state.project.totalDurationMs
            val startFrom = if (state.playheadMs >= total - 50L) 0L else state.playheadMs
            _uiState.update { it.copy(isPlaying = true, playheadMs = startFrom, errorBanner = null) }

            val mixedPcm = ProceduralAudioEngine.mixReelAudio(
                project = state.project,
                shotAudioMap = shotAudioMap,
                isMuted = state.isMuted
            )
            livePlayer.playFromMs(viewModelScope, mixedPcm, startFrom)

            val startWallMs = SystemClock.elapsedRealtime() - startFrom
            playLoopJob?.cancel()
            playLoopJob = viewModelScope.launch {
                while (isActive) {
                    val elapsedMs = SystemClock.elapsedRealtime() - startWallMs
                    if (elapsedMs >= total) {
                        _uiState.update { it.copy(playheadMs = 0L, isPlaying = false) }
                        livePlayer.stop()
                        break
                    } else {
                        _uiState.update { it.copy(playheadMs = elapsedMs) }
                    }
                    delay(28L)
                }
            }
        }
    }

    fun pausePlayback() {
        playbackLaunchJob?.cancel()
        playbackLaunchJob = null
        playLoopJob?.cancel()
        playLoopJob = null
        livePlayer.stop()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun restartPlayback() {
        pausePlayback()
        _uiState.update { it.copy(playheadMs = 0L) }
        startPlayback()
    }

    fun seekTo(ms: Long) {
        val wasPlaying = _uiState.value.isPlaying
        pausePlayback()
        val clamped = ms.coerceIn(0L, _uiState.value.project.totalDurationMs)
        _uiState.update { it.copy(playheadMs = clamped) }
        if (wasPlaying) startPlayback()
    }

    fun toggleMute() {
        val nextMute = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = nextMute) }
        if (_uiState.value.isPlaying) {
            pausePlayback()
            startPlayback()
        }
    }

    fun toggleCaptions() = _uiState.update { it.copy(showCaptions = !it.showCaptions) }
    fun toggleSpeechBubbles() = _uiState.update { it.copy(showSpeechBubbles = !it.showSpeechBubbles) }

    fun setStageViewMode(mode: StageViewMode) {
        if (mode == StageViewMode.YOUR_REEL || _uiState.value.referenceVideoUri != null) {
            _uiState.update { it.copy(stageViewMode = mode) }
        }
    }

    fun selectShot(shotId: String) {
        val shots = _uiState.value.project.shots
        var acc = 0L
        for (s in shots) {
            if (s.shotId == shotId) {
                pausePlayback()
                _uiState.update { it.copy(selectedShotId = shotId, playheadMs = acc + 120L) }
                return
            }
            acc += s.durationMs
        }
    }

    // --- AI Creation Workflows ---

    fun brainstormHooks(niche: String, twist: String, language: String) {
        val key = requireApiKey() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBrainstorming = true, errorBanner = null) }
            val result = geminiService.brainstormHooks(key, niche, twist, language)
            result.onSuccess { outcome ->
                _uiState.update {
                    it.copy(
                        isBrainstorming = false,
                        brainstormedHooks = outcome.data,
                        statusBanner = if (outcome.usedFallback) {
                            "${outcome.fallbackReason} — loaded ${outcome.data.size} instant comedy hooks."
                        } else {
                            "Generated ${outcome.data.size} viral comedy hooks."
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBrainstorming = false,
                        errorBanner = err.localizedMessage ?: "Failed to brainstorm hooks."
                    )
                }
            }
        }
    }

    fun generateReelFromNiche(niche: String, twist: String, language: String, hook: String) {
        val key = requireApiKey() ?: return
        pausePlayback()
        voiceJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingReel = true,
                    errorBanner = null,
                    statusBanner = "Writing stickman comedy storyboard with Gemini..."
                )
            }
            val result = geminiService.generateStoryboardFromNiche(
                apiKey = key,
                niche = niche,
                twist = twist,
                language = language,
                hook = hook,
                clonedBlueprint = _uiState.value.clonedStyleBlueprint,
                baseProject = _uiState.value.project
            )
            result.onSuccess { outcome ->
                val newProject = outcome.data
                shotAudioMap.clear()
                shotEnvelopeMap.clear()
                _uiState.update {
                    it.copy(
                        project = newProject,
                        selectedShotId = newProject.shots.firstOrNull()?.shotId.orEmpty(),
                        hasGeneratedReel = true,
                        lastMp4Export = null,
                        playheadMs = 0L,
                        stageViewMode = StageViewMode.YOUR_REEL,
                        isGeneratingReel = false,
                        errorBanner = null,
                        statusBanner = if (outcome.usedFallback) {
                            "${outcome.fallbackReason} — generated instant comedy reel! Preparing cast voices..."
                        } else {
                            "Video storyboard ready! Synthesizing cast voices..."
                        }
                    )
                }
                synthesizeAllSpokenShots()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingReel = false,
                        errorBanner = err.localizedMessage ?: "Storyboard generation failed."
                    )
                }
            }
        }
    }

    fun convertScriptToReel(scriptText: String, settingHint: RoomSetting?, language: String) {
        if (scriptText.isBlank()) {
            _uiState.update { it.copy(errorBanner = "Paste or load a dialogue script first.") }
            return
        }
        val key = repository.getApiKey()
        pausePlayback()
        voiceJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingReel = true,
                    errorBanner = null,
                    statusBanner = if (key.isNotBlank()) "Converting full script into shots..." else "Parsing full script into shots..."
                )
            }
            val result = geminiService.convertScriptToStoryboard(
                apiKey = key,
                scriptText = scriptText,
                settingHint = settingHint,
                language = language,
                baseProject = _uiState.value.project
            )
            result.onSuccess { newProject ->
                shotAudioMap.clear()
                shotEnvelopeMap.clear()
                _uiState.update {
                    it.copy(
                        project = newProject,
                        selectedShotId = newProject.shots.firstOrNull()?.shotId.orEmpty(),
                        hasGeneratedReel = true,
                        lastMp4Export = null,
                        playheadMs = 0L,
                        stageViewMode = StageViewMode.YOUR_REEL,
                        isGeneratingReel = false,
                        statusBanner = "All ${newProject.shots.size} shots ready! Voicing full script..."
                    )
                }
                synthesizeAllSpokenShots()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingReel = false,
                        errorBanner = err.localizedMessage ?: "Script conversion failed."
                    )
                }
            }
        }
    }

    fun analyzeReferenceClip(context: Context, uri: Uri) {
        val key = requireApiKey() ?: return
        _uiState.update { it.copy(referenceVideoUri = uri, isAnalyzingStyle = true, errorBanner = null) }
        viewModelScope.launch {
            val result = geminiService.analyzeReferenceVideoStyle(key, context.applicationContext, uri)
            result.onSuccess { blueprint ->
                _uiState.update {
                    it.copy(
                        isAnalyzingStyle = false,
                        clonedStyleBlueprint = blueprint,
                        project = it.project.copy(clonedStyleSummary = blueprint.summary),
                        statusBanner = "Style blueprint extracted! Ready to apply in Niche Mode."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAnalyzingStyle = false,
                        errorBanner = err.localizedMessage ?: "Video style analysis failed."
                    )
                }
            }
        }
    }

    // --- Voice Synthesis (Gemini TTS + Automatic Device TTS Fallback) & Mic Dubbing ---

    fun synthesizeAllSpokenShots() {
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            val key = repository.getApiKey()
            val spokenShots = _uiState.value.project.shots.filter {
                it.speaker != SpeakerSlot.SILENT && it.dialogue.isNotBlank()
            }
            if (spokenShots.isEmpty()) {
                _uiState.update { it.copy(isSynthesizingAllVoices = false) }
                return@launch
            }

            _uiState.update { it.copy(isSynthesizingAllVoices = true, errorBanner = null) }
            val geminiCount = AtomicInteger(0)
            val deviceCount = AtomicInteger(0)
            val synthCount = AtomicInteger(0)
            val geminiAllowed = AtomicBoolean(key.isNotBlank())
            val limiter = Semaphore(permits = 4)

            try {
                coroutineScope {
                    spokenShots.map { shot ->
                        async {
                            if (!isActive) return@async
                            limiter.withPermit {
                                if (!isActive) return@withPermit
                                when (synthesizeShotWithFallback(
                                    apiKey = if (geminiAllowed.get()) key else "",
                                    shotId = shot.shotId,
                                    geminiTtsAllowed = geminiAllowed
                                )) {
                                    VoiceSourceResult.GEMINI -> geminiCount.incrementAndGet()
                                    VoiceSourceResult.DEVICE -> deviceCount.incrementAndGet()
                                    VoiceSourceResult.CARTOON_SYNTH -> synthCount.incrementAndGet()
                                    VoiceSourceResult.FAILED -> Unit
                                }
                            }
                        }
                    }.awaitAll()
                }

                val gCount = geminiCount.get()
                val dCount = deviceCount.get()
                val cCount = synthCount.get()
                val totalVoiced = gCount + dCount + cCount
                _uiState.update {
                    it.copy(
                        hasGeneratedReel = true,
                        errorBanner = null,
                        statusBanner = when {
                            gCount > 0 && dCount == 0 && cCount == 0 ->
                                "Voiced all $gCount shots with Gemini AI Voice! Tap Play or Download MP4."
                            totalVoiced > 0 ->
                                "Voiced all $totalVoiced shots ($gCount Gemini, $dCount Device, $cCount Synth). Ready to Play or Download!"
                            else -> it.statusBanner
                        }
                    )
                }
            } finally {
                _uiState.update { it.copy(isSynthesizingAllVoices = false) }
            }
        }
    }

    fun revoiceShot(shotId: String) {
        viewModelScope.launch {
            val key = repository.getApiKey()
            val outcome = synthesizeShotWithFallback(
                apiKey = key,
                shotId = shotId,
                geminiTtsAllowed = AtomicBoolean(key.isNotBlank())
            )
            if (outcome != VoiceSourceResult.FAILED) {
                val label = when (outcome) {
                    VoiceSourceResult.GEMINI -> "Gemini AI Voice"
                    VoiceSourceResult.DEVICE -> "Device Voice"
                    VoiceSourceResult.CARTOON_SYNTH -> "Expressive Cartoon Synth"
                    VoiceSourceResult.FAILED -> ""
                }
                _uiState.update {
                    it.copy(
                        lastMp4Export = null,
                        errorBanner = null,
                        statusBanner = "Shot voiced ($label) & lip-synced."
                    )
                }
            }
        }
    }

    private suspend fun ensureMissingVoicesInternal(preferGeminiIfKeySet: Boolean) {
        val key = if (preferGeminiIfKeySet) repository.getApiKey() else ""
        val shots = _uiState.value.project.shots
        val missing = shots.filter {
            it.speaker != SpeakerSlot.SILENT && it.dialogue.isNotBlank() && !shotAudioMap.containsKey(it.shotId)
        }
        if (missing.isEmpty()) {
            _uiState.update { it.copy(isSynthesizingAllVoices = false) }
            return
        }

        _uiState.update { it.copy(isSynthesizingAllVoices = true) }
        val geminiAllowed = AtomicBoolean(key.isNotBlank())
        val limiter = Semaphore(permits = 4)
        try {
            coroutineScope {
                missing.map { shot ->
                    async {
                        limiter.withPermit {
                            synthesizeShotWithFallback(
                                apiKey = if (geminiAllowed.get()) key else "",
                                shotId = shot.shotId,
                                geminiTtsAllowed = geminiAllowed
                            )
                        }
                    }
                }.awaitAll()
            }
        } finally {
            _uiState.update { it.copy(isSynthesizingAllVoices = false) }
        }
    }

    private enum class VoiceSourceResult { GEMINI, DEVICE, CARTOON_SYNTH, FAILED }

    private fun isFatalGeminiTtsError(message: String?): Boolean {
        val msg = message.orEmpty()
        return msg.contains("no longer available", ignoreCase = true) ||
            msg.contains("not found", ignoreCase = true) ||
            msg.contains("HTTP 404", ignoreCase = true) ||
            msg.contains("HTTP 403", ignoreCase = true) ||
            msg.contains("HTTP 401", ignoreCase = true) ||
            msg.contains("HTTP 429", ignoreCase = true) ||
            msg.contains("API key", ignoreCase = true) ||
            msg.contains("quota", ignoreCase = true) ||
            msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("PERMISSION_DENIED", ignoreCase = true)
    }

    private suspend fun synthesizeShotWithFallback(
        apiKey: String,
        shotId: String,
        geminiTtsAllowed: AtomicBoolean
    ): VoiceSourceResult {
        val current = _uiState.value.project
        val shot = current.shots.firstOrNull { it.shotId == shotId } ?: return VoiceSourceResult.FAILED
        if (shot.speaker == SpeakerSlot.SILENT || shot.dialogue.isBlank()) return VoiceSourceResult.FAILED

        val charProfile = current.characterFor(shot.speaker)
        val voiceName = charProfile?.voiceName ?: "Puck"
        val gender = charProfile?.gender ?: CharacterGender.MALE
        val speed = charProfile?.voiceSpeed ?: 1.0f

        _uiState.update { it.copy(voicingShotIds = it.voicingShotIds + shotId) }

        var resultSource = VoiceSourceResult.FAILED
        try {
            if (apiKey.isNotBlank() && geminiTtsAllowed.get()) {
                val geminiRes = geminiService.synthesizeShotVoice(
                    apiKey = apiKey,
                    dialogue = shot.phoneticHint.ifBlank { shot.dialogue },
                    voiceName = voiceName,
                    moodPose = shot.moodPose
                )
                geminiRes.onSuccess { synth ->
                    applyShotPcm(shotId, synth.pcmBytes, synth.rawDurationMs, speed, "Gemini TTS ($voiceName)")
                    resultSource = VoiceSourceResult.GEMINI
                }.onFailure { err ->
                    if (isFatalGeminiTtsError(err.localizedMessage)) {
                        geminiTtsAllowed.set(false)
                    }
                }
            }

            if (resultSource == VoiceSourceResult.FAILED) {
                val deviceRes = deviceTts.synthesizeShotVoice(
                    dialogue = shot.dialogue,
                    phoneticHint = shot.phoneticHint,
                    gender = gender,
                    language = current.language,
                    moodPose = shot.moodPose
                )
                deviceRes.onSuccess { synth ->
                    val tag = if (gender == CharacterGender.FEMALE) "Device Voice (Girl)" else "Device Voice (Boy)"
                    applyShotPcm(shotId, synth.pcmBytes, synth.rawDurationMs, speed, tag)
                    resultSource = VoiceSourceResult.DEVICE
                }
            }

            // 3rd-tier guaranteed local cartoon voice synth so Device TTS empty audio never breaks reels
            if (resultSource == VoiceSourceResult.FAILED) {
                val fallbackSynth = ProceduralAudioEngine.synthesizeCartoonVoiceFallback(
                    text = shot.dialogue,
                    gender = gender,
                    moodPose = shot.moodPose
                )
                val tag = if (gender == CharacterGender.FEMALE) "Cartoon Synth (Girl)" else "Cartoon Synth (Boy)"
                applyShotPcm(shotId, fallbackSynth.pcmBytes, fallbackSynth.rawDurationMs, speed, tag)
                resultSource = VoiceSourceResult.CARTOON_SYNTH
            }
        } finally {
            _uiState.update { it.copy(voicingShotIds = it.voicingShotIds - shotId) }
        }
        return resultSource
    }

    private suspend fun applyShotPcm(
        shotId: String,
        pcmBytes: ByteArray,
        rawDurationMs: Long,
        speed: Float,
        sourceLabel: String
    ) {
        val current = _uiState.value.project
        val env = withContext(Dispatchers.Default) {
            repository.computeEnvelope30Fps(pcmBytes)
        }
        shotAudioMap[shotId] = pcmBytes
        shotEnvelopeMap[shotId] = env

        val projKey = current.id.ifBlank { "draft" }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveShotAudioPcm(projKey, shotId, pcmBytes)
        }

        // Allow up to 30 seconds per shot so long lines never get cut off mid-sentence
        val adjustedMs = ((rawDurationMs / speed).toLong() + 350L).coerceIn(1100L, 30000L)
        _uiState.update { st ->
            val updatedShots = st.project.shots.map {
                if (it.shotId == shotId) {
                    it.copy(durationMs = adjustedMs, audioSourceLabel = sourceLabel)
                } else it
            }
            st.copy(project = st.project.copy(shots = updatedShots))
        }
    }

    fun startMicRecording(shotId: String) {
        pausePlayback()
        _uiState.update { it.copy(recordingShotId = shotId, errorBanner = null) }
        micRecorder.startRecording(viewModelScope) { msg ->
            _uiState.update { it.copy(recordingShotId = null, errorBanner = msg) }
        }
    }

    fun stopMicRecording(shotId: String) {
        val pcm = micRecorder.stopRecording()
        _uiState.update { it.copy(recordingShotId = null) }
        if (pcm.size < 1600) {
            _uiState.update { it.copy(errorBanner = "Recording was too short. Speak clearly and try again.") }
            return
        }
        viewModelScope.launch {
            shotAudioMap[shotId] = pcm
            shotEnvelopeMap[shotId] = repository.computeEnvelope30Fps(pcm)
            val current = _uiState.value.project
            repository.saveShotAudioPcm(current.id.ifBlank { "draft" }, shotId, pcm)
            val durMs = ((pcm.size.toDouble() / 48000.0) * 1000.0).toLong().coerceIn(900L, 30000L)
            _uiState.update { st ->
                val updated = st.project.shots.map {
                    if (it.shotId == shotId) it.copy(durationMs = durMs + 250L, audioSourceLabel = "Mic Dub") else it
                }
                st.copy(
                    project = st.project.copy(shots = updated),
                    lastMp4Export = null,
                    statusBanner = "Mic dub recorded & lip-synced (${String.format("%.1fs", durMs / 1000f)})."
                )
            }
        }
    }

    fun previewSfx(sfx: ComicSfx) {
        if (sfx == ComicSfx.NONE) return
        livePlayer.playSfxPreview(viewModelScope, sfx)
    }

    // --- Shot & Cast Mutations ---

    fun updateShot(updated: ReelShot) {
        val oldShot = _uiState.value.project.shots.firstOrNull { it.shotId == updated.shotId }
        if (oldShot != null && (oldShot.dialogue != updated.dialogue || oldShot.phoneticHint != updated.phoneticHint || oldShot.speaker != updated.speaker)) {
            shotAudioMap.remove(updated.shotId)
            shotEnvelopeMap.remove(updated.shotId)
        }
        _uiState.update { st ->
            st.copy(
                lastMp4Export = null,
                project = st.project.copy(
                    shots = st.project.shots.map { if (it.shotId == updated.shotId) updated else it }
                )
            )
        }
    }

    fun addShot() {
        val current = _uiState.value.project
        if (current.shots.size >= ReelProject.MAX_SHOTS) {
            _uiState.update { it.copy(errorBanner = "Maximum ${ReelProject.MAX_SHOTS} shots per reel.") }
            return
        }
        val newShot = ReelShot(
            shotId = "shot_${UUID.randomUUID().toString().take(6)}",
            speaker = SpeakerSlot.B,
            dialogue = "Wait, seriously?!",
            speechBubble = "SERIOUSLY?!"
        )
        _uiState.update { st ->
            st.copy(
                lastMp4Export = null,
                project = st.project.copy(shots = st.project.shots + newShot),
                selectedShotId = newShot.shotId
            )
        }
    }

    fun deleteShot(shotId: String) {
        val current = _uiState.value.project
        if (current.shots.size <= 1) return
        shotAudioMap.remove(shotId)
        shotEnvelopeMap.remove(shotId)
        val remaining = current.shots.filterNot { it.shotId == shotId }
        _uiState.update { st ->
            st.copy(
                lastMp4Export = null,
                project = st.project.copy(shots = remaining),
                selectedShotId = remaining.firstOrNull()?.shotId.orEmpty()
            )
        }
    }

    fun updateCharacter(index: Int, profile: CharacterProfile) {
        val oldChar = _uiState.value.project.characters.getOrNull(index)
        var patchedShots = _uiState.value.project.shots
        if (oldChar != null) {
            if (oldChar.gender != profile.gender || oldChar.voiceName != profile.voiceName) {
                patchedShots.filter { it.speaker == profile.slot }.forEach { s ->
                    shotAudioMap.remove(s.shotId)
                    shotEnvelopeMap.remove(s.shotId)
                }
            } else if (oldChar.voiceSpeed != profile.voiceSpeed) {
                // Re-adjust shot durations immediately so slow voiceSpeed never cuts off audio
                patchedShots = patchedShots.map { s ->
                    if (s.speaker == profile.slot) {
                        val pcm = shotAudioMap[s.shotId]
                        if (pcm != null) {
                            val rawMs = ((pcm.size.toDouble() / 48000.0) * 1000.0).toLong()
                            val newDur = ((rawMs / profile.voiceSpeed).toLong() + 350L).coerceIn(1100L, 30000L)
                            s.copy(durationMs = newDur)
                        } else s
                    } else s
                }
            }
        }
        _uiState.update { st ->
            val chars = st.project.characters.toMutableList()
            if (index in chars.indices) {
                chars[index] = profile
            }
            st.copy(lastMp4Export = null, project = st.project.copy(characters = chars, shots = patchedShots))
        }
    }

    fun updateProjectSettings(
        title: String? = null,
        setting: RoomSetting? = null,
        colorTheme: ReelColorTheme? = null,
        bgmMood: BgmMood? = null,
        bgmVolume: Float? = null,
        captionStyle: CaptionStyle? = null,
        captionFontStyle: CaptionFontStyle? = null,
        captionPosition: CaptionPosition? = null,
        introHookBanner: String? = null,
        enableOutro: Boolean? = null,
        outroText: String? = null,
        watermarkHandle: String? = null,
        exportResolution: ExportResolution? = null
    ) {
        if (watermarkHandle != null) {
            repository.saveWatermarkHandle(watermarkHandle)
        }
        _uiState.update { st ->
            val p = st.project
            st.copy(
                lastMp4Export = null,
                project = p.copy(
                    title = title ?: p.title,
                    setting = setting ?: p.setting,
                    colorTheme = colorTheme ?: p.colorTheme,
                    bgmMood = bgmMood ?: p.bgmMood,
                    bgmVolume = bgmVolume ?: p.bgmVolume,
                    captionStyle = captionStyle ?: p.captionStyle,
                    captionFontStyle = captionFontStyle ?: p.captionFontStyle,
                    captionPosition = captionPosition ?: p.captionPosition,
                    introHookBanner = introHookBanner ?: p.introHookBanner,
                    enableOutro = enableOutro ?: p.enableOutro,
                    outroText = outroText ?: p.outroText,
                    watermarkHandle = watermarkHandle?.take(36) ?: p.watermarkHandle,
                    exportResolution = exportResolution ?: p.exportResolution
                )
            )
        }
    }

    // --- Local Saved Reels Persistence ---

    fun saveCurrentReel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingReel = true, errorBanner = null) }
            val proj = _uiState.value.project
            val res = repository.saveReel(proj)
            res.onSuccess { savedId ->
                _uiState.update {
                    it.copy(
                        isSavingReel = false,
                        project = it.project.copy(id = savedId),
                        statusBanner = "Saved to your Saved Reels library."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingReel = false,
                        errorBanner = err.localizedMessage ?: "Failed to save reel."
                    )
                }
            }
        }
    }

    fun loadSavedReel(reel: ReelProject) {
        pausePlayback()
        voiceJob?.cancel()
        viewModelScope.launch {
            shotAudioMap.clear()
            shotEnvelopeMap.clear()
            for (s in reel.shots) {
                val pcm = repository.loadShotAudioPcm(reel.id.ifBlank { "draft" }, s.shotId)
                if (pcm != null) {
                    shotAudioMap[s.shotId] = pcm
                    shotEnvelopeMap[s.shotId] = repository.computeEnvelope30Fps(pcm)
                }
            }
            val patchedShots = reel.shots.map { s ->
                if (shotAudioMap.containsKey(s.shotId)) s.copy(audioSourceLabel = "Cached Audio") else s
            }
            _uiState.update {
                it.copy(
                    project = reel.copy(shots = patchedShots),
                    selectedShotId = patchedShots.firstOrNull()?.shotId.orEmpty(),
                    hasGeneratedReel = true,
                    lastMp4Export = null,
                    playheadMs = 0L,
                    stageViewMode = StageViewMode.YOUR_REEL,
                    statusBanner = "Loaded \"${reel.title}\"."
                )
            }
            ensureMissingVoicesInternal(preferGeminiIfKeySet = false)
        }
    }

    fun duplicateSavedReel(reel: ReelProject) {
        viewModelScope.launch {
            val res = repository.duplicateReel(reel)
            res.onSuccess {
                _uiState.update { it.copy(statusBanner = "Duplicated \"${reel.title}\".") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.localizedMessage ?: "Duplicate failed.") }
            }
        }
    }

    fun deleteSavedReel(reelId: String) {
        viewModelScope.launch {
            val res = repository.deleteReel(reelId)
            res.onSuccess {
                _uiState.update {
                    val clearedCurrent = if (it.project.id == reelId) it.project.copy(id = "") else it.project
                    it.copy(project = clearedCurrent, statusBanner = "Deleted saved reel.")
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.localizedMessage ?: "Delete failed.") }
            }
        }
    }

    // --- Export MP4 & PNG Thumbnail ---

    fun exportMp4(context: Context) {
        pausePlayback()
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingMp4 = true, exportProgress = 0.02f, errorBanner = null) }
            try {
                voiceJob?.cancel()
                ensureMissingVoicesInternal(preferGeminiIfKeySet = false)

                val state = _uiState.value
                val res = Mp4Exporter.exportMp4(
                    context = context.applicationContext,
                    project = state.project,
                    shotAudioMap = shotAudioMap.toMap(),
                    isMuted = state.isMuted,
                    showCaptions = state.showCaptions,
                    showSpeechBubbles = state.showSpeechBubbles,
                    onProgress = { p -> _uiState.update { it.copy(exportProgress = p) } }
                )
                res.onSuccess { exp ->
                    _uiState.update {
                        it.copy(
                            exportProgress = 1f,
                            lastExportResult = exp,
                            lastMp4Export = exp,
                            statusBanner = "Video downloaded to ${exp.displayPath}"
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            exportProgress = 0f,
                            errorBanner = err.localizedMessage ?: "MP4 export failed."
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isExportingMp4 = false) }
            }
        }
    }

    fun saveThumbnailPng(context: Context) {
        val state = _uiState.value
        val amp = currentLipAmplitude()
        viewModelScope.launch {
            val res = Mp4Exporter.savePngThumbnail(
                context = context.applicationContext,
                project = state.project,
                playheadMs = state.playheadMs,
                lipAmplitude = amp
            )
            res.onSuccess { exp ->
                _uiState.update {
                    it.copy(
                        lastExportResult = exp,
                        statusBanner = "PNG thumbnail saved to ${exp.displayPath}"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.localizedMessage ?: "PNG save failed.") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceJob?.cancel()
        playbackLaunchJob?.cancel()
        playLoopJob?.cancel()
        livePlayer.stop()
        micRecorder.stopRecording()
        deviceTts.shutdown()
    }
}
