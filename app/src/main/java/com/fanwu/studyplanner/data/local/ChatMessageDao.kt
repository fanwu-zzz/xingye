package com.fanwu.studyplanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    /** 按时间顺序观察全部对话消息 */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    /** 一次性读取全部对话消息，用于发送给模型 */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    /** 清空对话历史 */
    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}
