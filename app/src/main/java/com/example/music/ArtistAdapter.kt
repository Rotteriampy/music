package com.arotter.music

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.net.Uri
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Matrix
import android.graphics.RectF
import java.io.InputStream
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class ArtistAdapter(
    private var artists: List<Artist>,
    private val context: Context
) : RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder>() {

    init {
        artists = reorderUnknownFirst(artists)
        setHasStableIds(true)
    }

    class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val artistName: TextView = itemView.findViewById(R.id.playlistName)
        val tracksInfo: TextView = itemView.findViewById(R.id.tracksInfo)
        val coverImage: ImageView = itemView.findViewById(R.id.playlistCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false)
        return ArtistViewHolder(view)
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<ImageView, Job>()
    private val infoCache = ConcurrentHashMap<String, String>()

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artists[position]

        // Загружаем кастомные данные
        val prefs = context.getSharedPreferences("custom_artists", Context.MODE_PRIVATE)
        val customName = prefs.getString("artist_${artist.name}_name", null)
        val customCoverUri = prefs.getString("artist_${artist.name}_cover", null)

        // Показываем название (кастомное или оригинальное)
        holder.artistName.text = customName ?: artist.name
        // Цвета по умолчанию из темы
        holder.artistName.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextPrimary))
        holder.tracksInfo.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextSecondary))

        // Сбрасываем предыдущую загрузку и ставим плейсхолдер
        jobs.remove(holder.coverImage)?.cancel()
        val placeholder = R.drawable.ic_album_placeholder
        holder.coverImage.setImageResource(placeholder)

        // Загружаем обложку
        if (artist.name.equals("Unknown", ignoreCase = true) || artist.name.equals("<unknown>", ignoreCase = true)) {
            holder.coverImage.setImageResource(R.drawable.ic_album_placeholder)
        } else if (customCoverUri != null) {
            // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой
            val cacheKey = getArtistCacheKey(artist.name, "custom_uri_${customCoverUri}")
            holder.coverImage.tag = cacheKey
            val job = ioScope.launch {
                val roundedKey = "${cacheKey}_r128"
                var rounded = DiskImageCache.getBitmap(roundedKey)
                if (rounded == null) {
                    var bmp = DiskImageCache.getBitmap(cacheKey)
                    if (bmp == null) {
                        bmp = try {
                            val uri = Uri.parse(customCoverUri)
                            val input: InputStream? = context.contentResolver.openInputStream(uri)
                            val decoded = input?.let { BitmapFactory.decodeStream(it) }
                            input?.close()
                            if (decoded != null) DiskImageCache.putBitmap(cacheKey, decoded)
                            decoded
                        } catch (_: Exception) { null }
                    }
                    if (bmp != null) {
                        rounded = roundToViewFixed(bmp, holder.coverImage, 12f)
                        DiskImageCache.putBitmap(roundedKey, rounded!!)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (holder.coverImage.tag == cacheKey && rounded != null) {
                        holder.coverImage.setImageBitmap(rounded)
                        // Есть аватарка -> белые тексты
                        holder.artistName.setTextColor(android.graphics.Color.WHITE)
                        holder.tracksInfo.setTextColor(android.graphics.Color.WHITE)
                    }
                }
            }
            jobs[holder.coverImage] = job
        } else {
            val cachedTrackPath = prefs.getString("artist_${artist.name}_cover_track", null)
            var set = false
            if (cachedTrackPath != null) {
                // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой
                val key = getArtistCacheKey(artist.name, "cover_track_${cachedTrackPath}")
                holder.coverImage.tag = key
                val job = ioScope.launch {
                    val ok = loadCoverAsyncRounded(cachedTrackPath, holder.coverImage, key) {
                        // Белые тексты при успешной загрузке
                        holder.artistName.setTextColor(android.graphics.Color.WHITE)
                        holder.tracksInfo.setTextColor(android.graphics.Color.WHITE)
                    }
                    if (ok) set = true
                }
                jobs[holder.coverImage] = job
            }

            if (!set) {
                val firstWithPath = artist.tracks.firstOrNull { it.path != null }?.path
                if (firstWithPath != null) {
                    // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой
                    val key = getArtistCacheKey(artist.name, "cover_track_${firstWithPath}")
                    holder.coverImage.tag = key
                    val job = ioScope.launch {
                        val ok = loadCoverAsyncRounded(firstWithPath, holder.coverImage, key) {
                            holder.artistName.setTextColor(android.graphics.Color.WHITE)
                            holder.tracksInfo.setTextColor(android.graphics.Color.WHITE)
                        }
                        if (ok) {
                            prefs.edit().putString("artist_${artist.name}_cover_track", firstWithPath).apply()
                        }
                    }
                    jobs[holder.coverImage] = job
                }
            }
            if (!set) {
                val displayName = (customName ?: artist.name).trim()
                // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой
                val letterKey = getArtistCacheKey(artist.name, "letter_cover")
                var rounded = DiskImageCache.getBitmap(letterKey)
                if (rounded == null) {
                    val bmp = generateLetterCover(displayName)
                    rounded = roundToViewFixed(bmp, holder.coverImage, 12f)
                    DiskImageCache.putBitmap(letterKey, rounded)
                }
                holder.coverImage.setImageBitmap(rounded)
                // Буквенная обложка — тоже аватарка: делаем тексты белыми
                holder.artistName.setTextColor(android.graphics.Color.WHITE)
                holder.tracksInfo.setTextColor(android.graphics.Color.WHITE)
            }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, ArtistActivity::class.java).apply {
                putExtra("ARTIST_NAME", artist.name)
            }
            context.startActivity(intent)
        }

        val info = infoCache.getOrPut(artist.name) {
            val trackCount = artist.tracks.size
            val totalDurationMs = artist.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
            val totalMinutes = totalDurationMs / 1000 / 60
            val totalSeconds = (totalDurationMs / 1000) % 60
            "${trackCount} треков • ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}"
        }
        holder.tracksInfo.text = info
    }

    private suspend fun loadCoverAsyncRounded(trackPath: String, imageView: ImageView, cacheKey: String, onSuccess: (() -> Unit)? = null): Boolean {
        val roundedKey = cacheKey + "_r128"
        var rounded = DiskImageCache.getBitmap(roundedKey)
        if (rounded == null) {
            var bmp = DiskImageCache.getBitmap(cacheKey)
            if (bmp == null) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(trackPath)
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) {
                        bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                        if (bmp != null) DiskImageCache.putBitmap(cacheKey, bmp!!)
                    }
                    retriever.release()
                } catch (_: Exception) {
                }
            }
            if (bmp != null) {
                rounded = roundToViewFixed(bmp!!, imageView, 12f)
                DiskImageCache.putBitmap(roundedKey, rounded!!)
            }
        }
        return withContext(Dispatchers.Main) {
            if (imageView.tag == cacheKey && rounded != null) {
                imageView.setImageBitmap(rounded)
                try { onSuccess?.invoke() } catch (_: Exception) {}
                true
            } else false
        }
    }

    private fun generateLetterCover(name: String): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)

        val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            textSize = size * 0.6f
        }
        val bounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, bounds)
        val x = (size - bounds.width()) / 2f - bounds.left
        val y = (size + bounds.height()) / 2f - bounds.bottom
        canvas.drawText(letter, x, y, paint)
        return bmp
    }

    private fun roundToViewFixed(src: Bitmap, view: ImageView, radiusDp: Float): Bitmap {
        val radiusPx = (radiusDp * view.resources.displayMetrics.density)
        val sizePx = (128f * view.resources.displayMetrics.density).toInt()
        val outW = sizePx
        val outH = sizePx
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isDither = true }
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(outW.toFloat() / src.width, outH.toFloat() / src.height)
        val dx = (outW - src.width * scale) / 2f
        val dy = (outH - src.height * scale) / 2f
        val matrix = Matrix().apply { setScale(scale, scale); postTranslate(dx, dy) }
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        val rect = RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        return out
    }

    override fun getItemId(position: Int): Long {
        return artists[position].name.hashCode().toLong()
    }

    override fun onViewRecycled(holder: ArtistViewHolder) {
        jobs.remove(holder.coverImage)?.cancel()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = artists.size

    fun updateArtists(newArtists: List<Artist>) {
        artists = reorderUnknownFirst(newArtists)
        notifyDataSetChanged()
    }

    private fun reorderUnknownFirst(src: List<Artist>): List<Artist> {
        val (unknowns, others) = src.partition { it.name.equals("Unknown", true) || it.name.equals("<unknown>", true) }
        return unknowns + others
    }
    // В ArtistAdapter.kt
    private fun getArtistTimestamp(artistName: String): Long {
        val prefs = context.getSharedPreferences("custom_artists", Context.MODE_PRIVATE)
        return prefs.getLong("artist_${artistName}_timestamp", 0)
    }

    private fun getArtistCacheKey(artistName: String, suffix: String = ""): String {
        val timestamp = getArtistTimestamp(artistName)
        return "artist_${artistName}_${timestamp}_$suffix"
    }
}