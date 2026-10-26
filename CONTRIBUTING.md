# 贡献说明

欢迎在 Issues 报告可复现的问题和提出功能建议。提交问题时请写明 Android 版本、星页版本、复现步骤、预期与实际结果；请先去除截图、日志或备份中的个人信息和 API Key。

贡献代码请从 `master` 创建分支并发起 Pull Request，说明改动、验证方式与数据迁移影响。涉及 Kotlin / Compose 的改动至少运行 `./gradlew assembleDebug testDebugUnitTest`。涉及设备测试时，只能使用独立的 `connectedFocusTestAndroidTest` / `com.fanwu.studyplanner.f01test`，不要清空日常应用数据。

修改 UI 前完整阅读 [.trae/rules/ui_style.md](.trae/rules/ui_style.md)。不要提交 `local.properties`、`.signing/`、真实课表、数据库、日志或密钥。安全问题请按 [SECURITY.md](SECURITY.md) 反馈。

本仓库暂未添加开源许可证。代码贡献的授权方式在接受贡献前需由维护者与贡献者明确约定。
