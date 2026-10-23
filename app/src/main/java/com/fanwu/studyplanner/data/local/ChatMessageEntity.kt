package com.fanwu.studyplanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fanwu.studyplanner.domain.ChatMessage

/**
 * AI 对话消息的数据库实体
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
    id = id,
    role = role,
    content = content,
    timestamp = timestamp
)
