package com.fanwu.studyplanner.ui.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.formatFocusDuration
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun StatsScreen(onOpenTasks: (String?) -> Unit = {}, onOpenFeature: (String) -> Unit = {},
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(LocalContext.current))) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<PlanGroup?>(null) }
    BrutalPage("学习统计", "看见安排，也看见每一次完成") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalChoice("本周", period == "week", { viewModel.selectPeriod("week") }, Modifier.weight(1f))
            BrutalChoice("本月", period == "month", { viewModel.selectPeriod("month") }, Modifier.weight(1f))
        }
        BrutalPanel(color = BrutalLime, shadow = true) {
            Text(state.periodLabel, style = MaterialTheme.typography.labelMedium)
            Text(state.weekHoursText, style = MaterialTheme.typography.displayLarge)
            Text("计划安排时长", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(color = BrutalBlack, thickness = 2.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BrutalMetric(state.completionText, "任务完成率", Modifier.weight(1f))
                BrutalMetric(state.completedHoursText, "已完成安排", Modifier.weight(1f))
            }
            Text("${state.completedCount} / ${state.plannedCount} 项已完成 · 连续 ${state.streakDays} 天有完成记录",
                style = MaterialTheme.typography.bodySmall)
        }
        BrutalPanel(color = BrutalCyan, shadow = true) {
            Text("实际专注时长", style = MaterialTheme.typography.titleLarge)
            Text(formatFocusDuration(state.actualFocusMs), style = MaterialTheme.typography.headlineMedium)
            Text("只统计计时期间的实际投入，排除暂停和休息；提前结束的投入也会保留。运行中的记录约每 15 秒更新。", style = MaterialTheme.typography.bodySmall)
            state.dailyFocusMs.forEachIndexed { index, milliseconds ->
                if (milliseconds > 0) Text("${if (period == "week") "周" else ""}${state.dayLabels.getOrElse(index) { "" }}${if (period == "month") "日" else ""} · ${formatFocusDuration(milliseconds)}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton({ onOpenFeature("focus") }) { Text("进入专注空间 →", color = BrutalBlack) }
        }
        BrutalSection("每日计划时长")
        BrutalPanel {
            if (state.dailyMinutes.all { it == 0 }) {
                Text("这一时段还没有学习安排", style = MaterialTheme.typography.titleMedium)
                Text("在日历中添加任务后，这里会显示每天的计划时长。", style = MaterialTheme.typography.bodySmall)
            } else {
                DurationChart(state.dailyMinutes, state.dayLabels)
            }
            Text("单位：分钟。按日历安排计算，不代表实际专注时间；课程不计入。", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
        }
        BrutalSection("待完成的计划") {
            TextButton({ onOpenTasks(null) }) { Text("全部 ${state.pendingCount} 项", color = BrutalBlack) }
        }
        if (state.groups.isEmpty()) BrutalEmpty("目前没有待办", "新的学习计划会在这里按计划名称归组。")
        state.groups.forEach { group ->
            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
                if (it == SwipeToDismissBoxValue.EndToStart && group.planName != null) deleting = group
                false
            })
            SwipeToDismissBox(dismiss, enableDismissFromStartToEnd = false, enableDismissFromEndToStart = group.planName != null,
                backgroundContent = {
                    Box(Modifier.fillMaxSize().background(BrutalDanger).padding(16.dp), contentAlignment = Alignment.CenterEnd) { Text("删除整组", color = BrutalWhite) }
                }) {
                BrutalPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(group.planName ?: "手动创建的任务", style = MaterialTheme.typography.titleMedium)
                            Text("${group.count} 项待办 · 最近日期 ${deadlineShort(group.nearestDeadline)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (group.planName != null) BrutalIconButton(Icons.Outlined.DeleteOutline, "删除计划", { deleting = group })
                    }
                    TextButton({ onOpenTasks(group.planName) }) {
                        Text(if (group.planName == null) "查看全部任务 →" else "查看计划任务 →", color = BrutalBlack)
                    }
                }
            }
        }
        BrutalPanel {
            BrutalLinkRow("学习目标", "设置每周投入，追踪目标进度", { onOpenFeature("goals") })
            HorizontalDivider(color = BrutalBlack)
            BrutalLinkRow("每周学习复盘", "从完成情况中找到下一步", { onOpenFeature("insights") })
        }
    }
    deleting?.let { group ->
        BrutalConfirm("删除整个计划？", "将删除「${group.planName}」下的全部日程，包括已完成记录；目前有 ${group.count} 项待办。", "确认删除",
            { group.planName?.let(viewModel::deletePlan); deleting = null }, { deleting = null }, true)
    }
}

@Composable
private fun DurationChart(minutes: List<Int>, labels: List<String>) {
    val peak = (minutes.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(180.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        minutes.forEachIndexed { index, value ->
            Column(Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value.toString(), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(24.dp).height((110f * value / peak).coerceAtLeast(if (value > 0) 4f else 0f).dp)
                    .background(if (value == peak) BrutalBlack else BrutalCyan).then(if (value > 0) Modifier.border(2.dp, BrutalBlack) else Modifier))
                HorizontalDivider(color = BrutalBlack, thickness = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text(labels.getOrElse(index) { "" }, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

internal fun daysLeft(event: CalendarEvent): Long = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(event.date))
internal fun daysLeftText(daysLeft: Long): String = when {
    daysLeft < 0 -> "已逾期 ${-daysLeft} 天"
    daysLeft == 0L -> "今天截止"
    else -> "剩 $daysLeft 天"
}
internal fun deadlineShort(date: String): String = runCatching { LocalDate.parse(date).let { "${it.monthValue}/${it.dayOfMonth}" } }.getOrDefault(date)
