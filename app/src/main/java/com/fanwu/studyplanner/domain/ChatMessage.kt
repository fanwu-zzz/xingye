package com.fanwu.studyplanner.domain

/**
 * AI 对话消息
 * role 取值："user" / "assistant" / "system"
 */
data class ChatMessage(
    val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long
)
