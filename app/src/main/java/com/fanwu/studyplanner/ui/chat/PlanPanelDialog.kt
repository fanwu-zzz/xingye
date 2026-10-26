package com.fanwu.studyplanner.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.calendar.eventTypeColor
import com.fanwu.studyplanner.ui.calendar.label
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*

@Composable
fun PlanPanelDialog(
    plan: StudyPlan, planName: String, applied: Boolean,
    onRename: (String) -> Unit, onApply: () -> Unit, onDismiss: () -> Unit
) {
    var draft by remember(planName) { mutableStateOf(planName) }
    BrutalDialog("计划预览", onDismiss, actions = {
        BrutalButton(if (applied) "已应用到日历" else "应用到日历", {
            if (draft.trim() != planName) onRename(draft)
            onApply()
        }, enabled = !applied && plan.events.isNotEmpty() && draft.isNotBlank())
        TextButton(onDismiss, Modifier.fillMaxWidth()) { Text(if (applied) "关闭" else "稍后决定", color = BrutalBlack) }
    }) {
        BrutalTag(if (applied) "已应用" else "等待确认", if (applied) BrutalLime else BrutalYellow)
        if (!applied) {
            BrutalField(draft, { draft = it }, "计划名称")
            if (draft != planName) {
                TextButton({ if (draft.isNotBlank()) onRename(draft) }, enabled = draft.isNotBlank()) { Text("保存计划名称", color = BrutalBlack) }
                Text("应用前请先保存修改后的名称。", style = MaterialTheme.typography.bodySmall)
            }
        } else Text(planName, style = MaterialTheme.typography.titleLarge)
        Text("共 ${plan.events.size} 项 · ${formatHours(plan.events.sumOf { it.durationMinutes() })}", style = MaterialTheme.typography.titleMedium)
        if (plan.events.isNotEmpty()) {
            val dates = plan.events.map { it.date }.sorted()
            Text("${dates.first()} — ${dates.last()}", style = MaterialTheme.typography.bodySmall)
        }
        if (plan.summary.isNotBlank()) Text(plan.summary, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(color = BrutalBlack, thickness = 2.dp)
        plan.events.sortedWith(compareBy<CalendarEvent> { it.date }.thenBy { it.startTime }).forEach { event ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BrutalTag(event.type.label(), eventTypeColor(event.type))
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text("${event.date} · ${event.startTime} — ${event.endTime}", style = MaterialTheme.typography.bodySmall)
                event.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BrutalMutedText) }
                HorizontalDivider(color = BrutalBlack)
            }
        }
        if (plan.events.isEmpty()) Text("这份计划没有可应用的事件，请返回对话补充信息。")
    }
}
