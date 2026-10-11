package com.example.videoplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppSettings {

    // থিমের অ্যাকসেন্ট রং (Theme.HMPlayer.Accent0..5 এর সাথে মিলিয়ে)
    val ACCENTS = intArrayOf(
        0xFFFF5A5F.toInt(), 0xFF3B82F6.toInt(), 0xFF22C55E.toInt(),
        0xFF8B5CF6.toInt(), 0xFFF59E0B.toInt(), 0xFFEC4899.toInt()
    )

    private val ACCENT_STYLES = intArrayOf(
        R.style.Theme_HMPlayer_Accent0, R.style.Theme_HMPlayer_Accent1,
        R.style.Theme_HMPlayer_Accent2, R.style.Theme_HMPlayer_Accent3,
        R.style.Theme_HMPlayer_Accent4, R.style.Theme_HMPlayer_Accent5
    )

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

    // থিম মোড: system / light / dark (ডিফল্ট: dark)
    fun themeMode(c: Context): String = prefs(c).getString("theme", "dark") ?: "dark"

    fun setThemeMode(c: Context, mode: String) {
        prefs(c).edit().putString("theme", mode).apply()
        AppCompatDelegate.setDefaultNightMode(nightModeFor(mode))
    }

    fun nightModeFor(mode: String): Int = when (mode) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_YES
    }

    // অ্যাকসেন্ট রং
    fun accent(c: Context): Int = prefs(c).getInt("accent", 0).coerceIn(0, ACCENTS.size - 1)

    fun setAccent(c: Context, index: Int) {
        prefs(c).edit().putInt("accent", index).apply()
    }

    fun accentStyle(c: Context): Int = ACCENT_STYLES[accent(c)]
}
