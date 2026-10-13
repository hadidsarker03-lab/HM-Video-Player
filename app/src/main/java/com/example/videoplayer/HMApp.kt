package com.example.videoplayer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

class HMApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            AppSettings.nightModeFor(AppSettings.themeMode(this))
        )
        AppSettings.applyLocale(AppSettings.lang(this))
        // প্রতিটি স্ক্রিনের আইকনে প্রেস-অ্যানিমেশন
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                Backgrounds.apply(a)
                val d = a.window.decorView
                d.post { Anim.attachTree(d) }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {
                Backgrounds.apply(a)
            }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
