package com.fanwu.studyplanner.domain

import com.fanwu.studyplanner.ui.calendar.validateInput
import com.fanwu.studyplanner.ui.stats.buildStatsState
import com.fanwu.studyplanner.ui.tasks.filterTasks
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DeadlineTaskTest {
    private val day = LocalDate.of(2026, 10, 28)
    private val deadline = CalendarEvent(id = 1, title = "交数学作业", date = day.toString(),
        startTime = "18:00", endTime = "18:00", type = EventType.DEADLINE)

    @Test fun editorAcceptsOneDeadlineInstantWithoutInventingDuration() {
        assertNull(validateInput("交作业", day.toString(), "23:59", "23:59", EventType.DEADLINE))
        assertNull(validateInput("交作业", day.toString(), "00:00", "00:00", EventType.DEADLINE))
        assertNotNull(validateInput("复习", day.toString(), "23:59", "23:59", EventType.STUDY_TASK))
        assertNotNull(validateInput("交作业", day.toString(), "18:00", "19:00", EventType.DEADLINE))
        listOf("24:00", "25:10", "18:00:30", "8:00", "不确定").forEach {
            assertNotNull(validateInput("交作业", day.toString(), it, it, EventType.DEADLINE))
        }
    }

    @Test fun deadlineHasZeroPlannedMinutesButRemainsInPendingCounts() {
        val study = deadline.copy(id = 2, type = EventType.STUDY_TASK, startTime = "16:00", endTime = "17:00")
        val stats = buildStatsState(listOf(deadline, study), day, "week")
        assertEquals(0, deadline.durationMinutes())
        assertEquals(60, stats.dailyMinutes.sum())
        assertEquals("1h", stats.weekHoursText)
        assertEquals(2, stats.pendingCount)
        assertTrue(deadline.hasValidTimeRange())
        assertFalse(deadline.copy(endTime = "19:00").hasValidTimeRange())
    }

    @Test fun overdueIncludesTodaysDeadlineOnlyAfterItsDueTimeAndExcludesCompleted() {
        val before = day.atTime(17, 59)
        val due = day.atTime(18, 0)
        assertFalse(deadline.isOverdue(before))
        assertTrue(deadline.isOverdue(due))
        assertFalse(deadline.copy(isDone = true).isOverdue(due))
        assertTrue(filterTasks(listOf(deadline), "逾期", "", EventType.DEADLINE, null, day, before).isEmpty())
        assertEquals(listOf(deadline), filterTasks(listOf(deadline), "逾期", "", EventType.DEADLINE, null, day, due))
    }

    @Test fun deadlineDoesNotBlockAStudyOrCourseTimeSlot() {
        val course = deadline.copy(type = EventType.CLASS, startTime = "17:00", endTime = "19:00")
        assertFalse(overlaps(deadline, course))
        assertFalse(overlaps(course, deadline))
        assertTrue(overlaps(course, course.copy(type = EventType.STUDY_TASK)))
    }

    @Test fun reminderUsesDueInstantAndCancelsCompletedDeadline() {
        val zone = ZoneId.of("Asia/Shanghai")
        val settings = ReminderSettings(tasksEnabled = true, leadMinutes = 30)
        val spec = ReminderPolicy.spec(deadline, settings, zone)!!
        assertEquals(day.atTime(17, 30), Instant.ofEpochMilli(spec.fireAt).atZone(zone).toLocalDateTime())
        assertEquals(day.atTime(18, 0), Instant.ofEpochMilli(spec.startsAt).atZone(zone).toLocalDateTime())
        assertNull(ReminderPolicy.spec(deadline.copy(isDone = true), settings, zone))
        assertNull(ReminderPolicy.spec(deadline, settings.copy(tasksEnabled = false), zone))
    }

    @Test fun deadlineRoundTripsAndCanBeAssociatedWithRealFocusTime() {
        assertEquals(deadline, Json.decodeFromString<CalendarEvent>(Json.encodeToString(deadline)))
        assertEquals("截止 18:00", deadline.timeText())
        assertTrue(deadline.type.isTask)
        val session = FocusTimer.newSession("deadline-focus", 25, 5, FocusInstant(1_000_000, 1000, 1), "Asia/Shanghai", deadline)
        assertEquals(deadline.id, session.taskId)
        assertEquals(25 * 60_000L, session.focusDurationMs)
        assertEquals(0, deadline.durationMinutes())
    }
}
