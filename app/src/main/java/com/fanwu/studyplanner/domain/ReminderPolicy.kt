package com.fanwu.studyplanner.domain

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ReminderSettings(
    val tasksEnabled: Boolean = false,
    val coursesEnabled: Boolean = false,
    val leadMinutes: Int = 15,
    val quietEnabled: Boolean = false,
    val quietStart: String = "23:00",
    val quietEnd: String = "07:00"
) {
    fun validate() {
        require(leadMinutes in 0..1440) { "提前时间应为 0—1440 分钟" }
        val start = LocalTime.parse(quietStart)
        val end = LocalTime.parse(quietEnd)
        require(!quietEnabled || start != end) { "静默开始和结束时间不能相同" }
    }
}

data class ReminderSpec(val eventId: Long, val occurrence: String, val fireAt: Long, val startsAt: Long)

object ReminderPolicy {
    const val MAX_LATENESS_MS = 10 * 60_000L

    fun isQuiet(time: LocalTime, settings: ReminderSettings): Boolean {
        if (!settings.quietEnabled) return false
        val start = LocalTime.parse(settings.quietStart)
        val end = LocalTime.parse(settings.quietEnd)
        return if (start < end) time >= start && time < end else time >= start || time < end
    }

    fun occurrence(event: CalendarEvent): String {
        // 标题、备注和计划名称变化不会为同一开始时间重复发送；改期则成为新的提醒。
        val key = "${event.id}|${event.date}|${event.startTime}|${event.type}"
        return MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun spec(event: CalendarEvent, settings: ReminderSettings, zone: ZoneId): ReminderSpec? {
        if (event.isDone || !(if (event.type == EventType.CLASS) settings.coursesEnabled else settings.tasksEnabled)) return null
        return runCatching {
            val start = LocalDate.parse(event.date).atTime(LocalTime.parse(event.startTime)).atZone(zone)
            val fire = start.minusMinutes(settings.leadMinutes.toLong())
            if (isQuiet(fire.toLocalTime(), settings)) null
            else ReminderSpec(event.id, occurrence(event), fire.toInstant().toEpochMilli(), start.toInstant().toEpochMilli())
        }.getOrNull()
    }

    fun deliverable(spec: ReminderSpec, now: Long, settings: ReminderSettings, zone: ZoneId): Boolean =
        now >= spec.fireAt && now - spec.fireAt <= MAX_LATENESS_MS && now <= spec.startsAt + 60_000L &&
            !isQuiet(java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalTime(), settings)
}
