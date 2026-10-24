# StudyPlanner

一个个人使用的 Android 学习计划助手：**和 AI 聊天 → 生成学习计划 → 一键写入日历 → 跟踪完成情况**。

> 项目状态：核心流程已实现，处于功能迭代阶段（日历、对话、计划生成、统计、任务管理均可运行）。

## 功能

| 模块 | 功能 | 状态 |
|------|------|------|
| 日历 | 按天浏览（日期条横向滑动）、下拉月历、添加 / 编辑 / 删除事件（考试、课程、学习任务） | 已实现 |
| 任务完成 | 主界面左滑标记完成，已完成任务在当日列表底部单独分组 | 已实现 |
| AI 对话 | 多轮会话、会话列表、新建对话命名、历史消息本地保存 | 已实现 |
| 计划生成 | 根据对话生成结构化计划，可预览、改组名、确认后写入日历 | 已实现 |
| 统计 | 待完成任务按计划分组（支持整组删除）、本周概览、每日学习时长柱状图 | 已实现 |
| 任务页 | 全部待办按截止时间排序，可勾选完成，剩 N 天分级徽章 | 已实现 |
| 本地存储 | 日历、会话、消息、API Key 全部保存在本机 | 已实现 |
| 课表 | 手动录入课程（周几、节次、教室） | 未开始 |

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2024.09.00 |
| 构建 | Gradle（Kotlin DSL）+ AGP | 8.12.0 |
| 数据库 | Room（KSP） | 2.6.1 |
| 网络 | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| 序列化 | kotlinx.serialization | 1.7.3 |
| 大模型 | DeepSeek API | - |

- `compileSdk = 36`，`targetSdk = 36`，`minSdk = 26`（Android 8.0+），JDK 11

## 项目结构

```
StudyPlanner/
├── app/src/main/java/com/fanwu/studyplanner/
│   ├── MainActivity.kt          # 底部三 Tab + 子页切换
│   ├── data/
│   │   ├── local/               # Room 实体、DAO、AppDatabase、ApiKeyStore
│   │   ├── remote/              # DeepSeek 接口与 DTO
│   │   └── repository/          # CalendarRepository、ChatRepository
│   ├── domain/                  # CalendarEvent、Conversation、ChatMessage、StudyPlan
│   └── ui/                      # calendar / chat / stats / tasks / settings / theme
├── docs/DESIGN.md               # 需求、架构、数据模型、AI 工具协议
├── design/                      # UI 设计图（不入库）
├── gradlew / gradlew.bat        # Gradle Wrapper
└── local.properties             # 不入库，存放 SDK 路径和 API Key
```

## 如何运行

1. 克隆仓库并用 Android Studio / IntelliJ IDEA 打开。
2. 在**项目根目录**创建 `local.properties`（已在 `.gitignore` 中）：

```properties
sdk.dir=D\:\\Android\\Sdk
AI_API_KEY=你的 DeepSeek API Key
```

3. 等待 Gradle Sync 完成。
4. 命令行编译并安装到模拟器：

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 IDE 运行。API Key 也可以在应用「我的」页里填写，无需重新编译。

## 数据与迁移

- Room 当前版本为 **4**；修改实体字段必须升版本并补 `Migration`，不要使用破坏性迁移。
- 计划生成后写入的事件会带上 `planName`（计划组名）；未带 `planName` 的事件视为「未分组」。

## API Key 安全

- `local.properties` 已加入 `.gitignore`，**不会提交到仓库**。
- 编译期通过 `BuildConfig.AI_API_KEY` 注入；运行时也可在「我的」页保存，Key 由 `ApiKeyStore` 用 Android Keystore 加密存在本机。
- 请勿将密钥硬编码到任何 Kotlin 文件、资源文件或提交历史中。
- 一旦密钥泄露，立即到 DeepSeek 控制台吊销并重新生成。

## 开发计划

- [x] 项目骨架 + Git 初始化
- [x] Gradle 依赖配置 + Room 数据库
- [x] 日历页面（日期条 + 月历 + 事件编辑）
- [x] AI 对话（会话化 + DeepSeek 接入）
- [x] 计划生成、预览、应用写入日历
- [x] 统计页（计划分组 + 周概览 + 每日时长）
- [x] 任务页（截止排序 + 完成勾选 + 剩余天数）
- [ ] 课表模块
- [ ] 界面细节打磨与真机验证

## 设计文档

需求、技术选型、数据模型和 AI 工具调用协议见 [docs/DESIGN.md](docs/DESIGN.md)。

## License

尚未添加。
