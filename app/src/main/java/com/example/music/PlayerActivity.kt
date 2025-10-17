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
        playbackMode = prefs.getString("playback_mode", "NORMAL") ?: "NORMAL"

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

        addToPlaylistButton.setOnClickListener {
            currentTrack?.let { track ->
                showPlaylistDialog(track)
            }
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
                    trackAvatar.setImageResource(android.R.drawable.ic_menu_gallery)
                }
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
                trackAvatar.setImageResource(android.R.drawable.ic_menu_gallery)
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