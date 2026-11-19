package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RoomSetting(val label: String) {
    CLASSROOM("Classroom"),
    OFFICE("Corporate Office"),
    HOSPITAL("Hospital"),
    INTERVIEW_ROOM("Interview Room"),
    COFFEE_SHOP("Coffee Shop"),
    GYM("Gym"),
    LIVING_ROOM("Living Room"),
    KITCHEN("Desi Kitchen"),
    STREET_PARK("Street / Cricket Ground"),
    GAMING_ROOM("Gaming Room");

    companion object {
        fun fromRaw(raw: String?): RoomSetting =
            entries.firstOrNull { it.name == raw } ?: CLASSROOM
    }
}

enum class CameraFraming(val label: String) {
    WIDE_DOORWAY("Wide Doorway"),
    MEDIUM_TWO_SHOT("Medium Two-Shot"),
    CLOSE_UP_A("Close-Up A"),
    CLOSE_UP_B("Close-Up B"),
    CLOSE_UP_C("Close-Up C"),
    SITTING_AT_DESK("Sitting at Desk"),
    OVER_THE_SHOULDER("Over-The-Shoulder"),
    SPLIT_PHONE_CALL("Split-Screen Phone Call"),
    DESK_SLAM("Desk Slam"),
    TIME_SKIP_CARD("Day / Time-Skip Card"),
    PROP_DROP("Prop Drop Shock"),
    SLAPSTICK_CHASE("Slapstick Chase"),
    BONK_ENDING("Dust-Cloud BONK!");

    companion object {
        fun fromRaw(raw: String?): CameraFraming =
            entries.firstOrNull { it.name == raw } ?: MEDIUM_TWO_SHOT
    }
}

enum class MoodPose(val label: String) {
    NEUTRAL("Neutral"),
    ANGRY("Angry"),
    NERVOUS("Nervous"),
    SWEATING("Sweating"),
    SHOCKED("Shocked"),
    SMUG("Smug"),
    DEADPAN("Deadpan"),
    FURIOUS("Furious (Veins)"),
    POINTING("Pointing"),
    HIDING("Peeking / Hiding");

    companion object {
        fun fromRaw(raw: String?): MoodPose =
            entries.firstOrNull { it.name == raw } ?: NEUTRAL
    }
}

enum class CharacterActivity(val label: String) {
    TALKING("Talking / Gesturing"),
    WALKING_IN("Walking / Strolling"),
    DANCING("Dancing / Vibing"),
    GYM_WORKOUT("Gym Lifting / Squats"),
    PLAYING_CRICKET("Cricket / Batting"),
    COOKING_CHAI("Cooking / Making Chai"),
    STUDYING_WRITING("Studying / Writing"),
    GAMING_PHONE("Gaming / Scrolling Reel"),
    SLEEPING_SNORING("Sleeping / Zzz"),
    CLAPPING_CHEERING("Clapping / Cheering"),
    CRYING_DRAMA("Crying / Facepalm"),
    TAKING_SELFIE("Selfie / Vlogging"),
    DRIVING_RIDING("Bike / Car Driving"),
    SWEEPING_CLEANING("Cleaning / Sweeping");

    companion object {
        fun fromRaw(raw: String?): CharacterActivity =
            entries.firstOrNull { it.name == raw } ?: TALKING
    }
}

enum class MemeSticker(val label: String) {
    NONE("No Sticker"),
    DEAL_WITH_IT_SHADES("Deal With It Shades"),
    RED_CIRCLE("Viral Red Circle + Arrow"),
    MIND_BLOWN("Mind Blown Sparks"),
    QUESTION_MARKS("Confused ???"),
    CROWN("King / Queen Crown"),
    BROKEN_HEART("Broken Heart"),
    MIC_DROP("Mic Drop");

