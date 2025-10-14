package com.example.music

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.TimeUnit

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
    private lateinit var playbackModeButton: ImageButton
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    enum class PlaybackMode { SEQUENTIAL, REPEAT_ONE, SHUFFLE }
    private var playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL

    // Добавлено!
    private var shuffleInitialized: Boolean = false

    private var currentTrack: Track? = null

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
        playbackModeButton = findViewById(R.id.playbackModeButton)

        playbackMode = intent.getStringExtra("PLAYBACK_MODE")?.let {
            PlaybackMode.valueOf(it)
        } ?: PlaybackMode.SEQUENTIAL
        shuffleInitialized = playbackMode == PlaybackMode.SHUFFLE
        if (playbackMode == PlaybackMode.SEQUENTIAL || playbackMode == PlaybackMode.REPEAT_ONE) {
            QueueManager.restoreOriginalQueue(this)
        }

        updatePlaybackModeIcon()
        playbackModeButton.setOnClickListener {
            val prevMode = playbackMode
            playbackMode = when (playbackMode) {
                PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ONE
                PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
                PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENTIAL
            }
            updatePlaybackModeIcon()

            when (playbackMode) {
                PlaybackMode.SHUFFLE -> {
                    // Перемешиваем только если переключаемся С другого режима
                    if (prevMode != PlaybackMode.SHUFFLE) {
                        QueueManager.shuffleQueue(this)
                        shuffleInitialized = true
                    }
                }
                PlaybackMode.SEQUENTIAL, PlaybackMode.REPEAT_ONE -> {
                    if (prevMode == PlaybackMode.SHUFFLE) {
                        QueueManager.restoreOriginalQueue(this)
                    }
                    shuffleInitialized = false
                }
            }
        }

        nextButton.setOnClickListener {
            when (playbackMode) {
                PlaybackMode.SEQUENTIAL, PlaybackMode.SHUFFLE -> loadNextTrack()
                PlaybackMode.REPEAT_ONE -> {
                    seekBar.progress = 0
                    mediaPlayer?.seekTo(0)
                    mediaPlayer?.start()
                }
            }
        }
        previousButton.setOnClickListener { loadPreviousTrack() }
        backButton.setOnClickListener { finish() }

        addToPlaylistButton.setOnClickListener { view ->
            currentTrack?.let { track ->
                val adapter = TrackAdapter(mutableListOf(), false)
                adapter.showAddToPlaylistMenu(view, track)
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
            Log.d("FAVORITE_TRACK", "duration=${fullTrack.duration}, name=${fullTrack.name}")
            if (PlaylistManager.isInFavorites(fullTrack)) {
                PlaylistManager.removeFromFavorites(this, fullTrack)
                updateFavoritesButton(fullTrack)
                Toast.makeText(this, "Удалено из избранного", Toast.LENGTH_SHORT).show()
            } else {
                PlaylistManager.addToFavorites(this, fullTrack)
                updateFavoritesButton(fullTrack)
                Toast.makeText(this, "Добавлено в избранное", Toast.LENGTH_SHORT).show()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    currentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        playPauseButton.setOnClickListener { togglePlayPause() }

        val trackPath = intent.getStringExtra("TRACK_PATH")
        val trackNameStr = intent.getStringExtra("TRACK_NAME")
        val trackArtistStr = intent.getStringExtra("TRACK_ARTIST")
        currentTrack = getCurrentTrack()

        this.trackName.text = trackNameStr
        this.trackArtist.text = trackArtistStr
        loadTrackCover(trackPath)

        currentTrack?.let { updateFavoritesButton(it) }

        if (trackPath != null) {
            loadTrack(trackPath)
        } else {
            Toast.makeText(this, "Трек не найден", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updatePlaybackModeIcon() {
        when (playbackMode) {
            PlaybackMode.SEQUENTIAL -> playbackModeButton.setImageResource(R.drawable.ic_repeat_off)
            PlaybackMode.REPEAT_ONE -> playbackModeButton.setImageResource(R.drawable.ic_repeat_on)
            PlaybackMode.SHUFFLE -> playbackModeButton.setImageResource(R.drawable.ic_shuffle)
        }
    }

    private fun playbackModeToString(mode: PlaybackMode): String {
        return when (mode) {
            PlaybackMode.SEQUENTIAL -> "Последовательный"
            PlaybackMode.REPEAT_ONE -> "Повтор одного"
            PlaybackMode.SHUFFLE -> "Случайный"
        }
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                playPauseButton.setImageResource(R.drawable.ic_play)
                handler.removeCallbacksAndMessages(null)
            } else {
                player.start()
                playPauseButton.setImageResource(R.drawable.ic_pause)
                updateSeekBar()
            }
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
                trackAvatar.setImageResource(android.R.drawable.ic_menu_gallery)
                Log.e("PlayerActivity", "Ошибка загрузки обложки: ${e.message}")
            }
        }
    }

    private fun loadTrack(trackPath: String) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(trackPath)
                prepareAsync()
                setOnPreparedListener {
                    try {
                        seekBar.max = it.duration
                        totalTime.text = formatTime(it.duration)
                        seekBar.progress = 0
                        currentTime.text = formatTime(0)
                        playPauseButton.setImageResource(R.drawable.ic_pause)
                        start()
                        updateSeekBar()
                    } catch (e: Exception) {
                        Log.e("PlayerActivity", "Error in onPreparedListener: ${e.message}")
                        Toast.makeText(
                            this@PlayerActivity,
                            "Ошибка воспроизведения",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                setOnCompletionListener {
                    when (playbackMode) {
                        PlaybackMode.SEQUENTIAL, PlaybackMode.SHUFFLE -> loadNextTrack()
                        PlaybackMode.REPEAT_ONE -> {
                            seekBar.progress = 0
                            mediaPlayer?.seekTo(0)
                            mediaPlayer?.start()
                        }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("PlayerActivity", "MediaPlayer error: what=$what, extra=$extra")
                    Toast.makeText(
                        this@PlayerActivity,
                        "Ошибка воспроизведения: $what",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error setting up MediaPlayer: ${e.message}")
            Toast.makeText(this, "Ошибка загрузки трека: ${e.message}", Toast.LENGTH_SHORT).show()
            stopPlayback()
        }
    }

    private fun loadNextTrack() {
        if (QueueManager.moveToNextTrack(this)) {
            val nextTrack = QueueManager.getCurrentTrack()
            if (nextTrack != null && nextTrack.path != null && File(nextTrack.path).exists()) {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("TRACK_PATH", nextTrack.path)
                    putExtra("TRACK_NAME", nextTrack.name)
                    putExtra("TRACK_ARTIST", nextTrack.artist)
                    putExtra("PLAYBACK_MODE", playbackMode.name)
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Следующий трек недоступен", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Конец очереди", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPreviousTrack() {
        if (QueueManager.moveToPreviousTrack(this)) {
            val prevTrack = QueueManager.getCurrentTrack()
            if (prevTrack != null && prevTrack.path != null && File(prevTrack.path).exists()) {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("TRACK_PATH", prevTrack.path)
                    putExtra("TRACK_NAME", prevTrack.name)
                    putExtra("TRACK_ARTIST", prevTrack.artist)
                    putExtra("PLAYBACK_MODE", playbackMode.name)
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Предыдущий трек недоступен", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Начало очереди", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSeekBar() {
        mediaPlayer?.let { player ->
            if (player.isPlaying && !isFinishing) {
                seekBar.progress = player.currentPosition
                currentTime.text = formatTime(player.currentPosition)
                handler.postDelayed({ updateSeekBar() }, 1000)
            }
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun stopPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateFavoritesButton(track: Track) {
        if (PlaylistManager.isInFavorites(track)) {
            favoritesButton.setImageResource(R.drawable.ic_favorite_filled_red)
        } else {
            favoritesButton.setImageResource(R.drawable.ic_favorite_border)
        }
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                updateSeekBar()
            } else {
                seekBar.progress = player.currentPosition
                currentTime.text = formatTime(player.currentPosition)
            }
        }
        currentTrack?.let { updateFavoritesButton(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        handler.removeCallbacksAndMessages(null)
    }

    private fun findFullTrack(path: String?): Track? {
        return QueueManager.getCurrentQueue().find { it.path == path }
            ?: PlaylistManager.getPlaylists().flatMap { it.tracks }.find { it.path == path }
    }

    private fun getCurrentTrack(): Track? {
        val path = intent.getStringExtra("TRACK_PATH")
        return findFullTrack(path)
            ?: Track(
                name = intent.getStringExtra("TRACK_NAME") ?: "",
                artist = intent.getStringExtra("TRACK_ARTIST") ?: "",
                albumId = null,
                path = path
            )
    }
}