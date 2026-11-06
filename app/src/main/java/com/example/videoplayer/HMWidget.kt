package com.example.videoplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

// হোম স্ক্রিন উইজেট: "ভিডিও" চাপলে অ্যাপ খোলে, "স্ট্রিম" চাপলে সরাসরি লিংক দেওয়ার ডায়ালগ খোলে।
class HMWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val base = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, 11, Intent(base), flags)
        val stream = PendingIntent.getActivity(
            context, 12, Intent(base).putExtra("open_stream", true), flags
        )
        for (id in ids) {
            val v = RemoteViews(context.packageName, R.layout.widget_hm)
            v.setOnClickPendingIntent(R.id.wOpen, open)
            v.setOnClickPendingIntent(R.id.wStream, stream)
            manager.updateAppWidget(id, v)
        }
    }
}
