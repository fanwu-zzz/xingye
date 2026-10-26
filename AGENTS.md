# StudyPlanner 项目指引

本指引适用于本仓库及所有子目录。

## UI 设计规则（每次 UI 任务必须读取）

在新增或修改页面、组件、布局、配色、排版、图标、导航、交互状态或动画之前，必须先完整阅读 [UI 设计规范](.trae/rules/ui_style.md)，并按该文件实施与自检。

- 唯一规范路径：`.trae/rules/ui_style.md`。不要另建一份容易失同步的 UI 规范。
- 当前目标风格：新野兽派 / Neo-Brutalist，适配 Android Jetpack Compose。
- 该规范取代旧的奶白杏橙、大圆角与柔和阴影方案；`design/` 旧图只作为内容和结构参考。
- 新增的共享主题 token 和组件必须沿用该规范；规范变更同步更新原文件。

## 项目上下文

StudyPlanner 是 Kotlin / Jetpack Compose Android 学习计划应用，核心流程为 AI 对话、生成计划、预览应用到日历、跟踪任务与统计。现有技术与业务约定见 `.trae/rules/project_rules.md`，具体代码与用户当前明确要求优先于过时描述。

涉及 Kotlin / Compose 代码时使用 `.\gradlew.bat assembleDebug` 验证；仅修改 Markdown 时检查文档和 diff，不要求编译。保留已有用户修改，不硬编码或输出 API Key，不提交 `local.properties`。

## 设备测试与数据保护

设备测试使用 `.\gradlew.bat connectedFocusTestAndroidTest`，其独立应用包为 `com.fanwu.studyplanner.f01test`。日常应用 `com.fanwu.studyplanner` 可能保存用户数据，禁止通过测试驱动卸载或清空它。安装、迁移或运行设备测试前，先确认目标包名、卸载/清理策略和数据备份；不能仅凭测试代码只清理自身记录就假定驱动会保留应用数据。
