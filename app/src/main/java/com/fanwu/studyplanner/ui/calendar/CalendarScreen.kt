package com.fanwu.studyplanner.ui.calendar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun CalendarScreen(
    onOpenChat: () -> Unit = {}, onOpenStats: () -> Unit = {},
    onOpenTasks: () -> Unit = {}, onOpenSchedule: () -> Unit = {}, onOpenFocus: () -> Unit = {},
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(LocalContext.current))
) {
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val events by viewModel.selectedDayEvents.collectAsStateWithLifecycle()
    val monthEvents by viewModel.monthEvents.collectAsStateWithLifecycle()
    val week by viewModel.weekEvents.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CalendarEvent?>(null) }
    var deleting by remember { mutableStateOf<CalendarEvent?>(null) }
    val today = LocalDate.now()
    val studyEvents = events.filter { it.type != EventType.CLASS }
    val completed = studyEvents.count { it.isDone }
    val progress = if (studyEvents.isEmpty()) 0f else completed.toFloat() / studyEvents.size

    BrutalPage("学习日历", "${today.year} 年 · 把目标变成今天的行动", actions = {
        BrutalIconButton(Icons.Outlined.CalendarMonth, "展开月历", { expanded = !expanded })
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${date.monthValue} 月 ${date.dayOfMonth} 日", style = MaterialTheme.typography.headlineSmall)
                Text("星期${WEEK_LABELS[date.dayOfWeek.value - 1]} · ${if (date == today) "今天" else date.year}", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
            }
            TextButton({ viewModel.selectDate(today) }) { Text("回到今天", color = BrutalBlack) }
        }
        DayStrip(date, viewModel::selectDate)
        if (expanded) {
            BrutalPanel(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrutalIconButton(Icons.Outlined.ChevronLeft, "上个月", viewModel::previousMonth)
                    Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    BrutalIconButton(Icons.Outlined.ChevronRight, "下个月", viewModel::nextMonth)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gridWidth = maxOf(maxWidth, 336.dp)
                    Column(Modifier.horizontalScroll(rememberScrollState()).width(gridWidth)) {
                        MonthGrid(month, date, monthEvents.map { it.date }.toSet()) {
                            viewModel.selectDate(it); expanded = false
                        }
                    }
                }
            }
        }
        BrutalPanel(color = BrutalYellow, shadow = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (date == today) "今日作战板" else "当日作战板", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                BrutalTag(if (studyEvents.isEmpty()) "等待安排" else "${completed}/${studyEvents.size} 已完成", BrutalWhite)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrutalMetric(formatHours(studyEvents.sumOf { it.durationMinutes() }), "当日计划", Modifier.weight(1f))
                BrutalMetric(formatHours(studyEvents.filter { it.isDone }.sumOf { it.durationMinutes() }), "已完成安排", Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(12.dp).background(BrutalWhite).border(2.dp, BrutalBlack)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(BrutalBlack))
            }
            Text("本周计划 ${formatHours(week.filter { it.type != EventType.CLASS }.sumOf { it.durationMinutes() })} · 课程不计入学习计划时长",
                style = MaterialTheme.typography.bodySmall)
        }
        BrutalButton("和 AI 制定学习计划", onOpenChat, Modifier.fillMaxWidth(), icon = Icons.Outlined.AutoAwesome)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalChoice("全部任务", false, onOpenTasks, Modifier.weight(1f))
            BrutalChoice("本周课表", false, onOpenSchedule, Modifier.weight(1f))
            BrutalChoice("开始专注", false, onOpenFocus, Modifier.weight(1f))
        }
        BrutalSection("当日日程") {
            TextButton({ selected = null; editor = true }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = BrutalBlack)
                Text("添加", color = BrutalBlack)
            }
        }
        if (events.isEmpty()) {
            BrutalEmpty("给今天留一个小目标", "添加一项学习任务，或让 AI 把你的考试目标拆成每天的计划。", "添加第一项任务", { selected = null; editor = true })
        } else {
            events.sortedWith(compareBy<CalendarEvent> { it.isDone }.thenBy { it.startTime }).forEach { event ->
                key(event.id) {
                    SwipeEventRow(event, onOpen = { selected = event }, onToggle = { viewModel.setDone(event.id, !event.isDone) })
                }
            }
            Text("点击查看详情 · 左滑切换完成状态", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
        }
        BrutalLinkRow("查看学习趋势", "看看这周的安排、完成率和计划分组", onOpenStats, icon = Icons.Outlined.BarChart)
    }

    if (editor) EventEditorDialog(selected, date, { editor = false; selected = null }) {
        viewModel.saveEvent(it); viewModel.selectDate(LocalDate.parse(it.date)); editor = false; selected = null
    }
    if (!editor) selected?.let { event ->
        EventDetailDialog(event, { selected = null }, onEdit = { editor = true },
            onDelete = { deleting = event; selected = null }, onToggle = { viewModel.setDone(event.id, !event.isDone); selected = null })
    }
    deleting?.let { event ->
        BrutalConfirm("删除日程？", "「${event.title}」将从日历中删除。", "确认删除",
            { viewModel.deleteEvent(event); deleting = null }, { deleting = null }, danger = true)
    }
}

private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
private fun DayStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    val center = 3000
    val state = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = (center + ChronoUnit.DAYS.between(today, selected).toInt()).coerceIn(0, 6000)
        state.scrollToItem((index - 2).coerceAtLeast(0))
    }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(6001) { index ->
            val day = today.plusDays((index - center).toLong())
            Column(
                Modifier.width(48.dp).background(if (selected == day) BrutalYellow else BrutalWhite)
                    .border(2.dp, BrutalBlack).clickable { onSelect(day) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(if (day == today) "今" else WEEK_LABELS[day.dayOfWeek.value - 1], style = MaterialTheme.typography.labelMedium)
                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, eventDates: Set<String>, onSelect: (LocalDate) -> Unit) {
    Row {
        WEEK_LABELS.forEach { Text(it, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    val leading = month.atDay(1).dayOfWeek.value - 1
    repeat((leading + month.lengthOfMonth() + 6) / 7) { row ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { column ->
                val number = row * 7 + column - leading + 1
                if (number in 1..month.lengthOfMonth()) {
                    val day = month.atDay(number)
                    Column(
                        Modifier.weight(1f).heightIn(min = 48.dp).background(if (day == selected) BrutalYellow else BrutalWhite)
                            .clickable { onSelect(day) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(number.toString(), style = MaterialTheme.typography.titleSmall)
                        Text(if (day.toString() in eventDates) "▪" else " ", style = MaterialTheme.typography.labelSmall)
                    }
                } else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SwipeEventRow(event: CalendarEvent, onOpen: () -> Unit, onToggle: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart && event.type != EventType.CLASS) onToggle()
        false
    })
    SwipeToDismissBox(state, enableDismissFromStartToEnd = false, enableDismissFromEndToStart = event.type != EventType.CLASS,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(BrutalLime).padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                Text(if (event.isDone) "恢复待办" else "标记完成", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) { EventRow(event, onOpen, onToggle) }
}

fun eventTypeColor(type: EventType) = when (type) {
    EventType.EXAM -> BrutalOrange
    EventType.CLASS -> BrutalCyan
    EventType.STUDY_TASK -> BrutalLime
    EventType.DEADLINE -> BrutalOrange
}
