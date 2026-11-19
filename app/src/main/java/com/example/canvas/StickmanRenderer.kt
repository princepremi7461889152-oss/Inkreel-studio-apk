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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object StickmanRenderer {

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#09090B")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val inkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#09090B")
        style = Paint.Style.FILL
    }
    private val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val shadowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22000000")
        style = Paint.Style.FILL
    }
    private val celShadeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E4E4E7")
        style = Paint.Style.FILL
    }
    private val sweatFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
    }
    private val angerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val accentFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626")
        style = Paint.Style.FILL
    }
    private val captionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DD09090B")
        style = Paint.Style.FILL
    }
    private val popWordBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

    private fun applyThemePaints(theme: ReelColorTheme) {
        val inkColor = Color.parseColor(theme.inkHex)
        val accentColor = Color.parseColor(theme.accentHex)
        inkPaint.color = inkColor
        inkFill.color = inkColor
        whiteFill.color = if (theme.isDarkStage) Color.parseColor("#27272A") else Color.WHITE
        celShadeFill.color = if (theme.isDarkStage) Color.parseColor("#3F3F46") else Color.parseColor("#E4E4E7")
        shadowFill.color = if (theme.isDarkStage) Color.parseColor("#44000000") else Color.parseColor("#22000000")
        angerPaint.color = accentColor
        accentFill.color = accentColor
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
        val unit = width / 720f
        val theme = project.colorTheme
        applyThemePaints(theme)

        val state = resolveShotAt(project, playheadMs)
        val shot = state.shot
        val prog = state.progress
        val timeSec = playheadMs / 1000f

        // Animated Outro Follow/Subscribe Card
        if (state.isOutro) {
            drawOutroCard(canvas, width, height, unit, project.outroText, project.watermarkHandle, theme, prog)
            return
        }

        // Full-frame Time-Skip Card
        if (shot.framing == CameraFraming.TIME_SKIP_CARD) {
            drawTimeSkipCard(canvas, width, height, unit, shot.transitionCard.ifBlank { "NEXT DAY" }, prog, theme)
            drawWatermark(canvas, width, height, unit, project.watermarkHandle, darkBg = true)
            return
        }

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
            // Side-by-side Split-Screen Phone Call view
            drawSplitPhoneCallScene(
                canvas = canvas,
                width = width,
                height = height,
                floorY = floorY,
                unit = unit,
                theme = theme,
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
                    drawSpeedLines(canvas, width, height, unit, timeSec)
                }
                CameraFraming.BONK_ENDING -> {
                    drawBonkDustCloud(canvas, width * 0.50f, floorY - 80f * unit, unit, prog, theme)
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
                        drawForegroundDesk(canvas, width, floorY, unit, shot.framing == CameraFraming.DESK_SLAM, prog)
                    }

                    if (shot.framing == CameraFraming.OVER_THE_SHOULDER) {
                        canvas.drawCircle(width * 0.14f, height * 0.69f, 110f * unit, inkFill)
                        canvas.drawCircle(width * 0.16f, height * 0.52f, 64f * unit, inkFill)
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
            drawMemeSticker(canvas, targetX, headCenterY, unit, shot.sticker, timeSec, theme)
        }

        // Speech bubble in world space if enabled
        if (showSpeechBubbles && shot.speechBubble.isNotBlank() && shot.framing != CameraFraming.BONK_ENDING) {
            val bubbleX = when (shot.speaker) {
                SpeakerSlot.A -> if (shot.framing == CameraFraming.SPLIT_PHONE_CALL) width * 0.26f else width * 0.34f
                SpeakerSlot.B -> if (shot.framing == CameraFraming.SPLIT_PHONE_CALL) width * 0.74f else width * 0.66f
                else -> width * 0.50f
            }
            drawSpeechBubble(canvas, bubbleX, height * 0.26f, width, unit, shot.speechBubble, theme)
        }

        canvas.restore()

        // Screen-space Intro Hook Banner (top viral hook strip)
        if (project.introHookBanner.isNotBlank()) {
            drawIntroHookBanner(canvas, width, unit, project.introHookBanner, theme)
        }

        // Screen-space Captions & Watermark
        if (showCaptions && shot.dialogue.isNotBlank() && shot.framing != CameraFraming.BONK_ENDING) {
            drawCaptions(
                canvas = canvas,
                w = width,
                h = height,
                unit = unit,
                text = shot.dialogue,
                progress = prog,
                style = project.captionStyle,
                fontStyle = project.captionFontStyle,
                position = project.captionPosition,
                theme = theme
            )
        }
        drawWatermark(canvas, width, height, unit, project.watermarkHandle, darkBg = theme.isDarkStage)
    }

    private fun drawSplitPhoneCallScene(
        canvas: Canvas,
        width: Float,
        height: Float,
        floorY: Float,
        unit: Float,
        theme: ReelColorTheme,
        charA: CharacterProfile,
        charB: CharacterProfile,
        shot: ReelShot,
        lipAmp: Float,
        timeSec: Float
    ) {
        // Left & Right contrasting phone panels
        val leftBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(theme.wallHex) }
        val rightBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(theme.floorHex) }
        canvas.drawRect(0f, 0f, width * 0.5f, height, leftBg)
        canvas.drawRect(width * 0.5f, 0f, width, height, rightBg)

        // Floor bar
        val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.floorHex)
        }
        canvas.drawRect(0f, floorY, width * 0.5f, height, floorPaint)
        inkPaint.strokeWidth = 4f * unit
        canvas.drawLine(0f, floorY, width, floorY, inkPaint)

        // Left Character A on Phone
        drawStickman(
            canvas = canvas,
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
        inkPaint.strokeWidth = 7f * unit
        canvas.drawLine(width * 0.5f, 0f, width * 0.5f, height, inkPaint)

        val badgeRect = RectF(width * 0.5f - 68f * unit, height * 0.14f, width * 0.5f + 68f * unit, height * 0.14f + 40f * unit)
        canvas.drawRoundRect(badgeRect, 20f * unit, 20f * unit, accentFill)
        textPaint.color = Color.WHITE
        textPaint.textSize = 20f * unit
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ON CALL", badgeRect.centerX(), badgeRect.centerY() + 7f * unit, textPaint)
    }

    private fun drawStickman(
        canvas: Canvas,
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
            shadowFill
        )

        inkPaint.strokeWidth = 10f * scale

        // Legs
        val isFemale = profile.gender == CharacterGender.FEMALE
        if (running || activity == CharacterActivity.WALKING_IN) {
            val freq = if (running) 22f else 10f
            val amp = if (running) 58f else 34f
            val stride = sin(timeSec * freq) * amp * scale
            canvas.drawLine(baseX, hipY, baseX + stride, footY - 14f * scale, inkPaint)
            canvas.drawLine(baseX, hipY, baseX - stride, footY - 6f * scale, inkPaint)
        } else if (activity == CharacterActivity.DANCING) {
            val step = sin(timeSec * 8f) * 24f * scale
            canvas.drawLine(baseX, hipY, baseX - 24f * scale + step, footY, inkPaint)
            canvas.drawLine(baseX, hipY, baseX + 24f * scale + step, footY - abs(step) * 0.35f, inkPaint)
        } else if (activity == CharacterActivity.GYM_WORKOUT) {
            canvas.drawLine(baseX, hipY, baseX - 36f * scale, footY, inkPaint)
            canvas.drawLine(baseX, hipY, baseX + 36f * scale, footY, inkPaint)
        } else if (isFemale) {
            canvas.drawLine(baseX - 10f * scale, hipY + 18f * scale, baseX - 16f * scale, footY, inkPaint)
            canvas.drawLine(baseX + 10f * scale, hipY + 18f * scale, baseX + 16f * scale, footY, inkPaint)
        } else {
            canvas.drawLine(baseX, hipY, baseX - 24f * scale, footY, inkPaint)
            canvas.drawLine(baseX, hipY, baseX + 24f * scale, footY, inkPaint)
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
            val dressPath = Path().apply {
                moveTo(shXTop - 11f * scale, shoulderY + 6f * scale)
                lineTo(shXTop + 11f * scale, shoulderY + 6f * scale)
                lineTo(baseX + 14f * scale, waistY)
                lineTo(baseX + 38f * scale, skirtY)
                quadTo(baseX, skirtY + 8f * scale, baseX - 38f * scale, skirtY)
                lineTo(baseX - 14f * scale, waistY)
                close()
            }
            canvas.drawLine(shXTop, shoulderY + 8f * scale, shXTop, shoulderY - 16f * scale, inkPaint)
            canvas.drawPath(dressPath, whiteFill)
            canvas.save()
            canvas.clipPath(dressPath)
            canvas.drawRect(
                baseX - 40f * scale,
                shoulderY,
                baseX - 4f * scale,
                skirtY + 10f * scale,
                celShadeFill
            )
            canvas.restore()
            inkPaint.strokeWidth = 6.5f * scale
            canvas.drawPath(dressPath, inkPaint)
            inkPaint.strokeWidth = 10f * scale
        } else {
            canvas.drawLine(baseX, hipY, baseX + lean, shoulderY, inkPaint)
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
                canvas.drawLine(shX, shoulderY, shX - 48f * scale * dir, shoulderY - 12f * scale, inkPaint)
                canvas.drawLine(shX - 48f * scale * dir, shoulderY - 12f * scale, earX, earY, inkPaint)
                canvas.drawLine(shX, shoulderY, shX + 36f * scale * dir, shoulderY + 58f * scale, inkPaint)
                drawProp(canvas, earX - 6f * scale * dir, earY, scale, CharacterProp.PHONE, false)
                activityDrawsCustomProp = true
            }
            deskSlam -> {
                canvas.drawLine(shX, shoulderY, shX + 64f * scale * dir, shoulderY + 62f * scale, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 40f * scale * dir, shoulderY + 58f * scale, inkPaint)
            }
            activity == CharacterActivity.DANCING -> {
                val lWave = sin(timeSec * 10f) * 34f * scale
                val rWave = -sin(timeSec * 10f) * 34f * scale
                canvas.drawLine(shX, shoulderY, shX + 54f * scale * dir, shoulderY - 38f * scale + lWave, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 54f * scale * dir, shoulderY - 38f * scale + rWave, inkPaint)
                // Floating music notes
                textPaint.color = inkFill.color
                textPaint.textSize = 30f * scale
                val noteBob = sin(timeSec * 6f) * 10f * scale
                canvas.drawText("♪", hX - 66f * scale, hY - 58f * scale + noteBob, textPaint)
                canvas.drawText("♫", hX + 66f * scale, hY - 68f * scale - noteBob, textPaint)
            }
            activity == CharacterActivity.GYM_WORKOUT -> {
                activityDrawsCustomProp = true
                val curl = sin(timeSec * 7f) * 38f * scale
                val rHandX = shX + 54f * scale * dir
                val rHandY = shoulderY + 10f * scale - curl
                val lHandX = shX - 54f * scale * dir
                val lHandY = shoulderY + 10f * scale + curl * 0.8f
                canvas.drawLine(shX, shoulderY, rHandX, rHandY, inkPaint)
                canvas.drawLine(shX, shoulderY, lHandX, lHandY, inkPaint)
                drawProp(canvas, rHandX, rHandY, scale, CharacterProp.DUMBBELL, false)
                drawProp(canvas, lHandX, lHandY, scale, CharacterProp.DUMBBELL, false)
            }
            activity == CharacterActivity.PLAYING_CRICKET -> {
                activityDrawsCustomProp = true
                val swing = sin(timeSec * 8f)
                val hxPos = shX + 56f * scale * dir
                val hyPos = shoulderY + (20f + swing * 24f) * scale
                canvas.drawLine(shX, shoulderY, hxPos, hyPos, inkPaint)
                canvas.drawLine(shX, shoulderY, hxPos - 14f * scale * dir, hyPos + 12f * scale, inkPaint)
                // Cricket bat
                inkPaint.strokeWidth = 11f * scale
                val batTipX = hxPos + (42f + swing * 32f) * scale * dir
                val batTipY = hyPos - (44f - swing * 48f) * scale
                canvas.drawLine(hxPos, hyPos, batTipX, batTipY, inkPaint)
                inkPaint.strokeWidth = 10f * scale
                // Flying ball
                val ballX = hxPos + (78f + ((timeSec * 90f) % 80f)) * scale * dir
                val ballY = shoulderY - 22f * scale - abs(sin(timeSec * 8f)) * 28f * scale
                canvas.drawCircle(ballX, ballY, 8f * scale, accentFill)
            }
            activity == CharacterActivity.COOKING_CHAI -> {
                activityDrawsCustomProp = true
                val stirX = sin(timeSec * 12f) * 14f * scale
                val handX = shX + 58f * scale * dir
                val handY = shoulderY + 34f * scale
                canvas.drawLine(shX, shoulderY, handX, handY, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 36f * scale * dir, shoulderY + 54f * scale, inkPaint)
                // Frying pan / Chai pan
                inkPaint.strokeWidth = 6f * scale
                canvas.drawLine(handX, handY, handX + 38f * scale * dir, handY, inkPaint)
                canvas.drawRect(handX + 32f * scale * dir, handY - 12f * scale, handX + 74f * scale * dir, handY + 12f * scale, inkFill)
                // Rising steam
                inkPaint.strokeWidth = 2.8f * scale
                canvas.drawLine(handX + 46f * scale * dir, handY - 18f * scale, handX + 46f * scale * dir + stirX, handY - 46f * scale, inkPaint)
                canvas.drawLine(handX + 60f * scale * dir, handY - 18f * scale, handX + 60f * scale * dir - stirX, handY - 52f * scale, inkPaint)
                inkPaint.strokeWidth = 10f * scale
            }
            activity == CharacterActivity.STUDYING_WRITING -> {
                activityDrawsCustomProp = true
                val scribble = sin(timeSec * 18f) * 6f * scale
                val handX = shX + 50f * scale * dir
                val handY = shoulderY + 42f * scale
                canvas.drawLine(shX, shoulderY, handX + scribble, handY, inkPaint)
                canvas.drawLine(shX, shoulderY, handX - 18f * scale * dir, handY + 10f * scale, inkPaint)
                drawProp(canvas, handX, handY + 6f * scale, scale, CharacterProp.CLIPBOARD, false)
            }
            activity == CharacterActivity.GAMING_PHONE -> {
                activityDrawsCustomProp = true
                val thumbTap = sin(timeSec * 24f) * 5f * scale
                val phoneX = shX + 54f * scale * dir
                val phoneY = shoulderY + 18f * scale
                canvas.drawLine(shX, shoulderY, phoneX, phoneY + thumbTap, inkPaint)
                canvas.drawLine(shX, shoulderY, phoneX - 10f * scale * dir, phoneY + 14f * scale, inkPaint)
                drawProp(canvas, phoneX + 8f * scale * dir, phoneY - 6f * scale, scale, CharacterProp.PHONE, false)
            }
            activity == CharacterActivity.SLEEPING_SNORING -> {
                canvas.drawLine(shX, shoulderY, shX + 26f * scale * dir, shoulderY + 68f * scale, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 24f * scale * dir, shoulderY + 68f * scale, inkPaint)
                val zRise = ((timeSec * 36f) % 48f) * scale
                textPaint.color = inkFill.color
                textPaint.textSize = 26f * scale
                canvas.drawText("Z", hX + 44f * scale * dir, hY - 40f * scale - zRise * 0.6f, textPaint)
                textPaint.textSize = 20f * scale
                canvas.drawText("z", hX + 64f * scale * dir, hY - 62f * scale - zRise, textPaint)
            }
            activity == CharacterActivity.CLAPPING_CHEERING -> {
                val clapGap = abs(sin(timeSec * 16f)) * 22f * scale
                val midX = shX + 42f * scale * dir
                val midY = shoulderY - 12f * scale
                canvas.drawLine(shX, shoulderY, midX + clapGap * 0.5f, midY, inkPaint)
                canvas.drawLine(shX, shoulderY, midX - clapGap * 0.5f, midY - 8f * scale, inkPaint)
                if (clapGap < 6f * scale) {
                    angerPaint.strokeWidth = 3f * scale
                    canvas.drawLine(midX - 18f * scale, midY - 24f * scale, midX - 28f * scale, midY - 36f * scale, angerPaint)
                    canvas.drawLine(midX + 18f * scale, midY - 24f * scale, midX + 28f * scale, midY - 36f * scale, angerPaint)
                }
            }
            activity == CharacterActivity.CRYING_DRAMA -> {
                // Hand on forehead dramatic pose + fountain tear drops
                canvas.drawLine(shX, shoulderY, hX + 18f * scale * dir, hY - 16f * scale, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 38f * scale * dir, shoulderY + 60f * scale, inkPaint)
                val tearDrop = ((timeSec * 65f) % 44f) * scale
                canvas.drawCircle(hX + 24f * scale * dir, hY + 6f * scale + tearDrop, 5.5f * scale, sweatFill)
                canvas.drawCircle(hX - 10f * scale * dir, hY + 12f * scale + tearDrop * 0.8f, 5f * scale, sweatFill)
            }
            activity == CharacterActivity.TAKING_SELFIE -> {
                activityDrawsCustomProp = true
                val camX = shX + 66f * scale * dir
                val camY = hY - 26f * scale
                canvas.drawLine(shX, shoulderY, camX, camY, inkPaint)
                canvas.drawLine(shX, shoulderY, shX + 36f * scale * dir, shoulderY + 34f * scale, inkPaint)
                drawProp(canvas, camX, camY, scale, CharacterProp.PHONE, false)
                // Flash sparkle every ~1.2s
                if ((timeSec % 1.2f) < 0.25f) {
                    angerPaint.strokeWidth = 3f * scale
                    canvas.drawCircle(camX, camY - 18f * scale, 12f * scale, angerPaint)
                }
            }
            activity == CharacterActivity.DRIVING_RIDING -> {
                activityDrawsCustomProp = true
                val turn = sin(timeSec * 6f) * 10f * scale
                val wx = shX + 54f * scale * dir
                val wy = shoulderY + 10f * scale
                canvas.drawLine(shX, shoulderY, wx, wy - turn, inkPaint)
                canvas.drawLine(shX, shoulderY, wx - 6f * scale * dir, wy + 14f * scale + turn, inkPaint)
                // Steering wheel / handlebar ring
                inkPaint.strokeWidth = 5f * scale
                canvas.drawCircle(wx + 8f * scale * dir, wy + 4f * scale, 20f * scale, inkPaint)
                inkPaint.strokeWidth = 10f * scale
            }
            activity == CharacterActivity.SWEEPING_CLEANING -> {
                activityDrawsCustomProp = true
                val sweep = sin(timeSec * 9f) * 28f * scale
                val hxPos = shX + 46f * scale * dir
                val hyPos = shoulderY + 34f * scale
                canvas.drawLine(shX, shoulderY, hxPos, hyPos, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 30f * scale * dir, shoulderY + 56f * scale, inkPaint)
                // Broom handle & bristles
                inkPaint.strokeWidth = 5.5f * scale
                val footBroomX = baseX + 56f * scale * dir + sweep
                canvas.drawLine(hxPos - 10f * scale * dir, hyPos - 26f * scale, footBroomX, footY - 8f * scale, inkPaint)
                canvas.drawRect(footBroomX - 18f * scale, footY - 12f * scale, footBroomX + 18f * scale, footY, celShadeFill)
                canvas.drawRect(footBroomX - 18f * scale, footY - 12f * scale, footBroomX + 18f * scale, footY, inkPaint)
                inkPaint.strokeWidth = 10f * scale
            }
            mood == MoodPose.POINTING -> {
                canvas.drawLine(shX, shoulderY, shX + 86f * scale * dir, shoulderY - 12f * scale, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 36f * scale * dir, shoulderY + 64f * scale, inkPaint)
            }
            mood == MoodPose.HIDING -> {
                canvas.drawLine(shX, shoulderY, shX + 22f * scale * dir, headY + 12f * scale, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 22f * scale * dir, headY + 14f * scale, inkPaint)
            }
            else -> {
                canvas.drawLine(shX, shoulderY, shX + 54f * scale * dir, shoulderY + 46f * scale - gestureWave, inkPaint)
                canvas.drawLine(shX, shoulderY, shX - 42f * scale * dir, shoulderY + 56f * scale, inkPaint)
            }
        }

        // Prop in hand (when not overridden by a custom activity prop)
        if (!activityDrawsCustomProp && profile.prop != CharacterProp.NONE) {
            val handX = shX + 58f * scale * dir
            val dropOffset = if (propDropped) propDropProgress * propDropProgress * 190f * scale else 0f
            val handY = shoulderY + 44f * scale + dropOffset
            drawProp(canvas, handX, handY, scale, profile.prop, dropOffset > 0f)
        }

        // Head with crisp cel shading
        canvas.drawCircle(hX, hY, headR, whiteFill)
        canvas.save()
        val clipPath = Path().apply { addCircle(hX, hY, headR, Path.Direction.CW) }
        canvas.clipPath(clipPath)
        canvas.drawCircle(hX + 14f * scale * dir, hY - 8f * scale, headR, whiteFill)
        canvas.drawCircle(hX - 38f * scale * dir, hY + 10f * scale, headR * 0.88f, celShadeFill)
        canvas.restore()
        inkPaint.strokeWidth = 6.5f * scale
        canvas.drawCircle(hX, hY, headR, inkPaint)

        // Hair style
        drawHair(canvas, hX, hY, headR, scale, dir, profile.hairStyle)

        // Facial expression + blinking + lip sync (sleeping closes eyes cleanly)
        val effectiveMood = if (activity == CharacterActivity.SLEEPING_SNORING) MoodPose.DEADPAN else mood
        drawFace(canvas, hX, hY, headR, scale, dir, effectiveMood, isSpeaking, lipAmp, timeSec, profile)
    }

    private fun drawFace(
        canvas: Canvas,
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

        inkPaint.strokeWidth = 4.2f * scale
        if (isBlinking || mood == MoodPose.DEADPAN) {
            val eyeW = 11f * scale
            canvas.drawLine(lx - eyeW, ey, lx + eyeW, ey, inkPaint)
            canvas.drawLine(rx - eyeW, ey, rx + eyeW, ey, inkPaint)
        } else if (mood == MoodPose.SHOCKED) {
            canvas.drawCircle(lx, ey, 12f * scale, whiteFill)
            canvas.drawCircle(rx, ey, 12f * scale, whiteFill)
            canvas.drawCircle(lx, ey, 12f * scale, inkPaint)
            canvas.drawCircle(rx, ey, 12f * scale, inkPaint)
            canvas.drawCircle(lx, ey, 3.2f * scale, inkFill)
            canvas.drawCircle(rx, ey, 3.2f * scale, inkFill)
        } else {
            canvas.drawCircle(lx, ey, 6f * scale, inkFill)
            canvas.drawCircle(rx, ey, 6f * scale, inkFill)
            if (profile.gender == CharacterGender.FEMALE) {
                inkPaint.strokeWidth = 3.2f * scale
                canvas.drawLine(lx - 4f * scale, ey - 5f * scale, lx - 12f * scale, ey - 10f * scale, inkPaint)
                canvas.drawLine(lx - 5f * scale, ey - 2f * scale, lx - 13f * scale, ey - 4f * scale, inkPaint)
                canvas.drawLine(rx + 4f * scale, ey - 5f * scale, rx + 12f * scale, ey - 10f * scale, inkPaint)
                canvas.drawLine(rx + 5f * scale, ey - 2f * scale, rx + 13f * scale, ey - 4f * scale, inkPaint)
            }
        }

        // Glasses
        if (profile.hasGlasses) {
            inkPaint.strokeWidth = 3.5f * scale
            canvas.drawRect(lx - 14f * scale, ey - 10f * scale, lx + 14f * scale, ey + 10f * scale, inkPaint)
            canvas.drawRect(rx - 14f * scale, ey - 10f * scale, rx + 14f * scale, ey + 10f * scale, inkPaint)
            canvas.drawLine(lx + 14f * scale, ey, rx - 14f * scale, ey, inkPaint)
        }

        // Eyebrows
        inkPaint.strokeWidth = 4.5f * scale
        when (mood) {
            MoodPose.ANGRY, MoodPose.FURIOUS -> {
                canvas.drawLine(lx - 12f * scale, ey - 18f * scale, lx + 10f * scale, ey - 10f * scale, inkPaint)
                canvas.drawLine(rx - 10f * scale, ey - 10f * scale, rx + 12f * scale, ey - 18f * scale, inkPaint)
            }
            MoodPose.NERVOUS, MoodPose.SWEATING -> {
                canvas.drawLine(lx - 12f * scale, ey - 12f * scale, lx + 10f * scale, ey - 18f * scale, inkPaint)
                canvas.drawLine(rx - 10f * scale, ey - 18f * scale, rx + 12f * scale, ey - 12f * scale, inkPaint)
            }
            MoodPose.SMUG -> {
                canvas.drawLine(lx - 11f * scale, ey - 13f * scale, lx + 10f * scale, ey - 13f * scale, inkPaint)
                canvas.drawLine(rx - 10f * scale, ey - 20f * scale, rx + 12f * scale, ey - 15f * scale, inkPaint)
            }
            else -> {
                canvas.drawLine(lx - 10f * scale, ey - 15f * scale, lx + 10f * scale, ey - 15f * scale, inkPaint)
                canvas.drawLine(rx - 10f * scale, ey - 15f * scale, rx + 10f * scale, ey - 15f * scale, inkPaint)
            }
        }

        // Facial hair (male models)
        val mx = hx + lookShift
        val my = hy + 22f * scale
        if (profile.gender == CharacterGender.MALE) {
            when (profile.facialHair) {
                FacialHair.MUSTACHE -> {
                    val mPath = Path().apply {
                        moveTo(mx - 18f * scale, my - 4f * scale)
                        quadTo(mx, my - 13f * scale, mx + 18f * scale, my - 4f * scale)
                    }
                    inkPaint.strokeWidth = 6f * scale
                    canvas.drawPath(mPath, inkPaint)
                }
                FacialHair.GOATEE -> {
                    canvas.drawOval(mx - 8f * scale, my + 8f * scale, mx + 8f * scale, my + 24f * scale, inkFill)
                }
                FacialHair.BEARD -> {
                    val bRect = RectF(hx - r * 0.72f, hy + 10f * scale, hx + r * 0.72f, hy + r * 1.02f)
                    canvas.drawArc(bRect, 0f, 180f, true, inkFill)
                }
                FacialHair.NONE -> Unit
            }
        }

        // Real-time lip-synced mouth (responsive even on quiet syllables)
        val mouthOpen = if (isSpeaking) (lipAmp * 16f * scale).coerceIn(2.0f * scale, 18f * scale) else 0f
        inkPaint.strokeWidth = 4f * scale
        when {
            mouthOpen > 2.2f * scale -> {
                canvas.drawOval(mx - 13f * scale, my - mouthOpen * 0.45f, mx + 13f * scale, my + mouthOpen * 0.75f, inkFill)
            }
            mood == MoodPose.SHOCKED -> {
                canvas.drawCircle(mx, my + 2f * scale, 9f * scale, inkFill)
            }
            mood == MoodPose.SMUG -> {
                val p = Path().apply {
                    moveTo(mx - 14f * scale, my)
                    quadTo(mx + 4f * scale, my + 10f * scale, mx + 16f * scale, my - 5f * scale)
                }
                canvas.drawPath(p, inkPaint)
            }
            mood == MoodPose.ANGRY || mood == MoodPose.FURIOUS -> {
                val p = Path().apply {
                    moveTo(mx - 14f * scale, my + 5f * scale)
                    quadTo(mx, my - 7f * scale, mx + 14f * scale, my + 5f * scale)
                }
                canvas.drawPath(p, inkPaint)
            }
            else -> {
                canvas.drawLine(mx - 12f * scale, my, mx + 12f * scale, my, inkPaint)
            }
        }

        // Animated sweat drops
        if (mood == MoodPose.SWEATING || mood == MoodPose.NERVOUS) {
            val dropY = hy - r * 0.45f + ((timeSec * 45f) % 28f) * scale
            val dx = hx - r * 0.85f * dir
            canvas.drawCircle(dx, dropY, 5.5f * scale, sweatFill)
        }

        // Pulsing comic anger vein '#'
        if (mood == MoodPose.FURIOUS) {
            val vx = hx + r * 0.56f * dir
            val vy = hy - r * 0.54f
            val pulse = (1f + 0.15f * sin(timeSec * 14f)) * scale
            angerPaint.strokeWidth = 3.5f * scale
            canvas.drawLine(vx - 8f * pulse, vy - 3f * pulse, vx + 8f * pulse, vy - 3f * pulse, angerPaint)
            canvas.drawLine(vx - 8f * pulse, vy + 4f * pulse, vx + 8f * pulse, vy + 4f * pulse, angerPaint)
            canvas.drawLine(vx - 3f * pulse, vy - 8f * pulse, vx - 3f * pulse, vy + 8f * pulse, angerPaint)
            canvas.drawLine(vx + 4f * pulse, vy - 8f * pulse, vx + 4f * pulse, vy + 8f * pulse, angerPaint)
        }
    }

    private fun drawHair(
        canvas: Canvas,
        hx: Float,
        hy: Float,
        r: Float,
        scale: Float,
        dir: Float,
        hair: HairStyle
    ) {
        inkPaint.strokeWidth = 5.5f * scale
        when (hair) {
            HairStyle.BALD -> Unit
            HairStyle.SPIKY -> {
                val p = Path().apply {
                    moveTo(hx - r * 0.7f, hy - r * 0.55f)
                    lineTo(hx - r * 0.4f, hy - r * 1.25f)
                    lineTo(hx - r * 0.1f, hy - r * 0.85f)
                    lineTo(hx + r * 0.15f, hy - r * 1.32f)
                    lineTo(hx + r * 0.4f, hy - r * 0.85f)
                    lineTo(hx + r * 0.72f, hy - r * 1.1f)
                    lineTo(hx + r * 0.65f, hy - r * 0.55f)
                    close()
                }
                canvas.drawPath(p, inkFill)
            }
            HairStyle.PARTED -> {
                inkPaint.strokeWidth = 10f * scale
                canvas.drawArc(
                    RectF(hx - r * 0.96f, hy - r * 1.05f, hx + r * 0.96f, hy + r * 0.25f),
                    195f,
                    150f,
                    false,
                    inkPaint
                )
            }
            HairStyle.BOB -> {
                val p = Path().apply {
                    addArc(RectF(hx - r * 1.05f, hy - r * 1.08f, hx + r * 1.05f, hy + r * 0.8f), 175f, 190f)
                }
                inkPaint.strokeWidth = 12f * scale
                canvas.drawPath(p, inkPaint)
            }
            HairStyle.BUN -> {
                canvas.drawCircle(hx - r * 0.25f * dir, hy - r * 1.15f, 20f * scale, inkFill)
            }
            HairStyle.MESSY -> {
                for (i in -2..2) {
                    canvas.drawLine(
                        hx + i * 16f * scale,
                        hy - r * 0.8f,
                        hx + (i * 20f - 8f) * scale,
                        hy - r * 1.22f,
                        inkPaint
                    )
                }
            }
            HairStyle.PONYTAIL -> {
                inkPaint.strokeWidth = 10f * scale
                canvas.drawArc(
                    RectF(hx - r * 0.98f, hy - r * 1.06f, hx + r * 0.98f, hy + r * 0.15f),
                    195f,
                    150f,
                    false,
                    inkPaint
                )
                val p = Path().apply {
                    moveTo(hx - r * 0.85f * dir, hy - r * 0.48f)
                    quadTo(hx - r * 1.68f * dir, hy - r * 0.35f, hx - r * 1.42f * dir, hy + r * 0.65f)
                }
                inkPaint.strokeWidth = 14f * scale
                canvas.drawPath(p, inkPaint)
            }
            HairStyle.LONG_WAVY -> {
                inkPaint.strokeWidth = 13f * scale
                canvas.drawArc(
                    RectF(hx - r * 1.04f, hy - r * 1.08f, hx + r * 1.04f, hy + r * 0.25f),
                    185f,
                    170f,
                    false,
                    inkPaint
                )
                val leftWave = Path().apply {
                    moveTo(hx - r * 0.96f, hy - r * 0.15f)
                    quadTo(hx - r * 1.28f, hy + r * 0.45f, hx - r * 0.95f, hy + r * 1.25f)
                }
                val rightWave = Path().apply {
                    moveTo(hx + r * 0.96f, hy - r * 0.15f)
                    quadTo(hx + r * 1.28f, hy + r * 0.45f, hx + r * 0.95f, hy + r * 1.25f)
                }
                inkPaint.strokeWidth = 12f * scale
                canvas.drawPath(leftWave, inkPaint)
                canvas.drawPath(rightWave, inkPaint)
            }
            HairStyle.TWIN_BRAIDS -> {
                inkPaint.strokeWidth = 11f * scale
                canvas.drawArc(
                    RectF(hx - r * 1.0f, hy - r * 1.06f, hx + r * 1.0f, hy + r * 0.2f),
                    185f,
                    170f,
                    false,
                    inkPaint
                )
                for (side in listOf(-1f, 1f)) {
                    val bx = hx + side * r * 0.95f
                    for (seg in 0..3) {
                        canvas.drawCircle(
                            bx + side * (seg * 3f * scale),
                            hy + (seg * 18f) * scale,
                            9.5f * scale,
                            inkFill
                        )
                    }
                }
            }
        }
    }

    private fun drawMemeSticker(
        canvas: Canvas,
        hx: Float,
        hy: Float,
        unit: Float,
        sticker: MemeSticker,
        timeSec: Float,
        theme: ReelColorTheme
    ) {
        val bob = sin(timeSec * 8f) * 6f * unit
        when (sticker) {
            MemeSticker.NONE -> Unit
            MemeSticker.DEAL_WITH_IT_SHADES -> {
                val shadesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#09090B")
                    style = Paint.Style.FILL
                }
                val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * unit
                }
                val ey = hy - 8f * unit
                canvas.drawRect(hx - 42f * unit, ey - 12f * unit, hx - 4f * unit, ey + 10f * unit, shadesPaint)
                canvas.drawRect(hx + 4f * unit, ey - 12f * unit, hx + 42f * unit, ey + 10f * unit, shadesPaint)
                canvas.drawRect(hx - 42f * unit, ey - 10f * unit, hx + 42f * unit, ey - 4f * unit, shadesPaint)
                canvas.drawLine(hx - 32f * unit, ey - 8f * unit, hx - 22f * unit, ey + 4f * unit, glintPaint)
                canvas.drawLine(hx + 14f * unit, ey - 8f * unit, hx + 24f * unit, ey + 4f * unit, glintPaint)
            }
            MemeSticker.RED_CIRCLE -> {
                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(theme.accentHex)
                    style = Paint.Style.STROKE
                    strokeWidth = 7f * unit
                }
                val pulse = 1f + 0.06f * sin(timeSec * 12f)
                canvas.drawCircle(hx, hy, 84f * unit * pulse, circlePaint)
                // Arrow pointing in from top-right
                canvas.drawLine(hx + 128f * unit, hy - 115f * unit, hx + 78f * unit, hy - 68f * unit, circlePaint)
                canvas.drawLine(hx + 78f * unit, hy - 68f * unit, hx + 104f * unit, hy - 72f * unit, circlePaint)
                canvas.drawLine(hx + 78f * unit, hy - 68f * unit, hx + 82f * unit, hy - 94f * unit, circlePaint)
            }
            MemeSticker.MIND_BLOWN -> {
                angerPaint.strokeWidth = 4.5f * unit
                for (i in 0..7) {
                    val ang = (i * 45.0) * PI / 180.0
                    val r1 = 68f * unit
                    val r2 = (96f + 12f * sin(timeSec * 14f + i)) * unit
                    canvas.drawLine(
                        hx + cos(ang).toFloat() * r1,
                        hy - 24f * unit + sin(ang).toFloat() * r1,
                        hx + cos(ang).toFloat() * r2,
                        hy - 24f * unit + sin(ang).toFloat() * r2,
                        angerPaint
                    )
                }
            }
            MemeSticker.QUESTION_MARKS -> {
                textPaint.color = Color.parseColor(theme.accentHex)
                textPaint.textSize = 42f * unit
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("???", hx, hy - 86f * unit + bob, textPaint)
            }
            MemeSticker.CROWN -> {
                val crownY = hy - 78f * unit + bob * 0.4f
                val crownPath = Path().apply {
                    moveTo(hx - 34f * unit, crownY + 22f * unit)
                    lineTo(hx - 38f * unit, crownY - 12f * unit)
                    lineTo(hx - 14f * unit, crownY + 6f * unit)
                    lineTo(hx, crownY - 22f * unit)
                    lineTo(hx + 14f * unit, crownY + 6f * unit)
                    lineTo(hx + 38f * unit, crownY - 12f * unit)
                    lineTo(hx + 34f * unit, crownY + 22f * unit)
                    close()
                }
                val goldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FACC15")
                    style = Paint.Style.FILL
                }
                canvas.drawPath(crownPath, goldFill)
                inkPaint.strokeWidth = 4f * unit
                canvas.drawPath(crownPath, inkPaint)
            }
            MemeSticker.BROKEN_HEART -> {
                textPaint.color = Color.parseColor(theme.accentHex)
                textPaint.textSize = 46f * unit
                canvas.drawText("</3", hx + 68f * unit, hy - 68f * unit + bob, textPaint)
            }
            MemeSticker.MIC_DROP -> {
                val dropY = hy + 96f * unit + abs(sin(timeSec * 6f)) * 28f * unit
                canvas.drawCircle(hx + 84f * unit, dropY - 14f * unit, 12f * unit, inkFill)
                inkPaint.strokeWidth = 7f * unit
                canvas.drawLine(hx + 84f * unit, dropY - 4f * unit, hx + 96f * unit, dropY + 28f * unit, inkPaint)
            }
        }
    }

    private fun drawProp(
        canvas: Canvas,
        x: Float,
        y: Float,
        scale: Float,
        prop: CharacterProp,
        isFalling: Boolean
    ) {
        inkPaint.strokeWidth = 3.5f * scale
        when (prop) {
            CharacterProp.NONE -> Unit
            CharacterProp.CLIPBOARD, CharacterProp.FOLDER -> {
                val rect = RectF(x - 16f * scale, y - 22f * scale, x + 16f * scale, y + 22f * scale)
                canvas.drawRect(rect, whiteFill)
                canvas.drawRect(rect, inkPaint)
                canvas.drawRect(x - 7f * scale, y - 26f * scale, x + 7f * scale, y - 18f * scale, inkFill)
            }
            CharacterProp.MUG -> {
                canvas.drawRect(x - 11f * scale, y - 12f * scale, x + 11f * scale, y + 14f * scale, whiteFill)
                canvas.drawRect(x - 11f * scale, y - 12f * scale, x + 11f * scale, y + 14f * scale, inkPaint)
            }
            CharacterProp.STETHOSCOPE -> {
                canvas.drawCircle(x, y, 12f * scale, inkPaint)
            }
            CharacterProp.PHONE -> {
                canvas.drawRect(x - 9f * scale, y - 16f * scale, x + 9f * scale, y + 16f * scale, inkFill)
            }
            CharacterProp.RULER -> {
                inkPaint.strokeWidth = 6f * scale
                canvas.drawLine(x, y + 8f * scale, x + 44f * scale, y - 34f * scale, inkPaint)
            }
            CharacterProp.DUMBBELL -> {
                canvas.drawLine(x - 18f * scale, y, x + 18f * scale, y, inkPaint)
                canvas.drawCircle(x - 18f * scale, y, 9f * scale, inkFill)
                canvas.drawCircle(x + 18f * scale, y, 9f * scale, inkFill)
            }
        }
        if (isFalling) {
            inkPaint.strokeWidth = 2.5f * scale
            canvas.drawLine(x - 12f * scale, y - 34f * scale, x - 12f * scale, y - 54f * scale, inkPaint)
            canvas.drawLine(x + 12f * scale, y - 34f * scale, x + 12f * scale, y - 54f * scale, inkPaint)
        }
    }

    private fun drawForegroundDesk(
        canvas: Canvas,
        w: Float,
        floorY: Float,
        unit: Float,
        hasImpact: Boolean,
        prog: Float
    ) {
        val deskRect = RectF(w * 0.14f, floorY - 64f * unit, w * 0.86f, floorY + 26f * unit)
        canvas.drawRect(deskRect, celShadeFill)
        inkPaint.strokeWidth = 5f * unit
        canvas.drawRect(deskRect, inkPaint)

        if (hasImpact && prog < 0.55f) {
            val ix = w * 0.36f
            val iy = deskRect.top
            inkPaint.strokeWidth = 4f * unit
            for (k in 0..5) {
                val ang = (-160.0 + k * 28.0) * PI / 180.0
                val r1 = 18f * unit
                val r2 = (44f + prog * 40f) * unit
                canvas.drawLine(
                    ix + cos(ang).toFloat() * r1,
                    iy + sin(ang).toFloat() * r1,
                    ix + cos(ang).toFloat() * r2,
                    iy + sin(ang).toFloat() * r2,
                    inkPaint
                )
            }
        }
    }

    private fun drawTimeSkipCard(
        canvas: Canvas,
        w: Float,
        h: Float,
        unit: Float,
        cardText: String,
        prog: Float,
        theme: ReelColorTheme
    ) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#09090B") }
        canvas.drawRect(0f, 0f, w, h, bg)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.accentHex)
            style = Paint.Style.STROKE
            strokeWidth = 5f * unit
        }
        canvas.drawRect(44f * unit, 44f * unit, w - 44f * unit, h - 44f * unit, borderPaint)

        val scalePop = (0.85f + sin(prog * PI.toFloat()) * 0.18f)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 68f * unit * scalePop
        canvas.drawText(cardText.uppercase(), w * 0.5f, h * 0.50f, textPaint)

        textPaint.textSize = 22f * unit
        textPaint.color = Color.parseColor("#A1A1AA")
        canvas.drawText("• MEANWHILE •", w * 0.5f, h * 0.56f, textPaint)
    }

    private fun drawOutroCard(
        canvas: Canvas,
        w: Float,
        h: Float,
        unit: Float,
        outroText: String,
        handle: String,
        theme: ReelColorTheme,
        prog: Float
    ) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#09090B") }
        canvas.drawRect(0f, 0f, w, h, bg)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.accentHex)
            style = Paint.Style.STROKE
            strokeWidth = 6f * unit
        }
        canvas.drawRect(36f * unit, 36f * unit, w - 36f * unit, h - 36f * unit, borderPaint)

        val pop = (0.90f + 0.12f * sin(prog * PI.toFloat()))
        val pill = RectF(w * 0.12f, h * 0.40f, w * 0.88f, h * 0.55f)
        canvas.drawRoundRect(pill, 28f * unit, 28f * unit, accentFill)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 40f * unit * pop
        canvas.drawText(outroText.take(28), w * 0.5f, pill.centerY() + 14f * unit, textPaint)

        val displayHandle = formatWatermarkHandle(handle)
        if (displayHandle.isNotBlank()) {
            textPaint.color = Color.parseColor("#E4E4E7")
            textPaint.textSize = 28f * unit
            canvas.drawText(displayHandle, w * 0.5f, h * 0.63f, textPaint)
        }
    }

    private fun drawIntroHookBanner(
        canvas: Canvas,
        w: Float,
        unit: Float,
        bannerText: String,
        theme: ReelColorTheme
    ) {
        val rect = RectF(44f * unit, 36f * unit, w - 44f * unit, 96f * unit)
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EE09090B")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.accentHex)
            style = Paint.Style.STROKE
            strokeWidth = 3f * unit
        }
        canvas.drawRoundRect(rect, 18f * unit, 18f * unit, bannerPaint)
        canvas.drawRoundRect(rect, 18f * unit, 18f * unit, borderPaint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 26f * unit
        canvas.drawText(bannerText.take(36), rect.centerX(), rect.centerY() + 9f * unit, textPaint)
    }

    private fun drawSpeedLines(canvas: Canvas, w: Float, h: Float, unit: Float, timeSec: Float) {
        inkPaint.strokeWidth = 3f * unit
        for (i in 0..10) {
            val ly = h * (0.18f + i * 0.06f)
            val offset = ((timeSec * 900f + i * 140f) % w)
            canvas.drawLine(offset, ly, offset + 120f * unit, ly, inkPaint)
        }
    }

    private fun drawBonkDustCloud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        prog: Float,
        theme: ReelColorTheme
    ) {
        val pulse = 1f + 0.08f * sin(prog * 24f)
        val puffs = listOf(
            Triple(-70f, 10f, 58f),
            Triple(70f, 16f, 62f),
            Triple(-25f, -45f, 64f),
            Triple(35f, -38f, 60f),
            Triple(0f, 20f, 72f)
        )
        puffs.forEach { (dx, dy, r) ->
            canvas.drawCircle(cx + dx * unit * pulse, cy + dy * unit * pulse, r * unit, whiteFill)
        }
        inkPaint.strokeWidth = 6f * unit
        puffs.forEach { (dx, dy, r) ->
            canvas.drawCircle(cx + dx * unit * pulse, cy + dy * unit * pulse, r * unit, inkPaint)
        }
        textPaint.color = Color.parseColor(theme.accentHex)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 64f * unit * pulse
        canvas.drawText("BONK!", cx, cy + 16f * unit, textPaint)
    }

    private fun drawSpeechBubble(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        frameWidth: Float,
        unit: Float,
        bubble: String,
        theme: ReelColorTheme
    ) {
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 26f * unit
        val textW = textPaint.measureText(bubble).coerceAtLeast(110f * unit)
        val padX = 28f * unit
        val boxW = (textW + padX * 2).coerceAtMost(420f * unit)
        val boxH = 66f * unit
        val maxLeft = (frameWidth - boxW - 24f * unit).coerceAtLeast(24f * unit)
        val left = (cx - boxW / 2f).coerceIn(24f * unit, maxLeft)
        val rect = RectF(left, cy - boxH / 2f, left + boxW, cy + boxH / 2f)

        val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (theme.isDarkStage) theme.accentHex else "#09090B")
            style = Paint.Style.STROKE
            strokeWidth = 4.5f * unit
        }
        canvas.drawRoundRect(rect, 22f * unit, 22f * unit, bubbleFill)
        canvas.drawRoundRect(rect, 22f * unit, 22f * unit, bubbleStroke)

        textPaint.color = Color.parseColor("#09090B")
        canvas.drawText(bubble.take(28), rect.centerX(), rect.centerY() + 9f * unit, textPaint)
    }

    private fun drawCaptions(
        canvas: Canvas,
        w: Float,
        h: Float,
        unit: Float,
        text: String,
        progress: Float,
        style: CaptionStyle,
        fontStyle: CaptionFontStyle,
        position: CaptionPosition,
        theme: ReelColorTheme
    ) {
        val baseY = if (position == CaptionPosition.CENTER_POP) h * 0.56f else h * 0.83f
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return

        textPaint.typeface = when (fontStyle) {
            CaptionFontStyle.BOLD_SANS -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            CaptionFontStyle.COMIC_PUNCH -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            CaptionFontStyle.TYPEWRITER_MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            CaptionFontStyle.SERIF_DRAMA -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        if (style == CaptionStyle.CLASSIC) {
            val lines = wrapLines(words, maxChars = 28)
            textPaint.textSize = 30f * unit
            val lineH = 38f * unit
            val boxH = lines.size * lineH + 26f * unit
            val box = RectF(w * 0.08f, baseY - boxH / 2f, w * 0.92f, baseY + boxH / 2f)
            canvas.drawRoundRect(box, 16f * unit, 16f * unit, captionBgPaint)
            textPaint.color = Color.WHITE
            lines.forEachIndexed { idx, line ->
                canvas.drawText(line, w * 0.5f, box.top + 38f * unit + idx * lineH, textPaint)
            }
        } else {
            val activeIdx = (progress * words.size).toInt().coerceIn(0, words.lastIndex)
            val windowStart = (activeIdx / 4) * 4
            val chunk = words.subList(windowStart, (windowStart + 4).coerceAtMost(words.size))
            val localActive = activeIdx - windowStart

            textPaint.textSize = 34f * unit
            val spacing = 14f * unit
            val widths = chunk.map { textPaint.measureText(it) + 18f * unit }
            val totalW = widths.sum() + spacing * (chunk.size - 1).coerceAtLeast(0)
            var cursorX = (w - totalW) * 0.5f

            val bgBox = RectF(
                (cursorX - 18f * unit).coerceAtLeast(24f * unit),
                baseY - 34f * unit,
                (cursorX + totalW + 18f * unit).coerceAtMost(w - 24f * unit),
                baseY + 34f * unit
            )
            canvas.drawRoundRect(bgBox, 16f * unit, 16f * unit, captionBgPaint)

            val isBounce = style == CaptionStyle.VIRAL_BOUNCE
            val wordDuration = 1f / words.size.coerceAtLeast(1)
            val wordLocalProg = ((progress - activeIdx * wordDuration) / wordDuration).coerceIn(0f, 1f)
            val bounceLift = if (isBounce) sin(wordLocalProg * PI.toFloat()) * 10f * unit else 0f

            popWordBgPaint.color = if (isBounce) Color.parseColor(theme.accentHex) else Color.WHITE

            chunk.forEachIndexed { i, word ->
                val ww = widths[i]
                val cx = cursorX + ww * 0.5f
                if (i == localActive) {
                    val pill = RectF(
                        cursorX,
                        baseY - 24f * unit - bounceLift,
                        cursorX + ww,
                        baseY + 24f * unit - bounceLift
                    )
                    canvas.drawRoundRect(pill, 10f * unit, 10f * unit, popWordBgPaint)
                    textPaint.color = if (isBounce) Color.WHITE else Color.parseColor("#09090B")
                    textPaint.textSize = if (isBounce) 39f * unit else 37f * unit
                    canvas.drawText(word, cx, baseY + 12f * unit - bounceLift, textPaint)
                } else {
                    textPaint.color = Color.WHITE
                    textPaint.textSize = 33f * unit
                    canvas.drawText(word, cx, baseY + 12f * unit, textPaint)
                }
                cursorX += ww + spacing
            }
        }
    }

    private fun drawWatermark(
        canvas: Canvas,
        w: Float,
        h: Float,
        unit: Float,
        handle: String,
        darkBg: Boolean
    ) {
        val displayHandle = formatWatermarkHandle(handle)
        if (displayHandle.isBlank()) return
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkBg) Color.parseColor("#88FFFFFF") else Color.parseColor("#8818181B")
            textSize = 22f * unit
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(displayHandle, w - 28f * unit, h - 26f * unit, wmPaint)
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
