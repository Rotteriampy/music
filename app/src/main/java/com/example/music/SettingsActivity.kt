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
import android.widget.Switch
import android.widget.ImageButton
import android.view.Gravity

class SettingsActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout

    companion object {
        private const val IMPORT_STATS_REQUEST = 200
    }

    private fun setLayoutFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            run {
                val decor = window.decorView
                var flags = decor.systemUiVisibility
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                decor.systemUiVisibility = flags
            }
        }
    }

    private fun applyContentTopPadding() {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resId > 0) resources.getDimensionPixelSize(resId) else 0
        val left = mainLayout.paddingLeft
        val right = mainLayout.paddingRight
        val bottom = mainLayout.paddingBottom
        if (mainLayout.paddingTop != statusBarHeight) {
            mainLayout.setPadding(left, statusBarHeight, right, bottom)
        }
    }

    private fun loadTabsOrder(): MutableList<String> {
        val prefs = getSharedPreferences("tabs_prefs", MODE_PRIVATE)
        val def = "tracks,playlists,albums,artists,genres"
        val s = prefs.getString("order", def) ?: def
        return s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveTabsOrder(order: List<String>) {
        val prefs = getSharedPreferences("tabs_prefs", MODE_PRIVATE)
        prefs.edit().putString("order", order.joinToString(",")).apply()
        Toast.makeText(this, "Порядок вкладок сохранен", Toast.LENGTH_SHORT).show()
    }

    private fun showTabsOrderDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            isClickable = false
            isFocusable = false
            foreground = null
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            background = null
            foreground = null
            isClickable = false
            isFocusable = false
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = false
            isFocusable = false
            foreground = null
        }

        val order = loadTabsOrder()
        fun buildRow(title: String): LinearLayout {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(12, 16, 12, 16)
            row.isClickable = false
            row.isFocusable = false
            row.isSoundEffectsEnabled = false
            // per-row rounded, light semi-transparent background
            val rowBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = (12 * resources.displayMetrics.density)
                setColor(android.graphics.Color.parseColor("#10FFFFFF"))
            }
            row.background = rowBg
            row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }

            val tv = TextView(this)
            tv.text = title
            tv.textSize = 16f
            tv.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary))
            val tvLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tvLp.marginStart = (24 * resources.displayMetrics.density).toInt()
            tv.layoutParams = tvLp

            val up = ImageView(this).apply {
                setImageResource(R.drawable.ic_arrow_up)
                setColorFilter(ContextCompat.getColor(this@SettingsActivity, R.color.colorIconTint))
                isClickable = true
                isFocusable = false
                isFocusableInTouchMode = false
                foreground = null
                setPadding(16, 16, 16, 16)
                isSoundEffectsEnabled = false
            }
            val down = ImageView(this).apply {
                setImageResource(R.drawable.ic_arrow_down)
                setColorFilter(ContextCompat.getColor(this@SettingsActivity, R.color.colorIconTint))
                isClickable = true
                isFocusable = false
                isFocusableInTouchMode = false
                foreground = null
                setPadding(16, 16, 16, 16)
                isSoundEffectsEnabled = false
            }

            row.addView(tv)
            row.addView(up)
            row.addView(down)

            fun moveUp() {
                val from = container.indexOfChild(row)
                if (from > 0) {
                    val target = container.getChildAt(from - 1)
                    val d1 = target.height.takeIf { it > 0 } ?: row.height
                    val d2 = row.height.takeIf { it > 0 } ?: target.height
                    target.animate().translationY(d2.toFloat()).setDuration(160L).start()
                    row.animate().translationY((-d1).toFloat()).setDuration(160L).withEndAction {
                        container.removeViewAt(from)
                        container.addView(row, from - 1)
                        row.translationY = 0f
                        target.translationY = 0f
                    }.start()
                }
            }
            fun moveDown() {
                val from = container.indexOfChild(row)
                if (from >= 0 && from < container.childCount - 1) {
                    val target = container.getChildAt(from + 1)
                    val d1 = target.height.takeIf { it > 0 } ?: row.height
                    val d2 = row.height.takeIf { it > 0 } ?: target.height
                    target.animate().translationY((-d2).toFloat()).setDuration(160L).start()
                    row.animate().translationY(d1.toFloat()).setDuration(160L).withEndAction {
                        container.removeViewAt(from)
                        container.addView(row, from + 1)
                        row.translationY = 0f
                        target.translationY = 0f
                    }.start()
                }
            }
            up.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        (v.parent as? View)?.isPressed = false
                        (v.parent?.parent as? View)?.isPressed = false
                        card.isPressed = false
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80L).start(); true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        (v.parent as? View)?.isPressed = false
                        (v.parent?.parent as? View)?.isPressed = false
                        card.isPressed = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80L).withEndAction { moveUp() }.start(); true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        (v.parent as? View)?.isPressed = false
                        (v.parent?.parent as? View)?.isPressed = false
                        card.isPressed = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80L).start(); true
                    }
                    else -> false
                }
            }
            down.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        (v.parent as? View)?.isPressed = false
                        (v.parent?.parent as? View)?.isPressed = false
                        card.isPressed = false
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80L).start(); true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        (v.parent as? View)?.isPressed = false
                        (v.parent?.parent as? View)?.isPressed = false
                        card.isPressed = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80L).withEndAction { moveDown() }.start(); true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        (v.parent as? View)?.isPressed = false
                        (v.parent?.parent as? View)?.isPressed = false
                        card.isPressed = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80L).start(); true
                    }
                    else -> false
                }
            }
            return row
        }
        val titles = mapOf(
            "tracks" to "Треки",
            "playlists" to "Плейлисты",
            "albums" to "Альбомы",
            "artists" to "Исполнители",
            "genres" to "Жанры"
        )
        order.forEach { key -> container.addView(buildRow(titles[key] ?: key)) }

        card.addView(container)

        // Кнопки управления (на одной линии), выравнивание по старту названий вкладок
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // paddingStart совпадает с отступом у названий вкладок (2dp), конец без отступа
            setPadding((2 * resources.displayMetrics.density).toInt(), (12 * resources.displayMetrics.density).toInt(), 0, 0)
            weightSum = 2f
        }
        val btnCancel = Button(this).apply {
            text = "Отмена"
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * resources.displayMetrics.density).toInt()
            }
            textSize = 13f
            setPadding((8 * resources.displayMetrics.density).toInt(), (6 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt(), (6 * resources.displayMetrics.density).toInt())
            minHeight = 0
            includeFontPadding = false
            val radius = 12 * resources.displayMetrics.density
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(android.graphics.Color.parseColor("#668D8D8D"))
            }
        }
        val btnSave = Button(this).apply {
            text = "Сохранить"
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * resources.displayMetrics.density).toInt()
            }
            textSize = 14f
            setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt())
            val radius = 12 * resources.displayMetrics.density
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(android.graphics.Color.parseColor("#664CAF50"))
            }
        }
        buttons.addView(btnCancel)
        buttons.addView(btnSave)

        card.addView(buttons)
        root.addView(card)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Порядок вкладок")
            .setView(root)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.dialog_background))
        dialog.window?.setDimAmount(0.7f)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val newOrder = mutableListOf<String>()
            for (i in 0 until container.childCount) {
                val row = container.getChildAt(i) as LinearLayout
                val tv = row.getChildAt(0) as TextView
                val key = titles.entries.firstOrNull { it.value == tv.text }?.key ?: tv.text.toString()
                newOrder.add(key)
            }
            saveTabsOrder(newOrder)
            dialog.dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
        val secondary = ThemeManager.getSecondaryColor(this)
        val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        ThemeManager.showSystemBars(window, this)
        setLayoutFullscreen()
        applyContentTopPadding()
        applyThemeGradient()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ThemeManager.showSystemBars(window, this)
            setLayoutFullscreen()
            applyContentTopPadding()
        }
    }

    private fun onThemeChanged() {
        applyThemeGradient()
        sendBroadcast(Intent("com.arotter.music.THEME_CHANGED"))
    }

    private fun applyThemeGradient() {
        mainLayout.background = ThemeManager.getBackgroundDrawable(this)
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
        setLayoutFullscreen()
        applyContentTopPadding()

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        applyThemeGradient()

        // Настройка кнопки управления темой
        findViewById<LinearLayout>(R.id.themeSettingsButton).setOnClickListener {
            val intent = Intent(this, ThemeSettingsActivity::class.java)
            startActivity(intent)
        }

        // Настройки приглушения
        findViewById<LinearLayout>(R.id.duckingSettingsButton).setOnClickListener {
            val intent = Intent(this, DuckingSettingsActivity::class.java)
            startActivity(intent)
        }

        // Настройка кнопки управления статистикой
        findViewById<LinearLayout>(R.id.statsSettingsButton).setOnClickListener {
            val intent = Intent(this, StatsSettingsActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.tabsOrderButton).setOnClickListener {
            showTabsOrderDialog()
        }

        // Переключатель: Продолжать воспроизведение с начала
        val prefs = getSharedPreferences("player_prefs", MODE_PRIVATE)
        val swContinue = findViewById<Switch>(R.id.switchContinueFromStart)
        val row = findViewById<LinearLayout>(R.id.continuePlaybackRow)
        val initial = prefs.getBoolean("continue_from_start", false)
        swContinue.isChecked = initial
        swContinue.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("continue_from_start", isChecked).apply()
        }
        row.setOnClickListener {
            swContinue.isChecked = !swContinue.isChecked
        }

    }
}