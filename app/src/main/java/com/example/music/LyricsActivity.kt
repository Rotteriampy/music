package com.arotter.music

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import androidx.core.view.ViewCompat

class LyricsActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var lyricsEdit: EditText
    private lateinit var btnSave: Button
    private lateinit var btnFind: Button
    private lateinit var btnBack: android.widget.ImageButton

    private var trackPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics)

        titleText = findViewById(R.id.lyricsTitle)
        artistText = findViewById(R.id.lyricsArtist)
        lyricsEdit = findViewById(R.id.lyricsEdit)
        btnSave = findViewById(R.id.btnSaveLyrics)
        btnFind = findViewById(R.id.btnFindLyrics)
        btnBack = findViewById(R.id.btnBack)

        // Принудительно назначаем нужные фоны и снимаем системный tint, чтобы не был фиолетовым
        try {
            ViewCompat.setBackgroundTintList(btnSave, null)
            ViewCompat.setBackgroundTintList(btnFind, null)
            btnSave.setTextColor(android.graphics.Color.WHITE)
            btnFind.setTextColor(android.graphics.Color.WHITE)
        } catch (_: Exception) { }

        val trackName = intent.getStringExtra("TRACK_NAME") ?: ""
        val trackArtist = intent.getStringExtra("TRACK_ARTIST") ?: ""
        trackPath = intent.getStringExtra("TRACK_PATH")

        titleText.text = trackName
        artistText.text = trackArtist

        lyricsEdit.setText(loadLyrics())

        btnSave.setOnClickListener {
            saveLyrics(lyricsEdit.text?.toString() ?: "")
            finish()
        }

        btnFind.setOnClickListener {
            val query = "$trackName $trackArtist текст".trim()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val uri = Uri.parse("https://www.google.com/search?q=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val secondary = ThemeManager.getSecondaryColor(this)
        val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        ThemeManager.showSystemBars(window, this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ThemeManager.showSystemBars(window, this)
    }

    private fun prefs() = getSharedPreferences("lyrics_store", MODE_PRIVATE)

    private fun key(): String = "lyrics_" + (trackPath ?: "unknown")

    private fun loadLyrics(): String {
        return prefs().getString(key(), "") ?: ""
    }

    private fun saveLyrics(text: String) {
        prefs().edit().putString(key(), text).apply()
    }
}
