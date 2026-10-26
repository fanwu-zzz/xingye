package com.fanwu.studyplanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fanwu.studyplanner.domain.ConversationMode

/**
 * 一轮 AI 对话（会话）的数据库实体
 */
@Entity(tableName = "conversations")
@kotlinx.serialization.Serializable
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** 对话用途：制定学习计划 / 导入课表 */
    val mode: ConversationMode = ConversationMode.STUDY_PLAN
)
