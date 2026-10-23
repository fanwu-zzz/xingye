package com.fanwu.studyplanner.domain

/**
 * 日历事件类型
 */
enum class EventType {
    EXAM,       // 考试
    CLASS,      // 课程
    STUDY_TASK  // 学习任务
}

/**
 * 日历事件（业务模型）
 * 日期统一使用 YYYY-MM-DD，时间统一使用 HH:mm，便于排序和按日期查询。
 */
data class CalendarEvent(
    val id: Long = 0,
    val title: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val type: EventType,
    val note: String? = null,
    /** 是否已完成，用于统计页和任务页 */
    val isDone: Boolean = false
)

/** 事件时长（分钟），时间串非法时按 0 计 */
fun CalendarEvent.durationMinutes(): Int {
    val start = startTime.toMinutesOrNull() ?: return 0
    val end = endTime.toMinutesOrNull() ?: return 0
    return (end - start).coerceAtLeast(0)
}

/** 分钟数格式化为 "6h" / "2.5h" */
fun formatHours(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60}h" else "%.1fh".format(minutes / 60f)

private fun String.toMinutesOrNull(): Int? = runCatching {
    val (h, m) = split(":").map { it.toInt() }
    h * 60 + m
}.getOrNull()
