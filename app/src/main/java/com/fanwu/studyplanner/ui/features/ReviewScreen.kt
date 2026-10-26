package com.fanwu.studyplanner.ui.features

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.BuildConfig
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.remote.DeepSeekClient
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.calendar.EventEditorDialog
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import kotlinx.coroutines.*
import java.time.LocalDate

@Composable
fun ReviewScreen(onBack: () -> Unit, vm: LearningViewModel = viewModel(factory = LearningViewModel.factory(LocalContext.current))) {
    val context = LocalContext.current.applicationContext
    val events by vm.events.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val intervals by vm.intervals.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val repository = remember { ReviewRepository(vm.db, DeepSeekClient.api) }
    val scope = rememberCoroutineScope()
    var generation by remember { mutableStateOf<Job?>(null) }
    var generating by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var weekText by rememberSaveable { mutableStateOf(mondayOf(LocalDate.now()).toString()) }
    val week = LocalDate.parse(weekText)
    val summary = buildWeekReview(week, events.map { it.toDomain() }, intervals.map { i -> i.toFocusSpan() to sessions.firstOrNull { it.id == i.sessionId }?.toFocusSession() })
    var sendConfirmation by remember { mutableStateOf<ReviewSendRequest?>(null) }
    val drafts = docs.filter { it.kind == "review" }.map { it.decode<ReviewDraft>() }.filter { it.week == weekText }
    var applying by remember { mutableStateOf<ReviewDraft?>(null) }
    var editing by remember { mutableStateOf<Pair<ReviewDraft, Int>?>(null) }
    BrutalPage("每周学习复盘", "本地摘要随时可看，AI 建议需确认发送", onBack, modifier = Modifier.navigationBarsPadding()) {
        WeekSelector(week) { weekText = it.toString() }
        BrutalPanel(color = BrutalCyan) { Text("本地复盘", style = MaterialTheme.typography.titleLarge); Text(summary.text) }
        FeatureFeedback(vm)
        generationError?.let { Text(it, color = BrutalDanger) }
        if (generating) {
            Text("AI 正在生成建议…")
            BrutalButton("取消生成", { generation?.cancel() }, color = BrutalWhite)
        } else BrutalButton("查看摘要并请求 AI 建议", {
            sendConfirmation = ReviewSendRequest(summary, mondayOf(LocalDate.now()).plusWeeks(1))
        }, enabled = !busy)
        Text("本地摘要不联网。点击上方按钮后仍需核对内容，再决定是否发送至 DeepSeek。返回页面会取消尚未完成的请求。")
        if (drafts.isEmpty()) Text("还没有这一周的 AI 建议；即使没有数据，也可以阅读本地复盘。")
        drafts.forEach { d ->
            BrutalSection(if (d.applied) "已应用建议" else "待确认建议")
            Text(d.reply)
            if (d.events.isEmpty()) Text("本次 AI 只返回了文字建议，没有新增日程。")
            d.events.forEachIndexed { index, e ->
                BrutalPanel {
                    Text(e.title, style = MaterialTheme.typography.titleMedium)
                    Text("${e.date} ${e.startTime}—${e.endTime}")
                    if (!d.applied) {
                        TextButton({ editing = d to index }, enabled = !busy) { Text("修改任务", color = BrutalBlack) }
                        TextButton({ vm.run { repository.update(d.copy(events = d.events.filterIndexed { i, _ -> i != index })) } }, enabled = !busy) { Text("移除建议", color = BrutalDanger) }
                    }
                }
            }
            if (!d.applied && d.events.isNotEmpty()) BrutalButton("预览确认后应用这些任务", { applying = d }, enabled = !busy, color = BrutalWhite)
        }
    }
    // 唯一的生成入口；打开或阅读本地复盘不会发起请求。
    sendConfirmation?.let { request ->
        BrutalDialog("确认向 DeepSeek 发送？", { sendConfirmation = null }, actions = {
            BrutalButton("同意发送此摘要至 DeepSeek", {
                sendConfirmation = null
                generation = scope.launch {
                    generating = true; generationError = null
                    try {
                        withContext(Dispatchers.IO) {
                            val key = ApiKeyStore(context).apiKey.trim().ifBlank { BuildConfig.AI_API_KEY.trim() }
                            repository.generate(request, key)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { generationError = "AI 建议生成失败。请检查 API Key 和网络，然后重新确认发送；本地摘要仍可使用。" }
                    finally { generating = false }
                }
            })
            TextButton({ sendConfirmation = null }) { Text("取消，不发送", color = BrutalBlack) }
        }) {
            Text("目的地：https://api.deepseek.com/chat/completions")
            Text("发送内容仅为下方汇总摘要（含科目标签、投入与完成统计），并附生成下周学习任务的系统指令。不会上传历史对话、备份文件或任务备注。API Key 只用于该地址的请求认证。")
            Text("建议日期范围：${request.nextMonday} 至 ${request.nextMonday.plusDays(6)}。返回结果先保存为草稿，不自动修改日历。")
            HorizontalDivider(color = BrutalBlack)
            Text(request.review.text)
        }
    }
    applying?.let { d -> BrutalDialog("应用建议预览", { applying = null }, actions = {
        BrutalButton("确认新增这些学习任务", {
            vm.run { repository.apply(d); vm.message.value = "建议已应用到日历" }; applying = null
        }, enabled = !busy)
    }) {
        Text("仅新增下方任务。若与已有课程、考试或任务冲突，将阻止整个应用，不覆盖已有安排。")
        d.events.forEach { Text("${it.date} ${it.startTime}—${it.endTime} ${it.title}") }
    } }
    editing?.let { (d, index) -> EventEditorDialog(d.events[index], LocalDate.now(), { editing = null }) { e ->
        vm.run { repository.update(d.copy(events = d.events.toMutableList().also { it[index] = e })) }; editing = null
    } }
}
