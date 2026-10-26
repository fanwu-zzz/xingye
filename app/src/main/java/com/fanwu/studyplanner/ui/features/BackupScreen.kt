package com.fanwu.studyplanner.ui.features

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.R
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate

@Composable
fun BackupScreen(onBack: () -> Unit, vm: LearningViewModel = viewModel(factory = LearningViewModel.factory(LocalContext.current))) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.restorePreview.collectAsStateWithLifecycle()
    var policy by remember { mutableStateOf(RestorePolicy.SKIP) }
    var confirm by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readBackup) }
    BrutalPage("数据与备份", "通过系统文件选择器保存和恢复", onBack, modifier = Modifier.navigationBarsPadding()) {
        BrutalPanel(color = BrutalCyan) {
            Text("备份范围", style = MaterialTheme.typography.titleLarge)
            Text("日历、计划、全部对话、专注时间片、周目标及历史版本、学期与重复课程、复盘草稿、提醒设置。")
            Text("API Key、Keystore 密钥和系统权限不会导出。文件包含你的学习记录和对话，请保存在可信位置。")
        }
        FeatureFeedback(vm)
        BrutalButton("导出备份", { create.launch("${context.getString(R.string.app_name)}-${LocalDate.now()}.json") }, enabled = !busy)
        BrutalButton("选择备份恢复", { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, color = BrutalWhite, enabled = !busy)
        Text("恢复前先校验格式和关联关系，再显示预览。导入失败会整体回滚；相同来源重复导入不会重复计算专注时间。恢复不会清空本机数据。")
        preview?.let { p ->
            BrutalSection("恢复预览")
            BrutalPanel {
                Text("格式 v${p.backup.schemaVersion} · 应用 ${p.backup.appVersion}")
                Text("日程 ${p.backup.events.size} · 对话 ${p.backup.conversations.size} · 消息 ${p.backup.messages.size}\n专注 ${p.backup.sessions.size} 轮 / ${p.backup.intervals.size} 片段\n目标/学期/课程/复盘 ${p.backup.documents.size} 项")
                Text("已识别重复 ${p.duplicates} 项，日程/配置内容冲突 ${p.conflicts} 项。")
                p.blockingReason?.let { Text(it, color = BrutalDanger) }
                Text("专注时间片按 UUID 去重且不可覆盖；未完成的专注恢复后结束，保留已结算时长。恢复前请结束本机当前专注。")
            }
            BrutalChoice("合并，冲突保留本机", policy == RestorePolicy.SKIP, { policy = RestorePolicy.SKIP }, Modifier.fillMaxWidth())
            BrutalChoice("合并，匹配项用备份覆盖", policy == RestorePolicy.OVERWRITE, { policy = RestorePolicy.OVERWRITE }, Modifier.fillMaxWidth())
            Text("覆盖范围：匹配日程、同 ID 的目标/学期/课程/复盘、对话名称与提醒配置；历史消息和专注仅追加去重，不删除。")
            BrutalButton("确认恢复这些记录", { confirm = true }, enabled = !busy && p.blockingReason == null)
            TextButton({ vm.restorePreview.value = null }) { Text("取消恢复", color = BrutalBlack) }
        }
    }
    if (confirm) BrutalConfirm("恢复备份？", if (policy == RestorePolicy.SKIP) "冲突记录保留本机，新增记录合并导入。" else "匹配的日程与配置将被备份覆盖；本机其他记录保留。", "开始恢复", {
        confirm = false
        preview?.let { p -> vm.run { vm.message.value = vm.backup.restore(p, policy); vm.restorePreview.value = null } }
    }, { confirm = false }, danger = policy == RestorePolicy.OVERWRITE)
}
