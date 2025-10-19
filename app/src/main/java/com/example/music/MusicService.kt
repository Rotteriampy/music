package com.example.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import android.util.Log

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val NOTIFICATION_ID = 1

        const val ACTION_PLAY = "com.example.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.music.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.music.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.music.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.music.ACTION_STOP"
        const val ACTION_CLOSE = "com.example.music.ACTION_CLOSE"

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
    private var lastCheckedPosition = 0 // Добавьте эту строку

    private val playbackModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            playbackMode = intent?.getStringExtra("playback_mode") ?: "NORMAL"
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
        createNotificationChannel()

        // Загружаем режим воспроизведения
        val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        playbackMode = prefs.getString("playback_mode", "NORMAL") ?: "NORMAL"

        // Регистрируем receiver для изменения режима
        val filter = IntentFilter("com.example.music.PLAYBACK_MODE_CHANGED")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            playbackModeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Создаём MediaSession для интеграции с экраном блокировки
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumeMusic()
                }

                override fun onPause() {
                    pauseMusic()
                }

                override fun onSkipToNext() {
                    playNext()
                }

                override fun onSkipToPrevious() {
                    playPrevious()
                }

                override fun onStop() {
                    stopService()
                }
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
            else -> {
                val trackPath = intent?.getStringExtra("TRACK_PATH")
                if (trackPath != null) {
                    playTrack(trackPath)
                }
            }
        }
        return START_STICKY
    }

    fun playTrack(path: String) {
        if (currentTrackPath == path && mediaPlayer != null) {
            resumeMusic()
            return
        }
        currentTrackPath = path
        currentTrack = QueueManager.getCurrentTrack()
        hasCountedAsPlayed = false
        lastCheckedPosition = 0 // Добавьте эту строку

        // Broadcast immediately so UI (lists/queue) updates highlighting without delay
        Intent("com.example.music.TRACK_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                start()
                MusicService.isPlaying = true

                setOnCompletionListener {
                    checkAndIncrementPlayCount()

                    when (playbackMode) {
                        "REPEAT_ONE" -> {
                            // Повторяем текущий трек
                            seekTo(0)
                            start()
                        }
                        "SHUFFLE" -> {
                            // Случайный трек из очереди
                            val queue = QueueManager.getCurrentQueue()
                            if (queue.size > 1) {
                                val randomIndex = (0 until queue.size).random()
                                QueueManager.initializeQueueFromPosition(this@MusicService, queue, randomIndex)
                                val randomTrack = QueueManager.getCurrentTrack()
                                if (randomTrack?.path != null) {
                                    playTrack(randomTrack.path)
                                }
                            } else {
                                stopSelf()
                            }
                        }
                        "STOP_AFTER" -> {
                            // Останавливаемся после текущего трека
                            stopSelf()
                        }
                        "REPEAT_ALL" -> {
                            // Переходим к следующему, если очередь закончилась - начинаем сначала
                            if (QueueManager.moveToNextTrack(this@MusicService)) {
                                val nextTrack = QueueManager.getCurrentTrack()
                                if (nextTrack?.path != null) {
                                    playTrack(nextTrack.path)
                                } else {
                                    val queue = QueueManager.getCurrentQueue()
                                    if (queue.isNotEmpty()) {
                                        QueueManager.initializeQueueFromPosition(this@MusicService, queue, 0)
                                        val firstTrack = QueueManager.getCurrentTrack()
                                        if (firstTrack?.path != null) {
                                            playTrack(firstTrack.path)
                                        }
                                    }
                                }
                            } else {
                                val queue = QueueManager.getCurrentQueue()
                                if (queue.isNotEmpty()) {
                                    QueueManager.initializeQueueFromPosition(this@MusicService, queue, 0)
                                    val firstTrack = QueueManager.getCurrentTrack()
                                    if (firstTrack?.path != null) {
                                        playTrack(firstTrack.path)
                                    }
                                }
                            }
                        }
                        else -> {
                            // NORMAL - обычное воспроизведение
                            if (QueueManager.moveToNextTrack(this@MusicService)) {
                                val nextTrack = QueueManager.getCurrentTrack()
                                if (nextTrack?.path != null) {
                                    playTrack(nextTrack.path)
                                } else {
                                    stopSelf()
                                }
                            } else {
                                stopSelf()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        updateMediaSessionMetadata()
        updatePlaybackState()
        showNotification()
        startPositionUpdates()
    }

    fun pauseMusic() {
        mediaPlayer?.pause()
        isPlaying = false
        updatePlaybackState()
        showNotification()
        Intent("com.example.music.PLAYBACK_STATE_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }
    }

    fun resumeMusic() {
        mediaPlayer?.start()
        isPlaying = true
        startPositionUpdates() // Добавьте этот вызов
        updatePlaybackState()
        showNotification()
        Intent("com.example.music.PLAYBACK_STATE_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }
    }

    private fun playNext() {
        if (QueueManager.moveToNextTrack(this)) {
            val nextTrack = QueueManager.getCurrentTrack()
            if (nextTrack?.path != null) {
                // Notify UI about current index change instantly
                Intent("com.example.music.TRACK_CHANGED").apply {
                    setPackage(applicationContext.packageName)
                }.also { sendBroadcast(it) }
                playTrack(nextTrack.path)
            } else {
                Toast.makeText(this, "Следующий трек недоступен", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Это последний трек", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playPrevious() {
        // Если воспроизведение ушло дальше первых 5 секунд, перематываем в начало текущего трека
        val positionMs = getCurrentPosition()
        if (positionMs > 5000) {
            seekTo(0)
            updatePlaybackState()
            showNotification()
            return
        }

        // Иначе переходим к предыдущему треку
        if (QueueManager.moveToPreviousTrack(this)) {
            val previousTrack = QueueManager.getCurrentTrack()
            if (previousTrack?.path != null) {
                Intent("com.example.music.TRACK_CHANGED").apply {
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

        // При любой перемотке сбрасываем счетчик
        hasCountedAsPlayed = false

        // Устанавливаем позицию, НО помечаем как "после перемотки"
        lastCheckedPosition = -1 // Специальное значение = "только что перемотали"

        Log.d("PLAY_COUNT", "Reset counted flag (any seek)")
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

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
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

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

        val duration = getDuration()
        val currentPos = getCurrentPosition()
        if (duration <= 0) return

        val playedPercentage = (currentPos.toFloat() / duration.toFloat()) * 100

        // Если только что была перемотка — пропустить этот тик
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
            Log.d("PLAY_COUNT", "✅ COUNTED!")
            ListeningStats.incrementPlayCount(this, currentTrackPath!!)
            // Запись события в историю для графиков
            try {
                val ct = currentTrack
                var genre: String? = null
                try {
                    if (currentTrackPath != null) {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(currentTrackPath)
                        genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        r.release()
                    }
                } catch (_: Exception) {}
                PlayHistory.append(
                    this,
                    PlayHistory.PlayEvent(
                        timestamp = System.currentTimeMillis(),
                        trackPath = ct?.path,
                        trackName = ct?.name,
                        artist = ct?.artist,
                        albumName = ct?.albumName,
                        genre = genre
                    )
                )
            } catch (_: Exception) { }

            Intent("com.example.music.STATS_UPDATED").apply {
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
        checkAndIncrementPlayCount()

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        currentTrack = null
        currentTrackPath = null
        hasCountedAsPlayed = false

        mediaSession.isActive = false

        Intent("com.example.music.TRACK_CHANGED").apply {
            setPackage(applicationContext.packageName)
        }.also { sendBroadcast(it) }
        Intent("com.example.music.PLAYBACK_STATE_CHANGED").apply {
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
            // Receiver не был зарегистрирован
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
                val retriever = MediaMetadataRetriever()
                track.path?.let { retriever.setDataSource(it) }
                val data = retriever.embeddedPicture
                if (data != null) {
                    retriever.release()
                    return BitmapFactory.decodeByteArray(data, 0, data.size)
                }
                retriever.release()

                track.albumId?.let { albumId ->
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        albumId
                    )
                    contentResolver.openInputStream(uri)?.use { input ->
                        return BitmapFactory.decodeStream(input)
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

        val playPauseIntent = if (isPlaying) {
            PendingIntent.getService(
                this,
                0,
                Intent(this, MusicService::class.java).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                0,
                Intent(this, MusicService::class.java).setAction(ACTION_PLAY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val nextIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusicService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusicService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusicService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track?.name ?: "Unknown Track")
            .setContentText(track?.artist ?: "Unknown Artist")
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(albumArt)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_previous_m, "Previous", previousIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
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
}