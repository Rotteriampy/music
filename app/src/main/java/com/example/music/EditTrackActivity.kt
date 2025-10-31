package com.example.music

import android.graphics.Color
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
import android.app.PendingIntent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.view.WindowManager
import androidx.activity.result.PickVisualMediaRequest
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.delay
import android.view.View
import android.view.ViewGroup

data class BatchUpdateParams(
    val searchBy: String,
    val searchValue: String,
    val applyField: String,
    val applyValue: String
)
class EditTrackActivity : AppCompatActivity() {

    private lateinit var trackPath: String
    private lateinit var editTrackName: EditText
    private lateinit var editTrackArtist: EditText
    private lateinit var editTrackAlbum: EditText
    private lateinit var editTrackGenre: EditText
    private lateinit var btnBack: ImageButton
    private lateinit var coverImageView: ImageView
    private lateinit var mainLayout: LinearLayout
    private lateinit var btnSaveTags: Button

    // Новые элементы для улучшенного UI
    private lateinit var previewCover: ImageView
    private lateinit var previewTitle: TextView
    private lateinit var previewArtist: TextView
    private lateinit var previewAlbum: TextView
    private lateinit var btnReset: Button
    private lateinit var btnApplyToAll: Button
    private val FIELD_ARTIST = "artist"
    private val FIELD_ALBUM = "album"
    private val FIELD_GENRE = "genre"
    private val FIELD_TITLE = "title"
    private var currentBatchUpdate: BatchUpdateParams? = null
    private var hasRequestedFolderPermission = false

