package com.example.music

import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.File
import androidx.core.content.ContextCompat

class SearchActivity : AppCompatActivity() {

    private lateinit var searchEditText: EditText
    private lateinit var searchTypeGroup: RadioGroup
    private lateinit var rbTracks: RadioButton
    private lateinit var rbPlaylists: RadioButton
    private lateinit var rbAlbums: RadioButton
    private lateinit var rbArtists: RadioButton
    private lateinit var rbGenres: RadioButton
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var searchLayout: LinearLayout
    private lateinit var btnClearSearch: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateView: TextView

    private lateinit var trackAdapter: TrackAdapter
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var artistAdapter: ArtistAdapter
    private lateinit var genreAdapter: GenreAdapter

    private val allTracks = mutableListOf<Track>()
    private val filteredTracks = mutableListOf<Track>()
    private var allPlaylists: List<Playlist> = listOf()
    private var allAlbums: List<Album> = listOf()
    private var allArtists: List<Artist> = listOf()
    private var allGenres: List<Genre> = listOf()

    private var currentSearchType = SearchType.TRACKS
    private var searchJob: Job? = null
    private val searchHistory = mutableListOf<String>()
    private val MAX_HISTORY = 10

    enum class SearchType {
        TRACKS, PLAYLISTS, ALBUMS, ARTISTS, GENRES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        initViews()
        setupSystemBars()
        setupAdapters()
        setupSearchListeners()
        setupTypeSwitcher()
        loadAllData()

        searchEditText.requestFocus()
    }

    private fun initViews() {
        searchLayout = findViewById(R.id.searchLayout)
        searchEditText = findViewById(R.id.searchEditText)
        searchTypeGroup = findViewById(R.id.searchTypeGroup)
        rbTracks = findViewById(R.id.rbTracks)
        rbPlaylists = findViewById(R.id.rbPlaylists)
        rbAlbums = findViewById(R.id.rbAlbums)
        rbArtists = findViewById(R.id.rbArtists)
        rbGenres = findViewById(R.id.rbGenres)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        progressBar = findViewById(R.id.progressBar)

        val btnBackSearch: ImageButton = findViewById(R.id.btnBackSearch)
        btnBackSearch.setOnClickListener { finish() }

        // Настройка пустого состояния
        setupEmptyState()
    }

