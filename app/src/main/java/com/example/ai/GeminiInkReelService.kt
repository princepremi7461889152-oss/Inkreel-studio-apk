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

            val prompt = """
                Create a complete 9:16 monochrome stickman comedy reel storyboard with 6 to 9 timed shots.
                Niche: $niche
                Comedy Twist Structure: $twist
                Language: $language (if Hinglish, write punchy conversational Hinglish in Latin script; if Hindi, write natural Hindi).
                Optional Story Hook: ${hook.ifBlank { "Invent the funniest relatable premise for this niche." }}
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
                    put("responseMimeType", "application/json")
                    put("responseSchema", storyboardSchema())
                })
            }

            val responseJson = withTimeout(52_000L) {
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
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
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
            Log.e(TAG, "analyzeReferenceVideoStyle failed", e)
            Result.failure(e)
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

            val responseJson = withTimeout(18_000L) {
                callGeminiRest(apiKey, TTS_MODEL, body, readTimeoutMs = 16_000)
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
                "gemini-2.5-flash-lite",
                "gemini-2.0-flash",
                "gemini-2.5-flash"
            )
        )
        var lastError: Exception? = null

        while (queue.isNotEmpty()) {
            val model = queue.removeFirst()
            if (!tried.add(model)) continue
            try {
                val res = callGeminiRest(apiKey, model, requestBody, readTimeoutMs = 20_000)
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
        return if (isHinglish) {
            listOf(
                "$niche mein jab sab normal lag raha ho, achanak sabse bada twist nikal aata hai ($twist)!",
                "Sir ne poocha excuse kya hai — aur jawab sunke poora room silent ho gaya!",
                "Chai pee kar plan banaya tha topper banne ka, par Wifi band hote hi sab gadbad ho gayi!",
                "Dost bola tension mat le main sambhal lunga — aur 5 minute baad khud bhaag gaya!",
                "Pehle laga free ka offer hai, par end mein bill double nikla!"
            )
        } else {
            listOf(
                "Everyone in $niche thought it was under control — until the $twist hit!",
                "He walked in confident with a fake plan... and accidentally confessed on speakerphone!",
                "She promised 5 minutes of work, took a 3-hour chai break, and still got promoted!",
                "The strict boss demanded proof — so the intern brought the actual receipt!",
                "One innocent excuse turned into a full courtroom chase in 10 seconds!"
            )
        }
    }

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
            RoomSetting.OFFICE -> listOf(
                CharacterProfile(SpeakerSlot.A, "Rohan Sir", "Manager", CharacterGender.MALE, "Orus", 1.0f, HairStyle.PARTED, true, FacialHair.MUSTACHE, CharacterProp.FOLDER),
                CharacterProfile(SpeakerSlot.B, "Neha", "Clever Dev", CharacterGender.FEMALE, "Leda", 1.05f, HairStyle.PONYTAIL, false, FacialHair.NONE, CharacterProp.MUG),
                CharacterProfile(SpeakerSlot.C, "Kavita Ma'am", "Director", CharacterGender.FEMALE, "Kore", 1.0f, HairStyle.BUN, true, FacialHair.NONE, CharacterProp.CLIPBOARD)
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
            else -> ReelProject.defaultCast()
        }

        val activityA = when (room) {
            RoomSetting.GYM -> CharacterActivity.GYM_WORKOUT
            RoomSetting.KITCHEN -> CharacterActivity.COOKING_CHAI
            RoomSetting.STREET_PARK -> CharacterActivity.PLAYING_CRICKET
            RoomSetting.GAMING_ROOM -> CharacterActivity.GAMING_PHONE
            else -> CharacterActivity.WALKING_IN
        }
        val activityB = when (room) {
            RoomSetting.GYM -> CharacterActivity.TAKING_SELFIE
            RoomSetting.KITCHEN -> CharacterActivity.COOKING_CHAI
            RoomSetting.STREET_PARK -> CharacterActivity.DANCING
            RoomSetting.GAMING_ROOM -> CharacterActivity.GAMING_PHONE
            else -> CharacterActivity.STUDYING_WRITING
        }

        val hookShort = hook.trim().take(70)
        val shots = if (isHinglish) {
            listOf(
                ReelShot(
                    shotId = "fb_1_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.A,
                    framing = CameraFraming.WIDE_DOORWAY,
                    dialogue = if (hookShort.isNotBlank()) hookShort else "Arre aaj $niche mein sab kuch time par chahiye, samjhe?!",
                    speechBubble = "TIME PAR!",
                    moodPose = MoodPose.ANGRY,
                    activity = activityA,
                    sfx = ComicSfx.DOOR_SLAM,
                    durationMs = 2800L
                ),
                ReelShot(
                    shotId = "fb_2_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.B,
                    framing = CameraFraming.CLOSE_UP_B,
                    dialogue = "Haan bilkul! Maine subah 5 baje se full planning aur practice shuru kar di thi.",
                    speechBubble = "FULL READY!",
                    moodPose = MoodPose.SMUG,
                    activity = activityB,
                    sfx = ComicSfx.NONE,
                    durationMs = 2900L
                ),
                ReelShot(
                    shotId = "fb_3_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.A,
                    framing = CameraFraming.DESK_SLAM,
                    dialogue = "Jhooth mat bolo! Tum abhi tak reel scroll aur chai peene mein busy the!",
                    speechBubble = "PAKDE GAYE!",
                    moodPose = MoodPose.FURIOUS,
                    activity = CharacterActivity.CRYING_DRAMA,
                    sfx = ComicSfx.DESK_SLAM,
                    durationMs = 2800L
                ),
                ReelShot(
                    shotId = "fb_4_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.SILENT,
                    framing = CameraFraming.TIME_SKIP_CARD,
                    dialogue = "",
                    transitionCard = "10 MINUTES LATER",
                    moodPose = MoodPose.NEUTRAL,
                    sfx = ComicSfx.TIME_SKIP_WHOOSH,
                    durationMs = 1400L
                ),
                ReelShot(
                    shotId = "fb_5_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.B,
                    framing = CameraFraming.SPLIT_PHONE_CALL,
                    dialogue = "Suno, twist ye hai ki client ko mera reel wala idea hi pasand aa gaya!",
                    speechBubble = "VIRAL TWIST!",
                    moodPose = MoodPose.SMUG,
                    activity = CharacterActivity.CLAPPING_CHEERING,
                    sticker = MemeSticker.DEAL_WITH_IT_SHADES,
                    sfx = ComicSfx.DRAMATIC_STING,
                    durationMs = 3100L
                ),
                ReelShot(
                    shotId = "fb_6_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.C,
                    framing = CameraFraming.CLOSE_UP_C,
                    dialogue = "Bilkul sahi! Promotion aur treat dono pakki — ab party shuru karo!",
                    speechBubble = "WAIT WHAT?!",
                    moodPose = MoodPose.SHOCKED,
                    activity = CharacterActivity.DANCING,
                    sticker = MemeSticker.MIND_BLOWN,
                    sfx = ComicSfx.PUNCHLINE_BOOM,
                    durationMs = 2900L
                ),
                ReelShot(
                    shotId = "fb_7_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.A,
                    framing = CameraFraming.SLAPSTICK_CHASE,
                    dialogue = "Ruko! Mera credit wapas do, selfie baad mein lena!",
                    speechBubble = "BHAAGO!!",
                    moodPose = MoodPose.FURIOUS,
                    activity = CharacterActivity.WALKING_IN,
                    sfx = ComicSfx.CARTOON_RUN,
                    durationMs = 2400L
                )
            )
        } else {
            listOf(
                ReelShot(
                    shotId = "fb_1_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.A,
                    framing = CameraFraming.WIDE_DOORWAY,
                    dialogue = if (hookShort.isNotBlank()) hookShort else "Listen up! Today in $niche, zero excuses and full discipline!",
                    speechBubble = "NO EXCUSES!",
                    moodPose = MoodPose.ANGRY,
                    activity = activityA,
                    sfx = ComicSfx.DOOR_SLAM,
                    durationMs = 2800L
                ),
                ReelShot(
                    shotId = "fb_2_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.B,
                    framing = CameraFraming.CLOSE_UP_B,
                    dialogue = "Relax! I stayed up all night perfecting my strategy for this exact moment.",
                    speechBubble = "TRUST ME!",
                    moodPose = MoodPose.SMUG,
                    activity = activityB,
                    sfx = ComicSfx.NONE,
                    durationMs = 2900L
                ),
                ReelShot(
                    shotId = "fb_3_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.A,
                    framing = CameraFraming.DESK_SLAM,
                    dialogue = "You literally forgot to save the file AND drank three coffees instead!",
                    speechBubble = "CAUGHT RED!",
                    moodPose = MoodPose.FURIOUS,
                    activity = CharacterActivity.CRYING_DRAMA,
                    sfx = ComicSfx.DESK_SLAM,
                    durationMs = 2800L
                ),
                ReelShot(
                    shotId = "fb_4_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.SILENT,
                    framing = CameraFraming.TIME_SKIP_CARD,
                    dialogue = "",
                    transitionCard = "10 MINUTES LATER",
                    moodPose = MoodPose.NEUTRAL,
                    sfx = ComicSfx.TIME_SKIP_WHOOSH,
                    durationMs = 1400L
                ),
                ReelShot(
                    shotId = "fb_5_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.B,
                    framing = CameraFraming.SPLIT_PHONE_CALL,
                    dialogue = "Plot twist! The camera was recording the whole chaos and it just hit a million views!",
                    speechBubble = "1M VIEWS!",
                    moodPose = MoodPose.SMUG,
                    activity = CharacterActivity.CLAPPING_CHEERING,
                    sticker = MemeSticker.DEAL_WITH_IT_SHADES,
                    sfx = ComicSfx.DRAMATIC_STING,
                    durationMs = 3100L
                ),
                ReelShot(
                    shotId = "fb_6_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.C,
                    framing = CameraFraming.CLOSE_UP_C,
                    dialogue = "Genius! Give them an instant bonus and make a Part 2 immediately!",
                    speechBubble = "PROMOTED?!",
                    moodPose = MoodPose.SHOCKED,
                    activity = CharacterActivity.DANCING,
                    sticker = MemeSticker.MIND_BLOWN,
                    sfx = ComicSfx.PUNCHLINE_BOOM,
                    durationMs = 2900L
                ),
                ReelShot(
                    shotId = "fb_7_${UUID.randomUUID().toString().take(4)}",
                    speaker = SpeakerSlot.A,
                    framing = CameraFraming.SLAPSTICK_CHASE,
                    dialogue = "Wait! At least share half the viral royalty with me!",
                    speechBubble = "COME BACK!",
                    moodPose = MoodPose.FURIOUS,
                    activity = CharacterActivity.WALKING_IN,
                    sfx = ComicSfx.CARTOON_RUN,
                    durationMs = 2400L
                )
            )
        }

        return baseProject.copy(
            id = "",
            title = "$niche Chaos",
            socialCaption = if (isHinglish) "$niche ka sabse bada comedy twist! End tak dekho!" else "Wait for the twist in this $niche skit!",
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

    companion object {
        private const val TAG = "GeminiInkReelService"
        private const val TEXT_MODEL = "gemini-3.8-flash"
        private const val TTS_MODEL = "gemini-2.5-flash-preview-tts"
        @Volatile
        private var preferredTextModel = TEXT_MODEL
    }
}
