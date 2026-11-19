package com.example.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.model.ReelColorTheme
import com.example.model.RoomSetting
import kotlin.math.sin

object RoomBackgroundRenderer {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FAFAFA")
    }
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EDEDEF")
    }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0E0E4")
    }
    private val darkShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#27272A")
    }
    private val midFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D4D4D8")
    }
    private val whiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#111111")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val thinInkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#52525B")
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#18181B")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
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
        val unit = w / 720f
        bgPaint.color = Color.parseColor(colorTheme.wallHex)
        floorPaint.color = Color.parseColor(colorTheme.floorHex)
        inkPaint.color = Color.parseColor(colorTheme.inkHex)
        thinInkPaint.color = if (colorTheme.isDarkStage) Color.parseColor("#A1A1AA") else Color.parseColor("#52525B")
        textPaint.color = Color.parseColor(colorTheme.inkHex)
        whiteFillPaint.color = if (colorTheme.isDarkStage) Color.parseColor("#27272A") else Color.WHITE
        shadePaint.color = if (colorTheme.isDarkStage) Color.parseColor("#3F3F46") else Color.parseColor("#E0E0E4")
        midFillPaint.color = if (colorTheme.isDarkStage) Color.parseColor("#52525B") else Color.parseColor("#D4D4D8")
        darkShadePaint.color = if (colorTheme.isDarkStage) Color.parseColor("#09090B") else Color.parseColor("#27272A")

        inkPaint.strokeWidth = 4f * unit
        thinInkPaint.strokeWidth = 2.2f * unit

        // Wall + floor horizon
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        val floorY = h * 0.72f
        canvas.drawRect(0f, floorY, w, h, floorPaint)
        canvas.drawLine(0f, floorY, w, floorY, inkPaint)

        // Subtle perspective corner lines
        canvas.drawLine(0f, h * 0.08f, w * 0.14f, floorY, thinInkPaint)
        canvas.drawLine(w, h * 0.08f, w * 0.86f, floorY, thinInkPaint)

        when (setting) {
            RoomSetting.CLASSROOM -> drawClassroom(canvas, w, h, floorY, unit, doorOpenFactor, timeSec)
            RoomSetting.OFFICE -> drawOffice(canvas, w, h, floorY, unit, doorOpenFactor)
            RoomSetting.HOSPITAL -> drawHospital(canvas, w, h, floorY, unit, doorOpenFactor, timeSec)
            RoomSetting.INTERVIEW_ROOM -> drawInterviewRoom(canvas, w, h, floorY, unit, doorOpenFactor)
            RoomSetting.COFFEE_SHOP -> drawCoffeeShop(canvas, w, h, floorY, unit, doorOpenFactor)
            RoomSetting.GYM -> drawGym(canvas, w, h, floorY, unit)
            RoomSetting.LIVING_ROOM -> drawLivingRoom(canvas, w, h, floorY, unit, doorOpenFactor)
            RoomSetting.KITCHEN -> drawKitchen(canvas, w, h, floorY, unit, doorOpenFactor, timeSec)
            RoomSetting.STREET_PARK -> drawStreetPark(canvas, w, h, floorY, unit, timeSec)
            RoomSetting.GAMING_ROOM -> drawGamingRoom(canvas, w, h, floorY, unit, timeSec)
        }
    }

    private fun drawSwingingDoor(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        unit: Float,
        openFactor: Float
    ) {
        // Dark doorway recess
        canvas.drawRect(left, top, left + width, top + height, darkShadePaint)
        canvas.drawRect(left, top, left + width, top + height, inkPaint)

        // Swinging door leaf
        val swing = openFactor.coerceIn(0f, 1f)
        val leafW = width * (1f - swing * 0.72f)
        val skewY = swing * 24f * unit
        val doorPath = Path().apply {
            moveTo(left, top)
            lineTo(left + leafW, top - skewY)
            lineTo(left + leafW, top + height + skewY * 0.6f)
            lineTo(left, top + height)
            close()
        }
        canvas.drawPath(doorPath, whiteFillPaint)
        canvas.drawPath(doorPath, inkPaint)
        // Door knob
        if (leafW > 14f * unit) {
            canvas.drawCircle(
                left + leafW - 14f * unit,
                top + height * 0.52f,
                5f * unit,
                darkShadePaint
            )
        }
    }

    private fun drawWallClock(canvas: Canvas, cx: Float, cy: Float, r: Float, unit: Float, timeSec: Float) {
        canvas.drawCircle(cx, cy, r, whiteFillPaint)
        canvas.drawCircle(cx, cy, r, inkPaint)
        canvas.drawLine(cx, cy, cx, cy - r * 0.55f, inkPaint)
        val angle = timeSec * 1.4f
        canvas.drawLine(
            cx,
            cy,
            cx + kotlin.math.cos(angle) * r * 0.68f,
            cy + sin(angle) * r * 0.68f,
            thinInkPaint
        )
        canvas.drawCircle(cx, cy, 3f * unit, darkShadePaint)
    }

    private fun drawClassroom(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float,
        timeSec: Float
    ) {
        drawSwingingDoor(canvas, w * 0.05f, floorY - 310f * unit, 130f * unit, 310f * unit, unit, doorOpen)
        drawWallClock(canvas, w * 0.52f, h * 0.14f, 34f * unit, unit, timeSec)

        // Chalkboard
        val board = RectF(w * 0.28f, h * 0.22f, w * 0.90f, h * 0.44f)
        canvas.drawRect(board, darkShadePaint)
        canvas.drawRect(board, inkPaint)
        val chalk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E4E4E7")
            textSize = 22f * unit
            isFakeBoldText = true
        }
        canvas.drawText("2 + 2 = ?", board.left + 28f * unit, board.top + 60f * unit, chalk)
        canvas.drawText("EXAM TODAY!", board.left + 28f * unit, board.top + 110f * unit, chalk)

        // Background student silhouettes at desks
        for (i in 0..1) {
            val sx = w * (0.42f + i * 0.26f)
            val sy = floorY - 26f * unit
            canvas.drawCircle(sx, sy - 76f * unit, 20f * unit, shadePaint)
            canvas.drawCircle(sx, sy - 76f * unit, 20f * unit, thinInkPaint)
            canvas.drawRect(sx - 44f * unit, sy - 42f * unit, sx + 44f * unit, sy + 26f * unit, midFillPaint)
            canvas.drawRect(sx - 44f * unit, sy - 42f * unit, sx + 44f * unit, sy + 26f * unit, thinInkPaint)
        }
    }

    private fun drawOffice(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float
    ) {
        drawSwingingDoor(canvas, w * 0.04f, floorY - 300f * unit, 122f * unit, 300f * unit, unit, doorOpen)

        // Glass window with blinds
        val win = RectF(w * 0.30f, h * 0.16f, w * 0.88f, h * 0.40f)
        canvas.drawRect(win, whiteFillPaint)
        canvas.drawRect(win, inkPaint)
        for (i in 1..6) {
            val ly = win.top + (win.height() / 7f) * i
            canvas.drawLine(win.left, ly, win.right, ly, thinInkPaint)
        }

        // Filing cabinet + chart
        val cab = RectF(w * 0.72f, floorY - 190f * unit, w * 0.90f, floorY)
        canvas.drawRect(cab, shadePaint)
        canvas.drawRect(cab, inkPaint)
        canvas.drawLine(cab.left, cab.top + 64f * unit, cab.right, cab.top + 64f * unit, thinInkPaint)
        canvas.drawLine(cab.left, cab.top + 128f * unit, cab.right, cab.top + 128f * unit, thinInkPaint)
    }

    private fun drawHospital(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float,
        timeSec: Float
    ) {
        drawSwingingDoor(canvas, w * 0.05f, floorY - 300f * unit, 124f * unit, 300f * unit, unit, doorOpen)

        // Medical cross sign
        val cx = w * 0.36f
        val cy = h * 0.22f
        canvas.drawCircle(cx, cy, 34f * unit, whiteFillPaint)
        canvas.drawCircle(cx, cy, 34f * unit, inkPaint)
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DC2626")
            style = Paint.Style.FILL
        }
        canvas.drawRect(cx - 6f * unit, cy - 18f * unit, cx + 6f * unit, cy + 18f * unit, crossPaint)
        canvas.drawRect(cx - 18f * unit, cy - 6f * unit, cx + 18f * unit, cy + 6f * unit, crossPaint)

        // ECG monitor
        val mon = RectF(w * 0.56f, h * 0.20f, w * 0.88f, h * 0.35f)
        canvas.drawRect(mon, darkShadePaint)
        canvas.drawRect(mon, inkPaint)
        val ecg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * unit
        }
        val midY = mon.centerY()
        val blipX = mon.left + ((timeSec * 80f * unit) % mon.width())
        val p = Path().apply {
            moveTo(mon.left + 8f * unit, midY)
            lineTo((blipX - 18f * unit).coerceAtLeast(mon.left + 8f * unit), midY)
            lineTo(blipX, midY - 32f * unit)
            lineTo((blipX + 12f * unit).coerceAtMost(mon.right - 8f * unit), midY + 18f * unit)
            lineTo((blipX + 24f * unit).coerceAtMost(mon.right - 8f * unit), midY)
            lineTo(mon.right - 8f * unit, midY)
        }
        canvas.drawPath(p, ecg)

        // Exam bed
        canvas.drawRect(w * 0.52f, floorY - 76f * unit, w * 0.92f, floorY - 32f * unit, whiteFillPaint)
        canvas.drawRect(w * 0.52f, floorY - 76f * unit, w * 0.92f, floorY - 32f * unit, inkPaint)
        canvas.drawLine(w * 0.56f, floorY - 32f * unit, w * 0.56f, floorY, inkPaint)
        canvas.drawLine(w * 0.88f, floorY - 32f * unit, w * 0.88f, floorY, inkPaint)
    }

    private fun drawInterviewRoom(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float
    ) {
        drawSwingingDoor(canvas, w * 0.05f, floorY - 300f * unit, 120f * unit, 300f * unit, unit, doorOpen)
        val sign = RectF(w * 0.32f, h * 0.20f, w * 0.86f, h * 0.31f)
        canvas.drawRect(sign, whiteFillPaint)
        canvas.drawRect(sign, inkPaint)
        textPaint.textSize = 24f * unit
        canvas.drawText("INTERVIEW ROOM 4", sign.centerX(), sign.centerY() + 8f * unit, textPaint)
    }

    private fun drawCoffeeShop(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float
    ) {
        drawSwingingDoor(canvas, w * 0.04f, floorY - 295f * unit, 118f * unit, 295f * unit, unit, doorOpen)
        val menu = RectF(w * 0.30f, h * 0.16f, w * 0.86f, h * 0.36f)
        canvas.drawRect(menu, darkShadePaint)
        canvas.drawRect(menu, inkPaint)
        val menuTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f * unit
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("ESPRESSO   $9", menu.centerX(), menu.top + 56f * unit, menuTxt)
        canvas.drawText("OAT LATTE  $12", menu.centerX(), menu.top + 104f * unit, menuTxt)

        // Counter on right
        val counter = RectF(w * 0.58f, floorY - 135f * unit, w * 0.94f, floorY)
        canvas.drawRect(counter, shadePaint)
        canvas.drawRect(counter, inkPaint)
    }

    private fun drawGym(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float
    ) {
        // Wall poster
        val poster = RectF(w * 0.22f, h * 0.16f, w * 0.78f, h * 0.28f)
        canvas.drawRect(poster, darkShadePaint)
        canvas.drawRect(poster, inkPaint)
        val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f * unit
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("NO PAIN NO GAIN", poster.centerX(), poster.centerY() + 9f * unit, pTxt)

        // Squat rack
        canvas.drawLine(w * 0.64f, floorY - 270f * unit, w * 0.64f, floorY, inkPaint)
        canvas.drawLine(w * 0.88f, floorY - 270f * unit, w * 0.88f, floorY, inkPaint)
        canvas.drawLine(w * 0.60f, floorY - 190f * unit, w * 0.92f, floorY - 190f * unit, inkPaint)
        canvas.drawCircle(w * 0.60f, floorY - 190f * unit, 22f * unit, darkShadePaint)
        canvas.drawCircle(w * 0.92f, floorY - 190f * unit, 22f * unit, darkShadePaint)
    }

    private fun drawLivingRoom(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float
    ) {
        drawSwingingDoor(canvas, w * 0.04f, floorY - 295f * unit, 118f * unit, 295f * unit, unit, doorOpen)
        // Framed portrait
        val frame = RectF(w * 0.34f, h * 0.19f, w * 0.56f, h * 0.34f)
        canvas.drawRect(frame, whiteFillPaint)
        canvas.drawRect(frame, inkPaint)
        canvas.drawCircle(frame.centerX(), frame.centerY() - 10f * unit, 20f * unit, thinInkPaint)

        // Sofa on right
        val sofa = RectF(w * 0.54f, floorY - 115f * unit, w * 0.94f, floorY)
        canvas.drawRect(sofa, shadePaint)
        canvas.drawRect(sofa, inkPaint)
        canvas.drawRect(sofa.left, sofa.top + 48f * unit, sofa.right, sofa.bottom - 18f * unit, midFillPaint)
    }

    private fun drawKitchen(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        doorOpen: Float,
        timeSec: Float
    ) {
        drawSwingingDoor(canvas, w * 0.04f, floorY - 295f * unit, 114f * unit, 295f * unit, unit, doorOpen)

        // Kitchen shelf + spice jars
        val shelf = RectF(w * 0.28f, h * 0.21f, w * 0.70f, h * 0.23f)
        canvas.drawRect(shelf, darkShadePaint)
        for (i in 0..3) {
            val jx = w * (0.33f + i * 0.09f)
            canvas.drawRect(jx, shelf.top - 34f * unit, jx + 28f * unit, shelf.top, whiteFillPaint)
            canvas.drawRect(jx, shelf.top - 34f * unit, jx + 28f * unit, shelf.top, inkPaint)
        }

        // Fridge on right
        val fridge = RectF(w * 0.74f, floorY - 285f * unit, w * 0.94f, floorY)
        canvas.drawRect(fridge, whiteFillPaint)
        canvas.drawRect(fridge, inkPaint)
        canvas.drawLine(fridge.left, fridge.top + 100f * unit, fridge.right, fridge.top + 100f * unit, inkPaint)
        canvas.drawLine(fridge.left + 16f * unit, fridge.top + 52f * unit, fridge.left + 16f * unit, fridge.top + 82f * unit, inkPaint)
        canvas.drawLine(fridge.left + 16f * unit, fridge.top + 132f * unit, fridge.left + 16f * unit, fridge.top + 182f * unit, inkPaint)

        // Gas stove counter
        val counter = RectF(w * 0.42f, floorY - 122f * unit, w * 0.72f, floorY)
        canvas.drawRect(counter, shadePaint)
        canvas.drawRect(counter, inkPaint)
        // Kettle / pot with gentle steam
        canvas.drawRect(w * 0.50f, counter.top - 36f * unit, w * 0.62f, counter.top, midFillPaint)
        canvas.drawRect(w * 0.50f, counter.top - 36f * unit, w * 0.62f, counter.top, inkPaint)
        val steamShift = sin(timeSec * 5f) * 8f * unit
        canvas.drawLine(w * 0.54f, counter.top - 44f * unit, w * 0.54f + steamShift, counter.top - 78f * unit, thinInkPaint)
        canvas.drawLine(w * 0.58f, counter.top - 44f * unit, w * 0.58f - steamShift, counter.top - 84f * unit, thinInkPaint)
    }

    private fun drawStreetPark(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        timeSec: Float
    ) {
        // Drifting clouds
        val cloudOffset = (timeSec * 14f * unit) % (w + 180f * unit)
        val cx1 = cloudOffset - 90f * unit
        canvas.drawOval(cx1, h * 0.14f, cx1 + 120f * unit, h * 0.19f, whiteFillPaint)
        canvas.drawOval(cx1, h * 0.14f, cx1 + 120f * unit, h * 0.19f, thinInkPaint)
        canvas.drawOval(w * 0.55f, h * 0.21f, w * 0.80f, h * 0.26f, whiteFillPaint)
        canvas.drawOval(w * 0.55f, h * 0.21f, w * 0.80f, h * 0.26f, thinInkPaint)

        // Street lamp on left
        canvas.drawLine(w * 0.12f, floorY, w * 0.12f, h * 0.26f, inkPaint)
        canvas.drawLine(w * 0.12f, h * 0.26f, w * 0.24f, h * 0.26f, inkPaint)
        canvas.drawCircle(w * 0.24f, h * 0.28f, 14f * unit, whiteFillPaint)
        canvas.drawCircle(w * 0.24f, h * 0.28f, 14f * unit, inkPaint)

        // Cricket wickets on right
        for (i in 0..2) {
            val sx = w * (0.78f + i * 0.03f)
            canvas.drawLine(sx, floorY, sx, floorY - 110f * unit, inkPaint)
        }
        canvas.drawLine(w * 0.775f, floorY - 112f * unit, w * 0.845f, floorY - 112f * unit, inkPaint)

        // Dashed road markings
        for (i in 0..3) {
            val rx = w * (0.10f + i * 0.24f)
            canvas.drawRect(rx, floorY + 58f * unit, rx + 70f * unit, floorY + 70f * unit, whiteFillPaint)
        }
    }

    private fun drawGamingRoom(
        canvas: Canvas,
        w: Float,
        h: Float,
        floorY: Float,
        unit: Float,
        timeSec: Float
    ) {
        // Wall poster
        val poster = RectF(w * 0.14f, h * 0.17f, w * 0.38f, h * 0.34f)
        canvas.drawRect(poster, darkShadePaint)
        canvas.drawRect(poster, inkPaint)
        val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f * unit
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("GG WP!", poster.centerX(), poster.centerY() + 7f * unit, pTxt)

        // Gaming desk + monitor on right
        val desk = RectF(w * 0.50f, floorY - 118f * unit, w * 0.92f, floorY)
        canvas.drawRect(desk, shadePaint)
        canvas.drawRect(desk, inkPaint)

        val monitor = RectF(w * 0.56f, desk.top - 96f * unit, w * 0.86f, desk.top - 16f * unit)
        canvas.drawRect(monitor, darkShadePaint)
        canvas.drawRect(monitor, inkPaint)
        canvas.drawLine(monitor.centerX(), monitor.bottom, monitor.centerX(), desk.top, inkPaint)
        val pulse = 18f * unit + sin(timeSec * 8f) * 6f * unit
        val monLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * unit
        }
        canvas.drawCircle(monitor.centerX(), monitor.centerY(), pulse, monLine)
    }
}
