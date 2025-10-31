package com.example.music

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var innerRadius = 0f
    private var ringWidthPx = 0f

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.WHITE
    }
    private val selectorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val triangleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val triangleBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var currentHue = 0f
    private var saturation = 1f
    private var value = 1f

    var onColorSelected: ((Int) -> Unit)? = null

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        // Preserve hue if color has undefined hue (s=0 or v=0), e.g., white/black
        val newHue = hsv[0]
        val newS = hsv[1]
        val newV = hsv[2]
        if (newS > 1e-3f && newV > 1e-3f) {
            currentHue = newHue
        }
        saturation = newS
        value = newV
        createColorWheel()
        buildTriangleBitmap()
        invalidate()
    }

    fun getCurrentColor(): Int {
        return Color.HSVToColor(floatArrayOf(currentHue, saturation, value))
    }

    // Triangle geometry
    private var pHue = PointF()
    private var pWhite = PointF()
    private var pBlack = PointF()
    private var trianglePath = Path()
    private var triangleBitmap: Bitmap? = null
    private var triangleBitmapRect = RectF()
    private enum class ActiveArea { NONE, HUE, TRIANGLE }
    private var activeArea: ActiveArea = ActiveArea.NONE
    // Rebuild throttling for performance when dragging hue
    private var lastHueForBitmap: Float = Float.NaN
    private var lastRebuildTimeMs: Long = 0L
    private val MIN_HUE_DELTA_DEG = 2f
    private val MIN_REBUILD_INTERVAL_MS = 16L
    // Additional perf: render triangle at lower resolution and scale up when drawing
    private val TRIANGLE_BITMAP_SCALE = 0.65f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) / 2f - 12f
        // Target ring width ~20% of radius (approximately double from previous ~10%)
        ringWidthPx = (radius * 0.20f).coerceAtLeast(12f)
        innerRadius = (radius - ringWidthPx).coerceAtLeast(0f)

        createColorWheel()
        updateTriangleGeometry()
        buildTriangleBitmap()
        lastHueForBitmap = currentHue
        lastRebuildTimeMs = android.os.SystemClock.uptimeMillis()
    }

    private fun createColorWheel() {
        val colors = IntArray(361)
        for (i in 0..360) {
            colors[i] = Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
        }
        val sweep = SweepGradient(centerX, centerY, colors, null)
        wheelPaint.strokeWidth = ringWidthPx
        wheelPaint.shader = sweep
    }

    private fun updateTriangleGeometry() {
        // Centered equilateral triangle inscribed with a tiny safety gap from inner ring
        val r = innerRadius * 0.985f
        // Vertices at -90° (top), 30°, 150° relative to center
        val angHue = Math.toRadians(-90.0)
        val angWhite = Math.toRadians(30.0)
        val angBlack = Math.toRadians(150.0)

        pHue.set(centerX + (cos(angHue).toFloat() * r), centerY + (sin(angHue).toFloat() * r))
        pWhite.set(centerX + (cos(angWhite).toFloat() * r), centerY + (sin(angWhite).toFloat() * r))
        pBlack.set(centerX + (cos(angBlack).toFloat() * r), centerY + (sin(angBlack).toFloat() * r))

        trianglePath.reset()
        trianglePath.moveTo(pHue.x, pHue.y)
        trianglePath.lineTo(pWhite.x, pWhite.y)
        trianglePath.lineTo(pBlack.x, pBlack.y)
        trianglePath.close()

        // Bitmap bounds (tight around triangle)
        val left = min(pHue.x, min(pWhite.x, pBlack.x))
        val right = maxOf(pHue.x, pWhite.x, pBlack.x)
        val top = min(pHue.y, min(pWhite.y, pBlack.y))
        val bottom = maxOf(pHue.y, pWhite.y, pBlack.y)
        triangleBitmapRect.set(left, top, right, bottom)
    }

    private fun buildTriangleBitmap() {
        if (triangleBitmapRect.width() <= 0 || triangleBitmapRect.height() <= 0) return
        val dstWidth = triangleBitmapRect.width().toInt().coerceAtLeast(1)
        val dstHeight = triangleBitmapRect.height().toInt().coerceAtLeast(1)
        val width = (dstWidth * TRIANGLE_BITMAP_SCALE).toInt().coerceAtLeast(1)
        val height = (dstHeight * TRIANGLE_BITMAP_SCALE).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val cx = triangleBitmapRect.left
        val cy = triangleBitmapRect.top

        // Colors at vertices
        val hueColor = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
        val hueR = Color.red(hueColor).toFloat()
        val hueG = Color.green(hueColor).toFloat()
        val hueB = Color.blue(hueColor).toFloat()

        // Precompute barycentric helpers
        val ax = pHue.x - cx
        val ay = pHue.y - cy
        val bx = pWhite.x - cx
        val by = pWhite.y - cy
        val cxp = pBlack.x - cx
        val cyp = pBlack.y - cy
        val denom = ((by - cyp) * (ax - cxp) + (cxp - bx) * (ay - cyp))
        if (denom == 0f) {
            triangleBitmap = bmp
            return
        }

        // Iterate pixels and shade into array for faster blit
        val pixels = IntArray(width * height)
        // Precompute mapping from small-bitmap coords to original triangle local space
        val scaleX = triangleBitmapRect.width() / width
        val scaleY = triangleBitmapRect.height() / height

        val whiteR = 255f; val whiteG = 255f; val whiteB = 255f
        val blackR = 0f; val blackG = 0f; val blackB = 0f

        var idx = 0
        for (y in 0 until height) {
            val py = (y * scaleY)
            for (x in 0 until width) {
                val px = (x * scaleX)
                if (!pointInTriangle(px, py, ax, ay, bx, by, cxp, cyp)) {
                    pixels[idx++] = 0x00000000
                    continue
                }
                // Barycentric coordinates relative to triangle ABC (A=hue, B=white, C=black)
                val u = ((by - cyp) * (px - cxp) + (cxp - bx) * (py - cyp)) / denom
                val v = ((cyp - ay) * (px - cxp) + (ax - cxp) * (py - cyp)) / denom
                val w = 1f - u - v
                val r = (u * hueR + v * whiteR + w * blackR).toInt().coerceIn(0, 255)
                val g = (u * hueG + v * whiteG + w * blackG).toInt().coerceIn(0, 255)
                val b = (u * hueB + v * whiteB + w * blackB).toInt().coerceIn(0, 255)
                pixels[idx++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        triangleBitmap = bmp
    }

    private fun pointInTriangle(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean {
        fun sign(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
            return (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3)
        }
        val d1 = sign(px, py, ax, ay, bx, by)
        val d2 = sign(px, py, bx, by, cx, cy)
        val d3 = sign(px, py, cx, cy, ax, ay)
        val hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0)
        val hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0)
        return !(hasNeg && hasPos)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw hue ring (donut)
        canvas.drawCircle(centerX, centerY, (radius + innerRadius) / 2f, wheelPaint)

        // Draw triangle bitmap (SV space)
        triangleBitmap?.let { bmp ->
            // Scale from low-res bitmap to destination triangle rect
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = RectF(triangleBitmapRect)
            canvas.drawBitmap(bmp, src, dst, triangleBitmapPaint)
        }
        // Triangle border for visibility
        canvas.drawPath(trianglePath, triangleBorderPaint)

        val angle = Math.toRadians(currentHue.toDouble())
        val selectorRadius = (radius + innerRadius) / 2f
        val selectorX = centerX + cos(angle).toFloat() * selectorRadius
        val selectorY = centerY + sin(angle).toFloat() * selectorRadius

        // Hue marker shows pure hue color (independent of SV)
        selectorFillPaint.color = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
        canvas.drawCircle(selectorX, selectorY, 22f, selectorFillPaint)
        canvas.drawCircle(selectorX, selectorY, 22f, selectorPaint)

        // Draw SV selector point inside triangle
        val svPoint = svToPoint(saturation, value)
        selectorFillPaint.color = getCurrentColor()
        canvas.drawCircle(svPoint.x, svPoint.y, 14f, selectorFillPaint)
        canvas.drawCircle(svPoint.x, svPoint.y, 14f, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                activeArea = when {
                    // Prefer hue if finger down on ring
                    distance >= innerRadius && distance <= radius -> ActiveArea.HUE
                    // Else if inside triangle, choose triangle
                    pointInTriangle(event.x, event.y, pHue.x, pHue.y, pWhite.x, pWhite.y, pBlack.x, pBlack.y) -> ActiveArea.TRIANGLE
                    else -> {
                        // Fallback: choose nearest of ring vs triangle by proximity
                        val distToRing = kotlin.math.abs(distance - (innerRadius + ringWidthPx / 2f))
                        val insideTri = projectToTriangle(event.x, event.y)
                        val triPoint = svToPoint(insideTri.first, insideTri.second)
                        val distToTri = sqrt((event.x - triPoint.x)*(event.x - triPoint.x) + (event.y - triPoint.y)*(event.y - triPoint.y))
                        if (distToRing <= distToTri) ActiveArea.HUE else ActiveArea.TRIANGLE
                    }
                }
                // Update immediately on down
                handleDrag(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeArea != ActiveArea.NONE) {
                    handleDrag(event.x, event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeArea != ActiveArea.NONE) {
                    handleDrag(event.x, event.y)
                    activeArea = ActiveArea.NONE
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun notifyColorChange() {
        onColorSelected?.invoke(getCurrentColor())
    }

    private fun barycentric(px: Float, py: Float, a: PointF, b: PointF, c: PointF): Pair<Float, Float> {
        val v0x = b.x - a.x; val v0y = b.y - a.y
        val v1x = c.x - a.x; val v1y = c.y - a.y
        val v2x = px - a.x; val v2y = py - a.y
        val den = v0x * v1y - v1x * v0y
        if (den == 0f) return 0f to 0f
        val v = (v2x * v1y - v1x * v2y) / den
        val w = (v0x * v2y - v2x * v0y) / den
        val u = 1f - v - w
        return u to v
    }

    private fun svToPoint(s: Float, v: Float): PointF {
        // Inverse mapping from HSV to triangle point using barycentric weights
        val u = (s * v)
        val vv = ((1f - s) * v)
        val w = 1f - v
        val x = u * pHue.x + vv * pWhite.x + w * pBlack.x
        val y = u * pHue.y + vv * pWhite.y + w * pBlack.y
        return PointF(x, y)
    }

    private fun handleDrag(x: Float, y: Float) {
        when (activeArea) {
            ActiveArea.HUE -> {
                val dx = x - centerX
                val dy = y - centerY
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle < 0) angle += 360f
                currentHue = angle
                // Throttle triangle rebuilds to reduce lag during drag
                val needRebuild = lastHueForBitmap.isNaN() ||
                        kotlin.math.abs(currentHue - lastHueForBitmap) >= MIN_HUE_DELTA_DEG ||
                        (android.os.SystemClock.uptimeMillis() - lastRebuildTimeMs) >= MIN_REBUILD_INTERVAL_MS
                if (needRebuild) {
                    buildTriangleBitmap()
                    lastHueForBitmap = currentHue
                    lastRebuildTimeMs = android.os.SystemClock.uptimeMillis()
                }
                postInvalidateOnAnimation()
                notifyColorChange()
            }
            ActiveArea.TRIANGLE -> {
                val (s, v) = projectToTriangle(x, y)
                saturation = s
                value = v
                postInvalidateOnAnimation()
                notifyColorChange()
            }
            else -> {}
        }
    }

    // Project arbitrary point to the closest point inside triangle in SV space, returning (s, v)
    private fun projectToTriangle(px: Float, py: Float): Pair<Float, Float> {
        // Compute unclamped barycentric for point P relative to triangle A(pHue), B(pWhite), C(pBlack)
        val uvm = barycentric(px, py, pHue, pWhite, pBlack)
        var u = uvm.first
        var v = uvm.second
        var w = 1f - u - v
        // Clamp to triangle by making all weights >= 0 and renormalizing
        if (u < 0f) u = 0f
        if (v < 0f) v = 0f
        if (w < 0f) w = 0f
        val sum = u + v + w
        if (sum > 0f) { u /= sum; v /= sum; w /= sum } else { u = 0f; v = 0f; w = 1f }
        // Map barycentric to HSV: value = 1 - w; saturation = u / (u + v) (when value > 0)
        val newV = (1f - w).coerceIn(0f, 1f)
        val denom = (u + v)
        val newS = if (denom > 1e-4f) (u / denom).coerceIn(0f, 1f) else 0f
        return newS to newV
    }
}