package com.fanwu.studyplanner.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.ui.calendar.label
import com.fanwu.studyplanner.ui.stats.daysLeft
import com.fanwu.studyplanner.ui.stats.daysLeftText
import com.fanwu.studyplanner.ui.theme.ApricotOrange
import com.fanwu.studyplanner.ui.theme.DarkBar
import com.fanwu.studyplanner.ui.theme.OrangePrimary
import java.time.LocalDate

@Composable
fun TasksScreen(
    onBack: () -> Unit,
    planFilter: String? = null,
    viewModel: TasksViewModel = viewModel(
        factory = TasksViewModel.factory(LocalContext.current)
    )
) {
    val tasks by viewModel.pendingTasks.collectAsStateWithLifecycle()
    // 从统计页某个计划组进入时只显示该组任务
    val visibleTasks = remember(tasks, planFilter) {
        if (planFilter == null) tasks else tasks.filter { it.planName == planFilter }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "待完成任务",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkBar,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                // 筛选入口预留，后续支持按类型筛选
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "筛选",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Text(
                text = if (planFilter == null) {
                    "共 ${visibleTasks.size} 个任务 · 按截止时间排序"
                } else {
                    "计划「$planFilter」· 共 ${visibleTasks.size} 个任务"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )

            if (visibleTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有待完成的任务",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(visibleTasks, key = { it.id }) { event ->
                        TaskRow(
                            event = event,
                            onToggle = { viewModel.setDone(event.id, !event.isDone) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(event: CalendarEvent, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleCheckbox(checked = event.isDone, onClick = onToggle)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val date = LocalDate.parse(event.date)
                val meta = buildString {
                    append(event.type.label())
                    append(" · 截止 ${date.monthValue}月${date.dayOfMonth}日")
                    event.planName?.let { append(" · $it") }
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            DaysLeftBadge(daysLeft = daysLeft(event))
        }
    }
}

/** 圆形复选框：勾选后标记任务完成 */
@Composable
private fun CircleCheckbox(checked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) OrangePrimary else Color.Transparent)
            .border(2.dp, if (checked) OrangePrimary else Color(0xFFC9C4BC), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = "已完成",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** “剩 N 天”徽章：≤2 天实心橙，≤5 天橙色描边，其余灰色描边 */
@Composable
private fun DaysLeftBadge(daysLeft: Long) {
    val text = daysLeftText(daysLeft)
    when {
        daysLeft <= 2 -> Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ApricotOrange)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }

        daysLeft <= 5 -> Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, OrangePrimary, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(text, color = OrangePrimary, style = MaterialTheme.typography.bodySmall)
        }

        else -> Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
