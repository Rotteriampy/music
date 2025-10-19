package com.example.music

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.Manifest
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.File
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.RadioGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.DefaultItemAnimator
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.IBinder
import android.widget.ImageView
import android.view.ContextThemeWrapper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import android.widget.ProgressBar

class MainActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout
    private lateinit var tabTracks: LinearLayout
    private lateinit var tabPlaylists: LinearLayout
    private lateinit var tabAlbums: LinearLayout
    private lateinit var tabArtists: LinearLayout
    private lateinit var tabGenres: LinearLayout

    private lateinit var searchButton: ImageButton
    private lateinit var sortButton: ImageButton
    private lateinit var shuffleButton: ImageButton
    private lateinit var trackList: RecyclerView
    private lateinit var contentTracks: LinearLayout
    private lateinit var contentPlaylists: LinearLayout
    private lateinit var contentAlbums: LinearLayout
    private lateinit var contentArtists: LinearLayout
    private lateinit var contentGenres: LinearLayout

    private lateinit var menuButton: ImageButton
    private lateinit var playlistList: RecyclerView
    private lateinit var albumList: RecyclerView
    private lateinit var artistList: RecyclerView
    private lateinit var genreList: RecyclerView

    private lateinit var createPlaylistButton: ImageButton
    private lateinit var btnReorderPlaylists: ImageButton

    private lateinit var trackAdapter: TrackAdapter
    private var lastHighlightedPath: String? = null

    private lateinit var btnReorderAlbums: ImageButton
    private lateinit var btnReorderArtists: ImageButton
    private lateinit var btnReorderGenres: ImageButton

    private lateinit var trackStatsText: TextView

    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniPlayerAvatar: ImageView
    private lateinit var miniPlayerTrackName: TextView
    private lateinit var miniPlayerArtist: TextView
    private lateinit var miniPlayerPlayPause: ImageButton
    private lateinit var miniPlayerPrevious: ImageButton
    private lateinit var miniPlayerNext: ImageButton

    private var musicService: MusicService? = null
    private var isBound = false

    private var isAlbumReorderMode = false
    private var isArtistReorderMode = false
    private var isGenreReorderMode = false

    private var itemTouchHelperAlbums: ItemTouchHelper? = null
    private var itemTouchHelperArtists: ItemTouchHelper? = null
    private var itemTouchHelperGenres: ItemTouchHelper? = null

    private var albumsOrder: MutableList<Album> = mutableListOf()
    private var artistsOrder: MutableList<Artist> = mutableListOf()
    private var genresOrder: MutableList<Genre> = mutableListOf()

    private val tracks = mutableListOf<Track>()
    private val filteredTracks = mutableListOf<Track>()
    private var searchQuery: String = ""
    private var sortType: Int = 0
    private var sortAscending: Boolean = false
    private lateinit var miniPlayerProgressBar: ProgressBar

    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var artistAdapter: ArtistAdapter
    private lateinit var genreAdapter: GenreAdapter

    private var itemTouchHelper: ItemTouchHelper? = null
    private var isPlaylistReorderMode = false
    private var allPlaylists: List<Playlist> = listOf()

    private val REQUEST_CODE_PERMISSION = 123
    private val REQUEST_CODE_IMAGE_PERMISSION = 124

    private var selectedCoverUri: Uri? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedCoverUri = uri
    }

    private val deletePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Разрешение получено, попробуйте снова", Toast.LENGTH_SHORT).show()
        }
    }

    private val trackChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.music.TRACK_CHANGED" -> {
                    updateMiniPlayer()
                    val newPath = QueueManager.getCurrentTrack()?.path
                    val oldPath = lastHighlightedPath
                    if (newPath != oldPath) {
                        val oldIdx = if (oldPath != null) filteredTracks.indexOfFirst { it.path == oldPath } else -1
                        val newIdx = if (newPath != null) filteredTracks.indexOfFirst { it.path == newPath } else -1
                        if (oldIdx >= 0) trackList.post { trackAdapter.notifyItemChanged(oldIdx, "HL") }
                        if (newIdx >= 0) trackList.post { trackAdapter.notifyItemChanged(newIdx, "HL") }
                        lastHighlightedPath = newPath
                    }
                }
                "com.example.music.PLAYBACK_STATE_CHANGED" -> {
                    updateMiniPlayerButton()
                    val curPath = QueueManager.getCurrentTrack()?.path
                    if (curPath != null) {
                        val idx = filteredTracks.indexOfFirst { it.path == curPath }
                        if (idx >= 0) trackList.post { trackAdapter.notifyItemChanged(idx, "HL") }
                    }
                }
                "com.example.music.STATS_UPDATED" -> {
                    trackAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateMiniPlayer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ListeningStats.loadStats(this)
        FavoritesManager.loadFavorites(this)

        mainLayout = findViewById(R.id.mainLayout)
        tabTracks = findViewById(R.id.tabTracks)
        tabPlaylists = findViewById(R.id.tabPlaylists)
        tabAlbums = findViewById(R.id.tabAlbums)
        tabArtists = findViewById(R.id.tabArtists)
        tabGenres = findViewById(R.id.tabGenres)

        searchButton = findViewById(R.id.searchButton)
        sortButton = findViewById(R.id.sortButton)
        shuffleButton = findViewById(R.id.shuffleButton)
        trackList = findViewById(R.id.trackList)
        contentTracks = findViewById(R.id.contentTracks)
        contentPlaylists = findViewById(R.id.contentPlaylists)
        contentAlbums = findViewById(R.id.contentAlbums)
        contentArtists = findViewById(R.id.contentArtists)
        contentGenres = findViewById(R.id.contentGenres)

        menuButton = findViewById(R.id.menuButton)
        playlistList = findViewById(R.id.playlistList)
        albumList = findViewById(R.id.albumList)
        artistList = findViewById(R.id.artistList)
        genreList = findViewById(R.id.genreList)
        btnReorderAlbums = findViewById(R.id.btnReorderAlbums)
        btnReorderArtists = findViewById(R.id.btnReorderArtists)
        btnReorderGenres = findViewById(R.id.btnReorderGenres)
        trackStatsText = findViewById(R.id.trackStatsText)

        miniPlayer = findViewById(R.id.miniPlayer)
        miniPlayerAvatar = findViewById(R.id.miniPlayerAvatar)
        miniPlayerTrackName = findViewById(R.id.miniPlayerTrackName)
        miniPlayerArtist = findViewById(R.id.miniPlayerArtist)
        miniPlayerPlayPause = findViewById(R.id.miniPlayerPlayPause)
        miniPlayerPrevious = findViewById(R.id.miniPlayerPrevious)
        miniPlayerNext = findViewById(R.id.miniPlayerNext)
        miniPlayerProgressBar = findViewById(R.id.miniPlayerProgressBar)

        val filter = IntentFilter().apply {
            addAction("com.example.music.TRACK_CHANGED")
            addAction("com.example.music.PLAYBACK_STATE_CHANGED")
            addAction("com.example.music.STATS_UPDATED")
        }
        ContextCompat.registerReceiver(
            this,
            trackChangedReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Привязка к сервису
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setupMiniPlayer()
        setupMiniPlayerUpdates()

        btnReorderAlbums.setOnClickListener { toggleAlbumReorderMode() }
        btnReorderArtists.setOnClickListener { toggleArtistReorderMode() }
        btnReorderGenres.setOnClickListener { toggleGenreReorderMode() }

        setupAlbumItemTouchHelper()
        setupArtistItemTouchHelper()
        setupGenreItemTouchHelper()
        updateStatusBarColor()

        createPlaylistButton = findViewById(R.id.createPlaylistButton)

        trackList.layoutManager = LinearLayoutManager(this)
        trackAdapter = TrackAdapter(filteredTracks as MutableList<Track>, false)
        trackList.adapter = trackAdapter

        playlistList.layoutManager = GridLayoutManager(this, 2)
        playlistAdapter = PlaylistAdapter(mutableListOf(), this)
        playlistList.adapter = playlistAdapter

        albumList.layoutManager = GridLayoutManager(this, 2)
        albumAdapter = AlbumAdapter(listOf(), this)
        albumList.adapter = albumAdapter

        artistList.layoutManager = GridLayoutManager(this, 2)
        artistAdapter = ArtistAdapter(listOf(), this)
        artistList.adapter = artistAdapter

        genreList.layoutManager = GridLayoutManager(this, 2)
        genreAdapter = GenreAdapter(this, listOf())
        genreList.adapter = genreAdapter

        searchButton.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }
        sortButton.setOnClickListener { showSortMenu(it) }
        shuffleButton.setOnClickListener { onShuffleClick() }

        tabTracks.setOnClickListener { selectTab(tabTracks) }
        tabPlaylists.setOnClickListener { selectTab(tabPlaylists) }
        tabAlbums.setOnClickListener { selectTab(tabAlbums) }
        tabArtists.setOnClickListener { selectTab(tabArtists) }
        tabGenres.setOnClickListener { selectTab(tabGenres) }

        val itemAnimator = DefaultItemAnimator()
        itemAnimator.moveDuration = 300

        albumList.itemAnimator = itemAnimator
        artistList.itemAnimator = itemAnimator
        genreList.itemAnimator = itemAnimator

        PlaylistManager.loadPlaylists(this)
        PlaylistManager.createFavoritesIfNotExists(this)
        QueueManager.loadQueue(this)

        checkAndRequestPermission()
        checkAndRequestImagePermission()

        setupTabListeners()
        setupButtonListeners()
        initPlaylistControls()
        restoreColor()
    }

    private fun setupMiniPlayerUpdates() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                if (MusicService.isPlaying) {
                    musicService?.let { service ->
                        val duration = service.getDuration()
                        val currentPos = service.getCurrentPosition()
                        if (duration > 0) {
                            val progress = (currentPos * 100) / duration
                            miniPlayerProgressBar.progress = progress
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun setupMiniPlayer() {
        miniPlayer.setOnClickListener {
            val currentTrack = MusicService.currentTrack
            if (currentTrack != null) {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("TRACK_PATH", currentTrack.path)
                    putExtra("TRACK_NAME", currentTrack.name)
                    putExtra("TRACK_ARTIST", currentTrack.artist)
                }
                startActivity(intent)
            }
        }

        miniPlayerPlayPause.setOnClickListener {
            if (MusicService.isPlaying) {
                musicService?.pauseMusic()
            } else {
                musicService?.resumeMusic()
            }
            updateMiniPlayerButton()
        }

        miniPlayerPrevious.setOnClickListener {
            if (QueueManager.moveToPreviousTrack(this)) {
                val prevTrack = QueueManager.getCurrentTrack()
                if (prevTrack != null && prevTrack.path != null && File(prevTrack.path).exists()) {
                    musicService?.playTrack(prevTrack.path)
                }
            }
        }

        miniPlayerNext.setOnClickListener {
            if (QueueManager.moveToNextTrack(this)) {
                val nextTrack = QueueManager.getCurrentTrack()
                if (nextTrack != null && nextTrack.path != null && File(nextTrack.path).exists()) {
                    musicService?.playTrack(nextTrack.path)
                }
            }
        }
    }

    private fun updateMiniPlayer() {
        if (MusicService.currentTrack != null) {
            miniPlayer.visibility = View.VISIBLE
            miniPlayerTrackName.text = MusicService.currentTrack?.name
            miniPlayerArtist.text = MusicService.currentTrack?.artist ?: "Unknown Artist"

            // Обновляем прогресс
            musicService?.let { service ->
                val duration = service.getDuration()
                val currentPos = service.getCurrentPosition()
                if (duration > 0) {
                    val progress = (currentPos * 100) / duration
                    miniPlayerProgressBar.progress = progress
                }
            }

            loadMiniPlayerCover(MusicService.currentTrack?.path, miniPlayerAvatar)
            updateMiniPlayerButton()
        } else {
            miniPlayer.visibility = View.GONE
        }
    }

    private fun updateMiniPlayerButton() {
        if (MusicService.isPlaying) {
            miniPlayerPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            miniPlayerPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun loadMiniPlayerCover(trackPath: String?, imageView: ImageView) {
        if (trackPath != null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(trackPath)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(R.drawable.ic_album_placeholder)
                }
                retriever.release()
            } catch (e: Exception) {
                imageView.setImageResource(R.drawable.ic_album_placeholder)
            }
        }
    }

    private fun initPlaylistControls() {
        btnReorderPlaylists = findViewById(R.id.btnReorderPlaylists)

        setupPlaylistItemTouchHelper()
        btnReorderPlaylists.setOnClickListener { togglePlaylistReorderMode() }
    }

    private fun updateTrackStats(tracks: List<Track>) {
        val count = tracks.size
        val totalDuration = tracks.sumOf { it.duration ?: 0L }

        val hours = totalDuration / 3600000
        val minutes = (totalDuration % 3600000) / 60000

        val durationText = if (hours > 0) {
            "$hours ч $minutes мин"
        } else {
            "$minutes мин"
        }

        val tracksText = when {
            count % 10 == 1 && count % 100 != 11 -> "$count трек"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "$count трека"
            else -> "$count треков"
        }

        trackStatsText.text = "$tracksText • $durationText"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == TrackAdapter.DELETE_PERMISSION_REQUEST) {
            if (resultCode == RESULT_OK) {
                val path = TrackAdapter.pendingDeletePath
                val position = TrackAdapter.pendingDeletePosition

                if (path != null && position >= 0) {
                    Toast.makeText(this, "Удаление разрешено, попробуйте еще раз", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Удаление отменено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlaylistItemTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                val fromPlaylist = playlistAdapter.getPlaylistAt(fromPosition)
                val toPlaylist = playlistAdapter.getPlaylistAt(toPosition)

                if (fromPlaylist.id == Playlist.FAVORITES_ID || toPlaylist.id == Playlist.FAVORITES_ID) {
                    return false
                }

                playlistAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean {
                return isPlaylistReorderMode
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(playlistList)
    }

    private fun togglePlaylistReorderMode() {
        isPlaylistReorderMode = !isPlaylistReorderMode
        playlistAdapter.isReorderMode = isPlaylistReorderMode

        if (isPlaylistReorderMode) {
            btnReorderPlaylists.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
        } else {
            btnReorderPlaylists.clearColorFilter()
            val currentPlaylists = (0 until playlistAdapter.itemCount).map {
                playlistAdapter.getPlaylistAt(it)
            }
            PlaylistManager.updatePlaylistOrder(this, currentPlaylists)
        }
    }

    private fun loadPlaylistsData() {
        if (!isPlaylistReorderMode) {
            allPlaylists = PlaylistManager.getPlaylists()
            playlistAdapter.updatePlaylists(allPlaylists)

            playlistAdapter.onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }

            playlistAdapter.onPlaylistMoved = { fromPos, toPos ->
            }
        }
    }

    private fun loadAlbumsData() {
        lifecycleScope.launch {
            // Показываем кэш сразу
            val cachedAlbums = DataCache.loadAlbums(this@MainActivity)
            if (cachedAlbums != null && cachedAlbums.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    albumsOrder = loadAlbumOrder(cachedAlbums).toMutableList()
                    albumAdapter.updateAlbums(albumsOrder)
                }
            }
            // Загружаем свежие данные
            val freshAlbums = withContext(Dispatchers.IO) {
                AlbumManager.getAlbumsFromTracks(this@MainActivity)
            }

            withContext(Dispatchers.Main) {
                albumsOrder = loadAlbumOrder(freshAlbums).toMutableList()
                albumAdapter.updateAlbums(albumsOrder)
            }

            DataCache.saveAlbums(this@MainActivity, freshAlbums)
        }
    }
    private fun loadArtistsData() {
        lifecycleScope.launch {
            // Показываем кэш сразу
            val cachedArtists = DataCache.loadArtists(this@MainActivity)
            if (cachedArtists != null && cachedArtists.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    artistsOrder = loadArtistOrder(cachedArtists).toMutableList()
                    artistAdapter.updateArtists(artistsOrder)
                }
            }
            // Загружаем свежие данные
            val freshArtists = withContext(Dispatchers.IO) {
                ArtistManager.getArtistsFromTracks(this@MainActivity)
            }

            withContext(Dispatchers.Main) {
                artistsOrder = loadArtistOrder(freshArtists).toMutableList()
                artistAdapter.updateArtists(artistsOrder)
            }

            DataCache.saveArtists(this@MainActivity, freshArtists)
        }
    }
    private fun loadGenresData() {
        lifecycleScope.launch {
            // Показываем кэш сразу
            val cachedGenres = DataCache.loadGenres(this@MainActivity)
            if (cachedGenres != null && cachedGenres.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    genresOrder = loadGenreOrder(cachedGenres).toMutableList()
                    genreAdapter.updateGenres(genresOrder)
                }
            }
            // Загружаем свежие данные
            val freshGenres = GenreManager.getGenresFromTracksAsync(this@MainActivity)
            withContext(Dispatchers.Main) {
                genresOrder = loadGenreOrder(freshGenres).toMutableList()
                genreAdapter.updateGenres(genresOrder)
            }

            DataCache.saveGenres(this@MainActivity, freshGenres)
        }
    }

    private fun onShuffleClick() {
        val currentTracks = getCurrentSortedTracks()
        if (currentTracks.isNotEmpty()) {
            QueueManager.shuffleQueue(this, currentTracks)
            val firstTrack = QueueManager.getCurrentTrack()
            if (firstTrack != null && firstTrack.path != null && File(firstTrack.path).exists()) {
                val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                    putExtra("TRACK_PATH", firstTrack.path)
                    putExtra("TRACK_NAME", firstTrack.name)
                    putExtra("TRACK_ARTIST", firstTrack.artist)
                    putExtra("PLAYBACK_MODE", "SHUFFLE")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Нет доступных треков для воспроизведения", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Нет треков для перемешивания", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentSortedTracks(): List<Track> {
        return filteredTracks.ifEmpty { tracks }
    }

    private fun checkPermissionsAndLoadTracks() {
        if (hasStoragePermission()) {
            loadTracks()
        } else {
            checkAndRequestPermission()
        }
    }

    private fun setupTabListeners() {
        tabTracks.setOnClickListener {
            selectTab(tabTracks)
        }
        tabPlaylists.setOnClickListener {
            selectTab(tabPlaylists)
        }
        tabAlbums.setOnClickListener {
            selectTab(tabAlbums)
        }
        tabArtists.setOnClickListener {
            selectTab(tabArtists)
        }
        tabGenres.setOnClickListener {
            selectTab(tabGenres)
        }
    }

    private fun setupButtonListeners() {
        menuButton.setOnClickListener { view ->
            showPopupMenu(view)
        }

        shuffleButton.setOnClickListener {
            val currentTracks = getCurrentSortedTracks()
            if (currentTracks.isNotEmpty()) {
                QueueManager.shuffleQueue(this, currentTracks)
                val firstTrack = QueueManager.getCurrentTrack()
                if (firstTrack != null && firstTrack.path != null && File(firstTrack.path).exists()) {
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra("TRACK_PATH", firstTrack.path)
                        putExtra("TRACK_NAME", firstTrack.name)
                        putExtra("TRACK_ARTIST", firstTrack.artist)
                        putExtra("PLAYBACK_MODE", "SHUFFLE")
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Нет доступных треков для воспроизведения", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Нет треков для перемешивания", Toast.LENGTH_SHORT).show()
            }
        }

        createPlaylistButton.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun showCreatePlaylistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_playlist, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.playlistNameEdit)
        val chooseCoverButton = dialogView.findViewById<ImageButton>(R.id.chooseCoverButton)

        chooseCoverButton.setOnClickListener {
            if (hasImagePermission()) {
                galleryLauncher.launch("image/*")
            } else {
                Toast.makeText(this, "Требуется разрешение на доступ к изображениям", Toast.LENGTH_SHORT).show()
                checkAndRequestImagePermission()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Создать плейлист")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val name = nameEditText.text.toString()
                if (name.isNotEmpty()) {
                    val playlist = Playlist(name = name, coverUri = selectedCoverUri?.toString())
                    PlaylistManager.addPlaylist(this, playlist)
                    playlistAdapter.updatePlaylists(PlaylistManager.getPlaylists())
                    selectedCoverUri = null
                } else {
                    Toast.makeText(this, "Введите название плейлиста", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun selectTab(selectedTab: LinearLayout) {
        findViewById<View>(R.id.tabTracksIndicator).visibility = View.GONE
        findViewById<View>(R.id.tabPlaylistsIndicator).visibility = View.GONE
        findViewById<View>(R.id.tabAlbumsIndicator).visibility = View.GONE
        findViewById<View>(R.id.tabArtistsIndicator).visibility = View.GONE
        findViewById<View>(R.id.tabGenresIndicator).visibility = View.GONE

        (tabTracks.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        (tabPlaylists.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        (tabAlbums.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        (tabArtists.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        (tabGenres.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        contentTracks.visibility = View.GONE
        contentPlaylists.visibility = View.GONE
        contentAlbums.visibility = View.GONE
        contentArtists.visibility = View.GONE
        contentGenres.visibility = View.GONE

        findViewById<LinearLayout>(R.id.trackButtons).visibility = View.GONE
        findViewById<LinearLayout>(R.id.playlistButtons).visibility = View.GONE
        findViewById<LinearLayout>(R.id.albumButtons).visibility = View.GONE
        findViewById<LinearLayout>(R.id.artistButtons).visibility = View.GONE
        findViewById<LinearLayout>(R.id.genreButtons).visibility = View.GONE
        trackStatsText.visibility = View.GONE

        when (selectedTab.id) {
            R.id.tabTracks -> {
                findViewById<View>(R.id.tabTracksIndicator).visibility = View.VISIBLE
                (tabTracks.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, R.color.accent_color))
                contentTracks.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.trackButtons).visibility = View.VISIBLE
                trackStatsText.visibility = View.VISIBLE
                checkPermissionsAndLoadTracks()
            }
            R.id.tabPlaylists -> {
                findViewById<View>(R.id.tabPlaylistsIndicator).visibility = View.VISIBLE
                (tabPlaylists.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, R.color.accent_color))
                contentPlaylists.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.playlistButtons).visibility = View.VISIBLE
                loadPlaylistsData()
            }
            R.id.tabAlbums -> {
                findViewById<View>(R.id.tabAlbumsIndicator).visibility = View.VISIBLE
                (tabAlbums.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, R.color.accent_color))
                contentAlbums.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.albumButtons).visibility = View.VISIBLE
                loadAlbumsData()
            }
            R.id.tabArtists -> {
                findViewById<View>(R.id.tabArtistsIndicator).visibility = View.VISIBLE
                (tabArtists.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, R.color.accent_color))
                contentArtists.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.artistButtons).visibility = View.VISIBLE
                loadArtistsData()
            }
            R.id.tabGenres -> {
                findViewById<View>(R.id.tabGenresIndicator).visibility = View.VISIBLE
                (tabGenres.getChildAt(0) as? TextView)?.setTextColor(ContextCompat.getColor(this, R.color.accent_color))
                contentGenres.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.genreButtons).visibility = View.VISIBLE
                loadGenresData()
            }
        }
    }

    private fun showSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_search, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)

        searchEditText.requestFocus()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Поиск треков")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                searchQuery = searchEditText.text.toString().trim()
                applyFilterAndSort(true)
            }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Очистить") { _, _ ->
                searchQuery = ""
                applyFilterAndSort(true)
            }
            .create()

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                applyFilterAndSort(false)
            }
        })

        dialog.show()
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
            applyFilterAndSort(true)
            dialog.dismiss()
        }

        btnCancelSort.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, android.R.color.black)))

        val titleView = dialog.findViewById<TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun applyFilterAndSort(notify: Boolean) {
        filteredTracks.clear()

        if (searchQuery.isEmpty()) {
            filteredTracks.addAll(tracks)
        } else {
            val query = searchQuery.lowercase()
            filteredTracks.addAll(tracks.filter {
                it.name?.lowercase()?.contains(query) == true ||
                        it.artist?.lowercase()?.contains(query) == true ||
                        it.albumName?.lowercase()?.contains(query) == true
            })
        }

        when (sortType) {
            0 -> {
                if (sortAscending) {
                    filteredTracks.sortBy { it.dateModified }
                } else {
                    filteredTracks.sortByDescending { it.dateModified }
                }
            }
            1 -> {
                if (sortAscending) {
                    filteredTracks.sortBy { it.name?.lowercase() }
                } else {
                    filteredTracks.sortByDescending { it.name?.lowercase() }
                }
            }
            2 -> {
                if (sortAscending) {
                    filteredTracks.sortBy { it.artist?.lowercase() }
                } else {
                    filteredTracks.sortByDescending { it.artist?.lowercase() }
                }
            }
            3 -> {
                if (sortAscending) {
                    filteredTracks.sortBy { it.duration }
                } else {
                    filteredTracks.sortByDescending { it.duration }
                }
            }
        }

        if (notify) {
            trackAdapter.notifyDataSetChanged()
        }

        updateTrackStats(filteredTracks)
    }

    private fun showPopupMenu(view: View) {
        val wrapper = ContextThemeWrapper(this, R.style.Base_Theme_Music)
        val popup = PopupMenu(wrapper, view)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_queue -> {
                    startActivity(Intent(this, QueueActivity::class.java))
                    true
                }
                R.id.menu_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showQueueDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_queue, null)
        val queueRecyclerView = dialogView.findViewById<RecyclerView>(R.id.queueRecyclerView)
        val emptyQueueText = dialogView.findViewById<TextView>(R.id.emptyQueueText)

        val queue = QueueManager.getCurrentQueue()

        if (queue.isEmpty()) {
            emptyQueueText.visibility = View.VISIBLE
            queueRecyclerView.visibility = View.GONE
        } else {
            emptyQueueText.visibility = View.GONE
            queueRecyclerView.visibility = View.VISIBLE
            queueRecyclerView.layoutManager = LinearLayoutManager(this)
            queueRecyclerView.adapter = QueueAdapter(queue)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Очередь воспроизведения")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, android.R.color.black)))

        val titleView = dialog.findViewById<TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    override fun onResume() {
        super.onResume()
        ListeningStats.loadStats(this)
        restoreColor()
        trackAdapter.notifyDataSetChanged()
        PlaylistManager.cleanupDeletedTracks(this)
        updateMiniPlayer()
        updateMiniPlayerButton()
        updateStatusBarColor()

        if (contentPlaylists.visibility == View.VISIBLE) {
            loadPlaylistsData()
        } else if (contentAlbums.visibility == View.VISIBLE) {
            loadAlbumsData()
        } else if (contentArtists.visibility == View.VISIBLE) {
            loadArtistsData()
        } else if (contentGenres.visibility == View.VISIBLE) {
            loadGenresData()
        }
    }

    private fun restoreColor() {
        val sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE)
        val scheme = sharedPreferences.getInt("color_scheme", 0)
        val color = when (scheme) {
            1 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.darker_gray))
            2 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            3 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            else -> ColorDrawable(ContextCompat.getColor(this, android.R.color.black))
        }
        mainLayout.background = color
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasImagePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermission() {
        if (!hasStoragePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                    REQUEST_CODE_PERMISSION
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISSION
                )
            }
        } else {
            loadTracks()
        }
    }

    private fun checkAndRequestImagePermission() {
        if (!hasImagePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQUEST_CODE_IMAGE_PERMISSION
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_IMAGE_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadTracks()
                } else {
                    Toast.makeText(this, "Требуется разрешение для доступа к медиафайлам", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CODE_IMAGE_PERMISSION -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Требуется разрешение для доступа к изображениям", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTracks() {
        lifecycleScope.launch {
            // 1. Показываем кэш сразу
            val cachedTracks = DataCache.loadTracks(this@MainActivity)
            if (cachedTracks != null && cachedTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    tracks.clear()
                    tracks.addAll(cachedTracks)
                    applyFilterAndSort(true)
                }
            }
            // 2. Загружаем свежие данные в фоне
            val freshTracks = withContext(Dispatchers.IO) {
                val loadedTracks = mutableListOf<Track>()

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
                val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
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
                    val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn).toString()
                        val title = cursor.getString(titleColumn)
                        val artist = cursor.getString(artistColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val albumName = cursor.getString(albumColumn)
                        val path = cursor.getString(dataColumn)
                        val duration = cursor.getLong(durationColumn)
                        val dateModified = cursor.getLong(dateModifiedColumn)
                        if (path != null && File(path).exists()) {
                            loadedTracks.add(
                                Track(
                                    id = id,
                                    name = title,
                                    artist = artist,
                                    albumId = albumId,
                                    albumName = albumName,
                                    path = path,
                                    duration = duration,
                                    dateModified = dateModified
                                )
                            )
                        }
                    }
                }

                loadedTracks
            }
            // 3. Обновляем UI и сохраняем кэш
            withContext(Dispatchers.Main) {
                tracks.clear()
                tracks.addAll(freshTracks)
                applyFilterAndSort(true)
            }

            DataCache.saveTracks(this@MainActivity, freshTracks)
        }
    }

    private fun setupAlbumItemTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                val item = albumsOrder.removeAt(fromPosition)
                albumsOrder.add(toPosition, item)
                albumAdapter.updateAlbums(albumsOrder)
                recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = isAlbumReorderMode
        }

        itemTouchHelperAlbums = ItemTouchHelper(callback)
        itemTouchHelperAlbums?.attachToRecyclerView(albumList)
    }

    private fun toggleAlbumReorderMode() {
        isAlbumReorderMode = !isAlbumReorderMode

        if (isAlbumReorderMode) {
            btnReorderAlbums.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
            Toast.makeText(this, "Удерживайте и перетаскивайте альбомы", Toast.LENGTH_SHORT).show()
        } else {
            btnReorderAlbums.clearColorFilter()
            saveAlbumOrder()
        }
    }

    private fun saveAlbumOrder() {
        val prefs = getSharedPreferences("album_order", MODE_PRIVATE)
        val editor = prefs.edit()
        val orderList = albumsOrder.map { it.name }
        editor.putString("order", orderList.joinToString(","))
        editor.apply()
    }

    private fun loadAlbumOrder(albums: List<Album>): List<Album> {
        val prefs = getSharedPreferences("album_order", MODE_PRIVATE)
        val orderString = prefs.getString("order", null) ?: return albums

        val orderList = orderString.split(",")
        val orderedAlbums = mutableListOf<Album>()
        val albumMap = albums.associateBy { it.name }

        orderList.forEach { name ->
            albumMap[name]?.let { orderedAlbums.add(it) }
        }

        albums.forEach { album ->
            if (!orderedAlbums.contains(album)) {
                orderedAlbums.add(album)
            }
        }

        return orderedAlbums
    }

    private fun setupArtistItemTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                val item = artistsOrder.removeAt(fromPosition)
                artistsOrder.add(toPosition, item)
                artistAdapter.updateArtists(artistsOrder)
                recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = isArtistReorderMode
        }

        itemTouchHelperArtists = ItemTouchHelper(callback)
        itemTouchHelperArtists?.attachToRecyclerView(artistList)
    }

    private fun toggleArtistReorderMode() {
        isArtistReorderMode = !isArtistReorderMode

        if (isArtistReorderMode) {
            btnReorderArtists.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
            Toast.makeText(this, "Удерживайте и перетаскивайте исполнителей", Toast.LENGTH_SHORT).show()
        } else {
            btnReorderArtists.clearColorFilter()
            saveArtistOrder()
        }
    }

    private fun saveArtistOrder() {
        val prefs = getSharedPreferences("artist_order", MODE_PRIVATE)
        val editor = prefs.edit()
        val orderList = artistsOrder.map { it.name }
        editor.putString("order", orderList.joinToString(","))
        editor.apply()
    }

    private fun loadArtistOrder(artists: List<Artist>): List<Artist> {
        val prefs = getSharedPreferences("artist_order", MODE_PRIVATE)
        val orderString = prefs.getString("order", null) ?: return artists

        val orderList = orderString.split(",")
        val orderedArtists = mutableListOf<Artist>()
        val artistMap = artists.associateBy { it.name }

        orderList.forEach { name ->
            artistMap[name]?.let { orderedArtists.add(it) }
        }

        artists.forEach { artist ->
            if (!orderedArtists.contains(artist)) {
                orderedArtists.add(artist)
            }
        }

        return orderedArtists
    }

    private fun setupGenreItemTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                val item = genresOrder.removeAt(fromPosition)
                genresOrder.add(toPosition, item)
                genreAdapter.updateGenres(genresOrder)
                recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = isGenreReorderMode
        }

        itemTouchHelperGenres = ItemTouchHelper(callback)
        itemTouchHelperGenres?.attachToRecyclerView(genreList)
    }

    private fun toggleGenreReorderMode() {
        isGenreReorderMode = !isGenreReorderMode

        if (isGenreReorderMode) {
            btnReorderGenres.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
            Toast.makeText(this, "Удерживайте и перетаскивайте жанры", Toast.LENGTH_SHORT).show()
        } else {
            btnReorderGenres.clearColorFilter()
            saveGenreOrder()
        }
    }

    private fun saveGenreOrder() {
        val prefs = getSharedPreferences("genre_order", MODE_PRIVATE)
        val editor = prefs.edit()
        val orderList = genresOrder.map { it.name }
        editor.putString("order", orderList.joinToString(","))
        editor.apply()
    }

    private fun loadGenreOrder(genres: List<Genre>): List<Genre> {
        val prefs = getSharedPreferences("genre_order", MODE_PRIVATE)
        val orderString = prefs.getString("order", null) ?: return genres

        val orderList = orderString.split(",")
        val orderedGenres = mutableListOf<Genre>()
        val genreMap = genres.associateBy { it.name }

        orderList.forEach { name ->
            genreMap[name]?.let { orderedGenres.add(it) }
        }

        genres.forEach { genre ->
            if (!orderedGenres.contains(genre)) {
                orderedGenres.add(genre)
            }
        }

        return orderedGenres
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(trackChangedReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun updateStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val scheme = sharedPreferences.getInt("color_scheme", 0)

            val color = when (scheme) {
                1 -> ContextCompat.getColor(this, android.R.color.darker_gray)
                2 -> ContextCompat.getColor(this, android.R.color.holo_blue_dark)
                3 -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
                else -> ContextCompat.getColor(this, android.R.color.black)
            }

            window.statusBarColor = color
        }
    }
}