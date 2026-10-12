package com.example.videoplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// তালিকার সারি: মাসের হেডার অথবা ভিডিও/গান
sealed class Row {
    data class Header(val text: String) : Row()
    data class Item(val video: Video) : Row()
}

class VideoAdapter(
    ctx: Context,
    private val onClick: (Video) -> Unit,
    private val onMore: (Video, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<Row> = emptyList()
    private var mode = AppSettings.viewMode(ctx)

    fun submit(list: List<Row>) {
        rows = list
        notifyDataSetChanged()
    }

    fun setMode(m: Int) {
        if (m != mode) {
            mode = m
            notifyDataSetChanged()
        }
    }

    fun isHeader(position: Int): Boolean = position in rows.indices && rows[position] is Row.Header

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.headerText)
    }

    class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val duration: TextView? = view.findViewById(R.id.duration)
        val title: TextView = view.findViewById(R.id.title)
        val meta: TextView = view.findViewById(R.id.meta)
        val chip: View? = view.findViewById(R.id.chipFolder)
        val folderName: TextView? = view.findViewById(R.id.folderName)
        val more: View = view.findViewById(R.id.more)
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) 0 else mode + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderVH(inflater.inflate(R.layout.item_header, parent, false))
            1 -> ItemVH(inflater.inflate(R.layout.item_video, parent, false))
            2 -> ItemVH(inflater.inflate(R.layout.item_video_grid, parent, false))
            else -> ItemVH(inflater.inflate(R.layout.item_video_compact, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        if (holder is HeaderVH && row is Row.Header) {
            holder.text.text = row.text
        } else if (holder is ItemVH && row is Row.Item) {
            bind(holder, row.video)
        }
    }

    private fun bind(h: ItemVH, v: Video) {
        val ctx = h.itemView.context
        h.title.text = v.title
        h.meta.text = if (mode == AppSettings.VIEW_COMPACT) {
            v.duration.toTimeString() + " | " + v.meta()
        } else v.meta()
        h.duration?.text = v.duration.toTimeString()
        if (v.folder.isNotEmpty()) {
            h.chip?.visibility = View.VISIBLE
            h.folderName?.text = v.folder
        } else {
            h.chip?.visibility = View.GONE
        }

        val padDp = when (mode) {
            AppSettings.VIEW_GRID -> 30
            AppSettings.VIEW_COMPACT -> 14
            else -> 24
        }
        val pad = (padDp * ctx.resources.displayMetrics.density).toInt()
        if (v.isAudio) {
            Glide.with(h.thumb).clear(h.thumb)
            h.thumb.scaleType = ImageView.ScaleType.FIT_CENTER
            h.thumb.setBackgroundResource(R.drawable.bg_audio_thumb)
            h.thumb.setPadding(pad, pad, pad, pad)
            h.thumb.setImageResource(R.drawable.ic_audio)
            h.thumb.setColorFilter(ContextCompat.getColor(ctx, R.color.accent))
        } else {
            h.thumb.clearColorFilter()
            h.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            h.thumb.setBackgroundColor(0)
            h.thumb.setPadding(0, 0, 0, 0)
            Glide.with(h.thumb).load(v.uri).centerCrop().into(h.thumb)
        }
        h.itemView.setOnClickListener { onClick(v) }
        h.more.setOnClickListener { onMore(v, it) }
    }

    override fun getItemCount() = rows.size
}
