package com.fanwu.studyplanner.data.repository

import androidx.room.withTransaction
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.remote.*
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.reminders.ReminderRuntime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class ReviewAiReply(val reply: String, val events: List<CalendarEvent> = emptyList())

/** 打开发送确认弹窗前冻结摘要，确保发送内容与用户核对的一致。 */
data class ReviewSendRequest(val review: WeekReview, val nextMonday: LocalDate)

class ReviewRepository(private val db: AppDatabase, private val api: DeepSeekApi) {
    suspend fun generate(confirmed: ReviewSendRequest, apiKey: String): ReviewDraft {
        require(apiKey.isNotBlank()) { "请先在我的页面设置 API Key" }
        val start = confirmed.nextMonday
        val response = api.chatCompletions("Bearer $apiKey", ChatCompletionRequest(temperature = 0.3,
            messages = listOf(ApiMessage("system", "你是学习复盘助手。只依据用户提供的摘要，注明数据缺失；不要推测成绩或学习能力。返回 JSON：{\"reply\":\"中文建议\",\"events\":[{\"title\":\"任务\",\"date\":\"YYYY-MM-DD\",\"startTime\":\"HH:mm\",\"endTime\":\"HH:mm\",\"type\":\"STUDY_TASK\",\"subject\":\"科目或null\"}]}。events 可为空，只能新增 $start 至 ${start.plusDays(6)} 的学习任务，不得修改课程、考试或已有任务。"),
                ApiMessage("user", confirmed.review.text))))
        val text = response.choices.firstOrNull()?.message?.content.orEmpty().trim()
        val body = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<ReviewAiReply>(body)
        require(parsed.reply.isNotBlank() && parsed.events.size <= 50) { "AI 回复内容无效，请重试" }
        parsed.events.forEach { e ->
            val date = LocalDate.parse(e.date)
            require(e.type == EventType.STUDY_TASK && e.title.isNotBlank() && e.title.length <= 1000 && date >= start && date <= start.plusDays(6) &&
                Regex("\\d{2}:\\d{2}").matches(e.startTime) && Regex("\\d{2}:\\d{2}").matches(e.endTime) &&
                LocalTime.parse(e.startTime) < LocalTime.parse(e.endTime)) { "AI 建议包含无效任务；未写入日历，请重新生成" }
        }
        return ReviewDraft(confirmed.review.monday.toString(), confirmed.review.text, parsed.reply,
            parsed.events.map { it.copy(id = 0, isDone = false, completedAt = null, recurringCourseId = null, originalOccurrenceDate = null) }).also {
            db.documentDao().put(document(it.id, "review", it))
        }
    }

    suspend fun apply(draft: ReviewDraft) = ReminderRuntime.calendarMutation {
        db.withTransaction {
            val saved = db.documentDao().get(draft.id)?.decode<ReviewDraft>() ?: error("草稿不存在，请重新生成")
            check(!saved.applied) { "这份建议已应用，不会重复添加" }
            check(saved == draft && saved.events.isNotEmpty()) { "草稿已经变化，请重新打开" }
            validateEvents(saved.events)
            val all = db.calendarEventDao().getAll().map { it.toDomain() }
            val conflict = saved.events.flatMapIndexed { index, e -> (all + saved.events.take(index)).filter { overlaps(e, it) }.map { "${e.date} ${e.title} 与 ${it.title} 时间冲突" } }
            check(conflict.isEmpty()) { "${conflict.first()}。请编辑建议或重新生成，已有安排未改动。" }
            db.calendarEventDao().insertAll(saved.events.map { it.copy(planName = "复盘建议 ${saved.week}").toEntity() })
            db.documentDao().put(document(saved.id, "review", saved.copy(applied = true)))
        }
    }

    private fun validateEvents(events: List<CalendarEvent>) = events.forEach { e ->
        require(e.type == EventType.STUDY_TASK && e.id == 0L && e.title.isNotBlank() && LocalDate.parse(e.date) >= LocalDate.now() &&
            LocalDate.parse(e.date) <= LocalDate.now().plusYears(1) && LocalTime.parse(e.startTime) < LocalTime.parse(e.endTime)) { "建议任务的日期、时间或类型无效" }
    }

    suspend fun update(draft: ReviewDraft) {
        validateEvents(draft.events)
        db.withTransaction {
            val old = db.documentDao().get(draft.id)?.decode<ReviewDraft>() ?: error("草稿不存在")
            check(!old.applied && !draft.applied)
            db.documentDao().put(document(draft.id, "review", draft))
        }
    }
}
