package com.arotter.music

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Switch
import android.view.View
import android.widget.AdapterView
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class DuckingSettingsActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout
    private lateinit var arrowLoss: ImageView
    private lateinit var arrowLossTransient: ImageView
    private lateinit var arrowCanDuck: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ducking_settings)

        mainLayout = findViewById(R.id.duckingSettingsLayout)

        val secondary = ThemeManager.getSecondaryColor(this)
        val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
        ThemeManager.showSystemBars(window, this)
        mainLayout.background = ThemeManager.getBackgroundDrawable(this)

        val prefs = getSharedPreferences("player_prefs", MODE_PRIVATE)

        val enableSwitch: Switch = findViewById(R.id.switchEnableDucking)
        val toggleRow: LinearLayout = findViewById(R.id.duckingToggleRow)
        val optionsContainer: LinearLayout = findViewById(R.id.optionsContainer)

        fun setOptionsEnabled(enabled: Boolean) {
            optionsContainer.isEnabled = enabled
            fun recurse(view: View) {
                view.isEnabled = enabled
                if (view is LinearLayout) {
                    for (i in 0 until view.childCount) recurse(view.getChildAt(i))
                }
            }
            recurse(optionsContainer)
            optionsContainer.alpha = if (enabled) 1f else 0.5f
        }

        val enabledInitial = prefs.getBoolean("enable_ducking", true)
        enableSwitch.isChecked = enabledInitial
        setOptionsEnabled(enabledInitial)
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_ducking", isChecked).apply()
            setOptionsEnabled(isChecked)
        }
        toggleRow.setOnClickListener { enableSwitch.isChecked = !enableSwitch.isChecked }

        val options = listOf(
            OptionItem("ignore", "Не изменять"),
            OptionItem("duck", "Приглушать"),
            OptionItem("pause", "Отключать (пауза)")
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options.map { it.title })

        val spLoss: Spinner = findViewById(R.id.spinnerLoss)
        val spLossTransient: Spinner = findViewById(R.id.spinnerLossTransient)
        val spCanDuck: Spinner = findViewById(R.id.spinnerCanDuck)
        val rowLoss: LinearLayout = findViewById(R.id.rowLoss)
        val rowLossTransient: LinearLayout = findViewById(R.id.rowLossTransient)
        val rowCanDuck: LinearLayout = findViewById(R.id.rowCanDuck)
        val valueLoss: TextView = findViewById(R.id.valueLoss)
        val valueLossTransient: TextView = findViewById(R.id.valueLossTransient)
        val valueCanDuck: TextView = findViewById(R.id.valueCanDuck)
        arrowLoss = findViewById(R.id.arrowLoss)
        arrowLossTransient = findViewById(R.id.arrowLossTransient)
        arrowCanDuck = findViewById(R.id.arrowCanDuck)

        spLoss.adapter = adapter
        spLossTransient.adapter = adapter
        spCanDuck.adapter = adapter

        fun setSelection(sp: Spinner, value: String) {
            val idx = options.indexOfFirst { it.key == value }.coerceAtLeast(0)
            sp.setSelection(idx)
        }

        setSelection(spLoss, prefs.getString("duck_action_loss", "duck") ?: "duck")
        setSelection(spLossTransient, prefs.getString("duck_action_loss_transient", "duck") ?: "duck")
        setSelection(spCanDuck, prefs.getString("duck_action_can_duck", "ignore") ?: "ignore")

        fun titleFor(sp: Spinner): String = options[sp.selectedItemPosition].title
        valueLoss.text = titleFor(spLoss)
        valueLossTransient.text = titleFor(spLossTransient)
        valueCanDuck.text = titleFor(spCanDuck)

        fun animateOpen(row: LinearLayout, arrow: ImageView) {
            row.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).withEndAction {
                row.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
            arrow.animate().rotation(180f).setDuration(150).start()
        }

        rowLoss.setOnClickListener { animateOpen(rowLoss, arrowLoss); spLoss.performClick() }
        rowLossTransient.setOnClickListener { animateOpen(rowLossTransient, arrowLossTransient); spLossTransient.performClick() }
        rowCanDuck.setOnClickListener { animateOpen(rowCanDuck, arrowCanDuck); spCanDuck.performClick() }

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (parent) {
                    spLoss -> { valueLoss.text = options[position].title; arrowLoss.animate().rotation(0f).setDuration(150).start() }
                    spLossTransient -> { valueLossTransient.text = options[position].title; arrowLossTransient.animate().rotation(0f).setDuration(150).start() }
                    spCanDuck -> { valueCanDuck.text = options[position].title; arrowCanDuck.animate().rotation(0f).setDuration(150).start() }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spLoss.onItemSelectedListener = listener
        spLossTransient.onItemSelectedListener = listener
        spCanDuck.onItemSelectedListener = listener

        val sbLevel: SeekBar = findViewById(R.id.seekDuckingLevel)
        val tvLevel: TextView = findViewById(R.id.textDuckingLevel)
        val level = prefs.getFloat("duck_level", 0.3f)
        sbLevel.max = 90 // 0.1..1.0 шаг 0.01 почти
        val progress = ((level.coerceIn(0.1f, 1.0f) - 0.1f) * 100).toInt().coerceIn(0, 90)
        sbLevel.progress = progress
        tvLevel.text = String.format("Интенсивность приглушения: %.0f%%", (level * 100))

        sbLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 0.1f + (p / 100f)
                tvLevel.text = String.format("Интенсивность приглушения: %.0f%%", (v * 100))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val loss = options[spLoss.selectedItemPosition].key
            val lossTransient = options[spLossTransient.selectedItemPosition].key
            val canDuck = options[spCanDuck.selectedItemPosition].key
            val duckLevel = 0.1f + (sbLevel.progress / 100f)
            prefs.edit()
                .putString("duck_action_loss", loss)
                .putString("duck_action_loss_transient", lossTransient)
                .putString("duck_action_can_duck", canDuck)
                .putFloat("duck_level", duckLevel)
                .apply()
            finish()
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try { arrowLoss.animate().rotation(0f).setDuration(150).start() } catch (_: Exception) {}
            try { arrowLossTransient.animate().rotation(0f).setDuration(150).start() } catch (_: Exception) {}
            try { arrowCanDuck.animate().rotation(0f).setDuration(150).start() } catch (_: Exception) {}
        }
    }

    data class OptionItem(val key: String, val title: String)
}
