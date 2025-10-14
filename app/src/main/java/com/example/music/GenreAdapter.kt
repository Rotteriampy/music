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

class GenreAdapter(
    private val context: Context,
    private var genres: List<Genre>,
    private val onGenreClick: (Genre) -> Unit
) : RecyclerView.Adapter<GenreAdapter.GenreViewHolder>() {
    init {
        setHasStableIds(true)
    }
    override fun getItemId(position: Int): Long {
        return genres[position].name.hashCode().toLong()
    }

    companion object {
        private val bitmapCache: MutableMap<Long, android.graphics.Bitmap?> = mutableMapOf()
    }

    class GenreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val genreCover: ImageView = itemView.findViewById(R.id.playlistCover)
        val genreName: TextView = itemView.findViewById(R.id.playlistName)
        val tracksInfo: TextView = itemView.findViewById(R.id.tracksInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false)
        return GenreViewHolder(view)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val genre = genres[position]
        holder.genreName.text = genre.name

        holder.itemView.setOnClickListener {
            val intent = Intent(context, GenreActivity::class.java).apply {
                putExtra("GENRE_NAME", genre.name)
            }
            context.startActivity(intent)
        }

        // Загрузка обложки первого трека жанра
        val firstTrack = genre.tracks.firstOrNull()
        firstTrack?.albumId?.let { albumId ->
            val cachedBitmap = bitmapCache[albumId]
            if (cachedBitmap != null) {
                holder.genreCover.setImageBitmap(cachedBitmap)
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
                            holder.genreCover.setImageBitmap(bitmap)
                            bitmapCache[albumId] = bitmap
                        }
                        inputStream?.close()
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            holder.genreCover.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                }
            }
        } ?: holder.genreCover.setImageResource(android.R.drawable.ic_menu_gallery)

        // Информация о треках
        val trackCount = genre.tracks.size
        val totalDurationMs = genre.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
        val totalMinutes = totalDurationMs / 1000 / 60
        val totalSeconds = (totalDurationMs / 1000) % 60
        holder.tracksInfo.text = "${trackCount} треков • ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}"
    }

    override fun getItemCount(): Int = genres.size

    fun updateGenres(newGenres: List<Genre>) {
        genres = newGenres
        notifyDataSetChanged()
    }
}