    private var selectedCoverUri: Uri? = null
    private val WRITE_PERMISSION_CODE = 100

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            // Запускаем активность обрезки
            val intent = Intent(this, CropImageActivity::class.java)
            intent.putExtra("imageUri", it)
            cropLauncher.launch(intent)
        }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedCoverUri = uri
                // Загружаем и скругляем выбранную обложку
                loadAndRoundImage(uri, coverImageView, 16f)
                loadAndRoundImage(uri, previewCover, 8f)
            }
        }
    }

    private val batchPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Разрешение получено, продолжаем массовое обновление
            currentBatchUpdate?.let { params ->
                processBatchUpdate(params.searchBy, params.searchValue, params.applyField, params.applyValue)
            }
        } else {
            Toast.makeText(this, "Отказано в разрешении для массового обновления", Toast.LENGTH_SHORT).show()
        }
    }

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Разрешение получено, пробуем снова
            saveMetadataToFile()
        } else {
            Toast.makeText(this, "Отказано в разрешении", Toast.LENGTH_SHORT).show()
            setSaveButtonEnabled(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_track)

        // Устанавливаем градиентный фон
        setupGradientBackground()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        trackPath = intent.getStringExtra("TRACK_PATH") ?: run {
            Toast.makeText(this, "Ошибка: путь к файлу не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupClickListeners()
        setupAnimations()
        setupValidation()
        setupPreviewUpdates()

        // Устанавливаем прозрачный статус-бар как в MainActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        loadCurrentMetadata()
    }

    private fun initViews() {
        editTrackName = findViewById(R.id.editTrackName)
        editTrackArtist = findViewById(R.id.editTrackArtist)
        editTrackAlbum = findViewById(R.id.editTrackAlbum)
        editTrackGenre = findViewById(R.id.editTrackGenre)
        btnBack = findViewById(R.id.btnBackEdit)
        coverImageView = findViewById(R.id.coverImageView)
        mainLayout = findViewById(R.id.mainLayout)
        btnSaveTags = findViewById(R.id.btnSaveTags)

        // Новые элементы
        previewCover = findViewById(R.id.previewCover)
        previewTitle = findViewById(R.id.previewTitle)
        previewArtist = findViewById(R.id.previewArtist)
        previewAlbum = findViewById(R.id.previewAlbum)
        btnReset = findViewById(R.id.btnReset)
        btnApplyToAll = findViewById(R.id.btnApplyToAll)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        // Обработчик нажатия на обложку
        coverImageView.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Обработчик нажатия на иконку редактирования
        val editCoverIcon = findViewById<ImageView>(R.id.editCoverIcon)
        editCoverIcon.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnSaveTags.setOnClickListener {
            saveMetadataToFile()
        }

        btnReset.setOnClickListener {
            resetToOriginal()
        }

        btnApplyToAll.setOnClickListener {
            showApplyToAllDialog()
        }
    }

    private fun setupGradientBackground() {
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)

        val gd = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(gradStart, gradEnd)
        )

        // Находим корневой layout и устанавливаем градиент
        val rootLayout = findViewById<LinearLayout>(R.id.mainLayout)
        rootLayout?.background = gd
    }

    private fun setupAnimations() {
        val views = listOf(
            coverImageView,
            previewCover,
            editTrackName,
            editTrackArtist,
            editTrackAlbum,
            editTrackGenre
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(100L * index)
                .setDuration(300L)
                .start()
        }
    }

    private fun setupValidation() {
        editTrackName.doAfterTextChanged { text ->
            val isValid = text?.isNotBlank() == true
            updatePreview()
        }

        editTrackArtist.doAfterTextChanged {
            updatePreview()
        }

        editTrackAlbum.doAfterTextChanged {
            updatePreview()
        }

        editTrackGenre.doAfterTextChanged {
            updatePreview()
        }
    }

    private fun setupPreviewUpdates() {
        // Предпросмотр будет обновляться автоматически через setupValidation
    }

    private fun updatePreview() {
        previewTitle.text = editTrackName.text.ifEmpty { "Название трека" }
        previewArtist.text = editTrackArtist.text.ifEmpty { "Исполнитель" }
        previewAlbum.text = editTrackAlbum.text.ifEmpty { "Альбом" }
    }

    private fun loadCurrentMetadata() {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(trackPath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)

            val finalTitle = title ?: File(trackPath).nameWithoutExtension
            val finalArtist = artist ?: "Unknown Artist"
            val finalAlbum = album ?: "Unknown Album"
            val finalGenre = genre ?: "Unknown Genre"

            editTrackName.setText(finalTitle)
            editTrackArtist.setText(finalArtist)
            editTrackAlbum.setText(finalAlbum)
            editTrackGenre.setText(finalGenre)

            // Обновляем предпросмотр
            previewTitle.text = finalTitle
            previewArtist.text = finalArtist
            previewAlbum.text = finalAlbum

            // Загружаем текущую обложку с скруглением
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                setRoundedImage(coverImageView, bitmap, 16f) // Большая обложка - радиус 16dp
                setRoundedImage(previewCover, bitmap, 8f)   // Маленькая обложка - радиус 8dp
            } else {
                // Для placeholder также создаем скругленные версии
                val placeholderLarge = createRoundedPlaceholder(140, 140, 16f)
                val placeholderSmall = createRoundedPlaceholder(48, 48, 8f)
                coverImageView.setImageBitmap(placeholderLarge)
                previewCover.setImageBitmap(placeholderSmall)
            }

            retriever.release()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки метаданных", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun loadAndRoundImage(uri: Uri, imageView: ImageView, cornerRadius: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                withContext(Dispatchers.Main) {
                    setRoundedImage(imageView, bitmap, cornerRadius)
                }
            } catch (e: Exception) {
                Log.e("EditTrackActivity", "Error loading image from URI", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTrackActivity, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setRoundedImage(imageView: ImageView, bitmap: Bitmap, cornerRadiusDp: Float) {
        val cornerRadiusPx = cornerRadiusDp * resources.displayMetrics.density

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint()
        paint.isAntiAlias = true

        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        // Рисуем скругленный прямоугольник
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)

        // Устанавливаем режим для обрезки
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        imageView.setImageBitmap(output)
    }

    private fun createRoundedPlaceholder(width: Int, height: Int, cornerRadiusDp: Float): Bitmap {
        val cornerRadiusPx = cornerRadiusDp * resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#333333")

        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)

        return bitmap
    }

    private fun resetToOriginal() {
        loadCurrentMetadata()
        selectedCoverUri = null
        Toast.makeText(this, "Изменения сброшены", Toast.LENGTH_SHORT).show()
    }

    private fun showApplyToAllDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_apply_to_all, null)

        val spinnerSearchBy = dialogView.findViewById<Spinner>(R.id.spinnerSearchBy)
        val editSearchValue = dialogView.findViewById<EditText>(R.id.editSearchValue)
        val spinnerApplyField = dialogView.findViewById<Spinner>(R.id.spinnerApplyField)
        val editApplyValue = dialogView.findViewById<EditText>(R.id.editApplyValue)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnApply = dialogView.findViewById<Button>(R.id.btnApply)

        // Настраиваем спиннеры с кастомным адаптером
        val fields = arrayOf("Исполнитель", "Альбом", "Жанр", "Название")
        val fieldKeys = arrayOf(FIELD_ARTIST, FIELD_ALBUM, FIELD_GENRE, FIELD_TITLE)

        // Кастомный адаптер для спиннеров
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, fields) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setBackgroundColor(Color.parseColor("#2D2D2D"))
                return view
            }
        }

        spinnerSearchBy.adapter = adapter
        spinnerApplyField.adapter = adapter

        // Автозаполнение значений из текущего трека
        autoFillDialogValues(spinnerSearchBy, editSearchValue, spinnerApplyField, editApplyValue)

        // Создаем диалог
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Настраиваем прозрачный фон для скругленных углов
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.7f) // Затемнение фона

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            val searchBy = fieldKeys[spinnerSearchBy.selectedItemPosition]
            val searchValue = editSearchValue.text.toString().trim()
            val applyField = fieldKeys[spinnerApplyField.selectedItemPosition]
            val applyValue = editApplyValue.text.toString().trim()

            if (searchValue.isEmpty()) {
                editSearchValue.error = "Введите значение для поиска"
                return@setOnClickListener
            }

            if (applyValue.isEmpty()) {
                editApplyValue.error = "Введите значение для применения"
                return@setOnClickListener
            }

            if (searchBy == applyField && searchValue == applyValue) {
                Toast.makeText(this, "Значения поиска и применения одинаковы", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            applyToAllTracks(searchBy, searchValue, applyField, applyValue)
        }

        dialog.show()
    }
    // Заменим метод updateSingleTrack для массового обновления
    private suspend fun updateSingleTrack(trackPath: String, field: String, value: String): Boolean {
        return try {
            Log.d("BatchUpdate", "=== Начало обновления трека ===")
            Log.d("BatchUpdate", "Трек: ${File(trackPath).name}")
            Log.d("BatchUpdate", "Устанавливаем поле '$field' в значение '$value'")

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(trackPath)

            val currentTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: File(trackPath).nameWithoutExtension
            val currentArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val currentAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val currentGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""

            retriever.release()

            Log.d("BatchUpdate", "Текущие метаданные:")
            Log.d("BatchUpdate", "  Название: '$currentTitle'")
            Log.d("BatchUpdate", "  Исполнитель: '$currentArtist'")
            Log.d("BatchUpdate", "  Альбом: '$currentAlbum'")
            Log.d("BatchUpdate", "  Жанр: '$currentGenre'")

            // Обновляем только указанное поле
            val newTitle = if (field == FIELD_TITLE) value else currentTitle
            val newArtist = if (field == FIELD_ARTIST) value else currentArtist
            val newAlbum = if (field == FIELD_ALBUM) value else currentAlbum
            val newGenre = if (field == FIELD_GENRE) value else currentGenre

            Log.d("BatchUpdate", "Новые метаданные:")
            Log.d("BatchUpdate", "  Название: '$newTitle'")
            Log.d("BatchUpdate", "  Исполнитель: '$newArtist'")
            Log.d("BatchUpdate", "  Альбом: '$newAlbum'")
            Log.d("BatchUpdate", "  Жанр: '$newGenre'")

            // Используем метод без прямого доступа для массового обновления
            val success = ID3TagEditor.updateTagsWithoutDirectAccess(
                trackPath,
                newTitle,
                newArtist,
                newAlbum,
                newGenre,
                null, // Не меняем обложку при массовом обновлении
                this@EditTrackActivity
            )

            Log.d("BatchUpdate", "Результат обновления: $success")
            Log.d("BatchUpdate", "=== Конец обновления трека ===\n")
            success

        } catch (securityException: SecurityException) {
            Log.e("BatchUpdate", "SecurityException for track: $trackPath", securityException)

            // Обрабатываем RecoverableSecurityException для Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    // Используем рефлексию для совместимости
                    val userAction = securityException.javaClass.getMethod("getUserAction").invoke(securityException)
                    val actionIntent = userAction?.javaClass?.getMethod("getActionIntent")?.invoke(userAction) as? PendingIntent

                    if (actionIntent != null) {
                        withContext(Dispatchers.Main) {
                            try {
                                // Сохраняем параметры текущего обновления
                                currentBatchUpdate = BatchUpdateParams(
                                    currentBatchUpdate?.searchBy ?: FIELD_ARTIST,
                                    currentBatchUpdate?.searchValue ?: "",
                                    field,
                                    value
                                )

                                val intentSender = actionIntent.intentSender
                                val request = IntentSenderRequest.Builder(intentSender).build()
                                batchPermissionLauncher.launch(request)
                            } catch (e: Exception) {
                                Log.e("BatchUpdate", "Error launching permission request", e)
                            }
                        }
                        return false
                    }
                } catch (e: Exception) {
                    Log.e("BatchUpdate", "Error getting RecoverableSecurityException", e)
                }
            }

            false
        } catch (e: Exception) {
            Log.e("BatchUpdate", "Error updating track: $trackPath", e)
            false
        }
    }
    // Новый метод для автозаполнения значений в диалоге
    private fun autoFillDialogValues(
        spinnerSearchBy: Spinner,
        editSearchValue: EditText,
        spinnerApplyField: Spinner,
        editApplyValue: EditText
    ) {
        // Устанавливаем текущие значения из полей редактирования
        val currentArtist = editTrackArtist.text.toString()
        val currentAlbum = editTrackAlbum.text.toString()
        val currentGenre = editTrackGenre.text.toString()
        val currentTitle = editTrackName.text.toString()

        // Автозаполняем поле поиска исполнителем
        if (currentArtist.isNotEmpty()) {
            editSearchValue.setText(currentArtist)

            // Устанавливаем исполнителя как поле для поиска по умолчанию
            val fields = arrayOf(FIELD_ARTIST, FIELD_ALBUM, FIELD_GENRE, FIELD_TITLE)
            val artistIndex = fields.indexOf(FIELD_ARTIST)
            if (artistIndex != -1) {
                spinnerSearchBy.setSelection(artistIndex)
            }
        }

        // Автозаполняем поле применения жанром
        if (currentGenre.isNotEmpty()) {
            editApplyValue.setText(currentGenre)
        }

        // Устанавливаем жанр как поле для применения по умолчанию
        val fields = arrayOf(FIELD_ARTIST, FIELD_ALBUM, FIELD_GENRE, FIELD_TITLE)
        val genreIndex = fields.indexOf(FIELD_GENRE)
        if (genreIndex != -1) {
            spinnerApplyField.setSelection(genreIndex)
        }
    }

    private fun applyToAllTracks(searchBy: String, searchValue: String, applyField: String, applyValue: String) {
        if (!checkPermissionsForBatchUpdate()) {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("BatchUpdate", "Starting enhanced batch update...")

                // Используем улучшенный поиск
                val allTracks = findTracksByScanning(searchBy, searchValue)

                Log.d("BatchUpdate", "Total unique tracks found: ${allTracks.size}")

                // Логируем найденные треки
                allTracks.forEachIndexed { index, track ->
                    Log.d("BatchUpdate", "Track ${index + 1}: ${File(track).name} -> $track")
                }

                if (allTracks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showNoTracksFoundDialog(searchBy, searchValue)
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    showConfirmationDialog(allTracks.size, searchBy, searchValue, applyField, applyValue)
                }

            } catch (e: Exception) {
                Log.e("BatchUpdate", "Error in applyToAllTracks", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTrackActivity, "Ошибка поиска треков: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showNoTracksFoundDialog(searchBy: String, searchValue: String) {
        val fieldNames = mapOf(
            FIELD_ARTIST to "исполнителю",
            FIELD_ALBUM to "альбому",
            FIELD_GENRE to "жанру",
            FIELD_TITLE to "названию"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Треки не найдены")
            .setMessage(
                "По ${fieldNames[searchBy]} '$searchValue' треки не найдены.\n\n" +
                        "Возможные причины:\n" +
                        "• Треки находятся в защищенной папке\n" +
                        "• MediaStore не обновлен\n" +
                        "• Неправильная кодировка метаданных\n" +
                        "• Треки защищены DRM\n\n" +
                        "Попробуйте:\n" +
                        "1. Перезагрузить устройство\n" +
                        "2. Использовать другое приложение для сканирования\n" +
                        "3. Проверить метаданные в другом плеере"
            )
            .setPositiveButton("ОК", null)
            .setNeutralButton("Показать все треки") { _, _ ->
                showAllTracks()
            }
            .create()

        dialog.show()
    }

    private fun showAllTracks() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ALBUM
                )

                val allTracks = mutableListOf<String>()

                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    null
                )?.use { cursor ->
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataColumn)
                        val artist = cursor.getString(artistColumn) ?: ""
                        val title = cursor.getString(titleColumn) ?: ""

                        if (File(path).exists()) {
                            allTracks.add("$artist - $title -> $path")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    val message = "Всего треков: ${allTracks.size}\n\n" +
                            allTracks.take(10).joinToString("\n") +
                            if (allTracks.size > 10) "\n\n... и еще ${allTracks.size - 10} треков" else ""

                    androidx.appcompat.app.AlertDialog.Builder(this@EditTrackActivity)
                        .setTitle("Все треки в MediaStore")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }

            } catch (e: Exception) {
                Log.e("BatchUpdate", "Error showing all tracks", e)
            }
        }
    }

    private fun showConfirmationDialog(
        trackCount: Int,
        searchBy: String,
        searchValue: String,
        applyField: String,
        applyValue: String
    ) {
        val fieldNames = mapOf(
            FIELD_ARTIST to "исполнителю",
            FIELD_ALBUM to "альбому",
            FIELD_GENRE to "жанру",
            FIELD_TITLE to "названию"
        )

        val applyFieldNames = mapOf(
            FIELD_ARTIST to "исполнителя",
            FIELD_ALBUM to "альбом",
            FIELD_GENRE to "жанр",
            FIELD_TITLE to "название"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Подтверждение массового обновления")
            .setMessage(
                "Найдено треков: $trackCount\n\n" +
                        "Критерий: ${fieldNames[searchBy]} = '$searchValue'\n" +
                        "Применить: ${applyFieldNames[applyField]} = '$applyValue'\n\n" +
                        "Продолжить?"
            )
            .setPositiveButton("Применить") { _, _ ->
                // Сохраняем параметры в переменную класса, чтобы не потерять
                this.currentBatchUpdate = BatchUpdateParams(searchBy, searchValue, applyField, applyValue)
                processBatchUpdate(searchBy, searchValue, applyField, applyValue)
            }
            .setNegativeButton("Отмена") { _, _ ->
                currentBatchUpdate = null
            }
            .setNeutralButton("Показать треки") { _, _ ->
                showFoundTracks(searchBy, searchValue)
            }
            .create()

        dialog.show()
    }
    private fun showFoundTracks(searchBy: String, searchValue: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val foundTracks = findTracksByScanning(searchBy, searchValue)

                withContext(Dispatchers.Main) {
                    val tracksInfo = foundTracks.take(20).mapIndexed { index, path ->
                        "${index + 1}. ${File(path).name}\n   $path"
                    }.joinToString("\n\n")

                    val message = if (foundTracks.size > 20) {
                        "Показано первые 20 из ${foundTracks.size} треков:\n\n$tracksInfo\n\n... и еще ${foundTracks.size - 20} треков"
                    } else {
                        "Найдено ${foundTracks.size} треков:\n\n$tracksInfo"
                    }

                    androidx.appcompat.app.AlertDialog.Builder(this@EditTrackActivity)
                        .setTitle("Найденные треки")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ ->
                            // После показа треков снова показываем диалог подтверждения
                            currentBatchUpdate?.let { params ->
                                showConfirmationDialog(
                                    foundTracks.size,
                                    params.searchBy,
                                    params.searchValue,
                                    params.applyField,
                                    params.applyValue
                                )
                            }
                        }
                        .show()
                }

            } catch (e: Exception) {
                Log.e("BatchUpdate", "Error showing found tracks", e)
            }
        }
    }
    private fun checkPermissionsForBatchUpdate(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    WRITE_PERMISSION_CODE
                )
                return false
            }
        }
        return true
    }

    private fun processBatchUpdate(
        searchBy: String,
        searchValue: String,
        applyField: String,
        applyValue: String
    ) {
        // Сохраняем параметры для возможного повторного запуска
        currentBatchUpdate = BatchUpdateParams(searchBy, searchValue, applyField, applyValue)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("BatchUpdate", "Starting batch update process...")

                // Получаем треки заново с подробным логированием
                val allTracks = findTracksByScanning(searchBy, searchValue)

                // Детальное логирование найденных треков
                Log.d("BatchUpdate", "=== FOUND TRACKS ===")
                allTracks.forEachIndexed { index, track ->
                    Log.d("BatchUpdate", "Track ${index + 1}: ${File(track).name} -> $track")
                    Log.d("BatchUpdate", "  File exists: ${File(track).exists()}")
                    Log.d("BatchUpdate", "  File readable: ${File(track).canRead()}")
                }
                Log.d("BatchUpdate", "=== END FOUND TRACKS ===")

                Log.d("BatchUpdate", "Tracks to update: ${allTracks.size}")

                // ПРОВЕРКА: если треки найдены, но список пустой в следующем шаге - проблема здесь
                if (allTracks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@EditTrackActivity,
                            "Найдено 0 треков для обновления",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                var successCount = 0
                var errorCount = 0

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@EditTrackActivity,
                        "Начинаем обновление ${allTracks.size} треков...",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Обновляем треки по одному с обработкой исключений
                for ((index, trackPath) in allTracks.withIndex()) {
                    Log.d("BatchUpdate", "Processing track $index: ${File(trackPath).name}")
                    Log.d("BatchUpdate", "Track path: $trackPath")

                    try {
                        // Проверяем доступность файла перед обновлением
                        if (!File(trackPath).exists()) {
                            Log.e("BatchUpdate", "File does not exist: $trackPath")
                            errorCount++
                            continue
                        }

                        if (!File(trackPath).canRead()) {
                            Log.e("BatchUpdate", "File not readable: $trackPath")
                            errorCount++
                            continue
                        }

                        val success = updateSingleTrack(trackPath, applyField, applyValue)
                        if (success) {
                            successCount++
                            Log.d("BatchUpdate", "Successfully updated track $index: ${File(trackPath).name}")

                            // Периодически обновляем статус
                            if (successCount % 5 == 0) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@EditTrackActivity,
                                        "Обновлено $successCount из ${allTracks.size} треков",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            errorCount++
                            Log.e("BatchUpdate", "Failed to update track $index: ${File(trackPath).name}")
                        }

                        // Небольшая задержка между обновлениями
                        delay(50)

                    } catch (e: Exception) {
                        errorCount++
                        Log.e("BatchUpdate", "Exception updating track $index: $trackPath", e)
                    }
                }

                // Завершаем процесс
                completeBatchUpdate(successCount, errorCount, allTracks)

            } catch (e: Exception) {
                Log.e("BatchUpdate", "Error in batch update process", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@EditTrackActivity,
                        "Ошибка массового обновления: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    currentBatchUpdate = null
                }
            }
        }
    }

    // Вынесем завершение обновления в отдельный метод
    private suspend fun completeBatchUpdate(successCount: Int, errorCount: Int, allTracks: List<String>) {
        Log.d("BatchUpdate", "Batch update completed: Success=$successCount, Errors=$errorCount")

        // Сканируем обновленные файлы
        if (successCount > 0) {
            MediaScannerConnection.scanFile(
                this@EditTrackActivity,
                allTracks.toTypedArray(),
                null
            ) { path, uri ->
                Log.d("BatchUpdate", "MediaScanner scanned: $path")
            }
        }

        // Отправляем broadcast об обновлении
        withContext(Dispatchers.Main) {
            val intent = Intent("com.example.music.TAGS_UPDATED")
            sendBroadcast(intent)

            val message = if (successCount > 0) {
                "Обновление завершено!\nУспешно: $successCount\nОшибок: $errorCount"
            } else {
                "Не удалось обновить ни одного трека.\n\nВозможные причины:\n• Нет разрешения на запись\n• Файлы защищены\n• Ошибка доступа"
            }

            Toast.makeText(
                this@EditTrackActivity,
                message,
                Toast.LENGTH_LONG
            ).show()

            Log.d("BatchUpdate", "Final result: $message")
            currentBatchUpdate = null
        }
    }
    private fun findTracksByCriteria(field: String, value: String): List<String> {
        val matchingTracks = mutableListOf<String>()

        try {
            Log.d("BatchUpdate", "Searching for tracks where $field = '$value'")

            val projection = arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media._ID
            )

            // Используем LIKE для более гибкого поиска
            val selection = when (field) {
                FIELD_ARTIST -> "${MediaStore.Audio.Media.ARTIST} LIKE ?"
                FIELD_ALBUM -> "${MediaStore.Audio.Media.ALBUM} LIKE ?"
                FIELD_GENRE -> "${MediaStore.Audio.Media.GENRE} LIKE ?"
                FIELD_TITLE -> "${MediaStore.Audio.Media.TITLE} LIKE ?"
                else -> return emptyList()
            }

            val selectionArgs = arrayOf("%$value%")

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                Log.d("BatchUpdate", "Cursor count: ${cursor.count}")

                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val genreColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                while (cursor.moveToNext()) {
                    val trackPath = cursor.getString(dataColumn)
                    val artist = cursor.getString(artistColumn)
                    val album = cursor.getString(albumColumn)
                    val genre = cursor.getString(genreColumn)
                    val title = cursor.getString(titleColumn)

                    Log.d("BatchUpdate", "Found track: $trackPath")
                    Log.d("BatchUpdate", "  Artist: '$artist', Album: '$album', Genre: '$genre', Title: '$title'")

                    if (File(trackPath).exists()) {
                        matchingTracks.add(trackPath)
                        Log.d("BatchUpdate", "  File exists, added to list")
                    } else {
                        Log.d("BatchUpdate", "  File does not exist!")
                    }
                }
            }

            Log.d("BatchUpdate", "Total matching tracks found: ${matchingTracks.size}")

        } catch (e: Exception) {
            Log.e("BatchUpdate", "Error finding tracks", e)
            e.printStackTrace()
        }

        return matchingTracks
    }

    private fun findTracksByScanning(field: String, value: String): List<String> {
        val matchingTracks = mutableListOf<String>()

        try {
            Log.d("BatchUpdate", "=== STARTING TRACK SEARCH ===")
            Log.d("BatchUpdate", "Searching for: $field = '$value'")

            // Получаем все аудиофайлы с более широким запросом
            val projection = arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME
            )

            // Пробуем разные selection для лучшей совместимости
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                Log.d("BatchUpdate", "Total tracks in MediaStore: ${cursor.count}")

                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val genreColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                var totalProcessed = 0
                var fileExistsCount = 0

                while (cursor.moveToNext()) {
                    totalProcessed++
                    val trackPath = cursor.getString(dataColumn)
                    val artist = cursor.getString(artistColumn) ?: ""
                    val album = cursor.getString(albumColumn) ?: ""
                    val genre = cursor.getString(genreColumn) ?: ""
                    val title = cursor.getString(titleColumn) ?: ""
                    val displayName = cursor.getString(displayNameColumn) ?: ""

                    // Проверяем существование файла
                    if (!File(trackPath).exists()) {
                        continue
                    }
                    fileExistsCount++

                    // Более гибкое сравнение с разными вариантами
                    val matches = when (field) {
                        FIELD_ARTIST -> {
                            artist.contains(value, ignoreCase = true) ||
                                    displayName.contains(value, ignoreCase = true) ||
                                    title.contains(value, ignoreCase = true)
                        }
                        FIELD_ALBUM -> album.contains(value, ignoreCase = true)
                        FIELD_GENRE -> genre.contains(value, ignoreCase = true)
                        FIELD_TITLE -> {
                            title.contains(value, ignoreCase = true) ||
                                    displayName.contains(value, ignoreCase = true)
                        }
                        else -> false
                    }

                    if (matches) {
                        matchingTracks.add(trackPath)
                        Log.d("BatchUpdate", "=== SEARCH COMPLETED ===")
                        Log.d("BatchUpdate", "Final result: ${matchingTracks.size} tracks")
                        matchingTracks.forEachIndexed { index, track ->
                            Log.d("BatchUpdate", "Result ${index + 1}: ${File(track).name} -> $track")
                        }
                    }

                    // Логируем прогресс каждые 100 треков
                    if (totalProcessed % 100 == 0) {
                        Log.d("BatchUpdate", "Processed $totalProcessed tracks, found ${matchingTracks.size} matches")
                    }
                }

                Log.d("BatchUpdate", "File scanning completed: Processed=$totalProcessed, Exists=$fileExistsCount, Matches=${matchingTracks.size}")
            }

        } catch (e: Exception) {
            Log.e("BatchUpdate", "Error in enhanced file scanning", e)
        }

        return matchingTracks
    }

    private fun findTracksByDirectScan(field: String, value: String): List<String> {
        val matchingTracks = mutableListOf<String>()

        try {
            Log.d("BatchUpdate", "Using direct file system scan for $field = '$value'")

            // Получаем основные директории с музыкой
            val musicDirs = arrayOf(
                File("/storage/emulated/0/Music"),
                File("/storage/emulated/0/Download"),
                File("/storage/emulated/0/DCIM"),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            )

            val audioExtensions = arrayOf("mp3", "m4a", "wav", "flac", "ogg", "aac")

            for (musicDir in musicDirs) {
                if (musicDir.exists() && musicDir.isDirectory) {
                    scanDirectoryForAudio(musicDir, audioExtensions, field, value, matchingTracks)
                }
            }

            Log.d("BatchUpdate", "Direct file scan found: ${matchingTracks.size} tracks")

        } catch (e: Exception) {
            Log.e("BatchUpdate", "Error in direct file scan", e)
        }

        return matchingTracks
    }

    private fun scanDirectoryForAudio(
        directory: File,
        extensions: Array<String>,
        field: String,
        value: String,
        results: MutableList<String>
    ) {
        try {
            val files = directory.listFiles() ?: return

            for (file in files) {
                if (file.isDirectory) {
                    // Рекурсивно сканируем поддиректории
                    scanDirectoryForAudio(file, extensions, field, value, results)
                } else {
                    // Проверяем расширение файла
                    val extension = file.extension.lowercase()
                    if (extensions.contains(extension)) {
                        // Для прямого сканирования используем MediaMetadataRetriever
                        if (matchesCriteria(file.absolutePath, field, value)) {
                            results.add(file.absolutePath)
                            Log.d("BatchUpdate", "Direct scan match: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BatchUpdate", "Error scanning directory: ${directory.absolutePath}", e)
        }
    }

    private fun matchesCriteria(filePath: String, field: String, value: String): Boolean {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""

            retriever.release()

            when (field) {
                FIELD_ARTIST -> artist.contains(value, ignoreCase = true)
                FIELD_ALBUM -> album.contains(value, ignoreCase = true)
                FIELD_GENRE -> genre.contains(value, ignoreCase = true)
                FIELD_TITLE -> title.contains(value, ignoreCase = true)
                else -> false
            }
        } catch (e: Exception) {
            Log.e("BatchUpdate", "Error checking criteria for: $filePath", e)
            false
        }
    }

    private fun checkWritePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    WRITE_PERMISSION_CODE
                )
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение получено! Попробуйте снова", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Разрешение отклонено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setSaveButtonEnabled(enabled: Boolean) {
        btnSaveTags.isEnabled = enabled
        btnSaveTags.text = if (enabled) "Сохранить" else "Сохранение..."
    }

    private fun saveMetadataToFile() {
        val newTitle = editTrackName.text.toString().trim()
        val newArtist = editTrackArtist.text.toString().trim()
        val newAlbum = editTrackAlbum.text.toString().trim()
        val newGenre = editTrackGenre.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }
        if (!checkWritePermission()) {
            return
        }

        setSaveButtonEnabled(false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = ID3TagEditor.updateTags(
                    trackPath,
                    newTitle,
                    newArtist,
                    newAlbum,
                    newGenre,
                    selectedCoverUri,
                    this@EditTrackActivity
                )

                if (success) {
                    // Обновляем MediaStore
                    updateMediaStore(newTitle, newArtist, newAlbum, newGenre)

                    // Сканируем файл
                    MediaScannerConnection.scanFile(
                        this@EditTrackActivity,
                        arrayOf(trackPath),
                        null
                    ) { path, uri ->
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(this@EditTrackActivity, "Теги сохранены!", Toast.LENGTH_SHORT).show()

                            // Обновляем треки в приложении
                            val intent = Intent("com.example.music.TAGS_UPDATED")
                            sendBroadcast(intent)

                            finish()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EditTrackActivity, "Ошибка сохранения тегов", Toast.LENGTH_SHORT).show()
                        setSaveButtonEnabled(true)
                    }
                }

            } catch (securityException: SecurityException) {
                Log.e("EditTrackActivity", "SecurityException", securityException)

                // Обрабатываем RecoverableSecurityException только на Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        // Используем рефлексию для совместимости
                        val userAction = securityException.javaClass.getMethod("getUserAction").invoke(securityException)
                        val actionIntent = userAction?.javaClass?.getMethod("getActionIntent")?.invoke(userAction) as? PendingIntent

                        if (actionIntent != null) {
                            withContext(Dispatchers.Main) {
                                try {
                                    val intentSender = actionIntent.intentSender
                                    val request = IntentSenderRequest.Builder(intentSender).build()
                                    writePermissionLauncher.launch(request)
                                } catch (e: Exception) {
                                    Toast.makeText(this@EditTrackActivity, "Ошибка запроса разрешения: ${e.message}", Toast.LENGTH_LONG).show()
                                    setSaveButtonEnabled(true)
                                }
                            }
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.e("EditTrackActivity", "Error getting RecoverableSecurityException", e)
                    }
                }

                // Если не получилось - показываем общую ошибку
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@EditTrackActivity,
                        "Нет доступа к файлу. Android ${Build.VERSION.SDK_INT}. Попробуйте выдать разрешение \"Управление файлами\" в настройках приложения.",
                        Toast.LENGTH_LONG
                    ).show()
                    setSaveButtonEnabled(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTrackActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    setSaveButtonEnabled(true)
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun updateMediaStore(title: String, artist: String, album: String, genre: String) {
        try {
            val contentResolver = contentResolver
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(trackPath)

            val cursor = contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val fileUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, artist)
                        put(MediaStore.Audio.Media.ALBUM, album)
                    }
                    contentResolver.update(fileUri, values, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // Обновляем градиент при возвращении на экран
        setupGradientBackground()
    }
}