    companion object {
        fun fromRaw(raw: String?): MemeSticker =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class ReelColorTheme(
    val label: String,
    val wallHex: String,
    val floorHex: String,
    val accentHex: String,
    val inkHex: String,
    val isDarkStage: Boolean = false
) {
    CLASSIC_MONO("Monochrome Ink", "#FAFAFA", "#EDEDEF", "#DC2626", "#09090B", false),
    INK_CRIMSON("Ink + Crimson Red", "#FFFFFF", "#F4F4F5", "#E11D48", "#09090B", false),
    INK_GOLD("Ink + Viral Gold", "#FFFDF7", "#F5EFE0", "#D97706", "#111111", false),
    CHALK_GREEN("Chalkboard Green", "#F2F8F4", "#E3EFE7", "#15803D", "#0F172A", false),
    MIDNIGHT_NOIR("Midnight Noir", "#18181B", "#09090B", "#FACC15", "#FAFAFA", true);

    companion object {
        fun fromRaw(raw: String?): ReelColorTheme =
            entries.firstOrNull { it.name == raw } ?: CLASSIC_MONO
    }
}

enum class ComicSfx(val label: String) {
    NONE("None"),
    DOOR_OPEN("Door Open"),
    DOOR_SLAM("Door Slam"),
    DRAMATIC_STING("Dramatic Sting"),
    DESK_SLAM("Desk Slam"),
    NERVOUS_RATTLE("Nervous Rattle"),
    CLIPBOARD_DROP("Clipboard Drop"),
    PUNCHLINE_BOOM("Punchline Boom"),
    RECORD_SCRATCH("Record Scratch"),
    SAD_VIOLIN("Sad Violin"),
    TIME_SKIP_WHOOSH("Time-Skip Whoosh"),
    CARTOON_RUN("Cartoon Run"),
    CARTOON_BONK("Cartoon Bonk");

    companion object {
        fun fromRaw(raw: String?): ComicSfx =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class BgmMood(val label: String) {
    QUIRKY_COMEDY("Quirky Comedy"),
    DRAMATIC_SUSPENSE("Dramatic Suspense"),
    CHAOTIC_CHASE("Chaotic Chase"),
    WHOLESOME_CHILL("Wholesome Chill");

    companion object {
        fun fromRaw(raw: String?): BgmMood =
            entries.firstOrNull { it.name == raw } ?: QUIRKY_COMEDY
    }
}

enum class SpeakerSlot(val label: String) {
    A("Char A"),
    B("Char B"),
    C("Char C"),
    SILENT("Silent Beat");

    companion object {
        fun fromRaw(raw: String?): SpeakerSlot =
            entries.firstOrNull { it.name == raw } ?: A
    }
}

enum class CharacterGender(val label: String) {
    MALE("Boy / Male"),
    FEMALE("Girl / Female");

    companion object {
        fun fromRaw(raw: String?): CharacterGender =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MALE
    }
}

enum class HairStyle(val label: String) {
    BALD("Bald"),
    SPIKY("Spiky"),
    PARTED("Side Part"),
    BOB("Bob Cut"),
    BUN("Top Bun"),
    MESSY("Messy"),
    PONYTAIL("Ponytail"),
    LONG_WAVY("Long Wavy"),
    TWIN_BRAIDS("Twin Braids");

    companion object {
        fun fromRaw(raw: String?): HairStyle =
            entries.firstOrNull { it.name == raw } ?: PARTED
    }
}

enum class FacialHair(val label: String) {
    NONE("Clean"),
    MUSTACHE("Mustache"),
    GOATEE("Goatee"),
    BEARD("Full Beard");

    companion object {
        fun fromRaw(raw: String?): FacialHair =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class CharacterProp(val label: String) {
    NONE("None"),
    CLIPBOARD("Clipboard"),
    MUG("Coffee Mug"),
    STETHOSCOPE("Stethoscope"),
    PHONE("Smartphone"),
    RULER("Wooden Ruler"),
    FOLDER("File Folder"),
    DUMBBELL("Dumbbell");

    companion object {
        fun fromRaw(raw: String?): CharacterProp =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class CaptionStyle(val label: String) {
    CLASSIC("Classic Bar"),
    WORD_POP("Word Pop"),
    VIRAL_BOUNCE("Viral Bounce Pop");

    companion object {
        fun fromRaw(raw: String?): CaptionStyle =
            entries.firstOrNull { it.name == raw } ?: WORD_POP
    }
}

enum class CaptionFontStyle(val label: String) {
    BOLD_SANS("Bold Modern"),
    COMIC_PUNCH("Comic Punch"),
    TYPEWRITER_MONO("Typewriter Mono"),
    SERIF_DRAMA("Drama Serif");

    companion object {
        fun fromRaw(raw: String?): CaptionFontStyle =
            entries.firstOrNull { it.name == raw } ?: BOLD_SANS
    }
}

enum class CaptionPosition(val label: String) {
    BOTTOM_THIRD("Bottom Third"),
    CENTER_POP("Center Pop");

    companion object {
        fun fromRaw(raw: String?): CaptionPosition =
            entries.firstOrNull { it.name == raw } ?: BOTTOM_THIRD
    }
}

enum class ExportResolution(val label: String, val width: Int, val height: Int) {
    HD_720P("720x1280 HD", 720, 1280),
    FHD_1080P("1080x1920 Full HD", 1080, 1920);

    companion object {
        fun fromRaw(raw: String?): ExportResolution =
            entries.firstOrNull { it.name == raw } ?: HD_720P
    }
}

enum class ReelLanguage(val label: String) {
    ENGLISH("English"),
    HINGLISH("Hinglish"),
    HINDI("Hindi");

    companion object {
        fun fromLabel(raw: String?): ReelLanguage =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: ENGLISH
    }
}

enum class NichePreset(val label: String, val defaultSetting: RoomSetting) {
    SCHOOL_TEACHER("School & Teacher", RoomSetting.CLASSROOM),
    CORPORATE_OFFICE("Corporate Office", RoomSetting.OFFICE),
    DOCTOR_PATIENT("Doctor & Patient", RoomSetting.HOSPITAL),
    JOB_INTERVIEW("Job Interview", RoomSetting.INTERVIEW_ROOM),
    DESI_PARENTS("Desi Parents & Marks", RoomSetting.LIVING_ROOM),
    GYM_TRAINER("Gym & Trainer", RoomSetting.GYM),
    CAFE_CUSTOMER("Cafe & Customer", RoomSetting.COFFEE_SHOP),
    HOSTEL_ROOMMATES("Hostel & Roommates", RoomSetting.LIVING_ROOM),
    KITCHEN_COOKING("Kitchen & Chai Chaos", RoomSetting.KITCHEN),
    CRICKET_SPORTS("Cricket & Gully Sports", RoomSetting.STREET_PARK),
    GAMING_VLOG("Gaming & Reel Addiction", RoomSetting.GAMING_ROOM),
    DANCE_PARTY("Wedding & Dance Practice", RoomSetting.LIVING_ROOM);
}

enum class TwistStructure(val label: String) {
    RULE_OF_3("Escalating Rule-of-3 Loop"),
    DEADPAN_LITERAL("Deadpan Literal Punchline"),
    PLOT_TWIST("Plot Twist Reversal"),
    SARCASTIC_ROAST("Sarcastic Roast");
}

data class VoiceActorOption(
    val id: String,
    val displayName: String,
    val tone: String,
    val gender: CharacterGender
)

object VoiceCatalog {
    val actors = listOf(
        VoiceActorOption("Orus", "Orus", "Firm Male", CharacterGender.MALE),
        VoiceActorOption("Puck", "Puck", "Upbeat Male", CharacterGender.MALE),
        VoiceActorOption("Fenrir", "Fenrir", "Excitable Male", CharacterGender.MALE),
        VoiceActorOption("Charon", "Charon", "Deadpan Male", CharacterGender.MALE),
        VoiceActorOption("Algenib", "Algenib", "Gravelly Male", CharacterGender.MALE),
        VoiceActorOption("Kore", "Kore", "Firm Female", CharacterGender.FEMALE),
        VoiceActorOption("Zephyr", "Zephyr", "Bright Female", CharacterGender.FEMALE),
        VoiceActorOption("Aoede", "Aoede", "Breezy Female", CharacterGender.FEMALE),
        VoiceActorOption("Leda", "Leda", "Youthful Female", CharacterGender.FEMALE),
        VoiceActorOption("Sulafat", "Sulafat", "Warm Female", CharacterGender.FEMALE)
    )

    fun isFemaleVoice(voiceId: String): Boolean =
        actors.firstOrNull { it.id.equals(voiceId, ignoreCase = true) }?.gender == CharacterGender.FEMALE

    fun defaultVoiceFor(gender: CharacterGender): String =
        if (gender == CharacterGender.FEMALE) "Leda" else "Puck"
}

data class CharacterProfile(
    val slot: SpeakerSlot = SpeakerSlot.A,
    val name: String = "Alex",
    val role: String = "Teacher",
    val gender: CharacterGender = CharacterGender.MALE,
    val voiceName: String = "Orus",
    val voiceSpeed: Float = 1.0f,
    val hairStyle: HairStyle = HairStyle.PARTED,
    val hasGlasses: Boolean = false,
    val facialHair: FacialHair = FacialHair.NONE,
    val prop: CharacterProp = CharacterProp.NONE
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("slot", slot.name)
        put("name", name)
        put("role", role)
        put("gender", gender.name)
        put("voiceName", voiceName)
        put("voiceSpeed", voiceSpeed.coerceIn(0.85f, 1.30f).toDouble())
        put("hairStyle", hairStyle.name)
        put("hasGlasses", hasGlasses)
        put("facialHair", facialHair.name)
        put("prop", prop.name)
    }

    companion object {
        fun fromJson(obj: JSONObject): CharacterProfile {
            val voice = obj.optString("voiceName", "Puck").ifBlank { "Puck" }
            val inferredGender = if (obj.has("gender")) {
                CharacterGender.fromRaw(obj.optString("gender"))
            } else if (VoiceCatalog.isFemaleVoice(voice)) {
                CharacterGender.FEMALE
            } else {
                CharacterGender.MALE
            }
            return CharacterProfile(
                slot = SpeakerSlot.fromRaw(obj.optString("slot")),
                name = obj.optString("name", "Character").ifBlank { "Character" },
                role = obj.optString("role", "Lead").ifBlank { "Lead" },
                gender = inferredGender,
                voiceName = voice,
                voiceSpeed = obj.optDouble("voiceSpeed", 1.0).toFloat().coerceIn(0.85f, 1.30f),
                hairStyle = HairStyle.fromRaw(obj.optString("hairStyle")),
                hasGlasses = obj.optBoolean("hasGlasses", false),
                facialHair = FacialHair.fromRaw(obj.optString("facialHair")),
                prop = CharacterProp.fromRaw(obj.optString("prop"))
            )
        }
    }
}

data class ReelShot(
    val shotId: String = UUID.randomUUID().toString().take(8),
    val speaker: SpeakerSlot = SpeakerSlot.A,
    val framing: CameraFraming = CameraFraming.MEDIUM_TWO_SHOT,
    val dialogue: String = "",
    val phoneticHint: String = "",
    val speechBubble: String = "",
    val transitionCard: String = "",
    val moodPose: MoodPose = MoodPose.NEUTRAL,
    val activity: CharacterActivity = CharacterActivity.TALKING,
    val sticker: MemeSticker = MemeSticker.NONE,
    val sfx: ComicSfx = ComicSfx.NONE,
    val durationMs: Long = 2600L,
    val audioSourceLabel: String = "Not Voiced"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("shotId", shotId)
        put("speaker", speaker.name)
        put("framing", framing.name)
        put("dialogue", dialogue)
        put("phoneticHint", phoneticHint)
        put("speechBubble", speechBubble)
        put("transitionCard", transitionCard)
        put("moodPose", moodPose.name)
        put("activity", activity.name)
        put("sticker", sticker.name)
        put("sfx", sfx.name)
        put("durationMs", durationMs.coerceIn(700L, 30000L))
        put("audioSourceLabel", audioSourceLabel)
    }

    companion object {
        fun fromJson(obj: JSONObject): ReelShot = ReelShot(
            shotId = obj.optString("shotId").ifBlank { UUID.randomUUID().toString().take(8) },
            speaker = SpeakerSlot.fromRaw(obj.optString("speaker")),
            framing = CameraFraming.fromRaw(obj.optString("framing")),
            dialogue = obj.optString("dialogue", ""),
            phoneticHint = obj.optString("phoneticHint", ""),
            speechBubble = obj.optString("speechBubble", ""),
            transitionCard = obj.optString("transitionCard", ""),
            moodPose = MoodPose.fromRaw(obj.optString("moodPose")),
            activity = CharacterActivity.fromRaw(obj.optString("activity")),
            sticker = MemeSticker.fromRaw(obj.optString("sticker")),
            sfx = ComicSfx.fromRaw(obj.optString("sfx")),
            durationMs = obj.optLong("durationMs", 2600L).coerceIn(700L, 30000L),
            audioSourceLabel = obj.optString("audioSourceLabel", "Not Voiced")
        )
    }
}

data class ClonedStyleBlueprint(
    val summary: String,
    val averageShotDurationMs: Long,
    val pacingStyle: String,
    val recommendedFramings: List<String>,
    val comedyFormula: String
)

data class ReelProject(
    val id: String = "",
    val title: String = "Assignment Excuse #404",
    val socialCaption: String = "When the teacher checks homework after 3 days...",
    val hashtags: List<String> = listOf("#stickman", "#comedyreels", "#schoolmemes", "#inkreel"),
    val niche: String = NichePreset.SCHOOL_TEACHER.label,
    val twistStructure: String = TwistStructure.RULE_OF_3.label,
    val language: String = ReelLanguage.ENGLISH.label,
    val setting: RoomSetting = RoomSetting.CLASSROOM,
    val colorTheme: ReelColorTheme = ReelColorTheme.CLASSIC_MONO,
    val bgmMood: BgmMood = BgmMood.QUIRKY_COMEDY,
    val bgmVolume: Float = 0.25f,
    val captionStyle: CaptionStyle = CaptionStyle.WORD_POP,
    val captionFontStyle: CaptionFontStyle = CaptionFontStyle.BOLD_SANS,
    val captionPosition: CaptionPosition = CaptionPosition.BOTTOM_THIRD,
    val introHookBanner: String = "WAIT FOR THE END!",
    val enableOutro: Boolean = true,
    val outroText: String = "PART 2? FOLLOW NOW!",
    val watermarkHandle: String = "@inkreel.studio",
    val exportResolution: ExportResolution = ExportResolution.HD_720P,
    val clonedStyleSummary: String = "",
    val characters: List<CharacterProfile> = defaultCast(),
    val shots: List<ReelShot> = defaultShots(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val shotsDurationMs: Long
        get() = shots.sumOf { it.durationMs }.coerceAtLeast(1000L)

    val outroDurationMs: Long
        get() = if (enableOutro && outroText.isNotBlank()) 1700L else 0L

    val totalDurationMs: Long
        get() = shotsDurationMs + outroDurationMs

    fun characterFor(slot: SpeakerSlot): CharacterProfile? = when (slot) {
        SpeakerSlot.A -> characters.getOrNull(0)
        SpeakerSlot.B -> characters.getOrNull(1)
        SpeakerSlot.C -> characters.getOrNull(2)
        SpeakerSlot.SILENT -> null
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("socialCaption", socialCaption)
        put("hashtags", JSONArray(hashtags))
        put("niche", niche)
        put("twistStructure", twistStructure)
        put("language", language)
        put("setting", setting.name)
        put("colorTheme", colorTheme.name)
        put("bgmMood", bgmMood.name)
        put("bgmVolume", bgmVolume.coerceIn(0f, 1f).toDouble())
        put("captionStyle", captionStyle.name)
        put("captionFontStyle", captionFontStyle.name)
        put("captionPosition", captionPosition.name)
        put("introHookBanner", introHookBanner)
        put("enableOutro", enableOutro)
        put("outroText", outroText)
        put("watermarkHandle", watermarkHandle)
        put("exportResolution", exportResolution.name)
        put("clonedStyleSummary", clonedStyleSummary)
        put("characters", JSONArray().apply { characters.forEach { put(it.toJson()) } })
        put("shots", JSONArray().apply { shots.forEach { put(it.toJson()) } })
        put("createdAtMs", createdAtMs)
        put("updatedAtMs", updatedAtMs)
    }

    companion object {
        const val MAX_SHOTS = 40

        fun fromJson(obj: JSONObject): ReelProject {
            val tagsArr = obj.optJSONArray("hashtags") ?: JSONArray()
            val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }

            val charsArr = obj.optJSONArray("characters") ?: JSONArray()
            val chars = (0 until charsArr.length()).map {
                CharacterProfile.fromJson(charsArr.getJSONObject(it))
            }.ifEmpty { defaultCast() }

            val shotsArr = obj.optJSONArray("shots") ?: JSONArray()
            val shotsList = (0 until shotsArr.length()).map {
                ReelShot.fromJson(shotsArr.getJSONObject(it))
            }.ifEmpty { defaultShots() }

            return ReelProject(
                id = obj.optString("id"),
                title = obj.optString("title", "Untitled InkReel").ifBlank { "Untitled InkReel" },
                socialCaption = obj.optString("socialCaption", ""),
                hashtags = tags,
                niche = obj.optString("niche", NichePreset.SCHOOL_TEACHER.label),
                twistStructure = obj.optString("twistStructure", TwistStructure.RULE_OF_3.label),
                language = ReelLanguage.fromLabel(obj.optString("language")).label,
                setting = RoomSetting.fromRaw(obj.optString("setting")),
                colorTheme = ReelColorTheme.fromRaw(obj.optString("colorTheme")),
                bgmMood = BgmMood.fromRaw(obj.optString("bgmMood")),
                bgmVolume = obj.optDouble("bgmVolume", 0.25).toFloat().coerceIn(0f, 1f),
                captionStyle = CaptionStyle.fromRaw(obj.optString("captionStyle")),
                captionFontStyle = CaptionFontStyle.fromRaw(obj.optString("captionFontStyle")),
                captionPosition = CaptionPosition.fromRaw(obj.optString("captionPosition")),
                introHookBanner = obj.optString("introHookBanner", ""),
                enableOutro = obj.optBoolean("enableOutro", true),
                outroText = obj.optString("outroText", "PART 2? FOLLOW NOW!"),
                watermarkHandle = obj.optString("watermarkHandle", "@inkreel.studio"),
                exportResolution = ExportResolution.fromRaw(obj.optString("exportResolution")),
                clonedStyleSummary = obj.optString("clonedStyleSummary", ""),
                characters = chars,
                shots = shotsList,
                createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
                updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
            )
        }

        fun defaultCast(): List<CharacterProfile> = listOf(
            CharacterProfile(
                slot = SpeakerSlot.A,
                name = "Mr. Verma",
                role = "Strict Teacher",
                gender = CharacterGender.MALE,
                voiceName = "Orus",
                voiceSpeed = 1.0f,
                hairStyle = HairStyle.BALD,
                hasGlasses = true,
                facialHair = FacialHair.MUSTACHE,
                prop = CharacterProp.RULER
            ),
            CharacterProfile(
                slot = SpeakerSlot.B,
                name = "Ananya",
                role = "Witty Student",
                gender = CharacterGender.FEMALE,
                voiceName = "Leda",
                voiceSpeed = 1.04f,
                hairStyle = HairStyle.PONYTAIL,
                hasGlasses = false,
                facialHair = FacialHair.NONE,
                prop = CharacterProp.CLIPBOARD
            ),
            CharacterProfile(
                slot = SpeakerSlot.C,
                name = "Principal Ma'am",
                role = "Surprise Guest",
                gender = CharacterGender.FEMALE,
                voiceName = "Kore",
                voiceSpeed = 1.0f,
                hairStyle = HairStyle.BUN,
                hasGlasses = true,
                facialHair = FacialHair.NONE,
                prop = CharacterProp.FOLDER
            )
        )

        fun defaultShots(): List<ReelShot> = listOf(
            ReelShot(
                shotId = "s1",
                speaker = SpeakerSlot.A,
                framing = CameraFraming.WIDE_DOORWAY,
                dialogue = "Alright class, submit your math homework right now!",
                speechBubble = "HOMEWORK NOW!",
                moodPose = MoodPose.ANGRY,
                activity = CharacterActivity.WALKING_IN,
                sfx = ComicSfx.DOOR_SLAM,
                durationMs = 2700L
            ),
            ReelShot(
                shotId = "s2",
                speaker = SpeakerSlot.B,
                framing = CameraFraming.CLOSE_UP_B,
                dialogue = "Sir, my dog ate my notebook... and then downloaded PDF proof.",
                speechBubble = "DOG ATE IT!",
                moodPose = MoodPose.SWEATING,
                activity = CharacterActivity.CRYING_DRAMA,
                sfx = ComicSfx.NERVOUS_RATTLE,
                durationMs = 3100L
            ),
            ReelShot(
                shotId = "s3",
                speaker = SpeakerSlot.A,
                framing = CameraFraming.DESK_SLAM,
                dialogue = "You told me that exact excuse on Monday!",
                speechBubble = "AGAIN?!",
                moodPose = MoodPose.FURIOUS,
                sfx = ComicSfx.DESK_SLAM,
                durationMs = 2400L
            ),
            ReelShot(
                shotId = "s4",
                speaker = SpeakerSlot.SILENT,
                framing = CameraFraming.TIME_SKIP_CARD,
                dialogue = "",
                transitionCard = "NEXT DAY",
                moodPose = MoodPose.NEUTRAL,
                sfx = ComicSfx.TIME_SKIP_WHOOSH,
                durationMs = 1500L
            ),
            ReelShot(
                shotId = "s5",
                speaker = SpeakerSlot.B,
                framing = CameraFraming.SITTING_AT_DESK,
                dialogue = "Sir, good news! Today I brought the dog instead. Ask him.",
                speechBubble = "MEET TOMMY!",
                moodPose = MoodPose.SMUG,
                activity = CharacterActivity.CLAPPING_CHEERING,
                sticker = MemeSticker.DEAL_WITH_IT_SHADES,
                sfx = ComicSfx.DRAMATIC_STING,
                durationMs = 3100L
            ),
            ReelShot(
                shotId = "s6",
                speaker = SpeakerSlot.A,
                framing = CameraFraming.PROP_DROP,
                dialogue = "Wait... why is the dog holding a calculator?!",
                speechBubble = "WHAT?!",
                moodPose = MoodPose.SHOCKED,
                sticker = MemeSticker.QUESTION_MARKS,
                sfx = ComicSfx.CLIPBOARD_DROP,
                durationMs = 2500L
            ),
            ReelShot(
                shotId = "s7",
                speaker = SpeakerSlot.B,
                framing = CameraFraming.SLAPSTICK_CHASE,
                dialogue = "RUN TOMMY! HE FOUND OUT YOU FAILED ALGEBRA TOO!",
                speechBubble = "RUN!!",
                moodPose = MoodPose.NERVOUS,
                sfx = ComicSfx.CARTOON_RUN,
                durationMs = 2400L
            ),
            ReelShot(
                shotId = "s8",
                speaker = SpeakerSlot.SILENT,
                framing = CameraFraming.BONK_ENDING,
                dialogue = "BONK!",
                speechBubble = "BONK!",
                moodPose = MoodPose.SHOCKED,
                sfx = ComicSfx.CARTOON_BONK,
                durationMs = 1700L
            )
        )
    }
}

object SampleScripts {
    val englishSample = """
        A (Boss): Why are you 40 minutes late to the morning standup?
        B (Dev): Sir, my alarm rang, so I muted it in my dream thinking it was a Slack huddle.
        A (Boss): That makes zero sense. Fix the prod bug in five minutes!
        [Next Day]
        B (Dev): Good news! The bug is gone.
        A (Boss): Really? How?
        B (Dev): I deleted the entire login screen. No users, no bugs.
        C (CEO): Promote him immediately.
    """.trimIndent()

    val hinglishSample = """
        A (Papa): Sharma ji ke bete ke 98 percent aaye hain, tumhare 62 kyun?
        B (Beti): Papa, unke ghar mein Wi-Fi nahi hai, aur hamare ghar mein distractions zyada hain.
        A (Papa): Accha? Abhi router fekta hoon bahar!
        [Day 3]
        B (Beti): Papa, Sharma ji ke bete ne Naya Startup khola hai... aur main uski Boss hoon!
        A (Papa): Kya baat hai! Kaise?!
        B (Beti): Kyunki uske paas Wi-Fi nahi tha, maine apna hotspot bech diya!
    """.trimIndent()
}
