package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.example.ai.GeminiInkReelService
import com.example.audio.DeviceTtsSynthesizer
import com.example.audio.HinglishPhoneticTuner
import com.example.audio.ProceduralAudioEngine
import com.example.canvas.StickmanRenderer
import com.example.data.ReelRepository
import com.example.model.CameraFraming
import com.example.model.CharacterActivity
import com.example.model.CharacterGender
import com.example.model.MoodPose
import com.example.model.NichePreset
import com.example.model.ReelColorTheme
import com.example.model.ReelProject
import com.example.model.RoomSetting
import com.example.model.SampleScripts
import com.example.model.SpeakerSlot
import com.example.viewmodel.InkReelViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InkReelBugFixTest {

    @Test
    fun geminiService_stripsJsonFencesCleanly() {
        val service = GeminiInkReelService()
        val rawFenced = "```json\n{\"title\":\"Test\"}\n```"
        assertEquals("{\"title\":\"Test\"}", service.stripJsonCodeFences(rawFenced))
        assertEquals("{\"ok\":true}", service.stripJsonCodeFences("{\"ok\":true}"))
    }

    @Test
    fun geminiService_offlineHooksAndFallbackStoryboardNeverEmpty() {
        val service = GeminiInkReelService()
        val hooks = service.buildOfflineHooks("Gym & Trainer", "Rule of 3", "Hinglish")
        assertEquals(5, hooks.size)
        assertTrue(hooks.all { it.isNotBlank() })

        val base = ReelProject(watermarkHandle = "@creator_prince")
        NichePreset.entries.forEach { preset ->
            val proj = service.buildFallbackNicheStoryboard(
                niche = preset.label,
                twist = "Plot Twist",
                language = "Hinglish",
                hook = "",
                clonedSummary = "",
                baseProject = base
            )
            assertTrue("Shots should be >= 6 for ${preset.label}", proj.shots.size >= 6)
            assertEquals("@creator_prince", proj.watermarkHandle)
            assertTrue(proj.characters.any { it.gender == CharacterGender.FEMALE })
            assertTrue(proj.shots.any { it.activity != CharacterActivity.TALKING })
        }
    }

    @Test
    fun localScriptParser_preservesAllLinesAndDetectsTimeSkipAndFemaleCast() {
        val service = GeminiInkReelService()
        val proj = service.parseScriptLocally(
            scriptText = SampleScripts.hinglishSample,
            settingHint = null,
            language = "Hinglish",
            baseProject = ReelProject()
        )
        // SampleScripts.hinglishSample has 7 non-empty lines including [Day 3]
        assertEquals(7, proj.shots.size)
        assertEquals(CameraFraming.TIME_SKIP_CARD, proj.shots[3].framing)
        assertEquals(SpeakerSlot.SILENT, proj.shots[3].speaker)
        assertEquals(CharacterGender.FEMALE, proj.characters[1].gender)
    }

    @Test
    fun audioEngine_cartoonVoiceFallbackAndMixNeverEmpty() {
        val maleSynth = ProceduralAudioEngine.synthesizeCartoonVoiceFallback(
            text = "Arre bhai homework kahan gaya?",
            gender = CharacterGender.MALE,
            moodPose = MoodPose.ANGRY
        )
        val femaleSynth = ProceduralAudioEngine.synthesizeCartoonVoiceFallback(
            text = "Sir dog ne PDF download kar liya!",
            gender = CharacterGender.FEMALE,
            moodPose = MoodPose.SMUG
        )
        assertTrue(maleSynth.pcmBytes.size >= 48000)
        assertTrue(femaleSynth.pcmBytes.size >= 48000)
        assertTrue(maleSynth.rawDurationMs >= 1100L)

        val tuned = HinglishPhoneticTuner.tune("Kyun nahi aaye?", "", "Hinglish")
        assertTrue(tuned.contains("kyoon", ignoreCase = true))

        val proj = ReelProject()
        val audioMap = proj.shots
            .filter { it.speaker != SpeakerSlot.SILENT && it.dialogue.isNotBlank() }
            .associate { it.shotId to femaleSynth.pcmBytes }
        val mixed = ProceduralAudioEngine.mixReelAudio(proj, audioMap, isMuted = false)
        assertTrue(mixed.size > 48000)
        assertTrue(mixed.any { it != 0.toByte() })
    }

    @Test
    fun stickmanRenderer_rendersAllActivitiesFramingsAndThemesWithoutClipBleed() {
        val width = 360
        val height = 640
        // Oversized parent canvas to ensure bounded renderFrame never paints outside (0..width, 0..height)
        val outerBmp = Bitmap.createBitmap(width + 40, height + 40, Bitmap.Config.ARGB_8888)
        outerBmp.eraseColor(android.graphics.Color.MAGENTA)
        val canvas = Canvas(outerBmp)

        val base = ReelProject(watermarkHandle = "prince_reels")
        CameraFraming.entries.forEachIndexed { idx, framing ->
            val activity = CharacterActivity.entries[idx % CharacterActivity.entries.size]
            val theme = ReelColorTheme.entries[idx % ReelColorTheme.entries.size]
            val setting = RoomSetting.entries[idx % RoomSetting.entries.size]
            val shot = base.shots.first().copy(
                framing = framing,
                activity = activity,
                speaker = if (framing == CameraFraming.TIME_SKIP_CARD) SpeakerSlot.SILENT else SpeakerSlot.B
            )
            val testProj = base.copy(
                colorTheme = theme,
                setting = setting,
                shots = listOf(shot)
            )
            canvas.save()
            canvas.clipRect(0, 0, width, height)
            StickmanRenderer.renderFrame(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat(),
                project = testProj,
                playheadMs = 450L,
                lipAmplitude = 0.65f,
                showCaptions = true,
                showSpeechBubbles = true
            )
            canvas.restore()
            // Pixel outside (width, height) must remain untouched MAGENTA
            assertEquals(android.graphics.Color.MAGENTA, outerBmp.getPixel(width + 20, height + 20))
        }

        // Also test unbounded canvas clear does NOT overwrite outside (0..width, 0..height)
        outerBmp.eraseColor(android.graphics.Color.MAGENTA)
        StickmanRenderer.renderFrame(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            project = base,
            playheadMs = 100L,
            lipAmplitude = 0.5f,
            showCaptions = true,
            showSpeechBubbles = true
        )
        assertEquals(android.graphics.Color.MAGENTA, outerBmp.getPixel(width + 20, height + 20))
        outerBmp.recycle()
    }

    @Test
    fun repositoryAndViewModel_watermarkAndVoiceFallbackNeverLeaveErrorOrStuckProgress() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ReelRepository(context)
        val service = GeminiInkReelService()
        val tts = DeviceTtsSynthesizer(context, initTimeoutMs = 120L, synthTimeoutMs = 120L)
        val vm = InkReelViewModel(repo, service, tts)

        vm.updateWatermarkUsername("prince_comedy")
        assertEquals("prince_comedy", repo.getWatermarkHandle())
        assertEquals("prince_comedy", vm.uiState.value.project.watermarkHandle)

        // Pump Main looper so init warmUp + Dispatchers.Main coroutines advance cleanly
        var waited = 0
        while (waited < 40) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (!vm.uiState.value.isSynthesizingAllVoices &&
                vm.uiState.value.project.shots.filter { it.speaker != SpeakerSlot.SILENT && it.dialogue.isNotBlank() }
                    .all { it.audioSourceLabel != "Not Voiced" }
            ) {
                break
            }
            delay(100)
            waited++
        }

        // Trigger explicit full re-voice (also tests cancelling/restarting voiceJob) and wait cleanly
        vm.synthesizeAllSpokenShots()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        var revoiceWait = 0
        while (revoiceWait < 40) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (!vm.uiState.value.isSynthesizingAllVoices &&
                vm.uiState.value.project.shots.filter { it.speaker != SpeakerSlot.SILENT && it.dialogue.isNotBlank() }
                    .all { it.audioSourceLabel != "Not Voiced" }
            ) {
                break
            }
            delay(100)
            revoiceWait++
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val state = vm.uiState.value
        assertFalse("Voice prep should never get stuck true", state.isSynthesizingAllVoices)
        assertNull("Voice synthesis should never show Device TTS empty audio error", state.errorBanner)
        assertTrue(state.voicingShotIds.isEmpty())

        val voicedShots = state.project.shots.filter { it.speaker != SpeakerSlot.SILENT && it.dialogue.isNotBlank() }
        assertTrue(voicedShots.all { it.audioSourceLabel != "Not Voiced" })

        val saveRes = repo.saveReel(state.project)
        assertTrue(saveRes.isSuccess)
        assertNotNull(saveRes.getOrNull())
    }
}
