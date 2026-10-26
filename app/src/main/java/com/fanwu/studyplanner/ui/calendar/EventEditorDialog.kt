package com.fanwu.studyplanner.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.EventType
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun EventEditorDialog(
    initialEvent: CalendarEvent?, defaultDate: LocalDate,
    onDismiss: () -> Unit, defaultType: EventType = EventType.STUDY_TASK,
    onSave: (CalendarEvent) -> Unit
) {
    var title by remember(initialEvent) { mutableStateOf(initialEvent?.title.orEmpty()) }
    var date by remember(initialEvent) { mutableStateOf(initialEvent?.date ?: defaultDate.toString()) }
    var start by remember(initialEvent) { mutableStateOf(initialEvent?.startTime ?: "09:00") }
    var end by remember(initialEvent) { mutableStateOf(initialEvent?.endTime ?: "10:00") }
    var type by remember(initialEvent) { mutableStateOf(initialEvent?.type ?: defaultType) }
    var note by remember(initialEvent) { mutableStateOf(initialEvent?.note.orEmpty()) }
    var subject by remember(initialEvent) { mutableStateOf(initialEvent?.subject.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    BrutalDialog(if (initialEvent == null) "添加日程" else "编辑日程", onDismiss, actions = {
        BrutalButton("保存到日历", {
            error = validateInput(title, date, start, end)
            if (error == null) onSave(CalendarEvent(
                id = initialEvent?.id ?: 0, title = title.trim(), date = date.trim(),
                startTime = start.trim(), endTime = end.trim(), type = type,
                note = note.trim().takeIf { it.isNotEmpty() }, isDone = initialEvent?.isDone ?: false,
                planName = initialEvent?.planName, completedAt = initialEvent?.completedAt,
                subject = subject.trim().takeIf { it.isNotEmpty() },
                recurringCourseId = initialEvent?.recurringCourseId,
                originalOccurrenceDate = initialEvent?.originalOccurrenceDate
            ))
        })
        TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("取消", color = BrutalBlack) }
    }) {
        Text("给时间一个明确的用途。", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
        BrutalField(title, { title = it }, "日程名称")
        BrutalField(date, { date = it }, "日期（YYYY-MM-DD）")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalField(start, { start = it }, "开始 HH:mm", Modifier.weight(1f))
            BrutalField(end, { end = it }, "结束 HH:mm", Modifier.weight(1f))
        }
        Text("日程类型", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EventType.entries.forEach { item ->
                BrutalChoice(item.label(), type == item, { type = item }, Modifier.weight(1f))
            }
        }
        BrutalField(note, { note = it }, if (type == EventType.CLASS) "教室 / 备注（可选）" else "备注（可选）", singleLine = false)
        BrutalField(subject, { subject = it.take(80) }, "科目标签（可选）")
        if (type != EventType.CLASS) {
            initialEvent?.planName?.let { Text("所属计划：$it", style = MaterialTheme.typography.bodySmall) }
        }
        error?.let { Text(it, color = BrutalDanger, style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun validateInput(title: String, date: String, startTime: String, endTime: String): String? {
    if (title.isBlank()) return "请输入日程名称"
    if (runCatching { LocalDate.parse(date.trim()) }.isFailure) return "日期格式应为 YYYY-MM-DD"
    val start = runCatching { LocalTime.parse(startTime.trim(), TIME_FORMAT) }.getOrNull() ?: return "开始时间格式应为 HH:mm"
    val end = runCatching { LocalTime.parse(endTime.trim(), TIME_FORMAT) }.getOrNull() ?: return "结束时间格式应为 HH:mm"
    if (!end.isAfter(start)) return "结束时间必须晚于开始时间"
    return null
}

fun EventType.label(): String = when (this) {
    EventType.EXAM -> "考试"
    EventType.CLASS -> "课程"
    EventType.STUDY_TASK -> "学习任务"
}
