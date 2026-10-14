package com.example.videoplayer

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Me ট্যাবের ব্রাউজার: ঠিকানা/সার্চ বার, সামনে-পিছনে, রিলোড, ডাউনলোড, ভিডিও লিংক সরাসরি HM প্লেয়ারে চালানো
class BrowserActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var btnBack: ImageView
    private lateinit var btnFwd: ImageView
    private lateinit var btnReload: ImageView

    private val videoExt = listOf(".mp4", ".mkv", ".webm", ".m3u8", ".3gp", ".mov", ".avi", ".mp3", ".m4a")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        // ---- উপরের বার ----
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 6), UiKit.dp(this, 8), UiKit.dp(this, 6))

        val close = iconBtn(R.drawable.ic_back) { finish() }
        btnBack = iconBtn(R.drawable.ic_prev) { if (web.canGoBack()) web.goBack() }
        btnFwd = iconBtn(R.drawable.ic_next) { if (web.canGoForward()) web.goForward() }
        btnReload = iconBtn(R.drawable.ic_history) { reloadOrStop() }

        urlBar = EditText(this).apply {
            hint = getString(R.string.br_hint)
            setSingleLine()
            textSize = 14f
            setTextColor(UiKit.textColor(this@BrowserActivity))
            setHintTextColor(0x88888888.toInt())
            setBackgroundResource(R.drawable.bg_tab)
            setPadding(UiKit.dp(this@BrowserActivity, 16), UiKit.dp(this@BrowserActivity, 10),
                UiKit.dp(this@BrowserActivity, 16), UiKit.dp(this@BrowserActivity, 10))
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO ||
                    (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER)
                ) { go(text.toString()); true } else false
            }
        }
        top.addView(close)
        top.addView(urlBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(UiKit.dp(this@BrowserActivity, 4), 0, UiKit.dp(this@BrowserActivity, 4), 0) })
        top.addView(btnBack)
        top.addView(btnFwd)
        top.addView(btnReload)
        root.addView(top)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(UiKit.accent(this@BrowserActivity))
        }
        root.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 3)))

        // ---- ওয়েবভিউ ----
        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url.toString()
                if (!u.startsWith("http")) {
                    // অন্য অ্যাপের লিংক (mailto:, tel: ইত্যাদি)
                    try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (e: Exception) { }
                    return true
                }
                if (isMediaLink(u)) { askMedia(u); return true }
                return false
            }

            override fun onPageStarted(v: WebView, url: String, icon: Bitmap?) {
                urlBar.setText(url)
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(v: WebView, url: String) {
                progress.visibility = View.GONE
                updateNav()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.progress = p
                progress.visibility = if (p >= 100) View.GONE else View.VISIBLE
            }
        }
        web.setDownloadListener { url, ua, cd, mime, _ -> askDownload(url, ua, cd, mime) }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val frame = FrameLayout(this)
        frame.addView(root)
        frame.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
            this, R.attr.hmBg, 0xFFDFE4EC.toInt()))
        setContentView(frame)
        UiKit.applyInsets(this, frame, top, true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else go("https://www.google.com")
    }

    private fun iconBtn(icon: Int, action: () -> Unit) = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@BrowserActivity, 38), UiKit.dp(this@BrowserActivity, 38))
            .apply { setMargins(UiKit.dp(this@BrowserActivity, 2), 0, UiKit.dp(this@BrowserActivity, 2), 0) }
        setPadding(UiKit.dp(this@BrowserActivity, 8), UiKit.dp(this@BrowserActivity, 8),
            UiKit.dp(this@BrowserActivity, 8), UiKit.dp(this@BrowserActivity, 8))
        setImageResource(icon)
        setColorFilter(UiKit.textColor(this@BrowserActivity))
        setBackgroundResource(R.drawable.bg_icon_box)
        setOnClickListener { action() }
    }

    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(" ") || !t.contains(".") ->
                "https://www.google.com/search?q=" + Uri.encode(t)
            else -> "https://$t"
        }
        web.loadUrl(url)
        urlBar.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    private fun reloadOrStop() {
        if (progress.visibility == View.VISIBLE) web.stopLoading() else web.reload()
    }

    private fun updateNav() {
        btnBack.alpha = if (web.canGoBack()) 1f else 0.4f
        btnFwd.alpha = if (web.canGoForward()) 1f else 0.4f
    }

    private fun isMediaLink(u: String): Boolean {
        val path = Uri.parse(u).path?.lowercase() ?: return false
        return videoExt.any { path.endsWith(it) }
    }

    // সরাসরি ভিডিও/অডিও লিংক: চালান বা ডাউনলোড
    private fun askMedia(url: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.br_video_found)
            .setMessage(url)
            .setPositiveButton(R.string.br_play) { _, _ ->
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_URI, url)
                        .putExtra(PlayerActivity.EXTRA_TITLE, URLUtil.guessFileName(url, null, null))
                )
            }
            .setNegativeButton(R.string.br_download) { _, _ ->
                download(url, null, null, null)
            }
            .setNeutralButton(R.string.btn_cancel, null)
            .show()
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

    private fun download(url: String, ua: String?, cd: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val isVideo = mime?.startsWith("video") == true || videoExt.any { name.lowercase().endsWith(it) }
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_DOWNLOADS, name
                )
            if (!mime.isNullOrEmpty()) req.setMimeType(mime)
            if (!ua.isNullOrEmpty()) req.addRequestHeader("User-Agent", ua)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, R.string.br_downloading, Toast.LENGTH_SHORT).show()
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
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
