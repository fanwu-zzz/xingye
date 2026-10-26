package com.fanwu.studyplanner.ui.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.repository.CalendarRepository
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.calendar.EventEditorDialog
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ReminderEventScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).calendarEventDao() }
    val repository = remember { CalendarRepository(dao, AppDatabase.getInstance(context)) }
    var event by remember(id) { mutableStateOf<CalendarEvent?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { dao.observeById(id).collect { event = it?.toDomain(); loaded = true } }
    fun change(block: suspend () -> Unit) {
        scope.launch {
            try { block(); error = null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = "操作失败，请重试。" }
        }
    }
    BrutalPage("日程详情", "来自日程提醒", onBack, modifier = Modifier.navigationBarsPadding()) {
        val current = event
        if (!loaded) Text("正在读取日程…")
        else if (current == null) BrutalEmpty("日程已不存在", "它可能已经被删除或在重新导入时替换。", "返回日历", onBack)
        else {
            BrutalPanel {
                BrutalTag(when (current.type) { EventType.CLASS -> "课程"; EventType.EXAM -> "考试"; else -> "学习任务" }, when (current.type) { EventType.CLASS -> BrutalCyan; EventType.EXAM -> BrutalOrange; else -> BrutalLime })
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Text("${current.date}\n${current.startTime} — ${current.endTime}")
                if (current.isDone) Text("已完成", style = MaterialTheme.typography.titleMedium)
                current.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                if (current.type != EventType.CLASS) current.planName?.let { Text("所属计划：$it") }
            }
            if (current.type != EventType.CLASS) BrutalButton(if (current.isDone) "恢复待办" else "标记完成", { change { repository.setDone(current.id, !current.isDone) } })
            BrutalButton("编辑日程", { editing = true }, color = BrutalWhite)
            TextButton({ deleting = true }) { Text("删除日程", color = BrutalDanger) }
        }
        error?.let { Text(it, color = BrutalDanger) }
    }
    if (editing && event != null) EventEditorDialog(event, LocalDate.parse(event!!.date), { editing = false }) { updated ->
        change { repository.update(updated); editing = false }
    }
    if (deleting && event != null) BrutalConfirm("删除日程？", "「${event!!.title}」及其待发提醒将取消。", "确认删除", {
        val current = event!!
        change { repository.delete(current); deleting = false }
    }, { deleting = false }, danger = true)
}
