package com.fanwu.studyplanner.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
enum class FocusPhase {
    FOCUS_RUNNING, FOCUS_PAUSED, FOCUS_READY, BREAK_RUNNING, BREAK_PAUSED, FINISHED, ABANDONED;

    val isRunning get() = this == FOCUS_RUNNING || this == BREAK_RUNNING
    val isPaused get() = this == FOCUS_PAUSED || this == BREAK_PAUSED
    val isTerminal get() = this == FINISHED || this == ABANDONED
    val isBreak get() = this == BREAK_RUNNING || this == BREAK_PAUSED
}

@Serializable
data class FocusPause(val startedAt: Long, val endedAt: Long? = null, val duringBreak: Boolean = false)

@Serializable
data class FocusSession(
    val id: String,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    val focusDurationMs: Long,
    val breakDurationMs: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val focusedMs: Long = 0,
    val breakElapsedMs: Long = 0,
    val phase: FocusPhase = FocusPhase.FOCUS_RUNNING,
    val outcome: String = "ACTIVE",
    val anchorWallMs: Long,
    val anchorElapsedMs: Long,
    val bootCount: Int,
    val zoneId: String,
    val pauses: List<FocusPause> = emptyList(),
    val taskCompletionApplied: Boolean = false,
    val recoveryMessage: String? = null,
    val planName: String? = null,
    val subject: String? = null
)

data class FocusInstant(val wallMs: Long, val elapsedMs: Long, val bootCount: Int)
enum class FocusAction { CHECKPOINT, PAUSE, RESUME, START_BREAK, FINISH }
data class FocusSpan(val startedAt: Long, val durationMs: Long, val zoneId: String)
data class FocusChange(
    val session: FocusSession,
    val span: FocusSpan? = null,
    val focusFinished: Boolean = false,
    val breakFinished: Boolean = false
)

/** 纯状态机：画面每秒刷新不写库，只有检查点和操作才结算真实经过时间。 */
object FocusTimer {
    fun newSession(id: String, focusMinutes: Int, breakMinutes: Int, now: FocusInstant, zoneId: String, task: CalendarEvent? = null): FocusSession {
        require(focusMinutes in 1..180) { "专注时长应为 1—180 分钟" }
        require(breakMinutes in 0..60) { "休息时长应为 0—60 分钟" }
        return FocusSession(
            id = id, taskId = task?.id, taskTitle = task?.title, planName = task?.planName, subject = task?.subject,
            focusDurationMs = focusMinutes * 60_000L, breakDurationMs = breakMinutes * 60_000L,
            startedAt = now.wallMs, anchorWallMs = now.wallMs, anchorElapsedMs = now.elapsedMs,
            bootCount = now.bootCount, zoneId = zoneId
        )
    }

    fun remainingMs(session: FocusSession, now: FocusInstant): Long {
        if (session.phase == FocusPhase.FOCUS_READY || session.phase.isTerminal) return 0
        val total = if (session.phase.isBreak) session.breakDurationMs else session.focusDurationMs
        val committed = if (session.phase.isBreak) session.breakElapsedMs else session.focusedMs
        val live = if (session.phase.isRunning && session.bootCount == now.bootCount) {
            (now.elapsedMs - session.anchorElapsedMs).coerceAtLeast(0)
        } else 0
        return (total - committed - live).coerceAtLeast(0)
    }

    fun focusedMs(session: FocusSession, now: FocusInstant): Long =
        if (session.phase == FocusPhase.FOCUS_RUNNING) session.focusDurationMs - remainingMs(session, now) else session.focusedMs

