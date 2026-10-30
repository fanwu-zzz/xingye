package com.fanwu.studyplanner.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LearningFeaturesTest {
    private val monday = LocalDate.parse("2026-10-26")
    private val semester = Semester("s", "秋季", monday.toString(), 4, listOf(PeriodTime(1, "08:00", "08:45"), PeriodTime(2, "08:55", "09:40")))
    private fun course(weeks: String = "1-4单周") = RecurringCourse("c", "s", "数学", 1, weeks, "1-2")

    @Test fun goalRevisionNeverChangesEarlierWeeks() {
        val old = GoalRevision(id = "a", goalId = "g", effectiveWeek = monday.toString(), name = "旧目标", minutes = 60, createdAt = 1)
        val updated = old.copy(id = "b", effectiveWeek = monday.plusWeeks(1).toString(), minutes = 120, createdAt = 2)
        assertEquals(60, goalsForWeek(listOf(old, updated), monday).single().minutes)
        assertEquals(120, goalsForWeek(listOf(old, updated), monday.plusWeeks(1)).single().minutes)
        assertTrue(goalsForWeek(listOf(old), monday.minusWeeks(1)).isEmpty())
    }
    @Test fun pausedGoalHasItsOwnEffectiveWeek() {
        val old = GoalRevision(goalId = "g", effectiveWeek = monday.toString(), name = "读书", minutes = 60)
        val pause = old.copy(id = "paused", effectiveWeek = monday.plusWeeks(1).toString(), state = "PAUSED")
        assertEquals("ACTIVE", goalsForWeek(listOf(old, pause), monday).single().state)
        assertEquals("PAUSED", goalsForWeek(listOf(old, pause), monday.plusWeeks(1)).single().state)
    }
    @Test fun weeklyFocusSplitsAcrossWeekBoundary() {
        val start = monday.minusDays(1).atTime(23, 50).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        assertEquals(10 * 60000L, focusedInWeek(listOf(FocusSpan(start, 20 * 60000, "Asia/Shanghai")), monday))
    }
    @Test fun oddEvenAndExceptionsExpandLocally() {
        assertEquals(listOf("2026-10-26", "2026-11-09"), expandCourse(course(), semester).map { it.date })
        assertEquals(listOf("2026-11-02", "2026-11-16"), expandCourse(course("1-4双周"), semester).map { it.date })
        assertEquals(1, expandCourse(course().copy(exceptions = listOf("2026-10-26")), semester).size)
        assertEquals("09:40", expandCourse(course(), semester).first().endTime)
    }
    @Test fun rangesCanPreserveImportedCombinedPeriodTimes() {
        val combined = semester.copy(periods = listOf(PeriodTime(1, "08:00", "09:40", 2)))
        assertEquals("09:40", expandCourse(course(), combined).first().endTime)
        assertTrue(runCatching { expandCourse(course().copy(periods = "1"), combined) }.isFailure)
    }
    @Test fun consecutiveCombinedBlocksCanFormOneLongClass() {
        val combined = semester.copy(periods = listOf(PeriodTime(1, "08:10", "09:50", 2), PeriodTime(3, "10:10", "11:50", 4)))
        val event = expandCourse(course().copy(periods = "1-4"), combined).first()
        assertEquals("08:10", event.startTime)
        assertEquals("11:50", event.endTime)
        assertEquals("08:10", expandCourse(course(), combined).first().startTime)
    }
    @Test fun partialCombinedBlockAsksForSpecificCourseTimes() {
        val combined = semester.copy(periods = listOf(PeriodTime(1, "08:10", "09:50", 2), PeriodTime(3, "10:10", "11:50", 4)))
        val failure = runCatching { expandCourse(course().copy(periods = "2-4"), combined) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message.orEmpty().contains("数学"))
        assertTrue(failure.message.orEmpty().contains("第 2 节开始"))
    }
    @Test fun invalidSemesterAndRulesFailWithoutSilentExpansion() {
        assertTrue(runCatching { semester.copy(startMonday = "2026-10-27").validate() }.isFailure)
        assertTrue(runCatching { semester.copy(periods = semester.periods + PeriodTime(3, "09:00", "10:00")).validate() }.isFailure)
        assertTrue(runCatching { semester.copy(periods = listOf(PeriodTime(1, "08:00", "09:00", Int.MAX_VALUE))).validate() }.isFailure)
        listOf(course("1-5"), course("1-4abc"), course().copy(day = 8), course().copy(periods = "1,3")).forEach {
            assertTrue(runCatching { expandCourse(it, semester) }.isFailure)
        }
        val extended = semester.copy(periods = listOf(PeriodTime(14, "18:00", "18:45")))
        assertEquals("18:45", expandCourse(course().copy(periods = "14"), extended).first().endTime)
    }
    @Test fun reviewSeparatesPlanActualCompletionAndUnknownData() {
        val task = CalendarEvent(title = "复习", date = monday.toString(), startTime = "08:00", endTime = "09:00", type = EventType.STUDY_TASK, isDone = true)
        val review = buildWeekReview(monday, listOf(task), emptyList(), monday.plusDays(7).atStartOfDay())
        assertEquals(0L, review.actualMs)
        assertTrue(review.text.contains("计划学习：60 分钟"))
        assertTrue(review.text.contains("旧任务 1 项缺少完成时间"))
        assertTrue(review.text.contains("暂无专注记录"))
        assertFalse(review.text.contains("100%"))
    }
    @Test fun emptyReviewRemainsReadable() {
        val review = buildWeekReview(monday, emptyList(), emptyList())
        assertEquals(0, review.taskCount)
        assertTrue(review.text.contains("没有记录表示暂无数据"))
    }
}
