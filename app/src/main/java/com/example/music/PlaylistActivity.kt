package com.example.music

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.View
import android.os.Bundle
import android.widget.TextView
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import java.io.File
import android.provider.MediaStore
import android.os.Build
import android.widget.Button
import android.widget.RadioGroup
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.renderscript.RenderScript
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.ScriptIntrinsicBlur
import androidx.core.graphics.ColorUtils
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.ColorStateList

class PlaylistActivity : AppCompatActivity() {

    private lateinit var playlistTracksList: RecyclerView
    private lateinit var playlistNameText: TextView
    private lateinit var playlistStatsText: TextView
    private lateinit var btnDeletePlaylist: ImageButton
    private lateinit var btnAddTrack: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnReorder: ImageButton
    private lateinit var btnSortPlaylist: ImageButton
    private lateinit var btnPlayPlaylist: ImageButton
    private lateinit var btnShufflePlaylist: ImageButton
    private lateinit var playlistRootLayout: LinearLayout

    private var playlistId: String? = null
    private var playlist: Playlist? = null
    private lateinit var trackAdapter: TrackAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private var isReorderMode = false
    private var sortType: Int = 0
    private var sortAscending: Boolean = false
    private var playlistBaseTopPadding: Int = -1
    private var editDialogCoverView: android.widget.ImageView? = null
    private var selectedCoverUri: android.net.Uri? = null
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

