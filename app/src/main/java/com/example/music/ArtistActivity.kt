package com.example.music

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.RelativeLayout
import android.widget.EditText
import android.widget.Button
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import android.widget.RadioGroup
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.BitmapFactory
import android.renderscript.RenderScript
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.ScriptIntrinsicBlur

class ArtistActivity : AppCompatActivity() {

    private lateinit var artistNameText: TextView
    private lateinit var artistTracksList: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnPlayArtist: ImageButton
    private lateinit var btnShuffleArtist: ImageButton
    private lateinit var artistRootLayout: LinearLayout
    private var editDialogCoverView: ImageView? = null
    private lateinit var artistStatsText: TextView
    private lateinit var btnSortArtist: ImageButton
    private lateinit var btnReorderTracks: ImageButton
    private var isReorderMode = false
    private var itemTouchHelper: ItemTouchHelper? = null
    private val trackUiReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.music.TRACK_CHANGED",
                "com.example.music.PLAYBACK_STATE_CHANGED" -> {
                    if (::trackAdapter.isInitialized) trackAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private var artistName: String? = null
    private lateinit var trackAdapter: TrackAdapter
    private val artistTracks = mutableListOf<Track>()

    private var sortType: Int = 0
    private var sortAscending: Boolean = false

    private lateinit var btnArtistMore: ImageButton
    private var customArtistCover: Uri? = null
    private var customArtistName: String? = null
    private lateinit var btnSearchArtist: ImageButton

    private val artistCoverLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val intent = Intent(this, CropImageActivity::class.java)
            intent.putExtra("imageUri", it)
            artistCropLauncher.launch(intent)
        }
    }

    private val artistCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                customArtistCover = uri
                editDialogCoverView?.setImageURI(uri)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist)

        artistRootLayout = findViewById(R.id.artistRootLayout)
        artistNameText = findViewById(R.id.artistNameText)
        artistTracksList = findViewById(R.id.artistTracksList)
        btnBack = findViewById(R.id.btnBackArtist)
        btnPlayArtist = findViewById(R.id.btnPlayArtist)
        btnShuffleArtist = findViewById(R.id.btnShuffleArtist)
        btnArtistMore = findViewById(R.id.btnArtistMore)
        btnSearchArtist = findViewById(R.id.btnSearch)
        artistStatsText = findViewById(R.id.artistStatsText)
        btnSortArtist = findViewById(R.id.btnSortArtist)
        btnReorderTracks = findViewById(R.id.btnReorderTracks)

        artistTracksList.layoutManager = LinearLayoutManager(this)

        artistName = intent.getStringExtra("ARTIST_NAME")

        restoreColor()
        loadCustomArtistData()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Рисуем контент под статус-баром и делаем его прозрачным
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            run {
                val flags = (window.decorView.systemUiVisibility
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                window.decorView.systemUiVisibility = flags
            }
        }

        // Показываем системные панели, включаем layout под статус-бар и применяем цвет нав-бара/иконок
        run {
            ThemeManager.showSystemBars(window, this)
            setLayoutFullscreen()
            applyContentTopPadding()
            reapplyBarsFromBackground()
        }

        if (artistName != null) {
            artistNameText.text = artistName
            loadArtistTracks()
            trackAdapter = TrackAdapter(artistTracks, isFromPlaylist = false)
            artistTracksList.adapter = trackAdapter
            updateStats()
            loadArtistCover()
            setupReorder()
        } else {
            finish()
        }

        btnBack.setOnClickListener { finish() }
        btnPlayArtist.setOnClickListener { playArtist() }
        btnShuffleArtist.setOnClickListener { shuffleAndPlayArtist() }
        btnSortArtist.setOnClickListener { showSortMenu(it) }
        btnReorderTracks.setOnClickListener { toggleReorderMode() }
        btnArtistMore.setImageResource(R.drawable.ic_edit)
        btnArtistMore.setOnClickListener { showEditArtistDialog() }
        btnSearchArtist.setOnClickListener { showTrackSearchDialog() }
    }

    override fun onResume() {
        super.onResume()
        // Обновляем адаптер при возврате на экран
        if (::trackAdapter.isInitialized) {
            trackAdapter.notifyDataSetChanged()
        }
        // Обновляем статус- и нав-бар под текущий фон
        ThemeManager.showSystemBars(window, this)
        setLayoutFullscreen()
        applyContentTopPadding()
        reapplyBarsFromBackground()
        val filter = android.content.IntentFilter().apply {
            addAction("com.example.music.TRACK_CHANGED")
            addAction("com.example.music.PLAYBACK_STATE_CHANGED")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            trackUiReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ThemeManager.showSystemBars(window, this)
            setLayoutFullscreen()
            applyContentTopPadding()
            reapplyBarsFromBackground()
        }
    }

    private fun setLayoutFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            run {
                val decor = window.decorView
                var flags = decor.systemUiVisibility
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                decor.systemUiVisibility = flags
            }
        }
    }

    private fun applyContentTopPadding() {
        val res = resources
        val resId = res.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resId > 0) res.getDimensionPixelSize(resId) else 0
        val sidePad = dp(16)
        artistRootLayout.setPadding(sidePad, statusBarHeight + sidePad, sidePad, sidePad)
    }

    private fun reapplyBarsFromBackground() {
        when (val bg = artistRootLayout.background) {
            is BitmapDrawable -> bg.bitmap?.let { applyBarsFromBitmapTop(it) }
            is ColorDrawable -> applyBarsForColor(bg.color)
            else -> applyBarsForColor(ThemeManager.getPrimaryGradientStart(this))
        }
    }

    private fun applyBarsFromBitmapTop(bmp: Bitmap) {
        try {
            val h = bmp.height.coerceAtLeast(1)
            val sampleHeight = (h * 0.08f).toInt().coerceIn(1, h)
            val yEnd = sampleHeight
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0L
            val w = bmp.width
            val step = (w / 50).coerceAtLeast(1)
            for (y in 0 until yEnd) {
                var x = 0
                while (x < w) {
                    val c = bmp.getPixel(x, y)
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                    count++
                    x += step
                }
            }
            if (count > 0) {
                val r = (rSum / count).toInt().coerceIn(0, 255)
                val g = (gSum / count).toInt().coerceIn(0, 255)
                val b = (bSum / count).toInt().coerceIn(0, 255)
                val color = android.graphics.Color.rgb(r, g, b)
                applyBarsForColor(color)
            }
        } catch (_: Exception) { }
    }

    private fun applyBarsForColor(color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                // Статус-бар оставляем прозрачным, чтобы фон был реальным продолжением
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                // Нав-бар красим в цвет верхней части фона
                window.navigationBarColor = color
            } catch (_: Exception) { }
        }
        val luminance = ColorUtils.calculateLuminance(color)
        val lightIcons = luminance > 0.5
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decor = window.decorView
            var vis = decor.systemUiVisibility
            vis = if (lightIcons) {
                vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            decor.systemUiVisibility = vis
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val decor = window.decorView
            var vis = decor.systemUiVisibility
            vis = if (lightIcons) {
                vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            decor.systemUiVisibility = vis
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(trackUiReceiver) } catch (_: Exception) {}
    }

    private fun loadArtistTracks() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ARTIST} = ?"
        val selectionArgs = arrayOf(artistName)

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            artistTracks.clear()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn).toString()
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val album = cursor.getString(albumColumn)
                val path = cursor.getString(dataColumn)
                val duration = cursor.getLong(durationColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

                if (path != null && File(path).exists()) {
                    artistTracks.add(
                        Track(
                            id = id,
                            name = title,
                            artist = artist,
                            albumId = albumId,
                            albumName = album,
                            path = path,
                            duration = duration,
                            dateModified = dateModified
                        )
                    )
                }
            }
        }
    }

    private fun playArtist() {
        if (artistTracks.isEmpty()) {
            Toast.makeText(this, "Нет треков исполнителя", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = artistTracks.filter { it.path != null && File(it.path).exists() }
        if (availableTracks.isEmpty()) {
            Toast.makeText(this, "Нет доступных треков", Toast.LENGTH_SHORT).show()
            return
        }

        QueueManager.initializeQueueFromPosition(this, availableTracks, 0)
        val firstTrack = availableTracks[0]

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("TRACK_PATH", firstTrack.path)
            putExtra("TRACK_NAME", firstTrack.name)
            putExtra("TRACK_ARTIST", firstTrack.artist)
        }
        startActivity(intent)
    }

    private fun shuffleAndPlayArtist() {
        if (artistTracks.isEmpty()) {
            Toast.makeText(this, "Нет треков исполнителя", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = artistTracks.filter { it.path != null && File(it.path).exists() }
        if (availableTracks.isEmpty()) {
            Toast.makeText(this, "Нет доступных треков", Toast.LENGTH_SHORT).show()
            return
        }

        QueueManager.shuffleQueue(this, availableTracks, 0)
        val firstTrack = QueueManager.getCurrentTrack()

        if (firstTrack != null && firstTrack.path != null) {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("TRACK_PATH", firstTrack.path)
                putExtra("TRACK_NAME", firstTrack.name)
                putExtra("TRACK_ARTIST", firstTrack.artist)
                putExtra("PLAYBACK_MODE", "SHUFFLE")
            }
            startActivity(intent)
        }
    }

    private fun restoreColor() {
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)

        val gd = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(gradStart, gradEnd)
        )

        artistRootLayout.background = gd // или artistRootLayout, genreRootLayout
    }

    private fun setupDialogGradient(dialog: AlertDialog) {
        // Заменяем градиент на серый фон
        val bg = ContextCompat.getDrawable(this, R.drawable.dialog_background)
        dialog.window?.setBackgroundDrawable(bg)

        // Устанавливаем темные цвета для системных баров
        val darkColor = Color.parseColor("#D3D3D3")
        dialog.window?.let { window ->
            window.statusBarColor = darkColor
            window.navigationBarColor = darkColor

            // Светлые иконки в статус-баре для лучшей читаемости на темном фоне
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = window.decorView.systemUiVisibility
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                window.decorView.systemUiVisibility = flags
            }

            // Для навигационной панели (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                var flags = window.decorView.systemUiVisibility
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                window.decorView.systemUiVisibility = flags
            }
        }
    }

    private fun showEditArtistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_artist, null)
        val coverImageView = dialogView.findViewById<ImageView>(R.id.editArtistCover)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editArtistName)
        val saveButton = dialogView.findViewById<Button>(R.id.btnSaveArtist)

        editDialogCoverView = coverImageView

        // Загружаем текущие данные
        nameEditText.setText(customArtistName ?: artistName)

        if (customArtistCover != null) {
            coverImageView.setImageURI(customArtistCover)
        } else {
            coverImageView.setImageResource(R.drawable.ic_album_placeholder)
        }

        // Обработчик клика на саму обложку
        coverImageView.setOnClickListener {
            artistCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        setupDialogGradient(dialog)

        // Обработчик кнопки сохранения (после создания dialog)
        saveButton.setOnClickListener {
            val newName = nameEditText.text.toString().trim()
            if (newName.isNotEmpty()) {
                customArtistName = newName
                saveCustomArtistData()
                updateArtistUI()
                loadArtistCover()
            }
            editDialogCoverView = null
            dialog.dismiss() // Закрыть диалог
        }

        dialog.show()

        // Настройка цветов текста для лучшей читаемости на градиентном фоне
        val titleView = dialog.findViewById<TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        dialog.window?.let { ThemeManager.showSystemBars(it, this) }
        reapplyBarsFromBackground()
    }

    private fun saveCustomArtistData() {
        val prefs = getSharedPreferences("custom_artists", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("artist_${artistName}_name", customArtistName)
        editor.putString("artist_${artistName}_cover", customArtistCover?.toString())
        editor.apply()
    }

    private fun loadCustomArtistData() {
        val prefs = getSharedPreferences("custom_artists", MODE_PRIVATE)
        customArtistName = prefs.getString("artist_${artistName}_name", null)
        val coverUri = prefs.getString("artist_${artistName}_cover", null)
        customArtistCover = if (coverUri != null) Uri.parse(coverUri) else null
        updateArtistUI()
    }

    private fun updateArtistUI() {
        artistNameText.text = customArtistName ?: artistName
    }

    private fun loadArtistCover() {
        // Unknown всегда плейсхолдер
        if (artistName?.equals("Unknown", ignoreCase = true) == true) {
            return
        }

        // Сначала используем кастомную обложку, если задана
        customArtistCover?.let {
            try {
                contentResolver.openInputStream(it)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) setBlurredBackground(bmp)
                }
            } catch (_: Exception) { }
            return
        }

        var found = false
        for (t in artistTracks) {
            val p = t.path ?: continue
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(p)
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    setBlurredBackground(bitmap)
                    found = true
                    break
                }
            } catch (_: Exception) { }
        }
        if (!found) {
            val name = (customArtistName ?: artistName ?: "").trim()
            if (!name.equals("Unknown", ignoreCase = true) && name.isNotEmpty()) {
                val bmp = generateLetterCover(name)
                setBlurredBackground(bmp)
            }
        }
    }

    private fun generateLetterCover(name: String): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)

        val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            textSize = size * 0.6f
        }
        val bounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, bounds)
        val x = (size - bounds.width()) / 2f - bounds.left
        val y = (size + bounds.height()) / 2f - bounds.bottom
        canvas.drawText(letter, x, y, paint)
        return bmp
    }

    private fun setBlurredBackground(src: Bitmap) {
        try {
            val scaled = Bitmap.createScaledBitmap(
                src,
                (src.width * 0.25f).toInt().coerceAtLeast(1),
                (src.height * 0.25f).toInt().coerceAtLeast(1),
                true
            )
            val blurred = blurWithRenderScriptCompat(this, scaled, 20f)
            val dark = applyDarkOverlay(blurred, 160)
            artistRootLayout.background = BitmapDrawable(resources, dark)
            reapplyBarsFromBackground()
        } catch (_: Exception) { }
    }

    private fun applyDarkOverlay(src: Bitmap, alpha: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(alpha.coerceIn(0,255), 0, 0, 0)
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        return out
    }

    @Suppress("DEPRECATION")
    private fun blurWithRenderScriptCompat(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val rs = RenderScript.create(context)
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setRadius(radius.coerceIn(0f, 25f))
        script.setInput(input)
        script.forEach(output)
        output.copyTo(bitmap)
        rs.destroy()
        return bitmap
    }

    private fun showSortMenu(view: View) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sort, null)

        val sortTypeGroup = dialogView.findViewById<RadioGroup>(R.id.sortTypeGroup)
        val btnSortAsc = dialogView.findViewById<ImageButton>(R.id.btnSortAsc)
        val btnSortDesc = dialogView.findViewById<ImageButton>(R.id.btnSortDesc)
        val sortAscContainer = dialogView.findViewById<LinearLayout>(R.id.sortAscContainer)
        val sortDescContainer = dialogView.findViewById<LinearLayout>(R.id.sortDescContainer)
        val sortDirectionIndicator = dialogView.findViewById<View>(R.id.sortDirectionIndicator)
        val btnApplySort = dialogView.findViewById<Button>(R.id.btnApplySort)
        val btnCancelSort = dialogView.findViewById<Button>(R.id.btnCancelSort)

        // Установка текущих значений
        when (sortType) {
            0 -> sortTypeGroup.check(R.id.sortByDate)
            1 -> sortTypeGroup.check(R.id.sortByName)
            2 -> sortTypeGroup.check(R.id.sortByArtist)
            3 -> sortTypeGroup.check(R.id.sortByDuration)
            4 -> sortTypeGroup.check(R.id.sortByPlays)
        }

        // Обновление индикатора направления
        updateSortDirectionIndicator(sortDirectionIndicator, sortAscending)

        // Обработчики направления сортировки
        val directionClickListener = View.OnClickListener { v ->
            when (v.id) {
                R.id.sortAscContainer, R.id.btnSortAsc -> {
                    sortAscending = true
                    updateSortDirectionIndicator(sortDirectionIndicator, true)
                }
                R.id.sortDescContainer, R.id.btnSortDesc -> {
                    sortAscending = false
                    updateSortDirectionIndicator(sortDirectionIndicator, false)
                }
            }
        }

        sortAscContainer.setOnClickListener(directionClickListener)
        sortDescContainer.setOnClickListener(directionClickListener)
        btnSortAsc.setOnClickListener(directionClickListener)
        btnSortDesc.setOnClickListener(directionClickListener)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Установка градиентного фона (вместо XML)
        setupDialogGradient(dialog)
        dialog.window?.setDimAmount(0.7f) // Затемнение фона

        btnApplySort.setOnClickListener {
            sortType = when (sortTypeGroup.checkedRadioButtonId) {
                R.id.sortByDate -> 0
                R.id.sortByName -> 1
                R.id.sortByArtist -> 2
                R.id.sortByDuration -> 3
                R.id.sortByPlays -> 4
                else -> 0
            }
            applyArtistSort() // Вызов соответствующего метода сортировки
            dialog.dismiss()
        }

        btnCancelSort.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Настройка размера окна
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    // Добавьте этот метод в класс ArtistActivity
    private fun updateSortDirectionIndicator(indicator: View, isAscending: Boolean) {
        val params = indicator.layoutParams as RelativeLayout.LayoutParams
        if (isAscending) {
            params.addRule(RelativeLayout.ALIGN_PARENT_START)
            params.removeRule(RelativeLayout.ALIGN_PARENT_END)
        } else {
            params.addRule(RelativeLayout.ALIGN_PARENT_END)
            params.removeRule(RelativeLayout.ALIGN_PARENT_START)
        }
        indicator.layoutParams = params
    }
    private fun applyArtistSort() {
        when (sortType) {
            0 -> if (sortAscending) artistTracks.sortBy { it.dateModified } else artistTracks.sortByDescending { it.dateModified }
            1 -> if (sortAscending) artistTracks.sortBy { it.name.lowercase() } else artistTracks.sortByDescending { it.name.lowercase() }
            2 -> if (sortAscending) artistTracks.sortBy { (it.artist ?: "").lowercase() } else artistTracks.sortByDescending { (it.artist ?: "").lowercase() }
            3 -> if (sortAscending) artistTracks.sortBy { it.duration ?: 0L } else artistTracks.sortByDescending { it.duration ?: 0L }
            4 -> if (sortAscending) artistTracks.sortBy { ListeningStats.getPlayCount(it.path ?: "") } else artistTracks.sortByDescending { ListeningStats.getPlayCount(it.path ?: "") }
        }
        trackAdapter.notifyDataSetChanged()
        updateStats()
    }

    private fun showSortOrderDialog(applySort: (Boolean) -> Unit) {
        val options = arrayOf("По возрастанию", "По убыванию")
        AlertDialog.Builder(this)
            .setTitle("Порядок сортировки")
            .setItems(options) { _, which ->
                val ascending = (which == 0)
                applySort(ascending)
                trackAdapter.notifyDataSetChanged()
                updateStats()
            }
            .show()
    }

    private fun updateStats() {
        val count = artistTracks.size
        val totalDuration = artistTracks.sumOf { it.duration ?: 0L }
        val hours = totalDuration / 3600000
        val minutes = (totalDuration % 3600000) / 60000
        val seconds = (totalDuration / 1000) % 60
        val durationText = if (hours > 0) "$hours:${minutes.toString().padStart(2,'0')}:${seconds.toString().padStart(2,'0')}" else "$minutes:${seconds.toString().padStart(2,'0')}"
        artistStatsText.text = "$count треков • $durationText"
    }

    private fun setupReorder() {
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from in artistTracks.indices && to in artistTracks.indices) {
                    val item = artistTracks.removeAt(from)
                    artistTracks.add(to, item)
                    trackAdapter.notifyItemMoved(from, to)
                    return true
                }
                return false
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
            override fun isLongPressDragEnabled(): Boolean = isReorderMode
        }
        itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(artistTracksList)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        trackAdapter.isReorderMode = isReorderMode
        if (isReorderMode) {
            btnReorderTracks.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
        } else {
            btnReorderTracks.clearColorFilter()
        }
    }

    private fun showTrackSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_search, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
        val btnSearchCancel = dialogView.findViewById<Button>(R.id.btnSearchCancel)
        val btnSearchConfirm = dialogView.findViewById<Button>(R.id.btnSearchConfirm)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Установка серого фона для диалога
        setupDialogGradient(dialog)

        btnSearchCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSearchConfirm.setOnClickListener {
            val query = searchEditText.text.toString().trim()
            if (query.isEmpty()) {
                loadArtistTracks() // или loadArtistTracks(), loadGenreTracks() в зависимости от активности
                trackAdapter.updateTracks(artistTracks) // или artistTracks, genreTracks
            } else {
                val filtered = artistTracks.filter {
                    it.name.contains(query, true) ||
                            (it.artist?.contains(query, true) == true)
                }
                trackAdapter.updateTracks(filtered.toMutableList())
            }
            updateStats()
            dialog.dismiss()
        }

        dialog.show()

        // Настройка размера окна
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialog.window?.let { ThemeManager.enableImmersive(it) }
    }
}