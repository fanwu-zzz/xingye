package com.fanwu.studyplanner.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderEngineTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: ReminderEngine
    private val zone = ZoneId.of("Asia/Shanghai")
    private var time = LocalDateTime.of(2026, 10, 27, 8, 0).atZone(zone).toInstant().toEpochMilli()
    private var next: Long? = null
    private var available = true
    private val sent = mutableListOf<Pair<CalendarEvent, ReminderSpec>>()
    private var retained = emptySet<String>()
    private val delivery = object : ReminderDelivery {
        override fun available() = available
        override fun notify(event: CalendarEvent, spec: ReminderSpec) { sent += event to spec }
        override fun retain(tags: Set<String>) { retained = tags }
    }
    private val settings = ReminderSettings(tasksEnabled = true, coursesEnabled = true)
    private fun createEngine() = ReminderEngine(db, { next = it }, delivery, { time }, { zone })
    private suspend fun add(title: String = "学习", plan: String? = null): Long = db.calendarEventDao().insert(CalendarEvent(title = title, date = "2026-10-27", startTime = "09:00", endTime = "10:00", type = EventType.STUDY_TASK, planName = plan).toEntity())
    private suspend fun fire() { time = next!!; engine.reconcile() }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        engine = createEngine()
    }
    @After fun cleanup() { db.close() }

    @Test fun repeatedAndConcurrentCallbacksDeliverOnceAcrossRecreation() = runBlocking {
        add(); engine.save(settings); time = next!!
        coroutineScope { List(4) { async { engine.reconcile() } }.awaitAll() }
        createEngine().reconcile()
        assertEquals(1, sent.size)
        assertNull(next)
        assertEquals("DELIVERED", db.reminderDao().all().single().state)
    }
    @Test fun editCancelsOldDeadlineAndUsesLatestTitle() = runBlocking {
        val id = add(); engine.save(settings)
        val old = next!!
        val event = db.calendarEventDao().getById(id)!!
        db.calendarEventDao().update(event.copy(startTime = "10:00", endTime = "11:00", title = "改期"))
        engine.reconcile()
        assertEquals(old + 3_600_000, next)
        time = old; engine.reconcile(); assertTrue(sent.isEmpty())
        fire(); assertEquals("改期", sent.single().first.title)
    }
    @Test fun deleteGroupAndReplacementRemoveOldReminders() = runBlocking {
        val oldId = add(plan = "课表"); engine.save(settings)
        db.calendarEventDao().applyEvents(listOf(CalendarEvent(title = "替换", date = "2026-10-27", startTime = "10:00", endTime = "11:00", type = EventType.CLASS, planName = "课表").toEntity()), "课表")
        engine.reconcile()
        assertFalse(db.reminderDao().all().any { it.eventId == oldId })
        assertEquals(1, db.reminderDao().all().size)
        db.calendarEventDao().deleteByPlanName("课表"); engine.reconcile()
        assertNull(next); assertTrue(db.reminderDao().all().isEmpty())
    }
    @Test fun disableCompleteAndDeleteCancelPendingAndVisibleNotifications() = runBlocking {
        val id = add(); engine.save(settings)
        db.calendarEventDao().setDone(id, true); engine.reconcile(); assertNull(next)
        db.calendarEventDao().setDone(id, false); engine.reconcile(); assertNotNull(next)
        fire(); assertEquals(1, retained.size)
        engine.save(ReminderSettings()); assertTrue(retained.isEmpty()); assertNull(next)
        engine.save(settings); assertEquals(1, sent.size)
        db.calendarEventDao().deleteById(id); engine.reconcile(); assertTrue(retained.isEmpty())
    }
    @Test fun permissionsDeniedAndLateCallbacksDoNotReplay() = runBlocking {
        add(); engine.save(settings); available = false; fire()
        available = true; engine.reconcile(); assertTrue(sent.isEmpty())
        time -= 60 * 60_000L
        val id = add("延迟"); engine.reconcile(); time = next!! + 11 * 60_000L
        engine.reconcile()
        assertEquals("MISSED", db.reminderDao().all().first { it.eventId == id }.state)
        assertTrue(sent.isEmpty())
    }
    @Test fun quietSuppressesScheduledAndLateDelivery() = runBlocking {
        add(); engine.save(settings.copy(quietEnabled = true, quietStart = "23:00", quietEnd = "09:00"))
        assertNull(next)
        engine.save(settings); val deadline = next!!
        engine.save(settings.copy(quietEnabled = true, quietStart = "08:46", quietEnd = "09:00"))
        time = deadline + 60_000; engine.reconcile(); assertTrue(sent.isEmpty()); assertNull(next)
    }
    @Test fun settingsPersistAndNewlyEnabledPastRemindersAreNotBackfilled() = runBlocking {
        add(); time += 50 * 60_000L
        engine.save(settings)
        assertNull(next); assertTrue(sent.isEmpty())
        assertEquals(settings, db.reminderDao().settings()!!.toDomain())
        createEngine().reconcile(); assertTrue(sent.isEmpty())
    }
    @Test fun sameTimeBatchUsesSingleAlarmAndAllEventsAreDelivered() = runBlocking {
        repeat(20) { add("任务 $it") }; engine.save(settings); fire()
        assertEquals(20, sent.size); assertNull(next)
        engine.save(settings.copy(leadMinutes = 5)); engine.reconcile()
        assertEquals(20, sent.size)
    }
    @Test fun timezoneChangeMovesFutureDeadlineWithoutResendingDeliveredOccurrence() = runBlocking {
        time -= 60 * 60_000L
        add(); engine.save(settings); val old = next!!
        val changed = ReminderEngine(db, { next = it }, delivery, { time }, { ZoneId.of("Asia/Tokyo") })
        changed.reconcile(); assertEquals(old - 3_600_000L, next)
        time = next!!; changed.reconcile(); engine.reconcile()
        assertEquals(1, sent.size)
    }

    @Test fun realRoomMigrationFromSixPreservesCalendarConversationAndFocus() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "reminders-migration-${UUID.randomUUID()}.db"
        try {
            // 旧表的结构在 6→7 中未变；去掉新增表构造独立的 v6 文件，再让 Room 校验完整迁移结果。
            val fixture = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                fixture.calendarEventDao().insert(CalendarEvent(title = "保留原日历", date = "2026-10-27", startTime = "09:00", endTime = "10:00", type = EventType.CLASS).toEntity())
                fixture.openHelper.writableDatabase.execSQL("INSERT INTO conversations (id, name, createdAt, mode) VALUES (999, '保留原会话', 1, 'STUDY_PLAN')")
                fixture.openHelper.writableDatabase.execSQL("INSERT INTO focus_sessions (id, activeSlot, startedAt, endedAt, focusedMs, outcome, payload) VALUES ('original-focus', NULL, 1, 2, 1000, 'COMPLETED', '{}')")
            } finally { fixture.close() }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                old.execSQL("DROP TABLE event_reminders")
                old.execSQL("DROP TABLE reminder_settings")
                old.execSQL("DROP TABLE planner_documents")
                old.execSQL("CREATE TABLE legacy_calendar (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, date TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, type TEXT NOT NULL, note TEXT, isDone INTEGER NOT NULL, planName TEXT)")
                old.execSQL("INSERT INTO legacy_calendar SELECT id,title,date,startTime,endTime,type,note,isDone,planName FROM calendar_events")
                old.execSQL("DROP TABLE calendar_events")
                old.execSQL("ALTER TABLE legacy_calendar RENAME TO calendar_events")
                old.execSQL("CREATE INDEX index_calendar_events_date ON calendar_events(date)")
                old.execSQL("DROP TABLE room_master_table")
                old.version = 6
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8).build()
            try {
                assertEquals("保留原日历", migrated.calendarEventDao().getAll().single().title)
                migrated.openHelper.readableDatabase.query("SELECT name FROM conversations WHERE id = 999").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("保留原会话", cursor.getString(0)) }
                assertEquals(1, migrated.focusDao().countSessions())
                assertTrue(migrated.reminderDao().all().isEmpty())
                assertNull(migrated.reminderDao().settings())
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