    private fun setupSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            run {
                val flags = (window.decorView.systemUiVisibility
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                window.decorView.systemUiVisibility = flags
            }
            val secondary = ThemeManager.getSecondaryColor(this)
            val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
            ThemeManager.applyTransparentStatusBar(window, darkIcons)
        }
        restoreColor()
    }

    private fun setupAdapters() {
        trackAdapter = TrackAdapter(filteredTracks, false)
        playlistAdapter = PlaylistAdapter(mutableListOf(), this)
        albumAdapter = AlbumAdapter(listOf(), this)
        artistAdapter = ArtistAdapter(listOf(), this)
        genreAdapter = GenreAdapter(this, listOf())

        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = trackAdapter
    }

    private fun setupSearchListeners() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                searchJob?.cancel()
                searchJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(300) // debounce 300ms
                    performSearch(query)
                }
            }
        })

        btnClearSearch.setOnClickListener {
            searchEditText.text.clear()
            btnClearSearch.visibility = View.GONE
            performSearch("")
        }

        // Обработка кнопки поиска на клавиатуре
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchEditText.text.toString()
                if (query.isNotEmpty()) {
                    saveToHistory(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupTypeSwitcher() {
        searchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbTracks -> switchSearchType(SearchType.TRACKS)
                R.id.rbPlaylists -> switchSearchType(SearchType.PLAYLISTS)
                R.id.rbAlbums -> switchSearchType(SearchType.ALBUMS)
                R.id.rbArtists -> switchSearchType(SearchType.ARTISTS)
                R.id.rbGenres -> switchSearchType(SearchType.GENRES)
            }
        }
    }

    private fun switchSearchType(newType: SearchType) {
        currentSearchType = newType

        val newAdapter = when (newType) {
            SearchType.TRACKS -> {
                resultsRecyclerView.layoutManager = LinearLayoutManager(this)
                trackAdapter
            }
            SearchType.PLAYLISTS -> {
                resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
                playlistAdapter
            }
            SearchType.ALBUMS -> {
                resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
                albumAdapter
            }
            SearchType.ARTISTS -> {
                resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
                artistAdapter
            }
            SearchType.GENRES -> {
                resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
                genreAdapter
            }
        }

        resultsRecyclerView.adapter = newAdapter
        performSearch(searchEditText.text.toString())
    }

    private fun setupEmptyState() {
        // Создаем пустое состояние программно вместо использования отдельного layout
        emptyStateView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            setTextColor(ContextCompat.getColor(this@SearchActivity, android.R.color.darker_gray))
            textSize = 16f
            text = "Введите запрос для поиска"
            setPadding(0, 48.dpToPx(), 0, 0)
        }

        // Добавляем пустое состояние в родительский контейнер
        val parent = resultsRecyclerView.parent as? ViewGroup
        parent?.addView(emptyStateView)
        emptyStateView.visibility = View.GONE
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun loadAllData() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                // Загрузка треков с кэшированием
                val cachedTracks = withContext(Dispatchers.IO) {
                    DataCache.loadTracks(this@SearchActivity)
                }
                if (cachedTracks != null && cachedTracks.isNotEmpty()) {
                    allTracks.clear()
                    allTracks.addAll(cachedTracks)
                } else {
                    loadAllTracksFromStorage()
                }

                // Загрузка остальных данных с кэшированием
                allPlaylists = PlaylistManager.getPlaylists()
                allAlbums = DataCache.loadAlbums(this@SearchActivity) ?:
                        AlbumManager.getAlbumsFromTracks(this@SearchActivity)
                allArtists = DataCache.loadArtists(this@SearchActivity) ?:
                        ArtistManager.getArtistsFromTracks(this@SearchActivity)
                allGenres = DataCache.loadGenres(this@SearchActivity) ?:
                        GenreManager.getGenresFromTracks(this@SearchActivity)

                performSearch(searchEditText.text.toString())

            } catch (e: Exception) {
                handleSearchError(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun loadAllTracksFromStorage() {
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
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            allTracks.clear()
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
                    allTracks.add(
                        Track(
                            id = id,
                            name = title,
                            artist = artist,
                            albumId = albumId,
                            albumName = album,
                            genre = null,
                            path = path,
                            duration = duration,
                            dateModified = dateModified
                        )
                    )
                }
            }
        }

        // Сохраняем в кэш
        CoroutineScope(Dispatchers.IO).launch {
            DataCache.saveTracks(this@SearchActivity, allTracks)
        }
    }

    private fun performSearch(query: String) {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isEmpty()) {
            showEmptyState(true, "")
            return
        }

        showLoading(true)

        try {
            when (currentSearchType) {
                SearchType.TRACKS -> searchTracks(trimmedQuery)
                SearchType.PLAYLISTS -> searchPlaylists(trimmedQuery)
                SearchType.ALBUMS -> searchAlbums(trimmedQuery)
                SearchType.ARTISTS -> searchArtists(trimmedQuery)
                SearchType.GENRES -> searchGenres(trimmedQuery)
            }

            if (trimmedQuery.isNotEmpty()) {
                saveToHistory(trimmedQuery)
            }

        } catch (e: Exception) {
            handleSearchError(e)
        } finally {
            showLoading(false)
        }
    }

    private fun searchTracks(query: String) {
        filteredTracks.clear()
        val results = allTracks.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.artist?.contains(query, ignoreCase = true) == true ||
                    it.albumName?.contains(query, ignoreCase = true) == true
        }
        filteredTracks.addAll(results)
        trackAdapter.notifyDataSetChanged()
        showEmptyState(results.isEmpty(), query)
    }

    private fun searchPlaylists(query: String) {
        val results = if (query.isEmpty()) {
            allPlaylists
        } else {
            allPlaylists.filter { it.name.contains(query, ignoreCase = true) }
        }
        playlistAdapter.updatePlaylists(results)
        showEmptyState(results.isEmpty(), query)
    }

    private fun searchAlbums(query: String) {
        val results = if (query.isEmpty()) {
            allAlbums
        } else {
            allAlbums.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.artist?.contains(query, ignoreCase = true) == true
            }
        }
        albumAdapter.updateAlbums(results)
        showEmptyState(results.isEmpty(), query)
    }

    private fun searchArtists(query: String) {
        val results = if (query.isEmpty()) {
            allArtists
        } else {
            allArtists.filter { it.name.contains(query, ignoreCase = true) }
        }
        artistAdapter.updateArtists(results)
        showEmptyState(results.isEmpty(), query)
    }

    private fun searchGenres(query: String) {
        val results = if (query.isEmpty()) {
            allGenres
        } else {
            allGenres.filter { it.name.contains(query, ignoreCase = true) }
        }
        genreAdapter.updateGenres(results)
        showEmptyState(results.isEmpty(), query)
    }

    private fun saveToHistory(query: String) {
        if (query.isNotEmpty() && !searchHistory.contains(query)) {
            searchHistory.remove(query)
            searchHistory.add(0, query)
            if (searchHistory.size > MAX_HISTORY) {
                // Заменяем removeLast() на совместимую версию
                searchHistory.removeAt(searchHistory.lastIndex)
            }
            // Можно сохранить в SharedPreferences для постоянного хранения
            val prefs = getSharedPreferences("search_history", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("history", searchHistory.toSet()).apply()
        }
    }

    private fun showEmptyState(show: Boolean, query: String = "") {
        if (show) {
            emptyStateView.text = if (query.isNotEmpty()) {
                "По запросу \"$query\" ничего не найдено"
            } else {
                "Введите запрос для поиска"
            }
            emptyStateView.visibility = View.VISIBLE
            resultsRecyclerView.visibility = View.GONE
        } else {
            emptyStateView.visibility = View.GONE
            resultsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            resultsRecyclerView.visibility = View.GONE
            emptyStateView.visibility = View.GONE
        }
    }

    private fun handleSearchError(error: Throwable) {
        Toast.makeText(this, "Ошибка поиска: ${error.message}", Toast.LENGTH_SHORT).show()
        showEmptyState(true, searchEditText.text.toString())
    }

    private fun restoreColor() {
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)
        val bg = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(gradStart, gradEnd)
        )
        searchLayout.background = bg
    }

    override fun onResume() {
        super.onResume()
        trackAdapter.notifyDataSetChanged()
        val secondary = ThemeManager.getSecondaryColor(this)
        val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        ThemeManager.showSystemBars(window, this)

        // Загрузка истории поиска
        val prefs = getSharedPreferences("search_history", Context.MODE_PRIVATE)
        val history = prefs.getStringSet("history", emptySet()) ?: emptySet()
        searchHistory.clear()
        searchHistory.addAll(history)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ThemeManager.showSystemBars(window, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}