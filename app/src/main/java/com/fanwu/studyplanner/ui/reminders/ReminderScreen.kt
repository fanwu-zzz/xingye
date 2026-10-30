package com.fanwu.studyplanner.ui.reminders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.ReminderSettings
import com.fanwu.studyplanner.reminders.*
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReminderScreen(onBack: () -> Unit, viewModel: ReminderViewModel = viewModel(factory = ReminderViewModel.factory)) {
    val context = LocalContext.current
    val config by viewModel.settings.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val schedulingError by viewModel.schedulingError.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var notifications by remember { mutableStateOf(EventNotifications(context).available()) }
    var exact by remember { mutableStateOf(ReminderAlarms.exactAllowed(context)) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifications = EventNotifications(context).available()
                exact = ReminderAlarms.exactAllowed(context)
                viewModel.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = EventNotifications(context).available()
        viewModel.refresh()
    }
    fun openSettings(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure { settingsError = "无法打开系统设置，请在系统应用管理中调整权限。" }
    }
    BrutalPage("提醒与通知", "在日程开始或任务截止前提醒", onBack, modifier = Modifier.navigationBarsPadding().imePadding()) {
        val saved = config
        if (saved == null) { Text("正在读取提醒设置…"); return@BrutalPage }
        var tasks by rememberSaveable(saved.tasksEnabled) { mutableStateOf(saved.tasksEnabled) }
        var courses by rememberSaveable(saved.coursesEnabled) { mutableStateOf(saved.coursesEnabled) }
        var lead by rememberSaveable(saved.leadMinutes) { mutableStateOf(saved.leadMinutes.toString()) }
        var quiet by rememberSaveable(saved.quietEnabled) { mutableStateOf(saved.quietEnabled) }
        var quietStart by rememberSaveable(saved.quietStart) { mutableStateOf(saved.quietStart) }
        var quietEnd by rememberSaveable(saved.quietEnd) { mutableStateOf(saved.quietEnd) }
        val draft = ReminderSettings(tasks, courses, lead.toIntOrNull() ?: -1, quiet, quietStart, quietEnd)
        val validation = runCatching { draft.validate() }.exceptionOrNull()?.let {
            if (it is java.time.format.DateTimeParseException) "静默时间请使用 HH:mm，例如 23:00、07:00。" else it.message
        }
        BrutalPanel(color = BrutalCyan, shadow = true) {
            Text(if (saved.tasksEnabled || saved.coursesEnabled) "日程提醒已启用" else "日程提醒未启用", style = MaterialTheme.typography.titleLarge)
            Text(if (notifications) "系统通知可用" else "系统通知未开启：设置可保存，提醒目前无法显示。")
            Text(if (exact) "准时提醒可用" else "当前使用普通提醒，省电状态下可能延后；过期提醒将跳过。", style = MaterialTheme.typography.bodySmall)
            if (!notifications) TextButton({
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("打开通知设置", color = BrutalBlack) }
            if (!exact) TextButton({
                if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("开启准时提醒", color = BrutalBlack) }
        }
        BrutalSection("提醒范围")
        BrutalPanel {
            ReminderToggle("任务与考试提醒", tasks) { tasks = it }
            HorizontalDivider(color = BrutalBlack)
            ReminderToggle("课程开始提醒", courses) { courses = it }
            Text("含学习任务、作业等截止任务与考试。截止任务按截止时刻提前提醒，其他日程按开始时刻。已完成日程不提醒。", style = MaterialTheme.typography.bodySmall)
        }
        BrutalSection("提前多久")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 30).forEach { minutes ->
                BrutalChoice("$minutes 分钟", lead.toIntOrNull() == minutes, { lead = minutes.toString() }, Modifier.weight(1f))
            }
        }
        BrutalField(lead, { lead = it.filter(Char::isDigit).take(4) }, "提前分钟（0—1440，0 为到点时）", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        BrutalSection("静默时段")
        ReminderToggle("启用静默时段", quiet) { quiet = it }
        BrutalField(quietStart, { quietStart = it.take(5) }, "静默开始（HH:mm）")
        BrutalField(quietEnd, { quietEnd = it.take(5) }, "静默结束（HH:mm）")
        Text("支持跨午夜；提醒时间或实际送达时间处于静默时段时跳过，不延后补发。此设置只影响日程提醒，专注计时提醒独立管理。", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
        validation?.let { Text(it, color = BrutalDanger) }
        listOfNotNull(error, schedulingError, settingsError).distinct().forEach { Text(it, color = BrutalDanger) }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        BrutalButton(if (busy) "正在保存…" else "保存提醒设置", {
            viewModel.save(draft)
            val preferences = context.getSharedPreferences("reminder_permissions", android.content.Context.MODE_PRIVATE)
            if ((tasks || courses) && Build.VERSION.SDK_INT >= 33 && !notifications && !preferences.getBoolean("requested", false)) {
                preferences.edit().putBoolean("requested", true).apply()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }, enabled = !busy && validation == null)
        BrutalSection("待提醒安排")
        Text("${pending.size} 项已排入调度；通知被关闭时无法显示。新开启或修改设置不会补发已错过的提醒。", style = MaterialTheme.typography.bodySmall)
        if (pending.isNotEmpty()) {
            val next = Instant.ofEpochMilli(pending.first().fireAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            Text("下一次提醒：$next", style = MaterialTheme.typography.titleMedium)
        }
        Text("系统强行停止应用或厂商后台限制可能阻止提醒；重新打开应用会恢复调度。", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
    }
}

@Composable
private fun ReminderToggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        BrutalCheckbox(value, title, onChange)
    }
}
