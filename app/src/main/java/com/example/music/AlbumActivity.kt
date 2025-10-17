package com.example.music

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
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

class AlbumActivity : AppCompatActivity() {

    private lateinit var albumNameText: TextView
    private lateinit var albumTracksList: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnPlayAlbum: ImageButton
    private lateinit var btnShuffleAlbum: ImageButton
    private lateinit var albumRootLayout: LinearLayout

    private var albumName: String? = null
    private lateinit var trackAdapter: TrackAdapter
    private val albumTracks = mutableListOf<Track>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)

        albumRootLayout = findViewById(R.id.albumRootLayout)
        albumNameText = findViewById(R.id.albumNameText)
        albumTracksList = findViewById(R.id.albumTracksList)
        btnBack = findViewById(R.id.btnBackAlbum)
        btnPlayAlbum = findViewById(R.id.btnPlayAlbum)
        btnShuffleAlbum = findViewById(R.id.btnShuffleAlbum)

        albumTracksList.layoutManager = LinearLayoutManager(this)

        albumName = intent.getStringExtra("ALBUM_NAME")

        restoreColor()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        if (albumName != null) {
            albumNameText.text = albumName
            loadAlbumTracks()
            trackAdapter = TrackAdapter(albumTracks, isFromPlaylist = false)
            albumTracksList.adapter = trackAdapter
        } else {
            finish()
        }

        btnBack.setOnClickListener { finish() }
        btnPlayAlbum.setOnClickListener { playAlbum() }
        btnShuffleAlbum.setOnClickListener { shuffleAndPlayAlbum() }
    }

    private fun loadAlbumTracks() {
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

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ALBUM} = ?"
        val selectionArgs = arrayOf(albumName)

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

            albumTracks.clear()
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
                    albumTracks.add(
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

    private fun playAlbum() {
        if (albumTracks.isEmpty()) {
            Toast.makeText(this, "Альбом пуст", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = albumTracks.filter { it.path != null && File(it.path).exists() }
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

    private fun shuffleAndPlayAlbum() {
        if (albumTracks.isEmpty()) {
            Toast.makeText(this, "Альбом пуст", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = albumTracks.filter { it.path != null && File(it.path).exists() }
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
        albumRootLayout.background = color
    }

    override fun onResume() {
        super.onResume()
        // Обновляем адаптер при возврате на экран
        trackAdapter.notifyDataSetChanged()
    }
}