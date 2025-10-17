package com.example.music

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import android.widget.Toast
import android.content.Intent
import android.app.AlertDialog
import androidx.core.content.FileProvider
import android.os.Build
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout
    private lateinit var gradientPreview: LinearLayout

    companion object {
        private const val IMPORT_STATS_REQUEST = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        mainLayout = findViewById(R.id.settingsLayout)
        gradientPreview = findViewById(R.id.gradientPreview)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        val exportStatsButton = findViewById<Button>(R.id.exportStatsButton)
        val importStatsButton = findViewById<Button>(R.id.importStatsButton)
        val clearStatsButton = findViewById<Button>(R.id.clearStatsButton)
        exportStatsButton.setOnClickListener {
            exportStats()
        }
        importStatsButton.setOnClickListener {
            importStats()
        }
        clearStatsButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Очистить статистику?")
                .setMessage("Все данные о прослушиваниях будут удалены. Действие необратимо.")
                .setPositiveButton("Очистить") { _, _ ->
                    ListeningStats.clearStats(this)
                    Toast.makeText(this, "Статистика очищена", Toast.LENGTH_SHORT).show()
                    sendBroadcast(Intent("com.example.music.STATS_UPDATED"))
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun exportStats() {
        val file = File(getExternalFilesDir(null), "listening_stats.json")
        if (ListeningStats.exportToFile(this, file)) {
            AlertDialog.Builder(this)
                .setTitle("Статистика экспортирована")
                .setMessage("Файл сохранён:\n${file.absolutePath}\n\nХотите поделиться?")
                .setPositiveButton("Поделиться") { _, _ ->
                    try {
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Экспорт статистики"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("OK", null)
                .show()
        } else {
            Toast.makeText(this, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importStats() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/json",
                "text/plain",
                "application/octet-stream"
            ))
        }
        startActivityForResult(intent, IMPORT_STATS_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMPORT_STATS_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val jsonString = inputStream?.bufferedReader().use { it?.readText() }

                    if (jsonString != null) {
                        val tempFile = File(cacheDir, "temp_import.json")
                        tempFile.writeText(jsonString)

                        val (success, message) = ListeningStats.importFromFile(this, tempFile)

                        if (success) {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            sendBroadcast(Intent("com.example.music.STATS_UPDATED"))
                        } else {
                            // Показываем подробную ошибку в диалоге
                            AlertDialog.Builder(this)
                                .setTitle("Ошибка импорта")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show()
                        }

                        tempFile.delete()
                    } else {
                        Toast.makeText(this, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    val errorDetails = "Ошибка чтения файла:\n${e.javaClass.simpleName}\n${e.message}\n${e.stackTraceToString().take(300)}"
                    AlertDialog.Builder(this)
                        .setTitle("Ошибка")
                        .setMessage(errorDetails)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
}