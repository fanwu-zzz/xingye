package com.fanwu.studyplanner.features

import android.content.Context
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fanwu.studyplanner.MainActivity
import com.fanwu.studyplanner.data.agent.*
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.remote.*
import com.fanwu.studyplanner.data.repository.ChatRepository
import com.fanwu.studyplanner.ui.chat.*
import com.fanwu.studyplanner.ui.theme.StudyPlannerTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class StudyAgentUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun agentProgressPreferencesPreviewAndConfirmationWorkTogether() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.fanwu.studyplanner.f01test")
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val day = LocalDate.now().plusDays(1).toString()
            var requests = 0
            val api = object : DeepSeekApi {
                override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                    val message = when (requests++) {
                        0 -> ApiMessage("assistant", toolCalls = listOf(ToolCall(id = "s", function = FunctionCall("web_search", """{"query":"数学学习指南"}"""))))
                        1 -> ApiMessage("assistant", toolCalls = listOf(ToolCall(id = "q", function = FunctionCall("query_calendar", """{"startDate":"$day","endDate":"$day"}"""))))
                        2 -> ApiMessage("assistant", toolCalls = listOf(ToolCall(id = "p", function = FunctionCall("propose_plan", """{"name":"明日复习","events":[{"title":"复习数学","date":"$day","startTime":"14:00","endTime":"14:30","type":"STUDY_TASK"}]}"""))))
                        else -> ApiMessage("assistant", "已经核对日历，草稿准备好了，请确认后保存。")
                    }
                    return ChatCompletionResponse(listOf(ChatChoice(message)))
                }
            }
            val web = object : AgentWebAccess {
                override suspend fun search(apiKey: String, query: String) = AgentToolResult("公开学习资料摘要", "联网搜索：1条资料来源", sources = listOf(
                    AgentSource("数学学习指南", "https://docs.example.com/guide", retrievedAt = "2026-10-30T00:00:00Z")))
                override suspend fun fetch(apiKey: String, url: String): AgentToolResult = error("不需要读取网页")
            }
            val agents = StudyAgentRepository(db, api, web) { "fake" }
            val chats = ChatRepository(api, db.chatMessageDao(), db.conversationDao(), db.calendarEventDao(), ApiKeyStore(context))
            val id = chats.createConversation("智能体界面")
            lateinit var vm: ChatViewModel
            compose.activityRule.scenario.onActivity { activity ->
                vm = ChatViewModel(chats, context, agents)
                activity.setContent { StudyPlannerTheme { ChatScreen({}, viewModel = vm) } }
            }
            compose.waitUntil(10_000) { vm.conversations.value.any { it.id == id } }
            compose.runOnIdle { vm.openConversation(id) }
            compose.waitUntil(10_000) { vm.currentConversation.value?.id == id }
            compose.onNode(hasSetTextAction()).performTextInput("安排明天复习数学半小时")
            compose.onNodeWithText("发送", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { vm.pendingPlan.value != null }
            assertEquals(0, db.calendarEventDao().getAll().size)
            compose.onNode(hasSetTextAction() and hasText("计划名称")).performTextReplacement("调整后的复习计划")
            compose.onNodeWithText("保存计划名称").performClick()
            compose.onNodeWithText("稍后决定").performClick()
            compose.onNode(hasText("本轮联网检索来源", substring = true)).assertExists()
            if (InstrumentationRegistry.getArguments().getString("visualWeb") == "true") { println("AGENT_WEB_SOURCES_VISUAL_READY"); Thread.sleep(30_000) }
            compose.onNodeWithText("学习偏好").performClick()
            if (InstrumentationRegistry.getArguments().getString("visualWeb") == "true") { println("AGENT_WEB_PREFERENCES_VISUAL_READY"); Thread.sleep(30_000) }
            compose.onNodeWithText("允许按需联网查资料").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("每日计划学习上限（分钟，可留空）")).performScrollTo().performTextInput("60")
            compose.onNodeWithText("保存偏好").performClick()
            compose.waitUntil(10_000) { vm.agentPreferences.value.maxMinutesPerDay == 60 }
            assertFalse(vm.agentPreferences.value.allowWebSearch)
            compose.onNodeWithText("继续预览上次计划").performClick()
            compose.waitUntil(10_000) { vm.pendingPlan.value != null }
            assertEquals("调整后的复习计划", vm.pendingPlan.value!!.name)
            if (InstrumentationRegistry.getArguments().getString("visualPause") == "true") { println("AGENT_PREVIEW_VISUAL_READY"); Thread.sleep(45_000) }
            compose.onNodeWithText("应用到日历").performClick()
            compose.waitUntil(10_000) { vm.agentRun.value?.status == AgentStatus.APPLIED }
            assertEquals(1, db.calendarEventDao().getAll().size)
            compose.runOnIdle { vm.backToList(); vm.openConversation(id) }
            compose.waitUntil(10_000) { vm.agentRun.value?.status == AgentStatus.APPLIED }
            assertEquals(4, requests)
        } finally { db.close() }
    }
}
