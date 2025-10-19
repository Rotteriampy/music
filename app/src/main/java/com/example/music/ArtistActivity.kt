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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import android.widget.EditText
import android.widget.Button
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog

class ArtistActivity : AppCompatActivity() {

    private lateinit var artistNameText: TextView
    private lateinit var artistTracksList: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnPlayArtist: ImageButton
    private lateinit var btnShuffleArtist: ImageButton
    private lateinit var artistRootLayout: LinearLayout

    private var artistName: String? = null
    private lateinit var trackAdapter: TrackAdapter
    private val artistTracks = mutableListOf<Track>()

    private lateinit var btnArtistMore: ImageButton
    private var customArtistCover: Uri? = null
    private var customArtistName: String? = null
    private var editDialogCoverView: ImageView? = null

    private val artistCoverLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val intent = Intent(this, CropImageActivity::class.java)
            intent.putExtra("imageUri", it)
            artistCropLauncher.launch(intent)
        }
    }

    private val artistCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                customArtistCover = uri
                editDialogCoverView?.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist)

        artistRootLayout = findViewById(R.id.artistRootLayout)
        artistNameText = findViewById(R.id.artistNameText)
        artistTracksList = findViewById(R.id.artistTracksList)
        btnBack = findViewById(R.id.btnBackArtist)
        btnPlayArtist = findViewById(R.id.btnPlayArtist)
        btnShuffleArtist = findViewById(R.id.btnShuffleArtist)
        btnArtistMore = findViewById(R.id.btnArtistMore)
        btnArtistMore.setOnClickListener { showArtistMoreMenu(it) }

        artistTracksList.layoutManager = LinearLayoutManager(this)

        artistName = intent.getStringExtra("ARTIST_NAME")

        restoreColor()
        loadCustomArtistData()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        if (artistName != null) {
            artistNameText.text = artistName
            loadArtistTracks()
            trackAdapter = TrackAdapter(artistTracks, isFromPlaylist = false)
            artistTracksList.adapter = trackAdapter
        } else {
            finish()
        }

        btnBack.setOnClickListener { finish() }
        btnPlayArtist.setOnClickListener { playArtist() }
        btnShuffleArtist.setOnClickListener { shuffleAndPlayArtist() }
    }

    private fun loadArtistTracks() {
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

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ARTIST} = ?"
        val selectionArgs = arrayOf(artistName)

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

            artistTracks.clear()
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
                    artistTracks.add(
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

    private fun playArtist() {
        if (artistTracks.isEmpty()) {
            Toast.makeText(this, "Нет треков исполнителя", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = artistTracks.filter { it.path != null && File(it.path).exists() }
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

    private fun shuffleAndPlayArtist() {
        if (artistTracks.isEmpty()) {
            Toast.makeText(this, "Нет треков исполнителя", Toast.LENGTH_SHORT).show()
            return
        }

        val availableTracks = artistTracks.filter { it.path != null && File(it.path).exists() }
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
        artistRootLayout.background = color
    }

    override fun onResume() {
        super.onResume()
        trackAdapter.notifyDataSetChanged()
    }

    private fun showArtistMoreMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.artist_more_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit_artist -> {
                    showEditArtistDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditArtistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_artist, null)
        val coverImageView = dialogView.findViewById<ImageView>(R.id.editArtistCover)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editArtistName)
        val selectCoverButton = dialogView.findViewById<Button>(R.id.btnSelectArtistCover)

        editDialogCoverView = coverImageView

        nameEditText.setText(customArtistName ?: artistName)

        if (customArtistCover != null) {
            coverImageView.setImageURI(customArtistCover)
        } else {
            coverImageView.setImageResource(R.drawable.ic_album_placeholder)
        }

        selectCoverButton.setOnClickListener {
            artistCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Редактировать исполнителя")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = nameEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    customArtistName = newName
                    saveCustomArtistData()
                    updateArtistUI()
                }
                editDialogCoverView = null
            }
            .setNegativeButton("Отмена") { _, _ ->
                editDialogCoverView = null
            }
            .create()

        dialog.show()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            ContextCompat.getColor(this, android.R.color.black)
        ))

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, android.R.color.white)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, android.R.color.white)
        )

        val titleView = dialog.findViewById<TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun saveCustomArtistData() {
        val prefs = getSharedPreferences("custom_artists", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("artist_${artistName}_name", customArtistName)
        editor.putString("artist_${artistName}_cover", customArtistCover?.toString())
        editor.apply()
    }

    private fun loadCustomArtistData() {
        val prefs = getSharedPreferences("custom_artists", MODE_PRIVATE)
        customArtistName = prefs.getString("artist_${artistName}_name", null)
        val coverUri = prefs.getString("artist_${artistName}_cover", null)
        customArtistCover = if (coverUri != null) Uri.parse(coverUri) else null
        updateArtistUI()
    }

    private fun updateArtistUI() {
        artistNameText.text = customArtistName ?: artistName
    }
}