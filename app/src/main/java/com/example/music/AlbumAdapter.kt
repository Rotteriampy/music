package com.example.music

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.content.ContentUris
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class AlbumAdapter(
    private var albums: List<Album>,
    private val context: Context
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    companion object {
        private val bitmapCache: MutableMap<Long, android.graphics.Bitmap?> = mutableMapOf()
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
        holder.albumName.text = album.name

        holder.itemView.setOnClickListener {
            val intent = Intent(context, AlbumActivity::class.java).apply {
                putExtra("ALBUM_NAME", album.name)
            }
            context.startActivity(intent)
        }

        // Загрузка обложки альбома
        val firstTrack = album.tracks.firstOrNull()
        firstTrack?.albumId?.let { albumId ->
            val cachedBitmap = bitmapCache[albumId]
            if (cachedBitmap != null) {
                holder.albumCover.setImageBitmap(cachedBitmap)
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            albumId
                        )
                        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        withContext(Dispatchers.Main) {
                            holder.albumCover.setImageBitmap(bitmap)
                            bitmapCache[albumId] = bitmap
                        }
                        inputStream?.close()
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            holder.albumCover.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                }
            }
        } ?: holder.albumCover.setImageResource(android.R.drawable.ic_menu_gallery)

        // Информация о треках
        val trackCount = album.tracks.size
        val totalDurationMs = album.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
        val totalMinutes = totalDurationMs / 1000 / 60
        val totalSeconds = (totalDurationMs / 1000) % 60
        holder.tracksInfo.text = "${trackCount} треков • ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}"
    }

    override fun getItemCount(): Int = albums.size

    fun updateAlbums(newAlbums: List<Album>) {
        albums = newAlbums
        notifyDataSetChanged()
    }
}