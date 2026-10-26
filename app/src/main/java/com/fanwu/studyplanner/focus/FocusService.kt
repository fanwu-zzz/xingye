package com.fanwu.studyplanner.focus

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.fanwu.studyplanner.data.repository.FocusRepository
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.*

/** 前台服务负责用户可见的计时控制；时间真值始终来自已持久化的单调时钟锚点。 */
class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { FocusRepository.get(this) }
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = FocusNotifications.build(this)
        if (Build.VERSION.SDK_INT >= 34) startForeground(FocusNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(FocusNotifications.ID, notification)
        scope.launch {
            try {
                val action = intent?.action?.let { runCatching { FocusAction.valueOf(it) }.getOrNull() } ?: FocusAction.CHECKPOINT
                val change = repository.act(intent?.getStringExtra("session_id"), action)
                FocusNotifications.boundary(this@FocusService, change)
                if (change != null && change.session.phase.isTerminal && !change.breakFinished) {
                    FocusNotifications.show(this@FocusService, change.session)
                }
                loop?.cancel()
                loop = scope.launch { monitor(startId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                runCatching { repository.act(action = FocusAction.PAUSE)?.let { FocusNotifications.show(this@FocusService, it.session) } }
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelfResult(startId)
            }
        }
        return START_STICKY
    }

    private suspend fun monitor(startId: Int) {
        var ticks = 0
        var scheduledId: String? = null
        while (currentCoroutineContext().isActive) {
            var session = repository.current()
            if (session == null) {
                FocusAlarm.sync(this, null)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelfResult(startId)
                return
            }
            val remaining = FocusTimer.remainingMs(session, repository.clock.now())
            if (ticks % 15 == 0 || (session.phase.isRunning && remaining <= 0)) {
                val change = repository.act(session.id)
                FocusNotifications.boundary(this, change)
                session = change?.session ?: repository.current() ?: continue
            }
            if (!session.phase.isRunning) {
                FocusAlarm.sync(this, null)
                stopForeground(STOP_FOREGROUND_DETACH)
                // 完成边界的提示已发送，避免静默通知立即覆盖有声完成通知。
                if (session.phase.isPaused) FocusNotifications.show(this, session)
                stopSelfResult(startId)
                return
            }
            if (scheduledId != session.id || ticks % 15 == 0) {
                FocusAlarm.sync(this, session)
                scheduledId = session.id
                FocusNotifications.show(this, session)
            }
            ticks++
            delay(1_000)
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_DETACH)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
        }
    }
}
