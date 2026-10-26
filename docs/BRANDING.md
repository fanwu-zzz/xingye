# 星页品牌与启动图标

用户于 2026-10-26 选择 A「星页」。图形沿用星星、打开的书页与青色书签，采用二次元插画。

## 名称与兼容性

- 正式 App 显示名称：**星页**，来自 `res/values/strings.xml` 的 `app_name`。
- 独立验收包显示名称：**星页（测试）**，方便与日常应用区分。
- 桌面、设置页品牌文案、锁屏提醒标题与导出文件名前缀同步使用新名称。
- Android applicationId 仍为 `com.fanwu.studyplanner`，工程目录、Kotlin 包名、数据库名及备份格式保持兼容；旧 JSON 备份仍可读取。同签名构建可以覆盖升级；从旧调试版迁移到公开 Release 时签名不同，需先导出备份，详见 [发布与安装](RELEASE.md#从调试版迁移)。

## 正式资源

- [透明图标前景](../app/src/main/res/drawable-nodpi/xingye_launcher_foreground.png)：由内置 image_gen 从选定 A 图提取透明背景并保留留白，1254×1254 PNG；未使用 CLI 或额外 API Key。
- `res/drawable/xingye_launcher_foreground_safe.xml`：在 108dp 自适应画布上增加 10dp 安全留白。透明像素抽样检查显示，图形最远点距中心约 32.46dp，位于半径 33dp 的安全区内。
- `res/values/colors.xml`：背景 `xingye_launcher_background = #001A4D`。
- `res/mipmap-anydpi-v26/ic_launcher.xml` 及 `ic_launcher_round.xml`：Android 8.0+ 自适应图标；本项目 minSdk 26，系统桌面负责决定遮罩形状。
- `res/mipmap-anydpi-v33/` 同名资源：增加 Android 13+ 单色主题图标，使用 `res/drawable/xingye_launcher_monochrome.xml` 的星星/书页矢量轮廓。
- 应用图标与圆形图标均已在 Manifest 中引用。旧日历形启动图标资源已替换。

二次元插画与深蓝底是用户选定的品牌图标；页面视觉继续遵循唯一规范 `.trae/rules/ui_style.md`。

## 验证

- `assembleDebug`、`assembleFocusTest` 编译通过，现有 45 项 JVM 单元测试通过。
- AAPT 检查正式 APK 的 applicationId、application-label、启动 Activity label 及自适应图标资源引用；正式包名称为「星页」。
- Android 9 / API 28 联想模拟器安装独立 `.f01test` 包，实际检查桌面图标、「星页（测试）」标签和设置页底部品牌文案。验收完成后已移除独立测试包。Android 13+ 单色资源已通过编译，尚未在 Android 13+ 真机实测。
- 设备验收前只读备份日常数据库与 WAL 到 Git 忽略的 `app/build/device-backups/xingye-brand-before/`；只操作独立测试包。日常应用的安装时间及数据库 SHA-256 在验收前后相同。

## 完整图像处理提示词

模式：内置 image_gen，编辑 A 原图，`transparent_background=true`。原始候选及其提示词存档于 `design/branding/2026-10-26-anime-options-v1/`。

```text
Use case: background-extraction. Edit the referenced ORIGINAL selected icon A for the Android app 星页. Preserve the EXACT same book-and-star mascot identity and drawing: smiling yellow four-point star with two navy oval eyes and tiny pink cheek marks, open white pages shaped like wings, cyan/blue covers, cyan ribbon bookmark, dark ink outlines and existing cel shading. Remove ONLY the entire dark navy background and replace it with genuine transparent alpha. Do not redraw or reinterpret the character, do not add anything. Place the COMPLETE unchanged mascot, proportionally scaled down, at the center of a square 1024x1024 transparent canvas. Important Android adaptive-icon safe area: the mascot including ribbon must fit entirely inside x=230..794 and y=230..794 (central 55% square), with very generous fully transparent padding on all four sides. No cropping, no white matte, no checkerboard pixels, no shadow outside the mascot, no outer border, no text, no new stars. Output a crisp transparent PNG foreground layer, not an app mockup.
```
