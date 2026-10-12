package com.example.videoplayer

import android.view.Gravity
import android.view.View
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
        if (!AudioFx.eqSupported()) {
            Toast.makeText(act, R.string.eq_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        val pad = UiKit.dp(act, 22)
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, UiKit.dp(act, 6), pad, 0)

        val sw = MaterialSwitch(act)
        sw.text = act.getString(R.string.eq_enable)
        sw.isChecked = AppSettings.eqOn(act)
        sw.setOnCheckedChangeListener { _, on ->
            AppSettings.setEqOn(act, on)
            AudioFx.applyAll(act)
        }
        box.addView(sw)

        val presetBtn = Button(act)
        presetBtn.text = act.getString(R.string.eq_preset)
        box.addView(presetBtn)

        val bandBox = LinearLayout(act)
        bandBox.orientation = LinearLayout.VERTICAL
        box.addView(bandBox)

        fun fill() {
            bandBox.removeAllViews()
            val range = AudioFx.bandRange()
            for (b in AudioFx.bands()) {
                seekRow(act, bandBox, freqLabel(b.freqHz), range[0], range[1], b.level, true) { v ->
                    AudioFx.setBand(act, b.index, v)
                    if (!sw.isChecked) sw.isChecked = true
                }
            }
        }
        fill()

        presetBtn.setOnClickListener {
            val names = AudioFx.presets()
            if (names.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(act)
                .setTitle(R.string.eq_preset)
                .setItems(names.toTypedArray()) { _, which ->
                    AudioFx.usePreset(act, which)
                    if (!sw.isChecked) sw.isChecked = true
                    fill()
                }
                .show()
        }

        val scroll = ScrollView(act)
        scroll.addView(box)
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.eq_title)
            .setView(scroll)
            .setPositiveButton(R.string.btn_ok, null)
            .setOnDismissListener { onDone() }
            .show()
    }

    // ---------- অডিও ইফেক্ট ----------
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
    fun queue(act: AppCompatActivity, p: Player) {
        val n = p.mediaItemCount
        if (n == 0) return
        val titles = Array(n) { i ->
            p.getMediaItemAt(i).mediaMetadata.title?.toString() ?: (i + 1).toString()
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.queue_title)
            .setSingleChoiceItems(titles, p.currentMediaItemIndex) { d, which ->
                d.dismiss()
                p.seekTo(which, 0L)
                p.play()
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
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

    fun tutorial(act: AppCompatActivity) {
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.tutorial_title)
            .setMessage(R.string.tutorial_body)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }
}
