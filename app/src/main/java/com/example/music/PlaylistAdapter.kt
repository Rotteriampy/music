package com.arotter.music

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

class PlaylistAdapter(
    private var playlists: MutableList<Playlist>,
    private val context: Context
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    companion object {
        private val bitmapCache: MutableMap<String, android.graphics.Bitmap?> = mutableMapOf()
    }

    var isReorderMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
    var onPlaylistMoved: ((Int, Int) -> Unit)? = null

    class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playlistName: TextView = itemView.findViewById(R.id.playlistName)
        val playlistCover: ImageView = itemView.findViewById(R.id.playlistCover)
        val tracksInfo: TextView = itemView.findViewById(R.id.tracksInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.playlistName.text = playlist.name
        // Default text colors from theme
        holder.playlistName.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextPrimary))
        holder.tracksInfo.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextSecondary))

        // Долгое нажатие для перетаскивания в режиме редактирования
        if (isReorderMode && playlist.id != Playlist.FAVORITES_ID) {
            holder.itemView.setOnLongClickListener {
                onStartDrag?.invoke(holder)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
        }

        // Клик работает только когда не в режиме переупорядочивания
        if (!isReorderMode) {
            holder.itemView.setOnClickListener {
                val intent = Intent(context, PlaylistActivity::class.java).apply {
                    putExtra("PLAYLIST_ID", playlist.id)
                }
                // Замените startActivity на launch с launcher'ом
                if (context is MainActivity) {
                    context.playlistUpdateLauncher.launch(intent)
                } else {
                    context.startActivity(intent)
                }
            }
        } else {
            holder.itemView.setOnClickListener(null)
        }

        // Load cover
        if (playlist.id == Playlist.FAVORITES_ID) {
            holder.playlistCover.setImageResource(R.drawable.ic_star_filled_yellow)
            // Favorites considered as having an avatar/icon -> force white text
            holder.playlistName.setTextColor(android.graphics.Color.WHITE)
            holder.tracksInfo.setTextColor(android.graphics.Color.WHITE)
        } else {
            playlist.coverUri?.let { uriString ->
                // ИСПРАВЛЕНИЕ: Используем ключ с временной меткой
                val cacheKey = getPlaylistCacheKey(playlist.id, "custom_uri_${uriString}")
                val cachedBitmap = DiskImageCache.getBitmap(cacheKey)
                if (cachedBitmap != null) {
                    holder.playlistCover.setImageBitmap(roundToView(holder.playlistCover, cachedBitmap, 12f))
                    // Valid avatar present -> white text
                    holder.playlistName.setTextColor(android.graphics.Color.WHITE)
                    holder.tracksInfo.setTextColor(android.graphics.Color.WHITE)
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val uri = Uri.parse(uriString)
                            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            withContext(Dispatchers.Main) {
                                if (bitmap != null) {
                                    val rounded = roundToView(holder.playlistCover, bitmap, 12f)
                                    holder.playlistCover.setImageBitmap(rounded)
                                    DiskImageCache.putBitmap(cacheKey, rounded)
                                    holder.playlistName.setTextColor(android.graphics.Color.WHITE)
                                    holder.tracksInfo.setTextColor(android.graphics.Color.WHITE)
                                } else {
                                    setPlaceholderCover(holder.playlistCover)
                                    holder.playlistName.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextPrimary))
                                    holder.tracksInfo.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextSecondary))
                                }
                            }
                            inputStream?.close()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                setPlaceholderCover(holder.playlistCover)
                                holder.playlistName.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextPrimary))
                                holder.tracksInfo.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextSecondary))
                            }
                        }
                    }
                }
            } ?: run {
                setPlaceholderCover(holder.playlistCover)
                holder.playlistName.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextPrimary))
                holder.tracksInfo.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorTextSecondary))
            }
        }

        // Tracks info
        val trackCount = playlist.tracks.size
        val totalDurationMs = playlist.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
        val totalMinutes = totalDurationMs / 1000 / 60
        val totalSeconds = (totalDurationMs / 1000) % 60
        holder.tracksInfo.text = "${trackCount} треков • ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}"
    }

    private fun setPlaceholderCover(imageView: ImageView) {
        val ph = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_album_placeholder)
        if (ph != null) {
            if (imageView.width == 0 || imageView.height == 0) {
                imageView.post {
                    imageView.setImageBitmap(
                        roundToView(imageView, drawableToSizedBitmapForView(imageView, ph), 12f)
                    )
                }
            } else {
                imageView.setImageBitmap(roundToView(imageView, drawableToSizedBitmapForView(imageView, ph), 12f))
            }
        } else imageView.setImageResource(R.drawable.ic_album_placeholder)
    }

    override fun getItemCount(): Int = playlists.size

    fun updatePlaylists(newPlaylists: List<Playlist>) {
        val favorites = newPlaylists.find { it.id == Playlist.FAVORITES_ID }
        val others = newPlaylists.filter { it.id != Playlist.FAVORITES_ID }
        playlists.clear()
        if (favorites != null) {
            playlists.add(favorites)
        }
        playlists.addAll(others)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(playlists, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(playlists, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onPlaylistMoved?.invoke(fromPosition, toPosition)
    }

    fun getPlaylistAt(position: Int): Playlist {
        return playlists[position]
    }

    private fun roundToView(view: ImageView, src: Bitmap, radiusDp: Float): Bitmap {
        val radiusPx = (radiusDp * view.resources.displayMetrics.density)
        val outW = if (view.width > 0) view.width else (view.layoutParams?.width ?: 0).let { if (it > 0) it else view.resources.displayMetrics.widthPixels / 2 }
        val outH = if (view.height > 0) view.height else (view.layoutParams?.height ?: 0).let { if (it > 0) it else outW }
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

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    private fun drawableToSizedBitmapForView(view: ImageView, drawable: android.graphics.drawable.Drawable): Bitmap {
        val targetW = (if (view.width > 0) view.width else (view.layoutParams?.width ?: 0)).let { sz -> if (sz > 0) sz else (view.resources.displayMetrics.widthPixels / 2) }
        val targetH = (if (view.height > 0) view.height else (view.layoutParams?.height ?: 0)).let { sz -> if (sz > 0) sz else targetW }
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    // ДОБАВЛЕНО: Методы для работы с временными метками и кэшем
    private fun getPlaylistTimestamp(playlistId: String): Long {
        val prefs = context.getSharedPreferences("custom_playlists", Context.MODE_PRIVATE)
        return prefs.getLong("playlist_${playlistId}_timestamp", 0)
    }

    private fun getPlaylistCacheKey(playlistId: String, suffix: String = ""): String {
        val timestamp = getPlaylistTimestamp(playlistId)
        return "playlist_${playlistId}_${timestamp}_$suffix"
    }
}