package com.fanwu.studyplanner.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.data.agent.AgentStatus
import com.fanwu.studyplanner.data.agent.AgentPreferences
import com.fanwu.studyplanner.data.agent.publicWebUrl
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    onBack: () -> Unit, initialMode: ConversationMode? = null,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(LocalContext.current))
) {
    val id by viewModel.currentConversationId.collectAsStateWithLifecycle()
    BackHandler(id != null) { viewModel.backToList() }
    if (id == null) ConversationList(onBack, initialMode, viewModel) else ChatDetail(viewModel)
}

@Composable
private fun ConversationList(onBack: () -> Unit, initialMode: ConversationMode?, vm: ChatViewModel) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(initialMode != null) }
    var mode by rememberSaveable { mutableStateOf(initialMode ?: ConversationMode.STUDY_PLAN) }
    var name by rememberSaveable {
        mutableStateOf(if (initialMode == ConversationMode.IMPORT_SCHEDULE) DEFAULT_IMPORT_NAME else "")
    }
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    BrutalPage("AI 学习搭子", "从一个目标，聊到一份可执行的计划", onBack,
        scrollable = false, modifier = Modifier.navigationBarsPadding()) {
        BrutalPanel(color = BrutalCyan, shadow = true) {
            Text("从目标，到每天的行动。", style = MaterialTheme.typography.titleLarge)
            Text("聊目标与空闲时间，预览计划后再应用。", style = MaterialTheme.typography.bodySmall)
        }
        BrutalButton("新建对话", {
            if (mode == ConversationMode.IMPORT_SCHEDULE && name.isBlank()) name = DEFAULT_IMPORT_NAME
            create = true
        }, icon = Icons.Outlined.Add)
        BrutalField(query, { query = it }, "搜索历史对话")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            val filtered = conversations.filter { query.isBlank() || it.name.contains(query.trim(), true) }
            if (filtered.isEmpty()) item {
                BrutalEmpty(if (query.isBlank()) "从一个具体目标开始" else "没有找到相关对话",
                    if (query.isBlank()) "例如：四周后考线性代数，每晚可以复习一小时。" else "换个关键词试试。")
            }
            items(filtered, key = { it.id }) { conversation ->
                val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.EndToStart) deleting = conversation
                    false
                })
                SwipeToDismissBox(dismiss, enableDismissFromStartToEnd = false, backgroundContent = {
                    Box(Modifier.fillMaxSize().background(BrutalDanger).padding(16.dp), contentAlignment = Alignment.CenterEnd) { Text("删除对话", color = BrutalWhite) }
                }) {
                    BrutalPanel(modifier = Modifier.clickable(role = Role.Button) { vm.openConversation(conversation.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                BrutalTag(if (conversation.mode == ConversationMode.IMPORT_SCHEDULE) "课表导入" else "学习计划",
                                    if (conversation.mode == ConversationMode.IMPORT_SCHEDULE) BrutalCyan else BrutalLime)
                                Text(conversation.name, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                            }
                            BrutalIconButton(Icons.Outlined.DeleteOutline, "删除对话", { deleting = conversation })
                        }
                        Text("${conversation.messageCount} 条消息 · ${SimpleDateFormat("M月d日", Locale.CHINA).format(Date(conversation.createdAt))}",
                            style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
                        TextButton({ vm.openConversation(conversation.id) }) { Text("继续对话 →", color = BrutalBlack) }
                    }
                }
            }
        }
    }
    if (create) BrutalDialog("开启一轮新对话", { create = false }, actions = {
        BrutalButton("开始对话", { vm.createConversation(name, mode); name = ""; create = false }, enabled = name.isNotBlank())
    }) {
        BrutalChoice("制定学习计划", mode == ConversationMode.STUDY_PLAN, {
            mode = ConversationMode.STUDY_PLAN
            if (name == DEFAULT_IMPORT_NAME) name = ""
        }, Modifier.fillMaxWidth())
        Text("讨论目标、安排时间，并生成可应用的学习任务。", style = MaterialTheme.typography.bodySmall)
        BrutalChoice("导入 Excel 课表", mode == ConversationMode.IMPORT_SCHEDULE, {
            mode = ConversationMode.IMPORT_SCHEDULE
            if (name.isBlank()) name = DEFAULT_IMPORT_NAME
        }, Modifier.fillMaxWidth())
        Text("上传课表，补充学期与节次信息，再确认写入日历。", style = MaterialTheme.typography.bodySmall)
        BrutalField(name, { name = it }, "对话名称")
    }
    deleting?.let { target ->
        BrutalConfirm("删除对话？", "「${target.name}」的 ${target.messageCount} 条消息将被删除，已应用到日历的计划保留。", "确认删除",
            { vm.deleteConversation(target.id); deleting = null }, { deleting = null }, true)
    }
}

