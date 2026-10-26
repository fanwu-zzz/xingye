package com.fanwu.studyplanner.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.fanwu.studyplanner.BuildConfig
import com.fanwu.studyplanner.R
import com.fanwu.studyplanner.data.local.ApiKeyStore
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*

@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null, onOpenFeature: (String) -> Unit = {}, onOpenChat: () -> Unit = {}, onOpenSchedule: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { ApiKeyStore(context.applicationContext) }
    var saved by remember { mutableStateOf(store.apiKey.isNotBlank()) }
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var visible by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val configured = saved || BuildConfig.AI_API_KEY.isNotBlank()
    BrutalPage("我的学习工作台", "属于你的目标、工具和学习节奏", onBack) {
        BrutalPanel(color = BrutalYellow, shadow = true) {
            Text("按自己的节奏，\n持续向前。", style = MaterialTheme.typography.headlineMedium)
            Text("日历与会话保存在本机", style = MaterialTheme.typography.bodySmall)
            BrutalTag(if (configured) "AI 服务已配置" else "AI 服务待配置", BrutalWhite)
        }
        BrutalSection("学习工具")
        BrutalPanel {
            BrutalLinkRow("AI 学习搭子", "讨论目标，生成可执行计划", onOpenChat, icon = Icons.Outlined.AutoAwesome)
            HorizontalDivider(color = BrutalBlack)
            BrutalLinkRow("我的课表", "本周课程、教室与课表导入", onOpenSchedule, icon = Icons.Outlined.CalendarMonth)
            HorizontalDivider(color = BrutalBlack)
            BrutalLinkRow("专注空间", "计时、任务关联与实际专注记录", { onOpenFeature("focus") }, icon = Icons.Outlined.Timer)
            HorizontalDivider(color = BrutalBlack)
            BrutalLinkRow("学习目标", "每周投入与目标管理", { onOpenFeature("goals") }, icon = Icons.Outlined.Flag)
        }
        BrutalSection("应用设置")
        BrutalPanel {
            BrutalLinkRow("AI 服务设置", if (configured) "已配置 · 点击管理密钥" else "添加 DeepSeek API Key", {
                apiKey = store.apiKey; visible = false; edit = true
            }, icon = Icons.Outlined.Key)
            HorizontalDivider(color = BrutalBlack)
            BrutalLinkRow("提醒与通知", "任务提醒、课程提醒与静默时段", { onOpenFeature("reminders") }, icon = Icons.Outlined.Notifications)
            HorizontalDivider(color = BrutalBlack)
            BrutalLinkRow("数据与备份", "导出、恢复与数据范围", { onOpenFeature("backup") }, icon = Icons.Outlined.FolderOpen)
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(stringResource(R.string.app_tagline, stringResource(R.string.app_name)), style = MaterialTheme.typography.titleSmall)
        Text("版本 ${BuildConfig.VERSION_NAME} · 新野兽派界面", style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
    }
    if (edit) BrutalDialog("AI 服务设置", { edit = false; visible = false }, actions = {
        BrutalButton("保存到本机", {
            store.apiKey = apiKey.trim(); saved = true; edit = false; visible = false; notice = "AI 服务密钥已加密保存在本机"
        }, enabled = apiKey.isNotBlank())
        TextButton({ clear = true }, Modifier.fillMaxWidth()) { Text("清除本机密钥", color = BrutalDanger) }
    }) {
        BrutalTag("DeepSeek")
        BrutalField(apiKey, { apiKey = it }, "API Key", visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = { TextButton({ visible = !visible }) { Text(if (visible) "隐藏" else "显示", color = BrutalBlack) } })
        Text("密钥通过 Android Keystore 加密保存在本机。调用 AI 时会发送给 DeepSeek 进行身份验证。", style = MaterialTheme.typography.bodySmall)
        Text(if (BuildConfig.AI_API_KEY.isBlank()) "未配置编译期默认密钥。" else "存在编译期默认密钥；清除本机密钥后仍会回退到默认配置。", style = MaterialTheme.typography.bodySmall)
    }
    if (clear) BrutalConfirm("清除本机密钥？", "将移除你在本机保存的 API Key，不影响编译期默认配置。", "确认清除", {
        store.apiKey = ""; apiKey = ""; saved = false; clear = false; edit = false; visible = false; notice = "已清除本机保存的密钥"
    }, { clear = false }, true)
}
