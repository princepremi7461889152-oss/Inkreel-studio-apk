package com.example.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.model.ReelColorTheme
import com.example.model.RoomSetting
import java.util.EnumMap
import kotlin.math.sin

object RoomBackgroundRenderer {

    private data class ThemeColors(
        val wallColor: Int,
        val floorColor: Int,
        val inkColor: Int,
        val thinInkColor: Int,
        val whiteFillColor: Int,
        val shadeColor: Int,
        val midFillColor: Int,
        val darkShadeColor: Int
    )

    private val themeColorCache: EnumMap<ReelColorTheme, ThemeColors> = EnumMap(ReelColorTheme::class.java)

    private fun colorsFor(theme: ReelColorTheme): ThemeColors {
        return themeColorCache[theme] ?: ThemeColors(
            wallColor = Color.parseColor(theme.wallHex),
            floorColor = Color.parseColor(theme.floorHex),
            inkColor = Color.parseColor(theme.inkHex),
            thinInkColor = if (theme.isDarkStage) Color.parseColor("#A1A1AA") else Color.parseColor("#52525B"),
            whiteFillColor = if (theme.isDarkStage) Color.parseColor("#27272A") else Color.WHITE,
            shadeColor = if (theme.isDarkStage) Color.parseColor("#3F3F46") else Color.parseColor("#E0E0E4"),
            midFillColor = if (theme.isDarkStage) Color.parseColor("#52525B") else Color.parseColor("#D4D4D8"),
            darkShadeColor = if (theme.isDarkStage) Color.parseColor("#09090B") else Color.parseColor("#27272A")
        ).also { themeColorCache[theme] = it }
    }

