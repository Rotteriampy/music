package com.arotter.music

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.MotionEvent

import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.File
import android.app.RecoverableSecurityException
import android.os.Build
import android.widget.LinearLayout

class TrackAdapter(
    private var tracks: MutableList<Track>,
    private val isFromPlaylist: Boolean = false
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    companion object {
        private val bitmapCache: MutableMap<String, Bitmap?> = mutableMapOf()
        const val DELETE_PERMISSION_REQUEST = 101
        var pendingDeletePath: String? = null
        var pendingDeletePosition: Int = -1
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
        val playCountBadge: TextView = itemView.findViewById(R.id.playCountBadge)
        val dragHandle: ImageView? = itemView.findViewById(R.id.dragHandle)
        val deleteButton: ImageButton? = itemView.findViewById(R.id.deleteTrackButton)
        val moreButton: ImageButton = itemView.findViewById(R.id.moreTrackButton)

        fun bind(track: Track, position: Int, adapter: TrackAdapter, context: Context) {
            trackName.text = track.name
            trackArtist.text = track.artist ?: "Unknown Artist"

            val playCount = ListeningStats.getPlayCount(track.path ?: "")
            if (playCount > 0) {
                playCountBadge.visibility = View.VISIBLE
                playCountBadge.text = playCount.toString()
            } else {
                playCountBadge.visibility = View.GONE
            }

            dragHandle?.visibility = if (adapter.isReorderMode) View.VISIBLE else View.GONE
            deleteButton?.visibility = if (adapter.isReorderMode && adapter.isFromPlaylist) View.VISIBLE else View.GONE

            // Enable drag start from handle (or long-press on item if no handle) in reorder mode
            if (adapter.isReorderMode) {
                dragHandle?.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        adapter.onStartDrag?.invoke(this)
                        return@setOnTouchListener true
                    }
                    false
                }
                if (dragHandle == null) {
                    itemView.setOnLongClickListener {
                        adapter.onStartDrag?.invoke(this)
                        true
                    }
                } else {
                    // Avoid interference when handle exists; no long-press on whole item
                    itemView.setOnLongClickListener(null)
                }
            } else {
                dragHandle?.setOnTouchListener(null)
                itemView.setOnLongClickListener(null)
            }

            loadTrackAvatar(context, track, trackAvatar)

            deleteButton?.setOnClickListener {
                adapter.onTrackDeleted?.invoke(position)
            }

            moreButton.setOnClickListener {
                adapter.showTrackOptions(track, position, context) // Передаем context
            }

            itemView.setOnClickListener {
                if (!adapter.isReorderMode) {
                    val allTracks = adapter.getTracks()
                    QueueManager.initializeQueueFromPosition(context, allTracks, position)

                    if (track.path != null && File(track.path).exists()) {
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            putExtra("TRACK_PATH", track.path)
                            putExtra("TRACK_NAME", track.name)
                            putExtra("TRACK_ARTIST", track.artist)
                        }
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            moreButton.setOnClickListener { view ->
                adapter.showTrackOptions(track, position, context)
            }

            val currentPath = QueueManager.getCurrentTrack()?.path
            if (currentPath != null && currentPath == track.path) {
                val accent = ThemeManager.getAccentColor(context)
                trackName.setTextColor(accent)
                trackArtist.setTextColor(accent)
            } else {
                trackName.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
                trackArtist.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
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

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            // Lightweight update: only (de)highlight current item, no cover reloads
            val track = tracks[position]
            val currentPath = QueueManager.getCurrentTrack()?.path
            if (currentPath != null && currentPath == track.path) {
                val accent = ThemeManager.getAccentColor(holder.itemView.context)
                holder.trackName.setTextColor(accent)
                holder.trackArtist.setTextColor(accent)
            } else {
                holder.trackName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorTextPrimary))
                holder.trackArtist.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorTextSecondary))
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = tracks.size

    private fun loadTrackAvatar(context: Context, track: Track, imageView: ImageView) {
        CoroutineScope(Dispatchers.IO).launch {
            var bitmap: Bitmap? = null
            val idKey = track.id ?: track.path ?: return@launch

            // RAM cache
            bitmapCache[idKey]?.let {
                bitmap = it
            } ?: run {
                // Disk cache first
                bitmap = DiskImageCache.getBitmap(idKey)
                if (bitmap == null) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        track.path?.let { retriever.setDataSource(it) }
                        val data = retriever.embeddedPicture
                        if (data != null) {
                            bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                            if (bitmap != null) {
                                bitmapCache[idKey] = bitmap
                                DiskImageCache.putBitmap(idKey, bitmap!!)
                            }
                        }
                        retriever.release()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            if (bitmap == null && track.albumId != null) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    track.albumId
                )
                var inputStream: InputStream? = null
                try {
                    inputStream = context.contentResolver.openInputStream(uri)
                    bitmap = inputStream?.let { BitmapFactory.decodeStream(it) }
                    if (bitmap != null) {
                        bitmapCache[idKey] = bitmap
                        DiskImageCache.putBitmap(idKey, bitmap!!)
                    }
                } catch (e: Exception) {
                    // ignore
                } finally {
                    inputStream?.close()
                }
            }

            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    setRounded(imageView, bitmap!!, 12f)
                } else {
                    imageView.setImageResource(R.drawable.ic_album_placeholder)
                }
            }
        }
    }

    private fun setRounded(view: ImageView, bitmap: Bitmap, radiusDp: Float) {
        val radiusPx = (radiusDp * view.resources.displayMetrics.density)
        val outW = if (view.width > 0) view.width else (view.layoutParams?.width ?: 0).let { if (it > 0) it else (48 * view.resources.displayMetrics.density).toInt() }
        val outH = if (view.height > 0) view.height else (view.layoutParams?.height ?: 0).let { if (it > 0) it else (48 * view.resources.displayMetrics.density).toInt() }
        val out = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val scale = maxOf(outW.toFloat() / bitmap.width, outH.toFloat() / bitmap.height)
        val dx = (outW - bitmap.width * scale) / 2f
        val dy = (outH - bitmap.height * scale) / 2f
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        val rect = android.graphics.RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        view.scaleType = ImageView.ScaleType.FIT_XY
        view.setImageBitmap(out)
    }

    private fun showTrackOptions(track: Track, position: Int, context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_track_menu, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        // Настройка заголовка
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvMenuTitle)
        tvTitle.text = track.name

        // Проверяем, находится ли трек в избранном
        val favoritesPlaylist = PlaylistManager.getPlaylists().find { it.id == Playlist.FAVORITES_ID }
        val isFavorite = favoritesPlaylist?.tracks?.any { it.path == track.path } == true

        // Иконки действий
        val ivFavorite = dialogView.findViewById<ImageView>(R.id.ivFavorite)
        val ivEdit = dialogView.findViewById<ImageView>(R.id.ivEdit)
        val ivInfo = dialogView.findViewById<ImageView>(R.id.ivInfo)
        val ivShare = dialogView.findViewById<ImageView>(R.id.ivShare)
        val ivDelete = dialogView.findViewById<ImageView>(R.id.ivDelete)

        // Обновляем иконку избранного
        updateFavoriteIcon(ivFavorite, isFavorite)

        // Обработчики кликов для иконок
        ivFavorite.setOnClickListener {
            toggleFavorite(track, context)
            dialog.dismiss()
        }

        ivEdit.setOnClickListener {
            showEditTagsDialog(track, position, context)
            dialog.dismiss()
        }

        ivInfo.setOnClickListener {
            showTrackInfo(track, context)
            dialog.dismiss()
        }

        ivShare.setOnClickListener {
            shareTrack(track, context)
            dialog.dismiss()
        }

        ivDelete.setOnClickListener {
            confirmDelete(track, position, context)
            dialog.dismiss()
        }

        dialogView.findViewById<LinearLayout>(R.id.tvAddToQueue).setOnClickListener {
            addToQueue(track, context)
            dialog.dismiss()
        }

        dialogView.findViewById<LinearLayout>(R.id.tvAddToPlaylist).setOnClickListener {
            showPlaylistDialog(track, context)
            dialog.dismiss()
        }

        dialogView.findViewById<LinearLayout>(R.id.tvSetAsRingtone).setOnClickListener {
            setAsRingtone(track, context)
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun updateFavoriteIcon(imageView: ImageView, isFavorite: Boolean) {
        if (isFavorite) {
            // Красная залитая иконка сердца
            imageView.setImageResource(R.drawable.ic_favorite_filled)
            imageView.setColorFilter(Color.RED)
        } else {
            // Белая незалитая иконка сердца
            imageView.setImageResource(R.drawable.ic_favorite_border)
            imageView.setColorFilter(Color.WHITE)
        }
    }

    private fun toggleFavorite(track: Track, context: Context) {
        val favoritesPlaylist = PlaylistManager.getPlaylists().find { it.id == Playlist.FAVORITES_ID }

        if (favoritesPlaylist == null) {
            Toast.makeText(context, "Плейлист Избранное не найден", Toast.LENGTH_SHORT).show()
            return
        }

        val isInFavorites = favoritesPlaylist.tracks.any { it.path == track.path }

        if (isInFavorites) {
            // Удаляем из избранного
            favoritesPlaylist.tracks.removeAll { it.path == track.path }
            PlaylistManager.savePlaylists(context)
            Toast.makeText(context, "Удалено из избранного", Toast.LENGTH_SHORT).show()
        } else {
            // Добавляем в избранное
            PlaylistManager.addTrackToPlaylist(context, Playlist.FAVORITES_ID, track)
            Toast.makeText(context, "Добавлено в избранное", Toast.LENGTH_SHORT).show()
        }

        context.sendBroadcast(Intent("com.arotter.music.FAVORITES_UPDATED"))
    }

    private fun addToQueue(track: Track, context: Context) {
        QueueManager.addToManualQueue(context, track)
        Toast.makeText(context, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
    }

    private fun showTrackInfo(track: Track, context: Context) {
        TrackInfoDialog(context, track).show()
    }

    private fun shareTrack(track: Track, context: Context) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "audio/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(track.path))
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            context.startActivity(Intent.createChooser(shareIntent, "Поделиться треком"))
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAsRingtone(track: Track, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.System.canWrite(context)) {
                Toast.makeText(context, "Необходимо разрешение на изменение системных настроек", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = android.net.Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
                return
            }
        }

        try {
            val file = File(track.path ?: return)
            val values = android.content.ContentValues()
            values.put(android.provider.MediaStore.MediaColumns.DATA, file.absolutePath)
            values.put(android.provider.MediaStore.MediaColumns.TITLE, track.name)
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/*")
            values.put(android.provider.MediaStore.Audio.Media.IS_RINGTONE, true)

            val uri = android.provider.MediaStore.Audio.Media.getContentUriForPath(file.absolutePath)
            context.contentResolver.delete(uri!!, android.provider.MediaStore.MediaColumns.DATA + "=\"" + file.absolutePath + "\"", null)
            val newUri = context.contentResolver.insert(uri, values)

            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                context,
                android.media.RingtoneManager.TYPE_RINGTONE,
                newUri
            )

            Toast.makeText(context, "Установлено как рингтон", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaylistDialog(track: Track, context: Context) {
        val playlists = PlaylistManager.getPlaylists().filter { it.id != Playlist.FAVORITES_ID }

        if (playlists.isEmpty()) {
            Toast.makeText(context, "Нет доступных плейлистов", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_select_styled, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.playlistContainer)

        val selectable = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }

        var dialog: AlertDialog? = null

        playlists.forEach { playlist ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16, context), dp(12, context), dp(16, context), dp(12, context))
                setBackgroundResource(selectable.resourceId)
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val icon = ImageView(context).apply {
                setImageResource(R.drawable.ic_playlist_add)
                setColorFilter(ContextCompat.getColor(context, R.color.colorIconTint))
                val lp = LinearLayout.LayoutParams(dp(24, context), dp(24, context))
                layoutParams = lp
            }

            val title = TextView(context).apply {
                text = playlist.name
                setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
                textSize = 16f
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = dp(12, context)
                layoutParams = lp
            }

            row.addView(icon)
            row.addView(title)

            row.setOnClickListener {
                val already = playlist.tracks.any { it.path == track.path }
                if (already) {
                    Toast.makeText(context, "Трек уже в плейлисте", Toast.LENGTH_SHORT).show()
                } else {
                    PlaylistManager.addTrackToPlaylist(context, playlist.id, track)
                    Toast.makeText(context, "Добавлено в ${playlist.name}", Toast.LENGTH_SHORT).show()
                }
                dialog?.dismiss()
            }

            container.addView(row)
        }

        dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        dialog?.show()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun dp(value: Int, context: Context): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun showEditTagsDialog(track: Track, position: Int, context: Context) {
        val intent = Intent(context, EditTrackActivity::class.java)
        track.path?.let { intent.putExtra("TRACK_PATH", it) }
        context.startActivity(intent)
    }

    private fun confirmDelete(track: Track, position: Int, context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Удалить трек?")
        builder.setMessage("Это удалит файл с устройства. Действие необратимо.")
        builder.setPositiveButton("Удалить") { _, _ ->
            deleteTrackFile(context, track.path, position)
        }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun deleteTrackFile(context: Context, path: String?, position: Int) {
        if (path == null) {
            Toast.makeText(context, "Некорректный путь к файлу", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentResolver = context.contentResolver
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(path)

            val cursor = contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val deleteUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    try {
                        val rowsDeleted = contentResolver.delete(deleteUri, null, null)
                        if (rowsDeleted > 0) {
                            Toast.makeText(context, "Трек удалён", Toast.LENGTH_SHORT).show()
                            removeItem(position)

                            PlaylistManager.getPlaylists().forEach { playlist ->
                                playlist.tracks.removeAll { it.path == path }
                            }
                            PlaylistManager.savePlaylists(context)
                        } else {
                            Toast.makeText(context, "Не удалось удалить файл", Toast.LENGTH_SHORT).show()
                        }
                    } catch (securityException: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val recoverableSecurityException = securityException as?
                                    RecoverableSecurityException
                                ?: throw RuntimeException(securityException.message, securityException)

                            val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                            pendingDeletePath = path
                            pendingDeletePosition = position

                            (context as? android.app.Activity)?.startIntentSenderForResult(
                                intentSender, DELETE_PERMISSION_REQUEST, null, 0, 0, 0, null
                            )
                        } else {
                            throw securityException
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun moveItem(from: Int, to: Int) {
        val item = tracks.removeAt(from)
        tracks.add(to, item)
        notifyItemMoved(from, to)
        onTrackMoved?.invoke(from, to)
    }

    fun removeItem(position: Int) {
        if (position in tracks.indices) {
            tracks.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun updateTracks(newTracks: MutableList<Track>) {
        tracks = newTracks
        notifyDataSetChanged()
    }

    fun getTracks(): MutableList<Track> = tracks
}