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
        genres = reorderUnknownFirst(genres)
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
        if (genre.name.equals("Unknown", ignoreCase = true)) {
            holder.genreName.text = "<unknown>"
            // Для Unknown используем стандартный плейсхолдер
            holder.coverImage.setImageResource(R.drawable.ic_album_placeholder)
        } else {
            holder.genreName.text = customName ?: genre.name
        }

        // Загружаем обложку (кроме Unknown)
        if (!genre.name.equals("Unknown", ignoreCase = true)) {
            if (customCoverUri != null) {
                holder.coverImage.setImageURI(Uri.parse(customCoverUri))
            } else {
                // Ищем первый трек с обложкой
                var set = false
                for (t in genre.tracks) {
                    val p = t.path ?: continue
                    if (loadCover(p, holder.coverImage)) { set = true; break }
                }
                if (!set) {
                    val displayName = (customName ?: genre.name).trim()
                    holder.coverImage.setImageBitmap(generateLetterCover(displayName))
                }
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

    override fun getItemCount(): Int = genres.size

    fun updateGenres(newGenres: List<Genre>) {
        genres = reorderUnknownFirst(newGenres)
        notifyDataSetChanged()
    }

    private fun reorderUnknownFirst(src: List<Genre>): List<Genre> {
        val (unknowns, others) = src.partition { it.name.equals("Unknown", true) || it.name.equals("<unknown>", true) }
        return unknowns + others
    }
}