package com.example.videoplayer

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

class HMApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            AppSettings.nightModeFor(AppSettings.themeMode(this), this)
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

// অ্যাপ-জুড়ে ব্যাকগ্রাউন্ড কাজের স্কোপ: স্ক্রিন বন্ধ হলেও যে কাজ শেষ হওয়া দরকার (ফাইল সেভ, কনভার্ট, সার্ভার)।
// স্ক্রিনের সাথে বাঁধা কাজের জন্য lifecycleScope ব্যবহার হয়। কোনো কাজ ব্যর্থ হলে অ্যাপ ক্র্যাশ না করে লগে লেখে।
object AppScope : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Dbg.w("AppScope", e) }
}

// নীরবে গিলে ফেলা ব্যতিক্রম এখন লগে থাকে (adb logcat -s HMPlayer)
object Dbg {
    fun w(where: String, e: Throwable) {
        android.util.Log.w("HMPlayer", where + ": " + e)
    }
}
