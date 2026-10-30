package com.fanwu.studyplanner.features

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.fanwu.studyplanner.MainActivity
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.calendar.EventEditorDialog
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.StudyPlannerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** 仅在独立测试包展示内存表单，不写入手机数据库。 */
class DeadlineEditorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectingDeadlineShowsOneTimeFieldAndPreservesDueTimeWhenEditing() {
        check(compose.activity.packageName == "com.fanwu.studyplanner.f01test")
        var saved: CalendarEvent? = null
        val arguments = InstrumentationRegistry.getArguments()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val original = LocalConfiguration.current
                val density = LocalDensity.current
                val width = arguments.getString("widthDp")?.toInt() ?: 360
                val config = Configuration(original).apply { screenWidthDp = width; screenHeightDp = original.screenHeightDp * width / original.screenWidthDp }
                CompositionLocalProvider(LocalConfiguration provides config,
                    LocalDensity provides Density(density.density * original.screenWidthDp / width, arguments.getString("fontScale")?.toFloat() ?: 1f)) {
                    StudyPlannerTheme {
                        var show by remember { mutableStateOf(true) }
                        var current by remember { mutableStateOf<CalendarEvent?>(null) }
                        BrutalPage("任务中心") { BrutalButton("重新编辑", { show = true }) }
                        if (show) EventEditorDialog(current, LocalDate.now(), { show = false }) {
                            saved = it; current = it; show = false
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("截止任务").performClick()
        compose.onNodeWithText("开始 HH:mm").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("任务名称")).performScrollTo().performTextInput("提交数学作业")
        compose.onNode(hasSetTextAction() and hasText("截止时间（HH:mm）")).performScrollTo().assertTextContains("23:59")
        compose.onNode(hasSetTextAction() and hasText("截止时间（HH:mm）")).performTextReplacement("18:00")
        compose.onNodeWithText("保存截止任务").performClick()
        compose.runOnIdle {
            assertEquals(EventType.DEADLINE, saved!!.type)
            assertEquals("18:00", saved!!.startTime)
            assertEquals("18:00", saved!!.endTime)
        }
        compose.onNodeWithText("重新编辑").performClick()
        compose.onNode(hasSetTextAction() and hasText("截止时间（HH:mm）")).performScrollTo().assertTextContains("18:00")
        compose.onNodeWithText("保存截止任务").assertIsDisplayed()
        compose.onNodeWithText("截止任务").performScrollTo()
        if (arguments.getString("visualPause") == "true") {
            println("DEADLINE_EDITOR_VISUAL_READY")
            Thread.sleep(25_000)
        }
    }
}
