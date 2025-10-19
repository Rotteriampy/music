package com.example.music

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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

    init {
        artists = reorderUnknownFirst(artists)
    }

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
        if (artist.name.equals("Unknown", ignoreCase = true) || artist.name.equals("<unknown>", ignoreCase = true)) {
            holder.coverImage.setImageResource(R.drawable.ic_album_placeholder)
        } else if (customCoverUri != null) {
            holder.coverImage.setImageURI(Uri.parse(customCoverUri))
        } else {
            val prefs = context.getSharedPreferences("custom_artists", Context.MODE_PRIVATE)
            val cachedTrackPath = prefs.getString("artist_${artist.name}_cover_track", null)
            var set = false
            if (cachedTrackPath != null) set = loadCover(cachedTrackPath, holder.coverImage)

            if (!set) {
                for (t in artist.tracks) {
                    val p = t.path ?: continue
                    if (loadCover(p, holder.coverImage)) {
                        prefs.edit().putString("artist_${artist.name}_cover_track", p).apply()
                        set = true
                        break
                    }
                }
            }
            if (!set) {
                val displayName = (customName ?: artist.name).trim()
                holder.coverImage.setImageBitmap(generateLetterCover(displayName))
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

    private fun loadCover(trackPath: String, imageView: ImageView): Boolean {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(trackPath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                imageView.setImageBitmap(bitmap)
                retriever.release()
                return true
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

    override fun getItemCount(): Int = artists.size

    fun updateArtists(newArtists: List<Artist>) {
        artists = reorderUnknownFirst(newArtists)
        notifyDataSetChanged()
    }

    private fun reorderUnknownFirst(src: List<Artist>): List<Artist> {
        val (unknowns, others) = src.partition { it.name.equals("Unknown", true) || it.name.equals("<unknown>", true) }
        return unknowns + others
    }
}