    fun transition(original: FocusSession, action: FocusAction, now: FocusInstant): FocusChange {
        val settled = settle(original, now)
        var state = settled.session
        if (state.phase.isTerminal) return settled
        when (action) {
            FocusAction.CHECKPOINT -> Unit
            FocusAction.PAUSE -> if (state.phase.isRunning) {
                state = state.copy(
                    phase = if (state.phase.isBreak) FocusPhase.BREAK_PAUSED else FocusPhase.FOCUS_PAUSED,
                    pauses = state.pauses + FocusPause(now.wallMs, duringBreak = state.phase.isBreak)
                )
            }
            FocusAction.RESUME -> if (state.phase.isPaused) {
                state = state.copy(
                    phase = if (state.phase.isBreak) FocusPhase.BREAK_RUNNING else FocusPhase.FOCUS_RUNNING,
                    anchorWallMs = now.wallMs, anchorElapsedMs = now.elapsedMs, bootCount = now.bootCount,
                    pauses = closePause(state.pauses, now.wallMs), recoveryMessage = null
                )
            }
            FocusAction.START_BREAK -> if (state.phase == FocusPhase.FOCUS_READY) {
                state = if (state.breakDurationMs == 0L) state.copy(phase = FocusPhase.FINISHED) else state.copy(
                    phase = FocusPhase.BREAK_RUNNING, anchorWallMs = now.wallMs,
                    anchorElapsedMs = now.elapsedMs, bootCount = now.bootCount
                )
            }
            FocusAction.FINISH -> {
                val completed = state.outcome == "COMPLETED"
                state = state.copy(
                    phase = if (completed) FocusPhase.FINISHED else FocusPhase.ABANDONED,
                    outcome = if (completed) "COMPLETED" else "ABANDONED",
                    endedAt = state.endedAt ?: now.wallMs,
                    pauses = closePause(state.pauses, now.wallMs)
                )
            }
        }
        return settled.copy(session = state)
    }

    private fun settle(state: FocusSession, now: FocusInstant): FocusChange {
        if (!state.phase.isRunning) return FocusChange(state)
        if (state.bootCount != now.bootCount || now.elapsedMs < state.anchorElapsedMs) {
            return FocusChange(state.copy(
                phase = if (state.phase.isBreak) FocusPhase.BREAK_PAUSED else FocusPhase.FOCUS_PAUSED,
                bootCount = now.bootCount, anchorWallMs = now.wallMs, anchorElapsedMs = now.elapsedMs,
                pauses = state.pauses + FocusPause(now.wallMs, duringBreak = state.phase.isBreak),
                recoveryMessage = "检测到设备重启，已保留最近检查点并暂停；关机期间不计时。"
            ))
        }
        val isBreak = state.phase.isBreak
        val target = if (isBreak) state.breakDurationMs else state.focusDurationMs
        val committed = if (isBreak) state.breakElapsedMs else state.focusedMs
        val delta = (now.elapsedMs - state.anchorElapsedMs).coerceIn(0, (target - committed).coerceAtLeast(0))
        val reached = committed + delta >= target
        val next = state.copy(
            focusedMs = if (isBreak) state.focusedMs else state.focusedMs + delta,
            breakElapsedMs = if (isBreak) state.breakElapsedMs + delta else state.breakElapsedMs,
            anchorWallMs = now.wallMs, anchorElapsedMs = now.elapsedMs,
            phase = when { reached && isBreak -> FocusPhase.FINISHED; reached -> FocusPhase.FOCUS_READY; else -> state.phase },
            outcome = if (reached && !isBreak) "COMPLETED" else state.outcome,
            endedAt = if (reached && !isBreak) state.anchorWallMs + delta else state.endedAt
        )
        return FocusChange(
            next, if (!isBreak && delta > 0) FocusSpan(state.anchorWallMs, delta, state.zoneId) else null,
            focusFinished = reached && !isBreak, breakFinished = reached && isBreak
        )
    }

    private fun closePause(pauses: List<FocusPause>, now: Long): List<FocusPause> =
        pauses.map { if (it.endedAt == null) it.copy(endedAt = now.coerceAtLeast(it.startedAt)) else it }
}

/** 按专注发生时的时区跨午夜拆分，不将整轮时长错误地归到结束那一天。 */
fun focusByDate(spans: List<FocusSpan>): Map<LocalDate, Long> {
    val result = mutableMapOf<LocalDate, Long>()
    spans.forEach { span ->
        val zone = runCatching { ZoneId.of(span.zoneId) }.getOrDefault(ZoneId.systemDefault())
        var cursor = span.startedAt
        var remaining = span.durationMs.coerceAtLeast(0)
        while (remaining > 0) {
            val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val boundary = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val piece = minOf(remaining, (boundary - cursor).coerceAtLeast(1))
            result[day] = (result[day] ?: 0) + piece
            cursor += piece
            remaining -= piece
        }
    }
    return result
}

fun formatFocusTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 999) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

fun formatFocusDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "${seconds / 3600} 小时 ${seconds % 3600 / 60} 分钟"
        else "${seconds / 60} 分 ${seconds % 60} 秒"
}
