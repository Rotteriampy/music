package com.example.music

import android.os.Bundle
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

class StatsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var back: ImageButton
    private lateinit var chart: LineChart
    private lateinit var extraTitle: TextView
    private lateinit var extraValue: TextView

    // Selectors
    private lateinit var spMode: Spinner
    private lateinit var rgPeriod: RadioGroup

    private enum class Mode { ALL, PLAYLISTS, ARTISTS, ALBUMS, GENRES }
    private enum class Period { DAY, WEEK, MONTH }

    private var mode = Mode.ALL
    private var period = Period.DAY
    // Диапазон больше не используется — строим по всем данным

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        root = findViewById(R.id.statsRoot)
        back = findViewById(R.id.btnBackStats)
        chart = findViewById(R.id.lineChart)
        extraTitle = findViewById(R.id.extraTitle)
        extraValue = findViewById(R.id.extraValue)
        // Hidden developer shortcut: long-press title to seed test data
        findViewById<TextView>(R.id.statsTitle).setOnLongClickListener {
            val added = seedTestData()
            android.widget.Toast.makeText(this, "Добавлено тестовых событий: $added", android.widget.Toast.LENGTH_SHORT).show()
            refresh()
            true
        }

        spMode = findViewById(R.id.spMode)
        rgPeriod = findViewById(R.id.rgPeriod)

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
        // Целочисленные значения по осям
        chart.axisLeft.granularity = 1f
        chart.xAxis.granularity = 1f
        chart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                return value.toInt().toString()
            }
        }
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                return value.toInt().toString()
            }
        }
        chart.setNoDataText("Нет данных для выбранного периода")
        chart.setNoDataTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun initSelectors() {
        val modes = listOf("Общее" to Mode.ALL, "Плейлисты" to Mode.PLAYLISTS, "Исполнители" to Mode.ARTISTS, "Альбомы" to Mode.ALBUMS, "Жанры" to Mode.GENRES)
        val modeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, modes.map { it.first }) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(ContextCompat.getColor(this@StatsActivity, android.R.color.white))
                return v
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? TextView)?.setTextColor(ContextCompat.getColor(this@StatsActivity, android.R.color.black))
                return v
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spMode.adapter = modeAdapter
        spMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
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
    }

    private fun refresh() {
        val events = PlayHistory.readAll(this)
        val filtered = events // больше не фильтруем по диапазону — используем все данные

        if (filtered.isEmpty()) {
            chart.clear()
            if (mode == Mode.ALL) {
                extraTitle.text = "Всего прослушиваний: ${totalAllTime()}"
                extraValue.text = ""
            } else {
                extraTitle.text = "Рейтинг"
                extraValue.text = "0"
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

        // Доп. данные (всегда за всё время)
        if (mode == Mode.ALL) {
            extraTitle.text = "Всего прослушиваний: ${totalAllTime()}"
            extraValue.text = ""
        } else {
            val totals = allTimeTotalsForMode(mode).take(5)
            extraTitle.text = "Рейтинг"
            extraValue.text = if (totals.isEmpty()) "—" else totals.joinToString("\n") { (k, v) -> "$k: $v" }
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

    private fun allTimeTotalsForMode(mode: Mode): List<Pair<String, Int>> {
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
                val totals = linkedMapOf<String, Int>()
                stats.forEach { (path, count) ->
                    val groups = pathToPlaylists[path]
                    if (!groups.isNullOrEmpty()) {
                        groups.forEach { g -> totals[g] = (totals[g] ?: 0) + count }
                    }
                }
                totals.toList().sortedByDescending { it.second }
            }
            Mode.ARTISTS, Mode.ALBUMS, Mode.GENRES -> {
                val totals = linkedMapOf<String, Int>()
                stats.forEach { (path, count) ->
                    var key = "Unknown"
                    try {
                        val r = android.media.MediaMetadataRetriever()
                        r.setDataSource(path)
                        key = when (mode) {
                            Mode.ARTISTS -> r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            Mode.ALBUMS -> r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            Mode.GENRES -> r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
                            else -> null
                        } ?: "Unknown"
                        r.release()
                    } catch (_: Exception) {}
                    if (key.isBlank()) key = "Unknown"
                    totals[key] = (totals[key] ?: 0) + count
                }
                totals.toList().sortedByDescending { it.second }
            }
            else -> emptyList()
        }
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
                    var name = (e.artist ?: "").trim()
                    if (name.isBlank()) {
                        try {
                            val r = android.media.MediaMetadataRetriever()
                            e.trackPath?.let { r.setDataSource(it) }
                            name = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                            r.release()
                        } catch (_: Exception) {}
                    }
                    if (name.isBlank()) name = "Unknown"
                    idxFor(e.timestamp)?.let { add(name, it) }
                }
            }
            Mode.ALBUMS -> {
                events.forEach { e ->
                    var name = (e.albumName ?: "").trim()
                    if (name.isBlank()) {
                        try {
                            val r = android.media.MediaMetadataRetriever()
                            e.trackPath?.let { r.setDataSource(it) }
                            name = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                            r.release()
                        } catch (_: Exception) {}
                    }
                    if (name.isBlank()) name = "Unknown"
                    idxFor(e.timestamp)?.let { add(name, it) }
                }
            }
            Mode.GENRES -> {
                events.forEach { e ->
                    var name = (e.genre ?: "").trim()
                    if (name.isBlank()) {
                        try {
                            val r = android.media.MediaMetadataRetriever()
                            e.trackPath?.let { r.setDataSource(it) }
                            name = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
                            r.release()
                        } catch (_: Exception) {}
                    }
                    if (name.isBlank()) name = "Unknown"
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

        // Для Плейлистов оставим ограничение топ-12; для Альбомов/Жанров/Исполнителей показываем все группы
        if (mode == Mode.PLAYLISTS && seriesMap.size > 12) {
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
        // Начинаем ось времени с момента установки приложения
        val startTs = getFirstInstallTime()
        val endTs = System.currentTimeMillis()

        val list = mutableListOf<Long>()
        var cur = bucketStart(startTs)
        val end = bucketStart(endTs)
        while (cur <= end) {
            list.add(cur)
            cur = nextBucket(cur)
        }
        return list
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
        sendBroadcast(Intent("com.example.music.STATS_UPDATED").setPackage(packageName))
        return added
    }
}
