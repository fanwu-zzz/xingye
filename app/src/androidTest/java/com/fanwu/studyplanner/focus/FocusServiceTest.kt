package com.fanwu.studyplanner.focus

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fanwu.studyplanner.MainActivity
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.local.toEntity
import com.fanwu.studyplanner.data.repository.FocusRepository
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 使用唯一测试轮次并在 finally 精确清理；不删除任何已有学习记录或任务。 */
@RunWith(AndroidJUnit4::class)
class FocusServiceTest {
    @Test fun realServiceHandlesBackgroundPauseScreenOffAndBreak() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val database = AppDatabase.getInstance(context)
        val repository = FocusRepository.get(context)
        assumeTrue("保留用户正在运行的专注", repository.current() == null)
        val originalCount = database.focusDao().countSessions()
        fun openFocus() {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(FocusNotifications.OPEN_FOCUS, true))
        }
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
        openFocus()
        delay(700)
        instrumentation.waitForIdleSync()
        val created = repository.start(1, 1, null)
        // 仅缩短本测试的唯一轮次，避免设备测试必须等待整分钟。
        database.focusDao().update(created.copy(focusDurationMs = 4_000, breakDurationMs = 3_000).toEntity())
        try {
            FocusService.start(context)
            delay(1_200)
            shell("input keyevent 3")
            val paused = repository.act(created.id, FocusAction.PAUSE)!!.session
            val pausedTime = paused.focusedMs
            delay(1_200)
            assertEquals(pausedTime, repository.current()!!.focusedMs)
            assertEquals(FocusPhase.FOCUS_PAUSED, repository.current()!!.phase)
            openFocus()
            delay(400)
            repository.act(created.id, FocusAction.RESUME)
            FocusService.start(context)
            withTimeout(12_000) {
                while (repository.current()?.phase != FocusPhase.FOCUS_READY) delay(100)
            }
            assertEquals(4_000L, repository.current()!!.focusedMs)
            repository.act(created.id, FocusAction.START_BREAK)
            FocusService.start(context)
            shell("input keyevent 223")
            delay(1_500)
            shell("input keyevent 224")
            withTimeout(12_000) { while (repository.current() != null) delay(100) }
            val saved = repository.history.first().first { it.id == created.id }
            assertEquals("COMPLETED", saved.outcome)
            assertEquals(4_000L, saved.focusedMs)
            assertEquals(4_000L, database.focusDao().intervalsFor(created.id).sumOf { it.durationMs })
        } finally {
            shell("input keyevent 224")
            repository.act(created.id, FocusAction.FINISH)
            if (repository.current() == null) {
                context.stopService(Intent(context, FocusService::class.java))
                FocusAlarm.sync(context, null)
                context.getSystemService(NotificationManager::class.java).cancel(FocusNotifications.ID)
            }
            database.openHelper.writableDatabase.execSQL("DELETE FROM focus_sessions WHERE id = ?", arrayOf(created.id))
        }
        assertEquals(originalCount, database.focusDao().countSessions())
    }
}
