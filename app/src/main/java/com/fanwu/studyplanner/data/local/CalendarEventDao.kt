package com.fanwu.studyplanner.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {

    @Query("SELECT * FROM calendar_events ORDER BY date, startTime, id")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY date, startTime, id")
    suspend fun getAll(): List<CalendarEventEntity>

    @Transaction
    suspend fun applyEvents(events: List<CalendarEventEntity>, replacePlanName: String?) {
        if (replacePlanName != null) deleteByPlanName(replacePlanName)
        insertAll(events)
    }

    /** 任务中心包含历史、逾期与已完成记录，课程仍只在课表展示。 */
    @Query("SELECT * FROM calendar_events WHERE type != 'CLASS' ORDER BY date ASC, endTime ASC")
    fun observeAllTasks(): Flow<List<CalendarEventEntity>>

    /** 查询某一天的事件，按开始时间排序 */
    @Query("SELECT * FROM calendar_events WHERE date = :date ORDER BY startTime ASC")
    fun observeByDate(date: String): Flow<List<CalendarEventEntity>>

    /** 查询日期范围内的事件，供月视图使用 */
    @Query(
        "SELECT * FROM calendar_events WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC, startTime ASC"
    )
    fun observeByDateRange(startDate: String, endDate: String): Flow<List<CalendarEventEntity>>

    /** 按 id 查询单条事件 */
    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getById(id: Long): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    fun observeById(id: Long): Flow<CalendarEventEntity?>

    /** 一次性读取日期范围内的事件（生成计划时用于避免冲突） */
    @Query(
        "SELECT * FROM calendar_events WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC, startTime ASC"
    )
    suspend fun getByDateRange(startDate: String, endDate: String): List<CalendarEventEntity>

    @Insert
    suspend fun insert(event: CalendarEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<CalendarEventEntity>): List<Long>

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Delete
    suspend fun delete(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 查询从今天起未完成的任务，按截止时间（日期+结束时间）排序；课程不计入待办 */
    @Query(
        "SELECT * FROM calendar_events WHERE isDone = 0 AND date >= :today AND type != 'CLASS' " +
            "ORDER BY date ASC, endTime ASC"
    )
    fun observePending(today: String): Flow<List<CalendarEventEntity>>

    /** 标记完成 / 取消完成 */
    @Query("UPDATE calendar_events SET isDone = :done, completedAt = CASE WHEN :done = 0 THEN NULL WHEN isDone = 1 THEN completedAt ELSE :at END WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, at: Long = System.currentTimeMillis())

    /** 删除某个计划组下的全部事件（日历上对应日期的日程一并消失） */
    @Query("DELETE FROM calendar_events WHERE planName = :planName")
    suspend fun deleteByPlanName(planName: String)
}
