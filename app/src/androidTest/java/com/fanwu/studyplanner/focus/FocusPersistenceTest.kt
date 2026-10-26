package com.fanwu.studyplanner.focus

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FocusPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var clock: TestClock
    private lateinit var repository: FocusRepository
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private class TestClock : FocusClock {
        var time = FocusInstant(1_700_000_000_000, 100_000, 1)
        override fun now() = time
        fun advance(ms: Long) { time = time.copy(wallMs = time.wallMs + ms, elapsedMs = time.elapsedMs + ms) }
    }

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        clock = TestClock()
        repository = FocusRepository(database, clock) { "Asia/Shanghai" }
    }

    @After fun close() { database.close() }

    @Test fun oneActiveRoundAndIdempotentFinish() = runBlocking {
        val session = repository.start(1, 0, null)
        assertTrue(runCatching { repository.start(1, 0, null) }.isFailure)
        clock.advance(70_000)
        repository.act(session.id)
        repository.act(session.id, FocusAction.FINISH)
        repository.act(session.id, FocusAction.FINISH)
        assertEquals(1, database.focusDao().countSessions())
        assertEquals(60_000L, database.focusDao().intervalsFor(session.id).sumOf { it.durationMs })
        assertNull(repository.current())
        assertEquals("COMPLETED", repository.history.first().single().outcome)
    }

    @Test fun taskCompletionIsExplicitAndPreservesExistingTask() = runBlocking {
        val task = CalendarEvent(title = "迁移测试任务", date = "2026-10-25", startTime = "09:00", endTime = "10:00", type = EventType.STUDY_TASK)
        val taskId = database.calendarEventDao().insert(task.toEntity())
        val session = repository.start(1, 0, taskId)
        clock.advance(10_000)
        repository.act(session.id, FocusAction.FINISH, completeTask = true)
        val stored = database.calendarEventDao().getById(taskId)!!
        assertTrue(stored.isDone)
        assertEquals("迁移测试任务", stored.title)
        assertEquals(10_000L, database.focusDao().intervalsFor(session.id).sumOf { it.durationMs })
    }

    @Test fun endingWithoutCompletionDoesNotMarkTaskDone() = runBlocking {
        val id = database.calendarEventDao().insert(CalendarEvent(title = "保留待办", date = "2026-10-25", startTime = "09:00", endTime = "10:00", type = EventType.STUDY_TASK).toEntity())
        val session = repository.start(1, 0, id)
        clock.advance(60_000)
        repository.act(session.id, FocusAction.FINISH)
        assertFalse(database.calendarEventDao().getById(id)!!.isDone)
    }

    @Test fun deletingAssociatedTaskDoesNotLoseFocusRecord() = runBlocking {
        val id = database.calendarEventDao().insert(CalendarEvent(title = "即将删除", date = "2026-10-25", startTime = "09:00", endTime = "10:00", type = EventType.STUDY_TASK).toEntity())
        val session = repository.start(1, 0, id)
        database.calendarEventDao().deleteById(id)
        clock.advance(20_000)
        repository.act(session.id, FocusAction.FINISH, completeTask = true)
        assertEquals(20_000L, repository.history.first().single().focusedMs)
        assertEquals("即将删除", repository.history.first().single().taskTitle)
    }

    @Test fun reopeningRepositoryRestoresSameRoundAndPauseLog() = runBlocking {
        val session = repository.start(1, 1, null)
        clock.advance(15_000)
        repository.act(session.id, FocusAction.PAUSE)
        clock.advance(30_000)
        val reopened = FocusRepository(database, clock)
        assertEquals(session.id, reopened.current()!!.id)
        assertEquals(15_000L, reopened.current()!!.focusedMs)
        reopened.act(session.id, FocusAction.RESUME)
        clock.advance(10_000)
        reopened.act(session.id, FocusAction.FINISH)
        val history = reopened.history.first().single()
        assertEquals(25_000L, history.focusedMs)
        assertNotNull(history.pauses.single().endedAt)
    }

    @Test fun simultaneousFinishWritesOneInterval() = runBlocking {
        val session = repository.start(1, 0, null)
        clock.advance(20_000)
        coroutineScope {
            List(4) { async(Dispatchers.IO) { repository.act(session.id, FocusAction.FINISH) } }.awaitAll()
        }
        assertEquals(20_000L, database.focusDao().intervalsFor(session.id).sumOf { it.durationMs })
        assertEquals(1, database.focusDao().countSessions())
    }

    @Test fun migrationAddsFocusTablesWithoutDeletingExistingData() {
        val name = "focus-migration-${UUID.randomUUID()}.db"
        fun helper(version: Int) = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE legacy_marker (id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL)")
                    db.execSQL("INSERT INTO legacy_marker VALUES(1, '保留现有学习计划')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    AppDatabase.MIGRATION_5_6.migrate(db)
                }
            }).build()
        )
        try {
            helper(5).use { it.writableDatabase }
            helper(6).use { upgraded ->
                val db = upgraded.writableDatabase
                db.query("SELECT label FROM legacy_marker WHERE id = 1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("保留现有学习计划", cursor.getString(0))
                }
                db.query("SELECT COUNT(*) FROM focus_sessions").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
                db.query("SELECT COUNT(*) FROM focus_intervals").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
            }
        } finally { context.deleteDatabase(name) }
    }
}
