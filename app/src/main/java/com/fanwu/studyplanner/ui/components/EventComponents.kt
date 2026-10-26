package com.fanwu.studyplanner.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.calendar.eventTypeColor
import com.fanwu.studyplanner.ui.calendar.label
import com.fanwu.studyplanner.ui.theme.*

@Composable
fun EventRow(event: CalendarEvent, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (event.isDone) BrutalDisabled else BrutalWhite)
            .border(2.dp, BrutalBlack).clickable(role = Role.Button, onClick = onOpen)
            .padding(start = if (event.type == EventType.EXAM) 0.dp else 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (event.type == EventType.EXAM) {
            BrutalCheckbox(event.isDone, if (event.isDone) "恢复任务" else "完成任务") { onToggle() }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BrutalTag(event.type.label(), if (event.isDone) BrutalDisabled else eventTypeColor(event.type))
                if (event.isDone) Text(if (event.type == EventType.CLASS) "已下课" else "已完成", style = MaterialTheme.typography.bodySmall)
            }
            Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (event.isDone && event.type != EventType.CLASS) TextDecoration.LineThrough else null)
            Text("${event.startTime} — ${event.endTime} · ${event.durationMinutes()} 分钟",
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            // 保留用户已有改动：课程教室直接展示在卡片内，长教室名允许两行。
            if (event.type == EventType.CLASS && !event.note.isNullOrBlank()) {
                Text(event.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = BrutalMutedText)
            }
            if (event.type != EventType.CLASS) {
                event.planName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BrutalMutedText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
fun EventDetailDialog(
    event: CalendarEvent, onDismiss: () -> Unit, onEdit: () -> Unit,
    onDelete: () -> Unit, onToggle: () -> Unit
) {
    BrutalDialog("日程详情", onDismiss, actions = {
        if (event.type != EventType.CLASS) BrutalButton(if (event.isDone) "恢复为待办" else "标记为完成", onToggle, color = BrutalLime)
        BrutalButton("编辑日程", onEdit, color = BrutalWhite)
        TextButton(onDelete, Modifier.fillMaxWidth()) { Text("删除日程", color = BrutalDanger) }
    }) {
        BrutalTag(event.type.label(), eventTypeColor(event.type))
        Text(event.title, style = MaterialTheme.typography.headlineSmall)
        Text("${event.date}\n${event.startTime} — ${event.endTime}", style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        if (event.type != EventType.CLASS) {
            event.planName?.let { Text("所属计划：$it") }
        }
        event.note?.let { Text(if (event.type == EventType.CLASS) "教室 / 备注：$it" else "备注：$it") }
    }
}
