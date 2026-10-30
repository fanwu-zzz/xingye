package com.fanwu.studyplanner.ui.tasks

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.calendar.EventEditorDialog
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

internal fun filterTasks(tasks: List<CalendarEvent>, status: String, query: String, type: EventType?, plan: String?, today: LocalDate,
                         now: LocalDateTime = today.atStartOfDay()): List<CalendarEvent> =
    tasks.filter { event ->
        (plan == null || event.planName == plan) && (type == null || event.type == type) &&
            (query.isBlank() || listOfNotNull(event.title, event.note, event.planName).any { it.contains(query.trim(), true) }) &&
            when (status) {
                "待办" -> !event.isDone
                "今天" -> event.date == today.toString() && !event.isDone
                "逾期" -> event.isOverdue(now)
                "已完成" -> event.isDone
                else -> true
            }
    }.sortedWith(compareBy<CalendarEvent> { it.isDone }.thenBy { it.date }.thenBy { it.endTime })

@Composable
fun TasksScreen(onBack: () -> Unit, planFilter: String? = null,
    viewModel: TasksViewModel = viewModel(factory = TasksViewModel.factory(LocalContext.current))) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("待办") }
    var type by remember { mutableStateOf<EventType?>(null) }
    var selected by remember { mutableStateOf<CalendarEvent?>(null) }
    var editor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CalendarEvent?>(null) }
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(30_000); value = LocalDateTime.now() }
    }
    val today = now.toLocalDate()
    val filtered = remember(tasks, query, status, type, planFilter, now) { filterTasks(tasks, status, query, type, planFilter, today, now) }
    BrutalPage("任务中心", planFilter ?: "把下一步看清楚，把一件事做完", onBack,
        scrollable = false, modifier = Modifier.navigationBarsPadding(), actions = {
            BrutalIconButton(Icons.Outlined.Add, "添加任务", { selected = null; editor = true })
        }) {
        BrutalField(query, { query = it }, "搜索任务 / 计划名称")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("待办", "今天", "逾期", "已完成", "全部").forEach { label -> BrutalChoice(label, status == label, { status = label }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalChoice("全部类型", type == null, { type = null })
            BrutalChoice("学习任务", type == EventType.STUDY_TASK, { type = EventType.STUDY_TASK })
            BrutalChoice("截止任务", type == EventType.DEADLINE, { type = EventType.DEADLINE })
            BrutalChoice("考试", type == EventType.EXAM, { type = EventType.EXAM })
        }
        Text("找到 ${filtered.size} 项 · 按日期与截止时间排序", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (filtered.isEmpty()) item {
                BrutalEmpty("这里暂时没有任务", if (query.isNotBlank()) "换个关键词或筛选条件试试。" else "添加任务，或在 AI 对话里生成学习计划。",
                    "添加任务", { selected = null; editor = true })
            }
            filtered.groupBy { it.date }.forEach { (date, events) ->
                item(key = "date-$date") {
                    Text(date + when { date == today.toString() -> " · 今天"; date < today.toString() -> " · 已过日期"; else -> "" },
                        style = MaterialTheme.typography.titleSmall)
                }
                items(events, key = { it.id }) { event ->
                    EventRow(event, { selected = event }, { viewModel.setDone(event.id, !event.isDone) })
                }
            }
        }
    }
    if (editor) EventEditorDialog(selected, today, { editor = false; selected = null }) {
        viewModel.saveEvent(it); editor = false; selected = null
    }
    if (!editor) selected?.let { event ->
        EventDetailDialog(event, { selected = null }, { editor = true }, { deleting = event; selected = null },
            { viewModel.setDone(event.id, !event.isDone); selected = null })
    }
    deleting?.let { event ->
        BrutalConfirm("删除任务？", "「${event.title}」将同时从日历中删除。", "确认删除",
            { viewModel.deleteEvent(event); deleting = null }, { deleting = null }, true)
    }
}
