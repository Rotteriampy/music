package com.example.music

import android.media.MediaMetadataRetriever
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color
import androidx.recyclerview.widget.SimpleItemAnimator
import android.graphics.drawable.GradientDrawable

class QueueActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)

        val recycler = findViewById<RecyclerView>(R.id.queueRecycler)
        findViewById<ImageView>(R.id.queueBackButton)?.setOnClickListener { finish() }
        val adapter = QueueDragAdapter(onStartDrag = { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
        })
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        // Disable change animations to make alpha/color updates instant
        (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recycler.itemAnimator = null
        adapter.submit(QueueManager.getCurrentQueue())

        // Apply themed gradient background
        applyGradientBackground()
        // Show bars to reset any flags
        ThemeManager.showSystemBars(window, this)
        // Do not draw under status bar to keep header fixed
        forceWhiteStatusBarIcons()
    }

    private val itemTouchHelper by lazy {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                (recyclerView.adapter as? QueueDragAdapter)?.moveInAdapter(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // no swipe
            }

            override fun isLongPressDragEnabled(): Boolean = false

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Commit final order on drop
                (recyclerView.adapter as? QueueDragAdapter)?.let { adp ->
                    QueueManager.setQueueOrder(this@QueueActivity, adp.items())
                    // Notify app to refresh highlighting/dimming immediately
                    val br = Intent("com.example.music.PLAYBACK_STATE_CHANGED").apply {
                        setPackage(this@QueueActivity.packageName)
                    }
                    sendBroadcast(br)
                }
            }
        }
        ItemTouchHelper(callback)
    }

    override fun onStart() {
        super.onStart()
        itemTouchHelper.attachToRecyclerView(findViewById(R.id.queueRecycler))
        // listen for playback/queue updates to refresh highlighting instantly
        val filter = IntentFilter().apply {
            addAction("com.example.music.TRACK_CHANGED")
            addAction("com.example.music.PLAYBACK_STATE_CHANGED")
        }
        ContextCompat.registerReceiver(
            this,
            refreshReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(refreshReceiver) } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        applyGradientBackground()
        ThemeManager.showSystemBars(window, this)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyGradientBackground()
            ThemeManager.showSystemBars(window, this)
            forceWhiteStatusBarIcons()
        }
    }

    private fun applyGradientBackground() {
        val start = ThemeManager.getPrimaryGradientStart(this)
        val end = ThemeManager.getPrimaryGradientEnd(this)
        val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(start, end))
        // Set as window background
        try { window.setBackgroundDrawable(gradient) } catch (_: Exception) {}
        // Color bars to match gradient edges
        try { window.statusBarColor = start } catch (_: Exception) {}
        try { window.navigationBarColor = end } catch (_: Exception) {}
    }

    private fun setLayoutFullscreen() {
        // No-op: we keep content below the status bar to prevent header shifting
    }

    private fun applyContentTopPadding() { /* no-op */ }

    private fun forceWhiteStatusBarIcons() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val decor = window.decorView
            var vis = decor.systemUiVisibility
            // Ensure LIGHT_STATUS_BAR flag is cleared so icons are white
            vis = vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            decor.systemUiVisibility = vis
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            (findViewById<RecyclerView>(R.id.queueRecycler).adapter as? QueueDragAdapter)?.let { adp ->
                adp.submit(QueueManager.getCurrentQueue())
            }
        }
    }
}

class QueueDragAdapter(
    initial: List<Track> = emptyList(),
    private val onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
) : RecyclerView.Adapter<QueueDragAdapter.VH>() {

    private var items: List<Track> = initial

    fun submit(newItems: List<Track>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun moveInAdapter(from: Int, to: Int) {
        if (from == to) return
        if (from !in items.indices || to !in items.indices) return
        val mutable = items.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        items = mutable
        notifyItemMoved(from, to)
    }

    fun items(): List<Track> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_queue_track, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val track = items[position]
        holder.bind(track)
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag?.invoke(holder)
            }
            false
        }

        val isPlaying = MusicService.isPlaying
        val currentIndex = QueueManager.getCurrentTrackIndex()
        if (isPlaying && position == currentIndex) {
            holder.title.setTextColor(Color.parseColor("#FFA500"))
            holder.artist.setTextColor(Color.parseColor("#FFA500"))
            holder.itemView.alpha = 1f
        } else {
            holder.title.setTextColor(Color.WHITE)
            holder.artist.setTextColor(Color.parseColor("#B0B0B0"))
            holder.itemView.alpha = if (isPlaying && position < currentIndex) 0.5f else 1f
        }

        holder.itemView.setOnClickListener {
            val t = items[holder.bindingAdapterPosition]
            QueueManager.setCurrentIndex(it.context, holder.bindingAdapterPosition)
            // Immediately refresh UI highlighting (scope to our package)
            val br = Intent("com.example.music.PLAYBACK_STATE_CHANGED").apply {
                setPackage(it.context.packageName)
            }
            it.context.sendBroadcast(br)
            if (t.path != null) {
                // Start PlayerActivity; service will handle playback
                val intent = Intent(it.context, PlayerActivity::class.java).apply {
                    putExtra("TRACK_PATH", t.path)
                    putExtra("TRACK_NAME", t.name)
                    putExtra("TRACK_ARTIST", t.artist)
                }
                it.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.queueItemAvatar)
        val title: TextView = itemView.findViewById(R.id.queueItemTitle)
        val artist: TextView = itemView.findViewById(R.id.queueItemArtist)
        val dragHandle: ImageView = itemView.findViewById(R.id.queueItemDrag)

        fun bind(track: Track) {
            title.text = track.name
            artist.text = track.artist ?: "Unknown Artist"
            val path = track.path
            if (path != null) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val art = retriever.embeddedPicture
                    if (art != null) {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                        avatar.setImageBitmap(bmp)
                    } else {
                        avatar.setImageResource(R.drawable.ic_album_placeholder)
                    }
                    retriever.release()
                } catch (_: Exception) {
                    avatar.setImageResource(R.drawable.ic_album_placeholder)
                }
            } else {
                avatar.setImageResource(R.drawable.ic_album_placeholder)
            }
        }
    }
}
