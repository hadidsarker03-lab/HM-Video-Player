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
    }

    private fun general(list: LinearLayout) {
        // ---- ডিসপ্লে ----
        UiKit.header(this, list, getString(R.string.sec_display))
        UiKit.switchRow(this, list, R.drawable.ic_video, getString(R.string.row_show_name),
            getString(R.string.row_show_name_sub), AppSettings.showName(this)) { AppSettings.setShowName(this, it) }
        UiKit.row(this, list, R.drawable.ic_rotate, getString(R.string.row_orientation),
            UiKit.orientationName(this)) { UiKit.chooseOrientation(this) { build() } }
        UiKit.switchRow(this, list, R.drawable.ic_rotate, getString(R.string.row_lock_orient),
            getString(R.string.row_lock_orient_sub), AppSettings.lockOrientation(this)) { AppSettings.setLockOrientation(this, it) }
        UiKit.row(this, list, R.drawable.ic_folder, getString(R.string.row_scan_list),
            getString(R.string.row_scan_list_sub)) { UiKit.manageScanList(this) }
        UiKit.switchRow(this, list, R.drawable.ic_history, getString(R.string.row_hide_history),
            getString(R.string.row_hide_history_sub), AppSettings.hideHistory(this)) { AppSettings.setHideHistory(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_video_display, getString(R.string.row_fixed_ratio),
            getString(R.string.row_fixed_ratio_sub), AppSettings.fixedRatio(this)) { AppSettings.setFixedRatio(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_info, getString(R.string.row_battery_time),
            getString(R.string.row_battery_time_sub), AppSettings.showBatteryTime(this)) { AppSettings.setShowBatteryTime(this, it) }

        // ---- প্লেব্যাক ----
        UiKit.header(this, list, getString(R.string.sec_playback))
        UiKit.switchRow(this, list, R.drawable.ic_play_circle, getString(R.string.row_yt_popup),
            getString(R.string.row_yt_popup_sub), AppSettings.youtubePopup(this)) { AppSettings.setYoutubePopup(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_info, getString(R.string.row_sub_custom),
            getString(R.string.row_sub_custom_sub), AppSettings.subtitleCustom(this)) { AppSettings.setSubtitleCustom(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_history, getString(R.string.row_resume),
            getString(R.string.row_resume_sub), AppSettings.resume(this)) { AppSettings.setResume(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_phone, getString(R.string.row_gestures),
            getString(R.string.row_gestures_sub), AppSettings.gestures(this)) { AppSettings.setGestures(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_next, getString(R.string.row_continuous),
            getString(R.string.row_continuous_sub), AppSettings.continuous(this)) { AppSettings.setContinuous(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_play_circle, getString(R.string.row_auto_popup),
            getString(R.string.row_auto_popup_sub), AppSettings.autoPopup(this)) { AppSettings.setAutoPopup(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_next, getString(R.string.row_dt_seek),
            getString(R.string.row_dt_seek_sub), AppSettings.doubleTapSeek(this)) { AppSettings.setDoubleTapSeek(this, it) }
        UiKit.row(this, list, R.drawable.ic_history, getString(R.string.row_seek_time),
            UiKit.seekSecondsName(this)) { UiKit.chooseSeekSeconds(this) { build() } }
        UiKit.switchRow(this, list, R.drawable.ic_next, getString(R.string.row_lp_boost),
            getString(R.string.row_lp_boost_sub), AppSettings.longPressBoost(this)) { AppSettings.setLongPressBoost(this, it) }
        UiKit.row(this, list, R.drawable.ic_history, getString(R.string.row_lp_speed),
            UiKit.longPressSpeedName(this)) { UiKit.chooseLongPressSpeed(this) { build() } }
        UiKit.switchRow(this, list, R.drawable.ic_phone, getString(R.string.row_lp_vibrate),
            getString(R.string.row_lp_vibrate_sub), AppSettings.longPressVibrate(this)) { AppSettings.setLongPressVibrate(this, it) }
        UiKit.switchRow(this, list, R.drawable.ic_video_display, getString(R.string.row_zoom_pan),
            getString(R.string.row_zoom_pan_sub), AppSettings.zoomPan(this)) { AppSettings.setZoomPan(this, it) }
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
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        fun head(res: Int) {
            val t = TextView(this)
            t.setText(res)
            t.textSize = 13f
            t.setTextColor(UiKit.accent(this))
            t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
            t.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 18), UiKit.dp(this, 20), UiKit.dp(this, 4))
            list.addView(t)
        }
        fun info(icon: Int, title: Int, msg: () -> String) {
            UiKit.row(this, list, icon, getString(title), null) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setMessage(msg())
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
        }
        fun link(icon: Int, title: Int, sub: Int, intent: () -> Intent) {
            UiKit.row(this, list, icon, getString(title), getString(sub)) {
                try {
                    startActivity(intent())
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, R.string.about_noapp, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        head(R.string.about_h_info)
        UiKit.row(this, list, R.drawable.ic_info, getString(R.string.app_name),
            getString(R.string.app_tagline), null)
        UiKit.row(this, list, R.drawable.ic_settings, getString(R.string.about_text, version), null, null)

        head(R.string.about_h_more)
        info(R.drawable.ic_play_circle, R.string.about_features) { getString(R.string.about_features_txt) }
        info(R.drawable.ic_playlist, R.string.about_new) { getString(R.string.about_new_txt) }

        head(R.string.about_h_contact)
        link(R.drawable.ic_browser, R.string.about_tg, R.string.about_tg_sub) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/hmfreeapps"))
        }
        link(R.drawable.ic_browser, R.string.about_gh, R.string.about_gh_sub) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/hm-official"))
        }
        link(R.drawable.ic_bulb, R.string.about_fb, R.string.about_fb_sub) {
            Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("hadidsarkeralif@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "HM player $version – feedback")
            }
        }
        UiKit.row(this, list, R.drawable.ic_share, getString(R.string.about_share), null) {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, getString(R.string.about_share_txt)), null))
        }

        head(R.string.about_h_legal)
        info(R.drawable.ic_shield, R.string.about_privacy) { getString(R.string.about_privacy_txt) }
        info(R.drawable.ic_lock, R.string.about_perms) { getString(R.string.about_perms_txt) }
        info(R.drawable.ic_queue, R.string.about_lic) { getString(R.string.about_lic_txt) }
        info(R.drawable.ic_favorite, R.string.about_credits) {
            val names = try {
                assets.open("credits.txt").bufferedReader().use { it.readText() }.lines().joinToString("\n") { "• $it" }
            } catch (e: Exception) { Dbg.w("SettingsActivity.link", e); "" }
            getString(R.string.about_credits_txt, names)
        }
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
