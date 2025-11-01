package com.arotter.music

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.text.TextWatcher
import android.text.Editable
import android.view.View
import androidx.core.content.ContextCompat

class ColorPickerDialog(
    context: Context,
    private val initialColor: Int,
    private val title: String,
    private val onColorPicked: (Int) -> Unit
) : Dialog(context) {

    private lateinit var colorWheel: ColorWheelView
    private lateinit var colorPreview: View
    private lateinit var etHexValue: EditText
    private lateinit var tvTitle: TextView
    private lateinit var btnOk: Button
    private lateinit var btnCancel: Button

    private var currentColor: Int = initialColor
    private var isUpdatingFromWheel = false
    private var isUpdatingFromHex = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_color_picker)
        window?.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.dialog_background))
        // Set dialog width to 90% of screen
        val dm = context.resources.displayMetrics
        val targetWidth = (dm.widthPixels * 0.9f).toInt()
        window?.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        colorWheel = findViewById(R.id.colorWheel)
        colorPreview = findViewById(R.id.colorPreview)
        etHexValue = findViewById(R.id.etHexValue)
        tvTitle = findViewById(R.id.tvColorPickerTitle)
        btnOk = findViewById(R.id.btnOk)
        btnCancel = findViewById(R.id.btnCancel)

        tvTitle.text = title

        colorWheel.setColor(initialColor)

        updatePreview(initialColor)

        colorWheel.onColorSelected = { color ->
            if (!isUpdatingFromHex) {
                isUpdatingFromWheel = true
                currentColor = color
                updatePreview(color, updateHexField = true)
                isUpdatingFromWheel = false
            }
        }

        // HEX input handling
        etHexValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromWheel) return

                val raw = s?.toString() ?: return
                val text = raw.trim()
                if (text.isEmpty()) return

                // Only react when a complete hex is entered to avoid overwriting user input mid-typing
                val hex = if (text.startsWith("#")) text else "#$text"
                val isComplete = (hex.length == 7 || hex.length == 9)
                if (!isComplete) return

                try {
                    isUpdatingFromHex = true
                    val color = Color.parseColor(hex)
                    currentColor = color
                    colorWheel.setColor(color)
                    // Do not update the EditText text while user is typing via hex
                    updatePreview(color, updateHexField = false)
                } catch (_: Exception) {
                    // ignore invalid hex
                } finally {
                    isUpdatingFromHex = false
                }
            }
        })

        btnOk.setOnClickListener {
            onColorPicked(currentColor)
            dismiss()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        // Remove default button background tint
        btnOk.backgroundTintList = null
        btnCancel.backgroundTintList = null
    }

    private fun updatePreview(color: Int, updateHexField: Boolean = true) {
        colorPreview.setBackgroundColor(color)

        // Format hex with alpha channel support
        val hex = if (Color.alpha(color) == 255) {
            String.format("#%06X", 0xFFFFFF and color)
        } else {
            String.format("#%08X", color)
        }

        if (updateHexField) {
            if (etHexValue.text?.toString() != hex) {
                etHexValue.setText(hex)
                etHexValue.setSelection(etHexValue.text?.length ?: 0)
            }
        }
    }
}