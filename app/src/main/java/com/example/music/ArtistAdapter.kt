package com.example.music

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.net.Uri

class ArtistAdapter(
    private var artists: List<Artist>,
    private val context: Context
) : RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder>() {

    class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val artistName: TextView = itemView.findViewById(R.id.playlistName)
        val tracksInfo: TextView = itemView.findViewById(R.id.tracksInfo)
        val coverImage: ImageView = itemView.findViewById(R.id.playlistCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artists[position]

        // Загружаем кастомные данные
        val prefs = context.getSharedPreferences("custom_artists", Context.MODE_PRIVATE)
        val customName = prefs.getString("artist_${artist.name}_name", null)
        val customCoverUri = prefs.getString("artist_${artist.name}_cover", null)

        // Показываем название (кастомное или оригинальное)
        holder.artistName.text = customName ?: artist.name

        // Загружаем обложку
        if (customCoverUri != null) {
            holder.coverImage.setImageURI(Uri.parse(customCoverUri))
        } else {
            // Загружаем обложку из первого трека
            val firstTrack = artist.tracks.firstOrNull()
            if (firstTrack?.path != null) {
                loadCover(firstTrack.path, holder.coverImage)
            } else {
                holder.coverImage.setImageResource(R.drawable.ic_album_placeholder)
            }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, ArtistActivity::class.java).apply {
                putExtra("ARTIST_NAME", artist.name)
            }
            context.startActivity(intent)
        }

        val trackCount = artist.tracks.size
        val totalDurationMs = artist.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
        val totalMinutes = totalDurationMs / 1000 / 60
        val totalSeconds = (totalDurationMs / 1000) % 60
        holder.tracksInfo.text = "${trackCount} треков • ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}"
    }

    private fun loadCover(trackPath: String, imageView: ImageView) {
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

    override fun getItemCount(): Int = artists.size

    fun updateArtists(newArtists: List<Artist>) {
        artists = newArtists
        notifyDataSetChanged()
    }
}