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

    private lateinit var btnGenreMore: ImageButton
    private var customGenreCover: Uri? = null
    private var customGenreName: String? = null
    private var editDialogCoverView: ImageView? = null
    private lateinit var genreCoverImage: ImageView
    private lateinit var genreStatsText: TextView
    private lateinit var btnSortGenre: ImageButton
    private lateinit var btnReorderTracks: ImageButton
    private var isReorderMode = false
    private var itemTouchHelper: ItemTouchHelper? = null
    private var sortType: Int = 0
    private var sortAscending: Boolean = false

    private val genreCoverLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val intent = Intent(this, CropImageActivity::class.java)
            intent.putExtra("imageUri", it)
            genreCropLauncher.launch(intent)
        }
    }

    private val genreCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                customGenreCover = uri
                editDialogCoverView?.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_genre)

        genreRootLayout = findViewById(R.id.genreRootLayout)
        genreNameText = findViewById(R.id.genreNameText)
        genreTracksList = findViewById(R.id.genreTracksList)
        btnBack = findViewById(R.id.btnBackGenre)
        btnPlayGenre = findViewById(R.id.btnPlayGenre)
        btnShuffleGenre = findViewById(R.id.btnShuffleGenre)
        btnGenreMore = findViewById(R.id.btnGenreMore)
        btnGenreMore.setOnClickListener { showGenreMoreMenu(it) }
        genreCoverImage = findViewById(R.id.genreCoverImage)
        genreStatsText = findViewById(R.id.genreStatsText)
        btnSortGenre = findViewById(R.id.btnSortGenre)
        btnReorderTracks = findViewById(R.id.btnReorderTracks)

        genreTracksList.layoutManager = LinearLayoutManager(this)

        genreName = intent.getStringExtra("GENRE_NAME")

        restoreColor()
        loadCustomGenreData()

        if (genreName != null) {
            genreNameText.text = genreName
            loadGenreTracks()
            trackAdapter = TrackAdapter(genreTracks, isFromPlaylist = false)
            genreTracksList.adapter = trackAdapter
            updateStats()
            loadGenreCover()
            setupReorder()
        } else {
            finish()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        btnBack.setOnClickListener { finish() }
        btnPlayGenre.setOnClickListener { playGenre() }
        btnShuffleGenre.setOnClickListener { shuffleAndPlayGenre() }
        btnSortGenre.setOnClickListener { showSortMenu(it) }
        btnReorderTracks.setOnClickListener { toggleReorderMode() }
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
        trackAdapter.notifyDataSetChanged()
    }

    private fun showGenreMoreMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.genre_more_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit_genre -> {
                    showEditGenreDialog()
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

    private fun showEditGenreDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_genre, null)
        val coverImageView = dialogView.findViewById<ImageView>(R.id.editGenreCover)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editGenreName)
        val selectCoverButton = dialogView.findViewById<Button>(R.id.btnSelectGenreCover)

        editDialogCoverView = coverImageView

        nameEditText.setText(customGenreName ?: genreName)

        if (customGenreCover != null) {
            coverImageView.setImageURI(customGenreCover)
        } else {
            coverImageView.setImageResource(R.drawable.ic_album_placeholder)
        }

        selectCoverButton.setOnClickListener {
            genreCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Редактировать жанр")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = nameEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    customGenreName = newName
                    saveCustomGenreData()
                    updateGenreUI()
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

    private fun saveCustomGenreData() {
        val prefs = getSharedPreferences("custom_genres", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("genre_${genreName}_name", customGenreName)
        editor.putString("genre_${genreName}_cover", customGenreCover?.toString())
        editor.apply()
    }

    private fun loadCustomGenreData() {
        val prefs = getSharedPreferences("custom_genres", MODE_PRIVATE)
        customGenreName = prefs.getString("genre_${genreName}_name", null)
        val coverUri = prefs.getString("genre_${genreName}_cover", null)
        customGenreCover = if (coverUri != null) Uri.parse(coverUri) else null
        updateGenreUI()
    }

    private fun updateGenreUI() {
        genreNameText.text = customGenreName ?: genreName
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
            applyGenreSort()
            dialog.dismiss()
        }

        btnCancelSort.setOnClickListener { dialog.dismiss() }

        val titleView = dialog.findViewById<TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        dialog.show()
    }

    private fun applyGenreSort() {
        when (sortType) {
            0 -> if (sortAscending) genreTracks.sortBy { it.dateModified } else genreTracks.sortByDescending { it.dateModified }
            1 -> if (sortAscending) genreTracks.sortBy { it.name.lowercase() } else genreTracks.sortByDescending { it.name.lowercase() }
            2 -> if (sortAscending) genreTracks.sortBy { (it.artist ?: "").lowercase() } else genreTracks.sortByDescending { (it.artist ?: "").lowercase() }
            3 -> if (sortAscending) genreTracks.sortBy { it.duration ?: 0L } else genreTracks.sortByDescending { it.duration ?: 0L }
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
        val count = genreTracks.size
        val totalDuration = genreTracks.sumOf { it.duration ?: 0L }
        val hours = totalDuration / 3600000
        val minutes = (totalDuration % 3600000) / 60000
        val seconds = (totalDuration / 1000) % 60
        val durationText = if (hours > 0) "$hours:${minutes.toString().padStart(2,'0')}:${seconds.toString().padStart(2,'0')}" else "$minutes:${seconds.toString().padStart(2,'0')}"
        genreStatsText.text = "$count треков • $durationText"
    }

    private fun setupReorder() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from in genreTracks.indices && to in genreTracks.indices) {
                    val item = genreTracks.removeAt(from)
                    genreTracks.add(to, item)
                    trackAdapter.notifyItemMoved(from, to)
                    return true
                }
                return false
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
            override fun isLongPressDragEnabled(): Boolean = isReorderMode
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(genreTracksList)
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
                    loadGenreTracks()
                    trackAdapter.updateTracks(genreTracks)
                } else {
                    val filtered = genreTracks.filter { it.name.contains(q, true) || (it.artist?.contains(q, true) == true) }
                    trackAdapter.updateTracks(filtered.toMutableList())
                }
                updateStats()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadGenreCover() {
        // Для Unknown всегда плейсхолдер
        if (genreName?.equals("Unknown", ignoreCase = true) == true) {
            genreCoverImage.setImageResource(R.drawable.ic_album_placeholder)
            return
        }

        // Сначала используем кастомную обложку, если задана (чтобы совпадало со списком)
        customGenreCover?.let {
            genreCoverImage.setImageURI(it)
            return
        }

        val firstTrack = genreTracks.firstOrNull()
        if (firstTrack?.path != null) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(firstTrack.path)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    genreCoverImage.setImageBitmap(bitmap)
                } else {
                    genreCoverImage.setImageResource(R.drawable.ic_album_placeholder)
                }
                retriever.release()
            } catch (e: Exception) {
                genreCoverImage.setImageResource(R.drawable.ic_album_placeholder)
            }
        } else {
            genreCoverImage.setImageResource(R.drawable.ic_album_placeholder)
        }
    }
}