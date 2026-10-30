# 星页 · StudyPlanner

<img src="docs/images/xingye.png" alt="星页的星星书页精灵图标" width="180">

**把想做的学习，落到今天。** 星页是一款 Android 学习计划应用：用 AI 对话梳理目标，预览后把计划写入日历，再用课表、专注计时、提醒和复盘跟踪执行。界面采用新野兽派风格，启动图标是一只星星书页精灵。

[下载最新版本](https://github.com/fanwu-zzz/xingye/releases/latest) · [功能清单](docs/FEATURE_BACKLOG.md) · [更新日志](CHANGELOG.md) · [问题反馈](https://github.com/fanwu-zzz/xingye/issues)

> 当前为公开测试版。请先备份重要学习数据；AI 功能需要自行配置 DeepSeek API Key，调用可能产生服务商费用。

## 功能

| 模块 | 功能 | 状态 |
|------|------|------|
| 日历 | 按天浏览（日期条横向滑动）、下拉月历、添加 / 编辑 / 删除事件（考试、课程、学习任务） | 已实现 |
| 任务完成 | 主界面左滑标记完成，已完成任务在当日列表底部单独分组 | 已实现 |
| AI 对话 | 多轮会话、会话列表、新建对话命名、历史消息本地保存 | 已实现 |
| 计划生成 | 根据对话生成结构化计划，可预览、改组名、确认后写入日历 | 已实现 |
| 统计 | 周/月计划时长、完成率、每日计划图表、计划分组与整组删除；课程不计入 | 已实现 |
| 任务页 | 搜索、待办/今天/逾期/已完成/全部、类型与计划筛选、详情与编辑 | 已实现 |
| 数据存储 | 日历、会话、消息保存在本机，API Key 在本机加密存储 | 已实现 |
| 课表 | 周时间网格、重叠课程并列、学期/节次、单双周重复课程、单次/后续/整门调整、Excel 规则导入 | 已实现 F05 |
| 专注 | 自定义时长、关联任务、暂停恢复、休息、后台通知、专注记录及实际时长统计 | 已实现 F01 |
| 日程提醒 | 任务/考试及课程提醒、提前时间、静默时段、日程变更联动、通知跳转 | 已实现 F03 |
| 每周目标 | 历史生效版本、计划关联、实际专注进度、暂停/归档 | 已实现 F02 |
| 手动备份 | 系统文件选择器、校验预览、事务合并/跳过/覆盖、关联恢复与去重；排除密钥 | 已实现 F04 |
| 每周复盘 | 本地真实周报、确认后发送 AI、可编辑建议草稿、冲突检查后确认应用 | 已实现 F06 |

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

- `compileSdk = 36`，`targetSdk = 36`，`minSdk = 26`（Android 8.0+）；构建需要 JDK 17 或更高版本。

## 下载与安装

到 [Releases](https://github.com/fanwu-zzz/xingye/releases) 下载标注版本的 `xingye-*.apk`，核对同页 SHA-256 后安装。下载来源请认准本仓库。

包名为 `com.fanwu.studyplanner`。正式 Release 使用独立签名；如果设备上已安装此前由 Android Studio 构建的调试版，签名不同，Android 不允许直接覆盖。请先在旧版中导出备份，再按 [安装与数据迁移说明](docs/RELEASE.md#从调试版迁移)操作，避免丢失数据。后续正式 Release 使用同一签名，可覆盖升级。

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
2. 安装 Android SDK 36，在**项目根目录**创建 `local.properties`（已在 `.gitignore` 中），填写本机 SDK 路径：

```properties
sdk.dir=D\:\\Android\\Sdk
# 可选：仅本地调试使用；不要在公开或共享构建中填写自己的密钥
# AI_API_KEY=你的 DeepSeek API Key
```

3. 等待 Gradle Sync 完成。
4. 命令行编译并安装到模拟器：

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 IDE 运行。API Key 可以在应用「我的」页里填写，无需重新编译。正式 Release 不包含任何编译期默认密钥。

## 数据与迁移

- Room 当前版本为 **8**；5→6 增加专注记录，6→7 增加提醒配置/凭据，7→8 增加目标/学期/复盘聚合及完成时间、科目和重复课程关联字段，保留原有数据。旧任务不伪造完成时间。修改实体字段必须补迁移，禁止破坏性迁移。
- 计划生成后写入的事件会带上 `planName`（计划组名）；未带 `planName` 的事件视为「未分组」。

## API Key 安全

- `local.properties` 已加入 `.gitignore`，**不会提交到仓库**。
- 本机 Debug 构建可选通过 `BuildConfig.AI_API_KEY` 注入；**Release 构建始终将其置空**。建议在「我的」页填写，Key 由 `ApiKeyStore` 使用 Android Keystore 加密保存在本机。
- Android 系统备份和设备迁移会排除加密密钥偏好文件；应用内手动 JSON 备份也不包含密钥。换机后需要重新填写。AI 请求会把所需内容发送给 DeepSeek，详见 [数据与隐私说明](docs/PRIVACY.md)。
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
- [x] 课表模块（学期、节次、重复规则、周网格）
- [x] 周目标、手动备份恢复、本地与 AI 学习复盘
- [x] 第一版学习规划智能体：连续工具调用、执行反馈、草稿校验、状态恢复和手动维护的学习偏好；日历写入仍需预览确认，详见 [实现说明](docs/AI_AGENT.md)。
- [ ] 界面细节打磨与真机验证

## 设计文档

需求、技术选型、数据模型和 AI 工具调用协议见 [docs/DESIGN.md](docs/DESIGN.md)。

UI 结构与验证方式见 [UI 重构说明](docs/UI_REFACTOR.md)，新增界面对应的待实现需求、优先级与验收标准见 [功能待实现清单](docs/FEATURE_BACKLOG.md)。设计规范以 [.trae/rules/ui_style.md](.trae/rules/ui_style.md) 为准。

F01 的计时口径、后台行为、权限降级与测试方式见 [专注计时说明](docs/F01_FOCUS.md)。

F03 的静默、去重、权限降级、通知跳转与验证方式见 [日程提醒说明](docs/F03_REMINDERS.md)。

F02、F04—F06 的数据结构、恢复策略、重复课程例外、AI 确认流程与验证见 [学习功能说明](docs/F02_F06_LEARNING_FEATURES.md)。设备测试仅运行 `.\gradlew.bat connectedFocusTestAndroidTest`，使用独立 `com.fanwu.studyplanner.f01test` 包；禁止用测试驱动卸载日常应用。

## 参与与授权

问题和建议请使用 [Issues](https://github.com/fanwu-zzz/xingye/issues)，提交代码前阅读 [贡献说明](CONTRIBUTING.md)。安全问题见 [SECURITY.md](SECURITY.md)。仓库目前**未添加开源许可证**；公开可见不代表自动授予复制、修改或再分发权利。
