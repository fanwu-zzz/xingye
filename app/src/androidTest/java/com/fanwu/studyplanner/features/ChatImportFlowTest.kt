package com.fanwu.studyplanner.features

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.fanwu.studyplanner.MainActivity
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.remote.*
import com.fanwu.studyplanner.data.repository.ChatRepository
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.chat.ChatScreen
import com.fanwu.studyplanner.ui.chat.ChatViewModel
import com.fanwu.studyplanner.ui.theme.StudyPlannerTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate

/** 只在独立测试包内运行；所有 AI 响应均来自本地替身。 */
class ChatImportFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var db: AppDatabase
    private lateinit var vm: ChatViewModel
    private lateinit var repository: ChatRepository
    private var conversationId = 0L
    private val requests = mutableListOf<ChatCompletionRequest>()
    private var answer: () -> String = { error("未设置测试响应") }
    private val valid get() = ScheduleImport(semesterStartMonday = mondayOf(LocalDate.now()).toString(),
        periodTimeTable = mapOf("1-2" to "08:10-09:50", "3-4" to "10:10-11:50"),
        courses = listOf(CourseSegment("测试连堂课程", LocalDate.now().dayOfWeek.value, "1-4", "1-4", "A101")),
        message = "课表已整理好，请先核对预览，确认后才会导入。")

    @Before fun setup() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.fanwu.studyplanner.f01test")
        db = AppDatabase.getInstance(context)
        // 仅清理可卸载测试包，不访问日常应用数据库。
        db.clearAllTables()
        val keys = ApiKeyStore(context).apply { apiKey = "fake-test-key" }
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                requests += request
                return ChatCompletionResponse(listOf(ChatChoice(ApiMessage("assistant", answer()))))
            }
        }
        repository = ChatRepository(api, db.chatMessageDao(), db.conversationDao(), db.calendarEventDao(), keys)
        conversationId = repository.createConversation("导入测试", ConversationMode.IMPORT_SCHEDULE)
        compose.runOnUiThread {
            vm = ChatViewModel(repository, context)
            val arguments = InstrumentationRegistry.getArguments()
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,
                    arguments.getString("fontScale")?.toFloat() ?: density.fontScale)) {
                    Box(Modifier.widthIn(max = (arguments.getString("widthDp")?.toInt() ?: 1000).dp)) {
                        StudyPlannerTheme { ChatScreen({}, viewModel = vm) }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { vm.conversations.value.any { it.id == conversationId } }
        compose.runOnIdle { vm.openConversation(conversationId) }
        compose.waitUntil(10_000) { vm.currentConversation.value?.id == conversationId }
    }

    private fun send(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("发送", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { !vm.isSending.value }
    }

    @Test fun validationFailureDoesNotRestoreSentTextAndPreviewRequiresConfirmation() = runBlocking<Unit> {
        answer = { Json.encodeToString(valid.copy(periodTimeTable = mapOf("1-4" to "08:10-11:50"),
            courses = listOf(valid.courses.single().copy(periods = "1-2")))) }
        send("周三第1-2节，学期首周已确认")
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertTrue(vm.error.value.orEmpty().contains("第 2 节结束"))
        assertNull(vm.failedInput.value)
        compose.onNodeWithText("预览并确认导入").assertIsNotEnabled()
        assertTrue(db.calendarEventDao().getAll().isEmpty())

        answer = { Json.encodeToString(valid) }
        send("补充1-2节08:10-09:50，3-4节10:10-11:50；这门课是1-4节连堂")
        compose.waitUntil(10_000) { vm.pendingPlan.value != null }
        compose.onNodeWithText("确认导入课程").assertIsDisplayed()
        assertTrue(db.calendarEventDao().getAll().isEmpty())
        compose.onNode(hasSetTextAction() and hasText("计划名称")).performTextReplacement("核对后的课表名")
        compose.onNodeWithText("保存计划名称").performClick()
        compose.onNodeWithText("稍后决定").performClick()
        compose.onNodeWithText("预览并确认导入").assertIsEnabled()

        answer = { """{"status":"chat","message":"还没有导入，点下方预览并确认后才会保存。"}""" }
        send("你导入了吗")
        assertNull(vm.pendingPlan.value)
        assertNotNull(vm.latestPlan.value)
        assertTrue(requests.last().messages.last().content.orEmpty().contains("尚未导入"))
        compose.onNodeWithText("预览并确认导入").performClick()
        compose.waitUntil(10_000) { vm.pendingPlan.value != null }
        assertEquals("核对后的课表名", vm.pendingPlan.value!!.name)
        compose.onNodeWithText("确认导入课程").performClick()
        compose.waitUntil(10_000) { vm.latestPlan.value?.applied == true }
        assertEquals(4, db.calendarEventDao().getAll().size)
        assertTrue(db.calendarEventDao().getAll().all { it.startTime == "08:10" && it.endTime == "11:50" })

        compose.runOnIdle { vm.backToList(); vm.openConversation(conversationId) }
        compose.waitUntil(10_000) { vm.latestPlan.value?.applied == true && vm.notice.value != null }
        compose.onNodeWithText("查看已导入课表").assertIsDisplayed()
    }

    @Test fun networkFailureOffersExplicitRestoreAndRetryDoesNotDuplicateMessage() = runBlocking<Unit> {
        answer = { throw IOException("测试断网") }
        send("9月7日是第一周周一")
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals("9月7日是第一周周一", vm.failedInput.value)
        compose.onNodeWithText("恢复上次输入").performClick()
        answer = { Json.encodeToString(valid.copy(status = "need_info", missing = listOf("periodTimeTable"))) }
        compose.onNodeWithText("发送", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { !vm.isSending.value }
        assertEquals(1, db.chatMessageDao().getByConversation(conversationId).count { it.role == "user" })
        assertTrue(db.calendarEventDao().getAll().isEmpty())
    }

    @Test fun reopeningUnappliedConversationRestoresPreviewWithoutCallingApi() = runBlocking<Unit> {
        db.chatMessageDao().insert(ChatMessageEntity(conversationId = conversationId, role = "assistant",
            content = Json.encodeToString(valid), timestamp = System.currentTimeMillis()))
        compose.runOnIdle { vm.backToList(); vm.openConversation(conversationId) }
        compose.waitUntil(10_000) { vm.latestPlan.value != null }
        assertFalse(vm.latestPlan.value!!.applied)
        assertNull(vm.pendingPlan.value)
        assertTrue(requests.isEmpty())
        assertTrue(db.calendarEventDao().getAll().isEmpty())
        compose.onNodeWithText("预览并确认导入").assertIsEnabled()
        compose.onNodeWithText("预览并确认导入").assertIsDisplayed()
        compose.onNodeWithText("选择 / 更换 Excel 课表").assertIsDisplayed()
        compose.onNodeWithText("发送", useUnmergedTree = true).assertIsDisplayed()
        // 可选人工视觉检查停留；普通测试不等待、不调用真实 API。
        if (InstrumentationRegistry.getArguments().getString("visualPause") == "true") {
            println("CHAT_VISUAL_READY")
            Thread.sleep(25_000)
            compose.onNodeWithText("预览并确认导入").performClick()
            compose.waitUntil(10_000) { vm.pendingPlan.value != null }
            println("CHAT_DIALOG_VISUAL_READY")
            Thread.sleep(25_000)
        }
    }
}
