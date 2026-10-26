package com.fanwu.studyplanner.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** 版本化学习聚合；日历和专注时间片继续使用关系表。 */
@Serializable
@Entity(tableName = "planner_documents")
data class PlannerDocument(@PrimaryKey val id: String, val kind: String, val payload: String)

@Dao
interface PlannerDocumentDao {
    @Query("SELECT * FROM planner_documents ORDER BY id")
    fun observeAll(): Flow<List<PlannerDocument>>
    @Query("SELECT * FROM planner_documents ORDER BY id")
    suspend fun all(): List<PlannerDocument>
    @Query("SELECT * FROM planner_documents WHERE id = :id")
    suspend fun get(id: String): PlannerDocument?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: PlannerDocument)
    @Query("DELETE FROM planner_documents WHERE id = :id")
    suspend fun delete(id: String)
}
