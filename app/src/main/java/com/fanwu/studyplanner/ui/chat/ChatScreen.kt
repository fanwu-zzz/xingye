package com.fanwu.studyplanner.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.domain.ChatMessage
import com.fanwu.studyplanner.domain.Conversation
import com.fanwu.studyplanner.domain.ConversationMode
import com.fanwu.studyplanner.ui.theme.ApricotOrange
import com.fanwu.studyplanner.ui.theme.CreamBackground
import com.fanwu.studyplanner.ui.theme.DarkBar
import com.fanwu.studyplanner.ui.theme.OrangePrimary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 对话入口：会话列表 或 某个会话的聊天详情
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(LocalContext.current)
    )
) {
    val currentId by viewModel.currentConversationId.collectAsStateWithLifecycle()

    if (currentId == null) {
        ConversationListView(onBack = onBack, viewModel = viewModel)
    } else {
        ChatDetailView(viewModel = viewModel)
    }
}

/** 会话列表页：新建对话入口 + 历史会话 */
@Composable
private fun ConversationListView(onBack: () -> Unit, viewModel: ChatViewModel) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    // 左滑触发删除后待确认的会话
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(containerColor = CreamBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = DarkBar)
                }
                Text(
                    text = "AI 对话",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DarkBar
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            // 新建对话入口：杏橙胶囊按钮 + 深棕文字
            Button(
                onClick = { showNameDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApricotOrange,
                    contentColor = DarkBar
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "新建对话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "还没有对话。\n点击上方按钮新建一轮对话，\n和 AI 聊聊你的学习目标。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        ConversationCard(
                            conversation = conversation,
                            onClick = { viewModel.openConversation(conversation.id) },
                            onDeleteRequest = { pendingDelete = conversation }
                        )
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        NewConversationDialog(
            onConfirm = { name, mode ->
                viewModel.createConversation(name, mode)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除对话") },
            text = {
                Text(
                    "「${target.name}」及其中的 ${target.messageCount} 条聊天消息将被删除，" +
                        "已应用到日历的计划不受影响。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(target.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = DeleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationCard(
    conversation: Conversation,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // 左滑只弹出确认框，卡片本身保持原位
            if (value == SwipeToDismissBoxValue.EndToStart) onDeleteRequest()
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(DeleteRed)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("删除", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkBar,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${conversation.messageCount} 条消息 · " +
                            SimpleDateFormat("M月d日", Locale.CHINA).format(Date(conversation.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = ChipGrey,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ChevronGrey,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private val ChipGrey = Color(0xFF9A9490)
private val ChevronGrey = Color(0xFFC9C4BE)
private val DeleteRed = Color(0xFFE0524A)

/** 新建对话命名弹窗：先选用途（学习计划 / 导入课表），再填名字 */
@Composable
private fun NewConversationDialog(
    onConfirm: (String, ConversationMode) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(ConversationMode.STUDY_PLAN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建对话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeOption(
                    title = "建立学习计划",
                    subtitle = "和 AI 聊目标，生成可执行的复习安排",
                    selected = mode == ConversationMode.STUDY_PLAN,
                    onClick = { mode = ConversationMode.STUDY_PLAN }
                )
                ModeOption(
                    title = "导入课表",
                    subtitle = "上传教务系统导出的 Excel，由 AI 识别后写入日历",
                    selected = mode == ConversationMode.IMPORT_SCHEDULE,
                    onClick = { mode = ConversationMode.IMPORT_SCHEDULE }
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("输入这一轮对话的名字") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, mode) },
                enabled = name.isNotBlank()
            ) { Text("开始对话") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            ApricotOrange.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** 聊天详情页：消息气泡 + 生成计划入口 + 输入框 */
@Composable
private fun ChatDetailView(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val pendingPlan by viewModel.pendingPlan.collectAsStateWithLifecycle()
    val latestPlan by viewModel.latestPlan.collectAsStateWithLifecycle()
    val conversationId by viewModel.currentConversationId.collectAsStateWithLifecycle()
    val conversation by viewModel.currentConversation.collectAsStateWithLifecycle()

    val isImportMode = conversation?.mode == ConversationMode.IMPORT_SCHEDULE
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importScheduleFile(uri, context.displayNameOf(uri))
        }
    }

    var input by rememberSaveable { mutableStateOf("") }
    var showGenerateConfirm by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 弹窗打开时收起键盘，避免输入法把弹窗底部的按钮顶出屏幕
    LaunchedEffect(showGenerateConfirm, pendingPlan) {
        if (showGenerateConfirm || pendingPlan != null) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // 至少完成一轮对话（有 AI 回复）后展示"生成计划"入口
    val canGenerate = !isImportMode && messages.any { it.role == "assistant" }
    // 本轮对话最近一次生成的计划（关闭弹窗后仍可重新查看）
    val cachedPlan = latestPlan?.takeIf { it.conversationId == conversationId }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(containerColor = CreamBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::backToList) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回列表")
                }
                Text(
                    text = viewModel.currentConversationName(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = viewModel::clearHistory) { Text("清空") }
            }

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isImportMode) {
                            "点击下方「导入课表文件」，\n选择教务系统导出的 Excel 课表。\nAI 识别后会先让你确认再写入日历。"
                        } else {
                            "和 AI 聊聊你的学习目标吧。\n例如：下个月有线性代数考试，\n帮我安排一下复习计划。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                }
            }

            // 导入课表模式：入口是「导入课表文件」；学习计划模式：聊过几轮后出现「生成计划」
            if (isImportMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { filePicker.launch(IMPORT_MIME_TYPES) },
                        enabled = !isSending,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApricotOrange,
                            contentColor = Color(0xFF4A3A28)
                        )
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSending) "正在解析课表…" else "导入课表文件")
                    }

                    if (cachedPlan != null && pendingPlan == null) {
                        OutlinedButton(
                            onClick = viewModel::reopenLatestPlan,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (cachedPlan.applied) "上次课表" else "查看课表")
                        }
                    }
                }
            } else if (canGenerate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showGenerateConfirm = true },
                        enabled = !isGenerating && !isSending,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApricotOrange,
                            contentColor = Color(0xFF4A3A28)
                        )
                    ) {
                        Text(if (isGenerating) "正在生成计划…" else "生成学习计划")
                    }

                    if (cachedPlan != null && pendingPlan == null) {
                        OutlinedButton(
                            onClick = viewModel::reopenLatestPlan,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (cachedPlan.applied) "上次计划" else "查看计划")
                        }
                    }
                }
            }

            if (isSending) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI 正在思考…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            notice?.let { text ->
                Text(
                    text = text,
                    color = OrangePrimary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("输入消息…") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !isSending
                ) {
                    Text("发送")
                }
            }
        }
    }

    pendingPlan?.let { draft ->
        PlanPanelDialog(
            plan = draft.plan,
            planName = draft.name,
            applied = draft.applied,
            onRename = viewModel::renamePlan,
            onApply = viewModel::confirmPlan,
            onDismiss = viewModel::dismissPlan
        )
    }

    if (showGenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showGenerateConfirm = false },
            title = { Text("生成学习计划？") },
            text = {
                Text("AI 会根据当前对话生成一组学习计划，生成后可以先预览、修改组名，再决定是否写入日历。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGenerateConfirm = false
                        viewModel.generatePlan()
                    }
                ) { Text("开始生成") }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) ApricotOrange.copy(alpha = 0.55f) else Color.White,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.displayText(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/** 发给 AI 的表格原始文本很长，气泡里只显示摘要；模型返回的 JSON 只展示其中的 message */
private fun ChatMessage.displayText(): String {
    val raw = content.trim()
    if (raw.startsWith("【课表文件】")) {
        val name = raw.lineSequence().firstOrNull().orEmpty()
            .removePrefix("【课表文件】")
            .substringBefore("｜")
            .trim()
        return "已导入课表文件：$name"
    }
    if (raw.startsWith("{")) {
        val friendly = runCatching {
            chatJson.parseToJsonElement(raw)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
        if (!friendly.isNullOrBlank()) return friendly
    }
    return content
}

private fun Context.displayNameOf(uri: Uri): String {
    val name = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
    return name ?: "课表文件.xlsx"
}

private val chatJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val IMPORT_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/octet-stream"
)
