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

class ArtistAdapter(
    private var artists: List<Artist>,
    private val context: Context
) : RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder>() {

    companion object {
        private val bitmapCache: MutableMap<Long, android.graphics.Bitmap?> = mutableMapOf()
    }

    class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val artistCover: ImageView = itemView.findViewById(R.id.playlistCover)
        val artistName: TextView = itemView.findViewById(R.id.playlistName)
        val tracksInfo: TextView = itemView.findViewById(R.id.tracksInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artists[position]
        holder.artistName.text = artist.name

        holder.itemView.setOnClickListener {
            val intent = Intent(context, ArtistActivity::class.java).apply {
                putExtra("ARTIST_NAME", artist.name)
            }
            context.startActivity(intent)
        }

        // Загрузка обложки первого трека исполнителя
        val firstTrack = artist.tracks.firstOrNull()
        firstTrack?.albumId?.let { albumId ->
            val cachedBitmap = bitmapCache[albumId]
            if (cachedBitmap != null) {
                holder.artistCover.setImageBitmap(cachedBitmap)
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
                            holder.artistCover.setImageBitmap(bitmap)
                            bitmapCache[albumId] = bitmap
                        }
                        inputStream?.close()
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            holder.artistCover.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                }
            }
        } ?: holder.artistCover.setImageResource(android.R.drawable.ic_menu_gallery)

        // Информация о треках
        val trackCount = artist.tracks.size
        val totalDurationMs = artist.tracks.mapNotNull { it.duration }.filter { it > 0 }.sum()
        val totalMinutes = totalDurationMs / 1000 / 60
        val totalSeconds = (totalDurationMs / 1000) % 60
        holder.tracksInfo.text = "${trackCount} треков • ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}"
    }

    override fun getItemCount(): Int = artists.size

    fun updateArtists(newArtists: List<Artist>) {
        artists = newArtists
        notifyDataSetChanged()
    }
}