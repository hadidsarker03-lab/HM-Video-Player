package com.example.videoplayer

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

// প্লেয়ার স্ক্রিনের ডায়ালগ: স্পিড, স্লিপ টাইমার, ইকুয়ালাইজার, অডিও ইফেক্ট, ট্র্যাক, কিউ, বুকমার্ক, টিউটোরিয়াল
object PlayerDialogs {

    private val speeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

    private fun speedLabel(s: Float): String =
        (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()) + "x"

    fun speed(act: AppCompatActivity, p: Player, onDone: () -> Unit) {
        val cur = speeds.indexOfFirst { kotlin.math.abs(it - p.playbackParameters.speed) < 0.01f }
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.speed_title)
            .setSingleChoiceItems(speeds.map { speedLabel(it) }.toTypedArray(), cur) { d, which ->
                p.setPlaybackSpeed(speeds[which])
                d.dismiss()
                onDone()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    fun sleep(act: AppCompatActivity, onDone: () -> Unit) {
        val mins = intArrayOf(0, 15, 30, 45, 60, 90, 120)
        val labels = mins.map {
            if (it == 0) act.getString(R.string.sleep_off) else act.getString(R.string.sleep_min, it)
        }
        val cur = if (SleepTimer.active()) mins.indexOf(-1) else 0
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.sleep_title)
            .setSingleChoiceItems(labels.toTypedArray(), cur) { d, which ->
                d.dismiss()
                val m = mins[which]
                if (m == 0) {
                    SleepTimer.cancel()
                    Toast.makeText(act, R.string.sleep_cancelled, Toast.LENGTH_SHORT).show()
                } else {
                    val app = act.applicationContext
                    SleepTimer.start(m) {
                        Toast.makeText(app, R.string.sleep_end, Toast.LENGTH_LONG).show()
                    }
                    Toast.makeText(act, act.getString(R.string.sleep_set, m), Toast.LENGTH_SHORT).show()
                }
                onDone()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ---------- ইকুয়ালাইজার ----------
    private fun seekRow(
        act: AppCompatActivity, parent: LinearLayout, label: String, min: Int, max: Int,
        value: Int, enabled: Boolean, onChange: (Int) -> Unit
    ) {
        val title = TextView(act)
        title.text = label
        title.textSize = 14f
        title.setPadding(0, UiKit.dp(act, 10), 0, 0)
        val bar = SeekBar(act)
        bar.max = max - min
        bar.progress = (value - min).coerceIn(0, max - min)
        bar.isEnabled = enabled
        title.alpha = if (enabled) 1f else 0.4f
        bar.alpha = if (enabled) 1f else 0.4f
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress + min)
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        parent.addView(title)
        parent.addView(bar)
    }

    private fun freqLabel(hz: Int) =
        if (hz >= 1000) String.format(Locale.US, "%.1f kHz", hz / 1000f).replace(".0 ", " ")
        else "$hz Hz"

    fun equalizer(act: AppCompatActivity, onDone: () -> Unit = {}) {
        EqSheet.show(act, onDone)
    }

    fun audioEffect(act: AppCompatActivity) {
        val pad = UiKit.dp(act, 22)
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, UiKit.dp(act, 6), pad, 0)
        seekRow(act, box, act.getString(R.string.fx_bass), 0, 1000, AppSettings.fx(act, "bass"),
            AudioFx.bassSupported()) { v ->
            AppSettings.setFx(act, "bass", v)
            AudioFx.applyAll(act)
        }
        seekRow(act, box, act.getString(R.string.fx_virtual), 0, 1000, AppSettings.fx(act, "virt"),
            AudioFx.virtSupported()) { v ->
            AppSettings.setFx(act, "virt", v)
            AudioFx.applyAll(act)
        }
        seekRow(act, box, act.getString(R.string.fx_loud), 0, 1500, AppSettings.fx(act, "loud"),
            AudioFx.loudSupported()) { v ->
            AppSettings.setFx(act, "loud", v)
            AudioFx.applyAll(act)
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.fx_title)
            .setView(box)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    // ---------- সাউন্ড বুস্টার ----------
    fun booster(act: AppCompatActivity, onDone: () -> Unit = {}) {
        val pad = UiKit.dp(act, 22)
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, UiKit.dp(act, 12), pad, 0)
        val label = TextView(act)
        label.textSize = 15f
        label.setPadding(0, 0, 0, UiKit.dp(act, 6))
        val warn = TextView(act)
        warn.setText(R.string.boost_warn)
        warn.textSize = 12f
        warn.alpha = 0.7f
        warn.setPadding(0, UiKit.dp(act, 8), 0, 0)
        val bar = SeekBar(act)
        bar.max = 100
        bar.progress = AppSettings.fx(act, "boost").coerceIn(0, 100)
        fun upd(v: Int) {
            label.text = act.getString(R.string.boost_label, v)
            warn.visibility = if (v > 60) View.VISIBLE else View.INVISIBLE
        }
        upd(bar.progress)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                upd(progress)
                if (fromUser) {
                    AppSettings.setFx(act, "boost", progress)
                    AudioFx.applyAll(act)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        box.addView(label)
        box.addView(bar)
        box.addView(warn)
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.boost_title)
            .setView(box)
            .setPositiveButton(R.string.btn_ok, null)
            .setOnDismissListener { onDone() }
            .show()
    }

