package com.fanwu.studyplanner.features

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.fanwu.studyplanner.BuildConfig
import com.fanwu.studyplanner.data.agent.*
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.remote.*
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class StudyAgentPersistenceTest {
    private lateinit var db: AppDatabase
    private val day = LocalDate.now().plusDays(2).toString()
    private var conversation = 0L
    private fun event(start: String = "14:00", end: String = "14:30") = CalendarEvent(title = "接口验证练习", date = day, startTime = start, endTime = end, type = EventType.STUDY_TASK)
    private fun response(message: ApiMessage) = ChatCompletionResponse(listOf(ChatChoice(message)))
    private fun tool(id: String, name: String, args: String) = response(ApiMessage("assistant", toolCalls = listOf(ToolCall(id = id, function = FunctionCall(name, args)))))
    private fun proposal(start: String, end: String) = """{"name":"验证计划","events":[{"title":"接口验证练习","date":"$day","startTime":"$start","endTime":"$end","type":"STUDY_TASK"}]}"""

    @Before fun setup() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.fanwu.studyplanner.f01test")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        conversation = db.conversationDao().insert(ConversationEntity(name = "智能体验证", createdAt = 1))
        db.calendarEventDao().insert(event("09:00", "10:00").copy(title = "模拟课程", type = EventType.CLASS).toEntity())
    }
    @After fun close() { db.close() }

    private fun fakeApi(): DeepSeekApi {
        var request = 0
        return object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, input: ChatCompletionRequest): ChatCompletionResponse = when (request++) {
                0 -> tool("calendar", "query_calendar", """{"startDate":"$day","endDate":"$day"}""")
                1 -> tool("conflict", "propose_plan", proposal("09:30", "10:00"))
                2 -> {
                    assertTrue(input.messages.last().content!!.contains("时间冲突"))
                    tool("fixed", "propose_plan", proposal("14:00", "14:30"))
                }
                else -> response(ApiMessage("assistant", "已避开课程，草稿待预览确认。"))
            }
        }
    }

    @Test fun conflictFeedbackRecoveryAndConfirmationArePersistentAndIdempotent() = runBlocking<Unit> {
        val repo = StudyAgentRepository(db, fakeApi()) { "fake" }
        val run = repo.start(conversation, "安排学习", {})
        assertEquals(AgentStatus.WAITING_CONFIRMATION, run.status)
        assertEquals(3, run.calls)
        assertEquals(1, db.calendarEventDao().getAll().size)
        val restored = StudyAgentRepository(db, fakeApi()) { "fake" }
        assertEquals(run, restored.load(conversation))
        val applied = restored.apply(conversation, run.id, "用户改名")
        restored.apply(conversation, run.id, "用户改名")
        assertEquals(2, db.calendarEventDao().getAll().size)
        assertEquals(1, applied.appliedIds.size)
        assertEquals(AgentStatus.APPLIED, restored.load(conversation)!!.status)
    }

    @Test fun changedCalendarAndPreferencesRequireRevalidation() = runBlocking<Unit> {
        val repo = StudyAgentRepository(db, fakeApi()) { "fake" }
        val run = repo.start(conversation, "安排学习", {})
        db.calendarEventDao().insert(event("16:00", "17:00").copy(title = "新安排").toEntity())
        assertTrue(runCatching { repo.apply(conversation, run.id, "计划") }.isFailure)
        repo.recheck(conversation)
        repo.savePreferences(AgentPreferences("每日少量学习", 20))
        assertTrue(runCatching { repo.apply(conversation, run.id, "计划") }.isFailure)
        assertEquals(2, db.calendarEventDao().getAll().size)
    }

    @Test fun preferencesAreBackedUpButExecutionTranscriptsAreExcluded() = runBlocking<Unit> {
        val repo = StudyAgentRepository(db, fakeApi()) { "fake" }
        repo.savePreferences(AgentPreferences("周末晚起", 90))
        repo.start(conversation, "安排学习", {})
        val backup = BackupRepository(db)
        val parsed = backup.parse(backup.export())
        assertTrue(parsed.documents.any { it.kind == "agent_preferences" })
        assertTrue(parsed.documents.none { it.kind == "agent_run" })
    }

    @Test fun webSourcesArePersistedWithReplyAndPlanningStillRequiresCalendarConfirmation() = runBlocking<Unit> {
        val url = "https://docs.example.com/guide"
        val source = AgentSource("公开学习指南", url, "2026-10-01T00:00:00Z", "2026-10-30T00:00:00Z")
        var searched = 0
        var fetched = 0
        val web = object : AgentWebAccess {
            override suspend fun search(apiKey: String, query: String): AgentToolResult {
                searched++; assertEquals("fake", apiKey)
                return AgentToolResult("""{"ok":true,"summary":"先练习基础","url":"$url"}""", "联网搜索：1条资料", sources = listOf(source))
            }
            override suspend fun fetch(apiKey: String, input: String): AgentToolResult {
                fetched++; assertEquals(url, input)
                return AgentToolResult("""{"ok":true,"content":"学习指南正文"}""", "读取公开学习指南", sources = listOf(source))
            }
        }
        var requests = 0
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, input: ChatCompletionRequest): ChatCompletionResponse = when (requests++) {
                0 -> tool("search", "web_search", """{"query":"公开学习指南"}""")
                1 -> { assertTrue(input.messages.last().content!!.contains("先练习基础")); tool("fetch", "web_fetch", """{"url":"$url"}""") }
                2 -> { assertTrue(input.messages.last().content!!.contains("学习指南正文")); tool("calendar", "query_calendar", """{"startDate":"$day","endDate":"$day"}""") }
                3 -> tool("plan", "propose_plan", proposal("14:00", "14:30"))
                else -> response(ApiMessage("assistant", "已参考指南并避开课程，等待预览确认。"))
            }
        }
        val repo = StudyAgentRepository(db, api, web) { "fake" }
        val run = repo.start(conversation, "查学习指南后安排半小时练习", {})
        assertEquals(1, searched); assertEquals(1, fetched)
        assertEquals(2, run.webRequests)
        assertEquals(listOf(source), run.sources)
        assertEquals(AgentStatus.WAITING_CONFIRMATION, run.status)
        assertEquals(1, db.calendarEventDao().getAll().size)
        assertEquals(run.sources, StudyAgentRepository(db, api, web) { "fake" }.load(conversation)!!.sources)
        assertTrue(db.chatMessageDao().getByConversation(conversation).last().content.contains(url))
    }

    @Test fun disabledWebAndUnverifiedUrlsCannotReachWebService() = runBlocking<Unit> {
        val web = object : AgentWebAccess {
            override suspend fun search(apiKey: String, query: String): AgentToolResult = error("不应联网")
            override suspend fun fetch(apiKey: String, url: String): AgentToolResult = error("不应读取猜测网址")
        }
        var requests = 0
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, input: ChatCompletionRequest): ChatCompletionResponse = when (requests++) {
                0 -> tool("guess", "web_fetch", """{"url":"https://docs.example.com/invented"}""")
                1 -> { assertTrue(input.messages.last().content!!.contains("不能猜测来源")); response(ApiMessage("assistant", "请提供资料链接。")) }
                2 -> { assertTrue(input.tools!!.none { it.function.name in StudyAgent.WEB_TOOLS }); tool("disabled", "web_search", """{"query":"学习指南"}""") }
                else -> { assertTrue(input.messages.last().content!!.contains("已关闭")); response(ApiMessage("assistant", "联网已关闭，请提供资料。")) }
            }
        }
        val repo = StudyAgentRepository(db, api, web) { "fake" }
        assertTrue(repo.start(conversation, "检查未知网址", {}).sources.isEmpty())
        repo.savePreferences(AgentPreferences(allowWebSearch = false))
        assertEquals(0, repo.start(conversation, "已关闭联网", {}).webRequests)
        val parsed = BackupRepository(db).parse(BackupRepository(db).export())
        assertFalse(parsed.documents.single { it.kind == "agent_preferences" }.decode<AgentPreferences>().allowWebSearch)
    }

    @Test fun realDeepSeekToolLoopUsesOnlySyntheticData() = runBlocking<Unit> {
        Assume.assumeTrue("仅显式启用时调用真实API", InstrumentationRegistry.getArguments().getString("liveAgent") == "true")
        Assume.assumeTrue("未配置开发测试Key", BuildConfig.AI_API_KEY.isNotBlank())
        val repo = StudyAgentRepository(db, DeepSeekClient.api) { BuildConfig.AI_API_KEY }
        val run = repo.start(conversation, "这是虚构数据的接口验证。请先查询${day}当天日历和未完成待办，再安排该日14:00—14:30的接口验证练习，生成一项待预览的学习草稿。日期、时间与目标已明确，无需追问，不安排其他日期。", {})
        println("LIVE_AGENT_RESULT status=${run.status} requests=${run.requests} tools=${run.calls} error=${run.error}")
        assertEquals(AgentStatus.WAITING_CONFIRMATION, run.status)
        assertTrue(run.calls >= 2)
        assertEquals(1, db.calendarEventDao().getAll().size)
        assertTrue(run.draft!!.events.all { it.date == day && it.type == EventType.STUDY_TASK })
    }
}
