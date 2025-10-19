package com.example.music

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import android.os.Build
import androidx.core.content.ContextCompat
import android.app.AlertDialog

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

    private var playbackMode = "NORMAL" // "NORMAL", "REPEAT_ONE", "REPEAT_ALL", "STOP_AFTER"
    private var currentTrack: Track? = null
    private var musicService: MusicService? = null
    private var isBound = false

    private val trackChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.music.TRACK_CHANGED" -> {
                    updateUI()
                }
                "com.example.music.PLAYBACK_STATE_CHANGED" -> {
                    updatePlayPauseButton()
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
            addAction("com.example.music.TRACK_CHANGED")
            addAction("com.example.music.PLAYBACK_STATE_CHANGED")
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
            // Если пользователь вручную выбрал трек (TRACK_PATH) без явного режима,
            // считаем это линейным воспроизведением и сбрасываем режим на NORMAL (визуально и для сервиса)
            if (intent.hasExtra("TRACK_PATH")) {
                playbackMode = "NORMAL"
                prefs.edit().putString("playback_mode", playbackMode).apply()
                val modeIntent = Intent("com.example.music.PLAYBACK_MODE_CHANGED").apply {
                    putExtra("playback_mode", playbackMode)
                    setPackage(applicationContext.packageName)
                }
                sendBroadcast(modeIntent)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
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

        // Tap: open Queue screen
        addToPlaylistButton.setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java))
        }

        // Long-press: open More menu
        addToPlaylistButton.setOnLongClickListener {
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
            true
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

    private fun showPlayerUI() {
        playerContainer.visibility = android.view.View.VISIBLE
        lyricsContainer.visibility = android.view.View.GONE
        // optional: highlight active tab
        btnSongTab.setTextColor(ContextCompat.getColor(this, R.color.accent_color))
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
        btnLyricsTab.setTextColor(ContextCompat.getColor(this, R.color.accent_color))
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
        sendBroadcast(Intent("com.example.music.FAVORITES_UPDATED"))
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
        val intent = Intent("com.example.music.PLAYBACK_MODE_CHANGED")
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
                    trackAvatar.setImageBitmap(bitmap)
                } else {
                    trackAvatar.setImageResource(R.drawable.ic_album_placeholder)
                }
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
                trackAvatar.setImageResource(R.drawable.ic_album_placeholder)
            }
        }
    }

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun updateFavoritesButton(track: Track) {
        if (PlaylistManager.isInFavorites(track)) {
            favoritesButton.setImageResource(R.drawable.ic_favorite_filled_red)
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