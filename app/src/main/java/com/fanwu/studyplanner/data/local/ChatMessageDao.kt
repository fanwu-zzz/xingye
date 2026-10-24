package com.fanwu.studyplanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    /** 按时间顺序观察某轮对话的消息 */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeByConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    /** 一次性读取某轮对话的消息，用于发送给模型 */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getByConversation(conversationId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    /** 清空某轮对话的历史 */
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clear(conversationId: Long)
}
