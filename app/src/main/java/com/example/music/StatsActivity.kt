package com.arotter.music

import android.os.Bundle
import android.os.Build
import android.view.View
import android.content.Intent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.*
import kotlin.math.max
import androidx.appcompat.widget.SwitchCompat
import android.media.MediaMetadataRetriever

class StatsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var back: ImageButton
    private lateinit var chart: LineChart
    private lateinit var extraTitle: TextView

    // Selectors
    private lateinit var spMode: Spinner
    private lateinit var rgPeriod: RadioGroup
    private lateinit var swValueMode: SwitchCompat

    // New elements for enhanced UI
    private lateinit var extraIcon: ImageView
    private lateinit var rankingIcon: ImageView
    private lateinit var ratingContainer: LinearLayout
    private lateinit var totalContainer: LinearLayout
    private lateinit var totalIcon: ImageView
    private lateinit var totalLabel: TextView
    private lateinit var totalValue: TextView
    private lateinit var totalTimeLabel: TextView
    private lateinit var totalTimeValue: TextView
    private lateinit var totalAvgLabel: TextView
    private lateinit var totalAvgValue: TextView
    private lateinit var totalAppTimeLabel: TextView
    private lateinit var totalAppTimeValue: TextView
    private lateinit var diversityLabel: TextView
    private lateinit var diversityValue: TextView

    private enum class Mode { ALL, PLAYLISTS, ARTISTS, ALBUMS, GENRES }
    private enum class Period { DAY, WEEK, MONTH }
    private enum class ValueMode { COUNT, TIME }
    private enum class ChartStyle { LINEAR, CUBIC, FILLED, CUBIC_FILLED, STEPPED }

    private var mode = Mode.ALL
    private var period = Period.DAY
    private var valueMode = ValueMode.COUNT
    private var chartStyle = ChartStyle.LINEAR

    private val durationCacheMs = hashMapOf<String, Int>()
    private var lastBuckets: List<Long> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        root = findViewById(R.id.statsRoot)
        back = findViewById(R.id.btnBackStats)
        chart = findViewById(R.id.lineChart)
        extraTitle = findViewById(R.id.extraTitle)

        // New UI elements
        extraIcon = findViewById(R.id.extraIcon)
        rankingIcon = findViewById(R.id.rankingIcon)
        ratingContainer = findViewById(R.id.ratingContainer)
        totalContainer = findViewById(R.id.totalContainer)
        totalIcon = findViewById(R.id.totalIcon)
        totalLabel = findViewById(R.id.totalLabel)
        totalValue = findViewById(R.id.totalValue)
        totalTimeLabel = findViewById(R.id.totalTimeLabel)
        totalTimeValue = findViewById(R.id.totalTimeValue)
        totalAvgLabel = findViewById(R.id.totalAvgLabel)
        totalAvgValue = findViewById(R.id.totalAvgValue)
        totalAppTimeLabel = findViewById(R.id.totalAppTimeLabel)
        totalAppTimeValue = findViewById(R.id.totalAppTimeValue)
        diversityLabel = findViewById(R.id.diversityLabel)
        diversityValue = findViewById(R.id.diversityValue)

        // Hidden developer shortcut: long-press title to seed test data
        findViewById<TextView>(R.id.statsTitle).setOnLongClickListener {
            val added = seedTestData()
            android.widget.Toast.makeText(this, "Добавлено тестовых событий: $added", android.widget.Toast.LENGTH_SHORT).show()
            refresh()
            true
        }


        fun getStatusBarHeight(): Int {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
        }

        spMode = findViewById(R.id.spMode)
        rgPeriod = findViewById(R.id.rgPeriod)
        swValueMode = findViewById(R.id.swValueMode)

        // Restore value mode from prefs
        val prefs = getSharedPreferences("stats_prefs", MODE_PRIVATE)
        valueMode = if (prefs.getBoolean("value_mode_time", false)) ValueMode.TIME else ValueMode.COUNT
        swValueMode.isChecked = (valueMode == ValueMode.TIME)

        restoreColor()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val secondary = ThemeManager.getSecondaryColor(this)
            val darkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
            ThemeManager.applyTransparentStatusBarWithBackground(window, darkIcons, this)
            ThemeManager.showSystemBars(window, this)
        }
        setLayoutFullscreen()
        applyContentTopPadding()

        back.setOnClickListener {
            try {
                back.animate().cancel()
                back.rotation = 0f
                back.animate()
                    .rotation(180f)
                    .setDuration(180L)
                    .withEndAction { finish() }
                    .start()
            } catch (_: Exception) { finish() }
        }

        initChart()
        initSelectors()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        restoreColor()
        ThemeManager.applyTransparentStatusBarWithBackground(window, androidx.core.graphics.ColorUtils.calculateLuminance(ThemeManager.getSecondaryColor(this)) > 0.5, this)
        ThemeManager.showSystemBars(window, this)
        setLayoutFullscreen()
        applyContentTopPadding()
        refresh()
    }

    private fun avgListenedPercent(): Float {
        val events = PlayHistory.readAll(this)
        val percents = events.mapNotNull { it.percent }
        if (percents.isEmpty()) return 0f
        val avg = percents.sum() / percents.size
        return avg.coerceIn(0f, 100f)
    }

    private fun restoreColor() {
        val primary = ThemeManager.getPrimaryColor(this)
        val secondary = ThemeManager.getSecondaryColor(this)
        val accent = ThemeManager.getAccentColor(this)
        val gradStart = ThemeManager.getPrimaryGradientStart(this)
        val gradEnd = ThemeManager.getPrimaryGradientEnd(this)

        // Фон экрана статистики: изображение из темы или градиент
        root.background = ThemeManager.getBackgroundDrawable(this)

        // Заголовок и кнопка назад
        findViewById<TextView>(R.id.statsTitle).setTextColor(secondary)
        back.setColorFilter(secondary)

        // Радио-кнопки периода и их текст
        listOf<RadioButton>(
            findViewById(R.id.rbDays),
            findViewById(R.id.rbWeeks),
            findViewById(R.id.rbMonths)
        ).forEach { rb ->
            rb.setTextColor(secondary)
            rb.buttonTintList = android.content.res.ColorStateList.valueOf(secondary)
        }

        // Блок дополнительных данных
        extraTitle.setTextColor(secondary)

        // Иконки
        extraIcon.setColorFilter(secondary)
        rankingIcon.setColorFilter(secondary)
        totalIcon.setColorFilter(secondary)

        // Текст в общем блоке
        totalLabel.setTextColor(secondary)
        totalValue.setTextColor(secondary)
        totalTimeLabel.setTextColor(secondary)
        totalTimeValue.setTextColor(secondary)
        totalAvgLabel.setTextColor(secondary)
        totalAvgValue.setTextColor(secondary)
        findViewById<SwitchCompat>(R.id.swValueMode).setTextColor(secondary)

        // Обновляем цвет текста в элементах рейтинга
        for (i in 0 until ratingContainer.childCount) {
            val child = ratingContainer.getChildAt(i) as? TextView
            child?.setTextColor(secondary)
        }
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
        val left = root.paddingLeft
        val right = root.paddingRight
        val bottom = root.paddingBottom
        if (root.paddingTop != statusBarHeight) {
            root.setPadding(left, statusBarHeight, right, bottom)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ThemeManager.showSystemBars(window, this)
            setLayoutFullscreen()
            applyContentTopPadding()
        }
    }

    private fun lighten(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        val nr = (r + ((255 - r) * factor)).toInt().coerceIn(0, 255)
        val ng = (g + ((255 - g) * factor)).toInt().coerceIn(0, 255)
        val nb = (b + ((255 - b) * factor)).toInt().coerceIn(0, 255)
        return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    private fun initChart() {
        chart.description.isEnabled = false
        chart.legend.apply {
            form = Legend.LegendForm.LINE
            this.textColor = ThemeManager.getSecondaryColor(this@StatsActivity)
        }
        chart.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        chart.setDrawGridBackground(false)
        chart.axisLeft.textColor = ThemeManager.getSecondaryColor(this)
        chart.axisRight.isEnabled = false
        chart.xAxis.isGranularityEnabled = true
        chart.xAxis.granularity = 1f
        chart.xAxis.setAvoidFirstLastClipping(false) // или true — смотрите на результат
        chart.xAxis.textColor = ThemeManager.getSecondaryColor(this)
        // Целочисленные значения по осям
        chart.axisLeft.granularity = 1f
        chart.xAxis.granularity = 1f
        chart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                return value.toInt().toString()
            }
        }
        chart.setNoDataText("Нет данных для выбранного периода")
        chart.setNoDataTextColor(ThemeManager.getSecondaryColor(this))
        // Разделители месяцев поверх данных
        chart.xAxis.setDrawLimitLinesBehindData(false)
    }

    private fun initSelectors() {
        val modes = listOf("Общее" to Mode.ALL, "Плейлисты" to Mode.PLAYLISTS, "Исполнители" to Mode.ARTISTS, "Альбомы" to Mode.ALBUMS, "Жанры" to Mode.GENRES)

        val modeAdapter = ArrayAdapter<String>(
            this,
            R.layout.spinner_item_white,  // выбранный элемент
            modes.map { it.first }
        ).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item_white)
        }
        spMode.adapter = modeAdapter

        spMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mode = modes[position].second
                refresh()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // RadioGroup for period selection
        rgPeriod.setOnCheckedChangeListener { _, checkedId ->
            period = when (checkedId) {
                R.id.rbDays -> Period.DAY
                R.id.rbWeeks -> Period.WEEK
                R.id.rbMonths -> Period.MONTH
                else -> Period.DAY
            }
            refresh()
        }
        // Default selection
        findViewById<RadioButton>(R.id.rbDays).isChecked = true

        // Toggle for value mode (count/time)
        swValueMode.setOnCheckedChangeListener { _, isChecked ->
            valueMode = if (isChecked) ValueMode.TIME else ValueMode.COUNT
            val prefsLocal = getSharedPreferences("stats_prefs", MODE_PRIVATE)
            prefsLocal.edit().putBoolean("value_mode_time", isChecked).apply()
            refresh()
        }
    }

    private fun loadChartStyleFromPrefs(): ChartStyle {
        val code = getSharedPreferences("stats_prefs", MODE_PRIVATE)
            .getString("chart_style", "LINEAR")
        return when (code) {
            "CUBIC" -> ChartStyle.CUBIC
            "FILLED" -> ChartStyle.FILLED
            "CUBIC_FILLED" -> ChartStyle.CUBIC_FILLED
            "STEPPED" -> ChartStyle.STEPPED
            else -> ChartStyle.LINEAR
        }
    }

    private fun applyChartStyle(set: LineDataSet) {
        when (chartStyle) {
            ChartStyle.LINEAR -> {
                set.mode = LineDataSet.Mode.LINEAR
                set.setDrawFilled(false)
                set.setDrawCircles(true)
            }
            ChartStyle.CUBIC -> {
                set.mode = LineDataSet.Mode.CUBIC_BEZIER
                set.cubicIntensity = 0.2f
                set.setDrawFilled(false)
                set.setDrawCircles(false)
            }
            ChartStyle.FILLED -> {
                set.mode = LineDataSet.Mode.LINEAR
                set.setDrawFilled(true)
                set.fillAlpha = 80
                set.setDrawCircles(false)
            }
            ChartStyle.CUBIC_FILLED -> {
                set.mode = LineDataSet.Mode.CUBIC_BEZIER
                set.cubicIntensity = 0.2f
                set.setDrawFilled(true)
                set.fillAlpha = 80
                set.setDrawCircles(false)
            }
            ChartStyle.STEPPED -> {
                set.mode = LineDataSet.Mode.STEPPED
                set.setDrawFilled(false)
                set.setDrawCircles(false)
            }
        }
    }

    private fun refresh() {
        chartStyle = loadChartStyleFromPrefs()
        val events = PlayHistory.readAll(this)
        val filtered = events

        // Очищаем контейнер рейтинга и скрываем его
        ratingContainer.removeAllViews()
        ratingContainer.visibility = View.GONE
        totalContainer.visibility = View.VISIBLE

        if (filtered.isEmpty()) {
            chart.clear()
            if (mode == Mode.ALL) {
                extraTitle.text = "Статистика"
                totalLabel.text = "Всего прослушиваний:"
                totalValue.text = "${totalAllTime()}"
                totalTimeLabel.text = "Всего прослушивания (часы):"
                totalTimeValue.text = formatHours(totalAllTimeDurationMs())
                totalAvgLabel.text = "Средний % прослушивания:"
                totalAvgValue.text = formatPercent(avgListenedPercent())
                totalAppTimeLabel.text = "Время в приложении (часы):"
                totalAppTimeValue.text = formatHours(totalAppUsageMs())
                diversityLabel.text = "Разнообразие артистов:"
                diversityValue.text = formatDiversity(artistDiversityIndex())
                // Показываем иконку статистики, скрываем иконку рейтинга
                extraIcon.visibility = View.VISIBLE
                rankingIcon.visibility = View.GONE
            } else {
                extraTitle.text = "Рейтинг"

                // Скрываем общий блок и показываем контейнер рейтинга
                totalContainer.visibility = View.GONE
                ratingContainer.visibility = View.VISIBLE

                // Добавляем сообщение "Нет данных" в рейтинг
                val emptyView = TextView(this).apply {
                    text = "Нет данных"
                    setTextColor(ContextCompat.getColor(this@StatsActivity, android.R.color.white))
                    textSize = 18f
                    background = ContextCompat.getDrawable(this@StatsActivity, R.drawable.bg_stats_item)
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                        resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                        resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                        resources.getDimensionPixelSize(R.dimen.rating_item_padding)
                    )
                }
                ratingContainer.addView(emptyView)

                // Показываем иконку рейтинга, скрываем иконку статистики
                extraIcon.visibility = View.GONE
                rankingIcon.visibility = View.VISIBLE
            }
            return
        }

        val bucketed = aggregate(filtered)
        val lineData = LineData()

        val colors = listOf(
            0xFF66BB6A.toInt(), 0xFF42A5F5.toInt(), 0xFFFFA726.toInt(), 0xFFAB47BC.toInt(), 0xFFEF5350.toInt(),
            0xFF26A69A.toInt(), 0xFF5C6BC0.toInt(), 0xFF78909C.toInt()
        )
        var colorIdx = 0

        // Подписи X — индексы бакетов (целые числа)
        bucketed.forEach { (seriesName, points) ->
            val entries = points.mapIndexed { idx, v -> Entry(idx.toFloat(), v) }
            val set = LineDataSet(entries, seriesName).apply {
                color = colors[colorIdx % colors.size]
                setCircleColor(color)
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
            }
            applyChartStyle(set)

            colorIdx++
            lineData.addDataSet(set)
        }
        // Обновим подписи оси X согласно выбранному периоду и текущим бакетам
        applyXAxisLabels()
        // Добавим разделители месяцев
        updateMonthSeparators()

        chart.data = lineData
        chart.invalidate()

        // Доп. данные (всегда за всё время)
        if (mode == Mode.ALL) {
            extraTitle.text = "Статистика"
            totalLabel.text = "Всего прослушиваний:"
            totalValue.text = "${totalAllTime()}"
            totalTimeLabel.text = "Всего прослушивания (часы):"
            totalTimeValue.text = formatHours(totalAllTimeDurationMs())
            totalAvgLabel.text = "Средний % прослушивания:"
            totalAvgValue.text = formatPercent(avgListenedPercent())
            totalAppTimeLabel.text = "Время в приложении (часы):"
            totalAppTimeValue.text = formatHours(totalAppUsageMs())
            diversityLabel.text = "Разнообразие артистов:"
            diversityValue.text = formatDiversity(artistDiversityIndex())

            // Показываем общий блок, скрываем рейтинг
            totalContainer.visibility = View.VISIBLE
            ratingContainer.visibility = View.GONE

            // Показываем иконку статистики, скрываем иконку рейтинга
            extraIcon.visibility = View.VISIBLE
            rankingIcon.visibility = View.GONE
        } else {
            val totals = allTimeTotalsForMode(mode).take(5)
            extraTitle.text = "Рейтинг"

            // Скрываем общий блок и показываем контейнер рейтинга
            totalContainer.visibility = View.GONE
            ratingContainer.visibility = View.VISIBLE

            // Добавляем элементы рейтинга с фонами
            if (totals.isEmpty()) {
                val emptyView = TextView(this).apply {
                    text = "—"
                    setTextColor(ContextCompat.getColor(this@StatsActivity, android.R.color.white))
                    textSize = 18f
                    background = ContextCompat.getDrawable(this@StatsActivity, R.drawable.bg_stats_item)
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                        resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                        resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                        resources.getDimensionPixelSize(R.dimen.rating_item_padding)
                    )
                }
                ratingContainer.addView(emptyView)
            } else {
                totals.forEachIndexed { index, (name, value) ->
                    val ratingItem = TextView(this).apply {
                        text = if (valueMode == ValueMode.TIME) "$name: ${formatMinutes(value.toInt())}" else "$name: ${value.toInt()}"
                        setTextColor(ContextCompat.getColor(this@StatsActivity, android.R.color.white))
                        textSize = 18f
                        background = ContextCompat.getDrawable(this@StatsActivity, R.drawable.bg_stats_item)
                        setPadding(
                            resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                            resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                            resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                            resources.getDimensionPixelSize(R.dimen.rating_item_padding)
                        )

                        // Добавляем отступ между элементами, кроме последнего
                        if (index < totals.size - 1) {
                            val layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.rating_item_margin)
                            this.layoutParams = layoutParams
                        }
                    }
                    ratingContainer.addView(ratingItem)
                }

                // Добавим блок с новыми группами за последние 5 дней (>=5 прослушиваний)
                val newGroups = newGroupsLast5Days(mode)
                if (newGroups.isNotEmpty()) {
                    val header = TextView(this).apply {
                        text = when (mode) {
                            Mode.PLAYLISTS -> "Новые плейлисты (последние 5 дней)"
                            Mode.ARTISTS -> "Новые исполнители (последние 5 дней)"
                            Mode.ALBUMS -> "Новые альбомы (последние 5 дней)"
                            Mode.GENRES -> "Новые жанры (последние 5 дней)"
                            else -> "Новые группы"
                        }
                        setTextColor(ContextCompat.getColor(this@StatsActivity, android.R.color.white))
                        textSize = 16f
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.topMargin = resources.getDimensionPixelSize(R.dimen.rating_item_margin)
                        layoutParams = lp
                    }
                    ratingContainer.addView(header)

                    newGroups.forEach { (name, count) ->
                        val item = TextView(this).apply {
                            text = "$name: ${count}"
                            setTextColor(ContextCompat.getColor(this@StatsActivity, android.R.color.white))
                            textSize = 16f
                            background = ContextCompat.getDrawable(this@StatsActivity, R.drawable.bg_stats_item)
                            setPadding(
                                resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                                resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                                resources.getDimensionPixelSize(R.dimen.rating_item_padding),
                                resources.getDimensionPixelSize(R.dimen.rating_item_padding)
                            )
                            val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            lp2.topMargin = resources.getDimensionPixelSize(R.dimen.rating_item_margin)
                            layoutParams = lp2
                        }
                        ratingContainer.addView(item)
                    }
                }
            }
        }
    }

    private fun totalAppUsageMs(): Long {
        return getSharedPreferences("app_usage", MODE_PRIVATE).getLong("total_ms", 0L)
    }

    private fun artistDiversityIndex(): Double {
        val events = PlayHistory.readAll(this)
        if (events.isEmpty()) return 0.0
        val counts = hashMapOf<String, Int>()
        events.forEach { e ->
            var name = (e.artist ?: "").trim()
            if (name.isBlank()) {
                try {
                    val r = MediaMetadataRetriever()
                    e.trackPath?.let { r.setDataSource(it) }
                    name = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                    r.release()
                } catch (_: Exception) {}
            }
            if (name.isBlank()) name = "Unknown"
            counts[name] = (counts[name] ?: 0) + 1
        }
        val total = counts.values.sum().toDouble()
        if (total <= 0.0) return 0.0
        val n = counts.size.toDouble()
        if (n <= 1.0) return 0.0
        var h = 0.0
        counts.values.forEach { c ->
            val p = c / total
            h += -p * kotlin.math.ln(p)
        }
        val norm = h / kotlin.math.ln(n)
        return norm.coerceIn(0.0, 1.0)
    }

    private fun formatDiversity(value: Double): String {
        val v = kotlin.math.round(value * 100.0) / 100.0
        return String.format(Locale.getDefault(), "%.2f", v)
    }

    private fun newGroupsLast5Days(mode: Mode): List<Pair<String, Int>> {
        val now = System.currentTimeMillis()
        val cutoff = now - 5L * 24L * 3600_000L
        val events = PlayHistory.readAll(this)
        if (events.isEmpty()) return emptyList()

        return when (mode) {
            Mode.PLAYLISTS -> {
                val playlists = PlaylistManager.getPlaylists().filter { it.tracks.isNotEmpty() }
                val pathToPlaylists = hashMapOf<String, MutableList<String>>()
                playlists.forEach { pl ->
                    pl.tracks.forEach { t ->
                        val p = t.path ?: return@forEach
                        pathToPlaylists.getOrPut(p) { mutableListOf() }.add(pl.name)
                    }
                }
                val firstTs = hashMapOf<String, Long>()
                val counts = hashMapOf<String, Int>()
                events.forEach { e ->
                    val p = e.trackPath ?: return@forEach
                    val groups = pathToPlaylists[p] ?: return@forEach
                    groups.forEach { g ->
                        val prev = firstTs[g]
                        if (prev == null || e.timestamp < prev) firstTs[g] = e.timestamp
                        counts[g] = (counts[g] ?: 0) + 1
                    }
                }
                counts.filter { (g, c) -> (firstTs[g] ?: Long.MAX_VALUE) >= cutoff && c >= 5 }
                    .toList()
                    .sortedByDescending { it.second }
            }
            Mode.ARTISTS, Mode.ALBUMS, Mode.GENRES -> {
                val firstTs = hashMapOf<String, Long>()
                val counts = hashMapOf<String, Int>()
                events.forEach { e ->
                    var key = when (mode) {
                        Mode.ARTISTS -> e.artist
                        Mode.ALBUMS -> e.albumName
                        Mode.GENRES -> e.genre
                        else -> null
                    }?.trim()
                    if (key.isNullOrBlank()) {
                        try {
                            val r = MediaMetadataRetriever()
                            e.trackPath?.let { r.setDataSource(it) }
                            key = when (mode) {
                                Mode.ARTISTS -> r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                Mode.ALBUMS -> r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                Mode.GENRES -> r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                                else -> null
                            }?.trim()
                            r.release()
                        } catch (_: Exception) {}
                    }
                    if (key.isNullOrBlank()) key = "Unknown"
                    val prev = firstTs[key]
                    if (prev == null || e.timestamp < prev) firstTs[key!!] = e.timestamp
                    counts[key!!] = (counts[key] ?: 0) + 1
                }
                counts.filter { (g, c) -> (firstTs[g] ?: Long.MAX_VALUE) >= cutoff && c >= 5 }
                    .toList()
                    .sortedByDescending { it.second }
            }
            else -> emptyList()
        }
    }

    private fun totalAllTime(): Int {
        val prefs = getSharedPreferences("listening_stats", MODE_PRIVATE)
        val json = prefs.getString("stats", null) ?: return 0
        return try {
            val arr = org.json.JSONArray(json)
            var sum = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                sum += obj.optInt("count", 0)
            }
            sum
        } catch (_: Exception) { 0 }
    }

    private fun totalAllTimeDurationMs(): Long {
        val prefs = getSharedPreferences("listening_stats", MODE_PRIVATE)
        val json = prefs.getString("stats", null) ?: return 0L
        return try {
            val arr = org.json.JSONArray(json)
            var sumMs = 0L
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("path", null)
                val count = obj.optInt("count", 0)
                if (!path.isNullOrBlank() && count > 0) {
                    val d = getDurationMs(path)
                    sumMs += (d.toLong() * count.toLong())
                }
            }
            sumMs
        } catch (_: Exception) { 0L }
    }

    private fun readAllStatsPairs(): List<Pair<String, Int>> {
        val prefs = getSharedPreferences("listening_stats", MODE_PRIVATE)
        val json = prefs.getString("stats", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            val list = ArrayList<Pair<String, Int>>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("path", null)
                val count = obj.optInt("count", 0)
                if (!path.isNullOrBlank() && count > 0) list.add(path to count)
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun allTimeTotalsForMode(mode: Mode): List<Pair<String, Float>> {
        val stats = readAllStatsPairs()
        if (stats.isEmpty()) return emptyList()

        return when (mode) {
            Mode.PLAYLISTS -> {
                val playlists = PlaylistManager.getPlaylists().filter { it.tracks.isNotEmpty() }
                val pathToPlaylists = hashMapOf<String, MutableList<String>>()
                playlists.forEach { pl ->
                    pl.tracks.forEach { t ->
                        val p = t.path ?: return@forEach
                        pathToPlaylists.getOrPut(p) { mutableListOf() }.add(pl.name)
                    }
                }
                val totals = linkedMapOf<String, Float>()
                stats.forEach { (path, count) ->
                    val groups = pathToPlaylists[path]
                    val value = if (valueMode == ValueMode.TIME) (getDurationMs(path) / 60000f) * count else count.toFloat()
                    if (!groups.isNullOrEmpty()) {
                        groups.forEach { g -> totals[g] = (totals[g] ?: 0f) + value }
                    }
                }
                totals.toList().sortedByDescending { it.second }
            }
            Mode.ARTISTS, Mode.ALBUMS, Mode.GENRES -> {
                val totals = linkedMapOf<String, Float>()
                stats.forEach { (path, count) ->
                    var key = "Unknown"
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(path)
                        key = when (mode) {
                            Mode.ARTISTS -> r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            Mode.ALBUMS -> r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            Mode.GENRES -> r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                            else -> null
                        } ?: "Unknown"
                        r.release()
                    } catch (_: Exception) {}
                    if (key.isBlank()) key = "Unknown"
                    val value = if (valueMode == ValueMode.TIME) (getDurationMs(path) / 60000f) * count else count.toFloat()
                    totals[key] = (totals[key] ?: 0f) + value
                }
                totals.toList().sortedByDescending { it.second }
            }
            else -> emptyList()
        }
    }

    private fun aggregate(events: List<PlayHistory.PlayEvent>): Map<String, List<Float>> {
        // Определяем границы и список бакетов по выбранному периоду
        val dates = buildBuckets(events)
        val indexOfDate = dates.mapIndexed { idx, ts -> ts to idx }.toMap()

        fun idxFor(ts: Long): Int? {
            // Находим бакет по началу периода
            val key = bucketStart(ts)
            return indexOfDate[key]
        }

        val seriesMap = linkedMapOf<String, MutableList<Float>>()
        fun add(series: String, idx: Int, value: Float) {
            val arr = seriesMap.getOrPut(series) { MutableList(dates.size) { 0f } }
            arr[idx] = arr[idx] + value
        }

        when (mode) {
            Mode.ALL -> {
                events.forEach { e ->
                    val v = if (valueMode == ValueMode.TIME) minutesForEvent(e) else 1f
                    idxFor(e.timestamp)?.let { add("Общее", it, v) }
                }
            }
            Mode.ARTISTS -> {
                events.forEach { e ->
                    var name = (e.artist ?: "").trim()
                    if (name.isBlank()) {
                        try {
                            val r = MediaMetadataRetriever()
                            e.trackPath?.let { r.setDataSource(it) }
                            name = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                            r.release()
                        } catch (_: Exception) {}
                    }
                    if (name.isBlank()) name = "Unknown"
                    val v = if (valueMode == ValueMode.TIME) minutesForEvent(e) else 1f
                    idxFor(e.timestamp)?.let { add(name, it, v) }
                }
            }
            Mode.ALBUMS -> {
                events.forEach { e ->
                    var name = (e.albumName ?: "").trim()
                    if (name.isBlank()) {
                        try {
                            val r = MediaMetadataRetriever()
                            e.trackPath?.let { r.setDataSource(it) }
                            name = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                            r.release()
                        } catch (_: Exception) {}
                    }
                    if (name.isBlank()) name = "Unknown"
                    val v = if (valueMode == ValueMode.TIME) minutesForEvent(e) else 1f
                    idxFor(e.timestamp)?.let { add(name, it, v) }
                }
            }
            Mode.GENRES -> {
                events.forEach { e ->
                    var name = (e.genre ?: "").trim()
                    if (name.isBlank()) {
                        try {
                            val r = MediaMetadataRetriever()
                            e.trackPath?.let { r.setDataSource(it) }
                            name = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
                            r.release()
                        } catch (_: Exception) {}
                    }
                    if (name.isBlank()) name = "Unknown"
                    val v = if (valueMode == ValueMode.TIME) minutesForEvent(e) else 1f
                    idxFor(e.timestamp)?.let { add(name, it, v) }
                }
            }
            Mode.PLAYLISTS -> {
                // Карта: путь трека -> список плейлистов, в которых он есть
                val playlists = PlaylistManager.getPlaylists().filter { it.tracks.isNotEmpty() }
                val map = hashMapOf<String, List<String>>()
                playlists.forEach { pl ->
                    pl.tracks.forEach { t ->
                        val p = t.path ?: return@forEach
                        val current = map[p]?.toMutableList() ?: mutableListOf()
                        current.add(pl.name)
                        map[p] = current
                    }
                }
                events.forEach { e ->
                    val p = e.trackPath
                    if (p != null) {
                        val groups = map[p]
                        if (!groups.isNullOrEmpty()) {
                            val idx = idxFor(e.timestamp) ?: return@forEach
                            val v = if (valueMode == ValueMode.TIME) minutesForEvent(e) else 1f
                            groups.forEach { name -> add(name, idx, v) }
                        }
                    }
                }
            }
        }

        // Для Плейлистов оставим ограничение топ-12; для Альбомов/Жанров/Исполнителей показываем все группы
        if (mode == Mode.PLAYLISTS && seriesMap.size > 12) {
            val top = seriesMap.entries
                .map { it.key to it.value.sum() }
                .sortedByDescending { it.second }
                .take(12)
                .map { it.first }
                .toSet()
            val filtered = linkedMapOf<String, MutableList<Float>>()
            seriesMap.filterKeys { it in top }.forEach { (k, v) -> filtered[k] = v }
            return filtered
        }

        return seriesMap
    }

    private fun buildBuckets(events: List<PlayHistory.PlayEvent>): List<Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val buckets = mutableListOf<Long>()

        when (period) {
            Period.DAY -> {
                // Последние 30 дней, включая сегодня
                val todayStart = bucketStart(now)
                val dayCal = Calendar.getInstance().apply { timeInMillis = todayStart }
                // Сдвинем назад на 29 дней, затем вперед
                dayCal.add(Calendar.DAY_OF_YEAR, -29)
                repeat(30) {
                    buckets.add(dayCal.timeInMillis)
                    dayCal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            Period.WEEK -> {
                // Последние 12 недель, включая текущую неделю
                // Начинаем с понедельника текущей недели и идем назад
                val calNow = Calendar.getInstance().apply { timeInMillis = now }
                // Устанавливаем на понедельник текущей недели
                calNow.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calNow.set(Calendar.HOUR_OF_DAY, 0)
                calNow.set(Calendar.MINUTE, 0)
                calNow.set(Calendar.SECOND, 0)
                calNow.set(Calendar.MILLISECOND, 0)

                // Идем назад на 11 недель (всего 12 недель)
                repeat(12) { weekIndex ->
                    val weekStart = calNow.timeInMillis
                    buckets.add(weekStart)
                    calNow.add(Calendar.WEEK_OF_YEAR, -1)
                }
                // Разворачиваем список, чтобы шли от старых к новым
                buckets.reverse()
            }
            Period.MONTH -> {
                // Последние 12 месяцев, включая текущий месяц
                val thisMonth = bucketStart(now)
                val mCal = Calendar.getInstance().apply { timeInMillis = thisMonth }
                mCal.add(Calendar.MONTH, -11)
                repeat(12) {
                    buckets.add(mCal.timeInMillis)
                    mCal.add(Calendar.MONTH, 1)
                }
            }
        }
        lastBuckets = buckets
        return buckets
    }

    private fun getFirstInstallTime(): Long {
        return try {
            packageManager.getPackageInfo(packageName, 0).firstInstallTime
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun bucketStart(ts: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ts; set(Calendar.MILLISECOND, 0); set(Calendar.SECOND, 0); set(Calendar.MINUTE, 0); set(Calendar.HOUR_OF_DAY, 0) }
        when (period) {
            Period.DAY -> Unit
            Period.WEEK -> {
                // Начало недели: понедельник (вне зависимости от локали)
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            Period.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun nextBucket(ts: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        when (period) {
            Period.DAY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            Period.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            Period.MONTH -> cal.add(Calendar.MONTH, 1)
        }
        return bucketStart(cal.timeInMillis)
    }

    private fun applyXAxisLabels() {
        val x = chart.xAxis

        val labels: List<String> = when (period) {
            Period.DAY -> {
                lastBuckets.mapIndexed { index, ts ->
                    val dayOfMonth = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH)
                    // Показываем метку каждые 5 дней ИЛИ если это последний день
                    if (index % 5 == 0 || index == lastBuckets.lastIndex) {
                        dayOfMonth.toString()
                    } else ""
                }
            }

            Period.WEEK -> {
                lastBuckets.mapIndexed { index, _ ->
                    // В WEEK показываем номер недели (1–12)
                    (index + 1).toString()
                }
            }

            Period.MONTH -> {
                lastBuckets.map { ts ->
                    val month = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.MONTH)
                    (month + 1).toString().padStart(2, '0')
                }
            }
        }

        // Гарантируем целочисленные позиции
        x.isGranularityEnabled = true
        x.granularity = 1f
        x.setLabelCount(minOf(labels.size, 30), false)

        x.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                val i = Math.round(value).toInt()
                return if (i in labels.indices) labels[i] else ""
            }
        }

        // Отключаем обрезку первой и последней метки
        x.setAvoidFirstLastClipping(false)
    }

    private fun updateMonthSeparators() {
        val x = chart.xAxis
        x.removeAllLimitLines()
        if (lastBuckets.isEmpty()) return

        val baseColor = ThemeManager.getSecondaryColor(this)
        val monthNames = arrayOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")

        // Определяем границы месяцев по неделям/дням
        val monthRanges = mutableListOf<Pair<Float, Float>>() // startIdx, endIdx
        val monthLabels = mutableListOf<Triple<Float, String, Float>>() // xPos, label, startIdx (только для WEEK)

        var currentMonth = -1
        var monthStartIdx: Float? = null

        lastBuckets.forEachIndexed { idx, ts ->
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            val month = cal.get(Calendar.MONTH)

            if (currentMonth != month || currentMonth == -1) {
                // Завершаем предыдущий месяц
                if (monthStartIdx != null) {
                    val endIdx = idx.toFloat()
                    monthRanges.add(monthStartIdx to endIdx)

                    // Сохраняем подпись только для WEEK
                    if (period == Period.WEEK) {
                        val label = monthNames[currentMonth] // вместо cal.get(Calendar.MONTH)
                        monthLabels.add(Triple(monthStartIdx, label, monthStartIdx))
                    }
                }


                currentMonth = month
                monthStartIdx = idx.toFloat()
            }
        }

        // Последний месяц
        if (monthStartIdx != null) {
            monthRanges.add(monthStartIdx to lastBuckets.size.toFloat())
            if (period == Period.WEEK) {
                val cal = Calendar.getInstance().apply { timeInMillis = lastBuckets.last() }
                val label = monthNames[cal.get(Calendar.MONTH)]
                monthLabels.add(Triple(monthStartIdx, label, monthStartIdx))
            }
        }

        // === Рендеринг ===
        chart.renderer = object : com.github.mikephil.charting.renderer.LineChartRenderer(
            chart, chart.animator, chart.viewPortHandler
        ) {
            override fun drawExtras(c: android.graphics.Canvas) {
                super.drawExtras(c)

                val trans = chart.getTransformer(chart.data.getDataSetByIndex(0).axisDependency)
                val vp = chart.viewPortHandler
                val top = vp.contentTop()
                val bottom = vp.contentBottom()

                val paintBand = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }

                val paintText = android.graphics.Paint().apply {
                    color = baseColor
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.LEFT
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                // Рисуем фоновые блоки
                monthRanges.forEachIndexed { i, (startIdx, endIdx) ->
                    val ptsStart = floatArrayOf(startIdx, 0f)
                    val ptsEnd = floatArrayOf(endIdx, 0f)
                    trans.pointValuesToPixel(ptsStart)
                    trans.pointValuesToPixel(ptsEnd)

                    val left = maxOf(ptsStart[0], vp.contentLeft())
                    val right = minOf(ptsEnd[0], vp.contentRight())
                    if (right - left < 2f) return@forEachIndexed

                    paintBand.color = if (i % 2 == 0) 0x17d0d0d0.toInt() else 0x10d0d0d0.toInt()
                    c.drawRect(left, top, right, bottom, paintBand)
                }

                // Подписи — только для WEEK
                if (period == Period.WEEK) {
                    monthLabels.forEach { (_, label, startIdx) ->
                        val pts = floatArrayOf(startIdx, 0f)
                        trans.pointValuesToPixel(pts)
                        val textX = maxOf(pts[0], vp.contentLeft()) + 8f
                        val textY = top + 32f
                        c.drawText(label, textX, textY, paintText)
                    }
                }
            }
        }
        chart.invalidate()
    }

    // Dev helper: generate synthetic events across artists/albums/genres and playlists
    private fun seedTestData(): Int {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 3600_000L
        val start = now - 120L * dayMs // 120 дней назад

        val artists = listOf("Alpha", "Beta", "Gamma")
        val albums = listOf("A-One", "B-Two", "C-Three")
        val genres = listOf("Rock", "Pop", "Jazz")

        // Соберём доступные пути из плейлистов (чтобы режим Плейлисты тоже заполнился)
        val playlistTracks: List<String> = try {
            PlaylistManager.getPlaylists()
                .flatMap { it.tracks }
                .mapNotNull { it.path }
        } catch (_: Exception) { emptyList() }

        var added = 0
        val rnd = kotlin.random.Random(System.currentTimeMillis())
        // Каждый день создадим 3-8 событий со смешанными группами
        var ts = start
        while (ts <= now) {
            val eventsToday = 3 + rnd.nextInt(6)
            repeat(eventsToday) {
                val a = artists[rnd.nextInt(artists.size)]
                val al = albums[rnd.nextInt(albums.size)]
                val g = genres[rnd.nextInt(genres.size)]
                // Иногда используем реальный путь из плейлистов, если есть
                val p = if (playlistTracks.isNotEmpty() && rnd.nextBoolean()) playlistTracks[rnd.nextInt(playlistTracks.size)] else null
                val ev = PlayHistory.PlayEvent(
                    timestamp = ts + rnd.nextLong(0, dayMs),
                    trackPath = p,
                    trackName = "Track-${rnd.nextInt(999)}",
                    artist = a,
                    albumName = al,
                    genre = g
                )
                PlayHistory.append(this, ev)
                added++
            }
            ts += dayMs
        }
        // Уведомим график
        sendBroadcast(Intent("com.arotter.music.STATS_UPDATED").setPackage(packageName))
        return added
    }

    private fun minutesForEvent(e: PlayHistory.PlayEvent): Float {
        val p = e.trackPath ?: return 0f
        val ms = getDurationMs(p)
        return (ms / 60000f).coerceAtLeast(0f)
    }

    private fun getDurationMs(path: String): Int {
        durationCacheMs[path]?.let { return it }
        var d = 0
        try {
            val r = MediaMetadataRetriever()
            r.setDataSource(path)
            val s = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            r.release()
            d = s?.toIntOrNull() ?: 0
        } catch (_: Exception) { }
        durationCacheMs[path] = d
        return d
    }

    private fun formatMinutes(mins: Int): String {
        return "$mins мин"
    }

    private fun formatHours(ms: Long): String {
        val hours = ms.toDouble() / 3600000.0
        val rounded = kotlin.math.round(hours * 10.0) / 10.0
        return rounded.toString()
    }

    private fun formatPercent(value: Float): String {
        val rounded = kotlin.math.round(value * 10f) / 10f
        return "$rounded%"
    }
}