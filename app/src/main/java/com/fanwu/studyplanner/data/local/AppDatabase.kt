package com.fanwu.studyplanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用本地数据库。
 * 单例获取方式供后续 Repository 使用。
 */
@Database(
    entities = [CalendarEventEntity::class, ChatMessageEntity::class, ConversationEntity::class, FocusSessionEntity::class, FocusIntervalEntity::class, ReminderSettingsEntity::class, EventReminderEntity::class, PlannerDocument::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): PlannerDocumentDao

    abstract fun reminderDao(): ReminderDao

    abstract fun focusDao(): FocusDao

    abstract fun calendarEventDao(): CalendarEventDao

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun conversationDao(): ConversationDao

    companion object {
        private const val DATABASE_NAME = "study_planner.db"

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN completedAt INTEGER")
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN subject TEXT")
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurringCourseId TEXT")
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN originalOccurrenceDate TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS planner_documents (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_settings (id INTEGER NOT NULL PRIMARY KEY, tasksEnabled INTEGER NOT NULL, coursesEnabled INTEGER NOT NULL, leadMinutes INTEGER NOT NULL, quietEnabled INTEGER NOT NULL, quietStart TEXT NOT NULL, quietEnd TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS event_reminders (eventId INTEGER NOT NULL PRIMARY KEY, occurrence TEXT NOT NULL, fireAt INTEGER NOT NULL, state TEXT NOT NULL, FOREIGN KEY(eventId) REFERENCES calendar_events(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS focus_sessions (id TEXT NOT NULL PRIMARY KEY, activeSlot INTEGER, startedAt INTEGER NOT NULL, endedAt INTEGER, focusedMs INTEGER NOT NULL, outcome TEXT NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_focus_sessions_activeSlot ON focus_sessions(activeSlot)")
                db.execSQL("CREATE TABLE IF NOT EXISTS focus_intervals (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL, zoneId TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES focus_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_intervals_sessionId ON focus_intervals(sessionId)")
            }
        }

        /** v1 -> v2：日历事件新增 isDone 字段 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE calendar_events ADD COLUMN isDone INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v2 -> v3：新增会话表，消息归属到会话，旧消息归入"默认对话" */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "ALTER TABLE `chat_messages` ADD COLUMN `conversationId` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId` " +
                        "ON `chat_messages` (`conversationId`)"
                )
                db.execSQL(
                    "INSERT INTO `conversations` (`name`, `createdAt`) " +
                        "VALUES ('默认对话', ${System.currentTimeMillis()})"
                )
                db.execSQL(
                    "UPDATE `chat_messages` SET `conversationId` = " +
                        "(SELECT MIN(`id`) FROM `conversations`)"
                )
            }
        }

        /** v3 -> v4：日历事件新增 planName（所属计划组）字段 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN planName TEXT")
            }
        }

        /** v4 -> v5：会话新增 mode，区分「学习计划」与「导入课表」 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN mode TEXT NOT NULL DEFAULT 'STUDY_PLAN'"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build().also { instance = it }
            }
    }
}
