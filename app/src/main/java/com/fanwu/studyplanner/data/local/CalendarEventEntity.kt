package com.fanwu.studyplanner.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.EventType

/**
 * 日历事件的数据库实体
 * 按日期建立索引，便于月视图按日期范围查询。
 */
@Entity(
    tableName = "calendar_events",
    indices = [Index("date")]
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String,          // YYYY-MM-DD
    val startTime: String,     // HH:mm
    val endTime: String,       // HH:mm
    val type: EventType,
    val note: String? = null,
    val isDone: Boolean = false,
    /** 所属计划组（即生成该计划的对话名），手动创建的事件为空 */
    val planName: String? = null,
    val completedAt: Long? = null,
    val subject: String? = null,
    val recurringCourseId: String? = null,
    val originalOccurrenceDate: String? = null
)

fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
    id = id,
    title = title,
    date = date,
    startTime = startTime,
    endTime = endTime,
    type = type,
    note = note,
    isDone = isDone,
    planName = planName, completedAt = completedAt, subject = subject,
    recurringCourseId = recurringCourseId, originalOccurrenceDate = originalOccurrenceDate
)

fun CalendarEvent.toEntity(): CalendarEventEntity = CalendarEventEntity(
    id = id,
    title = title,
    date = date,
    startTime = startTime,
    endTime = endTime,
    type = type,
    note = note,
    isDone = isDone,
    planName = planName, completedAt = completedAt, subject = subject,
    recurringCourseId = recurringCourseId, originalOccurrenceDate = originalOccurrenceDate
)
