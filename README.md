# StudyPlanner

一个个人使用的 Android 学习计划助手：**和 AI 聊天 → 生成学习计划 → 一键写入日历**。

> 项目状态：开发中（环境搭建阶段）。当前仅包含 Android 项目骨架（Hello World），功能尚未实现。

## 功能（MVP）

| 模块 | 功能 | 状态 |
|------|------|------|
| 日历 | 月视图浏览，添加 / 编辑 / 删除事件（考试、课程、学习任务） | 计划中 |
| 课表 | 手动录入课程（周几、节次、教室） | 计划中 |
| AI 对话 | 多轮对话、清空历史、上下文传递 | 计划中 |
| 计划生成 | 根据对话生成结构化学习计划，用户确认后生效 | 计划中 |
| 工具调用 | AI 通过 Function Calling 增删改日历事件 | 计划中 |
| 本地存储 | 日历、课表、对话记录、API Key 全部保存在本地 | 计划中 |

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2024.09.00 |
| 构建 | Gradle（Kotlin DSL）+ AGP | 8.12.0 |
| 数据库 | Room | 待接入 |
| 网络 | OkHttp + Retrofit | 待接入 |
| 序列化 | kotlinx.serialization | 待接入 |
| 大模型 | DeepSeek API（Function Calling） | - |

- `compileSdk = 36`，`targetSdk = 36`，`minSdk = 26`（Android 8.0+）

## 项目结构

```
StudyPlanner/
├── app/src/main/java/com/fanwu/studyplanner/
│   ├── MainActivity.kt
│   ├── data/                # 数据层（Room、Retrofit、Repository）
│   ├── domain/              # 业务模型
│   └── ui/                  # 界面层（calendar / chat / plan / settings / theme）
├── docs/
│   └── DESIGN.md            # 需求、架构、数据模型、AI 工具协议
├── build.gradle.kts
├── settings.gradle.kts
└── local.properties         # 不入库，存放 SDK 路径和 API Key
```

## 环境要求

- Android Studio 或 IntelliJ IDEA（已安装 Android 插件）
- Android SDK：Platform 36、Build-Tools、Platform-Tools
- Android 模拟器（API 35 镜像）或真机（Android 8.0+）

## 如何运行

1. 克隆仓库并用 Android Studio / IntelliJ IDEA 打开。
2. 在**项目根目录**创建 `local.properties`（该文件已在 `.gitignore` 中）：

```properties
sdk.dir=D\:\\Android\\Sdk
AI_API_KEY=你的 DeepSeek API Key
```

3. 等待 Gradle Sync 完成。
4. 选择模拟器或已连接的真机，点击运行。

> `local.properties` 中的 `sdk.dir` 由 IDE 自动生成；`AI_API_KEY` 用于后续接入大模型。

## API Key 安全

- `local.properties` 已加入 `.gitignore`，**不会提交到仓库**。
- API Key 通过 Gradle 读取并注入 `BuildConfig`，代码中引用 `BuildConfig.AI_API_KEY`。
- 请勿将密钥硬编码到任何 Kotlin 文件、资源文件或提交历史中。
- 一旦密钥泄露，立即到 DeepSeek 控制台吊销并重新生成。

## 开发计划

- [x] 项目骨架 + Git 初始化
- [ ] 环境验证（模拟器跑通 Hello World）
- [x] Gradle 依赖配置 + Room 数据库
- [x] 日历页面（月视图 + 事件编辑）
- [x] 设置页（API Key 输入）
- [x] AI 对话页 + DeepSeek 接口接入
- [x] 计划生成 + 工具调用 + 写入日历

## 设计文档

需求、技术选型、数据模型和 AI 工具调用协议见 [docs/DESIGN.md](docs/DESIGN.md)。

## License

尚未添加。
