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
    val note: String? = null
)
