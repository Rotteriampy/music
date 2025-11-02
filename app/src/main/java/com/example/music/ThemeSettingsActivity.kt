package com.arotter.music

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest

class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout
    private lateinit var btnPickAccent: Button
    private lateinit var btnPickPrimaryGradStart: Button
    private lateinit var btnPickPrimaryGradEnd: Button
    private lateinit var switchThemeMode: SwitchCompat
    private lateinit var darkPresetsContainer: LinearLayout
    private lateinit var lightPresetsContainer: LinearLayout
    private lateinit var btnPickBgImage: Button
    private lateinit var btnClearBgImage: Button

    private val currentThemePresets = mutableSetOf<String>()
    private var selectedPresetName: String? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            ThemeManager.setBackgroundImage(this, it)
            onThemeChanged()
        }
    }

    override fun onResume() {
        super.onResume()
        val secondary = ThemeManager.getSecondaryColor(this)
        val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        ThemeManager.showSystemBars(window, this)

        val currentMode = AppCompatDelegate.getDefaultNightMode()
        switchThemeMode.isChecked = currentMode == AppCompatDelegate.MODE_NIGHT_YES

        switchThemeMode.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit().putInt("night_mode", mode).apply()
            refreshThemePreview()
        }

        refreshThemePreview()
    }

    private fun onThemeChanged() {
        refreshThemePreview()
        sendBroadcast(Intent("com.arotter.music.THEME_CHANGED"))
    }

    private fun refreshThemePreview() {
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)
        val accent = ThemeManager.getAccentColor(this)

        // Apply either gradient or selected image
        mainLayout.background = ThemeManager.getBackgroundDrawable(this)

        btnPickAccent.text = toHex(accent)
        btnPickPrimaryGradStart.text = toHex(gradStart)
        btnPickPrimaryGradEnd.text = toHex(gradEnd)

        // Enable/disable clear button depending on mode
        if (::btnClearBgImage.isInitialized) {
            btnClearBgImage.isEnabled = ThemeManager.isBackgroundImageEnabled(this)
        }

        if (::darkPresetsContainer.isInitialized && ::lightPresetsContainer.isInitialized) {
            updatePresetSelection(gradStart, gradEnd)
        }
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
        switchThemeMode = findViewById(R.id.switchThemeMode)
        darkPresetsContainer = findViewById(R.id.darkPresetsContainer)
        lightPresetsContainer = findViewById(R.id.lightPresetsContainer)
        btnPickBgImage = findViewById(R.id.btnPickBgImage)
        btnClearBgImage = findViewById(R.id.btnClearBgImage)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        buildGradientPresets()
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

        btnPickBgImage.setOnClickListener {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        btnClearBgImage.setOnClickListener {
            ThemeManager.setBackgroundImage(this, null)
            onThemeChanged()
        }
    }

    private fun buildGradientPresets() {
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        fun addPreset(container: LinearLayout, name: String, startHex: String, endHex: String, accentHex: String) {
            val presetCard = LinearLayout(this)
            presetCard.orientation = LinearLayout.VERTICAL
            presetCard.layoutParams = LinearLayout.LayoutParams(
                dp(50),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
                bottomMargin = dp(6)
            }
            presetCard.setPadding(dp(2), dp(2), dp(2), dp(2))

            // Gradient preview
            val gradientPreview = View(this)
            gradientPreview.layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            val gd = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor(startHex), Color.parseColor(endHex))
            )
            gd.cornerRadius = dp(10).toFloat()
            gd.setStroke(dp(1), Color.LTGRAY) // Серый контур по умолчанию
            gradientPreview.background = gd

            presetCard.addView(gradientPreview)

            // Store preset data
            currentThemePresets.add("$name|$startHex|$endHex")

            // Click listener
            presetCard.setOnClickListener {
                val start = Color.parseColor(startHex)
                val end = Color.parseColor(endHex)
                val accent = Color.parseColor(accentHex)
                ThemeManager.setPrimaryGradientStart(this, start)
                ThemeManager.setPrimaryGradientEnd(this, end)
                ThemeManager.setAccentColor(this, accent)
                selectedPresetName = name
                onThemeChanged()
            }

            presetCard.isClickable = true
            presetCard.isFocusable = true

            container.addView(presetCard)
        }

        val dark = listOf(
            arrayOf("Глубокий космос", "#0c1e2c", "#1a1a2e", "#00e5ff"),
            arrayOf("Пурпурная страсть", "#2d0c3d", "#1c0c28", "#e100ff"),
            arrayOf("Багровый рассвет", "#1e0b0b", "#2a0a2a", "#ff4d4d"),
            arrayOf("Изумрудный лес", "#0d1b13", "#1a1f1c", "#00e676"),
            arrayOf("Вулканическая лава", "#1a1a1a", "#260e04", "#ff9800")
            // Монохромный техно убран
        )

        val light = listOf(
            arrayOf("Ледяная свежесть", "#f0f5ff", "#ffffff", "#4fc3f7"),
            arrayOf("Морская гладь", "#a8e6cf", "#dcedc1", "#ff8b94"),
            arrayOf("Лавандовый рассвет", "#e6e6fa", "#f0f8ff", "#9370db"),
            arrayOf("Сахарная вата", "#ffd1dc", "#b5e8fc", "#ff6b8b"),
            arrayOf("Песочный закат", "#fff5e1", "#ffeaea", "#ff9a76")
        )

        darkPresetsContainer.removeAllViews()
        lightPresetsContainer.removeAllViews()

        darkPresetsContainer.orientation = LinearLayout.HORIZONTAL
        lightPresetsContainer.orientation = LinearLayout.HORIZONTAL

        dark.forEach {
            addPreset(darkPresetsContainer, it[0], it[1], it[2], it[3])
        }
        light.forEach {
            addPreset(lightPresetsContainer, it[0], it[1], it[2], it[3])
        }
    }

    private fun updatePresetSelection(currentStart: Int, currentEnd: Int) {
        val currentStartHex = toHex(currentStart)
        val currentEndHex = toHex(currentEnd)

        // Check if current gradient matches any preset
        var matchedPresetName: String? = null
        for (preset in currentThemePresets) {
            val parts = preset.split("|")
            if (parts.size == 3) {
                val (name, startHex, endHex) = parts
                if (startHex.equals(currentStartHex, ignoreCase = true) &&
                    endHex.equals(currentEndHex, ignoreCase = true)) {
                    matchedPresetName = name
                    break
                }
            }
        }

        selectedPresetName = matchedPresetName

        // Update all preset cards - сбрасываем все контуры к серому
        resetAllPresetContours()

        // Затем выделяем выбранную тему белым контуром
        if (matchedPresetName != null) {
            highlightSelectedPreset(matchedPresetName)
        }
    }

    private fun resetAllPresetContours() {
        // Сбрасываем все контуры к серому
        updateContainerContours(darkPresetsContainer, Color.LTGRAY)
        updateContainerContours(lightPresetsContainer, Color.LTGRAY)
    }

    private fun highlightSelectedPreset(selectedName: String) {
        // Устанавливаем белый контур для выбранной темы
        val darkPreset = findPresetByName(darkPresetsContainer, selectedName)
        val lightPreset = findPresetByName(lightPresetsContainer, selectedName)

        darkPreset?.let { setPresetContour(it, Color.WHITE) }
        lightPreset?.let { setPresetContour(it, Color.WHITE) }
    }

    private fun updateContainerContours(container: LinearLayout, color: Int) {
        for (i in 0 until container.childCount) {
            val presetCard = container.getChildAt(i) as LinearLayout
            setPresetContour(presetCard, color)
        }
    }

    private fun setPresetContour(presetCard: LinearLayout, color: Int) {
        val gradientPreview = presetCard.getChildAt(0) as View
        val gd = gradientPreview.background as GradientDrawable
        gd.setStroke(dp(1), color)
    }

    private fun findPresetByName(container: LinearLayout, name: String): LinearLayout? {
        for (i in 0 until container.childCount) {
            val presetCard = container.getChildAt(i) as LinearLayout
            // Проверяем, соответствует ли пресет имени
            val presetData = currentThemePresets.find {
                it.startsWith(name) && container.getChildAt(i) == presetCard
            }
            if (presetData != null) {
                return presetCard
            }
        }
        return null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}