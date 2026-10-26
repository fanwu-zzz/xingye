package com.fanwu.studyplanner.ui.features

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.calendar.*
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun ScheduleScreen(
    onBack: () -> Unit, onImport: () -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(LocalContext.current))
) {
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()
    val events by viewModel.weekEvents.collectAsStateWithLifecycle()
    val monday = selected.with(DayOfWeek.MONDAY)
    val courses = events.filter { it.type == EventType.CLASS }
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var detail by remember { mutableStateOf<CalendarEvent?>(null) }
    var delete by remember { mutableStateOf<CalendarEvent?>(null) }
    var setup by remember { mutableStateOf(false) }
    BackHandler(setup) { setup = false }
    if (setup) {
        SemesterScreen(onBack = { setup = false })
        return
    }
    BrutalPage("本周课表", "课程与学习计划，放在同一个节奏里", onBack, modifier = Modifier.navigationBarsPadding()) {
        BrutalPanel(color = BrutalCyan, shadow = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrutalIconButton(Icons.Outlined.ChevronLeft, "上一周", { viewModel.selectDate(selected.minusWeeks(1)) })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${monday.monthValue}/${monday.dayOfMonth} — ${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}", style = MaterialTheme.typography.headlineSmall)
                    Text("本周 ${courses.size} 节课程", style = MaterialTheme.typography.bodySmall)
                }
                BrutalIconButton(Icons.Outlined.ChevronRight, "下一周", { viewModel.selectDate(selected.plusWeeks(1)) })
            }
            Text("教室、时间与课程来源均来自日历中的真实安排。", style = MaterialTheme.typography.bodySmall)
        }
        BrutalButton("从 Excel 导入课表", onImport, icon = Icons.Outlined.UploadFile)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalChoice("手动添加", false, { editing = null; add = true }, Modifier.weight(1f))
            BrutalChoice("回到本周", false, { viewModel.selectDate(LocalDate.now()) }, Modifier.weight(1f))
        }
        BrutalLinkRow("学期与节次设置", "开学日期、上课节次、单双周规则", { setup = true }, icon = Icons.Outlined.Tune)
        if (courses.isEmpty()) BrutalEmpty("这一周还没有课", "可手动录入单次课程，或上传教务系统导出的 Excel，再预览并确认导入。")
        if (courses.isNotEmpty()) CourseWeekGrid(monday, courses) { detail = it }
        repeat(7) { index ->
            val date = monday.plusDays(index.toLong())
            val daily = courses.filter { it.date == date.toString() }.sortedBy { it.startTime }
            if (daily.isNotEmpty()) {
                BrutalSection("周${listOf("一", "二", "三", "四", "五", "六", "日")[index]} · ${date.monthValue}/${date.dayOfMonth}")
                daily.forEach { event -> EventRow(event, { detail = event }, {}) }
            }
        }
    }
    if (add) EventEditorDialog(editing, selected, { add = false }, defaultType = EventType.CLASS) {
        viewModel.saveEvent(it); add = false
    }
    detail?.let { event ->
        EventDetailDialog(event, { detail = null }, { editing = event; add = true; detail = null }, { delete = event; detail = null }, {})
    }
    delete?.let { event ->
        BrutalConfirm("删除课程？", "只删除 ${event.date} 的「${event.title}」，不影响其他日期。", "确认删除", { viewModel.deleteEvent(event); delete = null }, { delete = null }, true)
    }
}
