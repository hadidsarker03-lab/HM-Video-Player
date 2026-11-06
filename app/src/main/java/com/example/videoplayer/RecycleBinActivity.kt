package com.example.videoplayer

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// রিসাইকেল বিন: Video / Music ট্যাব, খালি হলে ছবি + লেখা, নিচে Restore ও Clean বাটন
class RecycleBinActivity : MediaActivity() {

    private var all: List<RecycleBin.Entry> = emptyList()
    private val checked = HashSet<Uri>()
    private var musicTab = false

    private lateinit var tabVideo: TextView
    private lateinit var tabMusic: TextView
    private lateinit var indVideo: View
    private lateinit var indMusic: View
    private lateinit var rv: RecyclerView
    private lateinit var selectAll: CheckBox
    private lateinit var emptyBox: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var btnRestore: TextView
    private lateinit var btnClean: TextView
    private val adapter = BinAdapter()

    private fun dp(v: Int) = UiKit.dp(this, v)
    private fun attr(a: Int, def: Int) = MaterialColors.getColor(this, a, def)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        val text = UiKit.textColor(this)
        val sub = attr(R.attr.hmTextSecondary, Color.GRAY)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(attr(R.attr.hmBg, Color.BLACK))

        // ---- হেডার: ← শিরোনাম ⓘ ----
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(6), dp(8), dp(8), dp(4))
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(text)
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.setBackgroundResource(UiKit.ripple(this))
        back.contentDescription = getString(R.string.desc_back)
        back.setOnClickListener { finish() }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this)
        title.setText(R.string.me_recycle)
        title.textSize = 20f
        title.setTextColor(text)
        title.typeface = UiKit.mediumFont()
        title.maxLines = 1
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })
        val info = ImageView(this)
        info.setImageResource(R.drawable.ic_info)
        info.setColorFilter(text)
        info.setPadding(dp(12), dp(12), dp(12), dp(12))
        info.setBackgroundResource(UiKit.ripple(this))
        info.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.me_recycle)
                .setMessage(R.string.bin_info)
                .setPositiveButton(R.string.btn_ok, null)
                .show()
        }
        header.addView(info, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        // ---- ট্যাব: নিচে সবুজ/অ্যাকসেন্ট দাগ ----
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.setPadding(dp(16), dp(8), dp(16), 0)
        fun tab(label: Int, music: Boolean): Pair<TextView, View> {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            val t = TextView(this)
            t.setText(label)
            t.textSize = 15f
            t.gravity = Gravity.CENTER
            t.typeface = UiKit.mediumFont()
            t.setPadding(0, dp(12), 0, dp(12))
            col.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val ind = View(this)
            ind.background = GradientDrawable().apply {
                setColor(UiKit.accent(this@RecycleBinActivity))
                cornerRadius = dp(2).toFloat()
            }
            col.addView(ind, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
            col.setOnClickListener { selectTab(music) }
            tabs.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
            return t to ind
        }
        tab(R.string.tab_video, false).let { tabVideo = it.first; indVideo = it.second }
        tab(R.string.tab_audio, true).let { tabMusic = it.first; indMusic = it.second }
        root.addView(tabs)

        // ---- "সব বাছাই" ----
        selectAll = CheckBox(this)
        selectAll.setText(R.string.bin_select_all)
        selectAll.setTextColor(text)
        selectAll.setPadding(dp(8), 0, 0, 0)
        selectAll.setOnClickListener {
            val cur = current()
            if (selectAll.isChecked) cur.forEach { checked.add(it.uri) } else cur.forEach { checked.remove(it.uri) }
            adapter.notifyDataSetChanged()
            updateButtons()
        }
        root.addView(selectAll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(20), dp(8), dp(20), 0)
        })

        // ---- মাঝখান: তালিকা বা খালি ছবি ----
        val body = FrameLayout(this)
        rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.itemAnimator = null
        rv.clipToPadding = false
        body.addView(rv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        emptyBox = LinearLayout(this)
        emptyBox.orientation = LinearLayout.VERTICAL
        emptyBox.gravity = Gravity.CENTER
        val pic = ImageView(this)
        pic.setImageResource(R.drawable.ic_bin_empty)
        emptyBox.addView(pic, LinearLayout.LayoutParams(dp(160), dp(130)))
        emptyText = TextView(this)
        emptyText.textSize = 13f
        emptyText.setTextColor(sub)
        emptyText.gravity = Gravity.CENTER
        emptyText.setPadding(dp(24), dp(16), dp(24), 0)
        emptyBox.addView(emptyText)
        body.addView(emptyBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---- নিচের বাটন: Restore (বর্ডার) ও Clean (ভরাট) ----
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(16), dp(14), dp(16), dp(14))
        bar.setBackgroundColor(attr(R.attr.hmSurface, Color.parseColor("#1A1B1E")))
        btnRestore = button(R.string.bin_restore)
        btnClean = button(R.string.bin_clean)
        btnRestore.setOnClickListener { restore() }
        btnClean.setOnClickListener { clean() }
        bar.addView(btnRestore, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(16) })
        bar.addView(btnClean, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(bar)

        setContentView(root)
        UiKit.applyInsets(this, root, header, true)
        selectTab(false)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun button(label: Int): TextView {
        val b = TextView(this)
        b.setText(label)
        b.textSize = 15f
        b.gravity = Gravity.CENTER
        b.typeface = UiKit.mediumFont()
        return b
    }

    private fun current() = all.filter { it.video != musicTab }

    private fun selectTab(music: Boolean) {
        musicTab = music
        val on = UiKit.accent(this)
        val off = UiKit.textColor(this)
        tabVideo.setTextColor(if (music) off else on)
        tabMusic.setTextColor(if (music) on else off)
        indVideo.visibility = if (music) View.INVISIBLE else View.VISIBLE
        indMusic.visibility = if (music) View.VISIBLE else View.INVISIBLE
        refreshList()
    }

    private fun load() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { RecycleBin.list(applicationContext) }
            all = list
            checked.retainAll(list.map { it.uri }.toSet())
            refreshList()
        }
    }

    private fun refreshList() {
        val cur = current()
        val empty = cur.isEmpty()
        emptyBox.visibility = if (empty) View.VISIBLE else View.GONE
        rv.visibility = if (empty) View.GONE else View.VISIBLE
        selectAll.visibility = if (empty) View.GONE else View.VISIBLE
        emptyText.setText(if (musicTab) R.string.bin_empty_music else R.string.bin_empty_video)
        adapter.notifyDataSetChanged()
        updateButtons()
    }

    private fun updateButtons() {
        val cur = current()
        val sel = cur.count { it.uri in checked }
        selectAll.isChecked = cur.isNotEmpty() && sel == cur.size
        val enabled = sel > 0
        val sub = attr(R.attr.hmTextSecondary, Color.GRAY)
        val text = UiKit.textColor(this)
        val accent = UiKit.accent(this)
        btnRestore.isEnabled = enabled
        btnClean.isEnabled = enabled
        btnRestore.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), if (enabled) text else sub)
        }
        btnRestore.setTextColor(if (enabled) text else sub)
        btnClean.background = GradientDrawable().apply {
            setColor(if (enabled) accent else Color.parseColor("#6E6E70"))
            cornerRadius = dp(8).toFloat()
        }
        btnClean.setTextColor(if (enabled) Color.WHITE else Color.parseColor("#A0A0A2"))
    }

    private fun chosen(): List<Uri> = current().filter { it.uri in checked }.map { it.uri }

    private fun restore() {
        val uris = chosen()
        if (uris.isEmpty()) return
        try {
            val pi = MediaStore.createTrashRequest(contentResolver, uris, false)
            requestGrant(pi.intentSender) {
                MediaActions.finishChange(this, uris.size, R.string.bin_restored) { checked.removeAll(uris.toSet()); load() }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clean() {
        val uris = chosen()
        if (uris.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bin_clean)
            .setMessage(getString(R.string.bin_clean_confirm, uris.size))
            .setPositiveButton(R.string.bin_clean) { _, _ ->
                try {
                    val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                    requestGrant(pi.intentSender) {
                        MediaActions.finishChange(this, uris.size, R.string.deleted) { checked.removeAll(uris.toSet()); load() }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private inner class BinAdapter : RecyclerView.Adapter<BinAdapter.VH>() {
        inner class VH(val row: LinearLayout, val icon: ImageView, val name: TextView, val box: CheckBox) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(this@RecycleBinActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(20), dp(10), dp(12), dp(10))
            row.setBackgroundResource(UiKit.ripple(this@RecycleBinActivity))
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val icon = ImageView(this@RecycleBinActivity)
            icon.setPadding(dp(10), dp(10), dp(10), dp(10))
            icon.background = GradientDrawable().apply {
                setColor(attr(R.attr.hmSurfaceAlt, Color.DKGRAY))
                cornerRadius = dp(10).toFloat()
            }
            icon.setColorFilter(attr(R.attr.hmTextSecondary, Color.GRAY))
            row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
            val name = TextView(this@RecycleBinActivity)
            name.setTextColor(UiKit.textColor(this@RecycleBinActivity))
            name.textSize = 14f
            name.maxLines = 2
            name.ellipsize = android.text.TextUtils.TruncateAt.END
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14); marginEnd = dp(8)
            })
            val box = CheckBox(this@RecycleBinActivity)
            box.isClickable = false
            box.isFocusable = false
            row.addView(box)
            return VH(row, icon, name, box)
        }

        override fun getItemCount() = current().size

        override fun onBindViewHolder(h: VH, position: Int) {
            val e = current()[position]
            h.icon.setImageResource(if (e.video) R.drawable.ic_video else R.drawable.ic_audio)
            h.name.text = e.name
            h.box.isChecked = e.uri in checked
            h.row.setOnClickListener {
                if (!checked.remove(e.uri)) checked.add(e.uri)
                val p = h.bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) notifyItemChanged(p)
                updateButtons()
            }
        }
    }
}
