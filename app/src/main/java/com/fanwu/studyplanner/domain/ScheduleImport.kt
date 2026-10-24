package com.fanwu.studyplanner.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.contentOrNull

/**
 * AI 从课表文件里识别出的一条课程安排（未展开成具体日期）
 * @param dayOfWeek 1 = 周一 … 7 = 周日
 * @param weeks 原始周次写法，如 "1-4,6-14"，由本地代码展开
 * @param periods 原始节次写法，如 "6-9"
 */
@Serializable
data class CourseSegment(
    val name: String = "",
    @Serializable(with = DayOfWeekSerializer::class)
    val dayOfWeek: Int = 0,
    @Serializable(with = TextOrListSerializer::class)
    val weeks: String = "",
    @Serializable(with = TextOrListSerializer::class)
    val periods: String = "",
    val room: String = ""
)

/** 未能导入的原始条目，用于在摘要里告知用户 */
@Serializable
data class DroppedEntry(
    val raw: String = "",
    val reason: String = ""
)

/**
 * AI 解析课表文件后的完整结果
 */
@Serializable
data class ScheduleImport(
    val status: String = "ok",
    val missing: List<String> = emptyList(),
    @SerialName("semesterStartMonday")
    val semesterStartMonday: String? = null,
    @SerialName("periodTimeTable")
    val periodTimeTable: Map<String, String>? = null,
    val courses: List<CourseSegment> = emptyList(),
    val dropped: List<DroppedEntry> = emptyList(),
    val message: String = ""
) {
    val needsMoreInfo: Boolean get() = status == "need_info"
    val isNotSchedule: Boolean get() = status == "not_schedule"
}

/** 模型偶尔会把周次/节次输出成数组，这里统一拼回逗号分隔的字符串 */
object TextOrListSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonArray) return element
        return JsonPrimitive(
            element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
                .joinToString(",")
        )
    }
}

/** 模型偶尔会把星期写成「星期一」这类中文，这里统一转成 1..7 */
object DayOfWeekSerializer : JsonTransformingSerializer<Int>(Int.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val text = (element as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        text.toIntOrNull()?.let { return JsonPrimitive(it) }
        val value = when {
            text.contains("周一") || text.contains("星期 1") || text.contains("Monday", true) -> 1
            text.contains("周二") || text.contains("Tuesday", true) -> 2
            text.contains("周三") || text.contains("Wednesday", true) -> 3
            text.contains("周四") || text.contains("Thursday", true) -> 4
            text.contains("周五") || text.contains("Friday", true) -> 5
            text.contains("周六") || text.contains("Saturday", true) -> 6
            text.contains("周日") || text.contains("周天") ||
                text.contains("星期天") || text.contains("Sunday", true) -> 7
            text.contains("一") -> 1
            text.contains("二") -> 2
            text.contains("三") -> 3
            text.contains("四") -> 4
            text.contains("五") -> 5
            text.contains("六") -> 6
            text.contains("日") || text.contains("天") -> 7
            else -> 0
        }
        return JsonPrimitive(value)
    }
}
