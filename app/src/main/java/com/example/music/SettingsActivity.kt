package com.example.music

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.SharedPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout
    private lateinit var gradientPreview: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        mainLayout = findViewById(R.id.settingsLayout)
        gradientPreview = findViewById(R.id.gradientPreview)

        // Кнопки для выбора цветовых схем
        findViewById<Button>(R.id.btnScheme1).setOnClickListener { setColorScheme(1) }
        findViewById<Button>(R.id.btnScheme2).setOnClickListener { setColorScheme(2) }
        findViewById<Button>(R.id.btnScheme3).setOnClickListener { setColorScheme(3) }
        findViewById<Button>(R.id.btnScheme4).setOnClickListener { setColorScheme(4) }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setColorScheme(scheme: Int) {
        val color = when (scheme) {
            1 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.darker_gray)) // #212121
            2 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_blue_dark)) // #1A237E
            3 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_green_dark)) // #1B5E20
            else -> ColorDrawable(ContextCompat.getColor(this, android.R.color.black)) // #000000
        }

        mainLayout.background = color
        gradientPreview.background = color

        // Сохраняем выбранную схему
        val sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE)
        sharedPreferences.edit().putInt("color_scheme", scheme).apply()
    }
}