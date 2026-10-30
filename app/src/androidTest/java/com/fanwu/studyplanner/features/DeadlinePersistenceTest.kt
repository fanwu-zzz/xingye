package com.fanwu.studyplanner.features

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.remote.*
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.reminders.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

/** 独立测试包 + 内存数据库，不安装或清理日常应用，不调用真实 AI/通知。 */
class DeadlinePersistenceTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var calendar: CalendarRepository
    private fun deadline() = CalendarEvent(title = "提交报告", date = "2026-10-28", startTime = "18:00", endTime = "18:00", type = EventType.DEADLINE)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName == "com.fanwu.studyplanner.f01test")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        calendar = CalendarRepository(db.calendarEventDao(), db)
    }
    @After fun close() { db.close() }

    @Test fun deadlinePersistsAsTaskAndSurvivesBackupRestore() = runBlocking<Unit> {
        val id = calendar.insert(deadline())
        assertEquals(EventType.DEADLINE, calendar.observeAllTasks().first().single().type)
        calendar.setDone(id, true)
        val backup = BackupRepository(db)
        val exported = backup.parse(backup.export())
        assertEquals("18:00", exported.events.single().endTime)
        assertTrue(exported.events.single().isDone)
        assertNotNull(exported.events.single().completedAt)
        db.calendarEventDao().deleteById(id)
        backup.restore(backup.preview(exported), RestorePolicy.SKIP)
        val restored = db.calendarEventDao().getAll().single().toDomain()
        assertEquals(EventType.DEADLINE, restored.type)
        assertTrue(restored.isDone)
        assertEquals(0, restored.durationMinutes())
        assertEquals(restored.startTime, restored.endTime)
    }

    @Test fun deadlineIsScheduledBeforeDueTimeAndCompletionCancelsIt() = runBlocking<Unit> {
        val zone = ZoneId.of("Asia/Shanghai")
        var now = LocalDateTime.of(2026, 10, 28, 17, 0).atZone(zone).toInstant().toEpochMilli()
        var next: Long? = null
        val sent = mutableListOf<CalendarEvent>()
        val delivery = object : ReminderDelivery {
            override fun available() = true
            override fun notify(event: CalendarEvent, spec: ReminderSpec) { sent += event }
            override fun retain(tags: Set<String>) {}
        }
        val engine = ReminderEngine(db, { next = it }, delivery, { now }, { zone })
        val id = calendar.insert(deadline())
        engine.save(ReminderSettings(tasksEnabled = true, leadMinutes = 30))
        assertEquals(LocalDateTime.of(2026, 10, 28, 17, 30).atZone(zone).toInstant().toEpochMilli(), next)
        calendar.setDone(id, true)
        engine.reconcile()
        assertNull(next)
        calendar.setDone(id, false)
        engine.reconcile()
        now = next!!
        engine.reconcile()
        assertEquals(EventType.DEADLINE, sent.single().type)
        assertEquals("18:00", sent.single().startTime)
    }

    @Test fun completingAssociatedDeadlinePreservesRealFocusTime() = runBlocking<Unit> {
        val id = calendar.insert(deadline())
        var instant = FocusInstant(1_000_000, 1000, 1)
        val focus = FocusRepository(db, FocusClock { instant }, { "Asia/Shanghai" })
        val session = focus.start(25, 0, id)
        instant = FocusInstant(1_000_000 + 60_000, 1000 + 60_000, 1)
        focus.act(session.id, FocusAction.FINISH, completeTask = true)
        assertTrue(db.calendarEventDao().getById(id)!!.isDone)
        assertEquals(60_000L, db.focusDao().allIntervals().sumOf { it.durationMs })
        assertEquals(0, db.calendarEventDao().getById(id)!!.toDomain().durationMinutes())
    }

    @Test fun aiCanReturnDeadlineDraftButWritesOnlyAfterConfirmation() = runBlocking<Unit> {
        val due = LocalDate.now().plusDays(2).toString()
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                assertEquals("Bearer fake-test-key", authorization)
                return ChatCompletionResponse(listOf(ChatChoice(ApiMessage("assistant", "待确认的作业截止任务",
                    listOf(ToolCall(function = FunctionCall("add_event", """{"title":"交作业","date":"$due","startTime":"23:59","endTime":"23:59","type":"DEADLINE"}""")))))))
            }
        }
        val keys = ApiKeyStore(context).apply { apiKey = "fake-test-key" }
        val repo = ChatRepository(api, db.chatMessageDao(), db.conversationDao(), db.calendarEventDao(), keys)
        val conversation = repo.createConversation("作业截止")
        db.chatMessageDao().insert(ChatMessageEntity(conversationId = conversation, role = "user", content = "后天交作业", timestamp = 1))
        val plan = repo.generatePlan(conversation).getOrThrow()
        assertEquals(EventType.DEADLINE, plan.events.single().type)
        assertTrue(db.calendarEventDao().getAll().isEmpty())
        repo.applyPlan(plan, "作业截止").getOrThrow()
        assertEquals("23:59", db.calendarEventDao().getAll().single().endTime)
    }
}
