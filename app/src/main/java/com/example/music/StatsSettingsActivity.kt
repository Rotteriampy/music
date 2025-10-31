package com.example.music

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import android.widget.Toast
import android.content.Intent
import android.app.AlertDialog
import androidx.core.content.FileProvider
import android.graphics.drawable.GradientDrawable

class StatsSettingsActivity : AppCompatActivity() {

    companion object {
        private const val IMPORT_STATS_REQUEST = 200
    }

    private lateinit var mainLayout: LinearLayout

    override fun onResume() {
        super.onResume()
        val secondary = ThemeManager.getSecondaryColor(this)
        val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        ThemeManager.showSystemBars(window, this)
        applyThemeGradient()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats_settings)

        mainLayout = findViewById(R.id.statsSettingsLayout)

        findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() }

        val exportStatsButton = findViewById<LinearLayout>(R.id.exportStatsButton)
        val importStatsButton = findViewById<LinearLayout>(R.id.importStatsButton)
        val clearStatsButton = findViewById<LinearLayout>(R.id.clearStatsButton)

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
                .show().also { ThemeManager.showSystemBars(window, this) }
        }

        applyThemeGradient()
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
                .show().also { ThemeManager.showSystemBars(window, this) }
        } else {
            Toast.makeText(this, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyThemeGradient() {
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)
        val gd = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(gradStart, gradEnd)
        )
        mainLayout.background = gd
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
                            AlertDialog.Builder(this)
                                .setTitle("Ошибка импорта")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show().also { ThemeManager.showSystemBars(window, this) }
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
                        .show().also { ThemeManager.enableImmersive(window) }
                }
            }
        }
    }
}