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
    var deadline by remember(initialEvent) { mutableStateOf(initialEvent?.takeIf { it.type == EventType.DEADLINE }?.endTime ?: "23:59") }
    var note by remember(initialEvent) { mutableStateOf(initialEvent?.note.orEmpty()) }
    var subject by remember(initialEvent) { mutableStateOf(initialEvent?.subject.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    BrutalDialog(if (initialEvent == null) "添加日程" else "编辑日程", onDismiss, actions = {
        error?.let { Text(it, color = BrutalDanger, style = MaterialTheme.typography.bodySmall) }
        BrutalButton(if (type == EventType.DEADLINE) "保存截止任务" else "保存到日历", {
            val startValue = if (type == EventType.DEADLINE) deadline else start
            val endValue = if (type == EventType.DEADLINE) deadline else end
            error = validateInput(title, date, startValue, endValue, type)
            if (error == null) onSave(CalendarEvent(
                id = initialEvent?.id ?: 0, title = title.trim(), date = date.trim(),
                startTime = startValue.trim(), endTime = endValue.trim(), type = type,
                note = note.trim().takeIf { it.isNotEmpty() }, isDone = initialEvent?.isDone ?: false,
                planName = initialEvent?.planName, completedAt = initialEvent?.completedAt,
                subject = subject.trim().takeIf { it.isNotEmpty() },
                recurringCourseId = initialEvent?.recurringCourseId,
                originalOccurrenceDate = initialEvent?.originalOccurrenceDate
            ))
        })
        TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("取消", color = BrutalBlack) }
    }) {
        Text("日程类型", style = MaterialTheme.typography.titleSmall)
        listOf(EventType.STUDY_TASK, EventType.DEADLINE, EventType.CLASS, EventType.EXAM).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    BrutalChoice(item.label(), type == item, { type = item; error = null }, Modifier.weight(1f))
                }
            }
        }
        Text(type.description(), style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
        BrutalField(title, { title = it }, if (type == EventType.DEADLINE) "任务名称" else "日程名称")
        BrutalField(date, { date = it }, if (type == EventType.DEADLINE) "截止日期（YYYY-MM-DD）" else "日期（YYYY-MM-DD）")
        if (type == EventType.DEADLINE) {
            BrutalField(deadline, { deadline = it }, "截止时间（HH:mm）")
            Text("只知道日期时可保留 23:59，表示当天结束前完成。截止任务不计入计划学习时长。", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalField(start, { start = it }, "开始 HH:mm", Modifier.weight(1f))
                BrutalField(end, { end = it }, "结束 HH:mm", Modifier.weight(1f))
            }
        }
        BrutalField(note, { note = it }, if (type == EventType.CLASS) "教室 / 备注（可选）" else "备注（可选）", singleLine = false)
        BrutalField(subject, { subject = it.take(80) }, "科目标签（可选）")
        if (type != EventType.CLASS) {
            initialEvent?.planName?.let { Text("所属计划：$it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

internal fun validateInput(title: String, date: String, startTime: String, endTime: String, type: EventType = EventType.STUDY_TASK): String? {
    if (title.isBlank()) return "请输入日程名称"
    if (runCatching { LocalDate.parse(date.trim()) }.isFailure) return "日期格式应为 YYYY-MM-DD"
    if (type == EventType.DEADLINE) {
        if (!Regex("\\d{2}:\\d{2}").matches(endTime.trim()) || runCatching { LocalTime.parse(endTime.trim()) }.isFailure) return "截止时间格式应为 HH:mm，例如 23:59"
        if (startTime.trim() != endTime.trim()) return "截止任务只需一个截止时间"
        return null
    }
    if (!Regex("\\d{2}:\\d{2}").matches(startTime.trim())) return "开始时间格式应为 HH:mm"
    if (!Regex("\\d{2}:\\d{2}").matches(endTime.trim())) return "结束时间格式应为 HH:mm"
    val start = runCatching { LocalTime.parse(startTime.trim()) }.getOrNull() ?: return "开始时间格式应为 HH:mm"
    val end = runCatching { LocalTime.parse(endTime.trim()) }.getOrNull() ?: return "结束时间格式应为 HH:mm"
    if (!end.isAfter(start)) return "结束时间必须晚于开始时间"
    return null
}

fun EventType.label(): String = when (this) {
    EventType.EXAM -> "考试"
    EventType.CLASS -> "课程"
    EventType.STUDY_TASK -> "学习任务"
    EventType.DEADLINE -> "截止任务"
}

private fun EventType.description(): String = when (this) {
    EventType.STUDY_TASK -> "安排一段学习时间，例如 19:00—20:00 复习、练习或写作业。"
    EventType.DEADLINE -> "在指定时间之前完成，例如交作业、提交报告或报名截止。只需填写截止日期和时间。"
    EventType.CLASS -> "有固定上课时间的课程，可填写教室。"
    EventType.EXAM -> "有明确开始和结束时间的考试。"
}