    private val editCoverPickLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val intent = Intent(this, CropImageActivity::class.java)
            intent.putExtra("imageUri", it)
            editCoverCropLauncher.launch(intent)
        }
    }

    private val editCoverCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedCoverUri = uri
                editDialogCoverView?.setImageURI(uri)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        playlistRootLayout = findViewById(R.id.playlistRootLayout)
        playlistNameText = findViewById(R.id.playlistNameText)
        playlistStatsText = findViewById(R.id.playlistStatsText)
        playlistTracksList = findViewById(R.id.playlistTracksList)
        btnDeletePlaylist = findViewById(R.id.btnDeletePlaylist)
        findViewById<ImageButton>(R.id.btnEditPlaylist).setOnClickListener { showEditPlaylistDialog() }
        btnAddTrack = findViewById(R.id.btnAddTrack)
        btnBack = findViewById(R.id.btnBack)
        btnReorder = findViewById(R.id.btnReorder)
        btnSortPlaylist = findViewById(R.id.btnSortPlaylist)
        btnPlayPlaylist = findViewById(R.id.btnPlayPlaylist)
        btnShufflePlaylist = findViewById(R.id.btnShufflePlaylist)

        playlistTracksList.layoutManager = LinearLayoutManager(this)

        playlistId = intent.getStringExtra("PLAYLIST_ID")
        playlist = PlaylistManager.getPlaylists().find { it.id == playlistId }

        restoreColor()

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

        val currentPlaylist = playlist
        if (currentPlaylist != null) {
            playlistNameText.text = currentPlaylist.name
            updatePlaylistStats(currentPlaylist)
            loadPlaylistBackground(currentPlaylist)
            trackAdapter = TrackAdapter(currentPlaylist.tracks.toMutableList(), isFromPlaylist = true)
            playlistTracksList.adapter = trackAdapter

            setupItemTouchHelper()

            trackAdapter.onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }

            trackAdapter.onTrackMoved = { fromPos, toPos ->
                playlist?.let {
                    val track = it.tracks.removeAt(fromPos)
                    it.tracks.add(toPos, track)
                    PlaylistManager.savePlaylists(this)
                }
            }

            trackAdapter.onTrackDeleted = { position ->
                deleteTrackAt(position)
            }
        } else {
            finish()
        }

        btnDeletePlaylist.setOnClickListener { onDeletePlaylistClick() }
        btnAddTrack.setOnClickListener { onAddTrackClick() }
        btnBack.setOnClickListener { onBackClick() }
        btnReorder.setOnClickListener { toggleReorderMode() }
        btnSortPlaylist.setOnClickListener { showSortMenu(it) }
        btnPlayPlaylist.setOnClickListener { playPlaylist() }
        btnShufflePlaylist.setOnClickListener { shuffleAndPlayPlaylist() }
    }

    override fun onResume() {
        super.onResume()
        // Специфика плейлиста: восстанавливаем цвет и обновляем данные
        restoreColor()
        refreshPlaylist()
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
        playlistRootLayout.setPadding(sidePad, statusBarHeight + sidePad, sidePad, sidePad)
    }

    private fun reapplyBarsFromBackground() {
        when (val bg = playlistRootLayout.background) {
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

    private fun playPlaylist() {
        val currentPlaylist = playlist
        if (currentPlaylist == null || currentPlaylist.tracks.isEmpty()) {
            Toast.makeText(this, "Плейлист пуст", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = currentPlaylist.tracks.filter { it.path != null && File(it.path).exists() }
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

    private fun shuffleAndPlayPlaylist() {
        val currentPlaylist = playlist
        if (currentPlaylist == null || currentPlaylist.tracks.isEmpty()) {
            Toast.makeText(this, "Плейлист пуст", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = currentPlaylist.tracks.filter { it.path != null && File(it.path).exists() }
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

    private fun deleteTrackAt(position: Int) {
        val currentPlaylist = playlist ?: return

        val currentTracks = trackAdapter.getTracks()
        if (position !in currentTracks.indices) return

        val trackToDelete = currentTracks[position]

        AlertDialog.Builder(this)
            .setTitle("Удалить трек")
            .setMessage("Удалить \"${trackToDelete.name}\" из плейлиста?")
            .setPositiveButton("Удалить") { _, _ ->
                trackAdapter.removeItem(position)
                currentPlaylist.tracks.removeAt(position)
                PlaylistManager.savePlaylists(this)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupItemTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                trackAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(playlistTracksList)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        trackAdapter.isReorderMode = isReorderMode

        if (isReorderMode) {
            btnReorder.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
        } else {
            btnReorder.clearColorFilter()
            PlaylistManager.savePlaylists(this)
        }
    }

    private fun refreshPlaylist() {
        if (!isReorderMode) {
            playlist = PlaylistManager.getPlaylists().find { it.id == playlistId }
            val currentPlaylist = playlist
            if (currentPlaylist != null && ::trackAdapter.isInitialized) {
                trackAdapter.updateTracks(currentPlaylist.tracks.toMutableList())
                updatePlaylistStats(currentPlaylist)
                loadPlaylistBackground(currentPlaylist)
            }
        }
    }

    private fun showEditPlaylistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_playlist, null)
        val coverImageView = dialogView.findViewById<android.widget.ImageView>(R.id.editPlaylistCover)
        val nameEditText = dialogView.findViewById<android.widget.EditText>(R.id.editPlaylistName)
        val saveButton = dialogView.findViewById<android.widget.Button>(R.id.btnSavePlaylist)
        editDialogCoverView = coverImageView
        selectedCoverUri = null

        val pl = playlist
        nameEditText.setText(pl?.name ?: "")
        val curCover = pl?.coverUri
        if (!curCover.isNullOrBlank()) {
            coverImageView.setImageURI(android.net.Uri.parse(curCover))
        } else {
            coverImageView.setImageResource(R.drawable.ic_album_placeholder)
        }

        // Обработчик клика на саму обложку
        coverImageView.setOnClickListener {
            editCoverPickLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Установка градиентного фона для диалога
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)
        val bg = ContextCompat.getDrawable(this, R.drawable.dialog_background)
        dialog.window?.setBackgroundDrawable(bg)

        // Обработчик кнопки сохранения (после создания dialog)
        saveButton.setOnClickListener {
            val newName = nameEditText.text.toString().trim()
            val curId = playlist?.id
            if (curId != null) {
                val newCover = selectedCoverUri?.toString()
                PlaylistManager.updatePlaylist(
                    this,
                    playlistId = curId,
                    newName = if (newName.isNotEmpty()) newName else null,
                    newCoverUri = newCover
                )
                // reload current playlist instance and refresh UI
                playlist = PlaylistManager.getPlaylists().find { it.id == curId }
                playlist?.let { updated ->
                    playlistNameText.text = updated.name
                    updatePlaylistStats(updated)
                    loadPlaylistBackground(updated)
                }
            }
            editDialogCoverView = null
            selectedCoverUri = null
            dialog.dismiss() // Закрыть диалог
        }

        dialog.show()

        // Настройка цветов текста для лучшей читаемости на градиентном фоне
        val titleView = dialog.findViewById<android.widget.TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        dialog.window?.let { ThemeManager.enableImmersive(it) }
    }

    private fun restoreColor() {
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)

        val gd = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(gradStart, gradEnd)
        )

        playlistRootLayout.background = gd
    }

    private fun onDeletePlaylistClick() {
        val currentPlaylist = playlist ?: return

        if (currentPlaylist.id == Playlist.FAVORITES_ID) {
            Toast.makeText(this, "Избранное нельзя удалить", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Удалить плейлист")
            .setMessage("Вы уверены, что хотите удалить плейлист \"${currentPlaylist.name}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                PlaylistManager.removePlaylist(this, currentPlaylist.id)
                returnToPlaylists()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun onAddTrackClick() {
        val allTracks = getAllTracks()
        if (allTracks.isEmpty()) {
            Toast.makeText(this, "Нет доступных треков", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_track, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.trackListDialog)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelAdd)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmAdd)

        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = DialogTrackAdapter(allTracks)
        recyclerView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        setupDialogGradient(dialog)
        dialog.window?.setDimAmount(0.7f)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val selectedTracks = adapter.getSelectedTracks()
            if (selectedTracks.isEmpty()) {
                Toast.makeText(this, "Не выбрано ни одного трека", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentPlaylist = playlist
            if (currentPlaylist != null) {
                var addedCount = 0
                selectedTracks.forEach { selectedTrack ->
                    if (!currentPlaylist.tracks.any { it.path == selectedTrack.path }) {
                        PlaylistManager.addTrackToPlaylist(this, currentPlaylist.id, selectedTrack)
                        addedCount++
                    }
                }

                playlist = PlaylistManager.getPlaylists().find { it.id == playlistId }

                val updatedPlaylist = playlist
                if (updatedPlaylist != null) {
                    trackAdapter.updateTracks(updatedPlaylist.tracks.toMutableList())
                    updatePlaylistStats(updatedPlaylist)
                }

                val message = if (addedCount > 0) {
                    "✅ Добавлено треков: $addedCount"
                } else {
                    "ℹ️ Все выбранные треки уже в плейлисте"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()

        // Для стандартных Button устанавливаем прозрачный фон и цвет через backgroundTint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnCancel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FF5252"))
            btnConfirm.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#664CAF50"))
        }

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )

        dialog.window?.let { ThemeManager.enableImmersive(it) }
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

    private fun getAllTracks(): List<Track> {
        val tracks = mutableListOf<Track>()
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

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn).toString()
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val album = cursor.getString(albumColumn)
                val path = cursor.getString(dataColumn)
                val duration = cursor.getLong(durationColumn)

                tracks.add(
                    Track(
                        id = id,
                        name = title,
                        artist = artist,
                        albumId = albumId,
                        albumName = album,
                        path = path,
                        duration = duration
                    )
                )
            }
        }

        return tracks
    }

    private fun returnToPlaylists() {
        finish()
    }

    private fun onBackClick() {
        finish()
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
            applyPlaylistSort() // Вызов соответствующего метода сортировки
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

    // Добавьте этот метод в класс PlaylistActivity
    private fun updateSortDirectionIndicator(indicator: View, isAscending: Boolean) {
        val params = indicator.layoutParams as android.widget.RelativeLayout.LayoutParams
        if (isAscending) {
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
        } else {
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
        }
        indicator.layoutParams = params
    }

    private fun applyPlaylistSort() {
        val list = trackAdapter.getTracks()
        when (sortType) {
            0 -> if (sortAscending) list.sortBy { it.dateModified } else list.sortByDescending { it.dateModified }
            1 -> if (sortAscending) list.sortBy { it.name.lowercase() } else list.sortByDescending { it.name.lowercase() }
            2 -> if (sortAscending) list.sortBy { (it.artist ?: "").lowercase() } else list.sortByDescending { (it.artist ?: "").lowercase() }
            3 -> if (sortAscending) list.sortBy { it.duration ?: 0L } else list.sortByDescending { it.duration ?: 0L }
            4 -> if (sortAscending) list.sortBy { ListeningStats.getPlayCount(it.path ?: "") } else list.sortByDescending { ListeningStats.getPlayCount(it.path ?: "") }
        }
        trackAdapter.notifyDataSetChanged()
        playlist?.let { updatePlaylistStats(it) }
    }

    private fun updatePlaylistStats(pl: Playlist) {
        val count = pl.tracks.size
        val total = pl.tracks.sumOf { it.duration ?: 0L }
        val hours = total / 3600000
        val minutes = (total % 3600000) / 60000
        val durationText = if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
        val tracksText = when {
            count % 10 == 1 && count % 100 != 11 -> "$count трек"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "$count трека"
            else -> "$count треков"
        }
        playlistStatsText.text = "$tracksText • $durationText"
    }

    private fun loadPlaylistBackground(pl: Playlist) {
        val uriStr = pl.coverUri
        if (uriStr.isNullOrBlank()) {
            return
        }
        try {
            val uri = android.net.Uri.parse(uriStr)
            contentResolver.openInputStream(uri)?.use { input ->
                val bmp = android.graphics.BitmapFactory.decodeStream(input)
                if (bmp != null) {
                    setBlurredBackground(bmp)
                }
            }
        } catch (_: Exception) { }
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
            playlistRootLayout.background = BitmapDrawable(resources, dark)
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

    private fun lighten(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        val nr = (r + ((255 - r) * factor)).toInt().coerceIn(0, 255)
        val ng = (g + ((255 - g) * factor)).toInt().coerceIn(0, 255)
        val nb = (b + ((255 - b) * factor)).toInt().coerceIn(0, 255)
        return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }
}