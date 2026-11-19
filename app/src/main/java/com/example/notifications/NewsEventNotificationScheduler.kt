package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.domain.model.EconomicCalendarEvent

class NewsEventNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: return
        val currency = intent.getStringExtra(EXTRA_CURRENCY).orEmpty()
        val impact = intent.getStringExtra(EXTRA_IMPACT).orEmpty()
        val eventTime = intent.getLongExtra(EXTRA_EVENT_TIME, 0L)
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context, 7401,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$impact impact: $currency")
            .setContentText("$title starts in 10 minutes")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title ($currency) is scheduled for ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(eventTime))}."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(eventTime.hashCode(), notification) }
    }

    companion object {
        const val CHANNEL_ID = "tradepulse_news_events"
        const val EXTRA_TITLE = "news_title"
        const val EXTRA_CURRENCY = "news_currency"
        const val EXTRA_IMPACT = "news_impact"
        const val EXTRA_EVENT_TIME = "news_event_time"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Economic News Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                            description = "Alerts ten minutes before low, medium and high impact economic events"
                        }
                    )
                }
            }
        }
    }
}

object NewsEventNotificationScheduler {
    private const val LEAD_TIME_MS = 10 * 60 * 1000L

    fun sync(context: Context, events: List<EconomicCalendarEvent>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        NewsEventNotificationReceiver.ensureChannel(context)
        events.forEach { event ->
            val triggerAt = event.timestamp - LEAD_TIME_MS
            val impact = event.impact.trim().lowercase()
            if (impact !in setOf("low", "medium", "high") || triggerAt <= System.currentTimeMillis()) return@forEach
            val pendingIntent = PendingIntent.getBroadcast(
                context, event.id.hashCode(),
                Intent(context, NewsEventNotificationReceiver::class.java).apply {
                    putExtra(NewsEventNotificationReceiver.EXTRA_TITLE, event.title)
                    putExtra(NewsEventNotificationReceiver.EXTRA_CURRENCY, event.currency)
                    putExtra(NewsEventNotificationReceiver.EXTRA_IMPACT, event.impact)
                    putExtra(NewsEventNotificationReceiver.EXTRA_EVENT_TIME, event.timestamp)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }
}
