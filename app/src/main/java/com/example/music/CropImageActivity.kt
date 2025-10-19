package com.example.music

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class CropImageActivity : AppCompatActivity() {

    private lateinit var cropView: CropView
    private lateinit var btnCrop: Button
    private lateinit var btnBack: ImageButton
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_image)

        imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("imageUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("imageUri")
        }

        cropView = findViewById(R.id.cropView)
        btnCrop = findViewById(R.id.btnCrop)
        btnBack = findViewById(R.id.btnBackCrop)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        imageUri?.let { uri ->
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
            bitmap?.let { cropView.setImageBitmap(it) }
        }

        btnBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnCrop.setOnClickListener {
            val croppedBitmap = cropView.getCroppedBitmap()
            if (croppedBitmap != null) {
                val tempFile = File(cacheDir, "cropped_cover_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tempFile).use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val resultIntent = Intent()
                resultIntent.data = Uri.fromFile(tempFile)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}

class CropView(context: android.content.Context, attrs: android.util.AttributeSet) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var cropRect = RectF()
    private var imageBounds = RectF()

    private var paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private var overlayPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
        style = Paint.Style.FILL
    }
    private var cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var isDragging = false
    private var isResizing = false
    private var resizeCorner = -1
    private var lastX = 0f
    private var lastY = 0f
    private val cornerSize = 60f
    private val minCropSize = 150f

    fun setImageBitmap(bmp: Bitmap) {
        bitmap = bmp
        post {
            val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
            val scaledWidth = bmp.width * scale
            val scaledHeight = bmp.height * scale
            val left = (width - scaledWidth) / 2
            val top = (height - scaledHeight) / 2
            imageBounds.set(left, top, left + scaledWidth, top + scaledHeight)

            // Максимально большой квадрат по умолчанию
            val maxSize = minOf(scaledWidth, scaledHeight)
            val cropLeft = imageBounds.left + (scaledWidth - maxSize) / 2
            val cropTop = imageBounds.top + (scaledHeight - maxSize) / 2
            cropRect.set(cropLeft, cropTop, cropLeft + maxSize, cropTop + maxSize)

            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, imageBounds, null)

            canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, overlayPaint)
            canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), overlayPaint)
            canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
            canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, overlayPaint)

            canvas.drawRect(cropRect, paint)

            val thirdWidth = cropRect.width() / 3
            val thirdHeight = cropRect.height() / 3

            canvas.drawLine(cropRect.left + thirdWidth, cropRect.top, cropRect.left + thirdWidth, cropRect.bottom, paint)
            canvas.drawLine(cropRect.left + thirdWidth * 2, cropRect.top, cropRect.left + thirdWidth * 2, cropRect.bottom, paint)
            canvas.drawLine(cropRect.left, cropRect.top + thirdHeight, cropRect.right, cropRect.top + thirdHeight, paint)
            canvas.drawLine(cropRect.left, cropRect.top + thirdHeight * 2, cropRect.right, cropRect.top + thirdHeight * 2, paint)

            canvas.drawCircle(cropRect.left, cropRect.top, cornerSize / 2, cornerPaint)
            canvas.drawCircle(cropRect.right, cropRect.top, cornerSize / 2, cornerPaint)
            canvas.drawCircle(cropRect.left, cropRect.bottom, cornerSize / 2, cornerPaint)
            canvas.drawCircle(cropRect.right, cropRect.bottom, cornerSize / 2, cornerPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y

                when {
                    isNearCorner(event.x, event.y, cropRect.left, cropRect.top) -> {
                        isResizing = true
                        resizeCorner = 0
                    }
                    isNearCorner(event.x, event.y, cropRect.right, cropRect.top) -> {
                        isResizing = true
                        resizeCorner = 1
                    }
                    isNearCorner(event.x, event.y, cropRect.left, cropRect.bottom) -> {
                        isResizing = true
                        resizeCorner = 2
                    }
                    isNearCorner(event.x, event.y, cropRect.right, cropRect.bottom) -> {
                        isResizing = true
                        resizeCorner = 3
                    }
                    cropRect.contains(event.x, event.y) -> {
                        isDragging = true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY

                if (isResizing) {
                    // Вычисляем новый размер (квадрат)
                    val currentSize = cropRect.width()
                    val delta = maxOf(Math.abs(dx), Math.abs(dy))

                    var newSize = when (resizeCorner) {
                        0 -> currentSize - delta * Math.signum(dx + dy) // Top-left
                        1 -> currentSize + delta * Math.signum(dx - dy) // Top-right
                        2 -> currentSize + delta * Math.signum(-dx + dy) // Bottom-left
                        3 -> currentSize + delta * Math.signum(dx + dy) // Bottom-right
                        else -> currentSize
                    }

                    newSize = newSize.coerceIn(minCropSize,
                        minOf(imageBounds.width(), imageBounds.height()))

                    // Вычисляем центр текущей области
                    val centerX = cropRect.centerX()
                    val centerY = cropRect.centerY()

                    // Создаём новый квадрат вокруг центра
                    val halfSize = newSize / 2
                    val newRect = RectF(
                        centerX - halfSize,
                        centerY - halfSize,
                        centerX + halfSize,
                        centerY + halfSize
                    )

                    // Проверяем границы изображения
                    if (newRect.left < imageBounds.left) newRect.offset(imageBounds.left - newRect.left, 0f)
                    if (newRect.top < imageBounds.top) newRect.offset(0f, imageBounds.top - newRect.top)
                    if (newRect.right > imageBounds.right) newRect.offset(imageBounds.right - newRect.right, 0f)
                    if (newRect.bottom > imageBounds.bottom) newRect.offset(0f, imageBounds.bottom - newRect.bottom)

                    cropRect.set(newRect)
                    lastX = event.x
                    lastY = event.y
                    invalidate()

                } else if (isDragging) {
                    val newRect = RectF(cropRect)
                    newRect.offset(dx, dy)

                    if (newRect.left < imageBounds.left) newRect.offset(imageBounds.left - newRect.left, 0f)
                    if (newRect.top < imageBounds.top) newRect.offset(0f, imageBounds.top - newRect.top)
                    if (newRect.right > imageBounds.right) newRect.offset(imageBounds.right - newRect.right, 0f)
                    if (newRect.bottom > imageBounds.bottom) newRect.offset(0f, imageBounds.bottom - newRect.bottom)

                    cropRect.set(newRect)
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                isResizing = false
                resizeCorner = -1
            }
        }
        return true
    }

    private fun isNearCorner(x: Float, y: Float, cornerX: Float, cornerY: Float): Boolean {
        val distance = Math.sqrt(((x - cornerX) * (x - cornerX) + (y - cornerY) * (y - cornerY)).toDouble())
        return distance < cornerSize
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = bitmap ?: return null

        val scale = imageBounds.width() / bmp.width

        val cropX = ((cropRect.left - imageBounds.left) / scale).toInt().coerceIn(0, bmp.width)
        val cropY = ((cropRect.top - imageBounds.top) / scale).toInt().coerceIn(0, bmp.height)
        val cropSize = (cropRect.width() / scale).toInt()
            .coerceAtMost(bmp.width - cropX)
            .coerceAtMost(bmp.height - cropY)

        // Всегда возвращаем квадрат
        return Bitmap.createBitmap(bmp, cropX, cropY, cropSize, cropSize)
    }
}