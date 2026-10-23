package com.fanwu.studyplanner.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.domain.StudyPlan

/**
 * 计划预览弹窗：用户确认后才写入日历
 */
@Composable
fun PlanPreviewDialog(
    plan: StudyPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认学习计划") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (plan.summary.isNotBlank()) {
                    Text(text = plan.summary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "将写入 ${plan.events.size} 个事件：",
                    style = MaterialTheme.typography.titleSmall
                )
                plan.events.forEach { event ->
                    Text(
                        text = "${event.date} ${event.startTime}-${event.endTime}  ${event.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("写入日历") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
