package com.fanwu.studyplanner.reminders

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.app.job.*
import android.content.*
import android.os.Build
import android.util.Log
import com.fanwu.studyplanner.data.local.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object ReminderAlarms {
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, at: Long?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(context, 4301,
            Intent(context, ReminderReceiver::class.java).setAction("studyplanner.EVENT_REMINDER"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (at == null) { manager.cancel(operation); return }
        try {
            if (exactAllowed(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }
}

class StudyPlannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderRuntime.initialize(this)
    }
}

object ReminderRuntime {
    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    lateinit var engine: ReminderEngine
        private set

    fun initialize(context: Context) {
        if (app != null) return
        app = context.applicationContext
        val database = AppDatabase.getInstance(context)
        engine = ReminderEngine(database, { ReminderAlarms.schedule(context, it) }, EventNotifications(context))
        scope.launch {
            combine(database.calendarEventDao().observeAll(), engine.settings) { _, _ -> Unit }
                .collect { reconcileSafely() }
        }
    }

    /** 写入前排入持久化补偿任务；提交后即刻重排，取消屏幕协程也不能跳过更新。 */
    suspend fun <T> calendarMutation(block: suspend () -> T): T {
        scheduleRecovery(periodic = false)
        return try { block() } finally { withContext(NonCancellable) { reconcileSafely() } }
    }

    suspend fun reconcileSafely(): Boolean {
        if (!::engine.isInitialized) return true
        return try {
            engine.reconcile()
            val context = app!!
            val settings = AppDatabase.getInstance(context).reminderDao().settings()
            if (settings?.tasksEnabled == true || settings?.coursesEnabled == true) scheduleRecovery(periodic = true)
            else context.getSystemService(JobScheduler::class.java).cancel(4302)
            _error.value = null
            true
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.w("EventReminders", "日程提醒重排失败", e)
            _error.value = "提醒调度失败，系统将重试；可重新打开本页恢复。"
            scheduleRecovery(periodic = false)
            false
        }
    }

    private fun scheduleRecovery(periodic: Boolean) {
        val context = app ?: return
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (periodic && scheduler.getPendingJob(4302) != null) return
            val builder = JobInfo.Builder(if (periodic) 4302 else 4303, ComponentName(context, ReminderRecoveryJob::class.java))
                .setPersisted(true)
            if (periodic) builder.setPeriodic(15 * 60_000L)
            else builder.setMinimumLatency(1_000).setOverrideDeadline(10_000)
            if (scheduler.schedule(builder.build()) != JobScheduler.RESULT_SUCCESS) {
                Log.w("EventReminders", "系统暂未接受提醒补偿任务")
            }
        } catch (e: Exception) {
            // 调度器限额或厂商异常不能阻止日历写入；下一次打开页面仍会重排主闹钟。
            Log.w("EventReminders", "无法排入提醒补偿任务", e)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { ReminderRuntime.reconcileSafely() }
            finally { result.finish() }
        }
    }
}

class ReminderRecoveryJob : JobService() {
    private val jobs = mutableMapOf<Int, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onStartJob(params: JobParameters): Boolean {
        jobs[params.jobId] = scope.launch {
            val ok = withContext(Dispatchers.IO) { ReminderRuntime.reconcileSafely() }
            jobs.remove(params.jobId)
            jobFinished(params, !ok)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { jobs.remove(params.jobId)?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
