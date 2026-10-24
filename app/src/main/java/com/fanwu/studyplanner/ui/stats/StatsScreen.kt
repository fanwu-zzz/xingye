package com.fanwu.studyplanner.ui.stats

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.ui.theme.ApricotOrange
import com.fanwu.studyplanner.ui.theme.DarkBar
import com.fanwu.studyplanner.ui.theme.OrangePrimary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun StatsScreen(
    onOpenTasks: (String?) -> Unit = {},
    viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.factory(LocalContext.current)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<PlanGroup?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 标题 + 周/月切换（当前仅实现周视图，月视图后续开放）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "统计",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkBar,
                    modifier = Modifier.weight(1f)
                )
                WeekMonthPill()
            }

            PendingSummaryCard(
                pendingCount = state.pendingCount,
                groups = state.groups,
                onOpenTasks = onOpenTasks,
                onDeleteRequest = { deleteTarget = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            WeekOverviewCard(
                hoursText = state.weekHoursText,
                completionText = state.completionText,
                streakDays = state.streakDays
            )

            Spacer(modifier = Modifier.height(12.dp))

            DailyDurationCard(dailyMinutes = state.dailyMinutes)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    deleteTarget?.let { group ->
        val planName = group.planName ?: return@let
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除计划「$planName」？") },
            text = {
                Text(
                    "将同时删除该计划下的 ${group.count} 个任务，" +
                        "日历上对应日期的日程也会一并删除。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlan(planName)
                        deleteTarget = null
                        Toast.makeText(context, "已删除计划「$planName」", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WeekMonthPill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(OrangePrimary)
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text("周", color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                "月",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PendingSummaryCard(
    pendingCount: Int,
    groups: List<PlanGroup>,
    onOpenTasks: (String?) -> Unit,
    onDeleteRequest: (PlanGroup) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ApricotOrange),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "待完成任务",
                    color = DarkBar.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "查看全部 ›",
                    color = DarkBar,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenTasks(null) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = pendingCount.toString(),
                    color = DarkBar,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " 个任务待完成",
                    color = DarkBar.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (groups.isEmpty()) {
                Text(
                    text = "太棒了，没有待完成的任务",
                    color = DarkBar.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                groups.take(3).forEach { group ->
                    PlanGroupRow(
                        group = group,
                        onClick = { onOpenTasks(group.planName) },
                        onDeleteRequest = { onDeleteRequest(group) },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (groups.size > 3) {
                    Text(
                        text = "还有 ${groups.size - 3} 个计划，点「查看全部」浏览",
                        color = DarkBar.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (groups.singleOrNull()?.planName == null) {
                    Text(
                        text = "在 AI 对话里生成计划并应用后，这里会按计划分组显示",
                        color = DarkBar.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Text(
                    text = "左滑计划可删除整组",
                    color = DarkBar.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** 一个计划组：组名 + 任务数 + 最近截止；左滑露出删除 */
@Composable
private fun PlanGroupRow(
    group: PlanGroup,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (group.planName == null) {
        // 手动创建、未归组的事件：只做展示，不提供整组删除，也不跳转
        PlanGroupContent(group = group, onClick = {}, showArrow = false, modifier = modifier)
        return
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDeleteRequest()
            }
            // 弹确认框，列表项本身保持原位
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE0524A))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    ) {
        PlanGroupContent(group = group, onClick = onClick)
    }
}

@Composable
private fun PlanGroupContent(
    group: PlanGroup,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // 必须使用不透明底色，否则左滑的红色删除层会透过卡片显示出来
            .background(Color(0xFFFBE3CB))
            .then(if (showArrow) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.planName ?: "未分组",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = DarkBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${group.count} 个任务 · 最近截止 ${deadlineShort(group.nearestDeadline)}",
                style = MaterialTheme.typography.bodySmall,
                color = DarkBar.copy(alpha = 0.7f)
            )
        }
        if (showArrow) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DarkBar.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun WeekOverviewCard(
    hoursText: String,
    completionText: String,
    streakDays: Int
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本周概览", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewItem(value = hoursText, label = "学习时长", modifier = Modifier.weight(1f))
                OverviewItem(value = completionText, label = "完成率", modifier = Modifier.weight(1f))
                OverviewItem(value = "$streakDays 天", label = "连续学习", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OverviewItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = DarkBar
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DailyDurationCard(dailyMinutes: List<Int>) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val max = (dailyMinutes.maxOrNull() ?: 0).coerceAtLeast(1)
    val peakIndex = dailyMinutes.indexOf(dailyMinutes.maxOrNull())

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("每日时长", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                dailyMinutes.forEachIndexed { index, minutes ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 轨道式柱子：浅色满高底槽 + 底部彩色填充
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(88.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF0EDE8)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (minutes > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((88f * minutes / max).coerceAtLeast(8f).dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (index == peakIndex) DarkBar else ApricotOrange
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = labels[index],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

internal fun daysLeft(event: CalendarEvent): Long =
    ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(event.date))

internal fun daysLeftText(daysLeft: Long): String =
    if (daysLeft <= 0L) "今天截止" else "剩 $daysLeft 天"

internal fun deadlineShort(date: String): String {
    val parsed = LocalDate.parse(date)
    return "${parsed.monthValue}/${parsed.dayOfMonth}"
}
