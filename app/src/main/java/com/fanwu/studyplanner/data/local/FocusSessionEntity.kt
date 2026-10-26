package com.fanwu.studyplanner.data.local

import androidx.room.*
import com.fanwu.studyplanner.domain.FocusSession
import com.fanwu.studyplanner.domain.FocusSpan
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "focus_sessions", indices = [Index(value = ["activeSlot"], unique = true)])
@kotlinx.serialization.Serializable
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val activeSlot: Int?,
    val startedAt: Long,
    val endedAt: Long?,
    val focusedMs: Long,
    val outcome: String,
    val payload: String
)

@Entity(tableName = "focus_intervals", indices = [Index("sessionId")],
    foreignKeys = [ForeignKey(entity = FocusSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
@kotlinx.serialization.Serializable
data class FocusIntervalEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val startedAt: Long,
    val durationMs: Long,
    val zoneId: String
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt")
    suspend fun allSessions(): List<FocusSessionEntity>
    @Query("SELECT * FROM focus_intervals ORDER BY startedAt")
    suspend fun allIntervals(): List<FocusIntervalEntity>
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt")
    fun observeSessions(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions WHERE activeSlot = 1 LIMIT 1")
    suspend fun active(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE activeSlot = 1 LIMIT 1")
    fun observeActive(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE activeSlot IS NULL ORDER BY startedAt DESC LIMIT 30")
    fun observeHistory(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_intervals ORDER BY startedAt")
    fun observeIntervals(): Flow<List<FocusIntervalEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: FocusSessionEntity)

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInterval(interval: FocusIntervalEntity)

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun countSessions(): Int

    @Query("SELECT * FROM focus_intervals WHERE sessionId = :id ORDER BY startedAt")
    suspend fun intervalsFor(id: String): List<FocusIntervalEntity>
}

private val focusJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun FocusSessionEntity.toFocusSession(): FocusSession = focusJson.decodeFromString(payload)
fun FocusSession.toEntity() = FocusSessionEntity(
    id, if (phase.isTerminal) null else 1, startedAt, endedAt, focusedMs, outcome, focusJson.encodeToString(this)
)
fun FocusIntervalEntity.toFocusSpan() = FocusSpan(startedAt, durationMs, zoneId)
