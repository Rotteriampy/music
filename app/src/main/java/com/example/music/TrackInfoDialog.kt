package com.example.music

import android.app.Dialog
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TrackInfoDialog(context: Context, private val track: Track) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_track_info)

        val file = File(track.path ?: "")
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(track.path)

            findViewById<TextView>(R.id.tvInfoTitle).text = track.name
            findViewById<TextView>(R.id.tvInfoArtist).text = "Исполнитель: ${track.artist ?: "Unknown"}"
            findViewById<TextView>(R.id.tvInfoAlbum).text = "Альбом: ${track.albumName ?: "Unknown"}"
            findViewById<TextView>(R.id.tvInfoGenre).text = "Жанр: ${track.genre ?: "Unknown"}"

            val duration = (track.duration ?: 0) / 1000
            val minutes = duration / 60
            val seconds = duration % 60
            findViewById<TextView>(R.id.tvInfoDuration).text = "Длительность: ${minutes}:${String.format("%02d", seconds)}"

            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            findViewById<TextView>(R.id.tvInfoBitrate).text = "Битрейт: ${bitrate / 1000} kbps"

            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            findViewById<TextView>(R.id.tvInfoSampleRate).text = "Частота дискретизации: ${sampleRate / 1000} kHz"

            val fileSize = file.length() / (1024 * 1024)
            findViewById<TextView>(R.id.tvInfoFileSize).text = "Размер: $fileSize МБ"

            findViewById<TextView>(R.id.tvInfoPath).text = "Путь: ${track.path ?: "Unknown"}"

            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val modifiedDate = dateFormat.format(Date(file.lastModified()))
            findViewById<TextView>(R.id.tvInfoModified).text = "Изменён: $modifiedDate"

            val playCount = ListeningStats.getPlayCount(track.path ?: "")
            findViewById<TextView>(R.id.tvInfoPlayCount).text = "Прослушиваний: $playCount"

            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
            retriever.release()
        }

        findViewById<TextView>(R.id.btnCloseInfo).setOnClickListener {
            dismiss()
        }
    }
}