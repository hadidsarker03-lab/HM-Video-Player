package com.example.videoplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class VideoAdapter(
    private val onClick: (Video) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    private var items: List<Video> = emptyList()
    private var isAudio = false
    private var mode = AppSettings.VIEW_LIST

    fun submit(list: List<Video>, audio: Boolean) {
        items = list
        isAudio = audio
        notifyDataSetChanged()
    }

    fun setMode(m: Int) {
        if (m != mode) {
            mode = m
            notifyDataSetChanged()
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val duration: TextView = view.findViewById(R.id.duration)
        val title: TextView = view.findViewById(R.id.title)
        val size: TextView = view.findViewById(R.id.size)
    }

    override fun getItemViewType(position: Int): Int = mode

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (viewType) {
            AppSettings.VIEW_GRID -> R.layout.item_video_grid
            AppSettings.VIEW_COMPACT -> R.layout.item_video_compact
            else -> R.layout.item_video
        }
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]
        val ctx = holder.itemView.context
        holder.title.text = v.title
        holder.duration.text = v.duration.toTimeString()
        holder.size.text = v.size.toSizeString()

        val padDp = when (mode) {
            AppSettings.VIEW_GRID -> 30
            AppSettings.VIEW_COMPACT -> 12
            else -> 22
        }
        val pad = (padDp * ctx.resources.displayMetrics.density).toInt()

        if (isAudio) {
            Glide.with(holder.thumb).clear(holder.thumb)
            holder.thumb.scaleType = ImageView.ScaleType.FIT_CENTER
            holder.thumb.setBackgroundResource(R.drawable.bg_audio_thumb)
            holder.thumb.setPadding(pad, pad, pad, pad)
            holder.thumb.setImageResource(R.drawable.ic_audio)
            holder.thumb.setColorFilter(ContextCompat.getColor(ctx, R.color.accent))
        } else {
            holder.thumb.clearColorFilter()
            holder.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.thumb.setBackgroundColor(0)
            holder.thumb.setPadding(0, 0, 0, 0)
            Glide.with(holder.thumb).load(v.uri).centerCrop().into(holder.thumb)
        }
        holder.itemView.setOnClickListener { onClick(v) }
    }

    override fun getItemCount() = items.size
}
