package com.example.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class DialogTrackAdapter(
    private val tracks: List<Track>
) : RecyclerView.Adapter<DialogTrackAdapter.DialogTrackViewHolder>() {

    companion object {
        private val bitmapCache: MutableMap<String, Bitmap?> = mutableMapOf()
    }

    private val selectedTracks = mutableSetOf<Track>()

    class DialogTrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val trackAvatar: ImageView = itemView.findViewById(R.id.dialogTrackAvatar)
        val trackName: TextView = itemView.findViewById(R.id.dialogTrackName)
        val trackArtist: TextView = itemView.findViewById(R.id.dialogTrackArtist)
        val checkBox: CheckBox = itemView.findViewById(R.id.dialogTrackCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DialogTrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_track_item, parent, false)
        return DialogTrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: DialogTrackViewHolder, position: Int) {
        val track = tracks[position]
        val context = holder.itemView.context

        holder.trackName.text = track.name
        holder.trackArtist.text = track.artist ?: "Unknown Artist"
        holder.checkBox.isChecked = selectedTracks.contains(track)

        holder.itemView.setOnClickListener {
            if (selectedTracks.contains(track)) {
                selectedTracks.remove(track)
                holder.checkBox.isChecked = false
            } else {
                selectedTracks.add(track)
                holder.checkBox.isChecked = true
            }
        }

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedTracks.add(track)
            } else {
                selectedTracks.remove(track)
            }
        }

        val key = track.path ?: track.albumId.toString()
        val cachedBitmap = bitmapCache[key]
        if (cachedBitmap != null) {
            holder.trackAvatar.setImageBitmap(cachedBitmap)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = loadBitmap(context, track, key)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        holder.trackAvatar.setImageBitmap(bitmap)
                    } else {
                        holder.trackAvatar.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                    bitmapCache[key] = bitmap
                }
            }
        }
    }

    private fun loadBitmap(context: Context, track: Track, key: String): Bitmap? {
        var bitmap: Bitmap? = null

        if (track.path != null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(track.path)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
                retriever.release()
                if (bitmap != null) return bitmap
            } catch (e: Exception) {
                // Continue to fallback
            }
        }

        track.albumId?.let { albumId ->
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                albumId
            )
            var inputStream: InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                bitmap = inputStream?.let { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                // Fallback
            } finally {
                inputStream?.close()
            }
        }

        return bitmap
    }

    override fun getItemCount(): Int = tracks.size

    fun getSelectedTracks(): List<Track> = selectedTracks.toList()
}