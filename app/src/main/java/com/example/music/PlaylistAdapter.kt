package com.example.music

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
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
import android.util.Log
import java.util.Collections

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
                    holder.playlistCover.setImageBitmap(cachedBitmap)
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val uri = Uri.parse(uriString)
                            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            withContext(Dispatchers.Main) {
                                holder.playlistCover.setImageBitmap(bitmap)
                                bitmapCache[key] = bitmap
                            }
                            inputStream?.close()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                holder.playlistCover.setImageResource(R.drawable.ic_album_placeholder)
                            }
                        }
                    }
                }
            } ?: holder.playlistCover.setImageResource(R.drawable.ic_album_placeholder)
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
}