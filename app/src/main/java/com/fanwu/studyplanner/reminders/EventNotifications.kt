package com.fanwu.studyplanner.reminders

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fanwu.studyplanner.MainActivity
import com.fanwu.studyplanner.R
import com.fanwu.studyplanner.domain.*

class EventNotifications(private val context: Context) : ReminderDelivery {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun available(): Boolean {
        ensureChannel()
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "日程开始提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "学习任务、考试与课程的开始提醒"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }

    override fun notify(event: CalendarEvent, spec: ReminderSpec) {
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("studyplanner://event/${event.id}/${spec.occurrence}"))
            .putExtra(OPEN_EVENT, event.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val target = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val type = when (event.type) { EventType.CLASS -> "课程"; EventType.EXAM -> "考试"; else -> "学习任务" }
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(context.getString(R.string.calendar_reminder_public_title, context.getString(R.string.app_name)))
            .setContentText("打开应用查看安排").build()
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle("$type · ${event.title}")
            .setContentText("${event.date} ${event.startTime} 开始")
            .setContentIntent(target).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_EVENT).build()
        try { manager.notify(reminderTag(event.id, spec.occurrence), 0, notification) }
        catch (_: SecurityException) { /* 权限可能在检查后被撤销，不自动重发。 */ }
    }

    override fun retain(tags: Set<String>) {
        manager.activeNotifications.filter { it.tag?.startsWith("event:") == true && it.tag !in tags }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    companion object {
        const val CHANNEL = "event_reminders"
        const val OPEN_EVENT = "open_calendar_event"
    }
}
