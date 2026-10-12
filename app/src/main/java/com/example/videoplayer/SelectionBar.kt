package com.example.videoplayer

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

// একাধিক ভিডিও/গান বেছে নিয়ে একসাথে ডিলিট, প্রিয়, প্লেলিস্ট, শেয়ার, রিনেম
class SelectionBar(
    private val act: MediaActivity,
    root: LinearLayout,
    private val adapter: VideoAdapter,
    private val afterChange: () -> Unit,
    private val privateMode: Boolean = false
) {
    private val bar = LinearLayout(act)
    private val count = TextView(act)
    private var btnRename: ImageView? = null

    init {
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.visibility = View.GONE
        bar.setBackgroundColor(ContextCompat.getColor(act, R.color.surface_alt))
        bar.setPadding(UiKit.dp(act, 6), UiKit.dp(act, 4), UiKit.dp(act, 6), UiKit.dp(act, 4))

        bar.addView(button(R.drawable.ic_close, R.string.btn_cancel) { adapter.clearSelection() })
        count.setTextColor(ContextCompat.getColor(act, R.color.text_primary))
        count.textSize = 16f
        count.maxLines = 1
        bar.addView(count, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        bar.addView(button(R.drawable.ic_check, R.string.select_all) { adapter.selectAll() })
        if (privateMode) {
            bar.addView(button(R.drawable.ic_unlock, R.string.menu_unhide) {
                PrivacyOps.unhide(act, adapter.selectedVideos()) { done() }
            })
            bar.addView(button(R.drawable.ic_delete, R.string.btn_delete) {
                PrivacyOps.deleteForever(act, adapter.selectedVideos()) { done() }
            })
        } else {
            val rename = button(R.drawable.ic_edit, R.string.menu_rename) {
                adapter.selectedVideos().firstOrNull()?.let {
                    MediaActions.rename(act, it) { done() }
                }
            }
            btnRename = rename
            bar.addView(rename)
            bar.addView(button(R.drawable.ic_favorite, R.string.menu_fav) {
                MediaActions.favoriteMany(act, adapter.selectedVideos())
                done()
            })
            bar.addView(button(R.drawable.ic_playlist_add, R.string.menu_add_playlist) {
                MediaActions.addManyToPlaylist(act, adapter.selectedVideos()) { done() }
            })
            bar.addView(button(R.drawable.ic_share, R.string.menu_share) {
                MediaActions.shareMany(act, adapter.selectedVideos())
            })
            bar.addView(button(R.drawable.ic_lock, R.string.menu_hide) {
                PrivacyOps.hide(act, adapter.selectedVideos()) { done() }
            })
            bar.addView(button(R.drawable.ic_delete, R.string.btn_delete) {
                MediaActions.delete(act, adapter.selectedVideos()) { done() }
            })
        }
        root.addView(bar, 1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        adapter.onSelectionChanged = { update() }
    }

    private fun done() {
        adapter.clearSelection()
        afterChange()
    }

    private fun button(icon: Int, desc: Int, action: () -> Unit): ImageView {
        val tv = TypedValue()
        act.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        return ImageView(act).apply {
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(act, 44), UiKit.dp(act, 44))
            setPadding(UiKit.dp(act, 10), UiKit.dp(act, 10), UiKit.dp(act, 10), UiKit.dp(act, 10))
            setImageResource(icon)
            setColorFilter(ContextCompat.getColor(act, R.color.text_primary))
            setBackgroundResource(tv.resourceId)
            contentDescription = act.getString(desc)
            setOnClickListener { action() }
        }
    }

    fun update() {
        bar.visibility = if (adapter.selecting) View.VISIBLE else View.GONE
        count.text = act.getString(R.string.selected_count, adapter.selected.size)
        btnRename?.visibility = if (adapter.selected.size == 1) View.VISIBLE else View.GONE
    }

    fun active() = adapter.selecting
    fun exit() = adapter.clearSelection()
}
