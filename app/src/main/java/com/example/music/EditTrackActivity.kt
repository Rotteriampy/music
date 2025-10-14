package com.example.music

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import java.io.File

class EditTrackActivity : AppCompatActivity() {

    private lateinit var trackPath: String
    private lateinit var etTitle: EditText
    private lateinit var etArtist: EditText
    private lateinit var etAlbum: EditText
    private lateinit var etGenre: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton
    private lateinit var rootLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_track) // Создайте layout ниже

        rootLayout = findViewById(R.id.editRootLayout)
        trackPath = intent.getStringExtra("TRACK_PATH") ?: return
        etTitle = findViewById(R.id.etTitle)
        etArtist = findViewById(R.id.etArtist)
        etAlbum = findViewById(R.id.etAlbum)
        etGenre = findViewById(R.id.etGenre)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        loadTags()

        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            saveTags()
        }

        restoreColor()
    }

    private fun loadTags() {
        try {
            val file = File(trackPath)
            val audioFile: AudioFile = AudioFileIO.read(file)
            val tag: Tag = audioFile.tagOrCreateAndSetDefault

            etTitle.setText(tag.getFirst(FieldKey.TITLE))
            etArtist.setText(tag.getFirst(FieldKey.ARTIST))
            etAlbum.setText(tag.getFirst(FieldKey.ALBUM))
            etGenre.setText(tag.getFirst(FieldKey.GENRE))
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки тегов: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTags() {
        try {
            val file = File(trackPath)
            val audioFile: AudioFile = AudioFileIO.read(file)
            val tag: Tag = audioFile.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE, etTitle.text.toString())
            tag.setField(FieldKey.ARTIST, etArtist.text.toString())
            tag.setField(FieldKey.ALBUM, etAlbum.text.toString())
            tag.setField(FieldKey.GENRE, etGenre.text.toString())

            audioFile.commit() // Сохраняет изменения в файл

            // Обновляем MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, etTitle.text.toString())
                put(MediaStore.Audio.Media.ARTIST, etArtist.text.toString())
                put(MediaStore.Audio.Media.ALBUM, etAlbum.text.toString())
                // Для жанра MediaStore не имеет прямого поля, но тег обновлён в файле
            }
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            contentResolver.update(uri, values, "${MediaStore.Audio.Media.DATA} = ?", arrayOf(trackPath))

            Toast.makeText(this, "Теги сохранены", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreColor() {
        val sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE)
        val scheme = sharedPreferences.getInt("color_scheme", 0)
        val color = when (scheme) {
            1 -> android.R.color.darker_gray
            2 -> android.R.color.holo_blue_dark
            3 -> android.R.color.holo_green_dark
            else -> android.R.color.black
        }
        rootLayout.setBackgroundColor(ContextCompat.getColor(this, color))
    }
}