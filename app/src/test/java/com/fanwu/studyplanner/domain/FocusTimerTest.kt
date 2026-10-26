package com.fanwu.studyplanner.domain

import com.fanwu.studyplanner.ui.stats.buildStatsState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class FocusTimerTest {
    private val start = FocusInstant(1_700_000_000_000, 100_000, 7)
    private fun at(ms: Long) = start.copy(wallMs = start.wallMs + ms, elapsedMs = start.elapsedMs + ms)
    private fun session() = FocusTimer.newSession("test", 1, 1, start, "Asia/Shanghai")

    @Test fun pauseAndResumeDoNotCountPausedTime() {
        val paused = FocusTimer.transition(session(), FocusAction.PAUSE, at(20_000)).session
        assertEquals(20_000L, paused.focusedMs)
        assertEquals(40_000L, FocusTimer.remainingMs(paused, at(50_000)))
        val resumed = FocusTimer.transition(paused, FocusAction.RESUME, at(50_000)).session
        val finished = FocusTimer.transition(resumed, FocusAction.CHECKPOINT, at(90_000))
        assertEquals(60_000L, finished.session.focusedMs)
        assertEquals(FocusPhase.FOCUS_READY, finished.session.phase)
        assertEquals(start.wallMs + 50_000, finished.session.pauses.single().endedAt)
    }

    @Test fun lateCallbackCapsFocusAtTargetAndKeepsTrueDeadline() {
        val finished = FocusTimer.transition(session(), FocusAction.CHECKPOINT, at(180_000))
        assertEquals(60_000L, finished.session.focusedMs)
        assertEquals(start.wallMs + 60_000, finished.session.endedAt)
        assertEquals(60_000L, finished.span!!.durationMs)
        assertTrue(finished.focusFinished)
    }

    @Test fun checkpointAndRepeatedFinishCannotDoubleCount() {
        val first = FocusTimer.transition(session(), FocusAction.CHECKPOINT, at(15_000))
        val second = FocusTimer.transition(first.session, FocusAction.CHECKPOINT, at(30_000))
        val end = FocusTimer.transition(second.session, FocusAction.FINISH, at(35_000))
        val repeated = FocusTimer.transition(end.session, FocusAction.FINISH, at(50_000))
        assertEquals(35_000L, repeated.session.focusedMs)
        assertEquals(35_000L, first.span!!.durationMs + second.span!!.durationMs + end.span!!.durationMs)
        assertNull(repeated.span)
        assertEquals("ABANDONED", repeated.session.outcome)
    }

    @Test fun restAndRestPauseAreExcludedFromFocus() {
        val done = FocusTimer.transition(session(), FocusAction.CHECKPOINT, at(60_000)).session
        val resting = FocusTimer.transition(done, FocusAction.START_BREAK, at(70_000)).session
        val paused = FocusTimer.transition(resting, FocusAction.PAUSE, at(90_000)).session
        val resumed = FocusTimer.transition(paused, FocusAction.RESUME, at(120_000)).session
        val finished = FocusTimer.transition(resumed, FocusAction.CHECKPOINT, at(160_000))
        assertEquals(60_000L, finished.session.focusedMs)
        assertEquals(60_000L, finished.session.breakElapsedMs)
        assertEquals(FocusPhase.FINISHED, finished.session.phase)
        assertTrue(finished.breakFinished)
        assertNull(finished.span)
    }

    @Test fun wallClockChangesDoNotSpeedUpCountdown() {
        val changedWall = at(10_000).copy(wallMs = start.wallMs + 86_400_000)
        assertEquals(50_000L, FocusTimer.remainingMs(session(), changedWall))
        assertEquals(10_000L, FocusTimer.transition(session(), FocusAction.CHECKPOINT, changedWall).session.focusedMs)
    }

    @Test fun restartOnSameBootRecoversFromPersistedAnchor() {
        val committed = FocusTimer.transition(session(), FocusAction.CHECKPOINT, at(15_000)).session
        val restored = Json.decodeFromString<FocusSession>(Json.encodeToString(committed))
        assertEquals(15_000L, restored.focusedMs)
        assertEquals(15_000L, FocusTimer.remainingMs(restored, at(45_000)))
    }

    @Test fun deviceRebootPausesWithoutInventingPowerOffTime() {
        val committed = FocusTimer.transition(session(), FocusAction.CHECKPOINT, at(15_000)).session
        val reboot = FocusInstant(start.wallMs + 3_600_000, 5_000, 8)
        val recovered = FocusTimer.transition(committed, FocusAction.CHECKPOINT, reboot)
        assertEquals(FocusPhase.FOCUS_PAUSED, recovered.session.phase)
        assertEquals(15_000L, recovered.session.focusedMs)
        assertNull(recovered.span)
        assertNotNull(recovered.session.recoveryMessage)
    }

    @Test fun zeroRestFinishesWithoutAddingFocusTime() {
        val initial = session().copy(breakDurationMs = 0)
        val ready = FocusTimer.transition(initial, FocusAction.CHECKPOINT, at(60_000)).session
        val finished = FocusTimer.transition(ready, FocusAction.START_BREAK, at(120_000))
        assertEquals(FocusPhase.FINISHED, finished.session.phase)
        assertEquals(60_000L, finished.session.focusedMs)
        assertNull(finished.span)
    }

    @Test fun midnightSplitsTimeIntoActualDays() {
        val startAt = ZonedDateTime.parse("2026-10-25T23:59:30+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
        val days = focusByDate(listOf(FocusSpan(startAt, 90_000, "Asia/Shanghai")))
        assertEquals(30_000L, days[LocalDate.of(2026, 10, 25)])
        assertEquals(60_000L, days[LocalDate.of(2026, 10, 26)])
    }

    @Test fun weekStatsDoNotMixFocusAndPlannedHours() {
        val date = LocalDate.of(2026, 10, 25)
        val startAt = date.atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val task = CalendarEvent(title = "复习", date = date.toString(), startTime = "09:00", endTime = "10:00", type = EventType.STUDY_TASK)
        val result = buildStatsState(listOf(task), date, "week", listOf(FocusSpan(startAt, 120_000, "Asia/Shanghai")))
        assertEquals("1h", result.weekHoursText)
        assertEquals(120_000L, result.actualFocusMs)
        assertEquals("0h", result.completedHoursText)
    }

    @Test fun invalidDurationsAreRejected() {
        listOf(0 to 5, 181 to 5, 25 to -1, 25 to 61).forEach { (focus, rest) ->
            assertTrue(runCatching { FocusTimer.newSession("invalid", focus, rest, start, "UTC") }.isFailure)
        }
    }
}
