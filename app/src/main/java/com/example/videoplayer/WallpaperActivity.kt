package com.example.videoplayer

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

// অনলাইন ওয়ালপেপার (Pexels): সার্চ/ক্যাটাগরি, বাছলে ফোনের পূর্ণ রেজোলিউশনে নামিয়ে
// অ্যাপের ভেতরে সেভ হয় — তাই অফলাইনেও থাকে এবং ছবি ফাটে না
class WallpaperActivity : AppCompatActivity() {

    private class Photo(
        val id: Long, val thumb: String, val preview: String,
        val original: String, val large2x: String, val who: String
    )

    private val photos = ArrayList<Photo>()
    private var query = "nature"
    private var page = 1
    private var loading = false
    private var more = true
    private val http = OkHttpClient()

    private lateinit var status: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var search: EditText
    private lateinit var adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>

    private val categories = listOf(
        "Nature", "Space", "Abstract", "Dark", "Minimal", "City",
        "Ocean", "Flowers", "Mountains", "Sky", "Anime", "Cars"
    )

    private fun dp(v: Int) = UiKit.dp(this, v)

    private fun apiKey(): String =
        (AppSettings.prefs(this).getString("pexels_key", "") ?: "").trim()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        Backgrounds.apply(this)
        build()
        if (apiKey().isEmpty()) askKey() else load(true)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun build() {
        val fg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val accent = UiKit.accent(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(8), dp(8), dp(12), dp(4))
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(fg)
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.setOnClickListener { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this)
        title.setText(R.string.wp_title)
        title.setTextColor(fg)
        title.textSize = 20f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val keyBtn = TextView(this)
        keyBtn.setText(R.string.wp_key_btn)
        keyBtn.setTextColor(accent)
        keyBtn.textSize = 13f
        keyBtn.setPadding(dp(10), dp(8), dp(10), dp(8))
        keyBtn.setOnClickListener { askKey() }
        top.addView(keyBtn)
        root.addView(top)

        search = EditText(this)
        search.setHint(R.string.wp_search_hint)
        search.setTextColor(fg)
        search.setHintTextColor((fg and 0x00FFFFFF) or 0x88000000.toInt())
        search.setSingleLine(true)
        search.imeOptions = EditorInfo.IME_ACTION_SEARCH
        search.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                val q = search.text.toString().trim()
                if (q.isNotEmpty()) {
                    query = q
                    load(true)
                }
                true
            } else false
        }
        root.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(20), 0, dp(20), 0) })

        val chipScroll = HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(dp(16), dp(8), dp(16), dp(8))
        for (c in categories) {
            val t = TextView(this)
            t.text = c
            t.setTextColor(fg)
            t.textSize = 13f
            t.setPadding(dp(14), dp(7), dp(14), dp(7))
            t.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), (fg and 0x00FFFFFF) or 0x55000000)
            }
            t.setOnClickListener {
                query = c
                search.setText("")
                load(true)
            }
            chips.addView(t, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
        chipScroll.addView(chips)
        root.addView(chipScroll)

        status = TextView(this)
        status.setTextColor((fg and 0x00FFFFFF) or 0xAA000000.toInt())
        status.textSize = 13f
        status.setPadding(dp(20), dp(2), dp(20), dp(2))
        status.visibility = View.GONE
        root.addView(status)

        recycler = RecyclerView(this)
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.setPadding(dp(8), dp(4), dp(8), dp(4))
        recycler.clipToPadding = false
        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = photos.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val iv = ImageView(this@WallpaperActivity)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setBackgroundColor(0x33888888)
                val w = (resources.displayMetrics.widthPixels - dp(16)) / 3 - dp(8)
                iv.layoutParams = ViewGroup.MarginLayoutParams(w, w * 3 / 2).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                return object : RecyclerView.ViewHolder(iv) {}
            }

            override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
                val p = photos[position]
                val iv = h.itemView as ImageView
                Glide.with(iv).load(p.thumb).centerCrop().into(iv)
                iv.setOnClickListener { preview(p) }
            }
        }
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !rv.canScrollVertically(1)) load(false)
            }
        })
        root.addView(recycler, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val credit = TextView(this)
        credit.setText(R.string.wp_credit)
        credit.setTextColor(accent)
        credit.textSize = 12f
        credit.gravity = Gravity.CENTER
        credit.setPadding(0, dp(8), 0, dp(10))
        credit.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.pexels.com")))
            } catch (e: Exception) { Dbg.w("WallpaperActivity.onScrolled", e) }
        }
        root.addView(credit)

        setContentView(root)
    }

    private fun showStatus(msg: Int?) {
        if (msg == null) {
            status.visibility = View.GONE
        } else {
            status.setText(msg)
            status.visibility = View.VISIBLE
        }
    }

    private fun askKey() {
        val input = EditText(this)
        input.setSingleLine(true)
        input.setText(apiKey())
        input.setHint(R.string.wp_key_hint)
        val box = android.widget.FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.wp_key_title)
            .setMessage(R.string.wp_key_msg)
            .setView(box)
            .setPositiveButton(R.string.wp_save) { _, _ ->
                val k = input.text.toString().trim()
                AppSettings.prefs(this).edit().putString("pexels_key", k).apply()
                if (k.isNotEmpty()) load(true)
            }
            .setNeutralButton(R.string.wp_key_get) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.pexels.com/api/")))
                } catch (e: Exception) { Dbg.w("WallpaperActivity.askKey", e) }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun load(reset: Boolean) {
        val key = apiKey()
        if (key.isEmpty()) {
            askKey()
            return
        }
        if (reset) {
            photos.clear()
            page = 1
            more = true
            adapter.notifyDataSetChanged()
        } else if (loading || !more) {
            return
        }
        loading = true
        showStatus(R.string.wp_loading)
        val url = "https://api.pexels.com/v1/search?query=" + URLEncoder.encode(query, "UTF-8") +
            "&orientation=portrait&size=large&per_page=30&page=" + page
        val myQuery = query
        lifecycleScope.launch {
            var result: List<Photo>? = null
            var hasNext = false
            var bad = false
            withContext(Dispatchers.IO) {
            try {
                http.newCall(Request.Builder().url(url).header("Authorization", key).build())
                    .execute().use { r ->
                        if (r.code == 401 || r.code == 403) {
                            bad = true
                        } else if (r.isSuccessful) {
                            val j = JSONObject(r.body?.string() ?: "{}")
                            val arr = j.getJSONArray("photos")
                            val list = ArrayList<Photo>()
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                val src = o.getJSONObject("src")
                                list.add(
                                    Photo(
                                        o.getLong("id"), src.getString("medium"), src.getString("portrait"),
                                        src.getString("original"), src.getString("large2x"),
                                        o.optString("photographer", "")
                                    )
                                )
                            }
                            result = list
                            hasNext = j.has("next_page")
                        }
                    }
            } catch (e: Exception) {
                Dbg.w("wallpaper search", e)
            }
            }
            run {
                if (myQuery != query) {
                    loading = false
                    return@launch
                }
                loading = false
                val list = result
                when {
                    bad -> showStatus(R.string.wp_key_bad)
                    list == null -> showStatus(R.string.wp_offline)
                    else -> {
                        val start = photos.size
                        photos.addAll(list)
                        adapter.notifyItemRangeInserted(start, list.size)
                        more = hasNext
                        page++
                        showStatus(if (photos.isEmpty()) R.string.wp_empty else null)
                    }
                }
            }
        }
    }

    private fun preview(p: Photo) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(16), dp(8), dp(16), 0)
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setBackgroundColor(0x33888888)
        box.addView(iv, LinearLayout.LayoutParams(dp(220), dp(330)))
        Glide.with(iv).load(p.preview).centerCrop().into(iv)
        val who = TextView(this)
        who.text = getString(R.string.wp_by, p.who)
        who.textSize = 12f
        who.setPadding(0, dp(8), 0, 0)
        box.addView(who)
        MaterialAlertDialogBuilder(this)
            .setView(box)
            .setPositiveButton(R.string.wp_set) { _, _ -> download(p) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ফোনের স্ক্রিনের মাপে (সর্বোচ্চ ১৪৪০ চওড়া) নামানো — বাড়িয়ে ঝাপসা করা নয়; কাঁচা ফাইল সেভ, আবার কম্প্রেস নয়
    private fun download(p: Photo) {
        val dlg = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.wp_downloading)
            .setCancelable(false)
            .show()
        val dm = resources.displayMetrics
        val w = minOf(dm.widthPixels, 1440)
        val h = (w.toLong() * dm.heightPixels / dm.widthPixels).toInt()
        val urls = listOf(
            p.original + "?auto=compress&cs=tinysrgb&fit=crop&w=$w&h=$h",
            p.large2x
        )
        val dir = Backgrounds.dir(this)
        lifecycleScope.launch {
            val key = withContext(Dispatchers.IO) {
            var saved: String? = null
            for (u in urls) {
                val f = File(dir, "p${p.id}.jpg")
                try {
                    dir.mkdirs()
                    http.newCall(Request.Builder().url(u).build()).execute().use { r ->
                        if (r.isSuccessful) {
                            r.body?.byteStream()?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
                        }
                    }
                    val o = BitmapFactory.Options()
                    o.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(f.absolutePath, o)
                    if (o.outWidth > 0 && o.outHeight > 0) {
                        saved = "file:p${p.id}.jpg"
                        break
                    }
                    f.delete()
                } catch (e: Exception) {
                    f.delete()
                }
            }
            saved
            }
            dlg.dismiss()
            run {
                if (key != null) {
                    Backgrounds.select(this@WallpaperActivity, key)
                    Toast.makeText(this@WallpaperActivity, R.string.wp_saved, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@WallpaperActivity, R.string.wp_fail, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
