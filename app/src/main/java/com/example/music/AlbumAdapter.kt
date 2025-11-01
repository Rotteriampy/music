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
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Shader
import java.io.InputStream

class AlbumAdapter(
    private var albums: List<Album>,
    private val context: Context
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    init {
        albums = reorderUnknownFirst(albums)
    }

    class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val albumCover: ImageView = itemView.findViewById(R.id.playlistCover)
        val albumName: TextView = itemView.findViewById(R.id.playlistName)
        val tracksInfo: TextView = itemView.findViewById(R.id.tracksInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]

        // Загружаем кастомные данные
        val prefs = context.getSharedPreferences("custom_albums", Context.MODE_PRIVATE)
        val customName = prefs.getString("album_${album.name}_name", null)
        val customCoverUri = prefs.getString("album_${album.name}_cover", null)

        // Показываем название (кастомное или оригинальное)
        holder.albumName.text = customName ?: album.name
        // По умолчанию цвета темы
        holder.albumName.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextPrimary))
        holder.tracksInfo.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextSecondary))
        var avatarPresent = false

        // Загружаем обложку
        if (customName?.equals("<unknown>", ignoreCase = true) == true) {
            holder.albumCover.setImageResource(R.drawable.ic_album_placeholder)
        } else if (customCoverUri != null) {
            // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой
            val cacheKey = getAlbumCacheKey(album.name, "custom_uri_${customCoverUri}")
            try {
                val cached = DiskImageCache.getBitmap(cacheKey)
                if (cached != null) {
                    holder.albumCover.setImageBitmap(roundToView(holder.albumCover, cached, 12f))
                    avatarPresent = true
                } else {
                    val uri = Uri.parse(customCoverUri)
                    val input: InputStream? = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        DiskImageCache.putBitmap(cacheKey, bmp)
                        holder.albumCover.setImageBitmap(roundToView(holder.albumCover, bmp, 12f))
                        avatarPresent = true
                    }
                    input?.close()
                }
            } catch (_: Exception) {
                holder.albumCover.setImageResource(R.drawable.ic_album_placeholder)
            }
        } else {
            // Сперва используем кэш пути трека с обложкой (если уже определяли ранее)
            val cachedTrackPath = prefs.getString("album_${album.name}_cover_track", null)
            var set = false
            if (cachedTrackPath != null) {
                // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой
                val cacheKey = getAlbumCacheKey(album.name, "cover_track_${cachedTrackPath}")
                set = loadAlbumCover(cachedTrackPath, holder.albumCover, cacheKey)
                if (set) avatarPresent = true
            }
            // Если не удалось — ищем первый трек с обложкой и кэшируем его путь
            if (!set) {
                for (t in album.tracks) {
                    val p = t.path ?: continue
                    // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой
                    val cacheKey = getAlbumCacheKey(album.name, "cover_track_${p}")
                    if (loadAlbumCover(p, holder.albumCover, cacheKey)) {
                        prefs.edit().putString("album_${album.name}_cover_track", p).apply()
                        set = true
                        avatarPresent = true
                        break
                    }
                }
            }
            if (!set) {
                // Фолбэк: если имя не Unknown/<unknown> — буква, иначе плейсхолдер
                val name = (customName ?: album.name).trim()
                if (name.equals("Unknown", ignoreCase = true) || name.equals("<unknown>", ignoreCase = true)) {
                    holder.albumCover.setImageResource(R.drawable.ic_album_placeholder)
                } else {
                    // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой для буквенной обложки
                    val cacheKey = getAlbumCacheKey(album.name, "letter_cover")
                    var cached = DiskImageCache.getBitmap(cacheKey)
                    if (cached == null) {
                        cached = generateLetterCover(name)
                        DiskImageCache.putBitmap(cacheKey, cached)
                    }
                    holder.albumCover.setImageBitmap(roundToView(holder.albumCover, cached, 12f))
                    avatarPresent = true
                }
            }
        }

        if (avatarPresent) {
            holder.albumName.setTextColor(android.graphics.Color.WHITE)
            holder.tracksInfo.setTextColor(android.graphics.Color.WHITE)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, AlbumActivity::class.java).apply {
                putExtra("ALBUM_NAME", album.name)
            }
            context.startActivity(intent)
        }

        // Информация о треках
        val trackCount = album.tracks.size
        val totalDurationMs = album.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
        val totalMinutes = totalDurationMs / 1000 / 60
        val totalSeconds = (totalDurationMs / 1000) % 60
        holder.tracksInfo.text = "${trackCount} треков • ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}"
    }

    private fun loadAlbumCover(trackPath: String, imageView: ImageView, cacheKey: String): Boolean {
        // Добавляем суффикс для скругленной версии
        val roundedCacheKey = "${cacheKey}_rounded"

        DiskImageCache.getBitmap(roundedCacheKey)?.let { cached ->
            imageView.setImageBitmap(cached)
            return true
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(trackPath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                if (bitmap != null) {
                    val roundedBitmap = roundToView(imageView, bitmap, 12f)
                    DiskImageCache.putBitmap(roundedCacheKey, roundedBitmap)
                    imageView.setImageBitmap(roundedBitmap)
                    retriever.release()
                    return true
                }
            }
            retriever.release()
        } catch (e: Exception) {
            // ignore
        }
        return false
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

    /**
     * Обрезает и скругляет Bitmap до фиксированного квадратного размера (128dp),
     * используя логику CenterCrop. Это устраняет дергания при прокрутке.
     */
    private fun roundToView(view: ImageView, src: Bitmap, radiusDp: Float): Bitmap {
        val radiusPx = (radiusDp * view.resources.displayMetrics.density)

        // ИСПРАВЛЕНИЕ: Используем фиксированный квадратный размер (128dp)
        val sizeDp = 128f
        val sizePx = (sizeDp * view.resources.displayMetrics.density).toInt()
        val outW = sizePx
        val outH = sizePx

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isDither = true }
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        // Матрица для масштабирования и центрирования (CenterCrop-логика)
        val scale: Float
        val dx: Float
        val dy: Float

        // Масштабируем так, чтобы заполнить квадрат
        scale = maxOf(outW.toFloat() / src.width, outH.toFloat() / src.height)

        // Центрируем
        dx = (outW - src.width * scale) * 0.5f
        dy = (outH - src.height * scale) * 0.5f

        val matrix = Matrix().apply { setScale(scale, scale); postTranslate(dx, dy) }
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        val rect = RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        return out
    }



    override fun getItemCount(): Int = albums.size

    fun updateAlbums(newAlbums: List<Album>) {
        albums = reorderUnknownFirst(newAlbums)
        notifyDataSetChanged()
    }

    private fun reorderUnknownFirst(src: List<Album>): List<Album> {
        val (unknowns, others) = src.partition { it.name.equals("Unknown", true) || it.name.equals("<unknown>", true) }
        return unknowns + others
    }
    private fun getAlbumTimestamp(albumName: String): Long {
        val prefs = context.getSharedPreferences("custom_albums", Context.MODE_PRIVATE)
        return prefs.getLong("album_${albumName}_timestamp", 0)
    }

    private fun getAlbumCacheKey(albumName: String, suffix: String = ""): String {
        val timestamp = getAlbumTimestamp(albumName)
        return "album_${albumName}_${timestamp}_$suffix"
    }
}