package com.fanwu.studyplanner.focus

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fanwu.studyplanner.MainActivity
import com.fanwu.studyplanner.R
import com.fanwu.studyplanner.domain.*

object FocusNotifications {
    const val ID = 4101
    private const val CHANNEL = "focus_timer"
    private const val ALERT_CHANNEL = "focus_completion"
    const val OPEN_FOCUS = "open_focus"

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "专注计时", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "专注与休息完成", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun build(context: Context, session: FocusSession? = null, alert: Boolean = false): Notification {
        createChannels(context)
        val content = PendingIntent.getActivity(context, 4101,
            Intent(context, MainActivity::class.java).putExtra(OPEN_FOCUS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, if (alert) ALERT_CHANNEL else CHANNEL)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentIntent(content).setOnlyAlertOnce(!alert)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(session?.phase?.isRunning == true)
        if (session == null) return builder.setContentTitle("正在恢复专注计时…").build()
        val remaining = FocusTimer.remainingMs(session, com.fanwu.studyplanner.data.repository.AndroidFocusClock(context).now())
        val title = when (session.phase) {
            FocusPhase.FOCUS_RUNNING -> "正在专注"
            FocusPhase.FOCUS_PAUSED -> "专注已暂停"
            FocusPhase.FOCUS_READY -> "本轮专注完成"
            FocusPhase.BREAK_RUNNING -> "正在休息"
            FocusPhase.BREAK_PAUSED -> "休息已暂停"
            FocusPhase.FINISHED -> "本轮已结束"
            FocusPhase.ABANDONED -> "本轮提前结束"
        }
        builder.setContentTitle(title)
            .setContentText(if (session.phase.isRunning || session.phase.isPaused) "剩余 ${formatFocusTime(remaining)} · 点击返回专注空间"
                else "已专注 ${formatFocusDuration(session.focusedMs)} · 点击查看记录")
        if (session.phase.isRunning) {
            builder.setWhen(System.currentTimeMillis() + remaining).setUsesChronometer(true).setChronometerCountDown(true)
            builder.addAction(0, "暂停", command(context, session.id, FocusAction.PAUSE))
        } else if (session.phase.isPaused) {
            builder.addAction(0, "继续", command(context, session.id, FocusAction.RESUME))
        } else if (session.phase == FocusPhase.FOCUS_READY && session.breakDurationMs > 0) {
            builder.addAction(0, "开始休息", command(context, session.id, FocusAction.START_BREAK))
        }
        if (!session.phase.isTerminal) builder.addAction(0, "结束并保存", command(context, session.id, FocusAction.FINISH))
        return builder.build()
    }

    private fun command(context: Context, id: String, action: FocusAction): PendingIntent =
        PendingIntent.getForegroundService(context, action.ordinal,
            Intent(context, FocusService::class.java).setAction(action.name).putExtra("session_id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun show(context: Context, session: FocusSession, alert: Boolean = false) {
        if (allowed(context)) NotificationManagerCompat.from(context).notify(ID, build(context, session, alert))
    }

    fun boundary(context: Context, change: FocusChange?) {
        if (change != null && (change.focusFinished || change.breakFinished)) show(context, change.session, alert = true)
    }
}
