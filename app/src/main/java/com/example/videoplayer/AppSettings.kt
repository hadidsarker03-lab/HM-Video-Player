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

    // ব্যাকগ্রাউন্ডে চালানো (ডিফল্ট: চালু)
    fun backgroundPlay(c: Context): Boolean = prefs(c).getBoolean("bg_play", true)

    fun setBackgroundPlay(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("bg_play", on).apply()
    }

    // ভিউ মোড: list / grid / compact
    fun viewMode(c: Context): Int = prefs(c).getInt("view_mode", VIEW_LIST).coerceIn(0, 2)

    fun setViewMode(c: Context, mode: Int) {
        prefs(c).edit().putInt("view_mode", mode).apply()
    }
}
