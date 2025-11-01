package com.arotter.music

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import android.provider.MediaStore
import android.content.ContentUris
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var trackAvatar: ImageView
    private lateinit var trackName: TextView
    private lateinit var trackArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var addToPlaylistButton: ImageButton
    private lateinit var favoritesButton: ImageButton
    private lateinit var btnPlaybackMode: ImageButton
    private lateinit var btnSongTab: TextView
    private lateinit var btnLyricsTab: TextView
    private lateinit var playerContainer: android.widget.LinearLayout
    private lateinit var lyricsContainer: android.view.View
    private lateinit var lyricsTitleInPlayer: TextView
    private lateinit var lyricsArtistInPlayer: TextView
    private lateinit var lyricsEditInPlayer: android.widget.EditText
    private lateinit var btnSaveLyricsInPlayer: android.widget.Button
    private lateinit var btnFindLyricsInPlayer: android.widget.Button
    private lateinit var playerBlurBg: ImageView

    private var playbackMode = "NORMAL" // "NORMAL", "REPEAT_ONE", "REPEAT_ALL", "STOP_AFTER"
    private var currentTrack: Track? = null
    private var musicService: MusicService? = null
    private var isBound = false

    private val trackChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.arotter.music.TRACK_CHANGED" -> {
                    updateUI()
                }
                "com.arotter.music.PLAYBACK_STATE_CHANGED" -> {
                    updatePlayPauseButton()
                }
                "com.arotter.music.PLAYBACK_MODE_CHANGED" -> {
                    // Обновляем локальное состояние и иконку режима мгновенно
                    playbackMode = intent.getStringExtra("playback_mode")
                        ?: getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
                            .getString("playback_mode", playbackMode) ?: playbackMode
                    updatePlaybackModeIcon()
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true

            setupServiceListeners()
            updateUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        trackAvatar = findViewById(R.id.playerTrackAvatar)
        playerBlurBg = findViewById(R.id.playerBlurBg)
        trackName = findViewById(R.id.playerTrackName)
        trackArtist = findViewById(R.id.playerTrackArtist)
        seekBar = findViewById(R.id.seekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        playPauseButton = findViewById(R.id.playPauseButton)
        nextButton = findViewById(R.id.nextButton)
        previousButton = findViewById(R.id.previousButton)
        backButton = findViewById(R.id.backButton)
        addToPlaylistButton = findViewById(R.id.addToPlaylistButton)
        favoritesButton = findViewById(R.id.favoriteButton)
        btnPlaybackMode = findViewById(R.id.playbackModeButton)
        btnSongTab = findViewById(R.id.btnSongTab)
        btnLyricsTab = findViewById(R.id.btnLyricsTab)
        playerContainer = findViewById(R.id.playerContainer)
        lyricsContainer = findViewById(R.id.lyricsContainer)
        // children inside include_lyrics_content
        lyricsTitleInPlayer = findViewById(R.id.lyricsTitleInPlayer)
        lyricsArtistInPlayer = findViewById(R.id.lyricsArtistInPlayer)
        lyricsEditInPlayer = findViewById(R.id.lyricsEditInPlayer)
        btnSaveLyricsInPlayer = findViewById(R.id.btnSaveLyricsInPlayer)
        btnFindLyricsInPlayer = findViewById(R.id.btnFindLyricsInPlayer)

        val filter = IntentFilter().apply {
            addAction("com.arotter.music.TRACK_CHANGED")
            addAction("com.arotter.music.PLAYBACK_STATE_CHANGED")
            addAction("com.arotter.music.PLAYBACK_MODE_CHANGED")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            trackChangedReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Tabs actions: Песня | Текст (переключение контента на этой странице)
        btnSongTab.setOnClickListener { showPlayerUI() }
        btnLyricsTab.setOnClickListener { showLyricsUI() }

        // Применяем цвет темы сразу: выделение активной вкладки и фон кнопки Play/Pause
        applyAccentToPlayPause()
        showPlayerUI()

        val trackPath = intent.getStringExtra("TRACK_PATH")
        if (trackPath != null) {
            val serviceIntent = Intent(this, MusicService::class.java).apply {
                putExtra("TRACK_PATH", trackPath)
            }
            startService(serviceIntent)
        }

        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        // Загружаем сохраненный режим воспроизведения
        val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        // Если пришли с явным указанием режима (например, из перемешивания), применим его визуально
        intent.getStringExtra("PLAYBACK_MODE")?.let { mode ->
            playbackMode = mode
            prefs.edit().putString("playback_mode", playbackMode).apply()
        } ?: run {
            playbackMode = prefs.getString("playback_mode", "NORMAL") ?: "NORMAL"
        }

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

        updatePlaybackModeIcon()

        btnPlaybackMode.setOnClickListener {
            togglePlaybackMode()
        }

        nextButton.setOnClickListener {
            musicService?.let {
                val intent = Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_NEXT
                }
                startService(intent)
            }
        }

        previousButton.setOnClickListener {
            musicService?.let {
                val intent = Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_PREVIOUS
                }
                startService(intent)
            }
        }

        backButton.setOnClickListener { finish() }

        // Top-right 'more' button: open same options menu as in track list
        addToPlaylistButton.setOnClickListener {
            val trackPath = intent.getStringExtra("TRACK_PATH")
            val trackForMenu = currentTrack ?: findFullTrack(trackPath) ?: Track(
                name = intent.getStringExtra("TRACK_NAME") ?: "",
                artist = intent.getStringExtra("TRACK_ARTIST") ?: "",
                albumId = null,
                path = trackPath,
                duration = null,
                dateModified = null
            )
            showPlayerMoreOptions(trackForMenu)
        }

        favoritesButton.setOnClickListener {
            val trackPath = intent.getStringExtra("TRACK_PATH")
            val fullTrack = findFullTrack(trackPath) ?: Track(
                name = intent.getStringExtra("TRACK_NAME") ?: "",
                artist = intent.getStringExtra("TRACK_ARTIST") ?: "",
                albumId = null,
                path = trackPath,
                duration = null,
                dateModified = null
            )
            if (PlaylistManager.isInFavorites(fullTrack)) {
                PlaylistManager.removeFromFavorites(this, fullTrack)
                updateFavoritesButton(fullTrack)
            } else {
                PlaylistManager.addToFavorites(this, fullTrack)
                updateFavoritesButton(fullTrack)
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                    currentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        playPauseButton.setOnClickListener { togglePlayPause() }

        val trackNameStr = intent.getStringExtra("TRACK_NAME")
        val trackArtistStr = intent.getStringExtra("TRACK_ARTIST")
        currentTrack = QueueManager.getCurrentTrack()

        this.trackName.text = trackNameStr
        this.trackArtist.text = trackArtistStr
        loadTrackCover(trackPath)

        currentTrack?.let { updateFavoritesButton(it) }
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.showSystemBars(window, this)
        setLayoutFullscreen()
        applyContentTopPadding()
        reapplyBarsFromBackground()
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

    private fun showPlayerUI() {
        playerContainer.visibility = android.view.View.VISIBLE
        lyricsContainer.visibility = android.view.View.GONE
        // optional: highlight active tab
        btnSongTab.setTextColor(ThemeManager.getAccentColor(this))
        btnLyricsTab.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }
    private fun showLyricsUI() {
        val track = currentTrack ?: run {
            val path = intent.getStringExtra("TRACK_PATH")
            findFullTrack(path) ?: Track(
                name = intent.getStringExtra("TRACK_NAME") ?: "",
                artist = intent.getStringExtra("TRACK_ARTIST") ?: "",
                albumId = null,
                path = path,
                duration = null,
                dateModified = null
            )
        }

        lyricsTitleInPlayer.text = track.name
        lyricsArtistInPlayer.text = track.artist ?: ""
        lyricsEditInPlayer.setText(loadLyricsInPlayer(track.path))

        btnSaveLyricsInPlayer.setOnClickListener {
            saveLyricsInPlayer(track.path, lyricsEditInPlayer.text?.toString() ?: "")
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        }
        btnFindLyricsInPlayer.setOnClickListener {
            val query = "${track.name} ${track.artist ?: ""} текст".trim()
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val uri = android.net.Uri.parse("https://www.google.com/search?q=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        playerContainer.visibility = android.view.View.GONE
        lyricsContainer.visibility = android.view.View.VISIBLE
        // optional: highlight active tab
        btnSongTab.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        btnLyricsTab.setTextColor(ThemeManager.getAccentColor(this))
    }

    private fun applyAccentToPlayPause() {
        try {
            val accent = ThemeManager.getAccentColor(this)
            val bg = playPauseButton.background
            when (bg) {
                is android.graphics.drawable.GradientDrawable -> {
                    bg.setColor(accent)
                }
                is android.graphics.drawable.Drawable -> {
                    playPauseButton.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
                }
                else -> {
                    // no-op
                }
            }
        } catch (_: Exception) { }
    }

    private fun prefsLyrics() = getSharedPreferences("lyrics_store", Context.MODE_PRIVATE)
    private fun lyricsKey(path: String?) = "lyrics_" + (path ?: "unknown")
    private fun loadLyricsInPlayer(path: String?): String = prefsLyrics().getString(lyricsKey(path), "") ?: ""
    private fun saveLyricsInPlayer(path: String?, text: String) {
        prefsLyrics().edit().putString(lyricsKey(path), text).apply()
    }

    private fun showPlayerMoreOptions(track: Track) {
        val options = mutableListOf(
            "Добавить в избранное",
            "Добавить в очередь",
            "Информация о треке",
            "Поделиться",
            "Установить как рингтон",
            "Добавить в плейлист",
            "Редактировать теги",
            "Удалить",
            "Очередь"
        )

        if (PlaylistManager.isInFavorites(track)) {
            options[0] = "Удалить из избранного"
        }

        val builder = AlertDialog.Builder(this)
        if (!track.name.isNullOrBlank()) {
            val titleView = TextView(this).apply {
                text = track.name
                // Симметричные отступы сверху/снизу, чтобы убрать лишнюю пустоту сверху
                val hPad = dp(24)
                val vPad = dp(16)
                setPadding(hPad, vPad, hPad, vPad)
                textSize = 20f
            }
            builder.setCustomTitle(titleView)
        }
        builder.setItems(options.toTypedArray()) { _, which ->
            when (which) {
                0 -> toggleFavorite(track)
                1 -> addToQueue(track)
                2 -> TrackInfoDialog(this, track).show()
                3 -> shareTrack(track)
                4 -> setAsRingtone(track)
                5 -> showPlaylistDialog(track)
                6 -> showEditTags(track)
                7 -> confirmDelete(track)
                8 -> startActivity(Intent(this, QueueActivity::class.java))
            }
        }
        builder.show()
    }

    private fun showTrackMenu(anchor: android.view.View, track: Track) {
        // На экране плеера показываем тот же набор опций, что и в списке треков
        showPlayerMoreOptions(track)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toggleFavorite(track: Track) {
        if (PlaylistManager.isInFavorites(track)) {
            PlaylistManager.removeFromFavorites(this, track)
            Toast.makeText(this, "Удалено из избранного", Toast.LENGTH_SHORT).show()
        } else {
            PlaylistManager.addToFavorites(this, track)
            Toast.makeText(this, "Добавлено в избранное", Toast.LENGTH_SHORT).show()
        }
        updateFavoritesButton(track)
        sendBroadcast(Intent("com.arotter.music.FAVORITES_UPDATED"))
    }

    private fun addToQueue(track: Track) {
        QueueManager.addToManualQueue(this, track)
        Toast.makeText(this, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
    }

    private fun shareTrack(track: Track) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "audio/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(track.path))
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(shareIntent, "Поделиться треком"))
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAsRingtone(track: Track) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.System.canWrite(this)) {
                Toast.makeText(this, "Необходимо разрешение на изменение системных настроек", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = android.net.Uri.parse("package:" + packageName)
                startActivity(intent)
                return
            }
        }

        try {
            val file = File(track.path ?: return)
            val values = android.content.ContentValues()
            values.put(android.provider.MediaStore.MediaColumns.DATA, file.absolutePath)
            values.put(android.provider.MediaStore.MediaColumns.TITLE, track.name)
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/*")
            values.put(android.provider.MediaStore.Audio.Media.IS_RINGTONE, true)

            val uri = android.provider.MediaStore.Audio.Media.getContentUriForPath(file.absolutePath)
            contentResolver.delete(uri!!, android.provider.MediaStore.MediaColumns.DATA + "=\"" + file.absolutePath + "\"", null)
            val newUri = contentResolver.insert(uri, values)

            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                this,
                android.media.RingtoneManager.TYPE_RINGTONE,
                newUri
            )

            Toast.makeText(this, "Установлено как рингтон", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditTags(track: Track) {
        val intent = Intent(this, EditTrackActivity::class.java)
        track.path?.let { intent.putExtra("TRACK_PATH", it) }
        startActivity(intent)
    }

    private fun confirmDelete(track: Track) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Удалить трек?")
        builder.setMessage("Это удалит файл с устройства. Действие необратимо.")
        builder.setPositiveButton("Удалить") { _, _ ->
            deleteTrackFile(track.path)
        }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun deleteTrackFile(path: String?) {
        if (path == null) {
            Toast.makeText(this, "Некорректный путь к файлу", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentResolver = contentResolver
            val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${android.provider.MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(path)

            val cursor = contentResolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media._ID), selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID))
                    val deleteUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val rowsDeleted = contentResolver.delete(deleteUri, null, null)
                    if (rowsDeleted > 0) {
                        Toast.makeText(this, "Трек удалён", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Не удалось удалить файл", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLyrics(track: Track) {
        val intent = Intent(this, LyricsActivity::class.java)
        intent.putExtra("TRACK_PATH", track.path)
        intent.putExtra("TRACK_NAME", track.name)
        intent.putExtra("TRACK_ARTIST", track.artist)
        startActivity(intent)
    }

    private fun togglePlaybackMode() {
        val previousMode = playbackMode

        playbackMode = when (playbackMode) {
            "NORMAL" -> {
                btnPlaybackMode.setImageResource(R.drawable.ic_repeat_on)
                "REPEAT_ONE"
            }
            "REPEAT_ONE" -> {
                btnPlaybackMode.setImageResource(R.drawable.ic_shuffle)
                // Перемешиваем очередь при включении shuffle
                QueueManager.shuffleQueue(this)
                "SHUFFLE"
            }
            "SHUFFLE" -> {
                btnPlaybackMode.setImageResource(R.drawable.ic_stop_after)
                "STOP_AFTER"
            }
            "STOP_AFTER" -> {
                btnPlaybackMode.setImageResource(R.drawable.ic_repeat_off)
                "NORMAL"
            }
            else -> {
                btnPlaybackMode.setImageResource(R.drawable.ic_repeat_off)
                "NORMAL"
            }
        }

        // Восстанавливаем порядок при выходе из SHUFFLE
        if (previousMode == "SHUFFLE" && playbackMode != "SHUFFLE") {
            QueueManager.restoreOriginalQueue(this)
        }

        // Сохраняем режим
        val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("playback_mode", playbackMode).apply()

        // Отправляем режим в сервис
        val intent = Intent("com.arotter.music.PLAYBACK_MODE_CHANGED")
        intent.setPackage(packageName)
        intent.putExtra("playback_mode", playbackMode)
        sendBroadcast(intent)
    }

    private fun setupServiceListeners() {
        musicService?.setOnUpdateListener { current, duration ->
            runOnUiThread {
                seekBar.max = duration
                seekBar.progress = current
                currentTime.text = formatTime(current)
                totalTime.text = formatTime(duration)
            }
        }
    }

    private fun showPlaylistDialog(track: Track) {
        val playlists = PlaylistManager.getPlaylists().filter { it.id != Playlist.FAVORITES_ID }

        if (playlists.isEmpty()) {
            Toast.makeText(this, "Нет доступных плейлистов", Toast.LENGTH_SHORT).show()
            return
        }

        val playlistNames = playlists.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Добавить в плейлист")
            .setItems(playlistNames) { _, which ->
                val selectedPlaylist = playlists[which]

                if (selectedPlaylist.tracks.any { it.path == track.path }) {
                    Toast.makeText(this, "Трек уже в плейлисте", Toast.LENGTH_SHORT).show()
                } else {
                    PlaylistManager.addTrackToPlaylist(this, selectedPlaylist.id, track)
                    Toast.makeText(this, "Добавлено в ${selectedPlaylist.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateUI() {
        currentTrack = MusicService.currentTrack
        currentTrack?.let { track ->
            trackName.text = track.name
            trackArtist.text = track.artist ?: "Unknown Artist"
            updateFavoritesButton(track)
            loadTrackCover(track.path)
        }

        updatePlayPauseButton()

        val duration = musicService?.getDuration() ?: 0
        seekBar.max = duration
        totalTime.text = formatTime(duration)
    }

    private fun togglePlayPause() {
        if (MusicService.isPlaying) {
            musicService?.pauseMusic()
        } else {
            musicService?.resumeMusic()
        }
        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        if (MusicService.isPlaying) {
            playPauseButton.setImageResource(R.drawable.ic_pause)
        } else {
            playPauseButton.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updatePlaybackModeIcon() {
        when (playbackMode) {
            "NORMAL" -> btnPlaybackMode.setImageResource(R.drawable.ic_repeat_off)
            "REPEAT_ONE" -> btnPlaybackMode.setImageResource(R.drawable.ic_repeat_on)
            "SHUFFLE" -> btnPlaybackMode.setImageResource(R.drawable.ic_shuffle)
            "STOP_AFTER" -> btnPlaybackMode.setImageResource(R.drawable.ic_stop_after)
        }
    }

    private fun loadTrackCover(trackPath: String?) {
        if (trackPath != null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(trackPath)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    setRounded(trackAvatar, bitmap)
                    applyBlurBackground(bitmap)
                    playerBlurBg.visibility = android.view.View.VISIBLE
                } else {
                    // Fallback: пробуем вытащить обложку по albumId из MediaStore
                    val albumId = currentTrack?.albumId
                    var fallbackBitmap: android.graphics.Bitmap? = null
                    if (albumId != null) {
                        try {
                            val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                            contentResolver.openInputStream(uri)?.use { input ->
                                fallbackBitmap = BitmapFactory.decodeStream(input)
                            }
                        } catch (_: Exception) {}
                    }

                    if (fallbackBitmap != null) {
                        setRounded(trackAvatar, fallbackBitmap!!)
                        applyBlurBackground(fallbackBitmap!!)
                        playerBlurBg.visibility = android.view.View.VISIBLE
                    } else {
                        // Скруглённый плейсхолдер (рендерим по размеру view, чтобы не было мыла)
                        val ph = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_album_placeholder)
                        ph?.let { setRounded(trackAvatar, drawableToSizedBitmapForView(trackAvatar, it), 12f) } ?: run {
                            trackAvatar.setImageResource(R.drawable.ic_album_placeholder)
                        }
                        playerBlurBg.setImageDrawable(null)
                        playerBlurBg.visibility = android.view.View.GONE
                    }
                }
                retriever.release()
            } catch (e: Exception) {
                val ph = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_album_placeholder)
                ph?.let { setRounded(trackAvatar, drawableToSizedBitmapForView(trackAvatar, it), 12f) } ?: run {
                    trackAvatar.setImageResource(R.drawable.ic_album_placeholder)
                }
                playerBlurBg.setImageDrawable(null)
                playerBlurBg.visibility = android.view.View.GONE
            }
        }
    }

    private fun applyBlurBackground(src: android.graphics.Bitmap) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Устанавливаем исходник, затем применяем RenderEffect blur
                playerBlurBg.setImageBitmap(src)
                val effect = RenderEffect.createBlurEffect(56f, 56f, Shader.TileMode.CLAMP)
                playerBlurBg.setRenderEffect(effect)
                // На S+ нет готового битмапа после эффекта, используем верх исходника как приближение
                applyBarsFromBitmapTop(src)
            } else {
                // RenderScript blur (совместимо с Android 8.1), радиус до 25f
                val scaled = android.graphics.Bitmap.createScaledBitmap(
                    src,
                    (src.width * 0.25f).toInt().coerceAtLeast(1),
                    (src.height * 0.25f).toInt().coerceAtLeast(1),
                    true
                )
                val blurred = blurWithRenderScript(this, scaled, 20f)
                playerBlurBg.setImageBitmap(blurred)
                applyBarsFromBitmapTop(blurred)
            }
            // Сильное затемнение поверх размытого фона
            playerBlurBg.setColorFilter(android.graphics.Color.argb(160, 0, 0, 0), android.graphics.PorterDuff.Mode.SRC_OVER)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка обработки фона: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reapplyBarsFromBackground() {
        val bmp = (playerBlurBg.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (bmp != null) {
            applyBarsFromBitmapTop(bmp)
        } else {
            // Фоллбек: используем тему первичного фона
            applyBarsForColor(ThemeManager.getPrimaryGradientStart(this))
        }
    }

    private fun applyBarsFromBitmapTop(bmp: android.graphics.Bitmap) {
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
        val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(color)
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
        val topPad = if (resId > 0) res.getDimensionPixelSize(resId) else 0
        // Добавляем паддинг только к контенту, фон-изображение остаётся на весь экран
        if (playerContainer.paddingTop != topPad) {
            playerContainer.setPadding(
                playerContainer.paddingLeft,
                topPad,
                playerContainer.paddingRight,
                playerContainer.paddingBottom
            )
        }
        if (lyricsContainer.paddingTop != topPad) {
            lyricsContainer.setPadding(
                lyricsContainer.paddingLeft,
                topPad,
                lyricsContainer.paddingRight,
                lyricsContainer.paddingBottom
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun blurWithRenderScript(context: Context, bitmap: android.graphics.Bitmap, radius: Float): android.graphics.Bitmap {
        val rs = RenderScript.create(context)
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setRadius(radius.coerceIn(0.1f, 25f))
        script.setInput(input)
        script.forEach(output)
        val outBitmap = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
        output.copyTo(outBitmap)
        input.destroy(); output.destroy(); script.destroy(); rs.destroy()
        return outBitmap
    }

    private fun setRounded(view: ImageView, bitmap: android.graphics.Bitmap, radiusDp: Float = 12f) {
        val radiusPx = dp(radiusDp.toInt()).toFloat()
        val outW = (if (view.width > 0) view.width else (view.layoutParams?.width ?: 0)).let { if (it > 0) it else dp(300) }
        val outH = (if (view.height > 0) view.height else (view.layoutParams?.height ?: 0)).let { if (it > 0) it else dp(300) }
        val rounded = createRoundedBitmapCropped(bitmap, outW, outH, radiusPx)
        view.scaleType = ImageView.ScaleType.FIT_XY
        view.setImageBitmap(rounded)
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun drawableToSizedBitmapForView(view: ImageView, drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        val targetW = (if (view.width > 0) view.width else (view.layoutParams?.width ?: 0)).let { sz -> if (sz > 0) sz else dp(300) }
        val targetH = (if (view.height > 0) view.height else (view.layoutParams?.height ?: 0)).let { sz -> if (sz > 0) sz else dp(300) }
        val bitmap = android.graphics.Bitmap.createBitmap(targetW, targetH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createRoundedBitmapCropped(src: android.graphics.Bitmap, outW: Int, outH: Int, radiusPx: Float): android.graphics.Bitmap {
        val output = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        val shader = android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val scale = maxOf(outW.toFloat() / src.width, outH.toFloat() / src.height)
        val dx = (outW - src.width * scale) / 2f
        val dy = (outH - src.height * scale) / 2f
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        val rect = android.graphics.RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        return output
    }

    // Простой stack blur (по мотивам алгоритма Кусса/Марио Кляйна), упрощённый
    private fun fastBlur(sentBitmap: android.graphics.Bitmap, radius: Int): android.graphics.Bitmap {
        var bitmap = sentBitmap.copy(sentBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        val vmin = IntArray(maxOf(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = (i / divsum)
            i++
        }

        yi = 0
        var yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + minOf(wm, maxOf(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius

            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) vmin[x] = minOf(x + radius + 1, wm)
                p = pix[yw + vmin[x]]
                sir = stack[(stackpointer + 1) % div]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yw += w
        }

        x = 0
        while (x < w) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = maxOf(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) yp += w
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) vmin[y] = minOf(y + r1, hm) * w
                p = x + vmin[y]
                sir = stack[(stackpointer + 1) % div]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun updateFavoritesButton(track: Track) {
        if (PlaylistManager.isInFavorites(track)) {
            favoritesButton.setImageResource(R.drawable.ic_favorite_filled)
        } else {
            favoritesButton.setImageResource(R.drawable.ic_favorite_border)
        }
    }

    private fun findFullTrack(trackPath: String?): Track? {
        if (trackPath == null) return null
        return QueueManager.getCurrentQueue().find { it.path == trackPath }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(trackChangedReceiver)
        } catch (e: Exception) {
            // Receiver уже отменен
        }
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}