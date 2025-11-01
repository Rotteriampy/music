package com.arotter.music

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
import android.widget.TextView
import android.widget.EditText
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView

class SettingsActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout

    companion object {
        private const val IMPORT_STATS_REQUEST = 200
    }

    override fun onResume() {
        super.onResume()
        val secondary = ThemeManager.getSecondaryColor(this)
        val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        ThemeManager.showSystemBars(window, this)
        applyThemeGradient()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ThemeManager.showSystemBars(window, this)
    }

    private fun onThemeChanged() {
        applyThemeGradient()
        sendBroadcast(Intent("com.arotter.music.THEME_CHANGED"))
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        mainLayout = findViewById(R.id.settingsLayout)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
            ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        applyThemeGradient()

        // Настройка кнопки управления темой
        findViewById<LinearLayout>(R.id.themeSettingsButton).setOnClickListener {
            val intent = Intent(this, ThemeSettingsActivity::class.java)
            startActivity(intent)
        }

        // Настройка кнопки управления статистикой
        findViewById<LinearLayout>(R.id.statsSettingsButton).setOnClickListener {
            val intent = Intent(this, StatsSettingsActivity::class.java)
            startActivity(intent)
        }
    }
}