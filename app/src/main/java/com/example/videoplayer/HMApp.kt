package com.example.videoplayer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class HMApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            AppSettings.nightModeFor(AppSettings.themeMode(this))
        )
        AppSettings.applyLocale(AppSettings.lang(this))
    }
}
