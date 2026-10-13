package com.example.videoplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// ফোল্ডার ও প্লেলিস্টের সারি
data class RowData(val key: String, val icon: Int, val title: String, val sub: String)

class RowAdapter(
    private val onClick: (RowData) -> Unit,
    private val onLong: (RowData) -> Unit
) : RecyclerView.Adapter<RowAdapter.VH>() {

    private var items: List<RowData> = emptyList()

    fun submit(list: List<RowData>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.rowIcon)
        val title: TextView = view.findViewById(R.id.rowTitle)
        val sub: TextView = view.findViewById(R.id.rowSub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.icon.setImageResource(r.icon)
        holder.title.text = r.title
        if (r.sub.isEmpty()) holder.sub.visibility = View.GONE else {
            holder.sub.visibility = View.VISIBLE
            holder.sub.text = r.sub
        }
        holder.itemView.setOnClickListener { onClick(r) }
        holder.itemView.setOnLongClickListener {
            onLong(r)
            true
        }
    }

    override fun getItemCount() = items.size
}
