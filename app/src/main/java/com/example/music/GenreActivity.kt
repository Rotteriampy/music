package com.example.music

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.os.Build

class GenreActivity : AppCompatActivity() {

    private lateinit var genreNameText: TextView
    private lateinit var genreTracksList: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnPlayGenre: ImageButton
    private lateinit var btnShuffleGenre: ImageButton
    private lateinit var genreRootLayout: LinearLayout

    private var genreName: String? = null
    private lateinit var trackAdapter: TrackAdapter
    private val genreTracks = mutableListOf<Track>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_genre)

        genreRootLayout = findViewById(R.id.genreRootLayout)
        genreNameText = findViewById(R.id.genreNameText)
        genreTracksList = findViewById(R.id.genreTracksList)
        btnBack = findViewById(R.id.btnBackGenre)
        btnPlayGenre = findViewById(R.id.btnPlayGenre)
        btnShuffleGenre = findViewById(R.id.btnShuffleGenre)

        genreTracksList.layoutManager = LinearLayoutManager(this)

        genreName = intent.getStringExtra("GENRE_NAME")

        restoreColor()

        if (genreName != null) {
            genreNameText.text = genreName
            loadGenreTracks()
            trackAdapter = TrackAdapter(genreTracks, isFromPlaylist = false)
            genreTracksList.adapter = trackAdapter
        } else {
            finish()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        btnBack.setOnClickListener { finish() }
        btnPlayGenre.setOnClickListener { playGenre() }
        btnShuffleGenre.setOnClickListener { shuffleAndPlayGenre() }
    }

    private fun loadGenreTracks() {
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

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
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

            genreTracks.clear()
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
                    // Извлекаем жанр из метаданных
                    var trackGenre = "Unknown"
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(path)
                        trackGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "Unknown"
                        retriever.release()
                    } catch (e: Exception) {
                        // Используем "Unknown"
                    }

                    if (trackGenre == genreName) {
                        genreTracks.add(
                            Track(
                                id = id,
                                name = title,
                                artist = artist,
                                albumId = albumId,
                                albumName = album,
                                genre = trackGenre,
                                path = path,
                                duration = duration,
                                dateModified = dateModified
                            )
                        )
                    }
                }
            }
        }
    }

    private fun playGenre() {
        if (genreTracks.isEmpty()) {
            Toast.makeText(this, "Нет треков в жанре", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = genreTracks.filter { it.path != null && File(it.path).exists() }
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

    private fun shuffleAndPlayGenre() {
        if (genreTracks.isEmpty()) {
            Toast.makeText(this, "Нет треков в жанре", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = genreTracks.filter { it.path != null && File(it.path).exists() }
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
        val sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val scheme = sharedPreferences.getInt("color_scheme", 0)
        val color = when (scheme) {
            1 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.darker_gray))
            2 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            3 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            else -> ColorDrawable(ContextCompat.getColor(this, android.R.color.black))
        }
        genreRootLayout.background = color
    }

    override fun onResume() {
        super.onResume()
        // Обновляем адаптер при возврате на экран
        trackAdapter.notifyDataSetChanged()
    }
}