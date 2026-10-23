package com.fanwu.studyplanner.ui.stats

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    onOpenTasks: () -> Unit = {},
    viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.factory(LocalContext.current)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                upcoming = state.upcoming,
                onOpenTasks = onOpenTasks
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
    upcoming: List<CalendarEvent>,
    onOpenTasks: () -> Unit
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
                        .clickable(onClick = onOpenTasks)
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

            if (upcoming.isEmpty()) {
                Text(
                    text = "太棒了，没有待完成的任务",
                    color = DarkBar.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                upcoming.forEach { event ->
                    val daysLeft = daysLeft(event)
                    Text(
                        text = "${event.title} · 截止 ${deadlineShort(event.date)} · ${daysLeftText(daysLeft)}",
                        color = DarkBar,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
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
