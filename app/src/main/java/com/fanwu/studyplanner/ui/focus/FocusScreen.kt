package com.fanwu.studyplanner.ui.focus

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.focus.*
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FocusScreen(onBack: () -> Unit, viewModel: FocusViewModel = viewModel(factory = FocusViewModel.factory(LocalContext.current))) {
    val context = LocalContext.current
    val active by viewModel.active.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var focusText by rememberSaveable { mutableStateOf("25") }
    var restText by rememberSaveable { mutableStateOf("5") }
    var taskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var picker by remember { mutableStateOf(false) }
    var finishConfirm by remember { mutableStateOf(false) }
    var completeTask by rememberSaveable(active?.id) { mutableStateOf(false) }
    var record by remember { mutableStateOf<FocusSession?>(null) }
    var notifications by remember { mutableStateOf(FocusNotifications.allowed(context)) }
    var exact by remember { mutableStateOf(FocusAlarm.exactAllowed(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifications = FocusNotifications.allowed(context)
                exact = FocusAlarm.exactAllowed(context)
                viewModel.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = FocusNotifications.allowed(context)
        viewModel.start(focusText.toIntOrNull() ?: 0, restText.toIntOrNull() ?: -1, taskId)
    }
    val focusMinutes = focusText.toIntOrNull()
    val breakMinutes = restText.toIntOrNull()
    val valid = focusMinutes != null && focusMinutes in 1..180 && breakMinutes != null && breakMinutes in 0..60

    BrutalPage("专注空间", "让每一次投入，都留下真实记录", onBack, modifier = Modifier.navigationBarsPadding()) {
        if (!loaded) {
            Text("正在读取专注状态…", style = MaterialTheme.typography.bodyLarge)
            return@BrutalPage
        }
        if (!notifications || !exact) {
            BrutalPanel {
                if (!notifications) {
                    Text("通知未开启：仍可计时，但通知栏控制和结束提醒可能不可见。", style = MaterialTheme.typography.bodySmall)
                    TextButton({
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text("查看通知设置", color = BrutalBlack) }
                }
                if (!exact) {
                    Text("未开启准时提醒：息屏省电时结束提醒可能延迟，专注时长仍按真实经过时间结算。", style = MaterialTheme.typography.bodySmall)
                    TextButton({
                        if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }) { Text("开启准时提醒", color = BrutalBlack) }
                }
            }
        }
        error?.let { Text(it, color = BrutalDanger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val session = active
        if (session == null) {
            BrutalPanel(color = BrutalCyan, shadow = true, padding = 24.dp) {
                Text("准备开始你的下一轮专注", style = MaterialTheme.typography.titleMedium)
                TimerText(formatFocusTime((focusMinutes ?: 25).coerceIn(1, 180) * 60_000L))
                Text("选择时长和任务，然后开始。暂停与休息不会计入实际专注。", style = MaterialTheme.typography.bodySmall)
            }
            BrutalSection("专注时长")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 45, 60).forEach { minutes ->
                    BrutalChoice("${minutes} 分钟", focusMinutes == minutes, { focusText = minutes.toString() }, Modifier.weight(1f))
                }
            }
            BrutalField(focusText, { focusText = it.filter(Char::isDigit).take(3) }, "自定义专注（1—180 分钟）", keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            BrutalSection("休息时长")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10).forEach { minutes ->
                    BrutalChoice(if (minutes == 0) "不休息" else "${minutes} 分钟", breakMinutes == minutes, { restText = minutes.toString() }, Modifier.weight(1f))
                }
            }
            BrutalField(restText, { restText = it.filter(Char::isDigit).take(2) }, "自定义休息（0—60 分钟）", keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            BrutalPanel {
                val task = tasks.firstOrNull { it.id == taskId }
                BrutalLinkRow("关联学习任务", task?.title ?: if (taskId == null) "不关联任务，自由专注" else "原任务已完成或删除，请重新选择", { picker = true })
            }
            if (!valid) Text("请输入有效时长：专注 1—180 分钟，休息 0—60 分钟。", color = BrutalDanger, style = MaterialTheme.typography.bodySmall)
            BrutalButton(if (busy) "正在启动…" else "开始专注", {
                if (Build.VERSION.SDK_INT >= 33 && !notifications && !permissionRequested) {
                    permissionRequested = true
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else viewModel.start(focusMinutes ?: 0, breakMinutes ?: -1, taskId)
            }, enabled = valid && !busy, icon = Icons.Outlined.PlayArrow)
        } else {
            val phase = session.phase
            BrutalPanel(color = if (phase.isBreak) BrutalLime else BrutalCyan, shadow = true, padding = 24.dp) {
                Text(phaseLabel(phase), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TimerText(formatFocusTime(FocusTimer.remainingMs(session, now)))
                Text(session.taskTitle ?: "自由专注", style = MaterialTheme.typography.titleMedium)
                Text("本轮实际专注：${formatFocusDuration(FocusTimer.focusedMs(session, now))}", style = MaterialTheme.typography.bodySmall)
                session.recoveryMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            when {
                phase.isRunning -> BrutalButton("暂停", { viewModel.act(FocusAction.PAUSE) }, enabled = !busy, color = BrutalYellow)
                phase.isPaused -> BrutalButton("继续${if (phase.isBreak) "休息" else "专注"}", { viewModel.act(FocusAction.RESUME) }, enabled = !busy)
                phase == FocusPhase.FOCUS_READY -> {
                    Text("本轮专注已完成，实际时长已经保存。", style = MaterialTheme.typography.titleMedium)
                    if (session.taskId != null && !session.taskCompletionApplied) {
                        CompletionChoice(completeTask) { completeTask = it }
                    }
                    if (session.breakDurationMs > 0) BrutalButton("开始 ${session.breakDurationMs / 60_000} 分钟休息",
                        { viewModel.act(FocusAction.START_BREAK, completeTask) }, enabled = !busy, color = BrutalLime)
                }
            }
            BrutalButton(if (phase == FocusPhase.FOCUS_READY) "结束本轮" else "提前结束并保存",
                { finishConfirm = true }, enabled = !busy, color = BrutalWhite)
            Text("返回其他页面后计时继续，可从通知栏暂停或恢复。设备重启后保留最近检查点并暂停。", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
        }
        BrutalSection("最近专注记录")
        if (history.isEmpty()) Text("还没有结束的专注记录，完成第一轮后会显示在这里。", style = MaterialTheme.typography.bodySmall)
        history.forEach { item ->
            BrutalPanel {
                BrutalLinkRow(
                    item.taskTitle ?: "自由专注",
                    "${formatFocusDuration(item.focusedMs)} · ${if (item.outcome == "COMPLETED") "已完成" else "提前结束"}\n${dateText(item.startedAt, item.zoneId)}",
                    { record = item }
                )
            }
        }
    }
    if (picker) BrutalDialog("关联学习任务", { picker = false }) {
        BrutalChoice("不关联任务", taskId == null, { taskId = null; picker = false }, Modifier.fillMaxWidth())
        if (tasks.isEmpty()) Text("暂无待完成的学习任务，仍可进行自由专注。")
        tasks.forEach { task ->
            BrutalChoice("${task.title}\n${task.date}", taskId == task.id, { taskId = task.id; picker = false }, Modifier.fillMaxWidth())
        }
    }
    if (finishConfirm && active != null) {
        val session = active!!
        BrutalDialog("结束本轮专注？", { finishConfirm = false }, actions = {
            BrutalButton("结束并保存", { finishConfirm = false; viewModel.act(FocusAction.FINISH, completeTask) }, enabled = !busy)
        }) {
            Text("本轮已专注 ${formatFocusDuration(FocusTimer.focusedMs(session, now))}，暂停和休息不会计入。提前结束也会保存实际投入。")
            if (session.taskId != null && !session.taskCompletionApplied) CompletionChoice(completeTask) { completeTask = it }
        }
    }
    record?.let { item ->
        BrutalDialog("专注记录", { record = null }) {
            Text(item.taskTitle ?: "自由专注", style = MaterialTheme.typography.titleLarge)
            Text("实际专注：${formatFocusDuration(item.focusedMs)}")
            Text("目标时长：${item.focusDurationMs / 60_000} 分钟")
            Text("开始：${dateText(item.startedAt, item.zoneId)}")
            item.endedAt?.let { Text("专注结束：${dateText(it, item.zoneId)}") }
            Text(if (item.outcome == "COMPLETED") "状态：已完成专注目标" else "状态：提前结束")
            if (item.taskCompletionApplied) Text("已同时完成关联任务")
            Text("暂停记录：${item.pauses.size} 次（含休息暂停）")
            item.pauses.forEach { pause ->
                Text("${if (pause.duringBreak) "休息" else "专注"}暂停：${dateText(pause.startedAt, item.zoneId)} — ${pause.endedAt?.let { dateText(it, item.zoneId) } ?: "未恢复"}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ColumnScope.TimerText(value: String) {
    Text(value, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp),
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
        fontSize = if (value.length > 5) 48.sp else 64.sp, lineHeight = 76.sp, maxLines = 1)
}

@Composable
private fun CompletionChoice(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrutalCheckbox(checked, "同时完成关联任务", onChange)
        Text("同时将关联任务标记为完成", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun phaseLabel(phase: FocusPhase) = when (phase) {
    FocusPhase.FOCUS_RUNNING -> "正在专注"
    FocusPhase.FOCUS_PAUSED -> "专注已暂停"
    FocusPhase.FOCUS_READY -> "本轮专注完成"
    FocusPhase.BREAK_RUNNING -> "正在休息"
    FocusPhase.BREAK_PAUSED -> "休息已暂停"
    FocusPhase.FINISHED -> "本轮已结束"
    FocusPhase.ABANDONED -> "本轮提前结束"
}

private fun dateText(at: Long, zone: String): String =
    DateTimeFormatter.ofPattern("M月d日 HH:mm:ss").withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(at))
