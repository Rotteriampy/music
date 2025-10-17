package com.example.music

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.core.content.ContextCompat

class EditTrackActivity : AppCompatActivity() {

    private lateinit var trackPath: String
    private lateinit var editTrackName: EditText
    private lateinit var editTrackArtist: EditText
    private lateinit var editTrackAlbum: EditText
    private lateinit var editTrackGenre: EditText
    private lateinit var btnSaveTags: Button
    private lateinit var btnBack: ImageButton

    private var pendingTitle = ""
    private var pendingArtist = ""
    private var pendingAlbum = ""
    private var pendingGenre = ""
    private var tempFile: File? = null

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Разрешение получено, копируем отредактированный файл обратно
            tempFile?.let { temp ->
                copyTempFileBack(temp)
            }
        } else {
            Toast.makeText(this, "Отказано в разрешении", Toast.LENGTH_SHORT).show()
            btnSaveTags.isEnabled = true
            btnSaveTags.text = "Сохранить"
            tempFile?.delete()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_track)

        trackPath = intent.getStringExtra("TRACK_PATH") ?: run {
            Toast.makeText(this, "Ошибка: путь к файлу не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        editTrackName = findViewById(R.id.editTrackName)
        editTrackArtist = findViewById(R.id.editTrackArtist)
        editTrackAlbum = findViewById(R.id.editTrackAlbum)
        editTrackGenre = findViewById(R.id.editTrackGenre)
        btnSaveTags = findViewById(R.id.btnSaveTags)
        btnBack = findViewById(R.id.btnBackEdit)

        btnBack.setOnClickListener {
            finish()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        loadCurrentMetadata()

        btnSaveTags.setOnClickListener {
            saveMetadataToFile()
        }
    }

    private fun loadCurrentMetadata() {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(trackPath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)

            editTrackName.setText(title ?: File(trackPath).nameWithoutExtension)
            editTrackArtist.setText(artist ?: "Unknown Artist")
            editTrackAlbum.setText(album ?: "Unknown Album")
            editTrackGenre.setText(genre ?: "Unknown Genre")

            retriever.release()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки метаданных", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun saveMetadataToFile() {
        val newTitle = editTrackName.text.toString().trim()
        val newArtist = editTrackArtist.text.toString().trim()
        val newAlbum = editTrackAlbum.text.toString().trim()
        val newGenre = editTrackGenre.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }

        pendingTitle = newTitle
        pendingArtist = newArtist
        pendingAlbum = newAlbum
        pendingGenre = newGenre

        btnSaveTags.isEnabled = false
        btnSaveTags.text = "Сохранение..."

        performSave(newTitle, newArtist, newAlbum, newGenre)
    }

    private fun performSave(newTitle: String, newArtist: String, newAlbum: String, newGenre: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Копируем файл во временную директорию приложения
                val originalFile = File(trackPath)
                tempFile = File(cacheDir, "temp_${originalFile.name}")

                FileInputStream(originalFile).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Редактируем временный файл
                val audioFile = AudioFileIO.read(tempFile)
                val tag = audioFile.tagOrCreateAndSetDefault

                tag.setField(FieldKey.TITLE, newTitle)
                tag.setField(FieldKey.ARTIST, newArtist)
                tag.setField(FieldKey.ALBUM, newAlbum)
                tag.setField(FieldKey.GENRE, newGenre)

                audioFile.commit()

                // Теперь пробуем скопировать обратно
                copyTempFileBack(tempFile!!)

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTrackActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    btnSaveTags.isEnabled = true
                    btnSaveTags.text = "Сохранить"
                }
                tempFile?.delete()
                e.printStackTrace()
            }
        }
    }

    private fun copyTempFileBack(temp: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Получаем URI файла
                val contentResolver = contentResolver
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val selection = "${MediaStore.Audio.Media.DATA} = ?"
                val selectionArgs = arrayOf(trackPath)

                val cursor = contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), selection, selectionArgs, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        val fileUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                        // Пробуем записать
                        try {
                            contentResolver.openOutputStream(fileUri, "wt")?.use { output ->
                                FileInputStream(temp).use { input ->
                                    input.copyTo(output)
                                }
                            }

                            // Обновляем метаданные в MediaStore
                            val values = ContentValues().apply {
                                put(MediaStore.Audio.Media.TITLE, pendingTitle)
                                put(MediaStore.Audio.Media.ARTIST, pendingArtist)
                                put(MediaStore.Audio.Media.ALBUM, pendingAlbum)
                            }
                            contentResolver.update(fileUri, values, null, null)

                            // Сканируем файл
                            MediaScannerConnection.scanFile(
                                this@EditTrackActivity,
                                arrayOf(trackPath),
                                null,
                                null
                            )

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@EditTrackActivity, "Теги сохранены!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            temp.delete()

                        } catch (securityException: SecurityException) {
                            // Запрашиваем разрешение
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val recoverableSecurityException = securityException as? RecoverableSecurityException
                                    ?: throw RuntimeException(securityException.message, securityException)

                                val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender

                                withContext(Dispatchers.Main) {
                                    val request = IntentSenderRequest.Builder(intentSender).build()
                                    writePermissionLauncher.launch(request)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTrackActivity, "Ошибка записи: ${e.message}", Toast.LENGTH_LONG).show()
                    btnSaveTags.isEnabled = true
                    btnSaveTags.text = "Сохранить"
                }
                temp.delete()
                e.printStackTrace()
            }
        }
    }
}