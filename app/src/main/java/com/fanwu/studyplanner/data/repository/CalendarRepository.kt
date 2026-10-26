package com.fanwu.studyplanner.data.repository

import com.fanwu.studyplanner.data.local.CalendarEventDao
import com.fanwu.studyplanner.data.local.toDomain
import com.fanwu.studyplanner.data.local.toEntity
import com.fanwu.studyplanner.domain.CalendarEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.fanwu.studyplanner.reminders.ReminderRuntime

/**
 * 日历事件仓库：在数据库实体与领域模型之间转换
 */
class CalendarRepository(private val dao: CalendarEventDao, private val database: com.fanwu.studyplanner.data.local.AppDatabase? = null) {

    fun observeAllTasks(): Flow<List<CalendarEvent>> =
        dao.observeAllTasks().map { entities -> entities.map { it.toDomain() } }

    fun observeBetween(startDate: String, endDate: String): Flow<List<CalendarEvent>> =
        dao.observeByDateRange(startDate, endDate)
            .map { entities -> entities.map { it.toDomain() } }

    suspend fun insert(event: CalendarEvent): Long = ReminderRuntime.calendarMutation { dao.insert(event.toEntity()) }

    suspend fun update(event: CalendarEvent) {
        if (event.recurringCourseId != null && database != null) LearningRepository(database).except(event, event)
        else ReminderRuntime.calendarMutation { dao.update(event.toEntity()) }
    }

    suspend fun delete(event: CalendarEvent) {
        if (event.recurringCourseId != null && database != null) LearningRepository(database).except(event, null)
        else ReminderRuntime.calendarMutation { dao.delete(event.toEntity()) }
    }

    /** 从今天起未完成的任务，按截止时间升序 */
    fun observePending(today: String): Flow<List<CalendarEvent>> =
        dao.observePending(today)
            .map { entities -> entities.map { it.toDomain() } }

    suspend fun setDone(id: Long, done: Boolean) = ReminderRuntime.calendarMutation { dao.setDone(id, done) }

    /** 删除整个计划组，日历上对应的日程同时删除 */
    suspend fun deletePlan(planName: String) {
        if (database != null) LearningRepository(database).removePlan(planName)
        else ReminderRuntime.calendarMutation { dao.deleteByPlanName(planName) }
    }
}
