package com.fanwu.studyplanner.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.EventType
import com.fanwu.studyplanner.domain.StudyPlan
import com.fanwu.studyplanner.ui.theme.ApricotOrange
import com.fanwu.studyplanner.ui.theme.OrangePrimary

/**
 * 计划面板：展示 AI 生成的一组计划，组名可行内编辑，满意后点击"应用"写入日历。
 * 整体高度限制在屏幕内，列表区域单独滚动，底部按钮始终可见。
 */
@Composable
fun PlanPanelDialog(
    plan: StudyPlan,
    planName: String,
    applied: Boolean,
    onRename: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                PlanNameRow(planName = planName, onRename = onRename)

                Text(
                    text = "共 ${plan.events.size} 个任务" + if (applied) " · 已应用到日历" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 仅列表区域滚动，保证底部按钮不被挤出屏幕
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (plan.summary.isNotBlank()) {
                        Text(
                            text = plan.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    plan.events.forEach { event ->
                        PlanEventRow(event)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (applied) "关闭" else "取消")
                    }
                    Button(
                        onClick = onApply,
                        enabled = !applied,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrangePrimary,
                            contentColor = Color.White
                        )
                    ) { Text(if (applied) "已应用" else "应用到日历") }
                }
            }
        }
    }
}

@Composable
private fun PlanEventRow(event: CalendarEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(typeColor(event.type), CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${event.date} ${event.startTime}-${event.endTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 组名行：默认展示组名 + 编辑图标；点击编辑变为输入框 + 保存/取消 */
@Composable
private fun PlanNameRow(planName: String, onRename: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(planName) { mutableStateOf(planName) }

    if (editing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (draft.trim().isNotEmpty()) {
                        onRename(draft)
                        editing = false
                    }
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = "保存组名", tint = OrangePrimary)
            }
            IconButton(onClick = { editing = false }) {
                Icon(Icons.Default.Close, contentDescription = "取消编辑")
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = planName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                draft = planName
                editing = true
            }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "修改组名",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun typeColor(type: EventType): Color = when (type) {
    EventType.EXAM -> OrangePrimary
    EventType.CLASS -> ApricotOrange
    EventType.STUDY_TASK -> Color(0xFFA8C3A0)
}
