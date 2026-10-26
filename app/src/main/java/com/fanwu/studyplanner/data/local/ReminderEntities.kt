package com.fanwu.studyplanner.data.local

import androidx.room.*
import com.fanwu.studyplanner.domain.ReminderSettings
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminder_settings")
@kotlinx.serialization.Serializable
data class ReminderSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val tasksEnabled: Boolean = false,
    val coursesEnabled: Boolean = false,
    val leadMinutes: Int = 15,
    val quietEnabled: Boolean = false,
    val quietStart: String = "23:00",
    val quietEnd: String = "07:00"
) {
    fun toDomain() = ReminderSettings(tasksEnabled, coursesEnabled, leadMinutes, quietEnabled, quietStart, quietEnd)
}

fun ReminderSettings.toEntity() = ReminderSettingsEntity(1, tasksEnabled, coursesEnabled, leadMinutes, quietEnabled, quietStart, quietEnd)

/** 每个事件保留一次发送凭据；关闭再开启、进程重建、重复广播不会重发。 */
@Entity(tableName = "event_reminders", foreignKeys = [ForeignKey(
    entity = CalendarEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE
)])
data class EventReminderEntity(
    @PrimaryKey val eventId: Long,
    val occurrence: String,
    val fireAt: Long,
    val state: String = "PENDING"
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_settings WHERE id = 1")
    suspend fun settings(): ReminderSettingsEntity?
    @Query("SELECT * FROM reminder_settings WHERE id = 1")
    fun observeSettings(): Flow<ReminderSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ReminderSettingsEntity)
    @Query("SELECT * FROM event_reminders")
    suspend fun all(): List<EventReminderEntity>
    @Query("SELECT * FROM event_reminders WHERE state = 'PENDING' ORDER BY fireAt, eventId")
    fun observePending(): Flow<List<EventReminderEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(reminder: EventReminderEntity)
    @Query("DELETE FROM event_reminders WHERE eventId = :id")
    suspend fun delete(id: Long)
}
