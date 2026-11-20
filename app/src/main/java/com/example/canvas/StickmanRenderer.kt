package com.example.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.example.model.CameraFraming
import com.example.model.CaptionFontStyle
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.CharacterActivity
import com.example.model.CharacterGender
import com.example.model.CharacterProfile
import com.example.model.CharacterProp
import com.example.model.FacialHair
import com.example.model.HairStyle
import com.example.model.MemeSticker
import com.example.model.MoodPose
import com.example.model.ReelColorTheme
import com.example.model.ReelProject
import com.example.model.ReelShot
import com.example.model.SpeakerSlot
import java.util.EnumMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object StickmanRenderer {

    private val WHITESPACE_REGEX = Regex("\\s+")

    private val boldDefaultTf: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val boldSansTf: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val comicPunchTf: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
    private val monoBoldTf: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val serifBoldTf: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    private data class ThemeColors(
        val wallColor: Int,
        val floorColor: Int,
        val inkColor: Int,
        val accentColor: Int,
        val whiteFillColor: Int,
        val celShadeColor: Int,
        val shadowColor: Int,
        val bubbleStrokeColor: Int
    )

    private val themeColorCache = EnumMap<ReelColorTheme, ThemeColors>(ReelColorTheme::class.java)

    private fun colorsFor(theme: ReelColorTheme): ThemeColors {
        return themeColorCache[theme] ?: ThemeColors(
            wallColor = Color.parseColor(theme.wallHex),
            floorColor = Color.parseColor(theme.floorHex),
            inkColor = Color.parseColor(theme.inkHex),
            accentColor = Color.parseColor(theme.accentHex),
            whiteFillColor = if (theme.isDarkStage) Color.parseColor("#27272A") else Color.WHITE,
            celShadeColor = if (theme.isDarkStage) Color.parseColor("#3F3F46") else Color.parseColor("#E4E4E7"),
            shadowColor = if (theme.isDarkStage) Color.parseColor("#44000000") else Color.parseColor("#22000000"),
            bubbleStrokeColor = if (theme.isDarkStage) Color.parseColor(theme.accentHex) else Color.parseColor("#09090B")
        ).also { themeColorCache[theme] = it }
    }

    private class StickPaints {
        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val inkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val shadowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val celShadeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val sweatFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.FILL
        }
        val angerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val accentFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val captionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DD09090B")
            style = Paint.Style.FILL
        }
        val popWordBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = boldDefaultTf
        }
        val splitLeftBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val splitRightBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val shadesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#09090B")
            style = Paint.Style.FILL
        }
        val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
        }
        val goldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FACC15")
            style = Paint.Style.FILL
        }
        val darkCardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#09090B")
            style = Paint.Style.FILL
        }
        val bannerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EE09090B")
            style = Paint.Style.FILL
        }
        val pureWhiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.RIGHT
            typeface = monoBoldTf
        }
        val pathA = Path()
        val pathB = Path()
        val rect = RectF()
    }

    private val threadPaints = object : ThreadLocal<StickPaints>() {
        override fun initialValue(): StickPaints = StickPaints()
    }

    data class ActiveShotState(
        val shotIndex: Int,
        val shot: ReelShot,
        val localTimeMs: Long,
        val progress: Float,
        val isOutro: Boolean = false
    )

    fun resolveShotAt(project: ReelProject, playheadMs: Long): ActiveShotState {
        val shots = project.shots
        if (shots.isEmpty()) {
            return ActiveShotState(0, ReelShot(), 0L, 0f)
        }
        val shotsTotal = project.shotsDurationMs
        if (project.outroDurationMs > 0L && playheadMs >= shotsTotal) {
            val local = (playheadMs - shotsTotal).coerceIn(0L, project.outroDurationMs)
            val prog = (local.toFloat() / project.outroDurationMs.toFloat()).coerceIn(0f, 1f)
            return ActiveShotState(shots.lastIndex, shots.last(), local, prog, isOutro = true)
        }
        var acc = 0L
        for (i in shots.indices) {
            val s = shots[i]
            val end = acc + s.durationMs
            if (playheadMs < end || i == shots.lastIndex) {
                val local = (playheadMs - acc).coerceIn(0L, s.durationMs)
                val prog = (local.toFloat() / s.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                return ActiveShotState(i, s, local, prog)
            }
            acc = end
        }
        val last = shots.last()
        return ActiveShotState(shots.lastIndex, last, last.durationMs, 1f)
    }

    private fun applyThemePaints(p: StickPaints, colors: ThemeColors) {
        p.inkPaint.color = colors.inkColor
        p.inkFill.color = colors.inkColor
        p.whiteFill.color = colors.whiteFillColor
        p.celShadeFill.color = colors.celShadeColor
        p.shadowFill.color = colors.shadowColor
        p.angerPaint.color = colors.accentColor
        p.accentFill.color = colors.accentColor
    }

    fun renderFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        project: ReelProject,
        playheadMs: Long,
        lipAmplitude: Float,
        showCaptions: Boolean,
        showSpeechBubbles: Boolean
    ) {
        val p = threadPaints.get()!!
        val unit = width / 720f
        val theme = project.colorTheme
        val colors = colorsFor(theme)
        applyThemePaints(p, colors)

        val state = resolveShotAt(project, playheadMs)
        val shot = state.shot
        val prog = state.progress
        val timeSec = playheadMs / 1000f

        // Animated Outro Follow/Subscribe Card
        if (state.isOutro) {
            drawOutroCard(canvas, p, colors, width, height, unit, project.outroText, project.watermarkHandle, prog)
            return
        }

        // Full-frame Time-Skip Card
        if (shot.framing == CameraFraming.TIME_SKIP_CARD) {
            drawTimeSkipCard(canvas, p, colors, width, height, unit, shot.transitionCard.ifBlank { "NEXT DAY" }, prog)
            drawWatermark(canvas, p, width, height, unit, project.watermarkHandle, darkBg = true)
            return
        }

        // Bounded stage clear inside (0, 0, width, height) so Compose RecordingCanvas never bleeds outside viewport
        p.splitLeftBg.color = colors.wallColor
        canvas.drawRect(0f, 0f, width, height, p.splitLeftBg)
        canvas.save()

        // Camera framing & screen shake
        val shakeX: Float
        val shakeY: Float
        if (shot.framing == CameraFraming.DESK_SLAM && prog < 0.45f) {
            val decay = (1f - prog / 0.45f)
            shakeX = sin(timeSec * 75f) * 16f * unit * decay
            shakeY = cos(timeSec * 68f) * 14f * unit * decay
        } else {
            shakeX = 0f
            shakeY = 0f
        }

        val (zoom, focusX, focusY) = when (shot.framing) {
            CameraFraming.CLOSE_UP_A -> Triple(1.75f, width * 0.30f, height * 0.47f)
            CameraFraming.CLOSE_UP_B -> Triple(1.75f, width * 0.70f, height * 0.47f)
            CameraFraming.CLOSE_UP_C -> Triple(1.75f, width * 0.50f, height * 0.45f)
            CameraFraming.OVER_THE_SHOULDER -> Triple(1.38f, width * 0.66f, height * 0.50f)
            CameraFraming.DESK_SLAM -> Triple(1.22f, width * 0.50f, height * 0.54f)
            else -> Triple(1.0f, width * 0.5f, height * 0.5f)
        }

        canvas.translate(width * 0.5f + shakeX, height * 0.5f + shakeY)
        canvas.scale(zoom, zoom)
        canvas.translate(-focusX, -focusY)

        val doorOpen = if (shot.framing == CameraFraming.WIDE_DOORWAY) {
            (prog * 2.5f).coerceIn(0f, 1f)
        } else {
            0f
        }

        val charA = project.characters.getOrNull(0) ?: CharacterProfile(slot = SpeakerSlot.A)
        val charB = project.characters.getOrNull(1) ?: CharacterProfile(slot = SpeakerSlot.B)
        val charC = project.characters.getOrNull(2)
        val floorY = height * 0.72f

        if (shot.framing == CameraFraming.SPLIT_PHONE_CALL) {
            drawSplitPhoneCallScene(
                canvas = canvas,
                p = p,
                colors = colors,
                width = width,
                height = height,
                floorY = floorY,
                unit = unit,
                charA = charA,
                charB = charB,
                shot = shot,
                lipAmp = lipAmplitude,
                timeSec = timeSec
            )
        } else {
            RoomBackgroundRenderer.drawRoom(canvas, width, height, project.setting, doorOpen, timeSec, theme)

            when (shot.framing) {
                CameraFraming.SLAPSTICK_CHASE -> {
                    val runX = -0.15f + prog * 1.3f
                    drawStickman(
                        canvas = canvas,
                        p = p,
                        cx = width * runX,
                        footY = floorY + 24f * unit,
                        scale = 1.0f * unit,
                        profile = charB,
                        mood = MoodPose.NERVOUS,
                        isSpeaking = shot.speaker == SpeakerSlot.B,
                        lipAmp = lipAmplitude,
                        timeSec = timeSec,
                        facingRight = true,
                        running = true
                    )
                    drawStickman(
                        canvas = canvas,
                        p = p,
                        cx = width * (runX - 0.24f),
                        footY = floorY + 24f * unit,
                        scale = 1.04f * unit,
                        profile = charA,
                        mood = MoodPose.FURIOUS,
                        isSpeaking = shot.speaker == SpeakerSlot.A,
                        lipAmp = lipAmplitude,
                        timeSec = timeSec,
                        facingRight = true,
                        running = true
                    )
                    drawSpeedLines(canvas, p, width, height, unit, timeSec)
                }
                CameraFraming.BONK_ENDING -> {
                    drawBonkDustCloud(canvas, p, colors, width * 0.50f, floorY - 80f * unit, unit, prog)
                }
                else -> {
                    val walkShift = if (shot.framing == CameraFraming.WIDE_DOORWAY) {
                        (0.14f + (prog * 1.4f).coerceAtMost(1f) * 0.17f)
                    } else {
                        0.30f
                    }

                    val ax = width * walkShift
                    val bx = width * 0.70f
                    val deskMode = shot.framing == CameraFraming.SITTING_AT_DESK || shot.framing == CameraFraming.DESK_SLAM

                    if (shot.speaker == SpeakerSlot.C && charC != null) {
                        drawStickman(
                            canvas = canvas,
                            p = p,
                            cx = width * 0.50f,
                            footY = floorY + 18f * unit,
                            scale = 1.02f * unit,
                            profile = charC,
                            mood = shot.moodPose,
                            activity = shot.activity,
                            isSpeaking = true,
                            lipAmp = lipAmplitude,
                            timeSec = timeSec,
                            facingRight = false
                        )
                    } else {
                        val moodA = if (shot.speaker == SpeakerSlot.A || shot.framing == CameraFraming.DESK_SLAM) shot.moodPose else MoodPose.NEUTRAL
                        val moodB = if (shot.speaker == SpeakerSlot.B || shot.framing == CameraFraming.PROP_DROP) shot.moodPose else MoodPose.NEUTRAL
                        val actA = if (shot.speaker == SpeakerSlot.A || shot.speaker == SpeakerSlot.SILENT) shot.activity else CharacterActivity.TALKING
                        val actB = if (shot.speaker == SpeakerSlot.B) shot.activity else CharacterActivity.TALKING

                        drawStickman(
                            canvas = canvas,
                            p = p,
                            cx = ax,
                            footY = floorY + 20f * unit,
                            scale = 1.03f * unit,
                            profile = charA,
                            mood = moodA,
                            activity = actA,
                            isSpeaking = shot.speaker == SpeakerSlot.A,
                            lipAmp = lipAmplitude,
                            timeSec = timeSec,
                            facingRight = true,
                            propDropped = shot.framing == CameraFraming.PROP_DROP && shot.speaker == SpeakerSlot.A,
                            propDropProgress = prog,
                            deskSlam = shot.framing == CameraFraming.DESK_SLAM
                        )

                        drawStickman(
                            canvas = canvas,
                            p = p,
                            cx = bx,
                            footY = floorY + 20f * unit,
                            scale = 1.0f * unit,
                            profile = charB,
                            mood = moodB,
                            activity = actB,
                            isSpeaking = shot.speaker == SpeakerSlot.B,
                            lipAmp = lipAmplitude,
                            timeSec = timeSec,
                            facingRight = false,
                            propDropped = shot.framing == CameraFraming.PROP_DROP && shot.speaker == SpeakerSlot.B,
                            propDropProgress = prog
                        )
                    }

                    if (deskMode) {
                        drawForegroundDesk(canvas, p, width, floorY, unit, shot.framing == CameraFraming.DESK_SLAM, prog)
                    }

                    if (shot.framing == CameraFraming.OVER_THE_SHOULDER) {
                        canvas.drawCircle(width * 0.14f, height * 0.69f, 110f * unit, p.inkFill)
                        canvas.drawCircle(width * 0.16f, height * 0.52f, 64f * unit, p.inkFill)
                    }
                }
            }
        }

        // Meme Sticker Overlay on active speaker
        if (shot.sticker != MemeSticker.NONE && shot.framing != CameraFraming.BONK_ENDING) {
            val targetX = when {
                shot.framing == CameraFraming.SPLIT_PHONE_CALL && shot.speaker == SpeakerSlot.A -> width * 0.25f
                shot.framing == CameraFraming.SPLIT_PHONE_CALL && shot.speaker == SpeakerSlot.B -> width * 0.75f
                shot.speaker == SpeakerSlot.A -> width * 0.30f
                shot.speaker == SpeakerSlot.B -> width * 0.70f
                else -> width * 0.50f
            }
            val headCenterY = floorY - 300f * unit
            drawMemeSticker(canvas, p, colors, targetX, headCenterY, unit, shot.sticker, timeSec)
        }

        // Speech bubble in world space if enabled
        if (showSpeechBubbles && shot.speechBubble.isNotBlank() && shot.framing != CameraFraming.BONK_ENDING) {
            val bubbleX = when (shot.speaker) {
                SpeakerSlot.A -> if (shot.framing == CameraFraming.SPLIT_PHONE_CALL) width * 0.26f else width * 0.34f
                SpeakerSlot.B -> if (shot.framing == CameraFraming.SPLIT_PHONE_CALL) width * 0.74f else width * 0.66f
                else -> width * 0.50f
            }
            drawSpeechBubble(canvas, p, colors, bubbleX, height * 0.16f, width, unit, shot.speechBubble)
        }

        canvas.restore()

        // Screen-space Intro Hook Banner (top viral hook strip)
        if (project.introHookBanner.isNotBlank()) {
            drawIntroHookBanner(canvas, p, colors, width, unit, project.introHookBanner)
        }

        // Screen-space Captions & Watermark
        if (showCaptions && shot.dialogue.isNotBlank() && shot.framing != CameraFraming.BONK_ENDING) {
            drawCaptions(
                canvas = canvas,
                p = p,
                colors = colors,
                w = width,
                h = height,
                unit = unit,
                text = shot.dialogue,
                progress = prog,
                style = project.captionStyle,
                fontStyle = project.captionFontStyle,
                position = project.captionPosition
            )
        }
        drawWatermark(canvas, p, width, height, unit, project.watermarkHandle, darkBg = theme.isDarkStage)
    }

    private fun drawSplitPhoneCallScene(
        canvas: Canvas,
        p: StickPaints,
        colors: ThemeColors,
        width: Float,
        height: Float,
        floorY: Float,
        unit: Float,
        charA: CharacterProfile,
        charB: CharacterProfile,
        shot: ReelShot,
        lipAmp: Float,
        timeSec: Float
    ) {
        p.splitLeftBg.color = colors.wallColor
        p.splitRightBg.color = colors.floorColor
        canvas.drawRect(0f, 0f, width * 0.5f, height, p.splitLeftBg)
        canvas.drawRect(width * 0.5f, 0f, width, height, p.splitRightBg)

        canvas.drawRect(0f, floorY, width * 0.5f, height, p.splitRightBg)
        p.inkPaint.strokeWidth = 4f * unit
        canvas.drawLine(0f, floorY, width, floorY, p.inkPaint)

        // Left Character A on Phone
        drawStickman(
            canvas = canvas,
            p = p,
            cx = width * 0.26f,
            footY = floorY + 20f * unit,
            scale = 0.96f * unit,
            profile = charA.copy(prop = CharacterProp.PHONE),
            mood = if (shot.speaker == SpeakerSlot.A) shot.moodPose else MoodPose.NEUTRAL,
            isSpeaking = shot.speaker == SpeakerSlot.A,
            lipAmp = lipAmp,
            timeSec = timeSec,
            facingRight = true,
            holdingPhoneToEar = true
        )

        // Right Character B on Phone
        drawStickman(
            canvas = canvas,
            p = p,
            cx = width * 0.74f,
            footY = floorY + 20f * unit,
            scale = 0.96f * unit,
            profile = charB.copy(prop = CharacterProp.PHONE),
            mood = if (shot.speaker == SpeakerSlot.B) shot.moodPose else MoodPose.NEUTRAL,
            isSpeaking = shot.speaker == SpeakerSlot.B,
            lipAmp = lipAmp,
            timeSec = timeSec,
            facingRight = false,
            holdingPhoneToEar = true
        )

        // Center split divider + live call badge
        p.inkPaint.strokeWidth = 7f * unit
        canvas.drawLine(width * 0.5f, 0f, width * 0.5f, height, p.inkPaint)

        val badgeRect = p.rect.apply {
            set(width * 0.5f - 68f * unit, height * 0.14f, width * 0.5f + 68f * unit, height * 0.14f + 40f * unit)
        }
        canvas.drawRoundRect(badgeRect, 20f * unit, 20f * unit, p.accentFill)
        p.textPaint.color = Color.WHITE
        p.textPaint.textSize = 20f * unit
        p.textPaint.typeface = boldDefaultTf
        canvas.drawText("ON CALL", badgeRect.centerX(), badgeRect.centerY() + 7f * unit, p.textPaint)
    }

    private fun drawStickman(
        canvas: Canvas,
        p: StickPaints,
        cx: Float,
        footY: Float,
        scale: Float,
        profile: CharacterProfile,
        mood: MoodPose,
        isSpeaking: Boolean,
        lipAmp: Float,
        timeSec: Float,
        facingRight: Boolean,
        running: Boolean = false,
        propDropped: Boolean = false,
        propDropProgress: Float = 0f,
        deskSlam: Boolean = false,
        holdingPhoneToEar: Boolean = false,
        activity: CharacterActivity = CharacterActivity.TALKING
    ) {
        val dir = if (facingRight) 1f else -1f
        val jitterX = when {
            mood == MoodPose.NERVOUS || mood == MoodPose.SWEATING -> sin(timeSec * 45f) * 3.5f * scale
            activity == CharacterActivity.DANCING -> sin(timeSec * 8f) * 14f * scale
            activity == CharacterActivity.DRIVING_RIDING -> sin(timeSec * 28f) * 4f * scale
            else -> 0f
        }
        val bounceY = when {
            running -> abs(sin(timeSec * 22f)) * 18f * scale
            activity == CharacterActivity.DANCING -> abs(sin(timeSec * 10f)) * 14f * scale
            activity == CharacterActivity.GYM_WORKOUT -> abs(sin(timeSec * 5f)) * 22f * scale
            activity == CharacterActivity.CLAPPING_CHEERING -> abs(sin(timeSec * 11f)) * 16f * scale
            activity == CharacterActivity.WALKING_IN -> abs(sin(timeSec * 10f)) * 7f * scale
            activity == CharacterActivity.DRIVING_RIDING -> sin(timeSec * 24f) * 4.5f * scale
            activity == CharacterActivity.SLEEPING_SNORING -> sin(timeSec * 2.5f) * 3f * scale
            else -> sin(timeSec * 3.5f) * 2.5f * scale
        }
        val baseX = cx + jitterX
        val squatDrop = if (activity == CharacterActivity.GYM_WORKOUT) abs(sin(timeSec * 5f)) * 20f * scale else 0f
        val hipY = footY - 125f * scale - bounceY + squatDrop
        val shoulderY = hipY - 120f * scale
        val headDroopY = if (activity == CharacterActivity.SLEEPING_SNORING) 14f * scale else 0f
        val headY = shoulderY - 76f * scale + headDroopY
        val headR = 56f * scale

        // Ground shadow
        canvas.drawOval(
            baseX - 46f * scale,
            footY - 8f * scale,
            baseX + 46f * scale,
            footY + 10f * scale,
            p.shadowFill
        )

        p.inkPaint.strokeWidth = 10f * scale

        // Legs
        val isFemale = profile.gender == CharacterGender.FEMALE
        if (running || activity == CharacterActivity.WALKING_IN) {
            val freq = if (running) 22f else 10f
            val amp = if (running) 58f else 34f
            val stride = sin(timeSec * freq) * amp * scale
            canvas.drawLine(baseX, hipY, baseX + stride, footY - 14f * scale, p.inkPaint)
            canvas.drawLine(baseX, hipY, baseX - stride, footY - 6f * scale, p.inkPaint)
        } else if (activity == CharacterActivity.DANCING) {
            val step = sin(timeSec * 8f) * 24f * scale
            canvas.drawLine(baseX, hipY, baseX - 24f * scale + step, footY, p.inkPaint)
            canvas.drawLine(baseX, hipY, baseX + 24f * scale + step, footY - abs(step) * 0.35f, p.inkPaint)
        } else if (activity == CharacterActivity.GYM_WORKOUT) {
            canvas.drawLine(baseX, hipY, baseX - 36f * scale, footY, p.inkPaint)
            canvas.drawLine(baseX, hipY, baseX + 36f * scale, footY, p.inkPaint)
        } else if (isFemale) {
            canvas.drawLine(baseX - 10f * scale, hipY + 18f * scale, baseX - 16f * scale, footY, p.inkPaint)
            canvas.drawLine(baseX + 10f * scale, hipY + 18f * scale, baseX + 16f * scale, footY, p.inkPaint)
        } else {
            canvas.drawLine(baseX, hipY, baseX - 24f * scale, footY, p.inkPaint)
            canvas.drawLine(baseX, hipY, baseX + 24f * scale, footY, p.inkPaint)
        }

        // Torso (A-line cel-shaded dress silhouette for Girl/Female stickman)
        val lean = when {
            running -> 22f * scale * dir
            deskSlam -> 16f * scale * dir
            activity == CharacterActivity.DANCING -> sin(timeSec * 8f) * 14f * scale
            activity == CharacterActivity.PLAYING_CRICKET -> 12f * scale * dir
            activity == CharacterActivity.COOKING_CHAI || activity == CharacterActivity.STUDYING_WRITING -> 10f * scale * dir
            activity == CharacterActivity.CRYING_DRAMA -> -10f * scale * dir
            activity == CharacterActivity.SLEEPING_SNORING -> -14f * scale * dir
            else -> 0f
        }
        if (isFemale) {
            val shXTop = baseX + lean
            val skirtY = hipY + 26f * scale
            val waistY = hipY - 38f * scale
            val dressPath = p.pathA.apply {
                rewind()
                moveTo(shXTop - 11f * scale, shoulderY + 6f * scale)
                lineTo(shXTop + 11f * scale, shoulderY + 6f * scale)
                lineTo(baseX + 14f * scale, waistY)
                lineTo(baseX + 38f * scale, skirtY)
                quadTo(baseX, skirtY + 8f * scale, baseX - 38f * scale, skirtY)
                lineTo(baseX - 14f * scale, waistY)
                close()
            }
            canvas.drawLine(shXTop, shoulderY + 8f * scale, shXTop, shoulderY - 16f * scale, p.inkPaint)
            canvas.drawPath(dressPath, p.whiteFill)
            canvas.save()
            canvas.clipPath(dressPath)
            canvas.drawRect(
                baseX - 40f * scale,
                shoulderY,
                baseX - 4f * scale,
                skirtY + 10f * scale,
                p.celShadeFill
            )
            canvas.restore()
            p.inkPaint.strokeWidth = 6.5f * scale
            canvas.drawPath(dressPath, p.inkPaint)
            p.inkPaint.strokeWidth = 10f * scale
        } else {
            canvas.drawLine(baseX, hipY, baseX + lean, shoulderY, p.inkPaint)
        }

        // Arms & Activity Props
        val shX = baseX + lean
        val hX = shX + lean * 0.25f
        val hY = headY
        val gestureWave = if (isSpeaking) sin(timeSec * 12f) * 20f * scale else 0f
        var activityDrawsCustomProp = false

        when {
            holdingPhoneToEar -> {
                val earX = hX - 24f * scale * dir
                val earY = hY + 6f * scale
                canvas.drawLine(shX, shoulderY, shX - 48f * scale * dir, shoulderY - 12f * scale, p.inkPaint)
                canvas.drawLine(shX - 48f * scale * dir, shoulderY - 12f * scale, earX, earY, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX + 36f * scale * dir, shoulderY + 58f * scale, p.inkPaint)
                drawProp(canvas, p, earX - 6f * scale * dir, earY, scale, CharacterProp.PHONE, false)
                activityDrawsCustomProp = true
            }
            deskSlam -> {
                canvas.drawLine(shX, shoulderY, shX + 64f * scale * dir, shoulderY + 62f * scale, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 40f * scale * dir, shoulderY + 58f * scale, p.inkPaint)
            }
            activity == CharacterActivity.DANCING -> {
                val lWave = sin(timeSec * 10f) * 34f * scale
                val rWave = -sin(timeSec * 10f) * 34f * scale
                canvas.drawLine(shX, shoulderY, shX + 54f * scale * dir, shoulderY - 38f * scale + lWave, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 54f * scale * dir, shoulderY - 38f * scale + rWave, p.inkPaint)
                p.textPaint.color = p.inkFill.color
                p.textPaint.typeface = boldDefaultTf
                p.textPaint.textSize = 30f * scale
                val noteBob = sin(timeSec * 6f) * 10f * scale
                canvas.drawText("♪", hX - 66f * scale, hY - 58f * scale + noteBob, p.textPaint)
                canvas.drawText("♫", hX + 66f * scale, hY - 68f * scale - noteBob, p.textPaint)
            }
            activity == CharacterActivity.GYM_WORKOUT -> {
                activityDrawsCustomProp = true
                val curl = sin(timeSec * 7f) * 38f * scale
                val rHandX = shX + 54f * scale * dir
                val rHandY = shoulderY + 10f * scale - curl
                val lHandX = shX - 54f * scale * dir
                val lHandY = shoulderY + 10f * scale + curl * 0.8f
                canvas.drawLine(shX, shoulderY, rHandX, rHandY, p.inkPaint)
                canvas.drawLine(shX, shoulderY, lHandX, lHandY, p.inkPaint)
                drawProp(canvas, p, rHandX, rHandY, scale, CharacterProp.DUMBBELL, false)
                drawProp(canvas, p, lHandX, lHandY, scale, CharacterProp.DUMBBELL, false)
            }
            activity == CharacterActivity.PLAYING_CRICKET -> {
                activityDrawsCustomProp = true
                val swing = sin(timeSec * 8f)
                val hxPos = shX + 56f * scale * dir
                val hyPos = shoulderY + (20f + swing * 24f) * scale
                canvas.drawLine(shX, shoulderY, hxPos, hyPos, p.inkPaint)
                canvas.drawLine(shX, shoulderY, hxPos - 14f * scale * dir, hyPos + 12f * scale, p.inkPaint)
                p.inkPaint.strokeWidth = 11f * scale
                val batTipX = hxPos + (42f + swing * 32f) * scale * dir
                val batTipY = hyPos - (44f - swing * 48f) * scale
                canvas.drawLine(hxPos, hyPos, batTipX, batTipY, p.inkPaint)
                p.inkPaint.strokeWidth = 10f * scale
                val ballX = hxPos + (78f + ((timeSec * 90f) % 80f)) * scale * dir
                val ballY = shoulderY - 22f * scale - abs(sin(timeSec * 8f)) * 28f * scale
                canvas.drawCircle(ballX, ballY, 8f * scale, p.accentFill)
            }
            activity == CharacterActivity.COOKING_CHAI -> {
                activityDrawsCustomProp = true
                val stirX = sin(timeSec * 12f) * 14f * scale
                val handX = shX + 58f * scale * dir
                val handY = shoulderY + 34f * scale
                canvas.drawLine(shX, shoulderY, handX, handY, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 36f * scale * dir, shoulderY + 54f * scale, p.inkPaint)
                p.inkPaint.strokeWidth = 6f * scale
                canvas.drawLine(handX, handY, handX + 38f * scale * dir, handY, p.inkPaint)
                canvas.drawRect(handX + 32f * scale * dir, handY - 12f * scale, handX + 74f * scale * dir, handY + 12f * scale, p.inkFill)
                p.inkPaint.strokeWidth = 2.8f * scale
                canvas.drawLine(handX + 46f * scale * dir, handY - 18f * scale, handX + 46f * scale * dir + stirX, handY - 46f * scale, p.inkPaint)
                canvas.drawLine(handX + 60f * scale * dir, handY - 18f * scale, handX + 60f * scale * dir - stirX, handY - 52f * scale, p.inkPaint)
                p.inkPaint.strokeWidth = 10f * scale
            }
            activity == CharacterActivity.STUDYING_WRITING -> {
                activityDrawsCustomProp = true
                val scribble = sin(timeSec * 18f) * 6f * scale
                val handX = shX + 50f * scale * dir
                val handY = shoulderY + 42f * scale
                canvas.drawLine(shX, shoulderY, handX + scribble, handY, p.inkPaint)
                canvas.drawLine(shX, shoulderY, handX - 18f * scale * dir, handY + 10f * scale, p.inkPaint)
                drawProp(canvas, p, handX, handY + 6f * scale, scale, CharacterProp.CLIPBOARD, false)
            }
            activity == CharacterActivity.GAMING_PHONE -> {
                activityDrawsCustomProp = true
                val thumbTap = sin(timeSec * 24f) * 5f * scale
                val phoneX = shX + 54f * scale * dir
                val phoneY = shoulderY + 18f * scale
                canvas.drawLine(shX, shoulderY, phoneX, phoneY + thumbTap, p.inkPaint)
                canvas.drawLine(shX, shoulderY, phoneX - 10f * scale * dir, phoneY + 14f * scale, p.inkPaint)
                drawProp(canvas, p, phoneX + 8f * scale * dir, phoneY - 6f * scale, scale, CharacterProp.PHONE, false)
            }
            activity == CharacterActivity.SLEEPING_SNORING -> {
                canvas.drawLine(shX, shoulderY, shX + 26f * scale * dir, shoulderY + 68f * scale, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 24f * scale * dir, shoulderY + 68f * scale, p.inkPaint)
                val zRise = ((timeSec * 36f) % 48f) * scale
                p.textPaint.color = p.inkFill.color
                p.textPaint.typeface = boldDefaultTf
                p.textPaint.textSize = 26f * scale
                canvas.drawText("Z", hX + 44f * scale * dir, hY - 40f * scale - zRise * 0.6f, p.textPaint)
                p.textPaint.textSize = 20f * scale
                canvas.drawText("z", hX + 64f * scale * dir, hY - 62f * scale - zRise, p.textPaint)
            }
            activity == CharacterActivity.CLAPPING_CHEERING -> {
                val clapGap = abs(sin(timeSec * 16f)) * 22f * scale
                val midX = shX + 42f * scale * dir
                val midY = shoulderY - 12f * scale
                canvas.drawLine(shX, shoulderY, midX + clapGap * 0.5f, midY, p.inkPaint)
                canvas.drawLine(shX, shoulderY, midX - clapGap * 0.5f, midY - 8f * scale, p.inkPaint)
                if (clapGap < 6f * scale) {
                    p.angerPaint.strokeWidth = 3f * scale
                    canvas.drawLine(midX - 18f * scale, midY - 24f * scale, midX - 28f * scale, midY - 36f * scale, p.angerPaint)
                    canvas.drawLine(midX + 18f * scale, midY - 24f * scale, midX + 28f * scale, midY - 36f * scale, p.angerPaint)
                }
            }
            activity == CharacterActivity.CRYING_DRAMA -> {
                canvas.drawLine(shX, shoulderY, hX + 18f * scale * dir, hY - 16f * scale, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 38f * scale * dir, shoulderY + 60f * scale, p.inkPaint)
                val tearDrop = ((timeSec * 65f) % 44f) * scale
                canvas.drawCircle(hX + 24f * scale * dir, hY + 6f * scale + tearDrop, 5.5f * scale, p.sweatFill)
                canvas.drawCircle(hX - 10f * scale * dir, hY + 12f * scale + tearDrop * 0.8f, 5f * scale, p.sweatFill)
            }
            activity == CharacterActivity.TAKING_SELFIE -> {
                activityDrawsCustomProp = true
                val camX = shX + 66f * scale * dir
                val camY = hY - 26f * scale
                canvas.drawLine(shX, shoulderY, camX, camY, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX + 36f * scale * dir, shoulderY + 34f * scale, p.inkPaint)
                drawProp(canvas, p, camX, camY, scale, CharacterProp.PHONE, false)
                if ((timeSec % 1.2f) < 0.25f) {
                    p.angerPaint.strokeWidth = 3f * scale
                    canvas.drawCircle(camX, camY - 18f * scale, 12f * scale, p.angerPaint)
                }
            }
            activity == CharacterActivity.DRIVING_RIDING -> {
                activityDrawsCustomProp = true
                val turn = sin(timeSec * 6f) * 10f * scale
                val wx = shX + 54f * scale * dir
                val wy = shoulderY + 10f * scale
                canvas.drawLine(shX, shoulderY, wx, wy - turn, p.inkPaint)
                canvas.drawLine(shX, shoulderY, wx - 6f * scale * dir, wy + 14f * scale + turn, p.inkPaint)
                p.inkPaint.strokeWidth = 5f * scale
                canvas.drawCircle(wx + 8f * scale * dir, wy + 4f * scale, 20f * scale, p.inkPaint)
                p.inkPaint.strokeWidth = 10f * scale
            }
            activity == CharacterActivity.SWEEPING_CLEANING -> {
                activityDrawsCustomProp = true
                val sweep = sin(timeSec * 9f) * 28f * scale
                val hxPos = shX + 46f * scale * dir
                val hyPos = shoulderY + 34f * scale
                canvas.drawLine(shX, shoulderY, hxPos, hyPos, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 30f * scale * dir, shoulderY + 56f * scale, p.inkPaint)
                p.inkPaint.strokeWidth = 5.5f * scale
                val footBroomX = baseX + 56f * scale * dir + sweep
                canvas.drawLine(hxPos - 10f * scale * dir, hyPos - 26f * scale, footBroomX, footY - 8f * scale, p.inkPaint)
                canvas.drawRect(footBroomX - 18f * scale, footY - 12f * scale, footBroomX + 18f * scale, footY, p.celShadeFill)
                canvas.drawRect(footBroomX - 18f * scale, footY - 12f * scale, footBroomX + 18f * scale, footY, p.inkPaint)
                p.inkPaint.strokeWidth = 10f * scale
            }
            mood == MoodPose.POINTING -> {
                canvas.drawLine(shX, shoulderY, shX + 86f * scale * dir, shoulderY - 12f * scale, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 36f * scale * dir, shoulderY + 64f * scale, p.inkPaint)
            }
            mood == MoodPose.HIDING -> {
                canvas.drawLine(shX, shoulderY, shX + 22f * scale * dir, headY + 12f * scale, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 22f * scale * dir, headY + 14f * scale, p.inkPaint)
            }
            else -> {
                canvas.drawLine(shX, shoulderY, shX + 54f * scale * dir, shoulderY + 46f * scale - gestureWave, p.inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 42f * scale * dir, shoulderY + 56f * scale, p.inkPaint)
            }
        }

        // Prop in hand
        if (!activityDrawsCustomProp && profile.prop != CharacterProp.NONE) {
            val handX = shX + 58f * scale * dir
            val dropOffset = if (propDropped) propDropProgress * propDropProgress * 190f * scale else 0f
            val handY = shoulderY + 44f * scale + dropOffset
            drawProp(canvas, p, handX, handY, scale, profile.prop, dropOffset > 0f)
        }

        // Head with crisp cel shading
        canvas.drawCircle(hX, hY, headR, p.whiteFill)
        canvas.save()
        p.pathA.rewind()
        p.pathA.addCircle(hX, hY, headR, Path.Direction.CW)
        canvas.clipPath(p.pathA)
        canvas.drawCircle(hX + 14f * scale * dir, hY - 8f * scale, headR, p.whiteFill)
        canvas.drawCircle(hX - 38f * scale * dir, hY + 10f * scale, headR * 0.88f, p.celShadeFill)
        canvas.restore()
        p.inkPaint.strokeWidth = 6.5f * scale
        canvas.drawCircle(hX, hY, headR, p.inkPaint)

        // Hair style
        drawHair(canvas, p, hX, hY, headR, scale, dir, profile.hairStyle)

        // Facial expression + blinking + lip sync
        val effectiveMood = if (activity == CharacterActivity.SLEEPING_SNORING) MoodPose.DEADPAN else mood
        drawFace(canvas, p, hX, hY, headR, scale, dir, effectiveMood, isSpeaking, lipAmp, timeSec, profile)
    }

    private fun drawFace(
        canvas: Canvas,
        p: StickPaints,
        hx: Float,
        hy: Float,
        r: Float,
        scale: Float,
        dir: Float,
        mood: MoodPose,
        isSpeaking: Boolean,
        lipAmp: Float,
        timeSec: Float,
        profile: CharacterProfile
    ) {
        val eyeOffsetX = 18f * scale
        val lookShift = 8f * scale * dir
        val lx = hx - eyeOffsetX + lookShift
        val rx = hx + eyeOffsetX + lookShift
        val ey = hy - 8f * scale

        val blinkPhase = timeSec % 2.9f
        val isBlinking = blinkPhase > 2.77f

        p.inkPaint.strokeWidth = 4.2f * scale
        if (isBlinking || mood == MoodPose.DEADPAN) {
            val eyeW = 11f * scale
            canvas.drawLine(lx - eyeW, ey, lx + eyeW, ey, p.inkPaint)
            canvas.drawLine(rx - eyeW, ey, rx + eyeW, ey, p.inkPaint)
        } else if (mood == MoodPose.SHOCKED) {
            canvas.drawCircle(lx, ey, 12f * scale, p.whiteFill)
            canvas.drawCircle(rx, ey, 12f * scale, p.whiteFill)
            canvas.drawCircle(lx, ey, 12f * scale, p.inkPaint)
            canvas.drawCircle(rx, ey, 12f * scale, p.inkPaint)
            canvas.drawCircle(lx, ey, 3.2f * scale, p.inkFill)
            canvas.drawCircle(rx, ey, 3.2f * scale, p.inkFill)
        } else {
            canvas.drawCircle(lx, ey, 6f * scale, p.inkFill)
            canvas.drawCircle(rx, ey, 6f * scale, p.inkFill)
            if (profile.gender == CharacterGender.FEMALE) {
                p.inkPaint.strokeWidth = 3.2f * scale
                canvas.drawLine(lx - 4f * scale, ey - 5f * scale, lx - 12f * scale, ey - 10f * scale, p.inkPaint)
                canvas.drawLine(lx - 5f * scale, ey - 2f * scale, lx - 13f * scale, ey - 4f * scale, p.inkPaint)
                canvas.drawLine(rx + 4f * scale, ey - 5f * scale, rx + 12f * scale, ey - 10f * scale, p.inkPaint)
                canvas.drawLine(rx + 5f * scale, ey - 2f * scale, rx + 13f * scale, ey - 4f * scale, p.inkPaint)
            }
        }

        // Glasses
        if (profile.hasGlasses) {
            p.inkPaint.strokeWidth = 3.5f * scale
            canvas.drawRect(lx - 14f * scale, ey - 10f * scale, lx + 14f * scale, ey + 10f * scale, p.inkPaint)
            canvas.drawRect(rx - 14f * scale, ey - 10f * scale, rx + 14f * scale, ey + 10f * scale, p.inkPaint)
            canvas.drawLine(lx + 14f * scale, ey, rx - 14f * scale, ey, p.inkPaint)
        }

        // Eyebrows
        p.inkPaint.strokeWidth = 4.5f * scale
        when (mood) {
            MoodPose.ANGRY, MoodPose.FURIOUS -> {
                canvas.drawLine(lx - 12f * scale, ey - 18f * scale, lx + 10f * scale, ey - 10f * scale, p.inkPaint)
                canvas.drawLine(rx - 10f * scale, ey - 10f * scale, rx + 12f * scale, ey - 18f * scale, p.inkPaint)
            }
            MoodPose.NERVOUS, MoodPose.SWEATING -> {
                canvas.drawLine(lx - 12f * scale, ey - 12f * scale, lx + 10f * scale, ey - 18f * scale, p.inkPaint)
                canvas.drawLine(rx - 10f * scale, ey - 18f * scale, rx + 12f * scale, ey - 12f * scale, p.inkPaint)
            }
            MoodPose.SMUG -> {
                canvas.drawLine(lx - 11f * scale, ey - 13f * scale, lx + 10f * scale, ey - 13f * scale, p.inkPaint)
                canvas.drawLine(rx - 10f * scale, ey - 20f * scale, rx + 12f * scale, ey - 15f * scale, p.inkPaint)
            }
            else -> {
                canvas.drawLine(lx - 10f * scale, ey - 15f * scale, lx + 10f * scale, ey - 15f * scale, p.inkPaint)
                canvas.drawLine(rx - 10f * scale, ey - 15f * scale, rx + 10f * scale, ey - 15f * scale, p.inkPaint)
            }
        }

        // Facial hair (male models)
        val mx = hx + lookShift
        val my = hy + 22f * scale
        if (profile.gender == CharacterGender.MALE) {
            when (profile.facialHair) {
                FacialHair.MUSTACHE -> {
                    p.pathA.rewind()
                    p.pathA.moveTo(mx - 18f * scale, my - 4f * scale)
                    p.pathA.quadTo(mx, my - 13f * scale, mx + 18f * scale, my - 4f * scale)
                    p.inkPaint.strokeWidth = 6f * scale
                    canvas.drawPath(p.pathA, p.inkPaint)
                }
                FacialHair.GOATEE -> {
                    canvas.drawOval(mx - 8f * scale, my + 8f * scale, mx + 8f * scale, my + 24f * scale, p.inkFill)
                }
                FacialHair.BEARD -> {
                    p.rect.set(hx - r * 0.72f, hy + 10f * scale, hx + r * 0.72f, hy + r * 1.02f)
                    canvas.drawArc(p.rect, 0f, 180f, true, p.inkFill)
                }
                FacialHair.NONE -> Unit
            }
        }

        // Real-time lip-synced mouth
        val mouthOpen = if (isSpeaking) (lipAmp * 16f * scale).coerceIn(2.0f * scale, 18f * scale) else 0f
        p.inkPaint.strokeWidth = 4f * scale
        when {
            mouthOpen > 2.2f * scale -> {
                canvas.drawOval(mx - 13f * scale, my - mouthOpen * 0.45f, mx + 13f * scale, my + mouthOpen * 0.75f, p.inkFill)
            }
            mood == MoodPose.SHOCKED -> {
                canvas.drawCircle(mx, my + 2f * scale, 9f * scale, p.inkFill)
            }
            mood == MoodPose.SMUG -> {
                p.pathA.rewind()
                p.pathA.moveTo(mx - 14f * scale, my)
                p.pathA.quadTo(mx + 4f * scale, my + 10f * scale, mx + 16f * scale, my - 5f * scale)
                canvas.drawPath(p.pathA, p.inkPaint)
            }
            mood == MoodPose.ANGRY || mood == MoodPose.FURIOUS -> {
                p.pathA.rewind()
                p.pathA.moveTo(mx - 14f * scale, my + 5f * scale)
                p.pathA.quadTo(mx, my - 7f * scale, mx + 14f * scale, my + 5f * scale)
                canvas.drawPath(p.pathA, p.inkPaint)
            }
            else -> {
                canvas.drawLine(mx - 12f * scale, my, mx + 12f * scale, my, p.inkPaint)
            }
        }

        // Animated sweat drops
        if (mood == MoodPose.SWEATING || mood == MoodPose.NERVOUS) {
            val dropY = hy - r * 0.45f + ((timeSec * 45f) % 28f) * scale
            val dx = hx - r * 0.85f * dir
            canvas.drawCircle(dx, dropY, 5.5f * scale, p.sweatFill)
        }

        // Pulsing comic anger vein '#'
        if (mood == MoodPose.FURIOUS) {
            val vx = hx + r * 0.56f * dir
            val vy = hy - r * 0.54f
            val pulse = (1f + 0.15f * sin(timeSec * 14f)) * scale
            p.angerPaint.strokeWidth = 3.5f * scale
            canvas.drawLine(vx - 8f * pulse, vy - 3f * pulse, vx + 8f * pulse, vy - 3f * pulse, p.angerPaint)
            canvas.drawLine(vx - 8f * pulse, vy + 4f * pulse, vx + 8f * pulse, vy + 4f * pulse, p.angerPaint)
            canvas.drawLine(vx - 3f * pulse, vy - 8f * pulse, vx - 3f * pulse, vy + 8f * pulse, p.angerPaint)
            canvas.drawLine(vx + 4f * pulse, vy - 8f * pulse, vx + 4f * pulse, vy + 8f * pulse, p.angerPaint)
        }
    }

    private fun drawHair(
        canvas: Canvas,
        p: StickPaints,
        hx: Float,
        hy: Float,
        r: Float,
        scale: Float,
        dir: Float,
        hair: HairStyle
    ) {
        p.inkPaint.strokeWidth = 5.5f * scale
        when (hair) {
            HairStyle.BALD -> Unit
            HairStyle.SPIKY -> {
                p.pathA.rewind()
                p.pathA.moveTo(hx - r * 0.7f, hy - r * 0.55f)
                p.pathA.lineTo(hx - r * 0.4f, hy - r * 1.25f)
                p.pathA.lineTo(hx - r * 0.1f, hy - r * 0.85f)
                p.pathA.lineTo(hx + r * 0.15f, hy - r * 1.32f)
                p.pathA.lineTo(hx + r * 0.4f, hy - r * 0.85f)
                p.pathA.lineTo(hx + r * 0.72f, hy - r * 1.1f)
                p.pathA.lineTo(hx + r * 0.65f, hy - r * 0.55f)
                p.pathA.close()
                canvas.drawPath(p.pathA, p.inkFill)
            }
            HairStyle.PARTED -> {
                p.inkPaint.strokeWidth = 10f * scale
                p.rect.set(hx - r * 0.96f, hy - r * 1.05f, hx + r * 0.96f, hy + r * 0.25f)
                canvas.drawArc(p.rect, 195f, 150f, false, p.inkPaint)
            }
            HairStyle.BOB -> {
                p.pathA.rewind()
                p.rect.set(hx - r * 1.05f, hy - r * 1.08f, hx + r * 1.05f, hy + r * 0.8f)
                p.pathA.addArc(p.rect, 175f, 190f)
                p.inkPaint.strokeWidth = 12f * scale
                canvas.drawPath(p.pathA, p.inkPaint)
            }
            HairStyle.BUN -> {
                canvas.drawCircle(hx - r * 0.25f * dir, hy - r * 1.15f, 20f * scale, p.inkFill)
            }
            HairStyle.MESSY -> {
                for (i in -2..2) {
                    canvas.drawLine(
                        hx + i * 16f * scale,
                        hy - r * 0.8f,
                        hx + (i * 20f - 8f) * scale,
                        hy - r * 1.22f,
                        p.inkPaint
                    )
                }
            }
            HairStyle.PONYTAIL -> {
                p.inkPaint.strokeWidth = 10f * scale
                p.rect.set(hx - r * 0.98f, hy - r * 1.06f, hx + r * 0.98f, hy + r * 0.15f)
                canvas.drawArc(p.rect, 195f, 150f, false, p.inkPaint)
                p.pathA.rewind()
                p.pathA.moveTo(hx - r * 0.85f * dir, hy - r * 0.48f)
                p.pathA.quadTo(hx - r * 1.68f * dir, hy - r * 0.35f, hx - r * 1.42f * dir, hy + r * 0.65f)
                p.inkPaint.strokeWidth = 14f * scale
                canvas.drawPath(p.pathA, p.inkPaint)
            }
            HairStyle.LONG_WAVY -> {
                p.inkPaint.strokeWidth = 13f * scale
                p.rect.set(hx - r * 1.04f, hy - r * 1.08f, hx + r * 1.04f, hy + r * 0.25f)
                canvas.drawArc(p.rect, 185f, 170f, false, p.inkPaint)
                p.pathA.rewind()
                p.pathA.moveTo(hx - r * 0.96f, hy - r * 0.15f)
                p.pathA.quadTo(hx - r * 1.28f, hy + r * 0.45f, hx - r * 0.95f, hy + r * 1.25f)
                p.pathB.rewind()
                p.pathB.moveTo(hx + r * 0.96f, hy - r * 0.15f)
                p.pathB.quadTo(hx + r * 1.28f, hy + r * 0.45f, hx + r * 0.95f, hy + r * 1.25f)
                p.inkPaint.strokeWidth = 12f * scale
                canvas.drawPath(p.pathA, p.inkPaint)
                canvas.drawPath(p.pathB, p.inkPaint)
            }
            HairStyle.TWIN_BRAIDS -> {
                p.inkPaint.strokeWidth = 11f * scale
                p.rect.set(hx - r * 1.0f, hy - r * 1.06f, hx + r * 1.0f, hy + r * 0.2f)
                canvas.drawArc(p.rect, 185f, 170f, false, p.inkPaint)
                for (sIdx in 0..1) {
                    val side = if (sIdx == 0) -1f else 1f
                    val bx = hx + side * r * 0.95f
                    for (seg in 0..3) {
                        canvas.drawCircle(
                            bx + side * (seg * 3f * scale),
                            hy + (seg * 18f) * scale,
                            9.5f * scale,
                            p.inkFill
                        )
                    }
                }
            }
        }
    }

    private fun drawMemeSticker(
        canvas: Canvas,
        p: StickPaints,
        colors: ThemeColors,
        hx: Float,
        hy: Float,
        unit: Float,
        sticker: MemeSticker,
        timeSec: Float
    ) {
        val bob = sin(timeSec * 8f) * 6f * unit
        when (sticker) {
            MemeSticker.NONE -> Unit
            MemeSticker.DEAL_WITH_IT_SHADES -> {
                p.glintPaint.strokeWidth = 3f * unit
                val ey = hy - 8f * unit
                canvas.drawRect(hx - 42f * unit, ey - 12f * unit, hx - 4f * unit, ey + 10f * unit, p.shadesPaint)
                canvas.drawRect(hx + 4f * unit, ey - 12f * unit, hx + 42f * unit, ey + 10f * unit, p.shadesPaint)
                canvas.drawRect(hx - 42f * unit, ey - 10f * unit, hx + 42f * unit, ey - 4f * unit, p.shadesPaint)
                canvas.drawLine(hx - 32f * unit, ey - 8f * unit, hx - 22f * unit, ey + 4f * unit, p.glintPaint)
                canvas.drawLine(hx + 14f * unit, ey - 8f * unit, hx + 24f * unit, ey + 4f * unit, p.glintPaint)
            }
            MemeSticker.RED_CIRCLE -> {
                p.angerPaint.strokeWidth = 7f * unit
                val pulse = 1f + 0.06f * sin(timeSec * 12f)
                canvas.drawCircle(hx, hy, 84f * unit * pulse, p.angerPaint)
                canvas.drawLine(hx + 128f * unit, hy - 115f * unit, hx + 78f * unit, hy - 68f * unit, p.angerPaint)
                canvas.drawLine(hx + 78f * unit, hy - 68f * unit, hx + 104f * unit, hy - 72f * unit, p.angerPaint)
                canvas.drawLine(hx + 78f * unit, hy - 68f * unit, hx + 82f * unit, hy - 94f * unit, p.angerPaint)
            }
            MemeSticker.MIND_BLOWN -> {
                p.angerPaint.strokeWidth = 4.5f * unit
                for (i in 0..7) {
                    val ang = (i * 45.0) * PI / 180.0
                    val r1 = 68f * unit
                    val r2 = (96f + 12f * sin(timeSec * 14f + i)) * unit
                    canvas.drawLine(
                        hx + cos(ang).toFloat() * r1,
                        hy - 24f * unit + sin(ang).toFloat() * r1,
                        hx + cos(ang).toFloat() * r2,
                        hy - 24f * unit + sin(ang).toFloat() * r2,
                        p.angerPaint
                    )
                }
            }
            MemeSticker.QUESTION_MARKS -> {
                p.textPaint.color = colors.accentColor
                p.textPaint.textSize = 42f * unit
                p.textPaint.typeface = boldDefaultTf
                canvas.drawText("???", hx, hy - 86f * unit + bob, p.textPaint)
            }
            MemeSticker.CROWN -> {
                val crownY = hy - 78f * unit + bob * 0.4f
                p.pathA.rewind()
                p.pathA.moveTo(hx - 34f * unit, crownY + 22f * unit)
                p.pathA.lineTo(hx - 38f * unit, crownY - 12f * unit)
                p.pathA.lineTo(hx - 14f * unit, crownY + 6f * unit)
                p.pathA.lineTo(hx, crownY - 22f * unit)
                p.pathA.lineTo(hx + 14f * unit, crownY + 6f * unit)
                p.pathA.lineTo(hx + 38f * unit, crownY - 12f * unit)
                p.pathA.lineTo(hx + 34f * unit, crownY + 22f * unit)
                p.pathA.close()
                canvas.drawPath(p.pathA, p.goldFill)
                p.inkPaint.strokeWidth = 4f * unit
                canvas.drawPath(p.pathA, p.inkPaint)
            }
            MemeSticker.BROKEN_HEART -> {
                p.textPaint.color = colors.accentColor
                p.textPaint.textSize = 46f * unit
                p.textPaint.typeface = boldDefaultTf
                canvas.drawText("</3", hx + 68f * unit, hy - 68f * unit + bob, p.textPaint)
            }
            MemeSticker.MIC_DROP -> {
                val dropY = hy + 96f * unit + abs(sin(timeSec * 6f)) * 28f * unit
                canvas.drawCircle(hx + 84f * unit, dropY - 14f * unit, 12f * unit, p.inkFill)
                p.inkPaint.strokeWidth = 7f * unit
                canvas.drawLine(hx + 84f * unit, dropY - 4f * unit, hx + 96f * unit, dropY + 28f * unit, p.inkPaint)
            }
        }
    }

    private fun drawProp(
        canvas: Canvas,
        p: StickPaints,
        x: Float,
        y: Float,
        scale: Float,
        prop: CharacterProp,
        isFalling: Boolean
    ) {
        p.inkPaint.strokeWidth = 3.5f * scale
        when (prop) {
            CharacterProp.NONE -> Unit
            CharacterProp.CLIPBOARD, CharacterProp.FOLDER -> {
                p.rect.set(x - 16f * scale, y - 22f * scale, x + 16f * scale, y + 22f * scale)
                canvas.drawRect(p.rect, p.whiteFill)
                canvas.drawRect(p.rect, p.inkPaint)
                canvas.drawRect(x - 7f * scale, y - 26f * scale, x + 7f * scale, y - 18f * scale, p.inkFill)
            }
            CharacterProp.MUG -> {
                canvas.drawRect(x - 11f * scale, y - 12f * scale, x + 11f * scale, y + 14f * scale, p.whiteFill)
                canvas.drawRect(x - 11f * scale, y - 12f * scale, x + 11f * scale, y + 14f * scale, p.inkPaint)
            }
            CharacterProp.STETHOSCOPE -> {
                canvas.drawCircle(x, y, 12f * scale, p.inkPaint)
            }
            CharacterProp.PHONE -> {
                canvas.drawRect(x - 9f * scale, y - 16f * scale, x + 9f * scale, y + 16f * scale, p.inkFill)
            }
            CharacterProp.RULER -> {
                p.inkPaint.strokeWidth = 6f * scale
                canvas.drawLine(x, y + 8f * scale, x + 44f * scale, y - 34f * scale, p.inkPaint)
            }
            CharacterProp.DUMBBELL -> {
                canvas.drawLine(x - 18f * scale, y, x + 18f * scale, y, p.inkPaint)
                canvas.drawCircle(x - 18f * scale, y, 9f * scale, p.inkFill)
                canvas.drawCircle(x + 18f * scale, y, 9f * scale, p.inkFill)
            }
        }
        if (isFalling) {
            p.inkPaint.strokeWidth = 2.5f * scale
            canvas.drawLine(x - 12f * scale, y - 34f * scale, x - 12f * scale, y - 54f * scale, p.inkPaint)
            canvas.drawLine(x + 12f * scale, y - 34f * scale, x + 12f * scale, y - 54f * scale, p.inkPaint)
        }
    }

    private fun drawForegroundDesk(
        canvas: Canvas,
        p: StickPaints,
        w: Float,
        floorY: Float,
        unit: Float,
        hasImpact: Boolean,
        prog: Float
    ) {
        p.rect.set(w * 0.14f, floorY - 64f * unit, w * 0.86f, floorY + 26f * unit)
        canvas.drawRect(p.rect, p.celShadeFill)
        p.inkPaint.strokeWidth = 5f * unit
        canvas.drawRect(p.rect, p.inkPaint)

        if (hasImpact && prog < 0.55f) {
            val ix = w * 0.36f
            val iy = p.rect.top
            p.inkPaint.strokeWidth = 4f * unit
            for (k in 0..5) {
                val ang = (-160.0 + k * 28.0) * PI / 180.0
                val r1 = 18f * unit
                val r2 = (44f + prog * 40f) * unit
                canvas.drawLine(
                    ix + cos(ang).toFloat() * r1,
                    iy + sin(ang).toFloat() * r1,
                    ix + cos(ang).toFloat() * r2,
                    iy + sin(ang).toFloat() * r2,
                    p.inkPaint
                )
            }
        }
    }

    private fun drawTimeSkipCard(
        canvas: Canvas,
        p: StickPaints,
        colors: ThemeColors,
        w: Float,
        h: Float,
        unit: Float,
        cardText: String,
        prog: Float
    ) {
        canvas.drawRect(0f, 0f, w, h, p.darkCardBg)
        p.angerPaint.color = colors.accentColor
        p.angerPaint.strokeWidth = 5f * unit
        canvas.drawRect(44f * unit, 44f * unit, w - 44f * unit, h - 44f * unit, p.angerPaint)

        val scalePop = (0.85f + sin(prog * PI.toFloat()) * 0.18f)
        p.textPaint.color = Color.WHITE
        p.textPaint.typeface = boldDefaultTf
        p.textPaint.textSize = 68f * unit * scalePop
        canvas.drawText(cardText.uppercase(), w * 0.5f, h * 0.50f, p.textPaint)

        p.textPaint.textSize = 22f * unit
        p.textPaint.color = Color.parseColor("#A1A1AA")
        canvas.drawText("• MEANWHILE •", w * 0.5f, h * 0.56f, p.textPaint)
    }

    private fun drawOutroCard(
        canvas: Canvas,
        p: StickPaints,
        colors: ThemeColors,
        w: Float,
        h: Float,
        unit: Float,
        outroText: String,
        handle: String,
        prog: Float
    ) {
        canvas.drawRect(0f, 0f, w, h, p.darkCardBg)

        p.angerPaint.color = colors.accentColor
        p.angerPaint.strokeWidth = 6f * unit
        canvas.drawRect(36f * unit, 36f * unit, w - 36f * unit, h - 36f * unit, p.angerPaint)

        val pop = (0.90f + 0.12f * sin(prog * PI.toFloat()))
        val pill = p.rect.apply { set(w * 0.12f, h * 0.40f, w * 0.88f, h * 0.55f) }
        canvas.drawRoundRect(pill, 28f * unit, 28f * unit, p.accentFill)

        p.textPaint.color = Color.WHITE
        p.textPaint.typeface = boldDefaultTf
        p.textPaint.textSize = 40f * unit * pop
        canvas.drawText(outroText.take(28), w * 0.5f, pill.centerY() + 14f * unit, p.textPaint)

        val displayHandle = formatWatermarkHandle(handle)
        if (displayHandle.isNotBlank()) {
            p.textPaint.color = Color.parseColor("#E4E4E7")
            p.textPaint.textSize = 28f * unit
            canvas.drawText(displayHandle, w * 0.5f, h * 0.63f, p.textPaint)
        }
    }

    private fun drawIntroHookBanner(
        canvas: Canvas,
        p: StickPaints,
        colors: ThemeColors,
        w: Float,
        unit: Float,
        bannerText: String
    ) {
        val rect = p.rect.apply { set(48f * unit, 24f * unit, w - 48f * unit, 76f * unit) }
        p.angerPaint.color = colors.accentColor
        p.angerPaint.strokeWidth = 3f * unit
        canvas.drawRoundRect(rect, 16f * unit, 16f * unit, p.bannerBg)
        canvas.drawRoundRect(rect, 16f * unit, 16f * unit, p.angerPaint)

        p.textPaint.color = Color.WHITE
        p.textPaint.typeface = boldDefaultTf
        p.textPaint.textSize = 24f * unit
        canvas.drawText(bannerText.take(36), rect.centerX(), rect.centerY() + 8f * unit, p.textPaint)
    }

    private fun drawSpeedLines(canvas: Canvas, p: StickPaints, w: Float, h: Float, unit: Float, timeSec: Float) {
        p.inkPaint.strokeWidth = 3f * unit
        for (i in 0..10) {
            val ly = h * (0.18f + i * 0.06f)
            val offset = ((timeSec * 900f + i * 140f) % w)
            canvas.drawLine(offset, ly, offset + 120f * unit, ly, p.inkPaint)
        }
    }

    private fun drawBonkDustCloud(
        canvas: Canvas,
        p: StickPaints,
        colors: ThemeColors,
        cx: Float,
        cy: Float,
        unit: Float,
        prog: Float
    ) {
        val pulse = 1f + 0.08f * sin(prog * 24f)
        val uPulse = unit * pulse
        canvas.drawCircle(cx - 70f * uPulse, cy + 10f * uPulse, 58f * unit, p.whiteFill)
        canvas.drawCircle(cx + 70f * uPulse, cy + 16f * uPulse, 62f * unit, p.whiteFill)
        canvas.drawCircle(cx - 25f * uPulse, cy - 45f * uPulse, 64f * unit, p.whiteFill)
        canvas.drawCircle(cx + 35f * uPulse, cy - 38f * uPulse, 60f * unit, p.whiteFill)
        canvas.drawCircle(cx, cy + 20f * uPulse, 72f * unit, p.whiteFill)

        p.inkPaint.strokeWidth = 6f * unit
        canvas.drawCircle(cx - 70f * uPulse, cy + 10f * uPulse, 58f * unit, p.inkPaint)
        canvas.drawCircle(cx + 70f * uPulse, cy + 16f * uPulse, 62f * unit, p.inkPaint)
        canvas.drawCircle(cx - 25f * uPulse, cy - 45f * uPulse, 64f * unit, p.inkPaint)
        canvas.drawCircle(cx + 35f * uPulse, cy - 38f * uPulse, 60f * unit, p.inkPaint)
        canvas.drawCircle(cx, cy + 20f * uPulse, 72f * unit, p.inkPaint)

        p.textPaint.color = colors.accentColor
        p.textPaint.typeface = boldDefaultTf
        p.textPaint.textSize = 64f * unit * pulse
        canvas.drawText("BONK!", cx, cy + 16f * unit, p.textPaint)
    }

    private fun drawSpeechBubble(
        canvas: Canvas,
        p: StickPaints,
        colors: ThemeColors,
        cx: Float,
        cy: Float,
        frameWidth: Float,
        unit: Float,
        bubble: String
    ) {
        p.textPaint.typeface = boldDefaultTf
        p.textPaint.textSize = 26f * unit
        val textW = p.textPaint.measureText(bubble).coerceAtLeast(110f * unit)
        val padX = 28f * unit
        val boxW = (textW + padX * 2).coerceAtMost(420f * unit)
        val boxH = 66f * unit
        val maxLeft = (frameWidth - boxW - 24f * unit).coerceAtLeast(24f * unit)
        val left = (cx - boxW / 2f).coerceIn(24f * unit, maxLeft)
        val rect = p.rect.apply { set(left, cy - boxH / 2f, left + boxW, cy + boxH / 2f) }

        p.angerPaint.color = colors.bubbleStrokeColor
        p.angerPaint.strokeWidth = 4.5f * unit
        canvas.drawRoundRect(rect, 22f * unit, 22f * unit, p.pureWhiteFill)
        canvas.drawRoundRect(rect, 22f * unit, 22f * unit, p.angerPaint)

        p.textPaint.color = Color.parseColor("#09090B")
        canvas.drawText(bubble.take(28), rect.centerX(), rect.centerY() + 9f * unit, p.textPaint)
    }

    private fun drawCaptions(
        canvas: Canvas,
        p: StickPaints,
        colors: ThemeColors,
        w: Float,
        h: Float,
        unit: Float,
        text: String,
        progress: Float,
        style: CaptionStyle,
        fontStyle: CaptionFontStyle,
        position: CaptionPosition
    ) {
        val baseY = if (position == CaptionPosition.CENTER_POP) h * 0.56f else h * 0.83f
        val words = text.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (words.isEmpty()) return

        p.textPaint.typeface = when (fontStyle) {
            CaptionFontStyle.BOLD_SANS -> boldSansTf
            CaptionFontStyle.COMIC_PUNCH -> comicPunchTf
            CaptionFontStyle.TYPEWRITER_MONO -> monoBoldTf
            CaptionFontStyle.SERIF_DRAMA -> serifBoldTf
        }

        if (style == CaptionStyle.CLASSIC) {
            val lines = wrapLines(words, maxChars = 28)
            p.textPaint.textSize = 30f * unit
            val lineH = 38f * unit
            val boxH = lines.size * lineH + 26f * unit
            val box = p.rect.apply { set(w * 0.08f, baseY - boxH / 2f, w * 0.92f, baseY + boxH / 2f) }
            canvas.drawRoundRect(box, 16f * unit, 16f * unit, p.captionBgPaint)
            p.textPaint.color = Color.WHITE
            lines.forEachIndexed { idx, line ->
                canvas.drawText(line, w * 0.5f, box.top + 38f * unit + idx * lineH, p.textPaint)
            }
        } else {
            val activeIdx = (progress * words.size).toInt().coerceIn(0, words.lastIndex)
            val windowStart = (activeIdx / 4) * 4
            val chunk = words.subList(windowStart, (windowStart + 4).coerceAtMost(words.size))
            val localActive = activeIdx - windowStart

            p.textPaint.textSize = 34f * unit
            val spacing = 14f * unit
            val widths = chunk.map { p.textPaint.measureText(it) + 18f * unit }
            val totalW = widths.sum() + spacing * (chunk.size - 1).coerceAtLeast(0)
            var cursorX = (w - totalW) * 0.5f

            val bgBox = p.rect.apply {
                set(
                    (cursorX - 18f * unit).coerceAtLeast(24f * unit),
                    baseY - 34f * unit,
                    (cursorX + totalW + 18f * unit).coerceAtMost(w - 24f * unit),
                    baseY + 34f * unit
                )
            }
            canvas.drawRoundRect(bgBox, 16f * unit, 16f * unit, p.captionBgPaint)

            val isBounce = style == CaptionStyle.VIRAL_BOUNCE
            val wordDuration = 1f / words.size.coerceAtLeast(1)
            val wordLocalProg = ((progress - activeIdx * wordDuration) / wordDuration).coerceIn(0f, 1f)
            val bounceLift = if (isBounce) sin(wordLocalProg * PI.toFloat()) * 10f * unit else 0f

            p.popWordBgPaint.color = if (isBounce) colors.accentColor else Color.WHITE

            chunk.forEachIndexed { i, word ->
                val ww = widths[i]
                val cx = cursorX + ww * 0.5f
                if (i == localActive) {
                    p.rect.set(
                        cursorX,
                        baseY - 24f * unit - bounceLift,
                        cursorX + ww,
                        baseY + 24f * unit - bounceLift
                    )
                    canvas.drawRoundRect(p.rect, 10f * unit, 10f * unit, p.popWordBgPaint)
                    p.textPaint.color = if (isBounce) Color.WHITE else Color.parseColor("#09090B")
                    p.textPaint.textSize = if (isBounce) 39f * unit else 37f * unit
                    canvas.drawText(word, cx, baseY + 12f * unit - bounceLift, p.textPaint)
                } else {
                    p.textPaint.color = Color.WHITE
                    p.textPaint.textSize = 33f * unit
                    canvas.drawText(word, cx, baseY + 12f * unit, p.textPaint)
                }
                cursorX += ww + spacing
            }
        }
    }

    private fun drawWatermark(
        canvas: Canvas,
        p: StickPaints,
        w: Float,
        h: Float,
        unit: Float,
        handle: String,
        darkBg: Boolean
    ) {
        val displayHandle = formatWatermarkHandle(handle)
        if (displayHandle.isBlank()) return
        p.watermarkPaint.color = if (darkBg) Color.parseColor("#88FFFFFF") else Color.parseColor("#8818181B")
        p.watermarkPaint.textSize = 22f * unit
        canvas.drawText(displayHandle, w - 28f * unit, h - 26f * unit, p.watermarkPaint)
    }

    private fun formatWatermarkHandle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("@")) trimmed else "@$trimmed"
    }

    private fun wrapLines(words: List<String>, maxChars: Int): List<String> {
        val lines = mutableListOf<String>()
        val cur = StringBuilder()
        for (word in words) {
            if (cur.length + word.length + 1 > maxChars && cur.isNotEmpty()) {
                lines.add(cur.toString())
                cur.clear()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(word)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines.take(3)
    }
}

