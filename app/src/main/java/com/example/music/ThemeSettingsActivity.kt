package com.example.music

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.graphics.drawable.GradientDrawable

class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout
    private lateinit var btnPickAccent: Button
    private lateinit var btnPickPrimaryGradStart: Button
    private lateinit var btnPickPrimaryGradEnd: Button

    override fun onResume() {
        super.onResume()
        val secondary = ThemeManager.getSecondaryColor(this)
        val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        ThemeManager.showSystemBars(window, this)
        refreshThemePreview()
    }

    private fun onThemeChanged() {
        refreshThemePreview()
        sendBroadcast(Intent("com.example.music.THEME_CHANGED"))
    }

    private fun refreshThemePreview() {
        val primary = ThemeManager.getPrimaryColor(this)
        val secondary = ThemeManager.getSecondaryColor(this)
        val accent = ThemeManager.getAccentColor(this)
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)

        val gd = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(gradStart, gradEnd)
        )
        mainLayout.background = gd

        btnPickAccent.text = toHex(accent)
        btnPickPrimaryGradStart.text = toHex(gradStart)
        btnPickPrimaryGradEnd.text = toHex(gradEnd)
    }

    private fun toHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun showColorPickerDialog(title: String, currentColor: Int, onPicked: (Int) -> Unit) {
        val dialog = ColorPickerDialog(this, currentColor, title) { color ->
            onPicked(color)
        }
        dialog.show()
        dialog.setOnDismissListener {
            ThemeManager.showSystemBars(window, this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_settings)

        mainLayout = findViewById(R.id.themeSettingsLayout)
        btnPickAccent = findViewById(R.id.btnPickAccent)
        btnPickPrimaryGradStart = findViewById(R.id.btnPickPrimaryGradStart)
        btnPickPrimaryGradEnd = findViewById(R.id.btnPickPrimaryGradEnd)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        refreshThemePreview()

        btnPickPrimaryGradStart.setOnClickListener {
            showColorPickerDialog("Градиент: начало", ThemeManager.getPrimaryGradientStart(this)) { color ->
                ThemeManager.setPrimaryGradientStart(this, color)
                onThemeChanged()
            }
        }

        btnPickPrimaryGradEnd.setOnClickListener {
            showColorPickerDialog("Градиент: конец", ThemeManager.getPrimaryGradientEnd(this)) { color ->
                ThemeManager.setPrimaryGradientEnd(this, color)
                onThemeChanged()
            }
        }

        btnPickAccent.setOnClickListener {
            showColorPickerDialog("Выделяющий цвет", ThemeManager.getAccentColor(this)) { color ->
                ThemeManager.setAccentColor(this, color)
                onThemeChanged()
            }
        }
    }
}