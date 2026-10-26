# 发布与安装

## 获取与校验

只从 [GitHub Releases](https://github.com/fanwu-zzz/xingye/releases) 下载 APK。每个发布页附有 APK 的 SHA-256；Windows 可运行 `Get-FileHash .\xingye-*.apk -Algorithm SHA256`，macOS / Linux 可运行 `shasum -a 256 xingye-*.apk`，比较结果后再安装。

安装需要 Android 8.0（API 26）或更高版本。AI 功能需要在应用「我的」中填写自己的 DeepSeek API Key；公开 APK 不内置任何账户密钥。请先在系统设置中允许对应文件管理器安装 APK。

## 从调试版迁移

Android 只有在包名**和签名**均匹配时才允许覆盖安装。此前由 Android Studio 构建的调试版与公开 Release 使用不同签名，直接安装会报签名冲突。这不是数据库迁移失败。

1. 在旧版「我的」中使用手动备份导出 JSON，确认文件能读取并妥善保存。
2. 记录需要重新设置的 DeepSeek API Key、提醒权限等。手动备份不包含 API Key。
3. 仅在备份确认可用后卸载旧调试版，再安装公开 Release APK。
4. 在新版「我的」中使用备份恢复，先检查预览，再确认导入，并重新配置 API Key 与系统权限。

卸载会清除旧版应用数据。无法完成备份时不要卸载旧版，可继续使用调试版。后续同一发布签名的 Release 可通过 Android 更新机制覆盖安装；升级前仍建议保留备份。

## 维护者发布流程

`master` 的 GitHub Actions 会在 push 和 Pull Request 时运行 `assembleDebug`、`testDebugUnitTest` 与 `assembleRelease`。CI 无本地密钥及签名文件；生成的 Release 构建只用于验证，不能直接作为正式安装包发布。

正式签名文件在 Git 忽略的 `.signing/` 目录中，**私钥和密码必须独立备份**。`.signing/release.properties` 格式：

```properties
storeFile=.signing/xingye-release.jks
storePassword=本地签名库密码
keyAlias=xingye
keyPassword=本地签名密钥密码
```

发布前确认版本号与 `CHANGELOG.md` 一致，运行 `./gradlew assembleRelease testDebugUnitTest`，用 `apksigner verify --print-certs` 检查签名，用 `aapt dump badging` 检查包名和版本。核对 Release 构建生成的 `BuildConfig.AI_API_KEY` 为空，并确认 APK 不含本机密钥。计算 SHA-256，创建同版本 Git tag 与 GitHub Release，上传该签名 APK并在说明中写入 SHA-256、已知限制和调试版签名迁移提醒。

不要把调试版 APK、签名文件、密码、`local.properties` 或含个人数据的备份上传到仓库或 Release。设备验收只能使用独立测试包；不得通过测试驱动清除日常应用的数据。
