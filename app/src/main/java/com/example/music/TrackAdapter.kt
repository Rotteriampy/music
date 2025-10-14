package com.example.music

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Collections
import androidx.core.content.ContextCompat

class TrackAdapter(
    private var tracks: MutableList<Track>,
    private val isFromPlaylist: Boolean = false
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    companion object {
        private val bitmapCache: MutableMap<String, Bitmap?> = mutableMapOf()
    }

    var isReorderMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var onTrackMoved: ((Int, Int) -> Unit)? = null
    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
    var onTrackDeleted: ((Int) -> Unit)? = null

    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val trackName: TextView = itemView.findViewById(R.id.trackName)
        val trackArtist: TextView = itemView.findViewById(R.id.trackArtist)
        val trackAvatar: ImageView = itemView.findViewById(R.id.trackAvatar)
        val dragHandle: ImageView? = itemView.findViewById(R.id.dragHandle)
        val deleteButton: ImageButton? = itemView.findViewById(R.id.deleteTrackButton)
        val moreButton: ImageButton = itemView.findViewById(R.id.moreTrackButton)

        fun bind(track: Track, position: Int, adapter: TrackAdapter, context: Context) {
            trackName.text = track.name
            trackArtist.text = track.artist ?: "Unknown Artist"

            dragHandle?.visibility = if (adapter.isReorderMode) View.VISIBLE else View.GONE
            deleteButton?.visibility = if (adapter.isReorderMode && adapter.isFromPlaylist) View.VISIBLE else View.GONE

            loadTrackAvatar(context, track, trackAvatar)

            deleteButton?.setOnClickListener {
                adapter.onTrackDeleted?.invoke(position)
            }

            dragHandle?.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && adapter.isReorderMode) {
                    adapter.onStartDrag?.invoke(this@TrackViewHolder)
                    true
                } else {
                    false
                }
            }

            moreButton.setOnClickListener { view ->
                adapter.showTrackMenu(view, track, context, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.track_item, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.bind(track, position, this, holder.itemView.context)
    }

    override fun getItemCount(): Int = tracks.size

    private fun loadTrackAvatar(context: Context, track: Track, imageView: ImageView) {
        CoroutineScope(Dispatchers.IO).launch {
            var bitmap: Bitmap? = null

            val idKey = track.id ?: track.path ?: return@launch

            // Проверяем кэш
            bitmapCache[idKey]?.let {
                bitmap = it
            } ?: run {
                // Пробуем метаданные
                try {
                    val retriever = MediaMetadataRetriever()
                    track.path?.let { retriever.setDataSource(it) }
                    val data = retriever.embeddedPicture
                    if (data != null) {
                        bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                        bitmapCache[idKey] = bitmap
                    }
                    retriever.release()
                } catch (e: Exception) {
                    // пропускаем
                }
            }

            // Fallback — MediaStore (обложка альбома)
            if (bitmap == null && track.albumId != null) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    track.albumId
                )
                var inputStream: InputStream? = null
                try {
                    inputStream = context.contentResolver.openInputStream(uri)
                    bitmap = inputStream?.let { BitmapFactory.decodeStream(it) }
                    if (bitmap != null) bitmapCache[idKey] = bitmap
                } catch (e: Exception) {
                    // пропускаем
                } finally {
                    inputStream?.close()
                }
            }

            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }
    }

    fun showTrackMenu(view: View, track: Track, context: Context, position: Int) {
        val popupMenu = PopupMenu(context, view)
        popupMenu.menuInflater.inflate(R.menu.track_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    val intent = Intent(context, EditTrackActivity::class.java)
                    track.path?.let { intent.putExtra("TRACK_PATH", it) }
                    context.startActivity(intent)
                    true
                }

                R.id.action_delete -> {
                    val builder = AlertDialog.Builder(context)
                    builder.setTitle("Удалить трек?")
                    builder.setMessage("Это удалит файл с устройства. Действие необратимо.")
                    builder.setPositiveButton("Удалить") { _, _ ->
                        deleteTrackFile(context, track.path, position)
                    }
                    builder.setNegativeButton("Отмена", null)
                    builder.show()
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    private fun deleteTrackFile(context: Context, path: String?, position: Int) {
        if (path == null) {
            Toast.makeText(context, "Некорректный путь к файлу", Toast.LENGTH_SHORT).show()
            return
        }

        val contentResolver = context.contentResolver
        val rowsDeleted = contentResolver.delete(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Audio.Media.DATA} = ?",
            arrayOf(path)
        )

        if (rowsDeleted > 0) {
            Toast.makeText(context, "Трек удалён", Toast.LENGTH_SHORT).show()
            removeItem(position)
            if (isFromPlaylist) {
                // пример: PlaylistManager.removeTrackFromPlaylist(context, currentPlaylistId, track.id)
            }
        } else {
            Toast.makeText(context, "Ошибка удаления", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateTracks(newTracks: List<Track>) {
        tracks.clear()
        tracks.addAll(newTracks)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tracks, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tracks, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onTrackMoved?.invoke(fromPosition, toPosition)
    }

    fun removeItem(position: Int) {
        if (position in tracks.indices) {
            tracks.removeAt(position)
            notifyItemRemoved(position)
            onTrackDeleted?.invoke(position)
        }
    }

    fun getTracks(): List<Track> = tracks.toList()
}
