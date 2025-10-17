package com.example.music

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.widget.ImageButton
import android.os.Build

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

    enum class SearchType {
        TRACKS, PLAYLISTS, ALBUMS, ARTISTS, GENRES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        searchLayout = findViewById(R.id.searchLayout)
        searchEditText = findViewById(R.id.searchEditText)
        searchTypeGroup = findViewById(R.id.searchTypeGroup)
        rbTracks = findViewById(R.id.rbTracks)
        rbPlaylists = findViewById(R.id.rbPlaylists)
        rbAlbums = findViewById(R.id.rbAlbums)
        rbArtists = findViewById(R.id.rbArtists)
        rbGenres = findViewById(R.id.rbGenres)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)
        val btnBackSearch = findViewById<ImageButton>(R.id.btnBackSearch)

        btnBackSearch.setOnClickListener {
            finish()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        restoreColor()
        loadAllData()

        // Настройка адаптеров
        trackAdapter = TrackAdapter(filteredTracks, false)
        playlistAdapter = PlaylistAdapter(mutableListOf(), this)
        albumAdapter = AlbumAdapter(listOf(), this)
        artistAdapter = ArtistAdapter(listOf(), this)

        // По умолчанию - поиск треков
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = trackAdapter

        // Поиск
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString())
            }
        })

        // Переключение типа поиска
        searchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbTracks -> {
                    currentSearchType = SearchType.TRACKS
                    resultsRecyclerView.layoutManager = LinearLayoutManager(this)
                    resultsRecyclerView.adapter = trackAdapter
                    performSearch(searchEditText.text.toString())
                }
                R.id.rbPlaylists -> {
                    currentSearchType = SearchType.PLAYLISTS
                    resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
                    resultsRecyclerView.adapter = playlistAdapter
                    performSearch(searchEditText.text.toString())
                }
                R.id.rbAlbums -> {
                    currentSearchType = SearchType.ALBUMS
                    resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
                    resultsRecyclerView.adapter = albumAdapter
                    performSearch(searchEditText.text.toString())
                }
                R.id.rbArtists -> {
                    currentSearchType = SearchType.ARTISTS
                    resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
                    resultsRecyclerView.adapter = artistAdapter
                    performSearch(searchEditText.text.toString())
                }
                R.id.rbGenres -> {
                    currentSearchType = SearchType.GENRES
                    resultsRecyclerView.layoutManager = GridLayoutManager(this, 2)
                    resultsRecyclerView.adapter = genreAdapter
                    performSearch(searchEditText.text.toString())
                }
            }
        }

        // Фокус на поле поиска
        searchEditText.requestFocus()
    }

    private fun loadAllData() {
        loadAllTracks()
        allPlaylists = PlaylistManager.getPlaylists()
        allAlbums = AlbumManager.getAlbumsFromTracks(this)
        allArtists = ArtistManager.getArtistsFromTracks(this)
        allGenres = GenreManager.getGenresFromTracks(this)
    }

    private fun loadAllTracks() {
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
    }

    private fun performSearch(query: String) {
        when (currentSearchType) {
            SearchType.TRACKS -> searchTracks(query)
            SearchType.PLAYLISTS -> searchPlaylists(query)
            SearchType.ALBUMS -> searchAlbums(query)
            SearchType.ARTISTS -> searchArtists(query)
            SearchType.GENRES -> searchGenres(query)
        }
    }

    private fun searchTracks(query: String) {
        filteredTracks.clear()
        if (query.isEmpty()) {
            filteredTracks.addAll(allTracks)
        } else {
            filteredTracks.addAll(
                allTracks.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.artist?.contains(query, ignoreCase = true) == true
                }
            )
        }
        trackAdapter.notifyDataSetChanged()
    }

    private fun searchPlaylists(query: String) {
        val filtered = if (query.isEmpty()) {
            allPlaylists
        } else {
            allPlaylists.filter { it.name.contains(query, ignoreCase = true) }
        }
        playlistAdapter.updatePlaylists(filtered)
    }

    private fun searchAlbums(query: String) {
        val filtered = if (query.isEmpty()) {
            allAlbums
        } else {
            allAlbums.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.artist?.contains(query, ignoreCase = true) == true
            }
        }
        albumAdapter.updateAlbums(filtered)
    }

    private fun searchArtists(query: String) {
        val filtered = if (query.isEmpty()) {
            allArtists
        } else {
            allArtists.filter { it.name.contains(query, ignoreCase = true) }
        }
        artistAdapter.updateArtists(filtered)
    }

    private fun searchGenres(query: String) {
        val filtered = if (query.isEmpty()) {
            allGenres
        } else {
            allGenres.filter { it.name.contains(query, ignoreCase = true) }
        }
        genreAdapter.updateGenres(filtered)
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
        searchLayout.background = color
    }

    override fun onResume() {
        super.onResume()
        // Обновляем адаптер при возврате на экран
        trackAdapter.notifyDataSetChanged()
    }
}