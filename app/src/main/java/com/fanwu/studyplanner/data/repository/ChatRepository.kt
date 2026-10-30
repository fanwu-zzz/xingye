package com.fanwu.studyplanner.data.repository

import com.fanwu.studyplanner.BuildConfig
import com.fanwu.studyplanner.reminders.ReminderRuntime
import com.fanwu.studyplanner.data.local.ApiKeyStore
import com.fanwu.studyplanner.data.local.CalendarEventDao
import com.fanwu.studyplanner.data.local.ChatMessageDao
import com.fanwu.studyplanner.data.local.ChatMessageEntity
import com.fanwu.studyplanner.data.local.ConversationDao
import com.fanwu.studyplanner.data.local.ConversationEntity
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
import com.fanwu.studyplanner.domain.Conversation
import com.fanwu.studyplanner.domain.ConversationMode
import com.fanwu.studyplanner.domain.EventType
import com.fanwu.studyplanner.domain.ScheduleImport
import com.fanwu.studyplanner.domain.StudyPlan
import com.fanwu.studyplanner.domain.hasValidTimeRange
import com.fanwu.studyplanner.domain.timeText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * AI 对话仓库：管理多轮会话、保存对话历史、调用 DeepSeek，并处理学习计划的生成与写入
 */
class ChatRepository(
    private val api: DeepSeekApi,
    private val dao: ChatMessageDao,
    private val conversationDao: ConversationDao,
    private val calendarDao: CalendarEventDao,
    private val apiKeyStore: ApiKeyStore
) {

    fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeWithCount().map { rows ->
            rows.map {
                Conversation(
                    id = it.conversation.id,
                    name = it.conversation.name,
                    createdAt = it.conversation.createdAt,
                    messageCount = it.messageCount,
                    mode = it.conversation.mode
                )
            }
        }

    /** 新建一轮对话，返回会话 id */
    suspend fun createConversation(
        name: String,
        mode: ConversationMode = ConversationMode.STUDY_PLAN
    ): Long =
        conversationDao.insert(
            ConversationEntity(name = name, createdAt = System.currentTimeMillis(), mode = mode)
        )

    suspend fun renameConversation(id: Long, name: String) = conversationDao.rename(id, name)

    /** 删除一轮对话及其全部消息（已应用到日历的计划不受影响） */
    suspend fun deleteConversation(id: Long) {
        dao.clear(id)
        conversationDao.delete(id)
    }

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        dao.observeByConversation(conversationId)
            .map { messages -> messages.map { it.toDomain() } }

    /**
     * 保存用户消息、携带本会话完整历史请求模型、再保存模型回复。
     */
    suspend fun sendMessage(conversationId: Long, text: String): Result<Unit> = try {
        val apiKey = resolveApiKey()

        insertUserMessage(conversationId, text)

        val history = dao.getByConversation(conversationId)
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
                conversationId = conversationId,
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
     * 根据本会话对话历史生成学习计划：模型通过 add_event 工具返回结构化事件。
     */
    suspend fun generatePlan(conversationId: Long): Result<StudyPlan> = try {
        val apiKey = resolveApiKey()
        val history = dao.getByConversation(conversationId)
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
                "${event.date} ${event.toDomain().timeText()} ${event.title}"
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
     * 把确认后的计划批量写入日历，组名记在每个事件的 planName 上，用于统计页按计划分组。
     * @param replaceExisting 为 true 时先删掉同名计划组，避免重复导入课表产生重复事件
     */
    suspend fun applyPlan(
        plan: StudyPlan,
        groupName: String,
        replaceExisting: Boolean = false
    ): Result<Int> = try {
        require(plan.events.all { it.hasValidTimeRange() }) { "计划中有无效时间，请重新生成预览" }
        val tag = groupName.trim().takeIf { it.isNotEmpty() }
        ReminderRuntime.calendarMutation {
            calendarDao.applyEvents(plan.events.map { it.copy(planName = tag).toEntity() }, tag.takeIf { replaceExisting })
        }
        Result.success(plan.events.size)
    } catch (e: Exception) {
        Result.failure(IllegalStateException("写入日历失败：${e.message ?: "未知错误"}", e))
    }

    /**
     * 课表导入：把文件文本（或用户对追问的回答）发给模型。
     * 模型只负责还原课程段，周次展开与时间换算由本地代码完成，避免模型算错日期。
     */
    suspend fun importSchedule(
        conversationId: Long, userText: String,
        applicationState: String = "尚未导入。解析成功后仍需用户打开预览并确认。"
    ): Result<ScheduleImport> =
        try {
            val apiKey = resolveApiKey()

            insertUserMessage(conversationId, userText)

            val history = dao.getByConversation(conversationId)
            val request = ChatCompletionRequest(
                messages = buildList {
                    add(
                        ApiMessage(
                            role = "system",
                            content = IMPORT_SCHEDULE_PROMPT +
                                "\n今天是 ${LocalDate.now()}。学期首周允许早于今天，不要为此改到下一年。"
                        )
                    )
                    history.forEach { message ->
                        add(ApiMessage(role = message.role, content = message.content))
                    }
                    add(ApiMessage(role = "system", content = "应用实际状态（优先于历史回复）：$applicationState"))
                },
                temperature = 0.4
            )

            val response = api.chatCompletions("Bearer $apiKey", request)
            val reply = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (reply.isEmpty()) {
                throw IllegalStateException("模型没有返回内容")
            }

            val parsed = parseScheduleImport(reply)
            dao.insert(
                ChatMessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = reply,
                    timestamp = System.currentTimeMillis()
                )
            )

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.toFriendlyMessage(), e))
        }

    suspend fun clearHistory(conversationId: Long) {
        dao.clear(conversationId)
    }

    /** 重进会话时恢复最近的结构化结果；普通问答不会覆盖课表草稿。 */
    suspend fun latestScheduleImport(conversationId: Long): ScheduleImport? =
        dao.getByConversation(conversationId).asReversed().firstNotNullOfOrNull { message ->
            if (message.role != "assistant") null else
                runCatching { parseScheduleImport(message.content) }.getOrNull()?.takeUnless { it.isChatReply }
        }

    private suspend fun insertUserMessage(conversationId: Long, text: String) {
        val last = dao.getByConversation(conversationId).lastOrNull()
        // 网络失败重试时复用已落库的用户消息，避免对话中出现连续重复内容。
        if (last?.role == "user" && last.content == text) return
        dao.insert(ChatMessageEntity(conversationId = conversationId, role = "user",
            content = text, timestamp = System.currentTimeMillis()))
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
        ).takeIf { it.hasValidTimeRange() }
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    /** 解析模型返回的课表 JSON；格式不对或明显无效时抛异常，交给上层提示用户 */
    private fun parseScheduleImport(reply: String): ScheduleImport {
        val payload = reply.substring(
            reply.indexOf('{').coerceAtLeast(0),
            reply.lastIndexOf('}').takeIf { it > reply.indexOf('{') }?.plus(1) ?: reply.length
        )

        val parsed = runCatching {
            scheduleJson.decodeFromString<ScheduleImport>(payload)
        }.getOrElse {
            throw IllegalStateException("课表解析结果格式不正确，请重试或换一份文件")
        }

        if (parsed.isNotSchedule) {
            throw IllegalStateException(parsed.message.ifBlank { "这份文件看起来不是课表" })
        }
        if (parsed.needsMoreInfo || parsed.isChatReply) return parsed
        require(parsed.status == "ok") { "课表解析状态无法识别，请重试" }
        if (parsed.courses.isEmpty()) {
            throw IllegalStateException(parsed.message.ifBlank { "没有从文件里识别到课程" })
        }

        // 学期首周必须是周一，否则整学期日期都会偏移
        val start = parsed.semesterStartMonday
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start == null) {
            return parsed.copy(
                status = "need_info",
                missing = parsed.missing.ifEmpty { listOf("semesterStartMonday") }
            )
        }
        val monday = start.with(DayOfWeek.MONDAY)
        return parsed.copy(semesterStartMonday = monday.toString())
    }

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
            "你是星页里的 AI 学习搭子，用自然、友善的中文和用户交流。先回答用户当前的问题，" +
                "结合已有信息讨论目标、考试和时间限制；只追问真正缺失的信息，不重复总结或套用固定开场。" +
                "建议具体可执行，简单问题简短回答，复杂问题再展开。计划需用户点击生成、预览并应用才会写入日历，不能声称已执行。"

        const val PLAN_SYSTEM_PROMPT =
            "你是一个中文学习计划助手。请根据对话历史制定可执行的学习计划：" +
                "先给出简短的计划说明，然后为每个学习任务调用 add_event 工具。" +
                "日期使用 YYYY-MM-DD 格式，时间使用 HH:mm 格式，" +
                "类型从 EXAM（考试）、CLASS（课程）、STUDY_TASK（安排学习时段）、DEADLINE（作业或报告等截止任务）中选择。" +
                "截止任务的 startTime 和 endTime 都填写同一个截止时刻；只有截止日期时使用 23:59，不编造学习时长。" +
                "截止任务不占用时段，实际复习或写作业另设 STUDY_TASK。避免与已有时段冲突，学习时段建议 30 分钟到 2 小时。"

        const val ADD_EVENT_TOOL_NAME = "add_event"

        /**
         * 课表导入的系统提示词：模型只做「表格 → 课程段」的识别，
         * 不换算日期、不生成日历事件，这两件事由本地代码完成。
         */
        const val IMPORT_SCHEDULE_PROMPT = """
你是星页的「课表导入助手」，负责识别课表，也用自然、友善的中文回答用户当前的问题。
你不负责换算日期，也不负责生成日历事件。
先理解用户这一轮在问什么：询问是否导入、操作方法或解释结果时，返回 status="chat"，
只在 message 中直接回答，不必重发课程和作息，不要机械复述上一轮识别摘要。
用户上传文件或补充、修正学期/作息/课程信息时，返回完整的最新结构化结果，保留前文已经确认的数据。
实际导入状态以程序提供的「应用实际状态」为准。识别完成仅表示待预览，不能声称已导入、已写入或已安排。
遇到本地校验问题时解释具体阻塞点，告诉用户还需补充什么；不要用“整理完成”掩盖失败。

# 一、输入格式
用户会发送一条以「【课表文件】」开头的消息，内容是本地程序把 Excel 转成的纯文本表格：
- 第一行说明文件名、工作表名、总行数与总列数；
- 之后是 TSV 正文，单元格之间用制表符分隔，空单元格保留为空以保证列对齐；
- 每行开头有 #行序号；
- 合并单元格的内容只出现在左上角单元格；
- 一个单元格内可能有多门课，用 ⏎ 分隔。

# 二、识别步骤
1. 先判断这是不是课表。如果是成绩单、工资表、招生计划等，返回 status="not_schedule" 并说明。
2. 找到星期表头（星期一~星期日 / 周一~周日 / Mon~Sun 等）。
3. 找到节次列或节次行（形如 一/二/三、第1节、1-2、上午/下午）。
4. 逐单元格抽取课程条目，识别四个字段：课程名、周次、节次、地点。
   各校的字段顺序和文案都不统一，请按语义判断，不要套死某一种格式。
5. 去重：同一单元格内完全相同的条目只保留一条。
   四节连排的课常被重复写在相邻两行（例如「三」「四」两行都写了 6-9 节），应取并集去重。
6. 检查本地参数是否齐全（见第四节）。

# 三、硬性规则
- 只提取表格中真实存在的内容。禁止编造课程名、地点、周次、节次。
- 周次保持原样输出："1-4,6-14"、"5-6,8,10,12"、"3" 都不要展开，更不要换算成具体日期。
- 节次保持原样输出："1-2"、"6-9"、"10-13"。
- 地点缺失时输出空字符串，禁止猜测。
- 跳周信息必须保留（"1-4,6-14" 表示跳过第 5 周）。
- 只有周次、没有星期和节次的条目（如「占周占时间」「占周不占时间」「实践环节」），
  放入 dropped，reason 写「仅含周次，无上课时间」。
- 无法识别的内容也放入 dropped 并说明原因，不要静默丢弃。

# 四、需要向用户补充的参数
1. semesterStartMonday：学期第 1 周的周一日期，格式 YYYY-MM-DD。
2. periodTimeTable：节次到时间的映射，如 {"1-2":"08:00-09:40","6-7":"14:00-15:40"}。
   若表格里已经写了具体时间，直接采用，不要重复询问。
   保留用户实际提供的单节或合并节次时间，不平均拆分、不套用默认作息。
   连堂课可以跨多个完整时间块，例如 6-9 节可使用 6-7 和 8-9 两块的首尾时间；
   但只有 6-9 节总时间时，无法得出 6-7 节何时结束，应返回 need_info 并具体询问缺少的边界时间。
缺失时：先把能解析的部分解析完，把缺失项放入 missing，
再用一句自然语言向用户询问。一次最多问两项，并给出示例方便用户照着回答。
用户回答「9月1日开学」这类模糊表述时，请结合当前日期推断年份，
只有能唯一确定时才填入，否则继续追问。

# 五、输出格式
只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释性文字：

{
  "status": "ok | need_info | not_schedule | chat",
  "missing": ["semesterStartMonday", "periodTimeTable"],
  "semesterStartMonday": "2025-09-01",
  "periodTimeTable": {"1-2": "08:00-09:40", "6-7": "14:00-15:40"},
  "courses": [
    {"name": "计算机网络", "dayOfWeek": 1, "weeks": "1-4,6-14", "periods": "6-7", "room": "D1219"}
  ],
  "dropped": [
    {"raw": "硬件综合设计 [18-20周]", "reason": "仅含周次，无上课时间"}
  ],
  "message": "课表里找到了 26 个课程段，还有 2 条只有周次，暂时无法安排时间。接下来请打开预览核对，确认后才会导入。"
}

dayOfWeek 用 1~7 表示周一到周日。message 直接展示给用户，应针对本轮问题自然回答，避免固定句式、重复参数和无关的总结。
status="chat" 时可仅返回 {"status":"chat","message":"针对当前问题的回答"}。
"""

        val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val scheduleJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        val ADD_EVENT_TOOL = ToolDefinition(
            function = FunctionDefinition(
                name = ADD_EVENT_TOOL_NAME,
                description = "生成一项待用户预览的学习安排、考试或截止任务。DEADLINE 的两个时间都必须等于截止时刻。",
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
                                add("DEADLINE")
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
