package com.fanwu.studyplanner.data.repository

import com.fanwu.studyplanner.data.local.CalendarEventDao
import com.fanwu.studyplanner.data.local.toDomain
import com.fanwu.studyplanner.data.local.toEntity
import com.fanwu.studyplanner.domain.CalendarEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 日历事件仓库：在数据库实体与领域模型之间转换
 */
class CalendarRepository(private val dao: CalendarEventDao) {

    fun observeBetween(startDate: String, endDate: String): Flow<List<CalendarEvent>> =
        dao.observeByDateRange(startDate, endDate)
            .map { entities -> entities.map { it.toDomain() } }

    suspend fun insert(event: CalendarEvent): Long = dao.insert(event.toEntity())

    suspend fun update(event: CalendarEvent) = dao.update(event.toEntity())

    suspend fun delete(event: CalendarEvent) = dao.delete(event.toEntity())
}
