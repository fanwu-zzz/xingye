package com.fanwu.studyplanner.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 日历事件类型
 */
@kotlinx.serialization.Serializable
enum class EventType {
    EXAM,       // 考试
    CLASS,      // 课程
    STUDY_TASK, // 有起止时间的学习安排
    DEADLINE    // 只记录截止时刻的待办，startTime 与 endTime 相同
}

/**
 * 日历事件（业务模型）
 * 日期统一使用 YYYY-MM-DD，时间统一使用 HH:mm，便于排序和按日期查询。
 */
@kotlinx.serialization.Serializable
data class CalendarEvent(
    val id: Long = 0,
    val title: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val type: EventType,
    val note: String? = null,
    /** 是否已完成，用于统计页和任务页 */
    val isDone: Boolean = false,
    /** 所属计划组（生成该计划的对话名），null 表示手动创建、未分组 */
    val planName: String? = null,
    val completedAt: Long? = null,
    val subject: String? = null,
    val recurringCourseId: String? = null,
    val originalOccurrenceDate: String? = null
)

/** 事件时长（分钟），时间串非法时按 0 计 */
fun CalendarEvent.durationMinutes(): Int {
    if (type == EventType.DEADLINE) return 0
    val start = startTime.toMinutesOrNull() ?: return 0
    val end = endTime.toMinutesOrNull() ?: return 0
    return (end - start).coerceAtLeast(0)
}

/** 截止事项是一个时刻，不占用日历中的学习时段。 */
fun CalendarEvent.hasValidTimeRange(): Boolean = runCatching {
    val format = Regex("\\d{2}:\\d{2}")
    if (!format.matches(startTime) || !format.matches(endTime)) return@runCatching false
    val start = LocalTime.parse(startTime)
    val end = LocalTime.parse(endTime)
    if (type == EventType.DEADLINE) start == end else start < end
}.getOrDefault(false)

fun CalendarEvent.timeText(): String =
    if (type == EventType.DEADLINE) "截止 $endTime" else "$startTime — $endTime"

fun CalendarEvent.isOverdue(now: LocalDateTime = LocalDateTime.now()): Boolean =
    !isDone && runCatching { !LocalDate.parse(date).atTime(LocalTime.parse(endTime)).isAfter(now) }.getOrDefault(false)

val EventType.isTask: Boolean get() = this == EventType.STUDY_TASK || this == EventType.DEADLINE

/** 分钟数格式化为 "6h" / "2.5h" */
fun formatHours(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60}h" else "%.1fh".format(minutes / 60f)

/**
 * 课程在下课时间过后自动视为已完成。
 * 仅在展示层判定，不写回数据库，避免污染用户手动维护的数据。
 */
fun CalendarEvent.effectiveDone(now: LocalDateTime = LocalDateTime.now()): Boolean =
    isDone || (type == EventType.CLASS && hasFinishedAt(now))

private fun CalendarEvent.hasFinishedAt(now: LocalDateTime): Boolean = runCatching {
    LocalDate.parse(date).atTime(LocalTime.parse(endTime)).isBefore(now)
}.getOrDefault(false)

private fun String.toMinutesOrNull(): Int? = runCatching {
    val (h, m) = split(":").map { it.toInt() }
    h * 60 + m
}.getOrNull()
