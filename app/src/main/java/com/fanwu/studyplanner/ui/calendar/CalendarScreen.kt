package com.fanwu.studyplanner.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.EventType
import kotlinx.coroutines.launch
import com.fanwu.studyplanner.domain.durationMinutes
import com.fanwu.studyplanner.domain.formatHours
import com.fanwu.studyplanner.ui.theme.ApricotOrange
import com.fanwu.studyplanner.ui.theme.CreamBackground
import com.fanwu.studyplanner.ui.theme.DarkBar
import com.fanwu.studyplanner.ui.theme.OrangePrimary
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CalendarScreen(
    onOpenChat: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModel.factory(LocalContext.current)
    )
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val weekEvents by viewModel.weekEvents.collectAsStateWithLifecycle()
    val dayEvents by viewModel.selectedDayEvents.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    val dayListState = rememberLazyListState(initialFirstVisibleItemIndex = DAY_SCROLL_BASE)
    val scope = rememberCoroutineScope()

    var calendarExpanded by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var pendingDelete by remember { mutableStateOf<CalendarEvent?>(null) }

    Scaffold(
        containerColor = CreamBackground,
        floatingActionButton = {
            // 日历展开时隐藏，避免浮在暗色遮罩上
            if (!calendarExpanded) {
                FloatingActionButton(
                    onClick = {
                        editingEvent = null
                        showEditor = true
                    },
                    containerColor = OrangePrimary,
                    contentColor = Color.White
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                HomeHeader(
                    month = month,
                    onOpenChat = onOpenChat
                )

                DateStrip(
                    today = today,
                    selectedDate = selectedDate,
                    listState = dayListState,
                    onSelect = viewModel::selectDate,
                    onOpenCalendar = { calendarExpanded = true },
                    onBackToToday = {
                        viewModel.selectDate(today)
                        scope.launch {
                            dayListState.animateScrollToItem(DAY_SCROLL_BASE)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SummaryCard(
                    dayEvents = dayEvents,
                    weekEvents = weekEvents,
                    onOpenStats = onOpenStats
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (dayEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "这一天还没有安排，点右下角 + 添加",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(dayEvents, key = { it.id }) { event ->
                            DayEventCard(
                                event = event,
                                onClick = {
                                    editingEvent = event
                                    showEditor = true
                                },
                                onLongClick = { pendingDelete = event }
                            )
                        }
                    }
                }
            }

            // 遮罩：略暗色，点击收起；只覆盖内容区，不遮挡底部导航
            AnimatedVisibility(
                visible = calendarExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { calendarExpanded = false }
                )
            }

            // 日历面板：从顶部下拉展开，上滑收起
            AnimatedVisibility(
                visible = calendarExpanded,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                CalendarDropSheet(
                    today = today,
                    selectedDate = selectedDate,
                    onSelect = { date ->
                        viewModel.selectDate(date)
                        calendarExpanded = false
                    }
                )
            }
        }
    }

    if (showEditor) {
        EventEditorDialog(
            initialEvent = editingEvent,
            defaultDate = selectedDate,
            onDismiss = { showEditor = false },
            onSave = { event ->
                viewModel.saveEvent(event)
                showEditor = false
            }
        )
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除事件") },
            text = { Text("确定删除「${event.title}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(event)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/** 顶部：标题 + 月份 + 通知（暂作 AI 对话入口） */
@Composable
private fun HomeHeader(
    month: YearMonth,
    onOpenChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Study Planner",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = DarkBar,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${month.monthValue}月",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(10.dp))
        HeaderIconButton(onClick = onOpenChat) {
            Icon(Icons.Default.Notifications, contentDescription = "AI 对话", tint = DarkBar)
        }
    }
}

@Composable
private fun HeaderIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 日期条：白色胶囊容器，左侧日历按钮，中间按天横向滑动的日期。
 * 不在今天时，上方浮出「回到今天」按钮。
 */
private const val DAY_SCROLL_BASE = 3_000

@Composable
private fun DateStrip(
    today: LocalDate,
    selectedDate: LocalDate,
    listState: LazyListState,
    onSelect: (LocalDate) -> Unit,
    onOpenCalendar: () -> Unit,
    onBackToToday: () -> Unit
) {
    Column {
        AnimatedVisibility(visible = selectedDate != today) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .clickable(onClick = onBackToToday)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "回到今天",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OrangePrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CreamBackground)
                    .clickable(onClick = onOpenCalendar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "打开日历",
                    tint = DarkBar,
                    modifier = Modifier.size(22.dp)
                )
            }
            DayScroller(
                today = today,
                selectedDate = selectedDate,
                listState = listState,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayScroller(
    today: LocalDate,
    selectedDate: LocalDate,
    listState: LazyListState,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // 选中日期不在可见范围时（如从日历面板跳转），滚动到该日期
    LaunchedEffect(selectedDate) {
        val index = DAY_SCROLL_BASE + ChronoUnit.DAYS.between(today, selectedDate).toInt()
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (index !in visible) listState.animateScrollToItem(index)
    }

    LazyRow(state = listState, modifier = modifier) {
        items(count = 6_001) { offset ->
            val date = today.plusDays((offset - DAY_SCROLL_BASE).toLong())
            WeekDayCell(
                date = date,
                selected = date == selectedDate,
                weekdayLabel = WEEKDAY_LABELS[date.dayOfWeek.value - 1],
                onSelect = onSelect,
                modifier = Modifier.width(44.dp)
            )
        }
    }
}

/** 单个日期：选中为浅橙底+橙色深字，未选中为浅灰字 */
@Composable
private fun WeekDayCell(
    date: LocalDate,
    selected: Boolean,
    weekdayLabel: String,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = date == LocalDate.now()
    Column(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ApricotOrange.copy(alpha = 0.5f) else Color.Transparent)
            .clickable { onSelect(date) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                selected -> OrangePrimary
                isToday -> OrangePrimary
                else -> Color(0xFFB3AEA6)
            }
        )
        Text(
            text = weekdayLabel,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                selected -> OrangePrimary.copy(alpha = 0.85f)
                isToday -> OrangePrimary.copy(alpha = 0.7f)
                else -> Color(0xFFC6C1B9)
            }
        )
    }
}

/** 橙色 Summary 卡：当日计划/完成 + 本周总计 + 环形进度 + 各类型完成度 */
@Composable
private fun SummaryCard(
    dayEvents: List<CalendarEvent>,
    weekEvents: List<CalendarEvent>,
    onOpenStats: () -> Unit
) {
    val plannedMinutes = dayEvents.sumOf { it.durationMinutes() }
    val doneMinutes = dayEvents.filter { it.isDone }.sumOf { it.durationMinutes() }
    val weekTotalMinutes = weekEvents.sumOf { it.durationMinutes() }
    val progress = if (plannedMinutes > 0) {
        (doneMinutes.toFloat() / plannedMinutes).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ApricotOrange),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkBar,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Details ›",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkBar.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenStats)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryMetric("计划", formatHours(plannedMinutes))
                    SummaryMetric("已完成", formatHours(doneMinutes))
                    SummaryMetric("本周总计", formatHours(weekTotalMinutes))
                }
                ProgressDonut(progress = progress)
            }

            val typeProgress = weekEvents
                .groupBy { it.type }
                .map { (type, events) -> Triple(type, events.count { it.isDone }, events.size) }
                .sortedByDescending { it.third }
                .take(3)

            if (typeProgress.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                typeProgress.forEachIndexed { index, (type, done, total) ->
                    TypeProgressRow(
                        label = type.label(),
                        done = done,
                        total = total,
                        color = when (index) {
                            0 -> DarkBar
                            1 -> OrangePrimary
                            else -> Color(0xFFA8D5BA)
                        }
                    )
                    if (index < typeProgress.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/** 白色小药丸（标签+数值）+ 右侧大数字 */
@Composable
private fun SummaryMetric(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = DarkBar)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = DarkBar.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DarkBar
        )
    }
}

@Composable
private fun ProgressDonut(progress: Float) {
    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            drawArc(
                color = Color.White.copy(alpha = 0.45f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = DarkBar,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = DarkBar
            )
            Text(
                text = "progress",
                style = MaterialTheme.typography.bodySmall,
                color = DarkBar.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TypeProgressRow(label: String, done: Int, total: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = DarkBar,
            modifier = Modifier.width(56.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.55f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (total > 0) done.toFloat() / total else 0f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Text(
            text = "$done/$total",
            style = MaterialTheme.typography.bodySmall,
            color = DarkBar.copy(alpha = 0.7f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
    }
}

/** 当日任务卡：色块图标 + 标题 + 时长 + 箭头；点击编辑，长按删除 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayEventCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(eventTypeColor(event.type).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = event.type.label().take(1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = eventTypeColor(event.type)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkBar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${event.durationMinutes()} 分钟 · ${event.startTime}-${event.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 未选中日期文字色（浅灰） */
private val CalendarDateGray = Color(0xFF9E9990)

/**
 * 下拉日历面板：像下拉通知栏一样从顶部展开，占内容区约 86%，
 * 底部露出略暗的背景，不覆盖底部导航；点击暗色区域收起。
 */
@Composable
private fun CalendarDropSheet(
    today: LocalDate,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val currentMonth = remember { YearMonth.from(today) }
    val months = remember(currentMonth) {
        (-18..18).map { currentMonth.plusMonths(it.toLong()) }
    }
    val initialIndex = remember(currentMonth, selectedDate) {
        val offset = ChronoUnit.MONTHS.between(currentMonth, YearMonth.from(selectedDate)).toInt()
        (18 + offset).coerceIn(0, months.lastIndex)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    Surface(
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
    ) {
        Column {
            // 顶部拖动把手
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFDDD9D3))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 星期表头（左侧留出周数位置）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.width(28.dp))
                WEEKDAY_LABELS.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB3AEA6)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(months) { m ->
                    MonthSection(
                        month = m,
                        today = today,
                        selectedDate = selectedDate,
                        onSelect = onSelect
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrangePrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(today) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** 一个月：标题 + 学期周次 + 每行左侧周号 + 日期格 */
@Composable
private fun MonthSection(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val leading = month.atDay(1).dayOfWeek.value - 1 // 周一为每周第一天
    val totalCells = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    val firstMonday = month.atDay(1).minusDays(leading.toLong())
    val lastMonday = firstMonday.plusDays((totalCells - 7).toLong())
    val weekRange = "第 ${firstMonday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}-" +
        "${lastMonday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} 周"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "${month.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DarkBar
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = weekRange,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB3AEA6),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        for (row in 0 until totalCells / 7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = firstMonday.plusDays((row * 7).toLong())
                        .get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                        .toString(),
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCFCAC2)
                )
                for (col in 0 until 7) {
                    val day = row * 7 + col - leading + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            SheetDayCell(
                                date = date,
                                selected = date == selectedDate,
                                isToday = date == today,
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetDayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ApricotOrange.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected || isToday) FontWeight.Bold else null,
            color = when {
                selected -> OrangePrimary
                isToday -> OrangePrimary
                else -> CalendarDateGray
            }
        )
    }
}

/** 按事件类型区分颜色 */
fun eventTypeColor(type: EventType): Color = when (type) {
    EventType.EXAM -> Color(0xFFE57373)
    EventType.CLASS -> Color(0xFF64B5F6)
    EventType.STUDY_TASK -> Color(0xFF81C784)
}
