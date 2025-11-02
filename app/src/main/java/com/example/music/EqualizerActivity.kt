package com.arotter.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils

class EqualizerActivity : AppCompatActivity() {

    private var musicService: MusicService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            initValues()
            buildEqUI()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)

        val root = findViewById<LinearLayout>(R.id.rootEq)
        ThemeManager.applyBackground(root, this)
        val darkIcons = ColorUtils.calculateLuminance(ThemeManager.getSecondaryColor(this)) > 0.5
        ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnResetTuner).setOnClickListener {
            musicService?.resetPlaybackParams()
            initValues()
        }
        findViewById<ImageButton>(R.id.btnResetEq).setOnClickListener {
            try { musicService?.resetEqualizer() } catch (_: Exception) {}
            buildEqUI()
        }

        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        val speedSeek = findViewById<SeekBar>(R.id.seekSpeed)
        val pitchSeek = findViewById<SeekBar>(R.id.seekPitch)
        val speedLabel = findViewById<TextView>(R.id.lblSpeed)
        val pitchLabel = findViewById<TextView>(R.id.lblPitch)

        fun applyLabels(speed: Float, pitch: Float) {
            speedLabel.text = String.format("Скорость: %.2fx", speed)
            pitchLabel.text = String.format("Тон: %.2f", pitch)
        }

        speedSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress / 100f) * 1.5f // 0.5..2.0
                val pitch = 0.5f + (pitchSeek.progress / 100f) * 1.5f
                musicService?.setPlaybackSpeedPitch(speed, pitch)
                applyLabels(speed, pitch)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        pitchSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (speedSeek.progress / 100f) * 1.5f
                val pitch = 0.5f + (progress / 100f) * 1.5f // 0.5..2.0
                musicService?.setPlaybackSpeedPitch(speed, pitch)
                applyLabels(speed, pitch)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Disable controls on pre-M, as PlaybackParams not supported
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            speedSeek.isEnabled = false
            pitchSeek.isEnabled = false
            speedLabel.text = "Скорость: 1.00x (недоступно)"
            pitchLabel.text = "Тон: 1.00 (недоступно)"
        }
    }

    private fun initValues() {
        val speedSeek = findViewById<SeekBar>(R.id.seekSpeed)
        val pitchSeek = findViewById<SeekBar>(R.id.seekPitch)
        val speed = musicService?.getPlaybackSpeed() ?: 1.0f
        val pitch = musicService?.getPlaybackPitch() ?: 1.0f
        speedSeek.progress = (((speed - 0.5f) / 1.5f) * 100f).toInt().coerceIn(0,100)
        pitchSeek.progress = (((pitch - 0.5f) / 1.5f) * 100f).toInt().coerceIn(0,100)
        findViewById<TextView>(R.id.lblSpeed).text = String.format("Скорость: %.2fx", speed)
        findViewById<TextView>(R.id.lblPitch).text = String.format("Тон: %.2f", pitch)
    }

    private fun buildEqUI() {
        val container = findViewById<LinearLayout>(R.id.eqContainer)
        container?.removeAllViews()

        val svc = musicService ?: return
        val available = try { svc.isEqualizerAvailable() } catch (_: Exception) { false }
        if (!available) {
            val tv = TextView(this)
            tv.text = "Эквалайзер недоступен"
            tv.textSize = 14f
            container?.addView(tv)
            return
        }

        val bands = svc.getEqNumberOfBands().toInt()
        val range = svc.getEqBandLevelRange()
        val minMb = range.getOrNull(0) ?: 0
        val maxMb = range.getOrNull(1) ?: 0

        fun mbToDb(mb: Int): Float = mb / 100f

        for (i in 0 until bands) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }

            val center = svc.getEqCenterFreq(i.toShort()) // milliHertz
            val label = TextView(this)
            val hz = center / 1000
            val labelText = if (hz >= 1000) String.format("%.1f кГц", hz / 1000f) else "$hz Гц"
            val currentMb = svc.getEqBandLevel(i.toShort()).toInt()
            label.text = "$labelText  (${String.format("%+.1f", mbToDb(currentMb))} дБ)"
            label.textSize = 14f

            val seek = SeekBar(this)
            seek.max = 100
            val progress = if (maxMb - minMb != 0) ((currentMb - minMb) * 100 / (maxMb - minMb)) else 50
            seek.progress = progress.coerceIn(0, 100)

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val mb = minMb + (progress * (maxMb - minMb) / 100)
                    svc.setEqBandLevel(i.toShort(), mb.toShort())
                    label.text = "$labelText  (${String.format("%+.1f", mbToDb(mb))} дБ)"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            row.addView(label)
            row.addView(seek)
            container.addView(row)
        }
    }

    override fun onDestroy() {
        if (isBound) unbindService(serviceConnection)
        super.onDestroy()
    }
}
