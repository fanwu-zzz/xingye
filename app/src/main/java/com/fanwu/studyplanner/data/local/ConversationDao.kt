package com.fanwu.studyplanner.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 会话及其消息数量的查询结果 */
data class ConversationWithCount(
    @Embedded val conversation: ConversationEntity,
    val messageCount: Int
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY id")
    suspend fun all(): List<ConversationEntity>

    /** 全部会话（最新的在前），附带消息数量 */
    @Query(
        "SELECT conversations.*, " +
            "(SELECT COUNT(*) FROM chat_messages WHERE chat_messages.conversationId = conversations.id) AS messageCount " +
            "FROM conversations ORDER BY createdAt DESC"
    )
    fun observeWithCount(): Flow<List<ConversationWithCount>>

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** 删除会话本身，其消息由调用方一并清理 */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
}
