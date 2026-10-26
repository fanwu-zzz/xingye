package com.fanwu.studyplanner.reminders

import androidx.room.withTransaction
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

interface ReminderDelivery {
    fun available(): Boolean
    fun notify(event: CalendarEvent, spec: ReminderSpec)
    fun retain(tags: Set<String>)
}

fun reminderTag(id: Long, occurrence: String) = "event:$id:$occurrence"

/** 一把锁覆盖重新规划、领取与系统调度；发送前再次从数据库读取日程及设置。 */
class ReminderEngine(
    private val database: AppDatabase,
    private val schedule: (Long?) -> Unit,
    private val delivery: ReminderDelivery,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    private val mutex = Mutex()
    val settings = database.reminderDao().observeSettings()
    val pending = database.reminderDao().observePending()

    suspend fun save(settings: ReminderSettings) {
        settings.validate()
        database.reminderDao().saveSettings(settings.toEntity())
        reconcile()
    }

    suspend fun reconcile() = mutex.withLock {
        val notifications = mutableListOf<Pair<CalendarEvent, ReminderSpec>>()
        val retainedNotifications = mutableSetOf<String>()
        val next = database.withTransaction {
            val dao = database.reminderDao()
            val settings = dao.settings()?.toDomain() ?: ReminderSettings()
            val currentTime = now()
            val currentZone = zone()
            val events = database.calendarEventDao().getAll().map { it.toDomain() }
            val previous = dao.all().associateBy { it.eventId }
            val pendingTimes = mutableListOf<Long>()
            for (event in events) {
                val spec = ReminderPolicy.spec(event, settings, currentZone)
                val old = previous[event.id]
                if (spec == null) {
                    if (old?.state == "PENDING") dao.delete(event.id)
                    continue
                }
                val same = old?.occurrence == spec.occurrence
                if (same && old!!.state != "PENDING" && !(old.state == "MISSED" && old.fireAt != spec.fireAt && spec.fireAt > currentTime)) {
                    if (old.state == "DELIVERED") retainedNotifications += reminderTag(event.id, spec.occurrence)
                    continue
                }
                var record = EventReminderEntity(event.id, spec.occurrence, spec.fireAt)
                if (spec.fireAt < currentTime && (!same || old!!.fireAt != spec.fireAt)) {
                    // 新开关、新建或更改提前时间后不补发已经错过的提醒。
                    record = record.copy(state = "MISSED")
                } else if (spec.fireAt <= currentTime) {
                    if (ReminderPolicy.deliverable(spec, currentTime, settings, currentZone) && delivery.available()) {
                        // 先持久化领取，再调用系统通知；进程崩溃至多丢一条，不重复轰炸用户。
                        dao.put(record.copy(state = "DELIVERED"))
                        notifications += event to spec
                        retainedNotifications += reminderTag(event.id, spec.occurrence)
                        continue
                    }
                    record = record.copy(state = "MISSED")
                }
                dao.put(record)
                if (record.state == "PENDING") pendingTimes += record.fireAt
            }
            pendingTimes.minOrNull()
        }
        for ((_, spec) in notifications) {
            // 领取事务提交后再次核对，删除/编辑与系统回调竞争时以最新内容为准。
            val event = database.calendarEventDao().getById(spec.eventId)?.toDomain() ?: continue
            val settings = database.reminderDao().settings()?.toDomain() ?: ReminderSettings()
            val latest = ReminderPolicy.spec(event, settings, zone())
            if (latest == spec && ReminderPolicy.deliverable(spec, now(), settings, zone()) && delivery.available()) {
                delivery.notify(event, spec)
            }
        }
        delivery.retain(retainedNotifications)
        schedule(next)
    }
}
