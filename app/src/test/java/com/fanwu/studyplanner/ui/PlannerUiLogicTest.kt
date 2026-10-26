package com.fanwu.studyplanner.ui

import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.EventType
import com.fanwu.studyplanner.ui.stats.buildStatsState
import com.fanwu.studyplanner.ui.tasks.filterTasks
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlannerUiLogicTest {
    private val today = LocalDate.of(2026, 10, 25)
    private fun event(id: Long, date: String, done: Boolean = false, type: EventType = EventType.STUDY_TASK, plan: String? = null) =
        CalendarEvent(id, "线性代数-$id", date, "09:00", "10:00", type, isDone = done, planName = plan)

    @Test fun overdueFilterIncludesPastPendingButNotCompletedTasks() {
        val events = listOf(event(1, "2026-10-24"), event(2, "2026-10-24", true), event(3, "2026-10-25"))
        assertEquals(listOf(1L), filterTasks(events, "逾期", "", null, null, today).map { it.id })
    }

    @Test fun completedHistoryAndPlanSearchCanBeCombined() {
        val events = listOf(event(1, "2026-10-24", true, plan = "考前冲刺"), event(2, "2026-10-25", plan = "考前冲刺"))
        assertEquals(listOf(1L), filterTasks(events, "已完成", "冲刺", EventType.STUDY_TASK, "考前冲刺", today).map { it.id })
    }

    @Test fun todayFilterExcludesCompletedAndFutureTasks() {
        val events = listOf(event(1, "2026-10-25"), event(2, "2026-10-25", true), event(3, "2026-10-26"))
        assertEquals(listOf(1L), filterTasks(events, "今天", "", null, null, today).map { it.id })
    }

    @Test fun weekStatsExcludeClassesAndSeparatePlannedFromCompletedTime() {
        val events = listOf(event(1, "2026-10-25"), event(2, "2026-10-24", true), event(3, "2026-10-25", type = EventType.CLASS))
        val state = buildStatsState(events, today, "week")
        assertEquals("2h", state.weekHoursText)
        assertEquals("1h", state.completedHoursText)
        assertEquals("50%", state.completionText)
        assertEquals(7, state.dailyMinutes.size)
        assertEquals(120, state.dailyMinutes.sum())
    }

    @Test fun monthStatsCoverAllDaysAndExcludeAdjacentMonths() {
        val state = buildStatsState(listOf(event(1, "2026-10-01"), event(2, "2026-10-31"), event(3, "2026-11-01")), today, "month")
        assertEquals(31, state.dailyMinutes.size)
        assertEquals("2h", state.weekHoursText)
        assertEquals("1", state.dayLabels.first())
        assertEquals("31", state.dayLabels.last())
    }

    @Test fun streakUsesCompletedDatesAndDoesNotCountFutureArrangements() {
        val events = listOf(event(1, "2026-10-25", true), event(2, "2026-10-24", true), event(3, "2026-10-23"), event(4, "2026-10-26", true))
        assertEquals(2, buildStatsState(events, today, "week").streakDays)
    }

    @Test fun noEventsDoesNotProduceInventedProgress() {
        val state = buildStatsState(emptyList(), today, "month")
        assertEquals("--", state.completionText)
        assertEquals("0h", state.weekHoursText)
        assertEquals(0, state.streakDays)
        assertTrue(state.dailyMinutes.all { it == 0 })
    }
}
