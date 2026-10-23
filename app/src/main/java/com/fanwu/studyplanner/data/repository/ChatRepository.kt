package com.fanwu.studyplanner.data.repository

import com.fanwu.studyplanner.BuildConfig
import com.fanwu.studyplanner.data.local.ApiKeyStore
import com.fanwu.studyplanner.data.local.CalendarEventDao
import com.fanwu.studyplanner.data.local.ChatMessageDao
import com.fanwu.studyplanner.data.local.ChatMessageEntity
import com.fanwu.studyplanner.data.local.toDomain
import com.fanwu.studyplanner.data.local.toEntity
import com.fanwu.studyplanner.data.remote.ApiMessage
import com.fanwu.studyplanner.data.remote.ChatCompletionRequest
import com.fanwu.studyplanner.data.remote.DeepSeekApi
import com.fanwu.studyplanner.data.remote.FunctionDefinition
import com.fanwu.studyplanner.data.remote.ToolCall
import com.fanwu.studyplanner.data.remote.ToolDefinition
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.ChatMessage
import com.fanwu.studyplanner.domain.EventType
import com.fanwu.studyplanner.domain.StudyPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * AI 对话仓库：保存对话历史、调用 DeepSeek，并处理学习计划的生成与写入
 */
class ChatRepository(
    private val api: DeepSeekApi,
    private val dao: ChatMessageDao,
    private val calendarDao: CalendarEventDao,
    private val apiKeyStore: ApiKeyStore
) {

    fun observeMessages(): Flow<List<ChatMessage>> =
        dao.observeAll().map { messages -> messages.map { it.toDomain() } }

    /**
     * 保存用户消息、携带完整历史请求模型、再保存模型回复。
     */
    suspend fun sendMessage(text: String): Result<Unit> = try {
        val apiKey = resolveApiKey()

        dao.insert(
            ChatMessageEntity(
                role = "user",
                content = text,
                timestamp = System.currentTimeMillis()
            )
        )

        val history = dao.getAll()
        val request = ChatCompletionRequest(
            messages = buildList {
                add(ApiMessage(role = "system", content = SYSTEM_PROMPT + todayContext()))
                history.forEach { message ->
                    add(ApiMessage(role = message.role, content = message.content))
                }
            }
        )

        val response = api.chatCompletions("Bearer $apiKey", request)
        val reply = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (reply.isEmpty()) {
            throw IllegalStateException("模型没有返回内容")
        }

        dao.insert(
            ChatMessageEntity(
                role = "assistant",
                content = reply,
                timestamp = System.currentTimeMillis()
            )
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.toFriendlyMessage(), e))
    }

    /**
     * 根据对话历史生成学习计划：模型通过 add_event 工具返回结构化事件。
     */
    suspend fun generatePlan(): Result<StudyPlan> = try {
        val apiKey = resolveApiKey()
        val history = dao.getAll()
        if (history.none { it.role == "user" }) {
            throw IllegalStateException("请先和 AI 聊几句，再生成计划")
        }

        val today = LocalDate.now()
        val existing = calendarDao.getByDateRange(
            startDate = today.toString(),
            endDate = today.plusDays(30).toString()
        )
        val existingText = if (existing.isEmpty()) {
            "暂无"
        } else {
            existing.joinToString("\n") { event ->
                "${event.date} ${event.startTime}-${event.endTime} ${event.title}"
            }
        }

        val request = ChatCompletionRequest(
            messages = buildList {
                add(ApiMessage(role = "system", content = PLAN_SYSTEM_PROMPT + todayContext()))
                add(ApiMessage(role = "system", content = "未来 30 天已有安排：\n$existingText"))
                history.forEach { message ->
                    add(ApiMessage(role = message.role, content = message.content))
                }
                add(
                    ApiMessage(
                        role = "user",
                        content = "请根据以上对话制定学习计划，并为每个任务调用 add_event 工具。"
                    )
                )
            },
            tools = listOf(ADD_EVENT_TOOL),
            toolChoice = "auto",
            temperature = 0.3
        )

        val response = api.chatCompletions("Bearer $apiKey", request)
        val choice = response.choices.firstOrNull()
            ?: throw IllegalStateException("模型没有返回内容")

        val toolCalls = choice.message.toolCalls.orEmpty()
        val events = toolCalls.mapNotNull { parseEvent(it) }
        if (events.isEmpty()) {
            throw IllegalStateException(
                if (toolCalls.isEmpty()) {
                    "模型没有生成可写入的事件，请补充更具体的考试日期或时间后重试"
                } else {
                    "模型生成的事件日期无效（需晚于今天且不超过一年），请补充具体考试日期后重试"
                }
            )
        }

        val summary = choice.message.content?.trim().orEmpty()
            .ifBlank { "已根据对话生成 ${events.size} 个学习任务" }
        Result.success(StudyPlan(events = events, summary = summary))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.toFriendlyMessage(), e))
    }

    /**
     * 把确认后的计划批量写入日历。
     */
    suspend fun applyPlan(plan: StudyPlan): Result<Int> = try {
        calendarDao.insertAll(plan.events.map { it.toEntity() })
        Result.success(plan.events.size)
    } catch (e: Exception) {
        Result.failure(IllegalStateException("写入日历失败：${e.message ?: "未知错误"}", e))
    }

    suspend fun clearHistory() {
        dao.clear()
    }

    private fun resolveApiKey(): String {
        val apiKey = apiKeyStore.apiKey.ifBlank { BuildConfig.AI_API_KEY }
        if (apiKey.isBlank()) {
            throw IllegalStateException("未配置 API Key，请先在设置页填写")
        }
        return apiKey
    }

    /** 把模型返回的工具调用解析为日历事件，格式不合法则丢弃 */
    private fun parseEvent(call: ToolCall): CalendarEvent? {
        if (call.function.name != ADD_EVENT_TOOL_NAME) return null

        val args = runCatching {
            json.parseToJsonElement(call.function.arguments).jsonObject
        }.getOrNull() ?: return null

        val title = args.stringValue("title")?.trim().orEmpty()
        val date = args.stringValue("date")?.trim().orEmpty()
        val start = args.stringValue("startTime")?.trim().orEmpty()
        val end = args.stringValue("endTime")?.trim().orEmpty()

        if (title.isEmpty()) return null
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val today = LocalDate.now()
        // 拒绝模型因不知道当前日期而生成的过去日期，以及过远的日期
        if (parsedDate.isBefore(today) || parsedDate.isAfter(today.plusYears(1))) return null
        val startTime = runCatching { LocalTime.parse(start, TIME_FORMAT) }.getOrNull() ?: return null
        val endTime = runCatching { LocalTime.parse(end, TIME_FORMAT) }.getOrNull() ?: return null
        if (!endTime.isAfter(startTime)) return null

        val type = runCatching {
            EventType.valueOf(args.stringValue("type").orEmpty())
        }.getOrDefault(EventType.STUDY_TASK)

        return CalendarEvent(
            title = title,
            date = date,
            startTime = start,
            endTime = end,
            type = type,
            note = args.stringValue("note")?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    /** 告诉模型今天的日期，避免相对日期（如"下个月 15 号"）被推算错 */
    private fun todayContext(): String {
        val today = LocalDate.now()
        val weekday = WEEKDAYS[today.dayOfWeek.value - 1]
        return "今天是 $today（$weekday）。所有日期必须基于今天计算，禁止生成今天之前的日期。"
    }

    private fun Exception.toFriendlyMessage(): String = when (this) {
        is HttpException -> {
            val detail = runCatching {
                val body = response()?.errorBody()?.string()
                json.parseToJsonElement(body.orEmpty())
                    .jsonObject["error"]
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull()
            "请求失败（HTTP ${code()}）" + if (detail.isNullOrBlank()) "" else "：$detail"
        }
        is IOException -> "网络连接失败，请检查网络后重试"
        else -> message ?: "发送失败"
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "你是一个中文学习计划助手。请和用户讨论学习目标、考试安排和时间限制，" +
                "帮助用户制定具体、可执行的学习计划。回答简洁清晰，必要时给出时间安排建议。"

        const val PLAN_SYSTEM_PROMPT =
            "你是一个中文学习计划助手。请根据对话历史制定可执行的学习计划：" +
                "先给出简短的计划说明，然后为每个学习任务调用 add_event 工具。" +
                "日期使用 YYYY-MM-DD 格式，时间使用 HH:mm 格式，" +
                "类型从 EXAM（考试）、CLASS（课程）、STUDY_TASK（学习任务）中选择。" +
                "避免与已有安排冲突，任务时长建议 30 分钟到 2 小时。"

        const val ADD_EVENT_TOOL_NAME = "add_event"

        val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val ADD_EVENT_TOOL = ToolDefinition(
            function = FunctionDefinition(
                name = ADD_EVENT_TOOL_NAME,
                description = "在用户日历中添加一个学习任务或考试安排。生成学习计划时每个任务调用一次。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") {
                            put("type", "string")
                            put("description", "事件标题，例如：复习线性代数第一章")
                        }
                        putJsonObject("date") {
                            put("type", "string")
                            put("description", "日期，格式 YYYY-MM-DD")
                        }
                        putJsonObject("startTime") {
                            put("type", "string")
                            put("description", "开始时间，格式 HH:mm")
                        }
                        putJsonObject("endTime") {
                            put("type", "string")
                            put("description", "结束时间，格式 HH:mm")
                        }
                        putJsonObject("type") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("EXAM")
                                add("CLASS")
                                add("STUDY_TASK")
                            }
                        }
                        putJsonObject("note") {
                            put("type", "string")
                            put("description", "备注，可选")
                        }
                    }
                    putJsonArray("required") {
                        add("title")
                        add("date")
                        add("startTime")
                        add("endTime")
                        add("type")
                    }
                }
            )
        )
    }
}
