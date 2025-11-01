package com.example.music

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.activity.result.ActivityResultLauncher
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.widget.RelativeLayout
import android.view.LayoutInflater
import android.widget.PopupWindow
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout
    private lateinit var tabsScrollView: HorizontalScrollView
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
    private lateinit var createPlaylistGalleryLauncher: ActivityResultLauncher<String>
    private lateinit var createPlaylistCropLauncher: ActivityResultLauncher<Intent>
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
    private var currentDialog: AlertDialog? = null

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
    private var mainBaseTopPadding: Int = -1
    private var avatarSpin: ObjectAnimator? = null
    private var areTracksLoaded = false
    private var arePlaylistsLoaded = false
    private var areAlbumsLoaded = false
    private var areArtistsLoaded = false
    private var areGenresLoaded = false

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

    private val artistUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Перезагружаем данные артистов
            loadArtistsData()
        }
    }

    private val genreUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Перезагружаем данные жанров
            loadGenresData()
        }
    }
    val playlistUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Перезагружаем данные плейлистов
            loadPlaylistsData()
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
                ThemeManager.ACTION_THEME_CHANGED -> {
                    restoreColor()
                    reapplyBarsFromBackground()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize night mode from saved preference; default to dark on first launch
        run {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (!prefs.contains("night_mode")) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                prefs.edit().putInt("night_mode", AppCompatDelegate.MODE_NIGHT_YES).apply()
            } else {
                val mode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_YES)
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
        // Switch from SplashTheme to the normal app theme before inflating content
        setTheme(R.style.Theme_Music)
        setContentView(R.layout.activity_main)
        DiskImageCache.init(this)
        ListeningStats.loadStats(this)
        FavoritesManager.loadFavorites(this)

        mainLayout = findViewById(R.id.mainLayout)
        tabsScrollView = findViewById(R.id.tabsScrollView)
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
            addAction(ThemeManager.ACTION_THEME_CHANGED)
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

        // Инициализируем анимационное состояние подчёркиваний вкладок
        initTabIndicators()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Рисуем контент под статус-баром и делаем его прозрачным
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            run {
                val flags = (window.decorView.systemUiVisibility
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                window.decorView.systemUiVisibility = flags
            }
        }

        // Показываем системные панели, включаем layout под статус-бар и применяем цвет нав-бара/иконок
        run {
            ThemeManager.showSystemBars(window, this)
            setLayoutFullscreen()
            applyContentTopPadding()
            reapplyBarsFromBackground()
        }

        createPlaylistButton = findViewById(R.id.createPlaylistButton)

        trackList.layoutManager = LinearLayoutManager(this)
        trackAdapter = TrackAdapter(filteredTracks as MutableList<Track>, false)
        trackList.adapter = trackAdapter

        playlistList.layoutManager = GridLayoutManager(this, 2)
        playlistAdapter = PlaylistAdapter(mutableListOf(), this)
        playlistList.adapter = playlistAdapter

        // Smooth item appearance on scroll/attach
        attachAppearAnimation(trackList)
        attachAppearAnimation(playlistList)
        attachAppearAnimation(albumList)
        attachAppearAnimation(artistList)
        attachAppearAnimation(genreList)

        albumList.layoutManager = GridLayoutManager(this, 2)
        albumAdapter = AlbumAdapter(listOf(), this)
        albumList.adapter = albumAdapter

        artistList.layoutManager = GridLayoutManager(this, 2)
        artistAdapter = ArtistAdapter(listOf(), this)
        artistList.adapter = artistAdapter

        genreList.layoutManager = GridLayoutManager(this, 2)
        genreAdapter = GenreAdapter(this, listOf())
        genreList.adapter = genreAdapter


        createPlaylistGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val intent = Intent(this, CropImageActivity::class.java)
                intent.putExtra("imageUri", it)
                createPlaylistCropLauncher.launch(intent)
            }
        }

        createPlaylistCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedCoverUri = uri
                    // Обновим ImageView в диалоге, если он открыт
                    val dialog = currentDialog
                    val coverImageView = dialog?.findViewById<ImageView>(R.id.createPlaylistCover)
                    coverImageView?.setImageURI(uri)
                }
            }
        }
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
        // Use a safer animator for genres; we'll disable it to avoid conflicts with custom animations
        genreList.itemAnimator = null

        // RecyclerView perf tweaks (notably for Artists/Genres tabs)
        try {
            (artistList.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        } catch (_: Exception) {}
        artistList.setHasFixedSize(true)
        artistList.setItemViewCacheSize(20)

        try {
            (genreList.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        } catch (_: Exception) {}
        genreList.setHasFixedSize(true)
        genreList.setItemViewCacheSize(20)

        PlaylistManager.loadPlaylists(this)
        PlaylistManager.createFavoritesIfNotExists(this)
        QueueManager.loadQueue(this)

        checkAndRequestPermission()
        checkAndRequestImagePermission()

        setupTabListeners()
        setupButtonListeners()
        initPlaylistControls()
        restoreColor()

        preloadAllCachedUI()
    }

    override fun onResume() {
        super.onResume()
        ListeningStats.loadStats(this)
        restoreColor()
        // Безопасно обновляем адаптер (проверка инициализации)
        if (::trackAdapter.isInitialized) {
            trackAdapter.notifyDataSetChanged()
        }
        PlaylistManager.cleanupDeletedTracks(this)
        updateMiniPlayer()
        updateActiveTabColors()
        updateMiniPlayerButton()

        if (contentAlbums.visibility == View.VISIBLE) {
            loadAlbumsData()
        } else if (contentArtists.visibility == View.VISIBLE) {
            loadArtistsData()
        } else if (contentGenres.visibility == View.VISIBLE) {
            loadGenresData()
        }

        if (contentPlaylists.visibility == View.VISIBLE) {
            if (!arePlaylistsLoaded) {
                loadPlaylistsData()
                arePlaylistsLoaded = true
            }
        } else if (contentAlbums.visibility == View.VISIBLE) {
            if (!areAlbumsLoaded) {
                loadAlbumsData()
                areAlbumsLoaded = true
            }
        } else if (contentArtists.visibility == View.VISIBLE) {
            if (!areArtistsLoaded) {
                loadArtistsData()
                areArtistsLoaded = true
            }
        } else if (contentGenres.visibility == View.VISIBLE) {
            if (!areGenresLoaded) {
                loadGenresData()
                areGenresLoaded = true
            }
        }

        // Обновляем статус- и нав-бар под текущий фон (градиент темы)
        ThemeManager.showSystemBars(window, this)
        setLayoutFullscreen()
        applyContentTopPadding()
        reapplyBarsFromBackground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ThemeManager.showSystemBars(window, this)
            setLayoutFullscreen()
            applyContentTopPadding()
            reapplyBarsFromBackground()
        }
    }

    private fun setLayoutFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            run {
                val decor = window.decorView
                var flags = decor.systemUiVisibility
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                decor.systemUiVisibility = flags
            }
        }
    }

    private fun applyContentTopPadding() {
        val res = resources
        val resId = res.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resId > 0) res.getDimensionPixelSize(resId) else 0
        // Сохраняем текущие боковые и нижние отступы, меняем только верхний под статус-бар
        val left = mainLayout.paddingLeft
        val right = mainLayout.paddingRight
        val bottom = mainLayout.paddingBottom
        if (mainLayout.paddingTop != statusBarHeight) {
            mainLayout.setPadding(left, statusBarHeight, right, bottom)
        }
    }

    private fun reapplyBarsFromBackground() {
        // Базовое поведение: статус-бар прозрачный, нав-бар от фона главного экрана
        when (val bg = mainLayout.background) {
            is BitmapDrawable -> bg.bitmap?.let { applyBarsFromBitmapTop(it) }
            is ColorDrawable -> applyBarsForColor(bg.color)
            else -> applyBarsForColor(ThemeManager.getPrimaryGradientStart(this))
        }
        // Если мини-плеер показан, делаем навигационную панель единым целым с его фоном
        if (miniPlayer.visibility == View.VISIBLE) {
            val gradStart = ThemeManager.getPrimaryGradientStart(this)
            val gradEnd = ThemeManager.getPrimaryGradientEnd(this)
            val lightStart = lighten(gradStart, 0.15f)
            val lightEnd = lighten(gradEnd, 0.15f)
            // Нижняя часть мини-плеера соприкасается с нав-баром, используем нижний цвет
            applyNavBarForColor(lightEnd)
        }
    }

    private fun applyNavBarForColor(color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                window.navigationBarColor = color
            } catch (_: Exception) { }
        }
        val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(color)
        val lightIcons = luminance > 0.5
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val decor = window.decorView
            var vis = decor.systemUiVisibility
            vis = if (lightIcons) {
                vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            decor.systemUiVisibility = vis
        }
    }

    private fun applyBarsFromBitmapTop(bmp: Bitmap) {
        try {
            val h = bmp.height.coerceAtLeast(1)
            val sampleHeight = (h * 0.08f).toInt().coerceIn(1, h)
            val yEnd = sampleHeight
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0L
            val w = bmp.width
            val step = (w / 50).coerceAtLeast(1)
            for (y in 0 until yEnd) {
                var x = 0
                while (x < w) {
                    val c = bmp.getPixel(x, y)
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                    count++
                    x += step
                }
            }
            if (count > 0) {
                val r = (rSum / count).toInt().coerceIn(0, 255)
                val g = (gSum / count).toInt().coerceIn(0, 255)
                val b = (bSum / count).toInt().coerceIn(0, 255)
                val color = android.graphics.Color.rgb(r, g, b)
                applyBarsForColor(color)
            }
        } catch (_: Exception) { }
    }

    private fun applyBarsForColor(color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                // Статус-бар оставляем прозрачным, чтобы фон был реальным продолжением
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                // Нав-бар красим в цвет верхней части фона
                window.navigationBarColor = color
            } catch (_: Exception) { }
        }
        val luminance = ColorUtils.calculateLuminance(color)
        val lightIcons = luminance > 0.5
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decor = window.decorView
            var vis = decor.systemUiVisibility
            vis = if (lightIcons) {
                vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            decor.systemUiVisibility = vis
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val decor = window.decorView
            var vis = decor.systemUiVisibility
            vis = if (lightIcons) {
                vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            decor.systemUiVisibility = vis
        }
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
        val isTracksTabActive = contentTracks.visibility == View.VISIBLE
        if (MusicService.currentTrack != null && isTracksTabActive) {
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
            startAvatarSpin()
        } else {
            miniPlayerPlayPause.setImageResource(R.drawable.ic_play)
            pauseAvatarSpin()
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
                    setRoundedMini(imageView, bitmap, 12f)
                    Log.d("MainActivity", "mini rounded applied (embedded)")
                } else {
                    // Circular placeholder
                    val d = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_album_placeholder)
                    if (d != null) {
                        val bm = android.graphics.Bitmap.createBitmap(48 * resources.displayMetrics.density.toInt(), 48 * resources.displayMetrics.density.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(bm)
                        d.setBounds(0, 0, c.width, c.height)
                        d.draw(c)
                        setRoundedMini(imageView, bm, 999f)
                    } else {
                        imageView.setImageResource(R.drawable.ic_album_placeholder)
                    }
                    Log.d("MainActivity", "mini placeholder (no art)")
                }
                retriever.release()
            } catch (e: Exception) {
                val d = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_album_placeholder)
                if (d != null) {
                    val bm = android.graphics.Bitmap.createBitmap(48 * resources.displayMetrics.density.toInt(), 48 * resources.displayMetrics.density.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(bm)
                    d.setBounds(0, 0, c.width, c.height)
                    d.draw(c)
                    setRoundedMini(imageView, bm, 999f)
                } else {
                    imageView.setImageResource(R.drawable.ic_album_placeholder)
                }
                Log.d("MainActivity", "mini placeholder (error): ${e.message}")
            }
        }
    }

    private fun setRoundedMini(view: ImageView, bitmap: android.graphics.Bitmap, radiusDp: Float) {
        val density = resources.displayMetrics.density
        val target = run {
            val w = if (view.width > 0) view.width else (view.layoutParams?.width ?: 0)
            val h = if (view.height > 0) view.height else (view.layoutParams?.height ?: 0)
            val fallback = (48 * density).toInt()
            val sz = kotlin.math.max(1, kotlin.math.min(if (w > 0) w else fallback, if (h > 0) h else fallback))
            sz
        }

        val out = android.graphics.Bitmap.createBitmap(target, target, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val scale = kotlin.math.max(target.toFloat() / bitmap.width, target.toFloat() / bitmap.height)
        val dx = (target - bitmap.width * scale) / 2f
        val dy = (target - bitmap.height * scale) / 2f
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        val r = target / 2f
        canvas.drawCircle(r, r, r, paint)
        view.scaleType = ImageView.ScaleType.FIT_XY
        view.setImageBitmap(out)
    }

    private fun startAvatarSpin() {
        if (avatarSpin == null) {
            avatarSpin = ObjectAnimator.ofFloat(miniPlayerAvatar, View.ROTATION, 0f, 360f).apply {
                duration = 8000L
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
            }
        }
        if (miniPlayer.visibility == View.VISIBLE) {
            if (avatarSpin?.isPaused == true) avatarSpin?.resume() else if (!(avatarSpin?.isRunning ?: false)) avatarSpin?.start()
        }
    }

    private fun pauseAvatarSpin() {
        avatarSpin?.takeIf { it.isRunning }?.pause()
    }

    private fun stopAvatarSpin() {
        avatarSpin?.cancel()
        miniPlayerAvatar.rotation = 0f
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
        if (requestCode == CROP_IMAGE_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedCoverUri = uri
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
        if (isPlaylistReorderMode) return
        lifecycleScope.launch {
            // 1) Instant UI from cache
            val cached = DataCache.loadPlaylists(this@MainActivity)
            if (cached != null && cached.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    allPlaylists = cached.toMutableList()
                    playlistAdapter.updatePlaylists(allPlaylists)
                }
            }

            // 2) Refresh from source
            val fresh = withContext(Dispatchers.IO) {
                PlaylistManager.getPlaylists()
            }
            withContext(Dispatchers.Main) {
                allPlaylists = fresh.toMutableList()
                playlistAdapter.updatePlaylists(allPlaylists)
            }
            DataCache.savePlaylists(this@MainActivity, fresh)

            playlistAdapter.onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }
            playlistAdapter.onPlaylistMoved = { fromPos, toPos -> }
        }
    }

    private fun loadAlbumsData() {
        lifecycleScope.launch {
            // Показываем кэш сразу
            albumAdapter.updateAlbums(emptyList())
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
            artistAdapter.updateArtists(emptyList())
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
            genreAdapter.updateGenres(emptyList())
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
            val shuffled = currentTracks.shuffled()
            QueueManager.initializeQueueFromPosition(this, shuffled, 0)
            val first = QueueManager.getCurrentTrack()
            if (first != null && first.path != null && File(first.path).exists()) {
                musicService?.playTrack(first.path)
            } else {
                Toast.makeText(this, "Нет доступных треков для воспроизведения", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Нет треков для перемешивания", Toast.LENGTH_SHORT).show()
        }
    }

    private fun preloadAllCachedUI() {
        lifecycleScope.launch {
            try {
                // Tracks
                val cachedTracks = withContext(Dispatchers.IO) { DataCache.loadTracks(this@MainActivity) } ?: emptyList()
                if (cachedTracks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        tracks.clear()
                        tracks.addAll(cachedTracks)
                        applyFilterAndSort(true)
                        areTracksLoaded = true
                    }
                }

                // Playlists
                val cachedPlaylists = withContext(Dispatchers.IO) { DataCache.loadPlaylists(this@MainActivity) } ?: emptyList()
                if (cachedPlaylists.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        allPlaylists = cachedPlaylists.toMutableList()
                        playlistAdapter.updatePlaylists(allPlaylists)
                        arePlaylistsLoaded = true
                    }
                }

                // Albums
                val cachedAlbums = withContext(Dispatchers.IO) { DataCache.loadAlbums(this@MainActivity) } ?: emptyList()
                if (cachedAlbums.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        albumsOrder = loadAlbumOrder(cachedAlbums).toMutableList()
                        albumAdapter.updateAlbums(albumsOrder)
                        areAlbumsLoaded = true
                    }
                }

                // Artists
                val cachedArtists = withContext(Dispatchers.IO) { DataCache.loadArtists(this@MainActivity) } ?: emptyList()
                if (cachedArtists.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        artistsOrder = loadArtistOrder(cachedArtists).toMutableList()
                        artistAdapter.updateArtists(artistsOrder)
                        areArtistsLoaded = true
                    }
                }

                // Genres
                val cachedGenres = withContext(Dispatchers.IO) { DataCache.loadGenres(this@MainActivity) } ?: emptyList()
                if (cachedGenres.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        genresOrder = loadGenreOrder(cachedGenres).toMutableList()
                        genreAdapter.updateGenres(genresOrder)
                        areGenresLoaded = true
                    }
                }
            } catch (_: Exception) { }
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
        val coverImageView = dialogView.findViewById<ImageView>(R.id.createPlaylistCover)
        val createButton = dialogView.findViewById<Button>(R.id.btnCreatePlaylist)

        selectedCoverUri = null

        // Обработчик нажатия на обложку
        coverImageView.setOnClickListener {
            if (hasImagePermission()) {
                createPlaylistGalleryLauncher.launch("image/*")
            } else {
                Toast.makeText(this, "Требуется разрешение на доступ к изображениям", Toast.LENGTH_SHORT).show()
                checkAndRequestImagePermission()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setOnDismissListener {
                currentDialog = null
                selectedCoverUri = null
            }
            .create()
        setupDialogGradient(dialog)
        currentDialog = dialog

        setupDialogGradient(dialog)

        // Обработчик кнопки создания
        createButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            if (name.isNotEmpty()) {
                val playlist = Playlist(name = name, coverUri = selectedCoverUri?.toString())
                PlaylistManager.addPlaylist(this, playlist)
                playlistAdapter.updatePlaylists(PlaylistManager.getPlaylists())
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Введите название плейлиста", Toast.LENGTH_SHORT).show()
            }
        }

        // Настройка цветов текста
        val titleView = dialog.findViewById<TextView>(android.R.id.title)
        titleView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        dialog.show()

        // Включение иммерсивного режима для диалога
        dialog.window?.let { window ->
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    // Добавьте эту константу в класс MainActivity
    companion object {
        private const val CROP_IMAGE_REQUEST = 1001
    }

    private fun restoreColor() {
        val primary = ThemeManager.getPrimaryColor(this)
        val secondary = ThemeManager.getSecondaryColor(this)
        val accent = ThemeManager.getAccentColor(this)
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)

        // Градиентный фон
        val gd = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(gradStart, gradEnd)
        )
        mainLayout.background = gd

        // Мини-плеер: тот же градиент, но немного светлее
        val lightStart = lighten(gradStart, 0.15f)
        val lightEnd = lighten(gradEnd, 0.15f)
        val miniGd = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(lightStart, lightEnd)
        )
        miniPlayer.background = miniGd
        // Тонируем прогресс-бары под accent и приглушенный фон
        miniPlayerProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(accent)
        val bgTint = android.graphics.Color.argb(80, 255, 255, 255)
        miniPlayerProgressBar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(bgTint)
        // Цвет текста вкладок по умолчанию = secondary
        (tabTracks.getChildAt(0) as? TextView)?.setTextColor(secondary)
        (tabPlaylists.getChildAt(0) as? TextView)?.setTextColor(secondary)
        (tabAlbums.getChildAt(0) as? TextView)?.setTextColor(secondary)
        (tabArtists.getChildAt(0) as? TextView)?.setTextColor(secondary)
        (tabGenres.getChildAt(0) as? TextView)?.setTextColor(secondary)
        // Индикаторы перекрасим в accent
        findViewById<View>(R.id.tabTracksIndicator)?.setBackgroundColor(accent)
        findViewById<View>(R.id.tabPlaylistsIndicator)?.setBackgroundColor(accent)
        findViewById<View>(R.id.tabAlbumsIndicator)?.setBackgroundColor(accent)
        findViewById<View>(R.id.tabArtistsIndicator)?.setBackgroundColor(accent)
        findViewById<View>(R.id.tabGenresIndicator)?.setBackgroundColor(accent)
        // Текст статистики
        trackStatsText.setTextColor(secondary)

        reapplyBarsFromBackground()
    }
    private fun selectTab(selectedTab: LinearLayout) {
        findViewById<View>(R.id.tabTracksIndicator).visibility = View.GONE
        findViewById<View>(R.id.tabPlaylistsIndicator).visibility = View.GONE
        findViewById<View>(R.id.tabAlbumsIndicator).visibility = View.GONE
        findViewById<View>(R.id.tabArtistsIndicator).visibility = View.GONE
        findViewById<View>(R.id.tabGenresIndicator).visibility = View.GONE

        val inactive = ThemeManager.getSecondaryColor(this)
        (tabTracks.getChildAt(0) as? TextView)?.setTextColor(inactive)
        (tabPlaylists.getChildAt(0) as? TextView)?.setTextColor(inactive)
        (tabAlbums.getChildAt(0) as? TextView)?.setTextColor(inactive)
        (tabArtists.getChildAt(0) as? TextView)?.setTextColor(inactive)
        (tabGenres.getChildAt(0) as? TextView)?.setTextColor(inactive)

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
                (tabTracks.getChildAt(0) as? TextView)?.setTextColor(ThemeManager.getAccentColor(this))
                contentTracks.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.trackButtons).visibility = View.VISIBLE
                trackStatsText.visibility = View.VISIBLE
                if (!areTracksLoaded) {
                    checkPermissionsAndLoadTracks()
                    areTracksLoaded = true
                }
                // Показ мини-плеера только на вкладке Треки
                miniPlayer.visibility = if (MusicService.currentTrack != null) View.VISIBLE else View.GONE
                animateUnderlineTo(R.id.tabTracksIndicator)
                if (MusicService.isPlaying) startAvatarSpin() else pauseAvatarSpin()
            }
            R.id.tabPlaylists -> {
                findViewById<View>(R.id.tabPlaylistsIndicator).visibility = View.VISIBLE
                (tabPlaylists.getChildAt(0) as? TextView)?.setTextColor(ThemeManager.getAccentColor(this))
                contentPlaylists.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.playlistButtons).visibility = View.VISIBLE
                if (!arePlaylistsLoaded) {
                    loadPlaylistsData()
                    arePlaylistsLoaded = true
                }
                miniPlayer.visibility = View.GONE
                animateUnderlineTo(R.id.tabPlaylistsIndicator)
                stopAvatarSpin()
            }
            R.id.tabAlbums -> {
                findViewById<View>(R.id.tabAlbumsIndicator).visibility = View.VISIBLE
                (tabAlbums.getChildAt(0) as? TextView)?.setTextColor(ThemeManager.getAccentColor(this))
                contentAlbums.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.albumButtons).visibility = View.VISIBLE
                if (!areAlbumsLoaded) {
                    loadAlbumsData()
                    areAlbumsLoaded = true
                }
                miniPlayer.visibility = View.GONE
                animateUnderlineTo(R.id.tabAlbumsIndicator)
                stopAvatarSpin()
            }
            R.id.tabArtists -> {
                findViewById<View>(R.id.tabArtistsIndicator).visibility = View.VISIBLE
                (tabArtists.getChildAt(0) as? TextView)?.setTextColor(ThemeManager.getAccentColor(this))
                contentArtists.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.artistButtons).visibility = View.VISIBLE
                if (!areArtistsLoaded) {
                    loadArtistsData()
                    areArtistsLoaded = true
                }
                miniPlayer.visibility = View.GONE
                animateUnderlineTo(R.id.tabArtistsIndicator)
                stopAvatarSpin()
            }
            R.id.tabGenres -> {
                findViewById<View>(R.id.tabGenresIndicator).visibility = View.VISIBLE
                (tabGenres.getChildAt(0) as? TextView)?.setTextColor(ThemeManager.getAccentColor(this))
                contentGenres.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.genreButtons).visibility = View.VISIBLE
                if (!areGenresLoaded) {
                    loadGenresData()
                    areGenresLoaded = true
                }
                miniPlayer.visibility = View.GONE
                animateUnderlineTo(R.id.tabGenresIndicator)
                stopAvatarSpin()
            }
        }
        // Перекрасить системные панели после смены вкладки
        reapplyBarsFromBackground()
    }

    private fun initTabIndicators() {
        val indicators = listOf(
            findViewById<View>(R.id.tabTracksIndicator),
            findViewById<View>(R.id.tabPlaylistsIndicator),
            findViewById<View>(R.id.tabAlbumsIndicator),
            findViewById<View>(R.id.tabArtistsIndicator),
            findViewById<View>(R.id.tabGenresIndicator)
        )
        for (v in indicators) {
            v.scaleX = if (v.visibility == View.VISIBLE) 1f else 0f
            v.alpha = 1f
            v.pivotX = v.width / 2f
        }
    }

    private fun animateUnderlineTo(selectedIndicatorId: Int) {
        val ids = listOf(
            R.id.tabTracksIndicator,
            R.id.tabPlaylistsIndicator,
            R.id.tabAlbumsIndicator,
            R.id.tabArtistsIndicator,
            R.id.tabGenresIndicator
        )
        for (id in ids) {
            val v = findViewById<View>(id)
            v.pivotX = (v.width / 2f)
            if (id == selectedIndicatorId) {
                v.visibility = View.VISIBLE
                v.animate().cancel()
                if (v.scaleX < 1f) v.scaleX = 0f
                v.animate().scaleX(1f).setDuration(200).start()
            } else {
                v.animate().cancel()
                v.animate().scaleX(0f).setDuration(200).withEndAction {
                    v.visibility = View.GONE
                }.start()
            }
        }
    }

    private fun attachAppearAnimation(rv: RecyclerView) {
        rv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.alpha = 0.85f
                view.scaleX = 0.96f
                view.scaleY = 0.96f
                view.translationY = dp(8).toFloat()
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(160)
                    .start()
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
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

        // Находим основной контейнер для градиента
        val dialogContentLayout = dialogView.findViewById<LinearLayout>(R.id.dialogContentLayout)

        val sortTypeGroup = dialogView.findViewById<RadioGroup>(R.id.sortTypeGroup)
        val btnSortAsc = dialogView.findViewById<ImageButton>(R.id.btnSortAsc)
        val btnSortDesc = dialogView.findViewById<ImageButton>(R.id.btnSortDesc)
        val sortAscContainer = dialogView.findViewById<LinearLayout>(R.id.sortAscContainer)
        val sortDescContainer = dialogView.findViewById<LinearLayout>(R.id.sortDescContainer)
        val sortDirectionIndicator = dialogView.findViewById<View>(R.id.sortDirectionIndicator)
        val btnApplySort = dialogView.findViewById<Button>(R.id.btnApplySort)
        val btnCancelSort = dialogView.findViewById<Button>(R.id.btnCancelSort)

        // Установка текущих значений
        when (sortType) {
            0 -> sortTypeGroup.check(R.id.sortByDate)
            1 -> sortTypeGroup.check(R.id.sortByName)
            2 -> sortTypeGroup.check(R.id.sortByArtist)
            3 -> sortTypeGroup.check(R.id.sortByDuration)
            4 -> sortTypeGroup.check(R.id.sortByPlays)
        }

        // Обновление индикатора направления
        updateSortDirectionIndicator(sortDirectionIndicator, sortAscending)

        // Обработчики направления сортировки
        val directionClickListener = View.OnClickListener { v ->
            when (v.id) {
                R.id.sortAscContainer, R.id.btnSortAsc -> {
                    sortAscending = true
                    updateSortDirectionIndicator(sortDirectionIndicator, true)
                }
                R.id.sortDescContainer, R.id.btnSortDesc -> {
                    sortAscending = false
                    updateSortDirectionIndicator(sortDirectionIndicator, false)
                }
            }
        }

        sortAscContainer.setOnClickListener(directionClickListener)
        sortDescContainer.setOnClickListener(directionClickListener)
        btnSortAsc.setOnClickListener(directionClickListener)
        btnSortDesc.setOnClickListener(directionClickListener)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Установка серого фона для диалога
        setupDialogGradient(dialog)

        btnApplySort.setOnClickListener {
            sortType = when (sortTypeGroup.checkedRadioButtonId) {
                R.id.sortByDate -> 0
                R.id.sortByName -> 1
                R.id.sortByArtist -> 2
                R.id.sortByDuration -> 3
                R.id.sortByPlays -> 4
                else -> 0
            }
            applyFilterAndSort(true)
            dialog.dismiss()
        }

        btnCancelSort.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Настройка размера окна
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    // Вспомогательный метод для обновления индикатора направления
    private fun updateSortDirectionIndicator(indicator: View, isAscending: Boolean) {
        val params = indicator.layoutParams as RelativeLayout.LayoutParams
        if (isAscending) {
            params.addRule(RelativeLayout.ALIGN_PARENT_START)
            params.removeRule(RelativeLayout.ALIGN_PARENT_END)
        } else {
            params.addRule(RelativeLayout.ALIGN_PARENT_END)
            params.removeRule(RelativeLayout.ALIGN_PARENT_START)
        }
        indicator.layoutParams = params
    }

    private fun setupDialogGradient(dialog: AlertDialog) {
        // Заменяем градиент на серый фон
        val bg = ContextCompat.getDrawable(this, R.drawable.dialog_background)
        dialog.window?.setBackgroundDrawable(bg)

        // Устанавливаем темные цвета для системных баров
        val darkColor = Color.parseColor("#D3D3D3")
        dialog.window?.let { window ->
            window.statusBarColor = darkColor
            window.navigationBarColor = darkColor

            // Светлые иконки в статус-баре для лучшей читаемости на темном фоне
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = window.decorView.systemUiVisibility
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                window.decorView.systemUiVisibility = flags
            }

            // Для навигационной панели (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                var flags = window.decorView.systemUiVisibility
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                window.decorView.systemUiVisibility = flags
            }
        }
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
            4 -> {
                if (sortAscending) {
                    filteredTracks.sortBy { ListeningStats.getPlayCount(it.path ?: "") }
                } else {
                    filteredTracks.sortByDescending { ListeningStats.getPlayCount(it.path ?: "") }
                }
            }
        }

        if (notify) {
            trackAdapter.notifyDataSetChanged()
        }

        updateTrackStats(filteredTracks)
    }

    private fun showPopupMenu(view: View) {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.main_menu, null)

        val popupWindow = PopupWindow(
            popupView,
            650,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Устанавливаем темный фон
        val background = ContextCompat.getDrawable(this, R.drawable.popup_menu_background)
        popupWindow.setBackgroundDrawable(background)
        popupWindow.elevation = 0f
        popupWindow.isOutsideTouchable = true

        // Находим кнопки и устанавливаем обработчики
        val settingsBtn = popupView.findViewById<LinearLayout>(R.id.btn_settings)
        val queueBtn = popupView.findViewById<LinearLayout>(R.id.btn_queue)
        val statsBtn = popupView.findViewById<LinearLayout>(R.id.btn_stats)
        val clearCacheBtn = popupView.findViewById<LinearLayout>(R.id.btn_clear_cache)

        settingsBtn.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            popupWindow.dismiss()
        }

        queueBtn.setOnClickListener {
            val intent = Intent(this, QueueActivity::class.java)
            startActivity(intent)
            popupWindow.dismiss()
        }

        statsBtn.setOnClickListener {
            val intent = Intent(this, StatsActivity::class.java)
            startActivity(intent)
            popupWindow.dismiss()
        }

        clearCacheBtn.setOnClickListener {
            DiskImageCache.clear()
            DataCache.clearAll(this)
            Toast.makeText(this, "Кэш очищен", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        // Показываем popup
        popupWindow.showAsDropDown(view)
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

    private fun lighten(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        val nr = (r + ((255 - r) * factor)).toInt().coerceIn(0, 255)
        val ng = (g + ((255 - g) * factor)).toInt().coerceIn(0, 255)
        val nb = (b + ((255 - b) * factor)).toInt().coerceIn(0, 255)
        return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    private fun updateActiveTabColors() {
        val accent = ThemeManager.getAccentColor(this)
        val secondary = ThemeManager.getSecondaryColor(this)
        // Сбросим все
        (tabTracks.getChildAt(0) as? TextView)?.setTextColor(secondary)
        (tabPlaylists.getChildAt(0) as? TextView)?.setTextColor(secondary)
        (tabAlbums.getChildAt(0) as? TextView)?.setTextColor(secondary)
        (tabArtists.getChildAt(0) as? TextView)?.setTextColor(secondary)
        (tabGenres.getChildAt(0) as? TextView)?.setTextColor(secondary)
        // Активную окрасим в accent по видимому контейнеру
        when {
            contentTracks.visibility == View.VISIBLE -> (tabTracks.getChildAt(0) as? TextView)?.setTextColor(accent)
            contentPlaylists.visibility == View.VISIBLE -> (tabPlaylists.getChildAt(0) as? TextView)?.setTextColor(accent)
            contentAlbums.visibility == View.VISIBLE -> (tabAlbums.getChildAt(0) as? TextView)?.setTextColor(accent)
            contentArtists.visibility == View.VISIBLE -> (tabArtists.getChildAt(0) as? TextView)?.setTextColor(accent)
            contentGenres.visibility == View.VISIBLE -> (tabGenres.getChildAt(0) as? TextView)?.setTextColor(accent)
        }
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
}