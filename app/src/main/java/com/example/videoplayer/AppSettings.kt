package com.example.videoplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppSettings {

    // লিস্টের ভিউ মোড
    const val VIEW_LIST = 0
    const val VIEW_GRID = 1
    const val VIEW_COMPACT = 2

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // প্রথমবার প্লেয়ারে ঢুকলে জেসচার টিউটোরিয়াল একবারই দেখানো হয়
    fun tutorialSeen(c: Context): Boolean = prefs(c).getBoolean("tutorial_seen", false)

    fun setTutorialSeen(c: Context) {
        prefs(c).edit().putBoolean("tutorial_seen", true).apply()
    }

    // ভাষা (ডিফল্ট: ইংলিশ)
    fun lang(c: Context): String = prefs(c).getString("lang", "en") ?: "en"

    fun setLang(c: Context, code: String) {
        prefs(c).edit().putString("lang", code).apply()
        applyLocale(code)
    }

    fun applyLocale(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    // থিম: system / light / dark (ডিফল্ট: system)
    fun themeMode(c: Context): String = prefs(c).getString("theme", "system") ?: "system"

    fun setThemeMode(c: Context, mode: String) {
        prefs(c).edit().putString("theme", mode).apply()
        AppCompatDelegate.setDefaultNightMode(nightModeFor(mode, c))
    }

    // ফার (পশমি) থিম স্থায়ীভাবে বন্ধ (সরানো হয়েছে)
    @Suppress("UNUSED_PARAMETER")
    fun fur(c: Context): Boolean = false

    fun setFur(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("fur", on).apply()
        AppCompatDelegate.setDefaultNightMode(nightModeFor(themeMode(c), c))
    }

    fun nightModeFor(mode: String, c: Context? = null): Int = if (c != null && fur(c)) AppCompatDelegate.MODE_NIGHT_YES else when (mode) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    // ট্রান্সপারেন্ট (গ্লাস) ইন্টারফেস (ডিফল্ট: চালু)
    fun glass(c: Context): Boolean = prefs(c).getBoolean("glass", false)

    fun setGlass(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("glass", on).apply()
    }

    fun themeRes(c: Context): Int =
        if (fur(c)) R.style.Theme_HMPlayer_Fur
        else if (glass(c)) R.style.Theme_HMPlayer_Glass else R.style.Theme_HMPlayer

    // টেক্সট রং: 0 = ডিফল্ট, বাকিগুলো প্যালেটের নামি রং (ওভারলে স্টাইল)
    val textStyles = intArrayOf(
        0,
        R.style.TextColor_Denim,
        R.style.TextColor_Apricot,
        R.style.TextColor_Sand,
        R.style.TextColor_Coral,
        R.style.TextColor_Ember,
        R.style.TextColor_Periwinkle,
        R.style.TextColor_Petal,
        R.style.TextColor_Sunset,
        R.style.TextColor_Chestnut,
        R.style.TextColor_Peach,
        R.style.TextColor_Linen,
        R.style.TextColor_Spruce,
        R.style.TextColor_Ocean,
        R.style.TextColor_Lime,
        R.style.TextColor_Forest,
        R.style.TextColor_Orchid,
        R.style.TextColor_Mocha
    )
    val textColors = intArrayOf(
        0,
        0xFF6579BE.toInt(),
        0xFFEAB099.toInt(),
        0xFFE9DFD8.toInt(),
        0xFFEC654A.toInt(),
        0xFFF54800.toInt(),
        0xFFAFAFDA.toInt(),
        0xFFFDF1F5.toInt(),
        0xFFEE8E46.toInt(),
        0xFF8A6729.toInt(),
        0xFFEBC8B3.toInt(),
        0xFFECE7E2.toInt(),
        0xFF4A7766.toInt(),
        0xFF19485F.toInt(),
        0xFFD9E0A4.toInt(),
        0xFF285B23.toInt(),
        0xFFF2CFF1.toInt(),
        0xFF92736C.toInt()
    )
    val textNames = arrayOf("", "Denim", "Apricot", "Sand", "Coral", "Ember", "Periwinkle", "Petal", "Sunset", "Chestnut", "Peach", "Linen", "Spruce", "Ocean", "Lime", "Forest", "Orchid", "Mocha")

    // নতুন তালিকা চালুর পর পুরনো পছন্দের নম্বর মিলত না, তাই আলাদা চাবি (পুরনোটা নিজে ডিফল্টে ফিরবে)
    fun textIndex(c: Context): Int =
        prefs(c).getInt("text_color_v2", 0).coerceIn(0, textStyles.size - 1)

    fun setTextIndex(c: Context, i: Int) {
        prefs(c).edit().putInt("text_color_v2", i).apply()
    }

    // বর্তমান ব্যাকগ্রাউন্ডের আনুমানিক রং (হালকা/গাঢ় মোড বা ওয়ালপেপার অনুযায়ী)
    fun effectiveBg(c: Context): Int {
        if (!fur(c) && !glass(c) && Backgrounds.hasWallpaper(c)) {
            return if (Backgrounds.isLight(c, Backgrounds.selected(c))) 0xFFE8EAEF.toInt() else 0xFF1E2026.toInt()
        }
        val night = when (themeMode(c)) {
            "light" -> false
            "dark" -> true
            else -> (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        return if (night) 0xFF24272D.toInt() else 0xFFDFE4EC.toInt()
    }

    // এই লেখার রং বর্তমান ব্যাকগ্রাউন্ডে পড়া যায় কি? (হালকা থিমে সাদা/হালকা রং হারিয়ে যায়)
    fun textColorUsable(c: Context, color: Int): Boolean =
        androidx.core.graphics.ColorUtils.calculateContrast(color or 0xFF000000.toInt(), effectiveBg(c)) >= 2.5

    fun applyTheme(act: android.app.Activity) {
        act.setTheme(themeRes(act))
        // ওয়ালপেপার থিম বাছা থাকলে: সব বক্স স্বচ্ছ + ছবির উজ্জ্বলতা অনুযায়ী লেখার রং (আগে বসে)
        if (!fur(act) && !glass(act) && Backgrounds.hasWallpaper(act) && act !is PlayerActivity) {
            act.theme.applyStyle(R.style.WallpaperOverlay, true)
            val light = Backgrounds.isLight(act, Backgrounds.selected(act))
            act.theme.applyStyle(if (light) R.style.WallpaperTextOnLight else R.style.WallpaperTextOnDark, true)
        }
        // ইউজারের বাছা টেক্সট রং সবার শেষে (এটাই জেতে)
        // পড়া যায় না এমন রং (যেমন হালকা থিমে সাদা) বসানো হয় না, থিমের নিজের রংই থাকে
        val idx = textIndex(act)
        val st = textStyles[idx]
        if (st != 0 && textColorUsable(act, textColors[idx])) act.theme.applyStyle(st, true)
    }

    // ব্যাকগ্রাউন্ডে চালানো (ডিফল্ট: চালু)
    fun backgroundPlay(c: Context): Boolean = prefs(c).getBoolean("bg_play", true)

    fun setBackgroundPlay(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("bg_play", on).apply()
    }

    // সাজানোর ধরন: 0 নতুন আগে, 1 পুরনো আগে, 2 নাম A-Z, 3 নাম Z-A, 4 বড় সাইজ, 5 ছোট সাইজ, 6 বেশি সময়, 7 কম সময়
    fun sort(c: Context): Int = prefs(c).getInt("sort", 0).coerceIn(0, 7)

    fun setSort(c: Context, v: Int) {
        prefs(c).edit().putInt("sort", v).apply()
    }

    // ডিফল্ট স্ক্রিন ওরিয়েন্টেশন (প্লেয়ারের সাথে একই কী): 0 অটো, 1 ল্যান্ডস্কেপ, 2 পোর্ট্রেট
    fun rotation(c: Context): Int = prefs(c).getInt("rotation", 0).coerceIn(0, 2)

    fun setRotation(c: Context, v: Int) {
        prefs(c).edit().putInt("rotation", v).apply()
    }

    // ক্রমাগত চালানো, আগের জায়গা থেকে শুরু, ছোট অডিও লুকানো
    fun continuous(c: Context): Boolean = prefs(c).getBoolean("continuous", true)

    fun setContinuous(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("continuous", on).apply()
    }

    fun resume(c: Context): Boolean = prefs(c).getBoolean("resume", true)

    fun setResume(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("resume", on).apply()
    }

    // ইতিহাস লুকানো: ভিডিও ট্যাবের স্ট্রিপ ও Me-এর History আইকন দেখায় না (রেকর্ড চলতে থাকে)
    fun hideHistory(c: Context): Boolean = prefs(c).getBoolean("hide_history", false)

    fun setHideHistory(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("hide_history", on).apply()
    }

    fun hideShort(c: Context): Boolean = prefs(c).getBoolean("hide_short", false)

    fun setHideShort(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("hide_short", on).apply()
    }

    // ---------- জেনারেল > ডিসপ্লে / প্লেব্যাক ----------
    fun showName(c: Context): Boolean = prefs(c).getBoolean("show_name", true)
    fun setShowName(c: Context, on: Boolean) = prefs(c).edit().putBoolean("show_name", on).apply()

    fun lockOrientation(c: Context): Boolean = prefs(c).getBoolean("lock_orient", false)
    fun setLockOrientation(c: Context, on: Boolean) = prefs(c).edit().putBoolean("lock_orient", on).apply()

    fun fixedRatio(c: Context): Boolean = prefs(c).getBoolean("fixed_ratio", false)
    fun setFixedRatio(c: Context, on: Boolean) = prefs(c).edit().putBoolean("fixed_ratio", on).apply()

    fun showBatteryTime(c: Context): Boolean = prefs(c).getBoolean("battery_time", false)
    fun setShowBatteryTime(c: Context, on: Boolean) = prefs(c).edit().putBoolean("battery_time", on).apply()

    fun hiddenFolders(c: Context): Set<String> =
        (prefs(c).getString("hidden_folders", "") ?: "").split("\n").filter { it.isNotEmpty() }.toSet()
    fun setHiddenFolders(c: Context, f: Set<String>) =
        prefs(c).edit().putString("hidden_folders", f.joinToString("\n")).apply()

    fun youtubePopup(c: Context): Boolean = prefs(c).getBoolean("yt_popup", false)
    fun setYoutubePopup(c: Context, on: Boolean) = prefs(c).edit().putBoolean("yt_popup", on).apply()

    fun subtitleCustom(c: Context): Boolean = prefs(c).getBoolean("sub_custom", true)
    fun setSubtitleCustom(c: Context, on: Boolean) = prefs(c).edit().putBoolean("sub_custom", on).apply()

    fun gestures(c: Context): Boolean = prefs(c).getBoolean("gestures", true)
    fun setGestures(c: Context, on: Boolean) = prefs(c).edit().putBoolean("gestures", on).apply()

    fun autoPopup(c: Context): Boolean = prefs(c).getBoolean("auto_popup", false)
    fun setAutoPopup(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto_popup", on).apply()

    fun doubleTapSeek(c: Context): Boolean = prefs(c).getBoolean("dt_seek", true)
    fun setDoubleTapSeek(c: Context, on: Boolean) = prefs(c).edit().putBoolean("dt_seek", on).apply()

    // ডাবল ট্যাপ সিক সময় (সেকেন্ড, ডিফল্ট ১০)
    fun seekSeconds(c: Context): Int = prefs(c).getInt("dt_seek_sec", 10).coerceIn(1, 120)
    fun setSeekSeconds(c: Context, v: Int) = prefs(c).edit().putInt("dt_seek_sec", v).apply()

    fun longPressBoost(c: Context): Boolean = prefs(c).getBoolean("lp_boost", true)
    fun setLongPressBoost(c: Context, on: Boolean) = prefs(c).edit().putBoolean("lp_boost", on).apply()

    // লং প্রেস স্পিড (ডিফল্ট 2.0x)
    fun longPressSpeed(c: Context): Float = prefs(c).getFloat("lp_speed", 2.0f).coerceIn(1.1f, 4f)
    fun setLongPressSpeed(c: Context, v: Float) = prefs(c).edit().putFloat("lp_speed", v).apply()

    fun longPressVibrate(c: Context): Boolean = prefs(c).getBoolean("lp_vibrate", true)
    fun setLongPressVibrate(c: Context, on: Boolean) = prefs(c).edit().putBoolean("lp_vibrate", on).apply()

    fun zoomPan(c: Context): Boolean = prefs(c).getBoolean("zoom_pan", true)
    fun setZoomPan(c: Context, on: Boolean) = prefs(c).edit().putBoolean("zoom_pan", on).apply()

    // ভিউ মোড: list / grid / compact
    fun viewMode(c: Context): Int = prefs(c).getInt("view_mode", VIEW_LIST).coerceIn(0, 2)

    fun setViewMode(c: Context, mode: Int) {
        prefs(c).edit().putInt("view_mode", mode).apply()
    }

    // ---------- প্লেয়ার পেজের নতুন সেটিংস ----------
    // শর্টকাট সারি দেখানো হবে কি না (ডিফল্ট: হ্যাঁ)
    // ভিডিও ডিকোডার: 0 = HW, 1 = HW+
    const val HW = 0
    const val HW_PLUS = 1
    fun hwMode(c: Context): Int = prefs(c).getInt("hw_mode", HW)
    fun setHwMode(c: Context, m: Int) = prefs(c).edit().putInt("hw_mode", m).apply()

    fun shortcutsOn(c: Context): Boolean = prefs(c).getBoolean("sc_on", true)

    fun setShortcutsOn(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("sc_on", on).apply()
    }

    // লুকানো শর্টকাটের কী-গুলো (কমা দিয়ে আলাদা)
    fun hiddenShortcuts(c: Context): Set<String> =
        (prefs(c).getString("sc_hidden", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

    fun setHiddenShortcuts(c: Context, keys: Set<String>) {
        prefs(c).edit().putString("sc_hidden", keys.joinToString(",")).apply()
    }

    // ভিডিও দেখানো (বন্ধ করলে শুধু শব্দ চলে)
    fun videoDisplay(c: Context): Boolean = prefs(c).getBoolean("video_display", true)

    fun setVideoDisplay(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("video_display", on).apply()
    }

    // ইকুয়ালাইজার ও অডিও ইফেক্ট
    fun eqOn(c: Context): Boolean = prefs(c).getBoolean("eq_on", false)

    fun setEqOn(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("eq_on", on).apply()
    }

    fun eqLevels(c: Context): List<Int> =
        (prefs(c).getString("eq_levels", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }

    fun setEqLevels(c: Context, levels: List<Int>) {
        prefs(c).edit().putString("eq_levels", levels.joinToString(",")).apply()
    }

    // বাস বুস্ট / ভার্চুয়ালাইজার: 0..1000, লাউডনেস: 0..1500 (মিলিবেল)
    fun fx(c: Context, key: String): Int = prefs(c).getInt("fx_$key", 0)

    fun setFx(c: Context, key: String, v: Int) {
        prefs(c).edit().putInt("fx_$key", v).apply()
    }

    // নাইট মোড (ডিম) মনে রাখা
    fun nightMode(c: Context): Boolean = prefs(c).getBoolean("night_dim", false)

    fun setNightMode(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("night_dim", on).apply()
    }

    // প্লেয়ারের সেটিংস রিসেট
    fun resetPlayer(c: Context) {
        prefs(c).edit()
            .remove("sc_on").remove("sc_hidden").remove("video_display").remove("night_dim")
            .remove("aspect2").remove("eq_on").remove("eq_levels")
            .remove("fx_bass").remove("fx_virt").remove("fx_loud")
            .apply()
        AudioFx.applyAll(c)
    }
}
