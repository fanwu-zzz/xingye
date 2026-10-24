package com.fanwu.studyplanner.domain

/**
 * 一轮 AI 对话（会话）
 * @param messageCount 该会话下的消息数量
 */
data class Conversation(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val messageCount: Int = 0,
    val mode: ConversationMode = ConversationMode.STUDY_PLAN
)

/** 对话用途 */
enum class ConversationMode {
    /** 和 AI 讨论学习目标，生成学习计划 */
    STUDY_PLAN,

    /** 导入课表文件，由 AI 识别后写入日历 */
    IMPORT_SCHEDULE
}
