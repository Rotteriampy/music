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

class GenreAdapter(
    private val context: Context,
    private var genres: List<Genre>,
) : RecyclerView.Adapter<GenreAdapter.GenreViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return genres[position].name.hashCode().toLong()
    }

    class GenreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val genreName: TextView = itemView.findViewById(R.id.playlistName)
        val tracksInfo: TextView = itemView.findViewById(R.id.tracksInfo)
        val coverImage: ImageView = itemView.findViewById(R.id.playlistCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false)
        return GenreViewHolder(view)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val genre = genres[position]

        // Загружаем кастомные данные
        val prefs = context.getSharedPreferences("custom_genres", Context.MODE_PRIVATE)
        val customName = prefs.getString("genre_${genre.name}_name", null)
        val customCoverUri = prefs.getString("genre_${genre.name}_cover", null)

        // Показываем название (кастомное или оригинальное)
        holder.genreName.text = customName ?: genre.name

        // Загружаем обложку
        if (customCoverUri != null) {
            holder.coverImage.setImageURI(Uri.parse(customCoverUri))
        } else {
            // Загружаем обложку из первого трека
            val firstTrack = genre.tracks.firstOrNull()
            if (firstTrack?.path != null) {
                loadCover(firstTrack.path, holder.coverImage)
            } else {
                holder.coverImage.setImageResource(R.drawable.ic_album_placeholder)
            }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, GenreActivity::class.java).apply {
                putExtra("GENRE_NAME", genre.name)
            }
            context.startActivity(intent)
        }

        val trackCount = genre.tracks.size
        val totalDurationMs = genre.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
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

    override fun getItemCount(): Int = genres.size

    fun updateGenres(newGenres: List<Genre>) {
        genres = newGenres
        notifyDataSetChanged()
    }
}