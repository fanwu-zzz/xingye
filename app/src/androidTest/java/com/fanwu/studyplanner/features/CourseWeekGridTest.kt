package com.fanwu.studyplanner.features

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.fanwu.studyplanner.MainActivity
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.components.BrutalPage
import com.fanwu.studyplanner.ui.features.CourseWeekGrid
import com.fanwu.studyplanner.ui.theme.StudyPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** 纯内存课表，独立测试包；不接触日常应用数据。 */
class CourseWeekGridTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun allSevenDaysFitAndOverlappingCoursesRemainAccessible() {
        check(compose.activity.packageName == "com.fanwu.studyplanner.f01test")
        val monday = mondayOf(LocalDate.now())
        fun course(day: Int, title: String, start: String, end: String, room: String) = CalendarEvent(
            title = title, date = monday.plusDays(day - 1L).toString(), startTime = start, endTime = end,
            type = EventType.CLASS, note = room)
        val courses = listOf(
            course(1, "计算机网络", "14:00", "15:40", "D1219"),
            course(2, "操作系统", "10:00", "11:40", "D1210"),
            course(2, "JAVA企业级应用", "16:00", "18:00", "D1219"),
            course(3, "软件工程", "10:00", "11:40", "D1340"),
            course(3, "电子设计与科技创新", "16:00", "18:00", "DS1407"),
            course(4, "操作系统", "10:00", "11:40", "D1210"),
            course(5, "重叠课程甲", "10:00", "11:40", "A101"),
            course(5, "重叠课程乙", "10:30", "12:00", "B202"),
            course(5, "软件工程", "14:00", "15:40", "D1340"),
            course(7, "JAVA企业级应用", "14:00", "18:00", "DS1404"))
        var opened: CalendarEvent? = null
        val arguments = InstrumentationRegistry.getArguments()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val original = LocalConfiguration.current
                val density = LocalDensity.current
                val width = arguments.getString("widthDp")?.toInt() ?: 360
                val configuration = Configuration(original).apply {
                    screenWidthDp = width
                    screenHeightDp = original.screenHeightDp * width / original.screenWidthDp
                }
                CompositionLocalProvider(LocalConfiguration provides configuration,
                    LocalDensity provides Density(density.density * original.screenWidthDp / width,
                        arguments.getString("fontScale")?.toFloat() ?: 1f)) {
                    StudyPlannerTheme {
                        BrutalPage("本周课表", "七天同屏 · 点击课程查看详情") {
                            CourseWeekGrid(monday, courses) { opened = it }
                        }
                    }
                }
            }
        }
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
        if (arguments.getString("visualPause") == "true") {
            println("COURSE_WEEK_VISUAL_READY")
            Thread.sleep(25_000)
        }
        compose.onNode(hasContentDescription("重叠课程甲", substring = true)).performClick()
        compose.onNodeWithText("选择重叠课程").assertIsDisplayed()
        compose.onNodeWithText("重叠课程乙").performClick()
        compose.runOnIdle { assertEquals("重叠课程乙", opened?.title) }
    }
}
