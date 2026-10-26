package com.fanwu.studyplanner.reminders

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fanwu.studyplanner.MainActivity
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun notificationIsPrivateOpensLatestEventAndDeletedEventHasSafeDestination() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(context.packageName.endsWith(".f01test"))
        val dao = AppDatabase.getInstance(context).calendarEventDao()
        val reminders = AppDatabase.getInstance(context).reminderDao()
        val originalSettings = reminders.settings()
        val manager = context.getSystemService(NotificationManager::class.java)
        val title = "F03 通知验收 ${UUID.randomUUID().toString().take(6)}"
        val event = CalendarEvent(title = title, date = LocalDate.now().plusDays(2).toString(), startTime = "09:00", endTime = "10:00", type = EventType.CLASS, note = "私密教室备注")
        val id = dao.insert(event.toEntity())
        val stored = event.copy(id = id)
        val spec = ReminderPolicy.spec(stored, ReminderSettings(coursesEnabled = true), ZoneId.systemDefault())!!
        val tag = reminderTag(id, spec.occurrence)
        try {
            reminders.saveSettings((originalSettings?.toDomain() ?: ReminderSettings()).copy(coursesEnabled = true).toEntity())
            reminders.put(EventReminderEntity(id, spec.occurrence, spec.fireAt, "DELIVERED"))
            ReminderRuntime.engine.reconcile()
            val delivery = EventNotifications(context)
            assertTrue(delivery.available())
            delivery.notify(stored, spec)
            // 某些模拟器的 NotificationManager 会异步发布通知。
            rule.waitUntil(10_000) { manager.activeNotifications.any { it.tag == tag } }
            val notification = manager.activeNotifications.single { it.tag == tag }.notification
            assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
            assertEquals("打开应用查看安排", notification.publicVersion.extras.getString(Notification.EXTRA_TEXT))
            assertFalse(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("私密"))
            notification.contentIntent.send()
            rule.waitUntil(10_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText(title).assertIsDisplayed()
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single().recreate()
            }
            rule.waitForIdle()
            rule.waitUntil(10_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            dao.deleteById(id)
            rule.waitUntil(10_000) { rule.onAllNodesWithText("日程已不存在").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("返回日历").assertExists()
        } finally {
            dao.deleteById(id)
            reminders.saveSettings(originalSettings ?: ReminderSettings().toEntity())
            ReminderRuntime.engine.reconcile()
            manager.cancel(tag, 0)
            instrumentation.runOnMainSync {
                listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED).flatMap {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
                }.filterIsInstance<MainActivity>().forEach { it.finish() }
            }
        }
    }
}
