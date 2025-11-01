package com.arotter.music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QueueAdapter(private val queue: List<Track>) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    class QueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val trackName: TextView = itemView.findViewById(R.id.queueTrackName)
        val trackArtist: TextView = itemView.findViewById(R.id.queueTrackArtist)
        val position: TextView = itemView.findViewById(R.id.queuePosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val track = queue[position]
        holder.trackName.text = track.name
        holder.trackArtist.text = track.artist ?: "Unknown Artist"
        holder.position.text = "${position + 1}."
    }

    override fun getItemCount(): Int = queue.size
}