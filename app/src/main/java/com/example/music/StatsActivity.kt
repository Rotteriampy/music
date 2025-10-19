package com.example.music

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.*
import kotlin.math.max

class StatsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var back: ImageButton
    private lateinit var chart: LineChart
    private lateinit var extraTitle: TextView
    private lateinit var extraValue: TextView

    // Selectors
    private lateinit var btnModeAll: Button
    private lateinit var btnModePlaylists: Button
    private lateinit var btnModeArtists: Button
    private lateinit var btnModeAlbums: Button
    private lateinit var btnModeGenres: Button
    private lateinit var btnDays: Button
    private lateinit var btnWeeks: Button
    private lateinit var btnMonths: Button
    private lateinit var btnR7: Button
    private lateinit var btnR30: Button
    private lateinit var btnR90: Button
    private lateinit var btnR365: Button
    private lateinit var btnRAll: Button

    private enum class Mode { ALL, PLAYLISTS, ARTISTS, ALBUMS, GENRES }
    private enum class Period { DAY, WEEK, MONTH }

    private var mode = Mode.ALL
    private var period = Period.DAY
    private var rangeDays: Int? = 30 // null => all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        root = findViewById(R.id.statsRoot)
        back = findViewById(R.id.btnBackStats)
        chart = findViewById(R.id.lineChart)
        extraTitle = findViewById(R.id.extraTitle)
        extraValue = findViewById(R.id.extraValue)

        btnModeAll = findViewById(R.id.btnModeAll)
        btnModePlaylists = findViewById(R.id.btnModePlaylists)
        btnModeArtists = findViewById(R.id.btnModeArtists)
        btnModeAlbums = findViewById(R.id.btnModeAlbums)
        btnModeGenres = findViewById(R.id.btnModeGenres)
        btnDays = findViewById(R.id.btnPeriodDays)
        btnWeeks = findViewById(R.id.btnPeriodWeeks)
        btnMonths = findViewById(R.id.btnPeriodMonths)
        btnR7 = findViewById(R.id.btnRange7)
        btnR30 = findViewById(R.id.btnRange30)
        btnR90 = findViewById(R.id.btnRange90)
        btnR365 = findViewById(R.id.btnRange365)
        btnRAll = findViewById(R.id.btnRangeAll)

        restoreColor()

        back.setOnClickListener { finish() }

        initChart()
        initSelectors()
        refresh()
    }

    private fun restoreColor() {
        val sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE)
        val scheme = sharedPreferences.getInt("color_scheme", 0)
        val color = when (scheme) {
            1 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.darker_gray))
            2 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            3 -> ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            else -> ColorDrawable(ContextCompat.getColor(this, android.R.color.black))
        }
        root.background = color
    }

    private fun initChart() {
        chart.description.isEnabled = false
        chart.legend.apply {
            form = Legend.LegendForm.LINE
            textColor = ContextCompat.getColor(this@StatsActivity, android.R.color.white)
        }
        chart.axisLeft.textColor = ContextCompat.getColor(this, android.R.color.white)
        chart.axisRight.isEnabled = false
        chart.xAxis.textColor = ContextCompat.getColor(this, android.R.color.white)
        chart.setNoDataText("Нет данных для выбранного периода")
        chart.setNoDataTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun initSelectors() {
        fun modeClick(m: Mode) { mode = m; refresh() }
        btnModeAll.setOnClickListener { modeClick(Mode.ALL) }
        btnModePlaylists.setOnClickListener { modeClick(Mode.PLAYLISTS) }
        btnModeArtists.setOnClickListener { modeClick(Mode.ARTISTS) }
        btnModeAlbums.setOnClickListener { modeClick(Mode.ALBUMS) }
        btnModeGenres.setOnClickListener { modeClick(Mode.GENRES) }

        btnDays.setOnClickListener { period = Period.DAY; refresh() }
        btnWeeks.setOnClickListener { period = Period.WEEK; refresh() }
        btnMonths.setOnClickListener { period = Period.MONTH; refresh() }

        btnR7.setOnClickListener { rangeDays = 7; refresh() }
        btnR30.setOnClickListener { rangeDays = 30; refresh() }
        btnR90.setOnClickListener { rangeDays = 90; refresh() }
        btnR365.setOnClickListener { rangeDays = 365; refresh() }
        btnRAll.setOnClickListener { rangeDays = null; refresh() }
    }

    private fun refresh() {
        val events = PlayHistory.readAll(this)
        val now = System.currentTimeMillis()
        val fromTs = rangeDays?.let { now - it * 24L * 3600_000L }
        val filtered = if (fromTs != null) events.filter { it.timestamp >= fromTs } else events

        if (filtered.isEmpty()) {
            chart.clear()
            extraTitle.text = if (mode == Mode.ALL) "Всего прослушиваний (за все время)" else "ТОП групп"
            extraValue.text = if (mode == Mode.ALL) totalAllTime().toString() else "0"
            return
        }

        val bucketed = aggregate(filtered)
        val lineData = LineData()
        val colors = listOf(
            0xFF66BB6A.toInt(), 0xFF42A5F5.toInt(), 0xFFFFA726.toInt(), 0xFFAB47BC.toInt(), 0xFFEF5350.toInt(),
            0xFF26A69A.toInt(), 0xFF5C6BC0.toInt(), 0xFF78909C.toInt()
        )
        var colorIdx = 0

        // Подписи X будут по индексу; для человекочитаемых дат можно позже добавить форматтер
        bucketed.forEach { (seriesName, points) ->
            val entries = points.mapIndexed { idx, count -> Entry(idx.toFloat(), count.toFloat()) }
            val set = LineDataSet(entries, seriesName).apply {
                color = colors[colorIdx % colors.size]
                setCircleColor(color)
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
            }
            colorIdx++
            lineData.addDataSet(set)
        }
        chart.data = lineData
        chart.invalidate()

        // Доп. данные
        if (mode == Mode.ALL) {
            extraTitle.text = "Всего прослушиваний (за все время)"
            extraValue.text = totalAllTime().toString()
        } else {
            val totals = when (mode) {
                Mode.PLAYLISTS, Mode.ARTISTS, Mode.ALBUMS, Mode.GENRES -> bucketed.mapValues { (_, v) -> v.sum() }
                else -> emptyMap()
            }.toList().sortedByDescending { it.second }.take(5)
            extraTitle.text = "ТОП групп"
            extraValue.text = if (totals.isEmpty()) "—" else totals.joinToString("\n") { (k, v) -> "$k — $v" }
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

    private fun aggregate(events: List<PlayHistory.PlayEvent>): Map<String, List<Int>> {
        // Определяем границы и список бакетов по выбранному периоду
        val dates = buildBuckets(events)
        val indexOfDate = dates.mapIndexed { idx, ts -> ts to idx }.toMap()

        fun idxFor(ts: Long): Int? {
            // Находим бакет по началу периода
            val key = bucketStart(ts)
            return indexOfDate[key]
        }

        val seriesMap = linkedMapOf<String, MutableList<Int>>()
        fun add(series: String, idx: Int) {
            val arr = seriesMap.getOrPut(series) { MutableList(dates.size) { 0 } }
            arr[idx] = arr[idx] + 1
        }

        when (mode) {
            Mode.ALL -> {
                events.forEach { e -> idxFor(e.timestamp)?.let { add("Общее", it) } }
            }
            Mode.ARTISTS -> {
                events.forEach { e ->
                    val name = (e.artist ?: "Unknown").ifBlank { "Unknown" }
                    idxFor(e.timestamp)?.let { add(name, it) }
                }
            }
            Mode.ALBUMS -> {
                events.forEach { e ->
                    val name = (e.albumName ?: "Unknown").ifBlank { "Unknown" }
                    idxFor(e.timestamp)?.let { add(name, it) }
                }
            }
            Mode.GENRES -> {
                events.forEach { e ->
                    val name = (e.genre ?: "Unknown").ifBlank { "Unknown" }
                    idxFor(e.timestamp)?.let { add(name, it) }
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
                            groups.forEach { name -> add(name, idx) }
                        }
                    }
                }
            }
        }

        // Ограничим кол-во серий (для читаемости) топ-N по сумме, если их очень много
        if (mode != Mode.ALL && seriesMap.size > 12) {
            val top = seriesMap.entries
                .map { it.key to it.value.sum() }
                .sortedByDescending { it.second }
                .take(12)
                .map { it.first }
                .toSet()
            val filtered = linkedMapOf<String, MutableList<Int>>()
            seriesMap.filterKeys { it in top }.forEach { (k, v) -> filtered[k] = v }
            return filtered
        }

        return seriesMap
    }

    private fun buildBuckets(events: List<PlayHistory.PlayEvent>): List<Long> {
        val minTs = events.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        val maxTs = events.maxOfOrNull { it.timestamp } ?: minTs
        val fromTs = rangeDays?.let { max(maxTs - it * 24L * 3600_000L, 0L) } ?: minTs

        val list = mutableListOf<Long>()
        var cur = bucketStart(fromTs)
        val end = bucketStart(maxTs)
        while (cur <= end) {
            list.add(cur)
            cur = nextBucket(cur)
        }
        return list
    }

    private fun bucketStart(ts: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ts; set(Calendar.MILLISECOND, 0); set(Calendar.SECOND, 0); set(Calendar.MINUTE, 0); set(Calendar.HOUR_OF_DAY, 0) }
        when (period) {
            Period.DAY -> Unit
            Period.WEEK -> {
                // Начало недели: понедельник
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
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
}