    private class RoomPaints {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val darkShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val midFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val whiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val thinInkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val chalkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E4E4E7")
            isFakeBoldText = true
        }
        val redFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DC2626")
            style = Paint.Style.FILL
        }
        val whiteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
        }
        val whiteCenterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val path = Path()
        val rect = RectF()
    }

    private val threadPaints = object : ThreadLocal<RoomPaints>() {
        override fun initialValue(): RoomPaints = RoomPaints()
    }

    fun drawRoom(
        canvas: Canvas,
        w: Float,
        h: Float,
        setting: RoomSetting,
        doorOpenFactor: Float,
        timeSec: Float,
        colorTheme: ReelColorTheme = ReelColorTheme.CLASSIC_MONO
    ) {
        val p = threadPaints.get()!!
        val colors = colorsFor(colorTheme)
        val unit = w / 720f

        p.bgPaint.color = colors.wallColor
        p.floorPaint.color = colors.floorColor
        p.inkPaint.color = colors.inkColor
        p.thinInkPaint.color = colors.thinInkColor
        p.textPaint.color = colors.inkColor
        p.whiteFillPaint.color = colors.whiteFillColor
        p.shadePaint.color = colors.shadeColor
        p.midFillPaint.color = colors.midFillColor
        p.darkShadePaint.color = colors.darkShadeColor

        p.inkPaint.strokeWidth = 4f * unit
        p.thinInkPaint.strokeWidth = 2.2f * unit

        // Wall + floor horizon
        canvas.drawRect(0f, 0f, w, h, p.bgPaint)
        val floorY = h * 0.72f
        canvas.drawRect(0f, floorY, w, h, p.floorPaint)
        canvas.drawLine(0f, floorY, w, floorY, p.inkPaint)

        // Subtle perspective corner lines
        canvas.drawLine(0f, h * 0.08f, w * 0.14f, floorY, p.thinInkPaint)
        canvas.drawLine(w, h * 0.08f, w * 0.86f, floorY, p.thinInkPaint)

        when (setting) {
            RoomSetting.CLASSROOM -> drawClassroom(canvas, p, w, h, floorY, unit, doorOpenFactor, timeSec)
            RoomSetting.OFFICE -> drawOffice(canvas, p, w, h, floorY, unit, doorOpenFactor)
            RoomSetting.HOSPITAL -> drawHospital(canvas, p, w, h, floorY, unit, doorOpenFactor, timeSec)
            RoomSetting.INTERVIEW_ROOM -> drawInterviewRoom(canvas, p, w, h, floorY, unit, doorOpenFactor)
            RoomSetting.COFFEE_SHOP -> drawCoffeeShop(canvas, p, w, h, floorY, unit, doorOpenFactor)
            RoomSetting.GYM -> drawGym(canvas, p, w, h, floorY, unit)
            RoomSetting.LIVING_ROOM -> drawLivingRoom(canvas, p, w, h, floorY, unit, doorOpenFactor)
            RoomSetting.KITCHEN -> drawKitchen(canvas, p, w, h, floorY, unit, doorOpenFactor, timeSec)
            RoomSetting.STREET_PARK -> drawStreetPark(canvas, p, w, h, floorY, unit, timeSec)
            RoomSetting.GAMING_ROOM -> drawGamingRoom(canvas, p, w, h, floorY, unit, timeSec)
        }
    }

    private fun drawSwingingDoor(
        canvas: Canvas,
        p: RoomPaints,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        unit: Float,
        openFactor: Float
    ) {
        // Dark doorway recess
        canvas.drawRect(left, top, left + width, top + height, p.darkShadePaint)
        canvas.drawRect(left, top, left + width, top + height, p.inkPaint)

        // Swinging door leaf
        val swing = openFactor.coerceIn(0f, 1f)
        val leafW = width * (1f - swing * 0.72f)
        val skewY = swing * 24f * unit
        p.path.rewind()
        p.path.moveTo(left, top)
        p.path.lineTo(left + leafW, top - skewY)
        p.path.lineTo(left + leafW, top + height + skewY * 0.6f)
        p.path.lineTo(left, top + height)
        p.path.close()
        canvas.drawPath(p.path, p.whiteFillPaint)
        canvas.drawPath(p.path, p.inkPaint)
        // Door knob
        if (leafW > 14f * unit) {
            canvas.drawCircle(
                left + leafW - 14f * unit,
                top + height * 0.52f,
                5f * unit,
                p.darkShadePaint
            )
        }
    }

    private fun drawWallClock(
        canvas: Canvas,
        p: RoomPaints,
        cx: Float,
        cy: Float,
        r: Float,
        unit: Float,
        timeSec: Float
    ) {
        canvas.drawCircle(cx, cy, r, p.whiteFillPaint)
        canvas.drawCircle(cx, cy, r, p.inkPaint)
        canvas.drawLine(cx, cy, cx, cy - r * 0.55f, p.inkPaint)
        val angle = timeSec * 1.4f
        canvas.drawLine(
            cx,
            cy,
            cx + kotlin.math.cos(angle) * r * 0.68f,
            cy + sin(angle) * r * 0.68f,
            p.thinInkPaint
        )
        canvas.drawCircle(cx, cy, 3f * unit, p.darkShadePaint)
    }

    private fun drawClassroom(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float,
        timeSec: Float
    ) {
        drawSwingingDoor(canvas, p, w * 0.05f, floorY - 310f * unit, 130f * unit, 310f * unit, unit, doorOpen)
        drawWallClock(canvas, p, w * 0.52f, h * 0.14f, 34f * unit, unit, timeSec)

        // Chalkboard
        val board = p.rect.apply { set(w * 0.28f, h * 0.22f, w * 0.90f, h * 0.44f) }
        canvas.drawRect(board, p.darkShadePaint)
        canvas.drawRect(board, p.inkPaint)
        p.chalkPaint.textSize = 22f * unit
        canvas.drawText("2 + 2 = ?", board.left + 28f * unit, board.top + 60f * unit, p.chalkPaint)
        canvas.drawText("EXAM TODAY!", board.left + 28f * unit, board.top + 110f * unit, p.chalkPaint)

        // Background student silhouettes at desks
        for (i in 0..1) {
            val sx = w * (0.42f + i * 0.26f)
            val sy = floorY - 26f * unit
            canvas.drawCircle(sx, sy - 76f * unit, 20f * unit, p.shadePaint)
            canvas.drawCircle(sx, sy - 76f * unit, 20f * unit, p.thinInkPaint)
            canvas.drawRect(sx - 44f * unit, sy - 42f * unit, sx + 44f * unit, sy + 26f * unit, p.midFillPaint)
            canvas.drawRect(sx - 44f * unit, sy - 42f * unit, sx + 44f * unit, sy + 26f * unit, p.thinInkPaint)
        }
    }

    private fun drawOffice(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float
    ) {
        drawSwingingDoor(canvas, p, w * 0.04f, floorY - 300f * unit, 122f * unit, 300f * unit, unit, doorOpen)

        // Glass window with blinds
        val win = p.rect.apply { set(w * 0.30f, h * 0.16f, w * 0.88f, h * 0.40f) }
        canvas.drawRect(win, p.whiteFillPaint)
        canvas.drawRect(win, p.inkPaint)
        for (i in 1..6) {
            val ly = win.top + (win.height() / 7f) * i
            canvas.drawLine(win.left, ly, win.right, ly, p.thinInkPaint)
        }

        // Filing cabinet + chart
        val cab = p.rect.apply { set(w * 0.72f, floorY - 190f * unit, w * 0.90f, floorY) }
        canvas.drawRect(cab, p.shadePaint)
        canvas.drawRect(cab, p.inkPaint)
        canvas.drawLine(cab.left, cab.top + 64f * unit, cab.right, cab.top + 64f * unit, p.thinInkPaint)
        canvas.drawLine(cab.left, cab.top + 128f * unit, cab.right, cab.top + 128f * unit, p.thinInkPaint)
    }

    private fun drawHospital(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float,
        timeSec: Float
    ) {
        drawSwingingDoor(canvas, p, w * 0.05f, floorY - 300f * unit, 124f * unit, 300f * unit, unit, doorOpen)

        // Medical cross sign
        val cx = w * 0.36f
        val cy = h * 0.22f
        canvas.drawCircle(cx, cy, 34f * unit, p.whiteFillPaint)
        canvas.drawCircle(cx, cy, 34f * unit, p.inkPaint)
        canvas.drawRect(cx - 6f * unit, cy - 18f * unit, cx + 6f * unit, cy + 18f * unit, p.redFillPaint)
        canvas.drawRect(cx - 18f * unit, cy - 6f * unit, cx + 18f * unit, cy + 6f * unit, p.redFillPaint)

        // ECG monitor
        val mon = p.rect.apply { set(w * 0.56f, h * 0.20f, w * 0.88f, h * 0.35f) }
        canvas.drawRect(mon, p.darkShadePaint)
        canvas.drawRect(mon, p.inkPaint)
        p.whiteStrokePaint.strokeWidth = 3f * unit
        val midY = mon.centerY()
        val blipX = mon.left + ((timeSec * 80f * unit) % mon.width())
        p.path.rewind()
        p.path.moveTo(mon.left + 8f * unit, midY)
        p.path.lineTo((blipX - 18f * unit).coerceAtLeast(mon.left + 8f * unit), midY)
        p.path.lineTo(blipX, midY - 32f * unit)
        p.path.lineTo((blipX + 12f * unit).coerceAtMost(mon.right - 8f * unit), midY + 18f * unit)
        p.path.lineTo((blipX + 24f * unit).coerceAtMost(mon.right - 8f * unit), midY)
        p.path.lineTo(mon.right - 8f * unit, midY)
        canvas.drawPath(p.path, p.whiteStrokePaint)

        // Exam bed
        canvas.drawRect(w * 0.52f, floorY - 76f * unit, w * 0.92f, floorY - 32f * unit, p.whiteFillPaint)
        canvas.drawRect(w * 0.52f, floorY - 76f * unit, w * 0.92f, floorY - 32f * unit, p.inkPaint)
        canvas.drawLine(w * 0.56f, floorY - 32f * unit, w * 0.56f, floorY, p.inkPaint)
        canvas.drawLine(w * 0.88f, floorY - 32f * unit, w * 0.88f, floorY, p.inkPaint)
    }

    private fun drawInterviewRoom(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float
    ) {
        drawSwingingDoor(canvas, p, w * 0.05f, floorY - 300f * unit, 120f * unit, 300f * unit, unit, doorOpen)
        val sign = p.rect.apply { set(w * 0.32f, h * 0.20f, w * 0.86f, h * 0.31f) }
        canvas.drawRect(sign, p.whiteFillPaint)
        canvas.drawRect(sign, p.inkPaint)
        p.textPaint.textSize = 24f * unit
        canvas.drawText("INTERVIEW ROOM 4", sign.centerX(), sign.centerY() + 8f * unit, p.textPaint)
    }

    private fun drawCoffeeShop(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float
    ) {
        drawSwingingDoor(canvas, p, w * 0.04f, floorY - 295f * unit, 118f * unit, 295f * unit, unit, doorOpen)
        val menu = p.rect.apply { set(w * 0.30f, h * 0.16f, w * 0.86f, h * 0.36f) }
        canvas.drawRect(menu, p.darkShadePaint)
        canvas.drawRect(menu, p.inkPaint)
        p.whiteCenterTextPaint.textSize = 22f * unit
        canvas.drawText("ESPRESSO   $9", menu.centerX(), menu.top + 56f * unit, p.whiteCenterTextPaint)
        canvas.drawText("OAT LATTE  $12", menu.centerX(), menu.top + 104f * unit, p.whiteCenterTextPaint)

        // Counter on right
        val counter = p.rect.apply { set(w * 0.58f, floorY - 135f * unit, w * 0.94f, floorY) }
        canvas.drawRect(counter, p.shadePaint)
        canvas.drawRect(counter, p.inkPaint)
    }

    private fun drawGym(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float
    ) {
        // Wall poster
        val poster = p.rect.apply { set(w * 0.22f, h * 0.16f, w * 0.78f, h * 0.28f) }
        canvas.drawRect(poster, p.darkShadePaint)
        canvas.drawRect(poster, p.inkPaint)
        p.whiteCenterTextPaint.textSize = 26f * unit
        canvas.drawText("NO PAIN NO GAIN", poster.centerX(), poster.centerY() + 9f * unit, p.whiteCenterTextPaint)

        // Squat rack
        canvas.drawLine(w * 0.64f, floorY - 270f * unit, w * 0.64f, floorY, p.inkPaint)
        canvas.drawLine(w * 0.88f, floorY - 270f * unit, w * 0.88f, floorY, p.inkPaint)
        canvas.drawLine(w * 0.60f, floorY - 190f * unit, w * 0.92f, floorY - 190f * unit, p.inkPaint)
        canvas.drawCircle(w * 0.60f, floorY - 190f * unit, 22f * unit, p.darkShadePaint)
        canvas.drawCircle(w * 0.92f, floorY - 190f * unit, 22f * unit, p.darkShadePaint)
    }

    private fun drawLivingRoom(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float
    ) {
        drawSwingingDoor(canvas, p, w * 0.04f, floorY - 295f * unit, 118f * unit, 295f * unit, unit, doorOpen)
        // Framed portrait
        val frame = p.rect.apply { set(w * 0.34f, h * 0.19f, w * 0.56f, h * 0.34f) }
        canvas.drawRect(frame, p.whiteFillPaint)
        canvas.drawRect(frame, p.inkPaint)
        canvas.drawCircle(frame.centerX(), frame.centerY() - 10f * unit, 20f * unit, p.thinInkPaint)

        // Sofa on right
        val sofa = p.rect.apply { set(w * 0.54f, floorY - 115f * unit, w * 0.94f, floorY) }
        canvas.drawRect(sofa, p.shadePaint)
        canvas.drawRect(sofa, p.inkPaint)
        canvas.drawRect(sofa.left, sofa.top + 48f * unit, sofa.right, sofa.bottom - 18f * unit, p.midFillPaint)
    }

    private fun drawKitchen(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float,
        timeSec: Float
    ) {
        drawSwingingDoor(canvas, p, w * 0.04f, floorY - 295f * unit, 114f * unit, 295f * unit, unit, doorOpen)

        // Kitchen shelf + spice jars
        val shelf = p.rect.apply { set(w * 0.28f, h * 0.21f, w * 0.70f, h * 0.23f) }
        canvas.drawRect(shelf, p.darkShadePaint)
        for (i in 0..3) {
            val jx = w * (0.33f + i * 0.09f)
            canvas.drawRect(jx, shelf.top - 34f * unit, jx + 28f * unit, shelf.top, p.whiteFillPaint)
            canvas.drawRect(jx, shelf.top - 34f * unit, jx + 28f * unit, shelf.top, p.inkPaint)
        }

        // Fridge on right
        val fridge = p.rect.apply { set(w * 0.74f, floorY - 285f * unit, w * 0.94f, floorY) }
        canvas.drawRect(fridge, p.whiteFillPaint)
        canvas.drawRect(fridge, p.inkPaint)
        canvas.drawLine(fridge.left, fridge.top + 100f * unit, fridge.right, fridge.top + 100f * unit, p.inkPaint)
        canvas.drawLine(fridge.left + 16f * unit, fridge.top + 52f * unit, fridge.left + 16f * unit, fridge.top + 82f * unit, p.inkPaint)
        canvas.drawLine(fridge.left + 16f * unit, fridge.top + 132f * unit, fridge.left + 16f * unit, fridge.top + 182f * unit, p.inkPaint)

        // Gas stove counter
        val counter = p.rect.apply { set(w * 0.42f, floorY - 122f * unit, w * 0.72f, floorY) }
        canvas.drawRect(counter, p.shadePaint)
        canvas.drawRect(counter, p.inkPaint)
        // Kettle / pot with gentle steam
        canvas.drawRect(w * 0.50f, counter.top - 36f * unit, w * 0.62f, counter.top, p.midFillPaint)
        canvas.drawRect(w * 0.50f, counter.top - 36f * unit, w * 0.62f, counter.top, p.inkPaint)
        val steamShift = sin(timeSec * 5f) * 8f * unit
        canvas.drawLine(w * 0.54f, counter.top - 44f * unit, w * 0.54f + steamShift, counter.top - 78f * unit, p.thinInkPaint)
        canvas.drawLine(w * 0.58f, counter.top - 44f * unit, w * 0.58f - steamShift, counter.top - 84f * unit, p.thinInkPaint)
    }

    private fun drawStreetPark(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        timeSec: Float
    ) {
        // Drifting clouds
        val cloudOffset = (timeSec * 14f * unit) % (w + 180f * unit)
        val cx1 = cloudOffset - 90f * unit
        canvas.drawOval(cx1, h * 0.14f, cx1 + 120f * unit, h * 0.19f, p.whiteFillPaint)
        canvas.drawOval(cx1, h * 0.14f, cx1 + 120f * unit, h * 0.19f, p.thinInkPaint)
        canvas.drawOval(w * 0.55f, h * 0.21f, w * 0.80f, h * 0.26f, p.whiteFillPaint)
        canvas.drawOval(w * 0.55f, h * 0.21f, w * 0.80f, h * 0.26f, p.thinInkPaint)

        // Street lamp on left
        canvas.drawLine(w * 0.12f, floorY, w * 0.12f, h * 0.26f, p.inkPaint)
        canvas.drawLine(w * 0.12f, h * 0.26f, w * 0.24f, h * 0.26f, p.inkPaint)
        canvas.drawCircle(w * 0.24f, h * 0.28f, 14f * unit, p.whiteFillPaint)
        canvas.drawCircle(w * 0.24f, h * 0.28f, 14f * unit, p.inkPaint)

        // Cricket wickets on right
        for (i in 0..2) {
            val sx = w * (0.78f + i * 0.03f)
            canvas.drawLine(sx, floorY, sx, floorY - 110f * unit, p.inkPaint)
        }
        canvas.drawLine(w * 0.775f, floorY - 112f * unit, w * 0.845f, floorY - 112f * unit, p.inkPaint)

        // Dashed road markings
        for (i in 0..3) {
            val rx = w * (0.10f + i * 0.24f)
            canvas.drawRect(rx, floorY + 58f * unit, rx + 70f * unit, floorY + 70f * unit, p.whiteFillPaint)
        }
    }

    private fun drawGamingRoom(
        canvas: Canvas,
        p: RoomPaints,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        timeSec: Float
    ) {
        // Wall poster
        val poster = p.rect.apply { set(w * 0.14f, h * 0.17f, w * 0.38f, h * 0.34f) }
        canvas.drawRect(poster, p.darkShadePaint)
        canvas.drawRect(poster, p.inkPaint)
        p.whiteCenterTextPaint.textSize = 20f * unit
        canvas.drawText("GG WP!", poster.centerX(), poster.centerY() + 7f * unit, p.whiteCenterTextPaint)

        // Gaming desk + monitor on right
        val desk = p.rect.apply { set(w * 0.50f, floorY - 118f * unit, w * 0.92f, floorY) }
        canvas.drawRect(desk, p.shadePaint)
        canvas.drawRect(desk, p.inkPaint)

        val monitor = p.rect.apply { set(w * 0.56f, desk.top - 96f * unit, w * 0.86f, desk.top - 16f * unit) }
        canvas.drawRect(monitor, p.darkShadePaint)
        canvas.drawRect(monitor, p.inkPaint)
        canvas.drawLine(monitor.centerX(), monitor.bottom, monitor.centerX(), desk.top, p.inkPaint)
        val pulse = 18f * unit + sin(timeSec * 8f) * 6f * unit
        p.whiteStrokePaint.strokeWidth = 3f * unit
        canvas.drawCircle(monitor.centerX(), monitor.centerY(), pulse, p.whiteStrokePaint)
    }
}

