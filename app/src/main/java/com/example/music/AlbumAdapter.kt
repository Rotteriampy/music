package com.example.music

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlbumAdapter(
    private var albums: List<Album>,
    private val context: Context
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

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

        // Загружаем обложку
        if (customCoverUri != null) {
            holder.albumCover.setImageURI(Uri.parse(customCoverUri))
        } else {
            // Загружаем обложку из первого трека
            val firstTrack = album.tracks.firstOrNull()
            if (firstTrack?.path != null) {
                loadAlbumCover(firstTrack.path, holder.albumCover)
            } else {
                holder.albumCover.setImageResource(R.drawable.ic_album_placeholder)
            }
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

    private fun loadAlbumCover(trackPath: String, imageView: ImageView) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(trackPath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                imageView.setImageBitmap(bitmap)
            } else {
                imageView.setImageResource(R.drawable.ic_album_placeholder)
            }
            retriever.release()
        } catch (e: Exception) {
            imageView.setImageResource(R.drawable.ic_album_placeholder)
        }
    }

    override fun getItemCount(): Int = albums.size

    fun updateAlbums(newAlbums: List<Album>) {
        albums = newAlbums
        notifyDataSetChanged()
    }
}