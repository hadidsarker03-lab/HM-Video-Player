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

    fun submit(list: List<Video>) {
        items = list
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
        Glide.with(holder.thumb).load(v.uri).centerCrop().into(holder.thumb)
        holder.itemView.setOnClickListener { onClick(v) }
    }

    override fun getItemCount() = items.size
}
