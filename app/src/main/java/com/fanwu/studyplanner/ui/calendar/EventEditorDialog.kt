package com.fanwu.studyplanner.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.EventType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun EventEditorDialog(
    initialEvent: CalendarEvent?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit
) {
    var title by remember(initialEvent) { mutableStateOf(initialEvent?.title ?: "") }
    var date by remember(initialEvent) { mutableStateOf(initialEvent?.date ?: defaultDate.toString()) }
    var startTime by remember(initialEvent) { mutableStateOf(initialEvent?.startTime ?: "09:00") }
    var endTime by remember(initialEvent) { mutableStateOf(initialEvent?.endTime ?: "10:00") }
    var type by remember(initialEvent) { mutableStateOf(initialEvent?.type ?: EventType.STUDY_TASK) }
    var note by remember(initialEvent) { mutableStateOf(initialEvent?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEvent == null) "添加事件" else "编辑事件") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("日期（YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("开始（HH:mm）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("结束（HH:mm）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(text = "类型", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventType.entries.forEach { item ->
                        FilterChip(
                            selected = type == item,
                            onClick = { type = item },
                            label = { Text(item.label()) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val message = validateInput(title, date, startTime, endTime)
                    if (message != null) {
                        error = message
                    } else {
                        onSave(
                            CalendarEvent(
                                id = initialEvent?.id ?: 0,
                                title = title.trim(),
                                date = date.trim(),
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                type = type,
                                note = note.trim().takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun validateInput(
    title: String,
    date: String,
    startTime: String,
    endTime: String
): String? {
    if (title.isBlank()) return "请输入标题"
    if (runCatching { LocalDate.parse(date.trim()) }.isFailure) return "日期格式应为 YYYY-MM-DD"
    val start = runCatching { LocalTime.parse(startTime.trim(), TIME_FORMAT) }.getOrNull()
        ?: return "开始时间格式应为 HH:mm"
    val end = runCatching { LocalTime.parse(endTime.trim(), TIME_FORMAT) }.getOrNull()
        ?: return "结束时间格式应为 HH:mm"
    if (!end.isAfter(start)) return "结束时间必须晚于开始时间"
    return null
}

/** 事件类型的中文名 */
fun EventType.label(): String = when (this) {
    EventType.EXAM -> "考试"
    EventType.CLASS -> "课程"
    EventType.STUDY_TASK -> "学习任务"
}