@Composable
private fun ChatDetail(vm: ChatViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sending by vm.isSending.collectAsStateWithLifecycle()
    val generating by vm.isGenerating.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val pending by vm.pendingPlan.collectAsStateWithLifecycle()
    val latest by vm.latestPlan.collectAsStateWithLifecycle()
    val failedInput by vm.failedInput.collectAsStateWithLifecycle()
    val applying by vm.isApplying.collectAsStateWithLifecycle()
    val agent by vm.agentRun.collectAsStateWithLifecycle()
    val preferences by vm.agentPreferences.collectAsStateWithLifecycle()
    var preferencesDialog by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }
    val id by vm.currentConversationId.collectAsStateWithLifecycle()
    val conversation by vm.currentConversation.collectAsStateWithLifecycle()
    val importing = conversation?.mode == ConversationMode.IMPORT_SCHEDULE
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importScheduleFile(it, context.displayNameOf(it)) }
    }
    var input by rememberSaveable(id) { mutableStateOf("") }
    var generatingConfirm by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = sending || generating || applying
    val cached = latest?.takeIf { it.conversationId == id }
    LaunchedEffect(messages.size, error, notice) {
        listState.scrollToItem(messages.size.coerceAtLeast(1))
    }
    LaunchedEffect(pending, generatingConfirm, clearConfirm) {
        if (pending != null || generatingConfirm || clearConfirm) { focus.clearFocus(); keyboard?.hide() }
    }
    BrutalPage(conversation?.name ?: "AI 对话", if (importing) "课表导入 · 解析后预览确认" else "学习规划智能体 · 查询、规划、确认",
        vm::backToList, scrollable = false, modifier = Modifier.navigationBarsPadding().imePadding(), actions = {
            BrutalIconButton(Icons.Outlined.DeleteOutline, "清空当前会话消息", { clearConfirm = true })
        }) {
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (messages.isEmpty()) item {
                BrutalEmpty(if (importing) "让课表自己排进日历" else "先聊聊你想完成什么",
                    if (importing) "上传教务系统导出的 Excel；学期日期和节次时间可以在对话中补充。" else "告诉我目标和空闲时间。智能体可按需查询本地学习数据和联网查资料，相关信息会发送给 DeepSeek；日历变更先预览确认。联网可在学习偏好中关闭。")
                if (!importing) {
                    Spacer(Modifier.height(16.dp))
                    BrutalChoice("试试：四周复习计划", false, { input = "我想在四周内完成复习，每晚有一小时。请先问我需要补充哪些信息。" }, Modifier.fillMaxWidth())
                }
            }
            items(messages, key = { it.id }) { message ->
                val user = message.role == "user"
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
                    Text(if (user) "我" else "AI 学习搭子", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
                    Box(Modifier.widthIn(max = 640.dp).fillMaxWidth(if (user) .9f else 1f)
                        .background(if (user) BrutalCyan else BrutalWhite).border(2.dp, BrutalBlack).padding(16.dp)) {
                        SelectionContainer { LinkedMessageText(message.displayText()) }
                    }
                }
            }
            item(key = "feedback") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    agent?.takeIf { !importing && it.conversationId == id }?.let { run ->
                        Text(when (run.status) {
                            AgentStatus.RUNNING -> if (busy) "正在查询和规划 · 可取消" else "处理曾中断 · 可继续恢复"
                            AgentStatus.WAITING_CONFIRMATION -> "草稿已就绪 · 尚未写入日历"
                            AgentStatus.APPLIED -> "已实际保存${run.appliedIds.size}项到日历"
                            AgentStatus.COMPLETED -> "已回复 · 本轮未写入日历"
                            AgentStatus.CANCELLED -> "已取消 · 进度已保存"
                            AgentStatus.FAILED -> "处理失败 · 进度已保存"
                            AgentStatus.LIMIT_REACHED -> "达到本次处理上限 · 请缩小目标后发送"
                        }, style = MaterialTheme.typography.bodySmall)
                        (if (showSteps) run.steps else run.steps.takeLast(3)).forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                        if (run.webRequests > 0) Text("本轮联网尝试 ${run.webRequests}/6 次 · 来源随回复保存", style = MaterialTheme.typography.bodySmall)
                        if (run.steps.size > 3) TextButton({ showSteps = !showSteps }) { Text(if (showSteps) "收起处理步骤" else "查看全部处理步骤", color = BrutalBlack) }
                        if (!busy && run.status in listOf(AgentStatus.RUNNING, AgentStatus.CANCELLED, AgentStatus.FAILED)) {
                            TextButton(vm::resumeAgent) { Text("继续上次处理", color = BrutalBlack) }
                        }
                    }
                    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BrutalDanger) }
                    failedInput?.let { failed ->
                        TextButton({ input = failed; vm.clearFailedInput() }, enabled = input.isBlank()) {
                            Text("恢复上次输入", color = BrutalBlack)
                        }
                    }
                }
            }
        }
        if (busy) Text(if (generating) "正在整理计划，请稍候…" else "AI 正在思考，请稍候…", style = MaterialTheme.typography.bodySmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!importing) {
                if (sending) TextButton(vm::cancelAgent) { Text("取消处理并保留进度", color = BrutalBlack) }
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ preferencesDialog = true }, enabled = !busy) { Text("学习偏好", color = BrutalBlack) }
                    Text(if (preferences.allowWebSearch) "可按需联网查资料" else "联网检索已关闭", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (importing) {
                Text(when {
                    cached?.applied == true -> "已导入 · 可到课表或日历查看"
                    cached != null -> "尚未导入 · 请预览并确认"
                    sending -> "正在处理 · 完成后预览确认"
                    else -> "尚未导入 · 补齐信息后可预览"
                }, style = MaterialTheme.typography.bodySmall)
                if (messages.isNotEmpty() || cached != null) {
                    BrutalButton(if (cached?.applied == true) "查看已导入课表" else "预览并确认导入",
                        vm::reopenLatestPlan, enabled = cached != null && !busy, icon = Icons.Outlined.Preview)
                    TextButton({ picker.launch(IMPORT_MIME_TYPES) }, Modifier.fillMaxWidth(), enabled = !busy) {
                        Text("选择 / 更换 Excel 课表", color = BrutalBlack)
                    }
                } else {
                    BrutalButton("选择 Excel 课表", { picker.launch(IMPORT_MIME_TYPES) }, enabled = !busy, icon = Icons.Outlined.UploadFile)
                }
            } else if (messages.any { it.role == "assistant" }) {
                BrutalButton(if (generating) "正在生成计划…" else "让智能体制定计划", { generatingConfirm = true }, enabled = !busy, icon = Icons.Outlined.AutoAwesome)
            }
            if (!importing && cached != null && pending == null) {
                TextButton(vm::reopenLatestPlan, Modifier.fillMaxWidth()) {
                    Text(if (cached.applied) "查看已应用的计划" else "继续预览上次计划", color = BrutalBlack)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalField(input, { input = it }, if (importing) "补充课表信息或提问" else "输入学习目标或补充信息",
                    Modifier.weight(1f), singleLine = false)
                Box(Modifier.width(96.dp)) {
                    BrutalButton("发送", {
                        if (vm.send(input)) input = ""
                    }, enabled = input.isNotBlank() && !busy, color = BrutalYellow)
                }
            }
        }
    }
    pending?.let { draft ->
        PlanPanelDialog(draft.plan, draft.name, draft.applied, vm::renamePlan, vm::confirmPlan, vm::dismissPlan,
            importing = draft.coursePreview != null, applying = applying, error = error, allowEmpty = draft.coursePreview != null)
    }
    if (generatingConfirm) BrutalConfirm("制定学习计划？", "智能体将根据当前对话查询真实日程和待办，按联网设置查资料，必要时向你追问；草稿经预览确认后才写入日历。", "开始规划",
        { generatingConfirm = false; vm.generatePlan() }, { generatingConfirm = false })
    if (preferencesDialog) {
        var text by remember(preferences) { mutableStateOf(preferences.text) }
        var maximum by remember(preferences) { mutableStateOf(preferences.maxMinutesPerDay?.toString().orEmpty()) }
        var allowWeb by remember(preferences) { mutableStateOf(preferences.allowWebSearch) }
        var preferenceError by remember { mutableStateOf<String?>(null) }
        BrutalDialog("学习偏好", { preferencesDialog = false }, actions = {
            preferenceError?.let { Text(it, color = BrutalDanger) }
            BrutalButton("保存偏好", {
                preferenceError = runCatching {
                    AgentPreferences(text, if (maximum.isBlank()) null else maximum.toIntOrNull() ?: error("每日上限应为整数"), allowWeb).validate()
                }.exceptionOrNull()?.message
                if (preferenceError == null) { vm.saveAgentPreferences(text, maximum, allowWeb); preferencesDialog = false }
            })
            TextButton({ text = ""; maximum = "" }) { Text("清空填写内容", color = BrutalBlack) }
        }) {
            Text("由你维护的偏好会在后续规划时发送给 DeepSeek。这里只保存安排习惯，不需要填写密钥或个人身份信息。", style = MaterialTheme.typography.bodySmall)
            BrutalField(text, { text = it.take(1000) }, "安排偏好（如：周末不早起）", singleLine = false)
            BrutalField(maximum, { maximum = it.filter(Char::isDigit).take(3) }, "每日计划学习上限（分钟，可留空）")
            Text("每日上限包含日历中已有的学习安排；截止任务不计时长。保存后用于下一次规划和草稿确认。", style = MaterialTheme.typography.bodySmall)
            BrutalChoice("允许按需联网查资料", allowWeb, { allowWeb = !allowWeb }, Modifier.fillMaxWidth())
            Text("检索词和网址发送至 DeepSeek，沿用现有 API Key。搜索与网页读取按服务规则另行计费，每轮合计最多6次；不会因此自动写入日历。", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (clearConfirm) BrutalConfirm("清空当前会话？", "当前会话的全部消息会被删除，已经应用到日历的安排不受影响。", "确认清空",
        { vm.clearHistory(); clearConfirm = false }, { clearConfirm = false }, true)
}

@Composable
private fun LinkedMessageText(value: String) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember(value) { mutableStateOf(false) }
    val annotated = remember(value, uriHandler) {
        buildAnnotatedString {
            var start = 0
            WEB_LINK.findAll(value).forEach { match ->
                val raw = match.value.trimEnd('.', ',', ';', '。', '，', '；', '、')
                val safe = runCatching { publicWebUrl(raw) }.getOrNull()
                append(value.substring(start, match.range.first))
                if (safe == null) append(raw) else withLink(LinkAnnotation.Url(safe,
                    TextLinkStyles(style = SpanStyle(color = BrutalBlack, textDecoration = TextDecoration.Underline))) {
                    linkError = runCatching { uriHandler.openUri(safe) }.isFailure
                }) { append(raw) }
                start = match.range.first + raw.length
                append(value.substring(start, match.range.last + 1))
                start = match.range.last + 1
            }
            append(value.substring(start))
        }
    }
    Column {
        Text(annotated, style = MaterialTheme.typography.bodyLarge)
        if (linkError) Text("无法打开链接，可长按复制网址到浏览器。", style = MaterialTheme.typography.bodySmall, color = BrutalDanger)
    }
}

private val WEB_LINK = Regex("https?://[^\\s<>\"）)\\]]+", RegexOption.IGNORE_CASE)

private fun ChatMessage.displayText(): String {
    val raw = content.trim()
    if (raw.startsWith("【课表文件】")) return "已选择课表文件：" + raw.lineSequence().firstOrNull().orEmpty().removePrefix("【课表文件】").substringBefore("｜").trim()
    if (raw.startsWith("{")) {
        val friendly = runCatching { chatJson.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        if (!friendly.isNullOrBlank()) return friendly
    }
    return content
}
private fun Context.displayNameOf(uri: Uri): String =
    runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull() ?: "课表文件.xlsx"
private val chatJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val IMPORT_MIME_TYPES = arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
private const val DEFAULT_IMPORT_NAME = "导入课表"
