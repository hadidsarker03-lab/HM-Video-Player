package com.example.videoplayer

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream

// ব্রাউজার: অ্যাপের নিজস্ব UI (হোমপেজ, বার, মেনু), অ্যাড ব্লক, সব সাইটে অ্যাপের থিম, যেকোনো ভিডিও ডাউনলোড
class BrowserActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var home: View
    private lateinit var btnBack: ImageView
    private lateinit var btnFwd: ImageView
    private lateinit var btnReload: ImageView
    private lateinit var btnShield: ImageView
    private lateinit var btnDl: ImageView
    private lateinit var dlBadge: TextView
    private lateinit var incognitoLabel: TextView
    private lateinit var incognitoPill: LinearLayout

    private var incognito = false
    private var blocked = 0
    private val found = LinkedHashSet<String>()
    private var upgradedHost: String? = null
    private val noUpgrade = HashSet<String>()

    private val mediaExt = listOf(".mp4", ".mkv", ".webm", ".m3u8", ".3gp", ".mov", ".avi", ".mp3", ".m4a", ".mpd")

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val q = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == RESULT_OK && !q.isNullOrEmpty()) go(q)
    }

    private fun dp(v: Int) = UiKit.dp(this, v)
    private fun adOn() = AppSettings.prefs(this).getBoolean("adblock", true)
    private fun themeOn() = AppSettings.prefs(this).getBoolean("force_theme", true)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        // ---- উপরের বার: বন্ধ, ঠিকানা, রিলোড ----
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(8), dp(6), dp(8), dp(6))

        val close = iconBtn(R.drawable.ic_back) { finish() }
        btnReload = iconBtn(R.drawable.ic_history) { reloadOrStop() }
        urlBar = EditText(this).apply {
            hint = getString(R.string.br_hint)
            setSingleLine()
            textSize = 14f
            setTextColor(UiKit.boxTextColor(this@BrowserActivity))
            setHintTextColor(0x88888888.toInt())
            setBackgroundResource(R.drawable.bg_tab)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    go(text.toString()); true
                } else false
            }
        }
        top.addView(close)
        top.addView(urlBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(dp(4), 0, dp(4), 0) })
        top.addView(btnReload)
        root.addView(top)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(UiKit.accent(this@BrowserActivity))
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        // ---- ওয়েবভিউ + হোমপেজ ----
        web = WebView(this)
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, AppSettings.prefs(this).getBoolean("cookies_3p", true))
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url.toString()
                if (adOn() && AdBlock.isAd(u)) {
                    blocked++
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                if (isMediaLink(u)) runOnUiThread { addFound(u) }
                return null
            }

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url.toString()
                if (!u.startsWith("http")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (e: Exception) { }
                    return true
                }
                if (adOn() && AdBlock.isAd(u)) { blocked++; return true }
                if (r.isForMainFrame && u.startsWith("http://")) {
                    val up = upgrade(u)
                    if (up != u) { v.loadUrl(up); return true }
                }
                if (isMediaLink(u)) { askMedia(u); return true }
                return false
            }

            override fun onPageStarted(v: WebView, url: String, icon: Bitmap?) {
                urlBar.setText(url)
                progress.visibility = View.VISIBLE
                found.clear()
                updateDl()
                injectPage(v)
            }

            override fun onPageCommitVisible(v: WebView, url: String) { injectPage(v) }

            override fun onPageFinished(v: WebView, url: String) {
                progress.visibility = View.GONE
                updateNav()
                injectPage(v)
            }

            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                val h = r.url.host
                if (r.isForMainFrame && r.url.scheme == "https" && h != null && h == upgradedHost) {
                    noUpgrade.add(h)
                    upgradedHost = null
                    v.loadUrl(r.url.toString().replaceFirst("https://", "http://"))
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.progress = p
                progress.visibility = if (p >= 100) View.GONE else View.VISIBLE
            }
        }
        web.setDownloadListener { url, ua, cd, mime, _ -> askDownload(url, ua, cd, mime) }

        home = buildHome()
        val content = FrameLayout(this)
        content.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(home, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---- নিচের বার ----
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.setPadding(dp(8), dp(6), dp(8), dp(6))
        btnBack = iconBtn(R.drawable.ic_prev) { if (web.canGoBack()) web.goBack() }
        btnFwd = iconBtn(R.drawable.ic_next) { if (web.canGoForward()) web.goForward() }
        val btnHome = iconBtn(R.drawable.ic_home) { showHome(true) }
        btnShield = iconBtn(R.drawable.ic_shield) { openShields() }
        btnDl = iconBtn(R.drawable.ic_download) { openVideoList() }
        val btnMore = iconBtn(R.drawable.ic_more) { }
        btnMore.setOnClickListener { openMenu(btnMore) }
        dlBadge = TextView(this).apply {
            textSize = 10f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setBackgroundColor(UiKit.accent(this@BrowserActivity))
            setPadding(dp(5), 0, dp(5), 0)
            visibility = View.GONE
        }
        val dlWrap = FrameLayout(this)
        dlWrap.addView(btnDl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dlWrap.addView(dlBadge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(16), Gravity.TOP or Gravity.END)
            .apply { setMargins(0, dp(2), dp(4), 0) })
        for (v in listOf<View>(btnBack, btnFwd, btnHome, btnShield, dlWrap, btnMore)) {
            bottom.addView(v, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        root.addView(bottom)

        val frame = FrameLayout(this)
        frame.addView(root)
        frame.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
            this, R.attr.hmBg, 0xFFDFE4EC.toInt()))
        setContentView(frame)
        UiKit.applyInsets(this, frame, top, true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    home.visibility == View.VISIBLE -> finish()
                    web.canGoBack() -> web.goBack()
                    else -> showHome(true)
                }
            }
        })

        updateShield()
        updateNav()
        updateDl()
        val restored = savedInstanceState?.let { web.restoreState(it) }
        if (restored != null && web.url != null) showHome(false) else showHome(true)
    }

    // ---------- হোমপেজ (অ্যাপের UI-তে) ----------
    private fun buildHome(): View {
        val sv = ScrollView(this)
        sv.isFillViewport = true
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(18), dp(48), dp(18), dp(24))

        val logo = TextView(this).apply {
            text = "Brave"
            textSize = 48f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -0.03f
            setTextColor(UiKit.textColor(this@BrowserActivity))
            gravity = Gravity.CENTER
        }
        col.addView(logo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(28) })

        // সার্চ বার: সার্চ আইকন + লেখা + মাইক + লেন্স
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundResource(R.drawable.bg_tab)
        bar.setPadding(dp(20), 0, dp(12), 0)
        val sIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(UiKit.accent(this@BrowserActivity))
        }
        bar.addView(sIcon, LinearLayout.LayoutParams(dp(24), dp(24)))
        val hint = TextView(this).apply {
            text = getString(R.string.br_home_hint)
            textSize = 14f
            setTextColor(0x99888888.toInt())
            setPadding(dp(12), 0, dp(8), 0)
            setSingleLine()
        }
        bar.addView(hint, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val mic = iconBtn(R.drawable.ic_mic) { startVoice() }.apply { setBackgroundResource(0) }
        val lens = iconBtn(R.drawable.ic_lens) { go("https://search.brave.com/images") }.apply { setBackgroundResource(0) }
        bar.addView(mic)
        bar.addView(lens)
        bar.setOnClickListener { focusUrl() }
        col.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))

        // AI Mode + Incognito
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val ai = pill(R.drawable.ic_ai, getString(R.string.br_ai_mode)) { go("https://search.brave.com/news") }
        incognitoPill = pill(R.drawable.ic_incognito, getString(R.string.br_incognito)) { setIncognito(!incognito) }
        incognitoLabel = incognitoPill.getChildAt(1) as TextView
        row.addView(ai, LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(0, 0, dp(6), 0) })
        row.addView(incognitoPill, LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(6), 0, 0, 0) })
        col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(14) })

        sv.addView(col)
        return sv
    }

    private fun pill(icon: Int, label: String, action: () -> Unit): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.HORIZONTAL
        p.gravity = Gravity.CENTER
        p.setBackgroundResource(R.drawable.bg_tab)
        val i = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(UiKit.boxTextColor(this@BrowserActivity))
        }
        p.addView(i, LinearLayout.LayoutParams(dp(22), dp(22)))
        val t = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(UiKit.boxTextColor(this@BrowserActivity))
            setPadding(dp(10), 0, 0, 0)
        }
        p.addView(t)
        p.setOnClickListener { action() }
        return p
    }

    private fun showHome(show: Boolean) {
        home.visibility = if (show) View.VISIBLE else View.GONE
        web.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            urlBar.setText("")
            progress.visibility = View.GONE
        }
        updateNav()
    }

    private fun focusUrl() {
        urlBar.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showSoftInput(urlBar, 0)
    }

    private fun startVoice() {
        try {
            voiceLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.br_voice_prompt))
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.br_voice_missing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun iconBtn(icon: Int, action: () -> Unit) = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { setMargins(dp(2), 0, dp(2), 0) }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setImageResource(icon)
        setColorFilter(UiKit.boxTextColor(this@BrowserActivity))
        setBackgroundResource(R.drawable.bg_icon_box)
        setOnClickListener { action() }
    }

    // ---------- মেনু ----------
    private fun openMenu(anchor: View) {
        val items = listOf(
            1 to (if (adOn()) R.string.br_adblock_on else R.string.br_adblock_off),
            2 to (if (themeOn()) R.string.br_theme_on else R.string.br_theme_off),
            3 to (if (incognito) R.string.br_incognito_on else R.string.br_incognito_off),
            4 to R.string.br_videos,
            5 to R.string.br_open_brave
        )
        UiKit.borderedMenu(this, anchor, items) { id ->
            when (id) {
                1 -> toggleAdBlock(false)
                2 -> toggleTheme()
                3 -> setIncognito(!incognito)
                4 -> openVideoList()
                5 -> openInBrave()
            }
        }
    }

    // ---------- Brave Shields প্যানেল ----------
    private fun openShields() {
        val keys = listOf("adblock", "https_up", "cookies_3p", "fp", "cookie_notice")
        val labels = arrayOf(
            getString(R.string.br_shield_ads), getString(R.string.br_shield_https),
            getString(R.string.br_shield_cookies), getString(R.string.br_shield_fp),
            getString(R.string.br_shield_cookie_notice)
        )
        val p = AppSettings.prefs(this)
        val checked = BooleanArray(keys.size) { p.getBoolean(keys[it], true) }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.br_shields_title_fmt, blocked))
            .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton(R.string.br_shield_apply) { _, _ ->
                val e = p.edit()
                keys.forEachIndexed { i, k -> e.putBoolean(k, checked[i]) }
                e.apply()
                CookieManager.getInstance().setAcceptThirdPartyCookies(web, checked[2])
                updateShield()
                if (web.visibility == View.VISIBLE) web.reload()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun openInBrave() {
        val u = if (web.visibility == View.VISIBLE) web.url else null
        if (!Brave.open(this, u)) Toast.makeText(this, R.string.br_brave_missing, Toast.LENGTH_LONG).show()
    }

    // ---------- অ্যাড ব্লক ----------
    private fun toggleAdBlock(showCount: Boolean) {
        val now = !adOn()
        AppSettings.prefs(this).edit().putBoolean("adblock", now).apply()
        updateShield()
        if (showCount) {
            val msg = if (now) getString(R.string.br_blocked_fmt, blocked) else getString(R.string.br_adblock_off)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, if (now) R.string.br_adblock_on else R.string.br_adblock_off, Toast.LENGTH_SHORT).show()
        }
        if (web.visibility == View.VISIBLE) web.reload()
    }

    private fun updateShield() {
        val on = adOn()
        btnShield.setColorFilter(if (on) UiKit.accent(this) else UiKit.boxTextColor(this))
        btnShield.alpha = if (on) 1f else 0.5f
    }

    // ---------- সব সাইটে অ্যাপের থিম ----------
    private fun toggleTheme() {
        val now = !themeOn()
        AppSettings.prefs(this).edit().putBoolean("force_theme", now).apply()
        Toast.makeText(this, if (now) R.string.br_theme_on else R.string.br_theme_off, Toast.LENGTH_SHORT).show()
        if (web.visibility == View.VISIBLE) web.reload()
    }

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    private fun themeCss(): String {
        val bg = ContextCompat.getColor(this, R.color.bg)
        val alt = ContextCompat.getColor(this, R.color.surface_alt)
        val div = ContextCompat.getColor(this, R.color.divider)
        val text = UiKit.textColor(this)
        val acc = UiKit.accent(this)
        val dark = ColorUtils.calculateLuminance(bg) < 0.5
        return ":root{color-scheme:${if (dark) "dark" else "light"} !important}" +
            "html,body{background:${hex(bg)} !important;color:${hex(text)} !important}" +
            "*:not(img):not(video):not(canvas):not(svg):not(path):not(picture):not(iframe){" +
            "background-color:${hex(bg)} !important;color:${hex(text)} !important;border-color:${hex(div)} !important}" +
            "input,textarea,select,button{background-color:${hex(alt)} !important;color:${hex(text)} !important;" +
            "border-color:${hex(div)} !important}" +
            "a,a *{color:${hex(acc)} !important}" +
            "::placeholder{color:#8A8F9A !important}"
    }

    private fun styleJs(id: String, css: String) =
        "(function(){var s=document.getElementById('$id');if(!s){s=document.createElement('style');" +
            "s.id='$id';(document.head||document.documentElement).appendChild(s);}" +
            "s.textContent=${JSONObject.quote(css)};})();"

    private fun injectPage(v: WebView) {
        val sp = AppSettings.prefs(this)
        if (sp.getBoolean("fp", true)) v.evaluateJavascript(FP_JS, null)
        if (sp.getBoolean("cookie_notice", true)) v.evaluateJavascript(styleJs("hm-cookie", COOKIE_CSS), null)
        if (themeOn()) v.evaluateJavascript(styleJs("hm-theme", themeCss()), null)
        else v.evaluateJavascript("var s=document.getElementById('hm-theme');if(s)s.remove();", null)
        if (adOn()) {
            v.evaluateJavascript(styleJs("hm-adhide", AD_HIDE_CSS), null)
            val host = Uri.parse(v.url ?: "").host ?: ""
            if (host.contains("youtube.com")) v.evaluateJavascript(YT_SKIP_JS, null)
        }
    }

    // ---------- ইনকগনিটো ----------
    private fun setIncognito(on: Boolean) {
        incognito = on
        CookieManager.getInstance().setAcceptCookie(!on)
        web.settings.cacheMode = if (on) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        if (on) web.clearHistory()
        incognitoLabel.setText(if (on) R.string.br_incognito_on else R.string.br_incognito)
        incognitoPill.alpha = if (on) 1f else 0.85f
        Toast.makeText(this, if (on) R.string.br_incognito_on else R.string.br_incognito_off, Toast.LENGTH_SHORT).show()
    }

    // ---------- ভিডিও ডিটেক্ট + ডাউনলোড ----------
    private fun isMediaLink(u: String): Boolean {
        val path = Uri.parse(u).path?.lowercase() ?: return false
        return mediaExt.any { path.endsWith(it) }
    }

    private fun isStream(u: String): Boolean {
        val path = Uri.parse(u).path?.lowercase() ?: return false
        return path.endsWith(".m3u8") || path.endsWith(".mpd")
    }

    private fun addFound(u: String) {
        found.add(u)
        while (found.size > 25) found.remove(found.first())
        updateDl()
    }

    private fun updateDl() {
        val n = found.size
        dlBadge.visibility = if (n > 0) View.VISIBLE else View.GONE
        dlBadge.text = n.toString()
        btnDl.setColorFilter(if (n > 0) UiKit.accent(this) else UiKit.boxTextColor(this))
        btnDl.alpha = if (n > 0) 1f else 0.5f
    }

    private fun openVideoList() {
        if (web.visibility != View.VISIBLE) {
            Toast.makeText(this, R.string.br_no_video, Toast.LENGTH_SHORT).show()
            return
        }
        web.evaluateJavascript(SCAN_JS) { res ->
            try {
                val s = JSONTokener(res ?: "null").nextValue()
                if (s is String) s.split("\n").forEach { if (it.startsWith("http")) addFound(it) }
            } catch (e: Exception) { }
            showVideoList()
        }
    }

    private fun showVideoList() {
        if (found.isEmpty()) {
            Toast.makeText(this, R.string.br_no_video, Toast.LENGTH_LONG).show()
            return
        }
        val urls = found.toList().reversed()
        val labels = urls.map {
            val name = URLUtil.guessFileName(it, null, null)
            val host = Uri.parse(it).host ?: ""
            "$name\n$host"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.br_videos)
            .setItems(labels) { _, i -> askMedia(urls[i]) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(" ") || !t.contains(".") -> "https://search.brave.com/search?q=" + Uri.encode(t)
            else -> "https://$t"
        }
        showHome(false)
        web.loadUrl(upgrade(url), mapOf("Sec-GPC" to "1", "DNT" to "1"))
        urlBar.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    // Brave Shields: http লিংক https-এ আপগ্রেড (না চললে http-তে ফিরে যায়)
    private fun upgrade(url: String): String {
        if (!AppSettings.prefs(this).getBoolean("https_up", true) || !url.startsWith("http://")) return url
        val host = Uri.parse(url).host ?: return url
        if (host in noUpgrade) return url
        upgradedHost = host
        return "https://" + url.removePrefix("http://")
    }

    private fun reloadOrStop() {
        if (home.visibility == View.VISIBLE) return
        if (progress.visibility == View.VISIBLE) web.stopLoading() else web.reload()
    }

    private fun updateNav() {
        btnBack.alpha = if (web.canGoBack()) 1f else 0.4f
        btnFwd.alpha = if (web.canGoForward()) 1f else 0.4f
    }

    // সরাসরি ভিডিও/অডিও লিংক: চালান বা ডাউনলোড (স্ট্রিম হলে শুধু চালানো)
    private fun askMedia(url: String) {
        val stream = isStream(url)
        val b = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.br_video_found)
            .setMessage(if (stream) getString(R.string.br_stream_only) else url)
            .setPositiveButton(R.string.br_play) { _, _ ->
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_URI, url)
                        .putExtra(PlayerActivity.EXTRA_TITLE, URLUtil.guessFileName(url, null, null))
                )
            }
        if (!stream) b.setNegativeButton(R.string.br_download) { _, _ -> download(url, null, null, null) }
        b.setNeutralButton(R.string.btn_cancel, null).show()
    }

    private fun askDownload(url: String, ua: String?, cd: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, cd, mime)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.br_download)
            .setMessage(name)
            .setPositiveButton(R.string.br_download) { _, _ -> download(url, ua, cd, mime) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ডাউনলোড চলে নিজস্ব সার্ভিসে, Cloudflare DNS (1.1.1.1) ব্যবহার করে
    private fun download(url: String, ua: String?, cd: String?, mime: String?) {
        try {
            val i = Intent(this, DownloadService::class.java)
                .putExtra(DownloadService.EXTRA_URL, url)
                .putExtra(DownloadService.EXTRA_UA, ua ?: web.settings.userAgentString)
                .putExtra(DownloadService.EXTRA_COOKIE, CookieManager.getInstance().getCookie(url))
                .putExtra(DownloadService.EXTRA_REFERER, web.url)
                .putExtra(DownloadService.EXTRA_CD, cd)
                .putExtra(DownloadService.EXTRA_MIME, mime)
            ContextCompat.startForegroundService(this, i)
            Toast.makeText(this, R.string.dl_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }

    override fun onDestroy() {
        if (incognito) {
            web.clearCache(true)
            web.clearFormData()
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().setAcceptCookie(true)
        }
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val COOKIE_CSS =
            "#onetrust-banner-sdk,#onetrust-consent-sdk,.cookie-banner,.cookie-consent,#cookie-banner,#cookieConsent," +
                ".cc-window,.cc-banner,#CybotCookiebotDialog,.fc-consent-root,#didomi-host,.truste_box_overlay," +
                "[id*=\"cookie-banner\"],[class*=\"cookie-banner\"]{display:none !important}"

        // ফিঙ্গারপ্রিন্টিং কমানো: হার্ডওয়্যার তথ্য স্থির, ব্যাটারি API বন্ধ, ক্যানভাসে সামান্য নয়েজ
        private val FP_JS = """
            (function(){try{if(window.__hmFp)return;window.__hmFp=1;
            var d=function(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v},configurable:true})}catch(e){}};
            d(navigator,'hardwareConcurrency',4);d(navigator,'deviceMemory',4);d(navigator,'plugins',[]);
            if(navigator.getBattery){navigator.getBattery=function(){return Promise.reject(new DOMException('Blocked','NotAllowedError'))}}
            var tdu=HTMLCanvasElement.prototype.toDataURL;
            HTMLCanvasElement.prototype.toDataURL=function(){try{var c=this.getContext&&this.getContext('2d');
            if(c&&this.width>0&&this.height>0&&this.width<600){var p=c.getImageData(0,0,1,1);p.data[0]^=1;c.putImageData(p,0,0)}}catch(e){}
            return tdu.apply(this,arguments)};
            }catch(e){}})();
        """.trimIndent()

        private const val AD_HIDE_CSS =
            "ins.adsbygoogle,.adsbygoogle,[id^=\"google_ads\"],[id^=\"div-gpt-ad\"],iframe[src*=\"doubleclick\"]," +
                "iframe[src*=\"googlesyndication\"],iframe[id^=\"google_ads\"],#tads,#tadsb,#bottomads,[data-text-ad]," +
                ".ad-banner,.ad-container,.ad-slot,.ads-ad,.video-ads,.ytp-ad-module,ytd-ad-slot-renderer," +
                "ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,#player-ads,.ytp-ad-overlay-container," +
                "ytm-promoted-video-renderer{display:none !important}"

        private const val YT_SKIP_JS =
            "if(!window.__hmYt){window.__hmYt=1;setInterval(function(){" +
                "var p=document.querySelector('.html5-video-player.ad-showing');if(p){" +
                "var v=document.querySelector('video');if(v&&isFinite(v.duration)){v.currentTime=v.duration;}" +
                "var b=document.querySelector('.ytp-ad-skip-button,.ytp-skip-ad-button,.ytp-ad-skip-button-modern');" +
                "if(b)b.click();}},500);}"

        private const val SCAN_JS =
            "(function(){var o=[];document.querySelectorAll('video,audio,source').forEach(function(e){" +
                "[e.currentSrc,e.src].forEach(function(u){if(u&&u.indexOf('http')===0&&o.indexOf(u)<0)o.push(u);});});" +
                "return o.join('\\n');})()"
    }
}

// বিজ্ঞাপন/পপআপ নেটওয়ার্কের হোস্ট ও প্যাটার্ন
private object AdBlock {
    private val hosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "pagead2.googlesyndication.com", "adnxs.com", "taboola.com", "outbrain.com", "popads.net",
        "popcash.net", "propellerads.com", "exoclick.com", "juicyads.com", "adsterra.com", "mgid.com",
        "revcontent.com", "amazon-adsystem.com", "moatads.com", "pubmatic.com", "rubiconproject.com",
        "openx.net", "criteo.com", "criteo.net", "adform.net", "smartadserver.com", "advertising.com",
        "yieldmo.com", "adcolony.com", "unityads.unity3d.com", "applovin.com", "inmobi.com",
        "ads-twitter.com", "hotjar.com", "scorecardresearch.com", "2mdn.net", "adsrvr.org", "bidswitch.net",
        "casalemedia.com", "contextweb.com", "lijit.com", "sharethrough.com", "teads.tv", "zedo.com",
        "clickadu.com", "hilltopads.net", "trafficjunky.net", "onclickads.net", "a-ads.com"
    )
    private val parts = listOf(
        "/pagead/", "/adserver", "/ads/", "/adsbygoogle", "popunder", "/prebid", "/banner_ads", "/adframe"
    )

    fun isAd(url: String): Boolean {
        val u = Uri.parse(url)
        val host = (u.host ?: return false).lowercase()
        if (hosts.any { host == it || host.endsWith(".$it") }) return true
        if (host.startsWith("ads.") || host.contains(".ads.") || host.startsWith("adserver") ||
            host.contains("adservice")) return true
        val low = url.lowercase()
        return parts.any { low.contains(it) }
    }
}


// ব্রেভ অ্যাপ: ইনস্টল থাকলে সেটাই ডিফল্ট ব্রাউজার (থিম পেজ থেকে বন্ধ করা যায়)
object Brave {
    private val packages = listOf("com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly")

    fun pkg(c: android.content.Context): String? = packages.firstOrNull {
        try { c.packageManager.getPackageInfo(it, 0); true } catch (e: Exception) { false }
    }

    fun preferred(c: android.content.Context): Boolean = AppSettings.prefs(c).getBoolean("brave_default", true)

    fun open(c: android.content.Context, url: String?): Boolean {
        val p = pkg(c) ?: return false
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url ?: "https://search.brave.com")).setPackage(p)
            if (c !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
            true
        } catch (e: Exception) { false }
    }
}
