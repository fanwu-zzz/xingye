package com.fanwu.studyplanner.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val enabled = ReminderSettings(tasksEnabled = true, coursesEnabled = true)
    private fun event(type: EventType = EventType.STUDY_TASK) = CalendarEvent(id = 1, title = "学习", date = "2026-10-27", startTime = "09:00", endTime = "10:00", type = type)

    @Test fun defaultsDoNotEnableAnyNotifications() {
        EventType.entries.forEach { assertNull(ReminderPolicy.spec(event(it), ReminderSettings(), zone)) }
    }
    @Test fun typesAndCompletionAreFiltered() {
        val tasks = enabled.copy(coursesEnabled = false)
        assertNotNull(ReminderPolicy.spec(event(EventType.EXAM), tasks, zone))
        assertNull(ReminderPolicy.spec(event(EventType.CLASS), tasks, zone))
        assertNull(ReminderPolicy.spec(event().copy(isDone = true), enabled, zone))
    }
    @Test fun leadTimeCanCrossDateBoundary() {
        val spec = ReminderPolicy.spec(event().copy(startTime = "00:05"), enabled, zone)!!
        assertEquals(LocalDateTime.of(2026, 10, 26, 23, 50), Instant.ofEpochMilli(spec.fireAt).atZone(zone).toLocalDateTime())
    }
    @Test fun overnightQuietIsInclusiveStartExclusiveEnd() {
        val settings = enabled.copy(quietEnabled = true)
        assertTrue(ReminderPolicy.isQuiet(LocalTime.of(23, 0), settings))
        assertTrue(ReminderPolicy.isQuiet(LocalTime.of(0, 0), settings))
        assertTrue(ReminderPolicy.isQuiet(LocalTime.of(6, 59), settings))
        assertFalse(ReminderPolicy.isQuiet(LocalTime.of(7, 0), settings))
        assertFalse(ReminderPolicy.isQuiet(LocalTime.of(22, 59), settings))
    }
    @Test fun sameDayQuietAndScheduledQuietAreSkipped() {
        val settings = enabled.copy(quietEnabled = true, quietStart = "08:00", quietEnd = "09:00")
        assertNull(ReminderPolicy.spec(event(), settings, zone))
        assertFalse(ReminderPolicy.isQuiet(LocalTime.of(9, 0), settings))
    }
    @Test fun deliveryChecksLateWindowAndActualQuietTime() {
        val spec = ReminderPolicy.spec(event(), enabled, zone)!!
        assertFalse(ReminderPolicy.deliverable(spec, spec.fireAt - 1, enabled, zone))
        assertTrue(ReminderPolicy.deliverable(spec, spec.fireAt + 60_000, enabled, zone))
        assertFalse(ReminderPolicy.deliverable(spec, spec.fireAt + 11 * 60_000, enabled, zone))
        val quiet = enabled.copy(quietEnabled = true, quietStart = "08:46", quietEnd = "10:00")
        assertFalse(ReminderPolicy.deliverable(spec, spec.fireAt + 60_000, quiet, zone))
    }
    @Test fun titleOrNotesDoNotChangeOccurrenceButRescheduleDoes() {
        val original = event()
        assertEquals(ReminderPolicy.occurrence(original), ReminderPolicy.occurrence(original.copy(title = "新标题", note = "备注", planName = "计划")))
        assertNotEquals(ReminderPolicy.occurrence(original), ReminderPolicy.occurrence(original.copy(startTime = "10:00")))
    }
    @Test fun localTimeTracksTimezoneAndDst() {
        val shanghai = ReminderPolicy.spec(event(), enabled, zone)!!
        val tokyo = ReminderPolicy.spec(event(), enabled, ZoneId.of("Asia/Tokyo"))!!
        assertEquals(3_600_000L, shanghai.fireAt - tokyo.fireAt)
        assertNotNull(ReminderPolicy.spec(event().copy(date = "2026-03-08", startTime = "03:05"), enabled, ZoneId.of("America/New_York")))
    }
    @Test fun invalidSettingsAndEventsAreRejectedSafely() {
        listOf(enabled.copy(leadMinutes = -1), enabled.copy(leadMinutes = 1441), enabled.copy(quietEnabled = true, quietEnd = "23:00"), enabled.copy(quietStart = "25:00")).forEach {
            assertTrue(runCatching { it.validate() }.isFailure)
        }
        enabled.copy(leadMinutes = 0).validate()
        enabled.copy(leadMinutes = 1440).validate()
        assertNull(ReminderPolicy.spec(event().copy(date = "无效日期"), enabled, zone))
    }
}
