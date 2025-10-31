package com.example.music

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
                context.startActivity(intent)
            }
        } else {
            holder.itemView.setOnClickListener(null)
        }

        // Load cover
        if (playlist.id == Playlist.FAVORITES_ID) {
            holder.playlistCover.setImageResource(R.drawable.ic_star_filled_yellow)
        } else {
            playlist.coverUri?.let { uriString ->
                val key = uriString
                val cachedBitmap = bitmapCache[key]
                if (cachedBitmap != null) {
                    holder.playlistCover.setImageBitmap(roundToView(holder.playlistCover, cachedBitmap, 12f))
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val uri = Uri.parse(uriString)
                            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            withContext(Dispatchers.Main) {
                                if (bitmap != null) {
                                    holder.playlistCover.setImageBitmap(roundToView(holder.playlistCover, bitmap, 12f))
                                    bitmapCache[key] = bitmap
                                } else {
                                    val ph = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_album_placeholder)
                                    if (ph != null) {
                                        if (holder.playlistCover.width == 0 || holder.playlistCover.height == 0) {
                                            holder.playlistCover.post {
                                                holder.playlistCover.setImageBitmap(
                                                    roundToView(holder.playlistCover, drawableToSizedBitmapForView(holder.playlistCover, ph), 12f)
                                                )
                                            }
                                        } else {
                                            holder.playlistCover.setImageBitmap(roundToView(holder.playlistCover, drawableToSizedBitmapForView(holder.playlistCover, ph), 12f))
                                        }
                                    } else holder.playlistCover.setImageResource(R.drawable.ic_album_placeholder)
                                }
                            }
                            inputStream?.close()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                val ph = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_album_placeholder)
                                if (ph != null) {
                                    if (holder.playlistCover.width == 0 || holder.playlistCover.height == 0) {
                                        holder.playlistCover.post {
                                            holder.playlistCover.setImageBitmap(
                                                roundToView(holder.playlistCover, drawableToSizedBitmapForView(holder.playlistCover, ph), 12f)
                                            )
                                        }
                                    } else {
                                        holder.playlistCover.setImageBitmap(roundToView(holder.playlistCover, drawableToSizedBitmapForView(holder.playlistCover, ph), 12f))
                                    }
                                } else holder.playlistCover.setImageResource(R.drawable.ic_album_placeholder)
                            }
                        }
                    }
                }
            } ?: run {
                val ph = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_album_placeholder)
                if (ph != null) {
                    if (holder.playlistCover.width == 0 || holder.playlistCover.height == 0) {
                        holder.playlistCover.post {
                            holder.playlistCover.setImageBitmap(
                                roundToView(holder.playlistCover, drawableToSizedBitmapForView(holder.playlistCover, ph), 12f)
                            )
                        }
                    } else {
                        holder.playlistCover.setImageBitmap(roundToView(holder.playlistCover, drawableToSizedBitmapForView(holder.playlistCover, ph), 12f))
                    }
                } else holder.playlistCover.setImageResource(R.drawable.ic_album_placeholder)
            }
        }

        // Tracks info
        val trackCount = playlist.tracks.size
        val totalDurationMs = playlist.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
        val totalMinutes = totalDurationMs / 1000 / 60
        val totalSeconds = (totalDurationMs / 1000) % 60
        holder.tracksInfo.text = "${trackCount} треков • ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}"
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
}