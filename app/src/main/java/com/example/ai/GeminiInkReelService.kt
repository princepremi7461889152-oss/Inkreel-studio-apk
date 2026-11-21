package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.model.BgmMood
import com.example.model.CameraFraming
import com.example.model.CharacterActivity
import com.example.model.CharacterGender
import com.example.model.CharacterProfile
import com.example.model.CharacterProp
import com.example.model.ClonedStyleBlueprint
import com.example.model.ComicSfx
import com.example.model.FacialHair
import com.example.model.HairStyle
import com.example.model.MemeSticker
import com.example.model.MoodPose
import com.example.model.ReelProject
import com.example.model.ReelShot
import com.example.model.RoomSetting
import com.example.model.SpeakerSlot
import com.example.model.VoiceCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class SynthesizedAudioResult(
    val pcmBytes: ByteArray,
    val rawDurationMs: Long
)

data class GenerationOutcome<T>(
    val data: T,
    val usedFallback: Boolean = false,
    val fallbackReason: String? = null
)

class GeminiInkReelService {

    suspend fun brainstormHooks(
        apiKey: String,
        niche: String,
        twist: String,
        language: String
    ): Result<GenerationOutcome<List<String>>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Please enter your Gemini API key first."))
        }
        try {
            val prompt = """
                You are a viral 9:16 stickman comedy short writer.
                Brainstorm 5 hilarious, hyper-relatable, punchy comedy reel hooks/premises.
                Niche: $niche
                Comedy Twist Structure: $twist
                Language: $language (if Hinglish, write natural Indian Romanized Hindi+English mix; if Hindi, write clear Hindi).
                Make each hook 1-2 crisp sentences with a clear setup and comedic twist potential.
            """.trimIndent()

            val schema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("hooks", JSONObject().apply {
                        put("type", "ARRAY")
                        put("items", JSONObject().apply { put("type", "STRING") })
                    })
                })
                put("required", JSONArray(listOf("hooks")))
            }

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema)
                })
            }

            val responseJson = withTimeout(52_000L) {
                callGeminiTextWithFallback(apiKey, body)
            }
            val text = extractTextFromResponse(responseJson)
            val parsed = JSONObject(text)
            val arr = parsed.getJSONArray("hooks")
            val list = (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
            Result.success(GenerationOutcome(list.take(5)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "brainstormHooks Gemini unavailable, using instant hooks fallback", e)
            if (isRecoverableBusyOrNetworkError(e)) {
                Result.success(
                    GenerationOutcome(
                        data = buildOfflineHooks(niche, twist, language),
                        usedFallback = true,
                        fallbackReason = summarizeTransientReason(e)
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun generateStoryboardFromNiche(
        apiKey: String,
        niche: String,
        twist: String,
        language: String,
        hook: String,
        clonedBlueprint: ClonedStyleBlueprint?,
        baseProject: ReelProject
    ): Result<GenerationOutcome<ReelProject>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Please enter your Gemini API key first."))
        }
        try {
            val blueprintNote = if (clonedBlueprint != null) {
                """
                Apply this cloned reference style blueprint:
                - Summary: ${clonedBlueprint.summary}
                - Target avg shot duration: ${clonedBlueprint.averageShotDurationMs}ms
                - Pacing: ${clonedBlueprint.pacingStyle}
                - Preferred framings: ${clonedBlueprint.recommendedFramings.joinToString(", ")}
                - Comedy formula: ${clonedBlueprint.comedyFormula}
                """.trimIndent()
            } else {
                ""
            }

            val freshTwistAngles = listOf(
                "unexpected billing/receipt twist",
                "caught on accidental speakerphone",
                "literal misunderstanding of instructions",
                "hidden camera goes viral mid-argument",
                "role reversal where the junior outsmarts the senior",
                "fake confidence exposed by an absurd prop",
                "dramatic courtroom-style interrogation over a silly item",
                "shortcut backfires and accidentally succeeds"
            )
            val chosenAngle = freshTwistAngles[storyVariationCounter.getAndIncrement() % freshTwistAngles.size]
            val recentNote = if (recentGeneratedTitles.isNotEmpty()) {
                "Do NOT repeat these previous titles or plots: ${recentGeneratedTitles.joinToString(" | ")}."
            } else {
                ""
            }

            val prompt = """
                Create a brand-new, unique 9:16 monochrome stickman comedy reel storyboard with 6 to 9 timed shots.
                Niche: $niche
                Comedy Twist Structure: $twist
                Mandatory Fresh Comedy Angle: $chosenAngle
                Language: $language (if Hinglish, write punchy conversational Hinglish in Latin script; if Hindi, write natural Hindi).
                Optional Story Hook: ${hook.ifBlank { "Invent a totally original, hilarious premise specifically for '$niche' using the '$chosenAngle' angle." }}
                $recentNote
                $blueprintNote

                Rules:
                - Define exactly 3 characters (slots A, B, C) with distinct roles, gender (MALE or FEMALE — include female characters whenever natural like mom, sister, girlfriend, female boss/teacher/doctor/friend), matching voices, hair styles (use LONG_WAVY, PONYTAIL, BOB, BUN, or TWIN_BRAIDS for FEMALE), glasses, facial hair (NONE for FEMALE), and props.
                - Make the video lively and action-packed! Assign varied CharacterActivity values across shots (such as WALKING_IN, DANCING, GYM_WORKOUT, PLAYING_CRICKET, COOKING_CHAI, STUDYING_WRITING, GAMING_PHONE, SLEEPING_SNORING, CLAPPING_CHEERING, CRYING_DRAMA, TAKING_SELFIE, DRIVING_RIDING, SWEEPING_CLEANING, TALKING) matching what the character is actually doing in that moment.
                - Use dynamic comedy beats across shots: start with WIDE_DOORWAY or MEDIUM_TWO_SHOT, escalate with CLOSE_UP_A/B, DESK_SLAM, TIME_SKIP_CARD ("NEXT DAY" / "DAY 3"), PROP_DROP, SPLIT_PHONE_CALL, and finish with SLAPSTICK_CHASE or BONK_ENDING!
                - Keep dialogue crisp, snappy, and genuinely funny (max 18 words per shot).
                - Set appropriate ComicSfx per shot (DOOR_SLAM, DESK_SLAM, DRAMATIC_STING, CLIPBOARD_DROP, PUNCHLINE_BOOM, TIME_SKIP_WHOOSH, CARTOON_RUN, CARTOON_BONK, etc.).
                - Pick the best matching RoomSetting and BgmMood.
            """.trimIndent()

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 1.05)
                    put("responseMimeType", "application/json")
                    put("responseSchema", storyboardSchema())
                })
            }

            val responseJson = withTimeout(42_000L) {
                callGeminiTextWithFallback(apiKey, body)
            }
            val rawText = extractTextFromResponse(responseJson)
            val parsed = parseStoryboardJson(
                jsonText = rawText,
                niche = niche,
                twist = twist,
                language = language,
                clonedSummary = clonedBlueprint?.summary.orEmpty(),
                baseProject = baseProject
            )
            rememberRecentTitle(parsed.title)
            Result.success(GenerationOutcome(parsed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "generateStoryboardFromNiche Gemini unavailable, using built-in comedy generator", e)
            if (isRecoverableBusyOrNetworkError(e)) {
                val fallbackProject = buildFallbackNicheStoryboard(
                    niche = niche,
                    twist = twist,
                    language = language,
                    hook = hook,
                    clonedSummary = clonedBlueprint?.summary.orEmpty(),
                    baseProject = baseProject
                )
                Result.success(
                    GenerationOutcome(
                        data = fallbackProject,
                        usedFallback = true,
                        fallbackReason = summarizeTransientReason(e)
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun convertScriptToStoryboard(
        apiKey: String,
        scriptText: String,
        settingHint: RoomSetting?,
        language: String,
        baseProject: ReelProject
    ): Result<ReelProject> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            // Even without an API key, parse the FULL script line-by-line locally so every line speaks!
            return@withContext Result.success(
                parseScriptLocally(scriptText, settingHint, language, baseProject)
            )
        }
        try {
            val settingPrompt = settingHint?.let { "Force setting to: ${it.name}." }
                ?: "Auto-detect the best matching RoomSetting from the dialogue."

            val prompt = """
                Convert this ENTIRE dialogue script into a timed 9:16 monochrome stickman comedy storyboard.
                $settingPrompt
                Language: $language
                CRITICAL RULES:
                1. Preserve EVERY SINGLE dialogue line and speaker turn from the script word-for-word in exact order. NEVER skip, merge, truncate, or summarize any line!
                2. Emit 1 shot per speaker line (up to 40 shots total).
                3. Whenever the script contains tags like [Next Day], [Day 3], or time-skip cues, emit a SILENT shot with framing TIME_SKIP_CARD, transitionCard set to the time-skip text (e.g., "NEXT DAY"), and sfx TIME_SKIP_WHOOSH.
                4. Map speakers cleanly to CharacterProfiles A, B, C (setting gender MALE or FEMALE accurately based on roles/names like Mom/Beti/Girl/Ma'am vs Papa/Sir/Boy), and pick expressive MoodPose, dynamic CharacterActivity (WALKING_IN, DANCING, GYM_WORKOUT, PLAYING_CRICKET, COOKING_CHAI, STUDYING_WRITING, GAMING_PHONE, SLEEPING_SNORING, CLAPPING_CHEERING, CRYING_DRAMA, TAKING_SELFIE, DRIVING_RIDING, SWEEPING_CLEANING, TALKING), CameraFraming (including SPLIT_PHONE_CALL, DESK_SLAM, PROP_DROP, SLAPSTICK_CHASE, BONK_ENDING), MemeSticker, and ComicSfx.
                5. Provide a short viral introHookBanner (max 5 words, e.g. "WAIT FOR THE TWIST!").

                Script:
                $scriptText
            """.trimIndent()

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", storyboardSchema())
                })
            }

            val responseJson = withTimeout(60_000L) {
                callGeminiTextWithFallback(apiKey, body)
            }
            val rawText = extractTextFromResponse(responseJson)
            val parsed = parseStoryboardJson(
                jsonText = rawText,
                niche = "Custom Script",
                twist = "Script Adaptation",
                language = language,
                clonedSummary = baseProject.clonedStyleSummary,
                baseProject = baseProject
            ).let { proj ->
                if (settingHint != null) proj.copy(setting = settingHint) else proj
            }
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini script conversion failed, falling back to full local parser", e)
            Result.success(parseScriptLocally(scriptText, settingHint, language, baseProject))
        }
    }

    fun parseScriptLocally(
        scriptText: String,
        settingHint: RoomSetting?,
        language: String,
        baseProject: ReelProject
    ): ReelProject {
        val lines = scriptText.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(ReelProject.MAX_SHOTS)
        val shots = mutableListOf<ReelShot>()
        val labelToSlot = linkedMapOf<String, SpeakerSlot>()
        val slotsOrder = listOf(SpeakerSlot.A, SpeakerSlot.B, SpeakerSlot.C)
        val detectedCharacters = baseProject.characters.toMutableList()
        while (detectedCharacters.size < 3) {
            detectedCharacters.add(ReelProject.defaultCast()[detectedCharacters.size])
        }

        val timeSkipRegex = Regex("^\\[(.*)]$")
        val speakerLineRegex = Regex("^([A-Za-z0-9_ ()'.-]{1,28})\\s*:\\s*(.+)$")

        for ((idx, line) in lines.withIndex()) {
            val skipMatch = timeSkipRegex.matchEntire(line)
            if (skipMatch != null) {
                shots.add(
                    ReelShot(
                        shotId = "shot_loc_$idx",
                        speaker = SpeakerSlot.SILENT,
                        framing = CameraFraming.TIME_SKIP_CARD,
                        dialogue = "",
                        transitionCard = skipMatch.groupValues[1].ifBlank { "NEXT DAY" }.uppercase(),
                        moodPose = MoodPose.NEUTRAL,
                        sfx = ComicSfx.TIME_SKIP_WHOOSH,
                        durationMs = 1500L
                    )
                )
                continue
            }

            val spkMatch = speakerLineRegex.matchEntire(line)
            val rawSpeaker = spkMatch?.groupValues?.get(1)?.trim().orEmpty()
            val spoken = spkMatch?.groupValues?.get(2)?.trim() ?: line

            val slot = if (rawSpeaker.isNotEmpty()) {
                val normalized = rawSpeaker.uppercase()
                when {
                    normalized.startsWith("A ") || normalized == "A" -> SpeakerSlot.A
                    normalized.startsWith("B ") || normalized == "B" -> SpeakerSlot.B
                    normalized.startsWith("C ") || normalized == "C" -> SpeakerSlot.C
                    else -> labelToSlot.getOrPut(rawSpeaker.lowercase()) {
                        slotsOrder[labelToSlot.size % slotsOrder.size]
                    }
                }
            } else {
                slotsOrder[shots.size % 2]
            }

            // Update character name/gender hint if parsed from script like "B (Beti)" or "Priya"
            if (rawSpeaker.isNotEmpty() && slot != SpeakerSlot.SILENT) {
                val roleMatch = Regex("\\(([^)]+)\\)").find(rawSpeaker)
                val displayName = roleMatch?.groupValues?.get(1)?.trim() ?: rawSpeaker
                val charIdx = when (slot) {
                    SpeakerSlot.A -> 0
                    SpeakerSlot.B -> 1
                    SpeakerSlot.C -> 2
                    SpeakerSlot.SILENT -> -1
                }
                if (charIdx in 0..2 && displayName.length > 1) {
                    val lower = displayName.lowercase()
                    val looksFemale = listOf("mom", "maa", "mummy", "beti", "girl", "sister", "didi", "ma'am", "madam", "wife", "gf", "priya", "ananya", "neha", "pooja", "queen").any { lower.contains(it) }
                    val old = detectedCharacters[charIdx]
                    val targetGender = if (looksFemale) CharacterGender.FEMALE else old.gender
                    detectedCharacters[charIdx] = old.copy(
                        name = displayName,
                        gender = targetGender,
                        voiceName = if (targetGender == CharacterGender.FEMALE && !VoiceCatalog.isFemaleVoice(old.voiceName)) {
                            VoiceCatalog.defaultVoiceFor(CharacterGender.FEMALE)
                        } else old.voiceName,
                        hairStyle = if (targetGender == CharacterGender.FEMALE && old.hairStyle == HairStyle.BALD) {
                            HairStyle.LONG_WAVY
                        } else old.hairStyle,
                        facialHair = if (targetGender == CharacterGender.FEMALE) FacialHair.NONE else old.facialHair
                    )
                }
            }

            val wordCount = spoken.split(Regex("\\s+")).size
            val estimatedMs = (wordCount * 360L + 700L).coerceIn(1800L, 18000L)
            val framing = when {
                idx == 0 -> CameraFraming.WIDE_DOORWAY
                idx == lines.lastIndex && spoken.contains("!") -> CameraFraming.CLOSE_UP_B
                slot == SpeakerSlot.A && idx % 3 == 1 -> CameraFraming.CLOSE_UP_A
                slot == SpeakerSlot.B && idx % 3 == 2 -> CameraFraming.CLOSE_UP_B
                else -> CameraFraming.MEDIUM_TWO_SHOT
            }
            val mood = when {
                spoken.contains("?!") -> MoodPose.SHOCKED
                spoken.contains("!") -> MoodPose.ANGRY
                spoken.contains("?") -> MoodPose.NERVOUS
                else -> MoodPose.NEUTRAL
            }
            val bubble = spoken.split(Regex("\\s+")).take(3).joinToString(" ").uppercase()
            val activity = inferActivityFromText(spoken, idx, settingHint)

            shots.add(
                ReelShot(
                    shotId = "shot_loc_${UUID.randomUUID().toString().take(4)}_$idx",
                    speaker = slot,
                    framing = framing,
                    dialogue = spoken,
                    speechBubble = bubble,
                    moodPose = mood,
                    activity = activity,
                    sfx = if (idx == 0) ComicSfx.DOOR_OPEN else ComicSfx.NONE,
                    durationMs = estimatedMs
                )
            )
        }

        return baseProject.copy(
            id = "",
            title = "Script Reel (${shots.size} shots)",
            language = language,
            setting = settingHint ?: baseProject.setting,
            characters = detectedCharacters,
            shots = shots.ifEmpty { ReelProject.defaultShots() },
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun inferActivityFromText(text: String, idx: Int, setting: RoomSetting?): CharacterActivity {
        val lower = text.lowercase()
        return when {
            listOf("dance", "nach", "naach", "party", "dj", "wedding", "shaadi").any { lower.contains(it) } -> CharacterActivity.DANCING
            listOf("gym", "workout", "dumbbell", "pushup", "squat", "body", "protein", "weight").any { lower.contains(it) } -> CharacterActivity.GYM_WORKOUT
            listOf("cricket", "bat", "ball", "six", "wicket", "match", "out").any { lower.contains(it) } -> CharacterActivity.PLAYING_CRICKET
            listOf("chai", "coffee", "cook", "khana", "maggi", "kitchen", "roti", "tea", "pakora").any { lower.contains(it) } -> CharacterActivity.COOKING_CHAI
            listOf("homework", "study", "padh", "exam", "notebook", "marks", "test", "notes").any { lower.contains(it) } -> CharacterActivity.STUDYING_WRITING
            listOf("game", "bgmi", "pubg", "reel", "wifi", "hotspot", "phone", "scroll", "instagram").any { lower.contains(it) } -> CharacterActivity.GAMING_PHONE
            listOf("sleep", "alarm", "so ja", "neend", "dream", "late", "morning").any { lower.contains(it) } -> CharacterActivity.SLEEPING_SNORING
            listOf("promote", "congrats", "waah", "bravo", "good news", "boss", "startup", "jeet").any { lower.contains(it) } -> CharacterActivity.CLAPPING_CHEERING
            listOf("cry", "ro ", "rona", "fail", "sad", "sorry", "maaf").any { lower.contains(it) } -> CharacterActivity.CRYING_DRAMA
            listOf("selfie", "photo", "vlog", "camera", "video").any { lower.contains(it) } -> CharacterActivity.TAKING_SELFIE
            listOf("car", "bike", "drive", "scooty", "traffic", "auto").any { lower.contains(it) } -> CharacterActivity.DRIVING_RIDING
            listOf("clean", "jhadu", "safai", "dust", "room", "mess").any { lower.contains(it) } -> CharacterActivity.SWEEPING_CLEANING
            idx == 0 -> CharacterActivity.WALKING_IN
            setting == RoomSetting.GYM -> CharacterActivity.GYM_WORKOUT
            setting == RoomSetting.KITCHEN -> CharacterActivity.COOKING_CHAI
            setting == RoomSetting.GAMING_ROOM -> CharacterActivity.GAMING_PHONE
            setting == RoomSetting.STREET_PARK -> CharacterActivity.PLAYING_CRICKET
            else -> CharacterActivity.TALKING
        }
    }

    suspend fun analyzeReferenceVideoStyle(
        apiKey: String,
        context: Context,
        videoUri: Uri
    ): Result<ClonedStyleBlueprint> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Please enter your Gemini API key first."))
        }
        val retriever = MediaMetadataRetriever()
        val bitmaps = mutableListOf<Bitmap>()
        var durationMs = 15_000L
        try {
            retriever.setDataSource(context, videoUri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 15_000L
            val frameCount = 8
            val stepUs = ((durationMs * 1000L) / (frameCount + 1)).coerceAtLeast(200_000L)

            for (i in 1..frameCount) {
                val rawBmp = retriever.getFrameAtTime(i * stepUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (rawBmp != null) {
                    val scaled = scaleDownBitmap(rawBmp, 360)
                    bitmaps += scaled
                    if (rawBmp != scaled) rawBmp.recycle()
                }
            }

            if (bitmaps.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Could not extract frames from the selected video."))
            }

            val parts = JSONArray().apply {
                put(JSONObject().put("text", """
                    Analyze these ${bitmaps.size} chronological keyframes from a ${durationMs / 1000f}s short comedy video.
                    Extract its editing rhythm, shot cadence, camera framing patterns, and comedy punchline formula so it can be cloned into a 9:16 stickman comedy reel.
                """.trimIndent()))
                bitmaps.forEach { bmp ->
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    put(JSONObject().put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", b64)
                    }))
                }
            }

            val styleSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("summary", JSONObject().put("type", "STRING"))
                    put("averageShotDurationMs", JSONObject().put("type", "INTEGER"))
                    put("pacingStyle", JSONObject().put("type", "STRING"))
                    put("recommendedFramings", JSONObject().apply {
                        put("type", "ARRAY")
                        put("items", JSONObject().apply {
                            put("type", "STRING")
                            put("enum", JSONArray(CameraFraming.entries.map { it.name }))
                        })
                    })
                    put("comedyFormula", JSONObject().put("type", "STRING"))
                })
                put("required", JSONArray(listOf("summary", "averageShotDurationMs", "pacingStyle", "recommendedFramings", "comedyFormula")))
            }

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", styleSchema)
                })
            }

            val responseJson = withTimeout(60_000L) {
                callGeminiTextWithFallback(apiKey, body)
            }
            val json = JSONObject(extractTextFromResponse(responseJson))
            val framingsArr = json.optJSONArray("recommendedFramings") ?: JSONArray()
            val framings = (0 until framingsArr.length()).map { framingsArr.getString(it) }

            Result.success(
                ClonedStyleBlueprint(
                    summary = json.getString("summary"),
                    averageShotDurationMs = json.optLong("averageShotDurationMs", 2200L).coerceIn(1000L, 4500L),
                    pacingStyle = json.optString("pacingStyle", "Rapid Escalation"),
                    recommendedFramings = framings,
                    comedyFormula = json.optString("comedyFormula", "Setup -> Escalate -> Surprise Cut")
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "analyzeReferenceVideoStyle Gemini unavailable, using local pacing blueprint", e)
            if (isRecoverableBusyOrNetworkError(e)) {
                val avgShotMs = (durationMs / bitmaps.size.coerceAtLeast(4)).coerceIn(1400L, 2800L)
                Result.success(
                    ClonedStyleBlueprint(
                        summary = "Fast 9:16 comedy pacing (${bitmaps.size} keyframes analyzed, ~${avgShotMs / 1000f}s/shot)",
                        averageShotDurationMs = avgShotMs,
                        pacingStyle = "Rapid Escalation & Surprise Cut",
                        recommendedFramings = listOf(
                            CameraFraming.WIDE_DOORWAY.name,
                            CameraFraming.CLOSE_UP_B.name,
                            CameraFraming.DESK_SLAM.name,
                            CameraFraming.SPLIT_PHONE_CALL.name,
                            CameraFraming.SLAPSTICK_CHASE.name
                        ),
                        comedyFormula = "Hook Setup -> Escalate Tension -> Unexpected Reversal -> Chase Punchline"
                    )
                )
            } else {
                Result.failure(e)
            }
        } finally {
            runCatching { retriever.release() }
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    suspend fun synthesizeShotVoice(
        apiKey: String,
        dialogue: String,
        voiceName: String,
        moodPose: MoodPose
    ): Result<SynthesizedAudioResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Please enter your Gemini API key first."))
        }
        if (dialogue.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Dialogue is empty."))
        }
        try {
            val deliveryDirection = when (moodPose) {
                MoodPose.ANGRY, MoodPose.FURIOUS -> "Say angrily and sharply"
                MoodPose.NERVOUS, MoodPose.SWEATING -> "Say nervously and fast"
                MoodPose.SHOCKED -> "Say in utter shock"
                MoodPose.SMUG -> "Say smugly and playfully"
                MoodPose.DEADPAN -> "Say completely deadpan"
                else -> "Say expressively in a comedy skit"
            }
            val prompt = "$deliveryDirection: $dialogue"

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName.ifBlank { "Puck" })
                            })
                        })
                    })
                })
            }

            val responseJson = withTimeout(16_000L) {
                try {
                    callGeminiRest(apiKey, preferredTtsModel, body, readTimeoutMs = 14_000)
                } catch (e: Exception) {
                    val suggested = Regex("models/(gemini-[a-zA-Z0-9._-]+)").find(e.message.orEmpty())?.groupValues?.getOrNull(1)
                    if (!suggested.isNullOrBlank() && suggested != preferredTtsModel &&
                        (suggested.contains("tts", ignoreCase = true) || e.message.orEmpty().contains("no longer available", ignoreCase = true))
                    ) {
                        val retryRes = callGeminiRest(apiKey, suggested, body, readTimeoutMs = 14_000)
                        preferredTtsModel = suggested
                        retryRes
                    } else {
                        throw e
                    }
                }
            }
            val b64 = extractAudioBase64FromResponse(responseJson)
            val pcm = Base64.decode(b64, Base64.DEFAULT)
            val rawDurationMs = ((pcm.size.toDouble() / 48000.0) * 1000.0).toLong().coerceAtLeast(700L)
            Result.success(SynthesizedAudioResult(pcmBytes = pcm, rawDurationMs = rawDurationMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "synthesizeShotVoice failed", e)
            Result.failure(e)
        }
    }

    private fun isRecoverableBusyOrNetworkError(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        // Keep genuine invalid/empty API key errors explicit so user fixes typos
        val isAuthFailure = msg.contains("API_KEY_INVALID", ignoreCase = true) ||
            msg.contains("API key not valid", ignoreCase = true) ||
            msg.contains("HTTP 400", ignoreCase = true) ||
            msg.contains("HTTP 401", ignoreCase = true) ||
            msg.contains("HTTP 403", ignoreCase = true)
        return !isAuthFailure
    }

    private fun summarizeTransientReason(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("high demand", ignoreCase = true) || msg.contains("503") || msg.contains("overloaded", ignoreCase = true) ->
                "Gemini servers busy (high demand)"
            msg.contains("timeout", ignoreCase = true) ->
                "Slow network timeout"
            msg.contains("429") || msg.contains("quota", ignoreCase = true) ->
                "Gemini rate limit reached"
            else -> "Network busy"
        }
    }

    private fun shouldTryNextModel(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("no longer available", ignoreCase = true) ||
            msg.contains("not found", ignoreCase = true) ||
            msg.contains("HTTP 404", ignoreCase = true) ||
            msg.contains("use models/", ignoreCase = true) ||
            msg.contains("high demand", ignoreCase = true) ||
            msg.contains("overloaded", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("HTTP 500", ignoreCase = true) ||
            msg.contains("HTTP 502", ignoreCase = true) ||
            msg.contains("HTTP 503", ignoreCase = true) ||
            msg.contains("HTTP 504", ignoreCase = true) ||
            msg.contains("HTTP 429", ignoreCase = true) ||
            e is java.net.SocketTimeoutException
    }

    private fun callGeminiTextWithFallback(apiKey: String, requestBody: JSONObject): JSONObject {
        val tried = linkedSetOf<String>()
        val queue = ArrayDeque(
            listOf(
                preferredTextModel,
                TEXT_MODEL,
                "gemini-3-flash-preview",
                "gemini-2.5-flash-lite"
            )
        )
        var lastError: Exception? = null

        while (queue.isNotEmpty()) {
            val model = queue.removeFirst()
            if (!tried.add(model)) continue
            try {
                val res = callGeminiRest(apiKey, model, requestBody, readTimeoutMs = 15_000)
                preferredTextModel = model
                return res
            } catch (e: Exception) {
                lastError = e
                if (!shouldTryNextModel(e)) {
                    throw e
                }
                val suggested = Regex("models/(gemini-[a-zA-Z0-9._-]+)").find(e.message.orEmpty())?.groupValues?.getOrNull(1)
                if (!suggested.isNullOrBlank() && !tried.contains(suggested)) {
                    queue.addFirst(suggested)
                }
            }
        }
        throw lastError ?: IllegalStateException("Gemini API request failed.")
    }

    private fun callGeminiRest(
        apiKey: String,
        model: String,
        requestBody: JSONObject,
        readTimeoutMs: Int = 24_000
    ): JSONObject {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${apiKey.trim()}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 10_000
            readTimeout = readTimeoutMs
            doOutput = true
        }
        return try {
            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val errMsg = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw IllegalStateException(errMsg?.takeIf { it.isNotBlank() } ?: "Gemini API error (HTTP $code)")
            }
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractTextFromResponse(response: JSONObject): String {
        val parts = response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("No response candidates from Gemini.")
        for (i in 0 until parts.length()) {
            val txt = parts.optJSONObject(i)?.optString("text")
            if (!txt.isNullOrBlank()) return stripJsonCodeFences(txt)
        }
        throw IllegalStateException("Gemini returned an empty text response.")
    }

    internal fun stripJsonCodeFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun extractAudioBase64FromResponse(response: JSONObject): String {
        val parts = response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("No audio candidate returned from Gemini TTS.")
        for (i in 0 until parts.length()) {
            val inline = parts.optJSONObject(i)?.optJSONObject("inlineData")
            val data = inline?.optString("data")
            if (!data.isNullOrBlank()) return data
        }
        throw IllegalStateException("Gemini TTS returned no inline audio data.")
    }

    private fun storyboardSchema(): JSONObject {
        val charProps = JSONObject().apply {
            put("slot", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(listOf("A", "B", "C")))
            })
            put("name", JSONObject().put("type", "STRING"))
            put("role", JSONObject().put("type", "STRING"))
            put("gender", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(CharacterGender.entries.map { it.name }))
            })
            put("voiceName", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(VoiceCatalog.actors.map { it.id }))
            })
            put("hairStyle", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(HairStyle.entries.map { it.name }))
            })
            put("hasGlasses", JSONObject().put("type", "BOOLEAN"))
            put("facialHair", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(FacialHair.entries.map { it.name }))
            })
            put("prop", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(CharacterProp.entries.map { it.name }))
            })
        }

        val shotProps = JSONObject().apply {
            put("speaker", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(SpeakerSlot.entries.map { it.name }))
            })
            put("framing", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(CameraFraming.entries.map { it.name }))
            })
            put("dialogue", JSONObject().put("type", "STRING"))
            put("speechBubble", JSONObject().put("type", "STRING"))
            put("transitionCard", JSONObject().put("type", "STRING"))
            put("moodPose", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(MoodPose.entries.map { it.name }))
            })
            put("activity", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(CharacterActivity.entries.map { it.name }))
            })
            put("sticker", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(MemeSticker.entries.map { it.name }))
            })
            put("sfx", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(ComicSfx.entries.map { it.name }))
            })
            put("durationMs", JSONObject().put("type", "INTEGER"))
        }

        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("title", JSONObject().put("type", "STRING"))
                put("introHookBanner", JSONObject().put("type", "STRING"))
                put("socialCaption", JSONObject().put("type", "STRING"))
                put("hashtags", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().put("type", "STRING"))
                })
                put("setting", JSONObject().apply {
                    put("type", "STRING")
                    put("enum", JSONArray(RoomSetting.entries.map { it.name }))
                })
                put("bgmMood", JSONObject().apply {
                    put("type", "STRING")
                    put("enum", JSONArray(BgmMood.entries.map { it.name }))
                })
                put("characters", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", charProps)
                        put("required", JSONArray(listOf("slot", "name", "role", "gender", "voiceName", "hairStyle", "hasGlasses", "facialHair", "prop")))
                    })
                })
                put("shots", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", shotProps)
                        put("required", JSONArray(listOf("speaker", "framing", "dialogue", "speechBubble", "transitionCard", "moodPose", "activity", "sticker", "sfx", "durationMs")))
                    })
                })
            })
            put("required", JSONArray(listOf("title", "introHookBanner", "socialCaption", "hashtags", "setting", "bgmMood", "characters", "shots")))
        }
    }

    private fun parseStoryboardJson(
        jsonText: String,
        niche: String,
        twist: String,
        language: String,
        clonedSummary: String,
        baseProject: ReelProject
    ): ReelProject {
        val json = JSONObject(jsonText)
        val hashtagsArr = json.optJSONArray("hashtags") ?: JSONArray()
        val hashtags = (0 until hashtagsArr.length()).map { hashtagsArr.getString(it) }.take(15)

        val charsArr = json.optJSONArray("characters") ?: JSONArray()
        val characters = (0 until charsArr.length()).map { idx ->
            val obj = charsArr.getJSONObject(idx)
            val voice = obj.optString("voiceName", VoiceCatalog.actors[idx % VoiceCatalog.actors.size].id)
            val gender = if (obj.has("gender")) {
                CharacterGender.fromRaw(obj.optString("gender"))
            } else if (VoiceCatalog.isFemaleVoice(voice)) {
                CharacterGender.FEMALE
            } else {
                CharacterGender.MALE
            }
            CharacterProfile(
                slot = SpeakerSlot.fromRaw(obj.optString("slot")),
                name = obj.optString("name", "Char ${idx + 1}"),
                role = obj.optString("role", "Lead"),
                gender = gender,
                voiceName = voice,
                voiceSpeed = 1.0f,
                hairStyle = HairStyle.fromRaw(obj.optString("hairStyle")),
                hasGlasses = obj.optBoolean("hasGlasses", false),
                facialHair = if (gender == CharacterGender.FEMALE) FacialHair.NONE else FacialHair.fromRaw(obj.optString("facialHair")),
                prop = CharacterProp.fromRaw(obj.optString("prop"))
            )
        }.take(3).ifEmpty { ReelProject.defaultCast() }

        val shotsArr = json.optJSONArray("shots") ?: JSONArray()
        val shots = (0 until shotsArr.length()).map { idx ->
            val obj = shotsArr.getJSONObject(idx)
            ReelShot(
                shotId = "shot_${UUID.randomUUID().toString().take(6)}_$idx",
                speaker = SpeakerSlot.fromRaw(obj.optString("speaker")),
                framing = CameraFraming.fromRaw(obj.optString("framing")),
                dialogue = obj.optString("dialogue", ""),
                speechBubble = obj.optString("speechBubble", ""),
                transitionCard = obj.optString("transitionCard", ""),
                moodPose = MoodPose.fromRaw(obj.optString("moodPose")),
                activity = CharacterActivity.fromRaw(obj.optString("activity")),
                sticker = MemeSticker.fromRaw(obj.optString("sticker")),
                sfx = ComicSfx.fromRaw(obj.optString("sfx")),
                durationMs = obj.optLong("durationMs", 2800L).coerceIn(1000L, 15000L),
                audioSourceLabel = "Not Voiced"
            )
        }.take(ReelProject.MAX_SHOTS).ifEmpty { ReelProject.defaultShots() }

        return baseProject.copy(
            id = "",
            title = json.optString("title", "Untitled InkReel").take(120),
            introHookBanner = json.optString("introHookBanner", baseProject.introHookBanner).take(48),
            socialCaption = json.optString("socialCaption", "").take(500),
            hashtags = hashtags,
            niche = niche,
            twistStructure = twist,
            language = language,
            setting = RoomSetting.fromRaw(json.optString("setting")),
            bgmMood = BgmMood.fromRaw(json.optString("bgmMood")),
            clonedStyleSummary = clonedSummary,
            characters = characters,
            shots = shots,
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun scaleDownBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDimension && h <= maxDimension) return source
        val scale = maxDimension.toFloat() / maxOf(w, h).toFloat()
        val newW = (w * scale).toInt().coerceAtLeast(64)
        val newH = (h * scale).toInt().coerceAtLeast(64)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    fun buildOfflineHooks(niche: String, twist: String, language: String): List<String> {
        val isHinglish = language.equals("Hinglish", ignoreCase = true) || language.equals("Hindi", ignoreCase = true)
        val offset = storyVariationCounter.getAndIncrement()
        val pool = if (isHinglish) {
            listOf(
                "$niche mein sab normal chal raha tha — phir aaya sabse bada $twist!",
                "$niche ka sabse strict rule ek second mein ulta pad gaya!",
                "Confidence full tha, plan zero tha — $niche mein sab pakde gaye!",
                "Bas 2 minute ka kaam bolkar shuru kiya tha, aur poora scene viral ho gaya!",
                "Jisne mazak udaya tha, end mein wahi sabse zyada shock reh gaya!",
                "Chai break ke chakkar mein $niche ka poora game palat gaya!",
                "Pehle laga free ka offer hai, par end mein double twist nikal aaya!"
            )
        } else {
            listOf(
                "Everyone in $niche thought it was routine — until the $twist hit!",
                "The strictest rule in $niche backfired in literally five seconds!",
                "Zero preparation, 100% confidence — and somehow it actually worked!",
                "She promised a 2-minute fix and accidentally triggered total chaos!",
                "He walked in bluffing, forgot his mic was on, and shocked everyone!",
                "One tiny shortcut during $niche turned into a viral disaster!",
                "The person laughing hardest at the start was panicking by the end!"
            )
        }
        return pool.indices.map { idx -> pool[(idx + offset) % pool.size] }.take(5)
    }

    private data class ScriptSeed(
        val title: String,
        val timeSkipLabel: String,
        val s1: String, val b1: String, val a1: CharacterActivity,
        val s2: String, val b2: String, val a2: CharacterActivity,
        val s3: String, val b3: String, val a3: CharacterActivity,
        val s5: String, val b5: String, val a5: CharacterActivity,
        val s6: String, val b6: String, val a6: CharacterActivity,
        val s7: String, val b7: String, val a7: CharacterActivity
    )

    fun buildFallbackNicheStoryboard(
        niche: String,
        twist: String,
        language: String,
        hook: String,
        clonedSummary: String,
        baseProject: ReelProject
    ): ReelProject {
        val lower = niche.lowercase()
        val isHinglish = language.equals("Hinglish", ignoreCase = true) || language.equals("Hindi", ignoreCase = true)
        val variant = storyVariationCounter.getAndIncrement()

        val room = when {
            lower.contains("office") || lower.contains("corporate") -> RoomSetting.OFFICE
            lower.contains("doctor") || lower.contains("hospital") -> RoomSetting.HOSPITAL
            lower.contains("interview") -> RoomSetting.INTERVIEW_ROOM
            lower.contains("gym") -> RoomSetting.GYM
            lower.contains("cafe") || lower.contains("coffee") -> RoomSetting.COFFEE_SHOP
            lower.contains("kitchen") || lower.contains("chai") || lower.contains("cook") -> RoomSetting.KITCHEN
            lower.contains("cricket") || lower.contains("sport") || lower.contains("street") -> RoomSetting.STREET_PARK
            lower.contains("gaming") || lower.contains("reel") -> RoomSetting.GAMING_ROOM
            lower.contains("parent") || lower.contains("hostel") || lower.contains("wedding") || lower.contains("dance") -> RoomSetting.LIVING_ROOM
            else -> RoomSetting.CLASSROOM
        }

        val bgm = when {
            lower.contains("cricket") || lower.contains("gym") || lower.contains("dance") -> BgmMood.CHAOTIC_CHASE
            lower.contains("interview") || lower.contains("doctor") -> BgmMood.DRAMATIC_SUSPENSE
            else -> BgmMood.QUIRKY_COMEDY
        }

        val cast = when (room) {
            RoomSetting.HOSPITAL -> listOf(
                CharacterProfile(SpeakerSlot.A, "Dr. Mehta", "Senior Doctor", CharacterGender.MALE, "Orus", 1.0f, HairStyle.BALD, true, FacialHair.MUSTACHE, CharacterProp.STETHOSCOPE),
                CharacterProfile(SpeakerSlot.B, "Pooja", "Drama Patient", CharacterGender.FEMALE, "Leda", 1.04f, HairStyle.PONYTAIL, false, FacialHair.NONE, CharacterProp.PHONE),
                CharacterProfile(SpeakerSlot.C, "Nurse Priya", "Head Nurse", CharacterGender.FEMALE, "Kore", 1.0f, HairStyle.BUN, true, FacialHair.NONE, CharacterProp.CLIPBOARD)
            )
            RoomSetting.OFFICE -> listOf(
                CharacterProfile(SpeakerSlot.A, "Rohan Sir", "Manager", CharacterGender.MALE, "Orus", 1.0f, HairStyle.PARTED, true, FacialHair.MUSTACHE, CharacterProp.FOLDER),
                CharacterProfile(SpeakerSlot.B, "Neha", "Clever Dev", CharacterGender.FEMALE, "Leda", 1.05f, HairStyle.PONYTAIL, false, FacialHair.NONE, CharacterProp.MUG),
                CharacterProfile(SpeakerSlot.C, "Kavita Ma'am", "Director", CharacterGender.FEMALE, "Kore", 1.0f, HairStyle.BUN, true, FacialHair.NONE, CharacterProp.CLIPBOARD)
            )
            RoomSetting.INTERVIEW_ROOM -> listOf(
                CharacterProfile(SpeakerSlot.A, "HR Madam", "Interviewer", CharacterGender.FEMALE, "Kore", 1.0f, HairStyle.BOB, true, FacialHair.NONE, CharacterProp.CLIPBOARD),
                CharacterProfile(SpeakerSlot.B, "Aman", "Bold Candidate", CharacterGender.MALE, "Puck", 1.04f, HairStyle.SPIKY, false, FacialHair.NONE, CharacterProp.FOLDER),
                CharacterProfile(SpeakerSlot.C, "Tech Lead", "Co-Interviewer", CharacterGender.MALE, "Charon", 0.98f, HairStyle.PARTED, true, FacialHair.GOATEE, CharacterProp.MUG)
            )
            RoomSetting.COFFEE_SHOP -> listOf(
                CharacterProfile(SpeakerSlot.A, "Barista Kabir", "Cafe Owner", CharacterGender.MALE, "Puck", 1.0f, HairStyle.MESSY, false, FacialHair.GOATEE, CharacterProp.MUG),
                CharacterProfile(SpeakerSlot.B, "Sana", "Vlogger Guest", CharacterGender.FEMALE, "Zephyr", 1.05f, HairStyle.LONG_WAVY, false, FacialHair.NONE, CharacterProp.PHONE),
                CharacterProfile(SpeakerSlot.C, "Manager Uncle", "Cashier", CharacterGender.MALE, "Orus", 0.96f, HairStyle.BALD, true, FacialHair.MUSTACHE, CharacterProp.CLIPBOARD)
            )
            RoomSetting.GYM -> listOf(
                CharacterProfile(SpeakerSlot.A, "Coach Kabir", "Trainer", CharacterGender.MALE, "Fenrir", 1.0f, HairStyle.SPIKY, false, FacialHair.GOATEE, CharacterProp.DUMBBELL),
                CharacterProfile(SpeakerSlot.B, "Riya", "Cardio Pro", CharacterGender.FEMALE, "Zephyr", 1.04f, HairStyle.PONYTAIL, false, FacialHair.NONE, CharacterProp.PHONE),
                CharacterProfile(SpeakerSlot.C, "Aunty Ji", "Surprise Guest", CharacterGender.FEMALE, "Sulafat", 1.0f, HairStyle.BUN, true, FacialHair.NONE, CharacterProp.MUG)
            )
            RoomSetting.KITCHEN -> listOf(
                CharacterProfile(SpeakerSlot.A, "Mummy", "Head Chef", CharacterGender.FEMALE, "Kore", 1.0f, HairStyle.BUN, true, FacialHair.NONE, CharacterProp.CLIPBOARD),
                CharacterProfile(SpeakerSlot.B, "Aryan", "Chai Master", CharacterGender.MALE, "Puck", 1.05f, HairStyle.MESSY, false, FacialHair.NONE, CharacterProp.MUG),
                CharacterProfile(SpeakerSlot.C, "Didi", "Food Critic", CharacterGender.FEMALE, "Aoede", 1.0f, HairStyle.LONG_WAVY, false, FacialHair.NONE, CharacterProp.PHONE)
            )
            RoomSetting.GAMING_ROOM -> listOf(
                CharacterProfile(SpeakerSlot.A, "Papa", "Wifi Boss", CharacterGender.MALE, "Orus", 1.0f, HairStyle.PARTED, true, FacialHair.MUSTACHE, CharacterProp.RULER),
                CharacterProfile(SpeakerSlot.B, "Ananya", "Pro Gamer", CharacterGender.FEMALE, "Leda", 1.05f, HairStyle.TWIN_BRAIDS, false, FacialHair.NONE, CharacterProp.PHONE),
                CharacterProfile(SpeakerSlot.C, "Chintu", "Sidekick", CharacterGender.MALE, "Puck", 1.06f, HairStyle.SPIKY, false, FacialHair.NONE, CharacterProp.PHONE)
            )
            RoomSetting.STREET_PARK -> listOf(
                CharacterProfile(SpeakerSlot.A, "Captain Vikram", "Bowler", CharacterGender.MALE, "Orus", 1.0f, HairStyle.SPIKY, false, FacialHair.BEARD, CharacterProp.NONE),
                CharacterProfile(SpeakerSlot.B, "Tara", "Power Hitter", CharacterGender.FEMALE, "Zephyr", 1.04f, HairStyle.PONYTAIL, false, FacialHair.NONE, CharacterProp.NONE),
                CharacterProfile(SpeakerSlot.C, "Umpire Uncle", "Neighbor", CharacterGender.MALE, "Charon", 0.96f, HairStyle.BALD, true, FacialHair.MUSTACHE, CharacterProp.CLIPBOARD)
            )
            RoomSetting.LIVING_ROOM -> listOf(
                CharacterProfile(SpeakerSlot.A, "Papa", "Strict Parent", CharacterGender.MALE, "Orus", 1.0f, HairStyle.PARTED, true, FacialHair.MUSTACHE, CharacterProp.RULER),
                CharacterProfile(SpeakerSlot.B, "Simran", "Clever Kid", CharacterGender.FEMALE, "Leda", 1.05f, HairStyle.PONYTAIL, false, FacialHair.NONE, CharacterProp.PHONE),
                CharacterProfile(SpeakerSlot.C, "Mummy", "Judge", CharacterGender.FEMALE, "Kore", 1.0f, HairStyle.BUN, false, FacialHair.NONE, CharacterProp.MUG)
            )
            else -> ReelProject.defaultCast()
        }

        val seeds = selectNicheSeeds(room, niche, twist, variant, isHinglish)
        val picked = seeds[variant % seeds.size]
        val hookTrimmed = hook.trim().take(72)
        val openingLine = if (hookTrimmed.isNotBlank()) hookTrimmed else picked.s1

        val shots = listOf(
            ReelShot(
                shotId = "fb_1_${UUID.randomUUID().toString().take(4)}",
                speaker = SpeakerSlot.A,
                framing = CameraFraming.WIDE_DOORWAY,
                dialogue = openingLine,
                speechBubble = picked.b1,
                moodPose = MoodPose.ANGRY,
                activity = picked.a1,
                sfx = ComicSfx.DOOR_SLAM,
                durationMs = 2800L
            ),
            ReelShot(
                shotId = "fb_2_${UUID.randomUUID().toString().take(4)}",
                speaker = SpeakerSlot.B,
                framing = CameraFraming.CLOSE_UP_B,
                dialogue = picked.s2,
                speechBubble = picked.b2,
                moodPose = MoodPose.SMUG,
                activity = picked.a2,
                sfx = ComicSfx.NONE,
                durationMs = 2900L
            ),
            ReelShot(
                shotId = "fb_3_${UUID.randomUUID().toString().take(4)}",
                speaker = SpeakerSlot.A,
                framing = CameraFraming.DESK_SLAM,
                dialogue = picked.s3,
                speechBubble = picked.b3,
                moodPose = MoodPose.FURIOUS,
                activity = picked.a3,
                sfx = ComicSfx.DESK_SLAM,
                durationMs = 2800L
            ),
            ReelShot(
                shotId = "fb_4_${UUID.randomUUID().toString().take(4)}",
                speaker = SpeakerSlot.SILENT,
                framing = CameraFraming.TIME_SKIP_CARD,
                dialogue = "",
                transitionCard = picked.timeSkipLabel,
                moodPose = MoodPose.NEUTRAL,
                sfx = ComicSfx.TIME_SKIP_WHOOSH,
                durationMs = 1400L
            ),
            ReelShot(
                shotId = "fb_5_${UUID.randomUUID().toString().take(4)}",
                speaker = SpeakerSlot.B,
                framing = CameraFraming.SPLIT_PHONE_CALL,
                dialogue = picked.s5,
                speechBubble = picked.b5,
                moodPose = MoodPose.SMUG,
                activity = picked.a5,
                sticker = MemeSticker.DEAL_WITH_IT_SHADES,
                sfx = ComicSfx.DRAMATIC_STING,
                durationMs = 3100L
            ),
            ReelShot(
                shotId = "fb_6_${UUID.randomUUID().toString().take(4)}",
                speaker = SpeakerSlot.C,
                framing = CameraFraming.CLOSE_UP_C,
                dialogue = picked.s6,
                speechBubble = picked.b6,
                moodPose = MoodPose.SHOCKED,
                activity = picked.a6,
                sticker = MemeSticker.MIND_BLOWN,
                sfx = ComicSfx.PUNCHLINE_BOOM,
                durationMs = 2900L
            ),
            ReelShot(
                shotId = "fb_7_${UUID.randomUUID().toString().take(4)}",
                speaker = SpeakerSlot.A,
                framing = CameraFraming.SLAPSTICK_CHASE,
                dialogue = picked.s7,
                speechBubble = picked.b7,
                moodPose = MoodPose.FURIOUS,
                activity = picked.a7,
                sfx = ComicSfx.CARTOON_RUN,
                durationMs = 2400L
            )
        )

        rememberRecentTitle(picked.title)

        return baseProject.copy(
            id = "",
            title = picked.title,
            socialCaption = if (isHinglish) "${picked.title} — end wala twist miss mat karna!" else "${picked.title} — wait for the ending!",
            hashtags = listOf("#stickman", "#comedyreels", "#inkreel", "#viral"),
            niche = niche,
            twistStructure = twist,
            language = language,
            setting = room,
            bgmMood = bgm,
            clonedStyleSummary = clonedSummary,
            characters = cast,
            shots = shots,
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun selectNicheSeeds(
        room: RoomSetting,
        niche: String,
        twist: String,
        variant: Int,
        isHinglish: Boolean
    ): List<ScriptSeed> {
        return when (room) {
            RoomSetting.HOSPITAL -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "Fake Fever vs Real Bill",
                    timeSkipLabel = "CHECKUP KE BAAD",
                    s1 = "Doctor sahab, mujhe kal subah college jaana hai, achanak 104 bukhar feel ho raha hai!",
                    b1 = "104 FEVER!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Thik hai, par thermometer pe coffee kyun lagi hai aur haath mein selfie camera kyun on hai?",
                    b2 = "COFFEE TRICK?", a2 = CharacterActivity.TAKING_SELFIE,
                    s3 = "Medical certificate sirf exam skip karne ke liye nahi banta, chalo asli injection lagao!",
                    b3 = "INJECTION TIME!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Hello Mummy, doctor ne bola mujhe aaram aur pizza dono urgent chahiye!",
                    b5 = "PIZZA PRESCRIPTION!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Aur consultation fee 5000 rupaye pay karna mat bhoolna!",
                    b6 = "BILL 5000?!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Ruko! Bukhar theek ho gaya, mera student discount toh wapas do!",
                    b7 = "BHAGO!!", a7 = CharacterActivity.WALKING_IN
                ),
                ScriptSeed(
                    title = "X-Ray Of Empty Wallet",
                    timeSkipLabel = "10 MINUTE BAAD",
                    s1 = "Doctor sahab, pet mein bahut ajeeb awaaz aa rahi hai aur neend bhi nahi aati!",
                    b1 = "PET DARD!", a1 = CharacterActivity.CRYING_DRAMA,
                    s2 = "Humne poora X-ray aur ultrasound kar liya — report bilkul clean hai!",
                    b2 = "ALL CLEAR!", a2 = CharacterActivity.TALKING,
                    s3 = "Clean kaise? Mujhe toh lagta hai raat ko 3 baje wali bhookh ki bimari hai!",
                    b3 = "BHOOKH ATTACK!", a3 = CharacterActivity.COOKING_CHAI,
                    s5 = "Suno, report mein pet ke andar sirf do samosa aur aadha maggi packet dikha hai!",
                    b5 = "SAMOSA X-RAY!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "Isliye dawa nahi, aaj se phone pe food delivery app delete karni padegi!",
                    b6 = "APP DELETE?!", a6 = CharacterActivity.DANCING,
                    s7 = "Are sir dawai de do par food app mat udao please!",
                    b7 = "NOOO!!", a7 = CharacterActivity.WALKING_IN
                )
            ) else listOf(
                ScriptSeed(
                    title = "Fake Sick Leave Exposed",
                    timeSkipLabel = "10 MINUTES LATER",
                    s1 = "Doctor, I think I caught a sudden mystery flu right before Monday morning!",
                    b1 = "SICK LEAVE!", a1 = CharacterActivity.CRYING_DRAMA,
                    s2 = "Interesting... then why does your thermometer smell like hot chai and your smartwatch shows 12000 steps?",
                    b2 = "NICE TRY!", a2 = CharacterActivity.STUDYING_WRITING,
                    s3 = "Wait, that was me running away from my actual deadlines!",
                    b3 = "CAUGHT!", a3 = CharacterActivity.TALKING,
                    s5 = "Good news! Your recovery requires no screen time and zero gaming for two weeks!",
                    b5 = "NO GAMING!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Suddenly I feel miraculously cured! Tear up that prescription right now!",
                    b6 = "CURED INSTANTLY!", a6 = CharacterActivity.DANCING,
                    s7 = "Hey, you still owe me the clinic checkup fee!",
                    b7 = "PAY UP!", a7 = CharacterActivity.WALKING_IN
                ),
                ScriptSeed(
                    title = "Google vs Real Doctor",
                    timeSkipLabel = "5 MINUTES LATER",
                    s1 = "Doctor, I searched my sneeze online and the internet says I have only three days left!",
                    b1 = "PANIC!", a1 = CharacterActivity.GAMING_PHONE,
                    s2 = "Relax! You literally just inhaled chili powder while eating chips!",
                    b2 = "CHIPS DUST!", a2 = CharacterActivity.TALKING,
                    s3 = "Wait... why did the website ask for my credit card then?",
                    b3 = "SCAMMED?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Here is your medicine: one glass of water and shutting down your browser!",
                    b5 = "CLOSE CHROME!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "Even the nurse is laughing at my diagnosis now!",
                    b6 = "HAHAHA!", a6 = CharacterActivity.DANCING,
                    s7 = "Come back here! Next time just ask me instead of a blog!",
                    b7 = "RUN!!", a7 = CharacterActivity.WALKING_IN
                )
            )

            RoomSetting.GYM -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "100 Kg Selfie Only",
                    timeSkipLabel = "2 HAFTE BAAD",
                    s1 = "Aaj gym ka pehla din hai! Sirf 200 kilo deadlift maar kar ghar jaunga!",
                    b1 = "BEAST MODE!", a1 = CharacterActivity.GYM_WORKOUT,
                    s2 = "Bhai pehle khali rod utha le, 20 minute se mirror ke saamne sirf selfie le raha hai!",
                    b2 = "MIRROR CHECK!", a2 = CharacterActivity.TAKING_SELFIE,
                    s3 = "Selfie bina workout count hi nahi hota bro, lighting set karni padti hai!",
                    b3 = "LIGHTING BRO!", a3 = CharacterActivity.TAKING_SELFIE,
                    s5 = "Suno, trainer ne aaj mera pre-workout chhupa kar sirf neembu paani rakh diya!",
                    b5 = "NIMBU PANI?!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Aur Aunty Ji ne ek haath se tera 50 kilo dumbbell utha kar side mein rakh diya!",
                    b6 = "AUNTY OP!!", a6 = CharacterActivity.GYM_WORKOUT,
                    s7 = "Aunty ji aap apna diet plan share karo main aaj se follow karunga!",
                    b7 = "GURU AUNTY!", a7 = CharacterActivity.CLAPPING_CHEERING
                ),
                ScriptSeed(
                    title = "Treadmill vs Samosa",
                    timeSkipLabel = "SHAM 6 BAJE",
                    s1 = "Aaj bina ruke ek ghanta cardio hoga, no cheat meal ever again!",
                    b1 = "NO CHEAT!", a1 = CharacterActivity.GYM_WORKOUT,
                    s2 = "Bahut badhiya! Par treadmill pe daudte daudte phone mein momos kyun order ho rahe hain?",
                    b2 = "MOMOS ORDER?!", a2 = CharacterActivity.GAMING_PHONE,
                    s3 = "Woh calories burn karne ka advance celebration hai coach!",
                    b3 = "ADVANCE PARTY!", a3 = CharacterActivity.DANCING,
                    s5 = "Delivery bhaiya gym ke gate pe plate lekar khade hain!",
                    b5 = "CHUTNEY EXTRA!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "Ab toh trainer khud ek plate momos lekar baith gaya hai!",
                    b6 = "CHEAT DAY!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Kal se pakka double workout karenge, aaj toh chutney khatam karo!",
                    b7 = "BHAAGO!", a7 = CharacterActivity.WALKING_IN
                )
            ) else listOf(
                ScriptSeed(
                    title = "Day 1 Gym Ego Lift",
                    timeSkipLabel = "10 MINUTES LATER",
                    s1 = "Coach, load all the plates! Today I break every gym record!",
                    b1 = "HEAVY WEIGHTS!", a1 = CharacterActivity.GYM_WORKOUT,
                    s2 = "You spent forty minutes adjusting your playlist and only lifted your phone!",
                    b2 = "PLAYLIST GYM!", a2 = CharacterActivity.TAKING_SELFIE,
                    s3 = "Music is ninety percent of muscle memory, coach!",
                    b3 = "SCIENCE!", a3 = CharacterActivity.DANCING,
                    s5 = "Wait, grandma just walked in and started warming up with your max squat!",
                    b5 = "WAIT WHAT?!", a5 = CharacterActivity.GYM_WORKOUT,
                    s6 = "And she isn't even out of breath while sipping chai!",
                    b6 = "LEGEND!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Wait! Teach me that warm-up routine before I drop this barbell!",
                    b7 = "HELP ME!", a7 = CharacterActivity.CRYING_DRAMA
                )
            )

            RoomSetting.CLASSROOM -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "Backbencher vs Surprise Test",
                    timeSkipLabel = "AGLI SUBAH",
                    s1 = "Class mein aaj bina bataye surprise test hoga, saare phone desk par rakho!",
                    b1 = "SURPRISE TEST!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Sir mera pen ghar reh gaya, dost ke pen se likhun ya imagination se?",
                    b2 = "NO PEN SIR!", a2 = CharacterActivity.STUDYING_WRITING,
                    s3 = "Chup chaap likho! Aur ye last bench pe snack ka packet kaun chhupa raha hai?",
                    b3 = "CAUGHT!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Plot twist sir! Aaj teacher day hai, woh snack nahi aapka cake chhupaya tha!",
                    b5 = "TEACHER CAKE!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "Sach mein?! Toh Principal Ma'am ne bhi aaj chhutti declare kar di!",
                    b6 = "HOLIDAY?!", a6 = CharacterActivity.DANCING,
                    s7 = "Ruko mera cake toh chhod jao backbenchers!",
                    b7 = "MERI CAKE!", a7 = CharacterActivity.WALKING_IN
                ),
                ScriptSeed(
                    title = "Copy Paste Homework Kaand",
                    timeSkipLabel = "LUNCH BREAK",
                    s1 = "Tum dono ka essay ekdum word to word same kaise aaya?",
                    b1 = "SAME ESSAY?!", a1 = CharacterActivity.STUDYING_WRITING,
                    s2 = "Sir hum dono ka dimaag aur Wi-Fi router ek hi hai na!",
                    b2 = "SAME WIFI!", a2 = CharacterActivity.TALKING,
                    s3 = "Toh last line mein likha hai 'Print Page 2 Before Submitting' — ye kya hai?!",
                    b3 = "COPY PASTE!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Hello dost, lagta hai sir ne hamara AI prompt bhi padh liya!",
                    b5 = "PROMPT CAUGHT!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Kam se kam font aur naam toh change kar lete yaar!",
                    b6 = "GENIUS FAIL!", a6 = CharacterActivity.DANCING,
                    s7 = "Ab dono milkar poora blackboard 100 baar saaf karo!",
                    b7 = "CLEAN NOW!", a7 = CharacterActivity.SWEEPING_CLEANING
                )
            ) else listOf(
                ScriptSeed(
                    title = "Copy-Paste Homework Trap",
                    timeSkipLabel = "NEXT PERIOD",
                    s1 = "Explain why both your essays contain the exact same spelling mistake!",
                    b1 = "IDENTICAL!", a1 = CharacterActivity.STUDYING_WRITING,
                    s2 = "Great minds think alike... and share the exact same group chat!",
                    b2 = "TEAMWORK!", a2 = CharacterActivity.TALKING,
                    s3 = "You literally wrote the prompt 'make it sound smart' inside paragraph two!",
                    b3 = "CAUGHT!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Wait, did the Principal just give extra credit for honesty?!",
                    b5 = "WAIT WHAT?", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "No, she gave both of you weekend broom duty!",
                    b6 = "DETENTION!", a6 = CharacterActivity.SWEEPING_CLEANING,
                    s7 = "Run! Don't let them hand us the chalkboard dusters!",
                    b7 = "ESCAPE!", a7 = CharacterActivity.WALKING_IN
                )
            )

            RoomSetting.OFFICE -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "Mute Button Kaand",
                    timeSkipLabel = "MEETING KE BAAD",
                    s1 = "Monday morning 9 AM sharp sabko camera on karke report deni hai!",
                    b1 = "CAMERA ON!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Sir mera mic kharab hai, main chat mein full energy se present hoon!",
                    b2 = "MIC BROKEN!", a2 = CharacterActivity.SLEEPING_SNORING,
                    s3 = "Abhi tumhare piche se chai aur pakode talne ki awaaz kyun aa rahi hai?!",
                    b3 = "UNMUTED?!", a3 = CharacterActivity.COOKING_CHAI,
                    s5 = "Oh no! Main bina mute kiye bol diya ki meeting 2 line ke email mein ho sakti thi!",
                    b5 = "OPEN MIC FAIL!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Relax! Client ko woh honesty itni pasand aayi ki poori call cancel kar di!",
                    b6 = "NO MEETING!", a6 = CharacterActivity.DANCING,
                    s7 = "Next time mujhe bhi pakode bhej dena work from home wale!",
                    b7 = "CHAI PARTY!", a7 = CharacterActivity.CLAPPING_CHEERING
                ),
                ScriptSeed(
                    title = "Friday 5 PM Urgent Task",
                    timeSkipLabel = "5 MINUTE BAAD",
                    s1 = "Friday shaam 5:59 pe ek chhota sa urgent change aaya hai, bas 2 minute lagenge!",
                    b1 = "URGENT FIX!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Sir mera laptop already weekend mode pe shutdown ho chuka hai!",
                    b2 = "BYE WEEKEND!", a2 = CharacterActivity.TAKING_SELFIE,
                    s3 = "Ye button bas left se right karna hai, kyun bhaag rahe ho?",
                    b3 = "JUST 1 PIXEL!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Wait! Ek pixel hilane se poora production login page udd gaya!",
                    b5 = "PROD DOWN!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Shabash! Ab koi login nahi kar payega toh complain bhi zero aayegi!",
                    b6 = "BIG BRAIN!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Ruko pehle backup restore karo phir weekend manana!",
                    b7 = "FIX IT NOW!", a7 = CharacterActivity.WALKING_IN
                )
            ) else listOf(
                ScriptSeed(
                    title = "Unmuted On Client Call",
                    timeSkipLabel = "AFTER THE CALL",
                    s1 = "Everyone cameras on! Where is our live demo presentation?!",
                    b1 = "SHOW DEMO!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Sorry boss, my Wi-Fi freezes only during difficult questions!",
                    b2 = "FAKE FREEZE!", a2 = CharacterActivity.SLEEPING_SNORING,
                    s3 = "Then why are you clearly chewing chips while whispering 'this could be an email'?!",
                    b3 = "NOT MUTED!", a3 = CharacterActivity.COOKING_CHAI,
                    s5 = "Wait, the client actually agreed and ended the call early!",
                    b5 = "CLIENT AGREED!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "She just promoted you to Chief Efficiency Officer!",
                    b6 = "PROMOTED?!", a6 = CharacterActivity.DANCING,
                    s7 = "Hey! Bring some of those lucky snacks for my next meeting too!",
                    b7 = "SHARE CHIPS!", a7 = CharacterActivity.WALKING_IN
                )
            )

            RoomSetting.STREET_PARK -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "Gully Cricket Kaanch Kaand",
                    timeSkipLabel = "LAST BALL PE",
                    s1 = "Rule number 1: Jo ball seedha uncle ke window pe maarega wahi lene jayega!",
                    b1 = "RULE NO 1!", a1 = CharacterActivity.PLAYING_CRICKET,
                    s2 = "Tension mat le, aaj main sirf ground shot khelungi, pakka six nahi!",
                    b2 = "SAFE SHOT!", a2 = CharacterActivity.PLAYING_CRICKET,
                    s3 = "Arre ye ball toh seedha Sharma Uncle ki chai ke cup mein giri!",
                    b3 = "SHARMA CUP!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Hello bhai, bat lekar bhaago — Umpire Uncle khud ball rakh ke baith gaye!",
                    b5 = "BALL GAYAB!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Plot twist! Uncle khud batting karne utar aaye aur bole pehli ball trial hai!",
                    b6 = "UNCLE BATTING!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Pehle mera kanch ka bill bharo phir super over khelenge!",
                    b7 = "BHAAGO!!", a7 = CharacterActivity.WALKING_IN
                ),
                ScriptSeed(
                    title = "Owner Of The Bat Always Bats First",
                    timeSkipLabel = "6 RUN BAAKI",
                    s1 = "Main first batting karunga — warna apna bat aur ball dono wapas le ja raha hoon!",
                    b1 = "MY BAT MY RULE!", a1 = CharacterActivity.PLAYING_CRICKET,
                    s2 = "Thik hai, par clean bowled hone pe 'trial ball' mat chillana!",
                    b2 = "NO TRIAL BALL!", a2 = CharacterActivity.PLAYING_CRICKET,
                    s3 = "Arre stump ud gaya!! Ye ball toh pitch se neeche thi, count nahi hogi!",
                    b3 = "TRIAL BALL!!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Umpire Uncle ne slow-motion phone replay check kiya — pakka clean out hai!",
                    b5 = "DRS REPLAY!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "Aur agle hi over mein Tara ne teen lagatar chhakke maar diye!",
                    b6 = "HAT-TRICK SIX!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Chalo bat wapas do warna kal subah naya bat lekar aana!",
                    b7 = "MATCH JEET!", a7 = CharacterActivity.DANCING
                )
            ) else listOf(
                ScriptSeed(
                    title = "Street Cricket Window Smash",
                    timeSkipLabel = "LAST OVER",
                    s1 = "Remember the golden rule: whoever hits it into Uncle's garden goes to get it!",
                    b1 = "GOLDEN RULE!", a1 = CharacterActivity.PLAYING_CRICKET,
                    s2 = "Watch this smooth cover drive — zero chance it leaves the street!",
                    b2 = "CLEAN HIT!", a2 = CharacterActivity.PLAYING_CRICKET,
                    s3 = "Uh oh... it just landed right inside Uncle's hot tea mug!",
                    b3 = "SPLASH!!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Wait, he isn't angry — he wants to bowl the final ball himself!",
                    b5 = "FINAL BALL!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "And he just took three wickets in a row on a bicycle!",
                    b6 = "HAT TRICK!", a6 = CharacterActivity.DANCING,
                    s7 = "Nice match kids, now who's paying for my broken flowerpot?!",
                    b7 = "RUN!!", a7 = CharacterActivity.WALKING_IN
                )
            )

            RoomSetting.KITCHEN -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "Chai Mein Namak Twist",
                    timeSkipLabel = "2 MINUTE BAAD",
                    s1 = "Mehmaan aaye hain, aaj special adrak wali chai tum banao aur izzat bachao!",
                    b1 = "SPECIAL CHAI!", a1 = CharacterActivity.COOKING_CHAI,
                    s2 = "Bilkul tension mat lo Mummy, aaj meri chai peekar sab fan ho jayenge!",
                    b2 = "CHEF PRO!", a2 = CharacterActivity.COOKING_CHAI,
                    s3 = "Par tumne white dibbe se cheeni daali ya pichle hafte wala namak?!",
                    b3 = "SALT ALERT!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Hello Didi, mehmaan ne pehli sip lete hi bola 'Wah kya detox soup banaya hai!'",
                    b5 = "DETOX SOUP?!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "Aur recipe maang ke Insta pe reel bhi daal di!",
                    b6 = "VIRAL CHEF!", a6 = CharacterActivity.DANCING,
                    s7 = "Ruko! Agli baar kitchen mein namak aur cheeni pe label lagana!",
                    b7 = "BACH GAYE!", a7 = CharacterActivity.CLAPPING_CHEERING
                ),
                ScriptSeed(
                    title = "Ice Cream Box Mein Matar",
                    timeSkipLabel = "FRIDGE KHOLTE HI",
                    s1 = "Poori garmi ke baad aaj raat ko fridge mein rakhi vanilla ice cream khani hai!",
                    b1 = "ICE CREAM!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Aaram se kholna, Mummy ne bilkul naya family pack rakha tha!",
                    b2 = "FAMILY PACK!", a2 = CharacterActivity.TALKING,
                    s3 = "Dhoka!! Is ice cream ke dibbe mein thandi hari matar aur dhaniya bhara hua hai!",
                    b3 = "MATAR SCAM!!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Haan toh khali dibba waste thodi karna tha — asli ice cream neeche wale shelf pe hai!",
                    b5 = "DESI LOGIC!", a5 = CharacterActivity.COOKING_CHAI,
                    s6 = "Didi ne live prank record karke poori family group mein bhej diya!",
                    b6 = "PRANKED!!", a6 = CharacterActivity.TAKING_SELFIE,
                    s7 = "Chalo ab asli ice cream nikalo warna main saari matar khaa jaunga!",
                    b7 = "SCOOP TIME!", a7 = CharacterActivity.DANCING
                )
            ) else listOf(
                ScriptSeed(
                    title = "Salt Instead Of Sugar Chai",
                    timeSkipLabel = "2 MINUTES LATER",
                    s1 = "The guests are here — make your best masala chai without burning the pan!",
                    b1 = "SAVE US!", a1 = CharacterActivity.COOKING_CHAI,
                    s2 = "Watch a master at work! Two giant scoops from the white jar!",
                    b2 = "CHEF MODE!", a2 = CharacterActivity.COOKING_CHAI,
                    s3 = "Tell me you read the label... that jar was pure sea salt!",
                    b3 = "SEA SALT?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Wait, the food critic guest thinks it's an exotic gourmet broth!",
                    b5 = "FIVE STARS!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "She just offered to buy the secret recipe!",
                    b6 = "RECIPE DEAL!", a6 = CharacterActivity.DANCING,
                    s7 = "Don't tell anyone it was an accident — hide the salt jar now!",
                    b7 = "SHHH!!", a7 = CharacterActivity.WALKING_IN
                )
            )

            RoomSetting.INTERVIEW_ROOM -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "10 Years Experience At Age 21",
                    timeSkipLabel = "2 MINUTE BAAD",
                    s1 = "Resume mein likha hai 10 saal ka experience, aur tumhari age sirf 21 hai — kaise?!",
                    b1 = "10 YRS EXP?!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Madam main din raat overtime aur double shift karta tha, isliye double count hua!",
                    b2 = "OVERTIME MATH!", a2 = CharacterActivity.TALKING,
                    s3 = "Achha chalo ek tricky question: apni sabse badi weakness batao!",
                    b3 = "WEAKNESS?!", a3 = CharacterActivity.STUDYING_WRITING,
                    s5 = "Madam meri weakness honesty hai... aur mujhe lagta nahi aapko iski parwah hai!",
                    b5 = "BRUTAL HONESTY!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Tech Lead khud hass pada aur bola aisa hi fearless banda chahiye!",
                    b6 = "HIRED?!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Ruko! Offer letter pe sign karne se pehle mera samosa wapas karo!",
                    b7 = "SIGN NOW!", a7 = CharacterActivity.DANCING
                ),
                ScriptSeed(
                    title = "Salary Expectation vs Reality",
                    timeSkipLabel = "FINAL ROUND",
                    s1 = "Interview shuru karne se pehle batao tum is company mein kyun aana chahte ho?",
                    b1 = "WHY US?!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Kyunki aapke office ke bahar free chai aur AC dono top class hai madam!",
                    b2 = "FREE CHAI!", a2 = CharacterActivity.COOKING_CHAI,
                    s3 = "Mazak mat karo! Expected salary kya chahiye seedha number batao!",
                    b3 = "SALARY?", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Bas itni salary de do ki mahine ke last week mein dost se udhaar na maangna pade!",
                    b5 = "LAST WEEK PAIN!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "HR ne emotional hokar turant joining bonus bhi add kar diya!",
                    b6 = "BONUS UNLOCKED!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Chalo pehle cafeteria chalte hain aaj samosa meri taraf se!",
                    b7 = "PARTY!!", a7 = CharacterActivity.DANCING
                )
            ) else listOf(
                ScriptSeed(
                    title = "Overtime Math Resume",
                    timeSkipLabel = "FINAL ROUND",
                    s1 = "Your resume claims ten years of experience in a tool released three years ago!",
                    b1 = "FAKE EXP?!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "I worked triple shifts and drank four espressos a day — it counts as ten!",
                    b2 = "PRO MATH!", a2 = CharacterActivity.TALKING,
                    s3 = "Okay genius, tell me your single biggest weakness right now!",
                    b3 = "WEAKNESS?!", a3 = CharacterActivity.STUDYING_WRITING,
                    s5 = "Honestly? I never check whether my jokes land during job interviews!",
                    b5 = "BOLD MOVE!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Wait — the hiring manager just offered the senior role on the spot!",
                    b6 = "HIRED!!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Hold on! Can I still use the free coffee machine before signing?!",
                    b7 = "COFFEE FIRST!", a7 = CharacterActivity.DANCING
                )
            )

            RoomSetting.COFFEE_SHOP -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "Spelling Name Wrong On Cup",
                    timeSkipLabel = "ORDER READY",
                    s1 = "Ek caramel cold coffee dena — aur please cup pe mera naam sahi likhna!",
                    b1 = "SPELL RIGHT!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Bilkul madam, apna naam slowly batayein main note kar raha hoon!",
                    b2 = "NOTED!", a2 = CharacterActivity.COOKING_CHAI,
                    s3 = "Mera naam 'Priya' hai... tumne cup pe 'Pizza' kyun likh diya?!",
                    b3 = "PIZZA CUP?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Suno dosto, ye cafe sirf naam galat likhne ki wajah se viral reel banata hai!",
                    b5 = "VIRAL TRICK!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "Is cup ki selfie pe hi 5 lakh likes aa gaye hain bhai!",
                    b6 = "500K LIKES!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Chalo ek aur cup pe 'Samosa' likh kar do, free marketing meri taraf se!",
                    b7 = "FREE PROMO!", a7 = CharacterActivity.DANCING
                ),
                ScriptSeed(
                    title = "Free WiFi vs 100 Rupee Coffee",
                    timeSkipLabel = "3 GHANTE BAAD",
                    s1 = "Bas ek 80 rupaye ki regular chai order karke 6 ghante laptop chalana hai!",
                    b1 = "FREE WIFI!", a1 = CharacterActivity.GAMING_PHONE,
                    s2 = "Sir aap subah se sirf ek biscuit lekar poore cafe ka bandwidth khaa rahe ho!",
                    b2 = "BANDWIDTH?!", a2 = CharacterActivity.COOKING_CHAI,
                    s3 = "Wi-Fi password change kar diya kya? Mera upload 99 percent pe atak gaya!",
                    b3 = "99% STUCK!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Naya password sirf unko milega jo ek aur samosa order karenge!",
                    b5 = "BUY SAMOSA!", a5 = CharacterActivity.TALKING,
                    s6 = "Sab customers ne password ke liye ek saath double order kar diya!",
                    b6 = "GENIUS OWNER!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Chalo ab naya password batao jaldi, upload finish karna hai!",
                    b7 = "PASSWORD DO!", a7 = CharacterActivity.WALKING_IN
                )
            ) else listOf(
                ScriptSeed(
                    title = "Mystery Name On Coffee Cup",
                    timeSkipLabel = "5 MINUTES LATER",
                    s1 = "Just a simple iced latte please, and spell my short name correctly!",
                    b1 = "SIMPLE NAME!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Got it! Order ready for... 'Bananabread'?!",
                    b2 = "WAIT WHAT?!", a2 = CharacterActivity.COOKING_CHAI,
                    s3 = "My name is Maya! How did you turn four letters into a bakery item?!",
                    b3 = "HOW?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Wait, that weird cup photo just blew up our entire coffee vlog!",
                    b5 = "VIRAL CUP!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "Now everybody is lining up asking for funny fake names!",
                    b6 = "SOLD OUT!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Write 'Superstar' on the next mug before the crowd takes them all!",
                    b7 = "NEXT ORDER!", a7 = CharacterActivity.DANCING
                )
            )

            RoomSetting.GAMING_ROOM -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "1 HP Last Match vs Wifi Off",
                    timeSkipLabel = "LAST ZONE",
                    s1 = "Aaj raat sirf ek aakhri rank push match khelunga — pakka last game!",
                    b1 = "LAST MATCH!", a1 = CharacterActivity.GAMING_PHONE,
                    s2 = "Tum pichle 4 ghante se yahi bol rahe ho, Papa router ke paas khade hain!",
                    b2 = "PAPA ALERT!", a2 = CharacterActivity.TALKING,
                    s3 = "Arre final enemy 1 HP pe bacha tha — achanak ping 999 kaise ho gaya?!",
                    b3 = "PING 999!!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Hello Papa?! Aapne Wi-Fi plug nikal kar phone charger kyun lagaya?!",
                    b5 = "WIFI PLUG?!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Plot twist! Sidekick Chintu ne hotspot on karke aakhri clutch jeetwa diya!",
                    b6 = "CLUTCH WIN!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Papa ab chappal leke aa rahe hain, headset chhupao jaldi!",
                    b7 = "RUN FAST!", a7 = CharacterActivity.WALKING_IN
                ),
                ScriptSeed(
                    title = "Viral Reel Behind The Scenes",
                    timeSkipLabel = "POSTING KE BAAD",
                    s1 = "Is reel ko shoot karne mein 50 retakes lage hain, is baar pakka 10 million!",
                    b1 = "50 RETAKES!", a1 = CharacterActivity.TAKING_SELFIE,
                    s2 = "Light, camera, filter sab set hai — bas peeche Papa na aa jayein!",
                    b2 = "SHHH!", a2 = CharacterActivity.DANCING,
                    s3 = "Video ke beech mein peeche se kaun 'Dhaniya le aana' chilla diya?!",
                    b3 = "DHANIYA?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Wait! Galti se yahi bloopers wala clip upload ho gaya!",
                    b5 = "WRONG CLIP!", a5 = CharacterActivity.GAMING_PHONE,
                    s6 = "Aur logon ko woh Dhaniya wala dialogue itna pasand aaya ki trend ban gaya!",
                    b6 = "VIRAL AUDIO!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Ab Mummy ko bhi channel mein 50 percent partner banana padega!",
                    b7 = "MUMMY OP!", a7 = CharacterActivity.DANCING
                )
            ) else listOf(
                ScriptSeed(
                    title = "Last Match 999 Ping",
                    timeSkipLabel = "FINAL CIRCLE",
                    s1 = "One last ranked match, I promise! Zero distractions tonight!",
                    b1 = "ONE LAST GAME!", a1 = CharacterActivity.GAMING_PHONE,
                    s2 = "Uh oh, Dad just walked past the hallway holding the Wi-Fi router!",
                    b2 = "DANGER ZONE!", a2 = CharacterActivity.TALKING,
                    s3 = "Final boss at 1 HP and my screen literally froze at 999 ping!",
                    b3 = "999 PING!!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Wait, little bro shared mobile hotspot just in time for the winning shot!",
                    b5 = "CLUTCH SAVE!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "The whole squad cheered so loud we woke up the entire street!",
                    b6 = "VICTORY!", a6 = CharacterActivity.DANCING,
                    s7 = "Hide the controllers now before Dad checks the hallway!",
                    b7 = "ABORT!", a7 = CharacterActivity.WALKING_IN
                )
            )

            RoomSetting.LIVING_ROOM -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "Sharmaji Ka Beta vs 94%",
                    timeSkipLabel = "RESULT KE BAAD",
                    s1 = "Result aa gaya! Jaldi batao kitne percent aaye — Sharmaji ke bete ke 96 hain!",
                    b1 = "SHARMAJI?!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Papa mere 94 percent aaye hain, poori class mein second rank hai!",
                    b2 = "94 PERCENT!", a2 = CharacterActivity.STUDYING_WRITING,
                    s3 = "Bas 94?! Baaki 6 percent kahan gaye? Cricket khelne ya reel banane?!",
                    b3 = "BAKI 6%?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Papa suno! Sharmaji ka beta khud mere notes maangne aaya hai next exam ke liye!",
                    b5 = "NOTES PLEASE!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "Aur Mummy ne khush hokar aaj shaam ko golgappe party fix kar di!",
                    b6 = "PARTY FIX!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Shabash! Agli baar 97 laana warna Wi-Fi band kar dunga!",
                    b7 = "PROUD PAPA!", a7 = CharacterActivity.DANCING
                ),
                ScriptSeed(
                    title = "Hostel & Wedding Chaos",
                    timeSkipLabel = "SHAADI KE STAGE PE",
                    s1 = "Kal sangeet mein sabko sync mein step karna hai — koi galti nahi honi chahiye!",
                    b1 = "SYNC STEPS!", a1 = CharacterActivity.DANCING,
                    s2 = "Tension mat lo, maine poora ek mahina practice ki hai — dekho mera moonwalk!",
                    b2 = "MOONWALK!", a2 = CharacterActivity.DANCING,
                    s3 = "Par DJ wale bhaiya ne romantic song ki jagah nagin dhun kyun chala di?!",
                    b3 = "NAGIN DHUN?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Koi baat nahi, poore parivaar ne bina ruke freestyle nagin dance shuru kar diya!",
                    b5 = "FULL SWAG!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "Shaadi ka cameraman khud camera rakh ke naachne laga!",
                    b6 = "DJ ROCKS!", a6 = CharacterActivity.DANCING,
                    s7 = "Chalo pehle gulab jamun counter pe bhaago warna khatam ho jayenge!",
                    b7 = "SWEETS TIME!", a7 = CharacterActivity.WALKING_IN
                )
            ) else listOf(
                ScriptSeed(
                    title = "95% Still Not 100%",
                    timeSkipLabel = "RESULT DAY",
                    s1 = "Report cards are out! Tell me the exact score right this second!",
                    b1 = "SHOW SCORE!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "I got ninety-five percent overall and topped the science test!",
                    b2 = "95 PERCENT!", a2 = CharacterActivity.STUDYING_WRITING,
                    s3 = "Wait... where did the remaining five percent vanish?!",
                    b3 = "MISSING 5%?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Mom, tell Dad the neighbor's kid literally copied my study notes!",
                    b5 = "MY NOTES!", a5 = CharacterActivity.TALKING,
                    s6 = "Even the neighbor's parents are asking for my study timetable now!",
                    b6 = "TOPPER VIBES!", a6 = CharacterActivity.CLAPPING_CHEERING,
                    s7 = "Okay fine, victory samosas tonight — but aim for 100 next time!",
                    b7 = "TREAT TIME!", a7 = CharacterActivity.DANCING
                )
            )

            else -> if (isHinglish) listOf(
                ScriptSeed(
                    title = "$niche Chaos #${(variant % 9) + 1}",
                    timeSkipLabel = "KUCH DER BAAD",
                    s1 = "Aaj $niche mein ek simple sa kaam tha — aur tumne uspe bhi drama shuru kar diya!",
                    b1 = "YE KYA KIYA?", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Arre plan bilkul solid tha, bas end mein Wi-Fi aur kismat dono slow ho gayi!",
                    b2 = "WIFI SLOW!", a2 = CharacterActivity.GAMING_PHONE,
                    s3 = "Bahane band karo! Ye $twist wala scene toh poori duniya dekh chuki hai!",
                    b3 = "SAB DEKH LIYA!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Suno, jo galat button dabaya tha na — usi se poora video 1 million cross kar gaya!",
                    b5 = "1M VIEWS!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "Ab toh ghar wale bhi pooch rahe hain hamara cameo kab aayega!",
                    b6 = "NEXT PART?!", a6 = CharacterActivity.DANCING,
                    s7 = "Ruko! Pehle credit mera naam pe daalo phir celebration karna!",
                    b7 = "PART 2!", a7 = CharacterActivity.CLAPPING_CHEERING
                ),
                ScriptSeed(
                    title = "$niche Surprise Twist",
                    timeSkipLabel = "AGLA DIN",
                    s1 = "Bina soche samjhe $niche ka challenge kyun accept kar liya tumne?",
                    b1 = "KYUN KIYA?!", a1 = CharacterActivity.TALKING,
                    s2 = "Mujhe laga bas 5 minute ka kaam hai, par poora din chai aur plan banane mein gaya!",
                    b2 = "CHAI BREAK!", a2 = CharacterActivity.COOKING_CHAI,
                    s3 = "Chalo ab sach batao, original plan kahan gayab hua?",
                    b3 = "ASLI PLAN?", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Tension mat lo, plan B itna funny nikla ki sab hass hass ke lotpot ho gaye!",
                    b5 = "FULL COMEDY!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "Ab toh har koi yahi dialogue copy kar raha hai!",
                    b6 = "TRENDING!", a6 = CharacterActivity.DANCING,
                    s7 = "Chalo ab treat nikalo warna main agla secret leak kar dunga!",
                    b7 = "TREAT DO!", a7 = CharacterActivity.WALKING_IN
                )
            ) else listOf(
                ScriptSeed(
                    title = "$niche Plot Twist #${(variant % 9) + 1}",
                    timeSkipLabel = "10 MINUTES LATER",
                    s1 = "This was supposed to be a simple day in $niche — how did it get this chaotic?!",
                    b1 = "HOW?!", a1 = CharacterActivity.WALKING_IN,
                    s2 = "Technically my plan worked... just in the completely wrong direction!",
                    b2 = "WRONG WAY!", a2 = CharacterActivity.GAMING_PHONE,
                    s3 = "You ignored every single instruction and still expect a trophy!",
                    b3 = "SERIOUSLY?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Wait, someone filmed the whole mess and it just hit trending!",
                    b5 = "TRENDING!", a5 = CharacterActivity.TAKING_SELFIE,
                    s6 = "Now everyone wants a sequel before Monday!",
                    b6 = "PART 2?!", a6 = CharacterActivity.DANCING,
                    s7 = "Wait! Split the viral credit with me first!",
                    b7 = "HOLD ON!", a7 = CharacterActivity.WALKING_IN
                ),
                ScriptSeed(
                    title = "$niche Reverse Card",
                    timeSkipLabel = "NEXT MORNING",
                    s1 = "Who told you to improvise during the big $niche moment?!",
                    b1 = "NO IMPROVISING!", a1 = CharacterActivity.TALKING,
                    s2 = "I lost the notes, panicked, and just smiled confidently!",
                    b2 = "JUST SMILE!", a2 = CharacterActivity.STUDYING_WRITING,
                    s3 = "Wait... nobody noticed you forgot half the steps?!",
                    b3 = "NOBODY?!", a3 = CharacterActivity.CRYING_DRAMA,
                    s5 = "Confidence fixes ninety percent of bad math and bad excuses!",
                    b5 = "PRO MOVE!", a5 = CharacterActivity.CLAPPING_CHEERING,
                    s6 = "Even the boss is clapping right now!",
                    b6 = "BRAVO!", a6 = CharacterActivity.DANCING,
                    s7 = "Quick, run before they ask us to repeat it!",
                    b7 = "ESCAPE!!", a7 = CharacterActivity.WALKING_IN
                )
            )
        }
    }

    private fun rememberRecentTitle(title: String) {
        if (title.isBlank()) return
        synchronized(recentGeneratedTitles) {
            if (!recentGeneratedTitles.contains(title)) {
                recentGeneratedTitles.addLast(title)
                while (recentGeneratedTitles.size > 6) {
                    recentGeneratedTitles.removeFirst()
                }
            }
        }
    }

    companion object {
        private const val TAG = "GeminiInkReelService"
        private const val TEXT_MODEL = "gemini-3.8-flash"
        private const val TTS_MODEL = "gemini-2.5-flash-preview-tts"
        @Volatile
        private var preferredTextModel = TEXT_MODEL
        @Volatile
        private var preferredTtsModel = TTS_MODEL
        private val storyVariationCounter = java.util.concurrent.atomic.AtomicInteger(0)
        private val recentGeneratedTitles = ArrayDeque<String>()
    }
}
