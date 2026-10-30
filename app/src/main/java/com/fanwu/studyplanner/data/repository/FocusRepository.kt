package com.fanwu.studyplanner.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.UUID
import com.fanwu.studyplanner.reminders.ReminderRuntime

fun interface FocusClock { fun now(): FocusInstant }

class AndroidFocusClock(private val context: Context) : FocusClock {
    override fun now() = FocusInstant(
        System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    )
}

/** 单一写入入口；一次结算的状态、时间片与任务完成在同一事务中提交。 */
class FocusRepository(
    private val database: AppDatabase,
    val clock: FocusClock,
    private val zoneId: () -> String = { ZoneId.systemDefault().id }
) {
    private val mutex = Mutex()
    private val dao = database.focusDao()
    val active = dao.observeActive().map { it?.toFocusSession() }
    val history = dao.observeHistory().map { list -> list.map { it.toFocusSession() } }
    val intervals = dao.observeIntervals().map { list -> list.map { it.toFocusSpan() } }

    suspend fun current(): FocusSession? = dao.active()?.toFocusSession()

    suspend fun start(focusMinutes: Int, breakMinutes: Int, taskId: Long?): FocusSession = mutex.withLock {
        database.withTransaction {
            check(dao.active() == null) { "已有一轮专注，请先结束当前专注或休息。" }
            val task = taskId?.let { id ->
                val event = database.calendarEventDao().getById(id)?.toDomain()
                check(event != null && event.type.isTask && !event.isDone) { "关联任务不存在或已完成，请重新选择。" }
                event
            }
            val session = FocusTimer.newSession(UUID.randomUUID().toString(), focusMinutes, breakMinutes, clock.now(), zoneId(), task)
            dao.insert(session.toEntity())
            session
        }
    }

    suspend fun act(id: String? = null, action: FocusAction = FocusAction.CHECKPOINT, completeTask: Boolean = false): FocusChange? =
        if (completeTask) ReminderRuntime.calendarMutation { change(id, action, true) } else change(id, action, false)

    private suspend fun change(id: String?, action: FocusAction, completeTask: Boolean): FocusChange? = mutex.withLock {
        database.withTransaction {
            val previous = dao.active()?.toFocusSession() ?: return@withTransaction null
            if (id != null && previous.id != id) return@withTransaction null
            val change = FocusTimer.transition(previous, action, clock.now())
            var state = change.session
            val taskId = state.taskId
            if (completeTask && !state.taskCompletionApplied && taskId != null &&
                (action == FocusAction.FINISH || action == FocusAction.START_BREAK)) {
                val task = database.calendarEventDao().getById(taskId)
                if (task != null && task.type.isTask) {
                    database.calendarEventDao().setDone(task.id, true, clock.now().wallMs)
                    state = state.copy(taskCompletionApplied = true)
                }
            }
            dao.update(state.toEntity())
            change.span?.let { span ->
                dao.insertInterval(FocusIntervalEntity(UUID.randomUUID().toString(), state.id, span.startedAt, span.durationMs, span.zoneId))
            }
            change.copy(session = state)
        }
    }

    companion object {
        @Volatile private var instance: FocusRepository? = null
        fun get(context: Context): FocusRepository = instance ?: synchronized(this) {
            instance ?: FocusRepository(AppDatabase.getInstance(context.applicationContext), AndroidFocusClock(context.applicationContext))
                .also { instance = it }
        }
    }
}
