package com.fanwu.studyplanner.ui.features

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun FeatureFeedback(vm: LearningViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val failed by vm.failure.collectAsStateWithLifecycle()
    if (busy) Text("正在处理，请稍候…")
    message?.let { Text(it, color = if (failed) BrutalDanger else BrutalBlack) }
}

@Composable
fun WeekSelector(week: LocalDate, onChange: (LocalDate) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrutalChoice("上一周", false, { onChange(week.minusWeeks(1)) }, Modifier.weight(1f))
        BrutalChoice("本周", week == mondayOf(LocalDate.now()), { onChange(mondayOf(LocalDate.now())) }, Modifier.weight(1f))
        BrutalChoice("下一周", false, { onChange(week.plusWeeks(1)) }, Modifier.weight(1f))
    }
    Text("$week — ${week.plusDays(6)}", style = MaterialTheme.typography.titleMedium)
}

@Composable
fun GoalsScreen(onBack: () -> Unit, vm: LearningViewModel = viewModel(factory = LearningViewModel.factory(LocalContext.current))) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val intervals by vm.intervals.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var weekText by rememberSaveable { mutableStateOf(mondayOf(LocalDate.now()).toString()) }
    val week = LocalDate.parse(weekText)
    val revisions = docs.filter { it.kind == "goal" }.map { it.decode<GoalRevision>() }
    val goals = goalsForWeek(revisions, week)
    var editing by remember { mutableStateOf<GoalRevision?>(null) }
    var create by remember { mutableStateOf(false) }
    val historical = week < mondayOf(LocalDate.now())
    BrutalPage("学习目标", "按实际专注记录追踪每周投入", onBack, modifier = Modifier.navigationBarsPadding()) {
        WeekSelector(week) { weekText = it.toString() }
        FeatureFeedback(vm)
        if (historical) Text("历史周只读；修改从本周或未来生效，旧周目标不会改变。")
        BrutalButton("新增周目标", { create = true }, enabled = !busy && !historical)
        if (goals.isEmpty()) BrutalEmpty("这一周还没有目标", "设置每周投入，完成专注后在这里查看进度。")
        goals.forEach { goal ->
            val sessionIds = sessions.filter { goal.planName == null || it.toFocusSession().planName == goal.planName }.map { it.id }.toSet()
            val actual = focusedInWeek(intervals.filter { it.sessionId in sessionIds }.map { it.toFocusSpan() }, week)
            val minutes = actual / 60000
            BrutalPanel(color = if (goal.state == "ACTIVE") BrutalWhite else BrutalDisabled) {
                Text(goal.name, style = MaterialTheme.typography.titleLarge)
                BrutalTag(when (goal.state) { "PAUSED" -> "已暂停"; "ARCHIVED" -> "已归档"; else -> "进行中" }, BrutalYellow)
                Text("${minutes} / ${goal.minutes} 分钟 · ${((actual.toDouble() / (goal.minutes * 60000L)) * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                Text("${goal.planName?.let { "关联计划：$it" } ?: "所有专注"} · 自 ${goal.effectiveWeek} 生效")
                goal.deadline?.let { Text("截止日期：$it") }
                if (goal.state == "ACTIVE" && !historical) TextButton({ editing = goal }) { Text("编辑 / 暂停 / 归档", color = BrutalBlack) }
                if (goal.state != "ACTIVE" && !historical) TextButton({ editing = goal }) { Text("调整或恢复目标", color = BrutalBlack) }
            }
        }
        Text("同一段专注可能贡献给多个目标，目标之间不可相加。关联计划只计入开始专注时记录的计划；旧记录缺少归属时不推测。", style = MaterialTheme.typography.bodySmall)
    }
    if (create || editing != null) GoalDialog(editing, week, events.mapNotNull { it.planName }.distinct().sorted(), { create = false; editing = null }) { revision ->
        vm.run { vm.repository.saveGoal(revision); vm.message.value = "目标已保存" }
        create = false; editing = null
    }
}

@Composable
private fun GoalDialog(initial: GoalRevision?, week: LocalDate, plans: List<String>, dismiss: () -> Unit, save: (GoalRevision) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var hours by remember { mutableStateOf(initial?.let { (it.minutes / 60.0).toString() } ?: "10") }
    var deadline by remember { mutableStateOf(initial?.deadline.orEmpty()) }
    var plan by remember { mutableStateOf(initial?.planName) }
    var state by remember { mutableStateOf(initial?.state ?: "ACTIVE") }
    var error by remember { mutableStateOf<String?>(null) }
    BrutalDialog("周学习目标", dismiss, actions = {
        BrutalButton("保存目标", {
            runCatching {
                val value = hours.toDoubleOrNull(); require(value != null && value.isFinite() && value > 0 && value <= 168) { "每周时长应大于 0 且不超过 168 小时" }
                GoalRevision(goalId = initial?.goalId ?: UUID.randomUUID().toString(), effectiveWeek = week.toString(), name = name.trim(),
                    minutes = (value * 60).roundToInt(), planName = plan, deadline = deadline.trim().takeIf { it.isNotBlank() }, state = state).also { it.validate() }
            }.onSuccess(save).onFailure { error = it.message }
        })
    }) {
        BrutalField(name, { name = it.take(100) }, "目标名称")
        BrutalField(hours, { hours = it }, "每周投入（小时）", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        BrutalField(deadline, { deadline = it }, "截止日期 YYYY-MM-DD（可选）")
        Text("生效周：$week")
        BrutalChoice("全部专注", plan == null, { plan = null }, Modifier.fillMaxWidth())
        (plans + listOfNotNull(initial?.planName)).distinct().forEach { p -> BrutalChoice(p, plan == p, { plan = p }, Modifier.fillMaxWidth()) }
        if (initial != null) listOf("ACTIVE" to "进行中", "PAUSED" to "暂停", "ARCHIVED" to "归档").forEach { (s, label) ->
            BrutalChoice(label, state == s, { state = s }, Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = BrutalDanger) }
    }
}
