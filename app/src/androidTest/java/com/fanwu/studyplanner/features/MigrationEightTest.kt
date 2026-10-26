package com.fanwu.studyplanner.features

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class MigrationEightTest {
    @Test fun migrationSevenToEightPreservesDailyDataAndDoesNotInventCompletionDates() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".f01test"))
        val name = "features-migration-${UUID.randomUUID()}.db"
        try {
            val fixture = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val id = fixture.calendarEventDao().insert(CalendarEvent(title = "旧任务", date = "2026-10-26", startTime = "08:00", endTime = "09:00", type = EventType.STUDY_TASK, isDone = true).toEntity())
                fixture.reminderDao().saveSettings(ReminderSettingsEntity(tasksEnabled = true))
                fixture.reminderDao().put(EventReminderEntity(id, "old", 1, "DELIVERED"))
                fixture.conversationDao().insert(ConversationEntity(name = "保留对话", createdAt = 1))
            } finally { fixture.close() }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                old.execSQL("DROP TABLE planner_documents")
                old.execSQL("CREATE TABLE legacy_calendar (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, date TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, type TEXT NOT NULL, note TEXT, isDone INTEGER NOT NULL, planName TEXT)")
                old.execSQL("INSERT INTO legacy_calendar SELECT id,title,date,startTime,endTime,type,note,isDone,planName FROM calendar_events")
                old.execSQL("DROP TABLE calendar_events")
                old.execSQL("ALTER TABLE legacy_calendar RENAME TO calendar_events")
                old.execSQL("CREATE INDEX index_calendar_events_date ON calendar_events(date)")
                old.execSQL("DROP TABLE room_master_table")
                old.version = 7
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_7_8).build()
            try {
                assertEquals("旧任务", migrated.calendarEventDao().getAll().single().title)
                assertTrue(migrated.calendarEventDao().getAll().single().isDone)
                assertNull(migrated.calendarEventDao().getAll().single().completedAt)
                assertEquals("保留对话", migrated.conversationDao().all().single().name)
                assertTrue(migrated.reminderDao().settings()!!.tasksEnabled)
                assertEquals("DELIVERED", migrated.reminderDao().all().single().state)
                assertTrue(migrated.documentDao().all().isEmpty())
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