    // ---------- অডিও ট্র্যাক / সাবটাইটেল ----------
    fun tracks(act: AppCompatActivity, p: Player, type: Int) {
        class Opt(val label: String, val group: androidx.media3.common.Tracks.Group?, val index: Int)

        val opts = mutableListOf<Opt>()
        var n = 0
        for (g in p.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                n++
                val f = g.getTrackFormat(i)
                val lang = f.language?.takeIf { it != "und" }?.let {
                    Locale.forLanguageTag(it).displayLanguage
                }
                val label = f.label ?: lang ?: act.getString(R.string.track_n, n)
                opts.add(Opt(label, g, i))
            }
        }
        val title = if (type == C.TRACK_TYPE_TEXT) R.string.track_sub else R.string.track_audio
        if (opts.isEmpty()) {
            Toast.makeText(act, R.string.track_none, Toast.LENGTH_SHORT).show()
            return
        }
        val withOff = type == C.TRACK_TYPE_TEXT
        val labels = mutableListOf<String>()
        if (withOff) labels.add(act.getString(R.string.track_off))
        labels.addAll(opts.map { it.label })
        var checked = opts.indexOfFirst { it.group?.isTrackSelected(it.index) == true }
        if (withOff) {
            checked = if (p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || checked < 0) 0
            else checked + 1
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, which ->
                d.dismiss()
                val b = p.trackSelectionParameters.buildUpon()
                if (withOff && which == 0) {
                    b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    val o = opts[if (withOff) which - 1 else which]
                    b.setTrackTypeDisabled(type, false)
                    b.setOverrideForType(
                        TrackSelectionOverride(o.group!!.mediaTrackGroup, listOf(o.index))
                    )
                }
                p.trackSelectionParameters = b.build()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ---------- প্লেয়িং কিউ ----------
    // থাম্বনেইল, রেজোলিউশন | সাইজ, ড্র্যাগ করে ক্রম বদল, X দিয়ে বাদ, "Order" দিয়ে প্লে-অর্ডার বদল
    fun queue(act: AppCompatActivity, p: Player) {
        if (p.mediaItemCount == 0) return
        val dlg = com.google.android.material.bottomsheet.BottomSheetDialog(act)
        val accent = UiKit.accent(act)
        fun dp(v: Int) = UiKit.dp(act, v)

        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xB3101418.toInt())

        // হেডার: < All ............ Order
        val header = LinearLayout(act)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(14), dp(16), dp(10))
        val back = android.widget.ImageView(act)
        back.setImageResource(R.drawable.ic_back)
        back.setOnClickListener { dlg.dismiss() }
        header.addView(back, LinearLayout.LayoutParams(dp(24), dp(24)))
        val title = TextView(act)
        title.setText(R.string.queue_all)
        title.setTextColor(0xFFFFFFFF.toInt())
        title.textSize = 15f
        title.setPadding(dp(14), 0, 0, 0)
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val order = TextView(act)
        order.setText(R.string.queue_order)
        order.setTextColor(0xFFFFFFFF.toInt())
        order.textSize = 14f
        order.compoundDrawablePadding = dp(6)
        order.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_sort, 0)
        order.setOnClickListener {
            // ক্রম → সব লুপ → এক লুপ → লুপ বন্ধ → শাফল
            val msg: Int
            if (p.shuffleModeEnabled) {
                p.shuffleModeEnabled = false; p.repeatMode = Player.REPEAT_MODE_ALL; msg = R.string.loop_all
            } else if (p.repeatMode == Player.REPEAT_MODE_ALL) {
                p.repeatMode = Player.REPEAT_MODE_ONE; msg = R.string.loop_one
            } else if (p.repeatMode == Player.REPEAT_MODE_ONE) {
                p.repeatMode = Player.REPEAT_MODE_OFF; msg = R.string.loop_off
            } else {
                p.shuffleModeEnabled = true; msg = R.string.shuffle_on
            }
            Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
        }
        header.addView(order)
        root.addView(header)

        val rv = androidx.recyclerview.widget.RecyclerView(act)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(act)
        root.addView(rv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (act.resources.displayMetrics.heightPixels * 0.55f).toInt()
        ))

        lateinit var touch: androidx.recyclerview.widget.ItemTouchHelper

        class VH(val row: View, val thumb: android.widget.ImageView, val dur: TextView,
                 val name: TextView, val meta: TextView, val drag: View, val close: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(row)

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<VH>() {
            override fun getItemCount() = p.mediaItemCount

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val row = LinearLayout(act)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(14), dp(6), dp(10), dp(6))
                row.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val frame = android.widget.FrameLayout(act)
                val thumb = android.widget.ImageView(act)
                thumb.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                thumb.setBackgroundColor(0xFF333333.toInt())
                frame.addView(thumb, android.widget.FrameLayout.LayoutParams(dp(92), dp(52)))
                val dur = TextView(act)
                dur.setTextColor(0xFFFFFFFF.toInt())
                dur.textSize = 10f
                dur.setBackgroundColor(0x99000000.toInt())
                dur.setPadding(dp(4), 0, dp(4), 0)
                frame.addView(dur, android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).apply { setMargins(0, 0, dp(3), dp(3)) })
                row.addView(frame, LinearLayout.LayoutParams(dp(92), dp(52)))

                val col = LinearLayout(act)
                col.orientation = LinearLayout.VERTICAL
                col.setPadding(dp(10), 0, dp(6), 0)
                val name = TextView(act)
                name.textSize = 13f
                name.maxLines = 2
                name.ellipsize = android.text.TextUtils.TruncateAt.END
                val meta = TextView(act)
                meta.textSize = 11f
                meta.setTextColor(0xFF999999.toInt())
                col.addView(name)
                col.addView(meta)
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                val drag = TextView(act)
                (drag as TextView).text = "\u2630"
                drag.setTextColor(0xFFBBBBBB.toInt())
                drag.textSize = 20f
                drag.gravity = Gravity.CENTER
                row.addView(drag, LinearLayout.LayoutParams(dp(40), dp(40)))
                val close = android.widget.ImageView(act)
                close.setImageResource(R.drawable.ic_close)
                close.setPadding(dp(10), dp(10), dp(10), dp(10))
                row.addView(close, LinearLayout.LayoutParams(dp(40), dp(40)))
                return VH(row, thumb, dur, name, meta, drag, close)
            }

            @android.annotation.SuppressLint("ClickableViewAccessibility")
            override fun onBindViewHolder(h: VH, position: Int) {
                val item = p.getMediaItemAt(position)
                val id = item.mediaId
                val v = (Library.videos + Library.audios).firstOrNull { it.uri.toString() == id }
                h.name.text = item.mediaMetadata.title?.toString() ?: v?.title ?: (position + 1).toString()
                h.name.setTextColor(if (position == p.currentMediaItemIndex) accent else 0xFFFFFFFF.toInt())
                h.meta.text = v?.meta() ?: ""
                h.dur.text = v?.duration?.toTimeString() ?: ""
                h.dur.visibility = if (v == null) View.GONE else View.VISIBLE
                val uri = item.localConfiguration?.uri
                if (uri != null && v?.isAudio != true) {
                    com.bumptech.glide.Glide.with(h.thumb).load(uri).centerCrop().into(h.thumb)
                } else {
                    com.bumptech.glide.Glide.with(h.thumb).clear(h.thumb)
                    h.thumb.setImageResource(R.drawable.ic_audio)
                }
                h.row.setOnClickListener {
                    val pos = h.bindingAdapterPosition
                    if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return@setOnClickListener
                    p.seekTo(pos, 0L)
                    p.play()
                    dlg.dismiss()
                }
                h.close.setOnClickListener {
                    val pos = h.bindingAdapterPosition
                    if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return@setOnClickListener
                    p.removeMediaItem(pos)
                    if (p.mediaItemCount == 0) dlg.dismiss() else notifyItemRemoved(pos)
                }
                h.drag.setOnTouchListener { _, ev ->
                    if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) touch.startDrag(h)
                    false
                }
            }
        }
        rv.adapter = adapter

        touch = androidx.recyclerview.widget.ItemTouchHelper(object :
            androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
            ) {
            override fun isLongPressDragEnabled() = false

            override fun onMove(
                rv: androidx.recyclerview.widget.RecyclerView,
                vh: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                p.moveMediaItem(from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
        })
        touch.attachToRecyclerView(rv)

        dlg.setContentView(root)
        dlg.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dlg.show()
        // স্বচ্ছ নিচের প্যানেল: পিছনের স্ক্রিন দেখা যাবে, ডিম নেই
        dlg.window?.setDimAmount(0f)
        dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        rv.scrollToPosition(p.currentMediaItemIndex)
    }

    // ---------- বুকমার্ক ----------
    fun bookmarks(act: AppCompatActivity, p: Player) {
        val id = p.currentMediaItem?.mediaId ?: return
        val list = Store.bookmarks(act, id)
        val b = MaterialAlertDialogBuilder(act).setTitle(R.string.bookmark_title)
        if (list.isEmpty()) {
            b.setMessage(R.string.bookmark_none)
        } else {
            b.setItems(list.map { it.toTimeString() }.toTypedArray()) { _, which ->
                p.seekTo(list[which])
            }
            b.setNegativeButton(R.string.bookmark_clear) { _, _ -> Store.clearBookmarks(act, id) }
        }
        b.setNeutralButton(R.string.bookmark_add) { _, _ ->
            Store.addBookmark(act, id, p.currentPosition)
            Toast.makeText(act, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        }
        b.setPositiveButton(R.string.btn_close, null)
        b.show()
    }

    // ছবিওয়ালা জেসচার টিউটোরিয়াল: ২ পাতা, Skip / Next / Got it
    fun tutorial(act: AppCompatActivity) {
        val green = 0xFF2ECC71.toInt()
        fun dp(v: Int) = UiKit.dp(act, v)
        val d = android.app.Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = android.widget.FrameLayout(act)
        root.setBackgroundColor(0xE6000000.toInt())
        val content = android.widget.FrameLayout(act)
        root.addView(content, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // সব আইকন SVG (ভেক্টর) — ইমোজি/ইউনিকোড চিহ্ন নেই
        fun icon(res: Int, sizeDp: Int = 34, tint: Int = 0xFFFFFFFF.toInt()): android.widget.ImageView {
            val v = android.widget.ImageView(act)
            v.setImageResource(res)
            v.setColorFilter(tint)
            v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            return v
        }

        fun num(t: String): TextView {
            val g = TextView(act)
            g.text = t
            g.textSize = 26f
            g.setTextColor(0xFFFFFFFF.toInt())
            g.gravity = Gravity.CENTER
            g.setPadding(dp(4), 0, dp(4), 0)
            return g
        }

        fun mid(vararg items: View): LinearLayout {
            val r = LinearLayout(act)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER
            for (i in items) r.addView(i)
            return r
        }

        fun column(top: View?, mid: View, bottom: View?, label: String): LinearLayout {
            val col = LinearLayout(act)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            // উপরে/নিচে তীর না থাকলেও জায়গা সমান রাখা হয়
            col.addView(top ?: View(act).apply { layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)) })
            col.addView(mid, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10); bottomMargin = dp(10) })
            col.addView(bottom ?: View(act).apply { layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)) })
            val l = TextView(act)
            l.text = label
            l.textSize = 14f
            l.setTextColor(green)
            l.gravity = Gravity.CENTER
            l.setPadding(0, dp(24), 0, 0)
            col.addView(l)
            return col
        }

        fun page(cols: List<LinearLayout>): LinearLayout {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            for (c in cols) row.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }

        val sun = 0xFFFFB300.toInt()
        val pages = listOf(
            page(listOf(
                column(
                    icon(R.drawable.ic_tut_up),
                    mid(icon(R.drawable.ic_tut_hand, 30), num("50"), icon(R.drawable.ic_brightness, 30, sun)),
                    icon(R.drawable.ic_tut_down), act.getString(R.string.display_brightness)
                ),
                column(
                    icon(R.drawable.ic_tut_ne),
                    mid(icon(R.drawable.ic_tut_hand, 34)),
                    icon(R.drawable.ic_tut_sw), act.getString(R.string.tut_pinch)
                ),
                column(
                    icon(R.drawable.ic_tut_up),
                    mid(num("50"), icon(R.drawable.ic_volume, 30), icon(R.drawable.ic_tut_hand, 30)),
                    icon(R.drawable.ic_tut_down), act.getString(R.string.tut_volume)
                )
            )),
            page(listOf(
                column(
                    null,
                    mid(icon(R.drawable.ic_rewind, 36)),
                    icon(R.drawable.ic_tut_hand, 30), act.getString(R.string.tut_rewind)
                ),
                column(
                    null,
                    mid(icon(R.drawable.ic_play, 36), icon(R.drawable.ic_pause, 36)),
                    icon(R.drawable.ic_tut_hand, 30), act.getString(R.string.tut_pause)
                ),
                column(
                    null,
                    mid(icon(R.drawable.ic_forward, 36), num("+10s")),
                    icon(R.drawable.ic_tut_hand, 30), act.getString(R.string.tut_forward)
                )
            ))
        )
        var index = 0
        val btn = TextView(act)
        fun show() {
            content.removeAllViews()
            content.addView(pages[index], android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            btn.setText(if (index == pages.size - 1) R.string.tut_got_it else R.string.tut_next)
        }

        btn.textSize = 15f
        btn.setTextColor(0xFFFFFFFF.toInt())
        btn.gravity = Gravity.CENTER
        btn.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(green)
        }
        btn.setOnClickListener {
            if (index == pages.size - 1) d.dismiss() else { index++; show() }
        }
        root.addView(btn, android.widget.FrameLayout.LayoutParams(dp(220), dp(50), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = dp(40) })

        val skip = TextView(act)
        skip.setText(R.string.tut_skip)
        skip.textSize = 14f
        skip.setTextColor(0xFFFFFFFF.toInt())
        skip.setPadding(dp(16), dp(8), dp(16), dp(8))
        skip.setOnClickListener { d.dismiss() }
        root.addView(skip, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply { setMargins(0, dp(32), dp(16), 0) })

        show()
        d.setContentView(root)
        d.show()
    }
}
