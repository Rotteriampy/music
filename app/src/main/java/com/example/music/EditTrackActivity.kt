package com.example.music

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
import android.app.PendingIntent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.view.WindowManager
import android.graphics.Bitmap
import java.io.FileOutputStream
import androidx.activity.result.PickVisualMediaRequest

class EditTrackActivity : AppCompatActivity() {

    private lateinit var trackPath: String
    private lateinit var editTrackName: EditText
    private lateinit var editTrackArtist: EditText
    private lateinit var editTrackAlbum: EditText
    private lateinit var editTrackGenre: EditText
    private lateinit var btnSaveTags: Button
    private lateinit var btnBack: ImageButton
    private lateinit var coverImageView: ImageView
    private lateinit var btnSelectCover: Button

    private var selectedCoverUri: Uri? = null
    private val WRITE_PERMISSION_CODE = 100

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            // Запускаем активность обрезки
            val intent = Intent(this, CropImageActivity::class.java)
            intent.putExtra("imageUri", it)
            cropLauncher.launch(intent)
        }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedCoverUri = uri
                coverImageView.setImageURI(uri)
            }
        }
    }

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Разрешение получено, пробуем снова
            saveMetadataToFile()
        } else {
            Toast.makeText(this, "Отказано в разрешении", Toast.LENGTH_SHORT).show()
            btnSaveTags.isEnabled = true
            btnSaveTags.text = "Сохранить"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_track)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
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
        coverImageView = findViewById(R.id.coverImageView)
        btnSelectCover = findViewById(R.id.btnSelectCover)

        btnBack.setOnClickListener { finish() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        loadCurrentMetadata()

        btnSelectCover.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

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

            // Загружаем текущую обложку
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                coverImageView.setImageBitmap(bitmap)
            } else {
                coverImageView.setImageResource(R.drawable.ic_album_placeholder)
            }

            retriever.release()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки метаданных", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    // Добавьте эту функцию
    private fun checkWritePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    WRITE_PERMISSION_CODE
                )
                return false
            }
        }
        return true
    }

    // Добавьте обработчик результата
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение получено! Попробуйте снова", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Разрешение отклонено", Toast.LENGTH_SHORT).show()
            }
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
        if (!checkWritePermission()) {
            return
        }

        btnSaveTags.isEnabled = false
        btnSaveTags.text = "Сохранение..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = ID3TagEditor.updateTags(
                    trackPath,
                    newTitle,
                    newArtist,
                    newAlbum,
                    newGenre,
                    selectedCoverUri,
                    this@EditTrackActivity
                )

                if (success) {
                    // Обновляем MediaStore
                    updateMediaStore(newTitle, newArtist, newAlbum, newGenre)

                    // Сканируем файл
                    MediaScannerConnection.scanFile(
                        this@EditTrackActivity,
                        arrayOf(trackPath),
                        null
                    ) { path, uri ->
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(this@EditTrackActivity, "Теги сохранены!", Toast.LENGTH_SHORT).show()

                            // Обновляем треки в приложении
                            val intent = Intent("com.example.music.TAGS_UPDATED")
                            sendBroadcast(intent)

                            finish()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EditTrackActivity, "Ошибка сохранения тегов", Toast.LENGTH_SHORT).show()
                        btnSaveTags.isEnabled = true
                        btnSaveTags.text = "Сохранить"
                    }
                }

            } catch (securityException: SecurityException) {
                Log.e("EditTrackActivity", "SecurityException", securityException)

                // Обрабатываем RecoverableSecurityException только на Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        // Используем рефлексию для совместимости
                        val userAction = securityException.javaClass.getMethod("getUserAction").invoke(securityException)
                        val actionIntent = userAction?.javaClass?.getMethod("getActionIntent")?.invoke(userAction) as? PendingIntent

                        if (actionIntent != null) {
                            withContext(Dispatchers.Main) {
                                try {
                                    val intentSender = actionIntent.intentSender
                                    val request = IntentSenderRequest.Builder(intentSender).build()
                                    writePermissionLauncher.launch(request)
                                } catch (e: Exception) {
                                    Toast.makeText(this@EditTrackActivity, "Ошибка запроса разрешения: ${e.message}", Toast.LENGTH_LONG).show()
                                    btnSaveTags.isEnabled = true
                                    btnSaveTags.text = "Сохранить"
                                }
                            }
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.e("EditTrackActivity", "Error getting RecoverableSecurityException", e)
                    }
                }

                // Если не получилось - показываем общую ошибку
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@EditTrackActivity,
                        "Нет доступа к файлу. Android ${Build.VERSION.SDK_INT}. Попробуйте выдать разрешение \"Управление файлами\" в настройках приложения.",
                        Toast.LENGTH_LONG
                    ).show()
                    btnSaveTags.isEnabled = true
                    btnSaveTags.text = "Сохранить"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTrackActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    btnSaveTags.isEnabled = true
                    btnSaveTags.text = "Сохранить"
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun updateMediaStore(title: String, artist: String, album: String, genre: String) {
        try {
            val contentResolver = contentResolver
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(trackPath)

            val cursor = contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val fileUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, artist)
                        put(MediaStore.Audio.Media.ALBUM, album)
                    }
                    contentResolver.update(fileUri, values, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}