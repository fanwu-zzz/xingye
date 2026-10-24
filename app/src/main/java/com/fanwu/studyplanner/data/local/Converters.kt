package com.fanwu.studyplanner.data.local

import androidx.room.TypeConverter
import com.fanwu.studyplanner.domain.ConversationMode
import com.fanwu.studyplanner.domain.EventType

/**
 * Room 类型转换：枚举以枚举名（字符串）形式存储
 */
class Converters {

    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter
    fun fromConversationMode(value: ConversationMode): String = value.name

    @TypeConverter
    fun toConversationMode(value: String): ConversationMode =
        runCatching { ConversationMode.valueOf(value) }
            .getOrDefault(ConversationMode.STUDY_PLAN)
}
