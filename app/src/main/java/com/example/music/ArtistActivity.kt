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
import androidx.recyclerview.widget.ItemTouchHelper
import android.widget.RadioGroup
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

class ArtistActivity : AppCompatActivity() {

    private lateinit var artistNameText: TextView
    private lateinit var artistTracksList: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnPlayArtist: ImageButton
    private lateinit var btnShuffleArtist: ImageButton
    private lateinit var artistRootLayout: LinearLayout
    private lateinit var artistCoverImage: ImageView
    private lateinit var artistStatsText: TextView
    private lateinit var btnSortArtist: ImageButton
    private lateinit var btnReorderTracks: ImageButton
    private var isReorderMode = false
    private var itemTouchHelper: ItemTouchHelper? = null

    private var artistName: String? = null
    private lateinit var trackAdapter: TrackAdapter
    private val artistTracks = mutableListOf<Track>()

    private var sortType: Int = 0
    private var sortAscending: Boolean = false

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
        artistCoverImage = findViewById(R.id.artistCoverImage)
        artistStatsText = findViewById(R.id.artistStatsText)
        btnSortArtist = findViewById(R.id.btnSortArtist)
        btnReorderTracks = findViewById(R.id.btnReorderTracks)

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
            updateStats()
            loadArtistCover(artistCoverImage)
            setupReorder()
        } else {
            finish()
        }

        btnBack.setOnClickListener { finish() }
        btnPlayArtist.setOnClickListener { playArtist() }
        btnShuffleArtist.setOnClickListener { shuffleAndPlayArtist() }
        btnSortArtist.setOnClickListener { showSortMenu(it) }
        btnReorderTracks.setOnClickListener { toggleReorderMode() }
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
                R.id.menu_search -> {
                    showTrackSearchDialog()
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

    private fun showSortMenu(view: View) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sort, null)

        val sortTypeGroup = dialogView.findViewById<RadioGroup>(R.id.sortTypeGroup)
        val btnSortAsc = dialogView.findViewById<ImageButton>(R.id.btnSortAsc)
        val btnSortDesc = dialogView.findViewById<ImageButton>(R.id.btnSortDesc)
        val sortDirectionText = dialogView.findViewById<TextView>(R.id.sortDirectionText)
        val btnApplySort = dialogView.findViewById<Button>(R.id.btnApplySort)
        val btnCancelSort = dialogView.findViewById<Button>(R.id.btnCancelSort)

        when (sortType) {
            0 -> sortTypeGroup.check(R.id.sortByDate)
            1 -> sortTypeGroup.check(R.id.sortByName)
            2 -> sortTypeGroup.check(R.id.sortByArtist)
            3 -> sortTypeGroup.check(R.id.sortByDuration)
        }

        sortDirectionText.text = if (sortAscending) "↑" else "↓"

        btnSortAsc.setOnClickListener {
            sortAscending = true
            sortDirectionText.text = "↑"
        }

        btnSortDesc.setOnClickListener {
            sortAscending = false
            sortDirectionText.text = "↓"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnApplySort.setOnClickListener {
            sortType = when (sortTypeGroup.checkedRadioButtonId) {
                R.id.sortByDate -> 0
                R.id.sortByName -> 1
                R.id.sortByArtist -> 2
                R.id.sortByDuration -> 3
                else -> 0
            }
            applyArtistSort()
            dialog.dismiss()
        }

        btnCancelSort.setOnClickListener { dialog.dismiss() }

        val titleView = dialog.findViewById<TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        dialog.show()
    }

    private fun applyArtistSort() {
        when (sortType) {
            0 -> if (sortAscending) artistTracks.sortBy { it.dateModified } else artistTracks.sortByDescending { it.dateModified }
            1 -> if (sortAscending) artistTracks.sortBy { it.name.lowercase() } else artistTracks.sortByDescending { it.name.lowercase() }
            2 -> if (sortAscending) artistTracks.sortBy { (it.artist ?: "").lowercase() } else artistTracks.sortByDescending { (it.artist ?: "").lowercase() }
            3 -> if (sortAscending) artistTracks.sortBy { it.duration ?: 0L } else artistTracks.sortByDescending { it.duration ?: 0L }
        }
        trackAdapter.notifyDataSetChanged()
        updateStats()
    }

    private fun showSortOrderDialog(applySort: (Boolean) -> Unit) {
        val options = arrayOf("По возрастанию", "По убыванию")
        AlertDialog.Builder(this)
            .setTitle("Порядок сортировки")
            .setItems(options) { _, which ->
                val ascending = (which == 0)
                applySort(ascending)
                trackAdapter.notifyDataSetChanged()
                updateStats()
            }
            .show()
    }

    private fun updateStats() {
        val count = artistTracks.size
        val totalDuration = artistTracks.sumOf { it.duration ?: 0L }
        val hours = totalDuration / 3600000
        val minutes = (totalDuration % 3600000) / 60000
        val seconds = (totalDuration / 1000) % 60
        val durationText = if (hours > 0) "$hours:${minutes.toString().padStart(2,'0')}:${seconds.toString().padStart(2,'0')}" else "$minutes:${seconds.toString().padStart(2,'0')}"
        artistStatsText.text = "$count треков • $durationText"
    }

    private fun setupReorder() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from in artistTracks.indices && to in artistTracks.indices) {
                    val item = artistTracks.removeAt(from)
                    artistTracks.add(to, item)
                    trackAdapter.notifyItemMoved(from, to)
                    return true
                }
                return false
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
            override fun isLongPressDragEnabled(): Boolean = isReorderMode
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(artistTracksList)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        trackAdapter.isReorderMode = isReorderMode
        if (isReorderMode) {
            btnReorderTracks.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
        } else {
            btnReorderTracks.clearColorFilter()
        }
    }

    private fun showTrackSearchDialog() {
        val input = EditText(this)
        input.hint = "Поиск треков..."
        AlertDialog.Builder(this)
            .setTitle("Поиск")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isEmpty()) {
                    loadArtistTracks()
                    trackAdapter.updateTracks(artistTracks)
                } else {
                    val filtered = artistTracks.filter { it.name.contains(q, true) || (it.artist?.contains(q, true) == true) }
                    trackAdapter.updateTracks(filtered.toMutableList())
                }
                updateStats()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadArtistCover(imageView: ImageView) {
        // Unknown всегда плейсхолдер
        if (artistName?.equals("Unknown", ignoreCase = true) == true) {
            imageView.setImageResource(R.drawable.ic_album_placeholder)
            return
        }

        // Сначала используем кастомную обложку, если задана (чтобы совпадало со списком)
        customArtistCover?.let {
            imageView.setImageURI(it)
            return
        }

        var found = false
        for (t in artistTracks) {
            val p = t.path ?: continue
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(p)
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    imageView.setImageBitmap(bitmap)
                    found = true
                    break
                }
            } catch (_: Exception) { }
        }
        if (!found) {
            val name = (customArtistName ?: artistName ?: "").trim()
            if (name.equals("Unknown", ignoreCase = true) || name.isEmpty()) {
                imageView.setImageResource(R.drawable.ic_album_placeholder)
            } else {
                imageView.setImageBitmap(generateLetterCover(name))
            }
        }
    }

    private fun generateLetterCover(name: String): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)

        val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            textSize = size * 0.6f
        }
        val bounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, bounds)
        val x = (size - bounds.width()) / 2f - bounds.left
        val y = (size + bounds.height()) / 2f - bounds.bottom
        canvas.drawText(letter, x, y, paint)
        return bmp
    }
}