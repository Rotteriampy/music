package com.example.music

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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

class PlaylistActivity : AppCompatActivity() {

    private lateinit var playlistTracksList: RecyclerView
    private lateinit var playlistNameText: TextView
    private lateinit var btnDeletePlaylist: ImageButton
    private lateinit var btnAddTrack: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnReorder: ImageButton
    private lateinit var btnPlayPlaylist: ImageButton
    private lateinit var btnShufflePlaylist: ImageButton
    private lateinit var playlistRootLayout: LinearLayout

    private var playlistId: String? = null
    private var playlist: Playlist? = null
    private lateinit var trackAdapter: TrackAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private var isReorderMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        playlistRootLayout = findViewById(R.id.playlistRootLayout)
        playlistNameText = findViewById(R.id.playlistNameText)
        playlistTracksList = findViewById(R.id.playlistTracksList)
        btnDeletePlaylist = findViewById(R.id.btnDeletePlaylist)
        btnAddTrack = findViewById(R.id.btnAddTrack)
        btnBack = findViewById(R.id.btnBack)
        btnReorder = findViewById(R.id.btnReorder)
        btnPlayPlaylist = findViewById(R.id.btnPlayPlaylist)
        btnShufflePlaylist = findViewById(R.id.btnShufflePlaylist)

        playlistTracksList.layoutManager = LinearLayoutManager(this)

        playlistId = intent.getStringExtra("PLAYLIST_ID")
        playlist = PlaylistManager.getPlaylists().find { it.id == playlistId }

        restoreColor()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        val currentPlaylist = playlist
        if (currentPlaylist != null) {
            playlistNameText.text = currentPlaylist.name
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
        btnPlayPlaylist.setOnClickListener { playPlaylist() }
        btnShufflePlaylist.setOnClickListener { shuffleAndPlayPlaylist() }
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

    override fun onResume() {
        super.onResume()
        restoreColor()
        refreshPlaylist()
        trackAdapter.notifyDataSetChanged()
    }

    private fun refreshPlaylist() {
        if (!isReorderMode) {
            playlist = PlaylistManager.getPlaylists().find { it.id == playlistId }
            val currentPlaylist = playlist
            if (currentPlaylist != null && ::trackAdapter.isInitialized) {
                trackAdapter.updateTracks(currentPlaylist.tracks.toMutableList())
            }
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
        playlistRootLayout.background = color
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

        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = DialogTrackAdapter(allTracks)
        recyclerView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle("Добавить треки")
            .setView(dialogView)
            .setPositiveButton("Добавить") { _, _ ->
                val selectedTracks = adapter.getSelectedTracks()
                if (selectedTracks.isEmpty()) {
                    Toast.makeText(this, "Не выбрано ни одного трека", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
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
                    }

                    val message = if (addedCount > 0) {
                        "Добавлено треков: $addedCount"
                    } else {
                        "Все выбранные треки уже в плейлисте"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()
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
}