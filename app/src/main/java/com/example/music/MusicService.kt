package com.arotter.music

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.*
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.max

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.arotter.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.arotter.music.ACTION_PAUSE"
        const val ACTION_NEXT = "com.arotter.music.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.arotter.music.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.arotter.music.ACTION_STOP"
        const val ACTION_CLOSE = "com.arotter.music.ACTION_CLOSE"
        const val ACTION_SET_MODE = "com.arotter.music.ACTION_SET_MODE"
        const val ACTION_SET_SLEEP = "com.arotter.music.ACTION_SET_SLEEP"
        const val ACTION_CANCEL_SLEEP = "com.arotter.music.ACTION_CANCEL_SLEEP"

        var currentTrack: Track? = null
        var isPlaying = false
        var currentPosition = 0
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var currentTrackPath: String? = null
    private var updateListener: ((Int, Int) -> Unit)? = null
    private lateinit var mediaSession: MediaSessionCompat
    private var playbackMode = "NORMAL"
    private var hasCountedAsPlayed = false
    private var lastCheckedPosition = 0
    private var hasSeekedThisTrack = false
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isDucked = false
    private var pausedByFocusTransient = false
    private var sleepHandler: Handler? = null
    private var sleepRunnable: Runnable? = null
    private var playbackSpeed: Float = 1.0f
    private var playbackPitch: Float = 1.0f
    private var audioEqualizer: Equalizer? = null
    private var eqBandLevels: ShortArray? = null

    private val playbackModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            playbackMode = intent?.getStringExtra("playback_mode") ?: "NORMAL"
            updatePlaybackState()
            showNotification()
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        DiskImageCache.init(this)
        createNotificationChannel()

        // Загружаем режим воспроизведения
        val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        playbackMode = prefs.getString("playback_mode", "NORMAL") ?: "NORMAL"
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Регистрируем receiver для изменения режима
        val filter = IntentFilter("com.arotter.music.PLAYBACK_MODE_CHANGED")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            playbackModeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Создаём MediaSession
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resumeMusic()
                override fun onPause() = pauseMusic()
                override fun onSkipToNext() = playNext()
                override fun onSkipToPrevious() = playPrevious()
                override fun onStop() = stopService()
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumeMusic()
            ACTION_PAUSE -> pauseMusic()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopService()
            ACTION_CLOSE -> stopService()
            ACTION_SET_MODE -> handleSetMode(intent)
            ACTION_SET_SLEEP -> {
                val minutes = intent.getIntExtra("sleep_minutes", 0).coerceAtLeast(0)
                if (minutes > 0) scheduleSleepTimer(minutes) else cancelSleepTimer()
            }
            ACTION_CANCEL_SLEEP -> cancelSleepTimer()
            else -> {
                val trackPath = intent?.getStringExtra("TRACK_PATH")
                if (trackPath != null) {
                    playTrack(trackPath)
                }
            }
        }
        return START_STICKY
    }

    private fun scheduleSleepTimer(minutes: Int) {
        try {
            if (sleepHandler == null) sleepHandler = Handler(Looper.getMainLooper())
            sleepRunnable?.let { sleepHandler?.removeCallbacks(it) }
            val ms = minutes * 60_000L
            sleepRunnable = Runnable {
                try {
                    pauseMusic()
                    Toast.makeText(this, "Автовыключение: музыка на паузе", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
            sleepHandler?.postDelayed(sleepRunnable!!, ms)
            Toast.makeText(this, "Таймер сна: ${minutes} мин", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun cancelSleepTimer() {
        try {
            sleepRunnable?.let { sleepHandler?.removeCallbacks(it) }
            sleepRunnable = null
            Toast.makeText(this, "Таймер сна отменён", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun handleSetMode(intent: Intent) {
        val previousMode = playbackMode
        val requested = intent.getStringExtra("playback_mode")
        playbackMode = if (requested != null) {
            requested
        } else {
            when (previousMode) {
                "NORMAL" -> "REPEAT_ONE"
                "REPEAT_ONE" -> "SHUFFLE"
                "SHUFFLE" -> "STOP_AFTER"
                "STOP_AFTER" -> "NORMAL"
                else -> "NORMAL"
            }
        }

        // Применяем побочные эффекты для shuffle
        if (previousMode == "SHUFFLE" && playbackMode != "SHUFFLE") {
            try { QueueManager.restoreOriginalQueue(this) } catch (_: Exception) {}
        } else if (playbackMode == "SHUFFLE" && previousMode != "SHUFFLE") {
            try { QueueManager.shuffleQueue(this) } catch (_: Exception) {}
        }

        // Сохраняем режим
        getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("playback_mode", playbackMode)
            .apply()

        // Уведомляем UI
        Intent("com.arotter.music.PLAYBACK_MODE_CHANGED").apply {
            putExtra("playback_mode", playbackMode)
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }

        updatePlaybackState()
        showNotification()
    }

    fun playTrack(path: String) {
        if (currentTrackPath == path && mediaPlayer != null) {
            resumeMusic()
            return
        }

        // Фиксируем процент прослушивания предыдущего трека
        try {
            val prevPath = currentTrackPath
            if (prevPath != null && mediaPlayer != null && !hasSeekedThisTrack && !hasCountedAsPlayed) {
                val dur = getDuration()
                val pos = getCurrentPosition()
                if (dur > 0) {
                    val pct = ((pos.toFloat() / dur.toFloat()) * 100f).coerceIn(0f, 100f)
                    val ct = currentTrack
                    var genre: String? = null
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(prevPath)
                        genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        r.release()
                    } catch (_: Exception) {}
                    PlayHistory.append(
                        this,
                        PlayHistory.PlayEvent(
                            timestamp = System.currentTimeMillis(),
                            trackPath = ct?.path,
                            trackName = ct?.name,
                            artist = ct?.artist,
                            albumName = ct?.albumName,
                            genre = genre,
                            percent = pct
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        currentTrackPath = path
        currentTrack = QueueManager.getCurrentTrack()

        // Если очередь пустая — создаём минимальную
        if (QueueManager.getCurrentQueue().isEmpty() && currentTrack != null) {
            QueueManager.initializeQueueFromPosition(this, mutableListOf(currentTrack!!), 0)
        }

        hasCountedAsPlayed = false
        lastCheckedPosition = 0
        hasSeekedThisTrack = false

        // Уведомляем UI
        Intent("com.arotter.music.TRACK_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }

        mediaPlayer?.release()
        releaseEqualizer()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                start()
                MusicService.isPlaying = true
                setOnCompletionListener { onTrackCompleted() }
            } catch (e: Exception) {
                Log.e("MusicService", "Error playing track", e)
                return
            }
        }

        applyPlaybackParams()
        initEqualizer()
        requestAudioFocus()
        if (!isDucked) setPlayerVolume(1.0f)
        updateMediaSessionMetadata()
        updatePlaybackState()
        showNotification()
        startPositionUpdates()
    }

    private fun onTrackCompleted() {
        checkAndIncrementPlayCount()
        when (playbackMode) {
            "REPEAT_ONE" -> {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
                MusicService.isPlaying = true
                updatePlaybackState()
                showNotification()
                Intent("com.arotter.music.PLAYBACK_STATE_CHANGED").apply {
                    setPackage(applicationContext.packageName)
                }.also { sendBroadcast(it) }
            }
            "SHUFFLE" -> {
                val queue = QueueManager.getCurrentQueue()
                if (queue.size > 1) {
                    val randomIndex = (0 until queue.size).random()
                    QueueManager.initializeQueueFromPosition(this, queue, randomIndex)
                    val randomTrack = QueueManager.getCurrentTrack()
                    if (randomTrack?.path != null) {
                        playTrack(randomTrack.path)
                    }
                } else {
                    stopSelf()
                }
            }
            "STOP_AFTER" -> {
                pauseMusic()
                playbackMode = "NORMAL"
                getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("playback_mode", playbackMode)
                    .apply()
                Intent("com.arotter.music.PLAYBACK_MODE_CHANGED").apply {
                    putExtra("playback_mode", playbackMode)
                    setPackage(applicationContext.packageName)
                }.also { sendBroadcast(it) }
                stopSelf()
            }
            else -> { // NORMAL or REPEAT_ALL
                val moved = QueueManager.moveToNextTrack(this)
                if (moved) {
                    QueueManager.getCurrentTrack()?.path?.let { playTrack(it) }
                    return
                }
                val queue = QueueManager.getCurrentQueue()
                if (playbackMode == "REPEAT_ALL") {
                    if (queue.isNotEmpty()) {
                        QueueManager.initializeQueueFromPosition(this, queue, 0)
                        QueueManager.getCurrentTrack()?.path?.let { playTrack(it) }
                    }
                } else { // NORMAL
                    val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
                    val continueFromStart = prefs.getBoolean("continue_from_start", false)
                    if (continueFromStart && queue.isNotEmpty()) {
                        QueueManager.initializeQueueFromPosition(this, queue, 0)
                        QueueManager.getCurrentTrack()?.path?.let { playTrack(it) }
                    } else {
                        stopSelf()
                    }
                }
            }
        }
    }

    fun pauseMusic() {
        mediaPlayer?.pause()
        isPlaying = false
        updatePlaybackState()
        showNotification()
        Intent("com.arotter.music.PLAYBACK_STATE_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }
    }

    fun resumeMusic() {
        requestAudioFocus()
        mediaPlayer?.start()
        if (!isDucked) setPlayerVolume(1.0f)
        applyPlaybackParams()
        isPlaying = true
        startPositionUpdates()
        updatePlaybackState()
        showNotification()
        Intent("com.arotter.music.PLAYBACK_STATE_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }
    }

    private fun applyPlaybackParams() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { mp ->
                    val params = mp.playbackParams ?: PlaybackParams()
                    params.speed = playbackSpeed.coerceIn(0.5f, 2.0f)
                    params.pitch = playbackPitch.coerceIn(0.5f, 2.0f)
                    mp.playbackParams = params
                }
            }
        } catch (_: Exception) {}
    }

    fun setPlaybackSpeedPitch(speed: Float, pitch: Float) {
        playbackSpeed = speed
        playbackPitch = pitch
        applyPlaybackParams()
    }

    fun resetPlaybackParams() {
        playbackSpeed = 1.0f
        playbackPitch = 1.0f
        applyPlaybackParams()
    }

    fun getPlaybackSpeed(): Float = playbackSpeed
    fun getPlaybackPitch(): Float = playbackPitch

    private fun initEqualizer() {
        try {
            val sessionId = mediaPlayer?.audioSessionId ?: return
            if (sessionId == AudioManager.ERROR) return
            releaseEqualizer()
            audioEqualizer = Equalizer(0, sessionId).apply {
                enabled = true
                eqBandLevels?.let { arr ->
                    val bands = numberOfBands.toInt()
                    if (arr.size == bands) {
                        for (i in 0 until bands) {
                            val level = arr[i]
                            try { setBandLevel(i.toShort(), level) } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            audioEqualizer = null
        }
    }

    private fun releaseEqualizer() {
        try { audioEqualizer?.enabled = false } catch (_: Exception) {}
        try { audioEqualizer?.release() } catch (_: Exception) {}
        audioEqualizer = null
    }

    fun isEqualizerAvailable(): Boolean = try { audioEqualizer?.numberOfBands ?: 0 > 0 } catch (_: Exception) { false }
    fun getEqNumberOfBands(): Short = try { audioEqualizer?.numberOfBands ?: 0 } catch (_: Exception) { 0 }
    fun getEqBandLevelRange(): ShortArray = try { audioEqualizer?.bandLevelRange ?: shortArrayOf(0, 0) } catch (_: Exception) { shortArrayOf(0, 0) }
    fun getEqCenterFreq(band: Short): Int = try { audioEqualizer?.getCenterFreq(band) ?: 0 } catch (_: Exception) { 0 }
    fun getEqBandLevel(band: Short): Short = try { audioEqualizer?.getBandLevel(band) ?: 0 } catch (_: Exception) { 0 }

    fun setEqBandLevel(band: Short, level: Short) {
        try {
            val range = getEqBandLevelRange()
            val clamped = level.coerceIn(range[0], range[1])
            audioEqualizer?.setBandLevel(band, clamped)
            val bands = getEqNumberOfBands().toInt()
            if (bands > 0) {
                if (eqBandLevels == null || eqBandLevels?.size != bands) {
                    eqBandLevels = ShortArray(bands) { 0 }
                }
                eqBandLevels?.let { it[band.toInt()] = clamped }
            }
        } catch (_: Exception) {}
    }

    fun resetEqualizer() {
        try {
            val bands = getEqNumberOfBands().toInt()
            val zeros = ShortArray(bands) { 0 }
            for (i in 0 until bands) {
                try { audioEqualizer?.setBandLevel(i.toShort(), 0) } catch (_: Exception) {}
            }
            eqBandLevels = zeros
        } catch (_: Exception) {}
    }

    private fun playNext() {
        if (QueueManager.moveToNextTrack(this)) {
            val nextTrack = QueueManager.getCurrentTrack()
            if (nextTrack?.path != null) {
                Intent("com.arotter.music.TRACK_CHANGED").apply {
                    setPackage(applicationContext.packageName)
                }.also { sendBroadcast(it) }
                playTrack(nextTrack.path)
            } else {
                Toast.makeText(this, "Следующий трек недоступен", Toast.LENGTH_SHORT).show()
            }
        } else {
            val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            val continueFromStart = prefs.getBoolean("continue_from_start", false)
            if (continueFromStart) {
                val queue = QueueManager.getCurrentQueue()
                if (queue.isNotEmpty()) {
                    QueueManager.initializeQueueFromPosition(this, queue, 0)
                    val firstTrack = QueueManager.getCurrentTrack()
                    if (firstTrack?.path != null) {
                        Intent("com.arotter.music.TRACK_CHANGED").apply {
                            setPackage(applicationContext.packageName)
                        }.also { sendBroadcast(it) }
                        playTrack(firstTrack.path)
                    }
                }
            } else {
                Toast.makeText(this, "Это последний трек", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playPrevious() {
        val positionMs = getCurrentPosition()
        if (positionMs > 5000) {
            seekTo(0)
            updatePlaybackState()
            showNotification()
            return
        }
        if (QueueManager.moveToPreviousTrack(this)) {
            val previousTrack = QueueManager.getCurrentTrack()
            if (previousTrack?.path != null) {
                Intent("com.arotter.music.TRACK_CHANGED").apply {
                    setPackage(applicationContext.packageName)
                }.also { sendBroadcast(it) }
                playTrack(previousTrack.path)
            } else {
                Toast.makeText(this, "Предыдущий трек недоступен", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Это первый трек", Toast.LENGTH_SHORT).show()
        }
    }

    fun seekTo(position: Int) {
        val duration = getDuration()
        Log.d("PLAY_COUNT", "SEEK: position=$position, duration=$duration, 90%=${duration * 0.9f}")
        mediaPlayer?.seekTo(position)
        hasCountedAsPlayed = false
        hasSeekedThisTrack = true
        lastCheckedPosition = -1
        Log.d("PLAY_COUNT", "Reset counted flag (any seek)")
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun setOnUpdateListener(listener: (Int, Int) -> Unit) {
        updateListener = listener
    }

    private fun updateMediaSessionMetadata() {
        val track = currentTrack ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val albumArt = loadAlbumArt()
            withContext(Dispatchers.Main) {
                val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist ?: "Unknown Artist")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.albumName ?: "Unknown Album")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration().toLong())
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                    .build()
                mediaSession.setMetadata(metadata)
            }
        }
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, getCurrentPosition().toLong(), 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun checkAndIncrementPlayCount() {
        if (hasCountedAsPlayed || currentTrackPath == null || !isPlaying) {
            Log.d("PLAY_COUNT", "Early return: counted=$hasCountedAsPlayed, path=$currentTrackPath, playing=$isPlaying")
            return
        }
        if (hasSeekedThisTrack) {
            Log.d("PLAY_COUNT", "Skip count due to seek during track")
            return
        }
        val duration = getDuration()
        val currentPos = getCurrentPosition()
        if (duration <= 0) return
        val playedPercentage = (currentPos.toFloat() / duration.toFloat()) * 100
        if (lastCheckedPosition == -1) {
            lastCheckedPosition = currentPos
            Log.d("PLAY_COUNT", "Skip after seek; set lastPos=$currentPos")
            return
        }
        val positionDiff = currentPos - lastCheckedPosition
        val isNaturalProgress = positionDiff in 100..2000
        lastCheckedPosition = currentPos
        if (playedPercentage >= 90f && isNaturalProgress) {
            hasCountedAsPlayed = true
            Log.d("PLAY_COUNT", "COUNTED!")
            ListeningStats.incrementPlayCount(this, currentTrackPath!!)
            try {
                val ct = currentTrack
                var genre: String? = null
                currentTrackPath?.let {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(it)
                    genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    r.release()
                }
                PlayHistory.append(
                    this,
                    PlayHistory.PlayEvent(
                        timestamp = System.currentTimeMillis(),
                        trackPath = ct?.path,
                        trackName = ct?.name,
                        artist = ct?.artist,
                        albumName = ct?.albumName,
                        genre = genre,
                        percent = 100f
                    )
                )
            } catch (_: Exception) {}
            Intent("com.arotter.music.STATS_UPDATED").apply {
                setPackage(applicationContext.packageName)
            }.also { sendBroadcast(it) }
        }
    }

    private fun startPositionUpdates() {
        CoroutineScope(Dispatchers.Main).launch {
            while (mediaPlayer != null && isPlaying) {
                val current = getCurrentPosition()
                val duration = getDuration()
                currentPosition = current
                updateListener?.invoke(current, duration)
                checkAndIncrementPlayCount()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun stopService() {
        try {
            if (currentTrackPath != null && mediaPlayer != null && !hasSeekedThisTrack && !hasCountedAsPlayed) {
                val dur = getDuration()
                val pos = getCurrentPosition()
                if (dur > 0) {
                    val pct = ((pos.toFloat() / dur.toFloat()) * 100f).coerceIn(0f, 100f)
                    val ct = currentTrack
                    var genre: String? = null
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(currentTrackPath)
                        genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        r.release()
                    } catch (_: Exception) {}
                    PlayHistory.append(
                        this,
                        PlayHistory.PlayEvent(
                            timestamp = System.currentTimeMillis(),
                            trackPath = ct?.path,
                            trackName = ct?.name,
                            artist = ct?.artist,
                            albumName = ct?.albumName,
                            genre = genre,
                            percent = pct
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        Intent("com.arotter.music.STATS_UPDATED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }

        abandonAudioFocus()

        Intent("com.arotter.music.TRACK_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }

        Intent("com.arotter.music.PLAYBACK_STATE_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(playbackModeReceiver)
        } catch (e: Exception) {
            // Игнорируем, если не был зарегистрирован
        }
        mediaSession.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = loadAlbumArt()
            val notification = buildNotification(bitmap)
            withContext(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun loadAlbumArt(): Bitmap? {
        try {
            currentTrack?.let { track ->
                val cacheKey = "notif_art_${track.path ?: track.albumId ?: track.name ?: "unknown"}"
                DiskImageCache.getBitmap(cacheKey)?.let { return it }
                val retriever = MediaMetadataRetriever()
                track.path?.let { retriever.setDataSource(it) }
                val data = retriever.embeddedPicture
                if (data != null) {
                    retriever.release()
                    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bmp != null) DiskImageCache.putBitmap(cacheKey, bmp)
                    return bmp
                }
                retriever.release()
                track.albumId?.let { albumId ->
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                    contentResolver.openInputStream(uri)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        if (bmp != null) DiskImageCache.putBitmap(cacheKey, bmp)
                        return bmp
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun buildNotification(albumArt: Bitmap?): Notification {
        val track = currentTrack
        val modeIcon = when (playbackMode) {
            "REPEAT_ONE" -> R.drawable.ic_repeat_on
            "SHUFFLE" -> R.drawable.ic_shuffle
            "STOP_AFTER" -> R.drawable.ic_stop_after
            else -> R.drawable.ic_repeat_off
        }

        val modeIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).setAction(ACTION_SET_MODE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).setAction(if (isPlaying) ACTION_PAUSE else ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifW = try { resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width) }
        catch (_: Exception) { (64 * resources.displayMetrics.density).toInt() }

        val notifH = try { resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height) }
        catch (_: Exception) { (64 * resources.displayMetrics.density).toInt() }

        val radiusPx = 12f * resources.displayMetrics.density
        val largeIcon: Bitmap? = try {
            if (albumArt != null) {
                createRoundedBitmapCropped(albumArt, notifW, notifH, radiusPx)
            } else {
                val drawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_album_placeholder)
                drawable?.let {
                    val ph = drawableToSizedBitmap(it, notifW, notifH)
                    createRoundedBitmapCropped(ph, notifW, notifH, radiusPx)
                }
            }
        } catch (_: Exception) { null }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track?.name ?: "Unknown Track")
            .setContentText(track?.artist ?: "Unknown Artist")
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(largeIcon)
            .setContentIntent(contentIntent)
            .addAction(modeIcon, "Mode", modeIntent)
            .addAction(R.drawable.ic_previous_m, "Previous", previousIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause_m else R.drawable.ic_play_m,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(R.drawable.ic_next_m, "Next", nextIntent)
            .addAction(R.drawable.ic_close, "Close", closeIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .build()
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        val duckingEnabled = prefs.getBoolean("enable_ducking", true)
        val actionLoss = prefs.getString("duck_action_loss", "duck") ?: "duck"
        val actionLossTransient = prefs.getString("duck_action_loss_transient", "duck") ?: "duck"
        val actionCanDuck = prefs.getString("duck_action_can_duck", "ignore") ?: "ignore"
        val level = (prefs.getFloat("duck_level", 0.3f)).coerceIn(0.1f, 1.0f)

        fun applyAction(action: String, isTransient: Boolean) {
            when (action) {
                "ignore" -> Unit
                "duck" -> if (duckingEnabled) { setPlayerVolume(level); isDucked = true }
                "pause" -> {
                    if (isTransient) pausedByFocusTransient = true
                    pauseMusic()
                }
            }
        }

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isDucked) {
                    setPlayerVolume(1.0f)
                    isDucked = false
                }
                if (pausedByFocusTransient) {
                    pausedByFocusTransient = false
                    resumeMusic()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                applyAction(actionLossTransient, isTransient = true)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocusTransient = false
                applyAction(actionLoss, isTransient = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                applyAction(actionCanDuck, isTransient = true)
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .build()
                audioFocusRequest = afr
                audioManager.requestAudioFocus(afr)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (_: Exception) {}
    }

    private fun setPlayerVolume(vol: Float) {
        try { mediaPlayer?.setVolume(vol, vol) } catch (_: Exception) {}
    }

    private fun drawableToSizedBitmap(drawable: android.graphics.drawable.Drawable, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    private fun createRoundedBitmapCropped(src: Bitmap, outW: Int, outH: Int, radiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(outW.coerceAtLeast(1), outH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        val scale = max(outW.toFloat() / src.width, outH.toFloat() / src.height)
        val dx = (outW - src.width * scale) / 2f
        val dy = (outH - src.height * scale) / 2f
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        val shader = android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        val rect = android.graphics.RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        return output
    }
}