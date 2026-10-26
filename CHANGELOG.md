# 更新日志

遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)，测试版可能调整数据和交互；升级前请导出备份。

## 1.0.0-beta.1（2026-10-26）

- 首个公开测试版，应用名称与图标更新为「星页」。
- 完成 AI 对话、计划生成与预览应用、日历、任务、统计和 Excel 课表导入。
- 接入专注计时、提醒、每周目标、手动备份、学期与重复课程、每周复盘。
- Release APK 不内置开发者 API Key；系统备份排除设备绑定的加密密钥文件。
- 增加 GitHub Actions 构建与单元测试、安装和签名迁移说明。

已知限制：AI 功能需要用户自行提供 DeepSeek API Key；提醒的实际送达仍受系统通知、精确闹钟和省电策略影响。设备测试覆盖范围及功能后续增强见 [功能清单](docs/FEATURE_BACKLOG.md)。
