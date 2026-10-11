package com.example.videoplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class VideoAdapter(
    private val onClick: (Video) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    private var items: List<Video> = emptyList()
    private var isAudio = false

    fun submit(list: List<Video>, audio: Boolean) {
        items = list
        isAudio = audio
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val duration: TextView = view.findViewById(R.id.duration)
        val title: TextView = view.findViewById(R.id.title)
        val size: TextView = view.findViewById(R.id.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]
        holder.title.text = v.title
        holder.duration.text = v.duration.toTimeString()
        holder.size.text = v.size.toSizeString()

        val pad = (28 * holder.itemView.resources.displayMetrics.density).toInt()
        if (isAudio) {
            Glide.with(holder.thumb).clear(holder.thumb)
            holder.thumb.scaleType = ImageView.ScaleType.FIT_CENTER
            holder.thumb.setBackgroundResource(R.drawable.bg_audio_thumb)
            holder.thumb.setPadding(pad, pad, pad, pad)
            holder.thumb.setImageResource(R.drawable.ic_audio)
        } else {
            holder.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.thumb.setBackgroundColor(0)
            holder.thumb.setPadding(0, 0, 0, 0)
            Glide.with(holder.thumb).load(v.uri).centerCrop().into(holder.thumb)
        }
        holder.itemView.setOnClickListener { onClick(v) }
    }

    override fun getItemCount() = items.size
}
