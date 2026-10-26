package com.fanwu.studyplanner.features

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.data.remote.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class LearningPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var learning: LearningRepository
    private lateinit var backup: BackupRepository
    private val week = mondayOf(LocalDate.now())
    private val semester get() = Semester("s", "测试学期", week.toString(), 4, listOf(PeriodTime(1, "08:00", "08:45"), PeriodTime(2, "08:55", "09:40")))
    private val course get() = RecurringCourse("c", "s", "测试课程", 1, "1-4", "1-2")
    private fun event(title: String = "测试任务") = CalendarEvent(title = title, date = week.toString(), startTime = "10:00", endTime = "11:00", type = EventType.STUDY_TASK)
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".f01test"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        learning = LearningRepository(db); backup = BackupRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun completionTimestampAndUndoAreConsistent() = runBlocking<Unit> {
        val id = db.calendarEventDao().insert(event().toEntity())
        db.calendarEventDao().setDone(id, true, 1234)
        db.calendarEventDao().setDone(id, true, 5678)
        assertEquals(1234L, db.calendarEventDao().getById(id)?.completedAt)
        db.calendarEventDao().setDone(id, false)
        assertNull(db.calendarEventDao().getById(id)?.completedAt)
    }
    @Test fun goalEditUpsertsCurrentRevisionAndRejectsPast() = runBlocking<Unit> {
        val g = GoalRevision(goalId = "g", effectiveWeek = week.toString(), name = "目标", minutes = 60)
        learning.saveGoal(g); learning.saveGoal(g.copy(id = "new", minutes = 120))
        assertEquals(1, db.documentDao().all().size)
        assertTrue(runCatching { learning.saveGoal(g.copy(effectiveWeek = week.minusWeeks(1).toString())) }.isFailure)
    }
    @Test fun cancelledPreviewWritesNothingAndApplyIsAtomic() = runBlocking<Unit> {
        val p = learning.preview(semester, listOf(course))
        assertTrue(db.documentDao().all().isEmpty()); assertTrue(db.calendarEventDao().getAll().isEmpty())
        learning.apply(p)
        assertEquals(4, db.calendarEventDao().getAll().size)
        assertEquals(2, db.documentDao().all().size)
    }
    @Test fun staleCoursePreviewDoesNotOverwriteConcurrentEdit() = runBlocking<Unit> {
        val p = learning.preview(semester, listOf(course))
        db.calendarEventDao().insert(event().toEntity())
        assertTrue(runCatching { learning.apply(p) }.isFailure)
        assertEquals(1, db.calendarEventDao().getAll().size)
        assertTrue(db.documentDao().all().isEmpty())
    }
    @Test fun singleCancellationAndRescheduleSurviveRegeneration() = runBlocking<Unit> {
        learning.apply(learning.preview(semester, listOf(course)))
        val all = db.calendarEventDao().getAll()
        learning.except(all[0].toDomain(), null)
        learning.except(all[1].toDomain(), all[1].toDomain().copy(startTime = "12:00", endTime = "13:00"))
        val p = learning.preview(semester.copy(name = "更新"), listOf(course))
        learning.apply(p)
        assertEquals(3, db.calendarEventDao().getAll().size)
        assertTrue(db.calendarEventDao().getAll().any { it.startTime == "12:00" })
        assertFalse(db.calendarEventDao().getAll().any { it.date == all[0].date })
    }
    @Test fun conflictsAndDuplicatesAreVisibleAndManualEventsSurvive() = runBlocking<Unit> {
        val manual = expandCourse(course, semester).first().copy(title = "考试", type = EventType.EXAM, recurringCourseId = null, originalOccurrenceDate = null)
        val id = db.calendarEventDao().insert(manual.toEntity())
        val p = learning.preview(semester, listOf(course, course.copy(id = "duplicate")))
        assertEquals(4, p.duplicates); assertEquals(1, p.conflicts.size)
        learning.apply(p)
        assertEquals(EventType.EXAM, db.calendarEventDao().getById(id)?.type)
    }
    @Test fun exceptionsSurviveWeekdayAndSemesterStartChanges() = runBlocking<Unit> {
        learning.apply(learning.preview(semester, listOf(course)))
        val old = db.calendarEventDao().getAll()
        learning.except(old[0].toDomain(), null)
        learning.except(old[1].toDomain(), old[1].toDomain().copy(startTime = "12:00", endTime = "13:00"))
        val changedSemester = semester.copy(startMonday = week.plusWeeks(1).toString())
        learning.apply(learning.preview(changedSemester, listOf(course.copy(day = 3))))
        val events = db.calendarEventDao().getAll()
        assertEquals(3, events.size)
        assertTrue(events.any { it.id == old[1].id && it.startTime == "12:00" })
        assertFalse(events.any { it.date == week.plusWeeks(1).plusDays(2).toString() })
        assertFalse(events.any { it.date == week.plusWeeks(2).plusDays(2).toString() })
    }
    @Test fun futureCourseChangeKeepsEarlierOccurrences() = runBlocking<Unit> {
        learning.apply(learning.preview(semester, listOf(course)))
        val pivot = week.plusWeeks(2)
        val before = course.copy(until = pivot.minusDays(1).toString())
        val after = course.copy(id = "future", from = pivot.toString(), name = "后续课程", room = "新教室")
        learning.apply(learning.preview(semester, listOf(before, after)))
        val events = db.calendarEventDao().getAll()
        assertEquals(4, events.size)
        assertEquals(2, events.count { it.date < pivot.toString() && it.title == course.name })
        assertEquals(2, events.count { it.date >= pivot.toString() && it.title == "后续课程" && it.note == "新教室" })
    }
    @Test fun backupRoundTripKeepsAssociationsAndIsIdempotent() = runBlocking<Unit> {
        val id = db.calendarEventDao().insert(event().toEntity())
        val c = db.conversationDao().insert(ConversationEntity(name = "对话", createdAt = 10))
        db.chatMessageDao().insert(ChatMessageEntity(conversationId = c, role = "user", content = "学习", timestamp = 11))
        learning.saveGoal(GoalRevision(effectiveWeek = week.toString(), name = "目标", minutes = 60))
        learning.apply(learning.preview(semester, listOf(course)))
        val timer = FocusTimer.newSession("session", 1, 0, FocusInstant(10000, 0, 1), "UTC", event().copy(id = id))
        val change = FocusTimer.transition(timer, FocusAction.FINISH, FocusInstant(20000, 10000, 1))
        db.focusDao().insert(change.session.toEntity())
        db.focusDao().insertInterval(FocusIntervalEntity("i", "session", 10000, 10000, "UTC"))
        val exported = backup.parse(backup.export())
        assertFalse(backup.export().contains("AI_API_KEY")); assertFalse(backup.export().contains("deepseek_api_key"))
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            // 主动错开本机 ID，验证恢复确实重建关联。
            other.calendarEventDao().insert(event("本机保留").toEntity())
            val restore = BackupRepository(other)
            restore.restore(restore.preview(exported), RestorePolicy.SKIP)
            val count = other.calendarEventDao().getAll().size
            val restoredSession = other.focusDao().allSessions().single().toFocusSession()
            assertNotEquals(id, restoredSession.taskId)
            assertEquals("测试任务", other.calendarEventDao().getById(restoredSession.taskId!!)?.title)
            restore.restore(restore.preview(exported), RestorePolicy.SKIP)
            assertEquals(count, other.calendarEventDao().getAll().size)
            assertEquals(1, other.focusDao().countSessions())
            assertEquals(1, other.chatMessageDao().all().size)
            assertEquals(3, other.documentDao().all().count { it.kind in listOf("goal", "semester", "course") })
        } finally { other.close() }
    }
    @Test fun backupValidationRejectsFutureVersionsAndOrphanMessages() = runBlocking<Unit> {
        val b = backup.parse(backup.export())
        assertTrue(runCatching { backup.parse(plannerJson.encodeToString(b.copy(schemaVersion = 99))) }.isFailure)
        val orphan = ChatMessageEntity(id = 1, conversationId = 99, role = "user", content = "坏记录", timestamp = 1)
        assertTrue(runCatching { backup.validate(b.copy(messages = listOf(orphan))) }.isFailure)
        assertTrue(db.calendarEventDao().getAll().isEmpty())
    }
    @Test fun restoreRollsBackEarlierInsertsWhenFocusConflictFails() = runBlocking<Unit> {
        val original = FocusTimer.newSession("same", 1, 0, FocusInstant(10000, 0, 1), "UTC")
        db.focusDao().insert(original.copy(phase = FocusPhase.ABANDONED, outcome = "ABANDONED", endedAt = 10000).toEntity())
        val newer = original.copy(phase = FocusPhase.ABANDONED, outcome = "ABANDONED", endedAt = 20000, focusedMs = 10000)
        val b = backup.parse(backup.export()).copy(events = listOf(event("不应写入").copy(id = 999)), sessions = listOf(newer.toEntity()), intervals = listOf(FocusIntervalEntity("i", "same", 10000, 10000, "UTC")))
        val p = backup.preview(b)
        assertTrue(runCatching { backup.restore(p, RestorePolicy.OVERWRITE) }.isFailure)
        assertTrue(db.calendarEventDao().getAll().isEmpty())
        assertEquals(0L, db.focusDao().allSessions().single().focusedMs)
    }
    @Test fun restoreSkipVersusOverwriteAndStalePreview() = runBlocking<Unit> {
        val id = db.calendarEventDao().insert(event().toEntity())
        val b = backup.parse(backup.export())
        db.calendarEventDao().update(event().copy(id = id, note = "本机备注").toEntity())
        backup.restore(backup.preview(b), RestorePolicy.SKIP)
        assertEquals("本机备注", db.calendarEventDao().getById(id)?.note)
        backup.restore(backup.preview(b), RestorePolicy.OVERWRITE)
        assertNull(db.calendarEventDao().getById(id)?.note)
        val p = backup.preview(b)
        db.calendarEventDao().insert(event("新任务").toEntity())
        assertTrue(runCatching { backup.restore(p, RestorePolicy.OVERWRITE) }.isFailure)
    }
    @Test fun repeatedRestoreCanRecoverDeletedConversationAndMessages() = runBlocking<Unit> {
        val id = db.conversationDao().insert(ConversationEntity(name = "恢复后删除的对话", createdAt = 10))
        db.chatMessageDao().insert(ChatMessageEntity(conversationId = id, role = "user", content = "中文历史消息", timestamp = 11))
        val b = backup.parse(backup.export())
        backup.restore(backup.preview(b), RestorePolicy.SKIP)
        db.chatMessageDao().clear(id); db.conversationDao().delete(id)
        backup.restore(backup.preview(b), RestorePolicy.SKIP)
        assertEquals(1, db.conversationDao().all().size)
        assertEquals("中文历史消息", db.chatMessageDao().all().single().content)
        backup.restore(backup.preview(b), RestorePolicy.SKIP)
        assertEquals(1, db.chatMessageDao().all().size)
    }
    @Test fun sameWeekGoalWithNewRevisionIdHonorsRestoreConflictPolicy() = runBlocking<Unit> {
        val g = GoalRevision(id = "old", goalId = "g", effectiveWeek = week.toString(), name = "旧目标", minutes = 60)
        learning.saveGoal(g)
        val b = backup.parse(backup.export())
        learning.saveGoal(g.copy(id = "new", name = "本机修改", minutes = 120))
        assertEquals(1, backup.preview(b).conflicts)
        backup.restore(backup.preview(b), RestorePolicy.SKIP)
        assertEquals("本机修改", db.documentDao().all().single { it.kind == "goal" }.decode<GoalRevision>().name)
        backup.restore(backup.preview(b), RestorePolicy.OVERWRITE)
        assertEquals("旧目标", db.documentDao().all().single { it.kind == "goal" }.decode<GoalRevision>().name)
    }
    @Test fun restoredDeletedEventKeepsItsSourceIdentityAfterLocalEdits() = runBlocking<Unit> {
        val oldId = db.calendarEventDao().insert(event().toEntity())
        val b = backup.parse(backup.export())
        db.calendarEventDao().deleteById(oldId)
        backup.restore(backup.preview(b), RestorePolicy.SKIP)
        val recovered = db.calendarEventDao().getAll().single()
        assertNotEquals(oldId, recovered.id)
        db.calendarEventDao().update(recovered.copy(title = "恢复后修改名称"))
        backup.restore(backup.preview(b), RestorePolicy.SKIP)
        assertEquals(1, db.calendarEventDao().getAll().size)
        assertEquals("恢复后修改名称", db.calendarEventDao().getAll().single().title)
        backup.restore(backup.preview(b), RestorePolicy.OVERWRITE)
        assertEquals(1, db.calendarEventDao().getAll().size)
        assertEquals("测试任务", db.calendarEventDao().getAll().single().title)
    }
    @Test fun deletingAPlanCannotResurrectItsManagedOccurrences() = runBlocking<Unit> {
        learning.apply(learning.preview(semester, listOf(course.copy(source = "课程组"))))
        CalendarRepository(db.calendarEventDao(), db).deletePlan("课程组")
        val rules = db.documentDao().all().filter { it.kind == "course" }.map { it.decode<RecurringCourse>() }
        assertEquals(4, rules.single().exceptions.size)
        learning.apply(learning.preview(semester, rules))
        assertTrue(db.calendarEventDao().getAll().isEmpty())
    }
    @Test fun reviewSendsOnlyConfirmedSummaryAndSavesUnappliedDraft() = runBlocking<Unit> {
        val summary = buildWeekReview(week, emptyList(), emptyList())
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                assertEquals("Bearer fake-test-key", authorization)
                assertEquals(summary.text, request.messages.last().content)
                assertEquals(2, request.messages.size)
                return ChatCompletionResponse(listOf(ChatChoice(ApiMessage("assistant", "{\"reply\":\"暂无数据，建议先记录专注。\",\"events\":[]}"))))
            }
        }
        val d = ReviewRepository(db, api).generate(ReviewSendRequest(summary, week.plusWeeks(1)), "fake-test-key")
        assertFalse(d.applied); assertTrue(db.calendarEventDao().getAll().isEmpty())
        assertEquals(d, db.documentDao().get(d.id)?.decode<ReviewDraft>())
    }
    @Test fun applyingReviewProtectsClassesAndIsIdempotent() = runBlocking<Unit> {
        val api = object : DeepSeekApi { override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest) = error("Must not call network") }
        val repo = ReviewRepository(db, api)
        val e = event().copy(date = week.plusWeeks(1).toString())
        val courseId = db.calendarEventDao().insert(e.copy(type = EventType.CLASS).toEntity())
        val d = ReviewDraft(week.toString(), "摘要", "建议", listOf(e))
        db.documentDao().put(document(d.id, "review", d))
        assertTrue(runCatching { repo.apply(d) }.isFailure)
        assertEquals(1, db.calendarEventDao().getAll().size)
        db.calendarEventDao().deleteById(courseId)
        repo.apply(d)
        assertEquals(1, db.calendarEventDao().getAll().size)
        assertTrue(runCatching { repo.apply(d) }.isFailure)
        assertEquals(1, db.calendarEventDao().getAll().size)
    }
}
