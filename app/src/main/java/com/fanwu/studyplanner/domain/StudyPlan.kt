package com.fanwu.studyplanner.domain

/**
 * AI 生成的学习计划，用户确认后写入日历
 */
data class StudyPlan(
    val events: List<CalendarEvent>,
    val summary: String
)
