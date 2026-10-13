package com.example.videoplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// সেটিংস: প্রধান তালিকা এবং General / Video / Audio / About us
class SettingsActivity : AppCompatActivity() {

    private var section: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        UiKit.applyInsets(this, findViewById(R.id.root), findViewById(R.id.headerRow), true)
        section = intent.getStringExtra(EXTRA_SECTION)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        build()
    }

    private fun build() {
        val list = findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        val title = findViewById<TextView>(R.id.title)
        when (section) {
            SEC_GENERAL -> {
                title.setText(R.string.set_general)
                general(list)
            }
            SEC_VIDEO -> {
                title.setText(R.string.tab_video)
                video(list)
            }
            SEC_AUDIO -> {
                title.setText(R.string.set_audio)
                audio(list)
            }
            SEC_ABOUT -> {
                title.setText(R.string.me_about)
                about(list)
            }
            else -> {
                title.setText(R.string.tab_settings)
                main(list)
            }
        }
    }

    private fun open(sec: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_SECTION, sec))
    }

    private fun main(list: LinearLayout) {
        UiKit.row(this, list, R.drawable.ic_phone, getString(R.string.set_general),
            getString(R.string.set_general_sub)) { open(SEC_GENERAL) }
        UiKit.row(this, list, R.drawable.ic_play_circle, getString(R.string.tab_video),
            getString(R.string.set_video_sub)) { open(SEC_VIDEO) }
        UiKit.row(this, list, R.drawable.ic_headset, getString(R.string.set_audio),
            getString(R.string.set_audio_sub)) { open(SEC_AUDIO) }
        UiKit.row(this, list, R.drawable.ic_info, getString(R.string.me_about),
            getString(R.string.set_about_sub)) { open(SEC_ABOUT) }
        UiKit.row(this, list, R.drawable.ic_lock, getString(R.string.privacy),
            getString(R.string.privacy_sub)) {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
    }

    private fun general(list: LinearLayout) {
        UiKit.row(this, list, R.drawable.ic_language, getString(R.string.settings_language),
            UiKit.languageName(this)) { UiKit.chooseLanguage(this) { build() } }
        UiKit.row(this, list, R.drawable.ic_palette, getString(R.string.settings_theme),
            UiKit.themeName(this)) { UiKit.chooseTheme(this) { build() } }
        UiKit.row(this, list, R.drawable.ic_view_grid, getString(R.string.settings_layout),
            UiKit.layoutName(this)) { UiKit.chooseLayout(this) { build() } }
    }

    private fun video(list: LinearLayout) {
        UiKit.row(this, list, R.drawable.ic_rotate, getString(R.string.row_orientation),
            UiKit.orientationName(this)) { UiKit.chooseOrientation(this) { build() } }
        UiKit.switchRow(this, list, R.drawable.ic_next, getString(R.string.row_continuous),
            getString(R.string.row_continuous_sub), AppSettings.continuous(this)) { on ->
            AppSettings.setContinuous(this, on)
        }
        UiKit.switchRow(this, list, R.drawable.ic_history, getString(R.string.row_resume),
            getString(R.string.row_resume_sub), AppSettings.resume(this)) { on ->
            AppSettings.setResume(this, on)
        }
        UiKit.switchRow(this, list, R.drawable.ic_play_circle, getString(R.string.settings_background),
            getString(R.string.row_bg_sub), AppSettings.backgroundPlay(this)) { on ->
            AppSettings.setBackgroundPlay(this, on)
        }
    }

    private fun audio(list: LinearLayout) {
        UiKit.switchRow(this, list, R.drawable.ic_headset, getString(R.string.settings_background),
            getString(R.string.row_bg_sub), AppSettings.backgroundPlay(this)) { on ->
            AppSettings.setBackgroundPlay(this, on)
        }
        UiKit.switchRow(this, list, R.drawable.ic_audio, getString(R.string.row_hide_short),
            getString(R.string.row_hide_short_sub), AppSettings.hideShort(this)) { on ->
            AppSettings.setHideShort(this, on)
        }
    }

    private fun about(list: LinearLayout) {
        UiKit.row(this, list, R.drawable.ic_info, getString(R.string.app_name),
            getString(R.string.app_tagline), null)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        UiKit.row(this, list, R.drawable.ic_settings, getString(R.string.about_text, version), null, null)
    }

    companion object {
        private const val EXTRA_SECTION = "section"
        private const val SEC_GENERAL = "general"
        private const val SEC_VIDEO = "video"
        private const val SEC_AUDIO = "audio"
        private const val SEC_ABOUT = "about"

        fun open(ctx: Context, section: String? = null) {
            ctx.startActivity(Intent(ctx, SettingsActivity::class.java).apply {
                if (section != null) putExtra(EXTRA_SECTION, section)
            })
        }

        fun openAbout(ctx: Context) = open(ctx, SEC_ABOUT)
    }
}
