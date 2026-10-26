# StudyPlanner 项目上下文

个人使用的 Android 学习计划 App：和 AI 聊天 → 生成学习计划 → 应用后写入日历 → 在统计/任务页跟踪完成情况。

## 工作方式（强制）
- 收到任何需求后，先用自然语言向用户复述一遍：**要做什么、改哪里、不做什么、有无关键取舍**，然后停下等待用户确认
- 用户明确确认（如「确认」「可以」「开始」）后才开始改代码、跑命令、生成文件
- 用户提出修改或纠正复述时，按新理解重新复述并再次等待确认，不得直接开工
- 复述要简短，一两句话讲清理解，不写方案文档、不提前贴大段代码

## 技术栈
- Kotlin 2.0.21、Jetpack Compose（BOM 2024.09.00）、Material 3、Room 2.6.1（KSP）、Retrofit + kotlinx.serialization、DeepSeek API
- compileSdk/targetSdk 36、minSdk 26，JDK 11
- 无 Navigation 库，页面切换由 `MainActivity` 的状态驱动

## 常用命令
- 编译：`.\gradlew.bat assembleDebug`
- 安装：`adb install -r app\build\outputs\apk\debug\app-debug.apk`
- 启动：`adb shell monkey -p com.fanwu.studyplanner -c android.intent.category.LAUNCHER 1`

## 架构（app/src/main/java/com/fanwu/studyplanner/）
- `data/local/`：Room 实体 + DAO + `AppDatabase` + `ApiKeyStore`（Keystore 加密存 Key）
- `data/remote/`：`DeepSeekClient` / `DeepSeekApi` / DTO
- `data/repository/`：`CalendarRepository`、`ChatRepository`（实体 ↔ 领域模型转换在此）
- `domain/`：`CalendarEvent`、`Conversation`、`ChatMessage`、`StudyPlan`
- `ui/calendar`、`ui/chat`、`ui/stats`、`ui/tasks`、`ui/settings`、`ui/theme`，共享组件在 `ui/components`，课表/目标/备份/学期/复盘在 `ui/features`，专注页面在 `ui/focus`，后台服务/闹钟/通知在 `focus`

## 关键结构
- `MainActivity`：底部三 Tab（日历 `calendar` / 统计 `stats` / 我的 `profile`），子页 `subPage` 管理对话列表、聊天详情、任务页
- `CalendarEventEntity`：`id, title, date(YYYY-MM-DD), startTime/endTime(HH:mm), type(EXAM/CLASS/STUDY_TASK), note, isDone, planName`
- `planName` 标记事件属于哪个 AI 计划组，为 null 表示未分组；整组删除按 `planName` 删除
- AI 计划流程：会话 → 生成计划 JSON → `PlanPanelDialog` 预览/改组名 → 应用时写入 `calendar_events` 并带 `planName`
- 统计页：待完成任务按计划分组（含逾期、可左滑整组删除）、周/月计划时长与完成率、每日计划图表（Compose 自绘，无图表库）；课程排除，实际专注时长只来自 F01 有效时间片
- F01：专注轮次与暂停区间、有效时间片保存在 Room；状态推进统一经 FocusRepository 事务处理，使用单调时钟，不按 UI tick 累计时长；设备重启后保留最近检查点并暂停，见 `docs/F01_FOCUS.md`
- F03：`ui/reminders` 为正式设置和通知日程详情，`reminders` 负责 Room 提醒凭据、单个最近 AlarmManager 闹钟、系统变更广播与 JobScheduler 补偿；日历写入统一经 ReminderRuntime.calendarMutation 重排，课表替换为事务，见 `docs/F03_REMINDERS.md`
- Android 设备测试必须使用 `connectedFocusTestAndroidTest` 独立测试包 `com.fanwu.studyplanner.f01test`；禁止让设备测试驱动卸载含用户数据的日常应用包，安装/迁移测试前先确认清理策略并备份
- F01—F06 均已接入；功能状态与后续增强见 `docs/FEATURE_BACKLOG.md`，F02/F04—F06 细节见 `docs/F02_F06_LEARNING_FEATURES.md`。正式入口不显示“功能预览”，日程变更与备份恢复仍保留真实预览确认。

## 约定
- UI 文案、注释、提交信息均用中文
- 改任何 UI 前先完整阅读 `.trae/rules/ui_style.md`（唯一设计规范：新野兽派、主题 token、直角、硬阴影、间距、组件与交互）；根目录 `AGENTS.md` 提供 Codex 读取入口
- UI 目标为新野兽派：纸白底、纯黑边框、直角、无模糊硬阴影与鲜艳实色强调；替代旧杏橙大圆角方案。设计图在 `design/`（已 gitignore，不要提交），旧图仅作内容与结构参考
- Room 当前 version = 8：7→8 新增版本化学习聚合和日历完成时间、科目、重复课程关联；旧任务完成时间保持未知。任何实体字段变更都必须升版本并写 `Migration`，禁止 `fallbackToDestructiveMigration`
- API Key 只通过 `ApiKeyStore`/`BuildConfig.AI_API_KEY` 使用，禁止硬编码或提交 `local.properties`

## 已知坑
- 联想模拟器 `screencap` 只返回白屏，视觉验证看模拟器窗口实际渲染；`uiautomator dump` 仅辅助核对层级与文本
- 模拟器上的测试数据会污染统计结果；需要时先备份数据库再清理
- 应用内任务标题在 `uiautomator` 中可能拿不到 text，交互定位用时长文本或坐标
