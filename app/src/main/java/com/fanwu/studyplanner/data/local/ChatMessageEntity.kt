package com.fanwu.studyplanner.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fanwu.studyplanner.domain.ChatMessage

/**
 * AI 对话消息的数据库实体
 * 按会话建立索引，每条消息属于一轮对话。
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index("conversationId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,          // "user" / "assistant" / "system"
    val content: String,
    val timestamp: Long
)

fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    role = role,
    content = content,
    timestamp = timestamp
)
