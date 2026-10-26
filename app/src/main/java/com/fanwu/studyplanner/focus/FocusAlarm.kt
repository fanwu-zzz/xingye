package com.fanwu.studyplanner.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.fanwu.studyplanner.data.repository.FocusRepository
import com.fanwu.studyplanner.domain.FocusSession
import com.fanwu.studyplanner.domain.FocusTimer
import kotlinx.coroutines.*

object FocusAlarm {
    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pending(context: Context, id: String? = null) = PendingIntent.getBroadcast(
        context, 4102, Intent(context, FocusAlarmReceiver::class.java).setAction("focus_deadline").putExtra("session_id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun sync(context: Context, session: FocusSession?) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (session == null || !session.phase.isRunning) {
            alarm.cancel(pending(context))
            return
        }
        val remaining = FocusTimer.remainingMs(session, com.fanwu.studyplanner.data.repository.AndroidFocusClock(context).now())
        val at = SystemClock.elapsedRealtime() + remaining
        val operation = pending(context, session.id)
        try {
            if (exactAllowed(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation)
            else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation)
        } catch (_: SecurityException) {
            // 权限可能在检查之后被撤销；仍保留可恢复的非精确截止提醒。
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation)
        }
    }
}

/** 截止时直接结算数据库，不依赖从后台重新启动前台服务。 */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = FocusRepository.get(context)
                val change = repository.act(intent.getStringExtra("session_id"))
                FocusNotifications.boundary(context, change)
                val session = repository.current()
                FocusAlarm.sync(context, session)
                if (intent.action == Intent.ACTION_BOOT_COMPLETED && session != null) {
                    FocusNotifications.show(context, session)
                }
            } finally { pending.finish() }
        }
    }
}
