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

class AlbumActivity : AppCompatActivity() {

    private lateinit var albumNameText: TextView
    private lateinit var albumTracksList: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnPlayAlbum: ImageButton
    private lateinit var btnShuffleAlbum: ImageButton
    private lateinit var albumRootLayout: LinearLayout
    private var editDialogCoverView: ImageView? = null
    private lateinit var albumCoverImage: ImageView
    private lateinit var albumStatsText: TextView
    private lateinit var btnSortAlbum: ImageButton
    private lateinit var btnReorderTracks: ImageButton
    private var isReorderMode = false
    private var itemTouchHelper: ItemTouchHelper? = null

    private var albumName: String? = null
    private lateinit var trackAdapter: TrackAdapter
    private val albumTracks = mutableListOf<Track>()

    private var sortType: Int = 0
    private var sortAscending: Boolean = false

    private lateinit var btnAlbumMore: ImageButton
    private var customAlbumCover: Uri? = null
    private var customAlbumName: String? = null

    private val albumCoverLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val intent = Intent(this, CropImageActivity::class.java)
            intent.putExtra("imageUri", it)
            albumCropLauncher.launch(intent)
        }
    }

    private val albumCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                customAlbumCover = uri
                editDialogCoverView?.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)

        albumRootLayout = findViewById(R.id.albumRootLayout)
        albumNameText = findViewById(R.id.albumNameText)
        albumTracksList = findViewById(R.id.albumTracksList)
        btnBack = findViewById(R.id.btnBackAlbum)
        btnPlayAlbum = findViewById(R.id.btnPlayAlbum)
        btnShuffleAlbum = findViewById(R.id.btnShuffleAlbum)
        btnAlbumMore = findViewById(R.id.btnAlbumMore)
        btnAlbumMore.setOnClickListener { showAlbumMoreMenu(it) }
        albumCoverImage = findViewById(R.id.albumCoverImage)
        albumStatsText = findViewById(R.id.albumStatsText)
        btnSortAlbum = findViewById(R.id.btnSortAlbum)
        btnReorderTracks = findViewById(R.id.btnReorderTracks)

        albumTracksList.layoutManager = LinearLayoutManager(this)

        albumName = intent.getStringExtra("ALBUM_NAME")

        restoreColor()
        loadCustomAlbumData()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        if (albumName != null) {
            albumNameText.text = albumName
            loadAlbumTracks()
            trackAdapter = TrackAdapter(albumTracks, isFromPlaylist = false)
            albumTracksList.adapter = trackAdapter
            updateStats()
            loadAlbumCover(albumName, albumCoverImage)
            setupReorder()
        } else {
            finish()
        }

        btnBack.setOnClickListener { finish() }
        btnPlayAlbum.setOnClickListener { playAlbum() }
        btnShuffleAlbum.setOnClickListener { shuffleAndPlayAlbum() }
        btnSortAlbum.setOnClickListener { showSortMenu(it) }
        btnReorderTracks.setOnClickListener { toggleReorderMode() }
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

    private fun showAlbumMoreMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.album_more_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit_album -> {
                    showEditAlbumDialog()
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

    private fun showEditAlbumDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_album, null)
        val coverImageView = dialogView.findViewById<ImageView>(R.id.editAlbumCover)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editAlbumName)
        val selectCoverButton = dialogView.findViewById<Button>(R.id.btnSelectAlbumCover)

        editDialogCoverView = coverImageView // Сохраняем ссылку

        // Загружаем текущие данные
        nameEditText.setText(customAlbumName ?: albumName)

        if (customAlbumCover != null) {
            coverImageView.setImageURI(customAlbumCover)
        } else {
            coverImageView.setImageResource(R.drawable.ic_album_placeholder)
        }

        selectCoverButton.setOnClickListener {
            albumCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Редактировать альбом")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = nameEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    customAlbumName = newName
                    saveCustomAlbumData()
                    updateAlbumUI()
                }
                editDialogCoverView = null
            }
            .setNegativeButton("Отмена") { _, _ ->
                editDialogCoverView = null
            }
            .create()

        dialog.show()

        // Применяем черную тему
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

    private fun saveCustomAlbumData() {
        val prefs = getSharedPreferences("custom_albums", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("album_${albumName}_name", customAlbumName)
        editor.putString("album_${albumName}_cover", customAlbumCover?.toString())
        editor.apply()
    }

    private fun loadCustomAlbumData() {
        val prefs = getSharedPreferences("custom_albums", MODE_PRIVATE)
        customAlbumName = prefs.getString("album_${albumName}_name", null)
        val coverUri = prefs.getString("album_${albumName}_cover", null)
        customAlbumCover = if (coverUri != null) Uri.parse(coverUri) else null
        updateAlbumUI()
    }

    private fun updateAlbumUI() {
        albumNameText.text = customAlbumName ?: albumName
    }

    private fun loadAlbumCover(albumName: String?, imageView: ImageView) {
        if (albumName == null) {
            imageView.setImageResource(R.drawable.ic_album_placeholder)
            return
        }

        // Сначала используем кастомную обложку, если задана (чтобы совпадало со списком)
        customAlbumCover?.let {
            imageView.setImageURI(it)
            return
        }

        // Иначе ищем первый трек с обложкой
        var found = false
        for (t in albumTracks) {
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
            val name = (customAlbumName ?: albumName ?: "").trim()
            if (name.equals("Unknown", ignoreCase = true) || name.equals("<unknown>", ignoreCase = true) || name.isEmpty()) {
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
            applyAlbumSort()
            dialog.dismiss()
        }

        btnCancelSort.setOnClickListener { dialog.dismiss() }

        val titleView = dialog.findViewById<TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        dialog.show()
    }

    private fun applyAlbumSort() {
        when (sortType) {
            0 -> if (sortAscending) albumTracks.sortBy { it.dateModified } else albumTracks.sortByDescending { it.dateModified }
            1 -> if (sortAscending) albumTracks.sortBy { it.name.lowercase() } else albumTracks.sortByDescending { it.name.lowercase() }
            2 -> if (sortAscending) albumTracks.sortBy { (it.artist ?: "").lowercase() } else albumTracks.sortByDescending { (it.artist ?: "").lowercase() }
            3 -> if (sortAscending) albumTracks.sortBy { it.duration ?: 0L } else albumTracks.sortByDescending { it.duration ?: 0L }
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
        val count = albumTracks.size
        val totalDuration = albumTracks.sumOf { it.duration ?: 0L }
        val hours = totalDuration / 3600000
        val minutes = (totalDuration % 3600000) / 60000
        val seconds = (totalDuration / 1000) % 60
        val durationText = if (hours > 0) "$hours:${minutes.toString().padStart(2,'0')}:${seconds.toString().padStart(2,'0')}" else "$minutes:${seconds.toString().padStart(2,'0')}"
        albumStatsText.text = "$count треков • $durationText"
    }

    private fun setupReorder() {
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from in albumTracks.indices && to in albumTracks.indices) {
                    val item = albumTracks.removeAt(from)
                    albumTracks.add(to, item)
                    trackAdapter.notifyItemMoved(from, to)
                    return true
                }
                return false
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
            override fun isLongPressDragEnabled(): Boolean = isReorderMode
        }
        itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(albumTracksList)
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
                    loadAlbumTracks()
                    trackAdapter.updateTracks(albumTracks)
                } else {
                    val filtered = albumTracks.filter { it.name.contains(q, true) || (it.artist?.contains(q, true) == true) }
                    trackAdapter.updateTracks(filtered.toMutableList())
                }
                updateStats()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}