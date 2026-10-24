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
- `ui/calendar`、`ui/chat`、`ui/stats`、`ui/tasks`、`ui/settings`、`ui/theme`

## 关键结构
- `MainActivity`：底部三 Tab（日历 `calendar` / 统计 `stats` / 我的 `profile`），子页 `subPage` 管理对话列表、聊天详情、任务页
- `CalendarEventEntity`：`id, title, date(YYYY-MM-DD), startTime/endTime(HH:mm), type(EXAM/CLASS/STUDY_TASK), note, isDone, planName`
- `planName` 标记事件属于哪个 AI 计划组，为 null 表示未分组；整组删除按 `planName` 删除
- AI 计划流程：会话 → 生成计划 JSON → `PlanPanelDialog` 预览/改组名 → 应用时写入 `calendar_events` 并带 `planName`
- 统计页：待完成任务按计划分组（可左滑整组删除）、本周概览、每日时长柱状图（Compose 自绘，无图表库）

## 约定
- UI 文案、注释、提交信息均用中文
- 改任何 UI 前先阅读 `.trae/rules/ui_style.md`（设计规范：配色 token、圆角、间距、组件范式）
- 配色：奶白底 `#FAFAF7`、杏橙 `#F6C08B`、深灰底栏；设计图在 `design/`（已 gitignore，不要提交）
- Room 当前 version = 5：任何实体字段变更都必须升版本并写 `Migration`，禁止 `fallbackToDestructiveMigration`
- API Key 只通过 `ApiKeyStore`/`BuildConfig.AI_API_KEY` 使用，禁止硬编码或提交 `local.properties`

## 已知坑
- 联想模拟器 `screencap` 只返回白屏，验证界面用 `uiautomator dump` + 数据库查询
- 模拟器上的测试数据会污染统计结果；需要时先备份数据库再清理
- 应用内任务标题在 `uiautomator` 中可能拿不到 text，交互定位用时长文本或坐标
