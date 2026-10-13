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
        AppCompatDelegate.setDefaultNightMode(nightModeFor(mode))
    }

    fun nightModeFor(mode: String): Int = when (mode) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    // ট্রান্সপারেন্ট (গ্লাস) ইন্টারফেস (ডিফল্ট: চালু)
    fun glass(c: Context): Boolean = prefs(c).getBoolean("glass", true)

    fun setGlass(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("glass", on).apply()
    }

    fun themeRes(c: Context): Int =
        if (glass(c)) R.style.Theme_HMPlayer_Glass else R.style.Theme_HMPlayer

    // অ্যাকসেন্ট রং: 0 = ডিফল্ট, বাকিগুলো ওভারলে স্টাইল
    val accentStyles = intArrayOf(
        0, R.style.Accent_Blue, R.style.Accent_Pink,
        R.style.Accent_Orange, R.style.Accent_Purple, R.style.Accent_Red
    )
    val accentColors = intArrayOf(
        0, 0xFF2D8CFF.toInt(), 0xFFD946C8.toInt(),
        0xFFFF7A3D.toInt(), 0xFF8B5CF6.toInt(), 0xFFEF4444.toInt()
    )

    fun accentIndex(c: Context): Int =
        prefs(c).getInt("accent", 0).coerceIn(0, accentStyles.size - 1)

    fun setAccentIndex(c: Context, i: Int) {
        prefs(c).edit().putInt("accent", i).apply()
    }

    fun applyTheme(act: android.app.Activity) {
        act.setTheme(themeRes(act))
        val st = accentStyles[accentIndex(act)]
        if (st != 0) act.theme.applyStyle(st, true)
        // সাধারণ মোডে ছবি ব্যাকগ্রাউন্ড বাছা থাকলে পেজের সমতল রং স্বচ্ছ করে
        if (!glass(act) && Backgrounds.hasWallpaper(act) && act !is PlayerActivity) {
            act.theme.applyStyle(R.style.WallpaperOverlay, true)
        }
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

    fun hideShort(c: Context): Boolean = prefs(c).getBoolean("hide_short", false)

    fun setHideShort(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("hide_short", on).apply()
    }

    // ভিউ মোড: list / grid / compact
    fun viewMode(c: Context): Int = prefs(c).getInt("view_mode", VIEW_LIST).coerceIn(0, 2)

    fun setViewMode(c: Context, mode: Int) {
        prefs(c).edit().putInt("view_mode", mode).apply()
    }

    // ---------- প্লেয়ার পেজের নতুন সেটিংস ----------
    // শর্টকাট সারি দেখানো হবে কি না (ডিফল্ট: হ্যাঁ)
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
