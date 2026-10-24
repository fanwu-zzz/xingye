# StudyPlanner UI 设计规范

本文件是 UI 改动的唯一风格基准。任何界面调整（新增页面、改布局、加组件、调色）都必须先对齐本规范；
与 `design/` 下的设计稿冲突时，以本文件的设计 token 为准，设计稿只作为构图参考。

## 1. 主题

- 全局固定浅色主题，**关闭动态取色**，不跟随系统深色模式（`StudyPlannerTheme(dynamicColor = false)`）
- 页面底色统一 `CreamBackground #FAFAF7`，卡片用纯白
- 不使用 Material 默认紫色系；`DarkColorScheme` 中的紫色仅占位，不新增深色界面

## 2. 颜色 Token（定义在 `ui/theme/Color.kt`）

| 名称 | 值 | 用途 |
|---|---|---|
| `CreamBackground` | `#FAFAF7` | 页面底色 |
| `OrangePrimary` | `#E08E45` | 主强调：选中态、主 CTA、勾选圆点 |
| `ApricotOrange` | `#F6C08B` | 杏橙：摘要卡、柱状图、次按钮/胶囊按钮 |
| `DarkBar` | `#4A4440` | 深棕：标题、深色文字、选中日期 |
| `NavDark` | `#3E3A36` | 底部导航栏底色 |
| 卡片白 | `#FFFFFF` | 列表卡片、弹窗 |
| 次级文字灰 | `#9A9490` / `#B3AEA6` | 副标题、说明文字 |
| 箭头/边框灰 | `#C9C4BE` | 列表尾部箭头、未选中描边 |
| 分隔线 | `#DDD9D3` | 分组分割线 |
| 完成态底色 | `#F4F2ED` | 已完成任务卡片 |
| 危险/提醒 | `#E0524A` | 删除、逾期提示 |
| 对话入口 chip | 底 `#F8F6FD` / 边 `#ECE9F6` / 图标 `#5B6478` | 主界面 AI 对话入口 |

事件类型色（日历、计划预览）：

- `EXAM` `#E57373`
- `CLASS` `#64B5F6`
- `STUDY_TASK` `#81C784`（计划弹窗内用 `#A8C3A0`）

规则：**新颜色必须先加到 `Color.kt` 并登记到本表**，禁止在页面里散落新的 `Color(0xFF...)`。
历史页面里已存在的局部常量（如日历页的 `CalendarDateGray`）可保留，但不再扩散。

## 3. 圆角

| 元素 | 圆角 |
|---|---|
| 主卡片 / 统计卡片 / 弹窗 | 20dp |
| 会话、任务列表卡片 | 18dp |
| 对话气泡、小标签、行内按钮 | 12dp |
| 主按钮 / 胶囊按钮 | 24dp |
| 日期胶囊、小方块 | 8–10dp |
| 底部导航 / 展开面板顶角 | 28dp（仅顶部两角） |

## 4. 阴影与描边

- 卡片：白底 + `shadowElevation = 2.dp`（列表）或 `tonalElevation = 1–2.dp`（弹窗），不做重投影
- 浅色底组件（chip、按钮）用 `1.dp` 浅色描边代替阴影
- 不使用渐变作为大面积背景（现有页面均为纯色底）

## 5. 间距（8pt 网格）

- 页面水平外边距：20dp
- 列表内卡片间距：12dp；列表上下留白 8/24dp
- 卡片内边距：横向 18dp、纵向 16dp
- 顶部栏纵向留白 10dp；标题与首个内容块间距 10–16dp
- 区块之间 16–24dp

## 6. 字体

| 层级 | 样式 |
|---|---|
| 页面标题 | `headlineSmall` / `titleLarge`，`FontWeight.Bold`，`DarkBar` |
| 卡片标题 | `titleMedium`，`FontWeight.SemiBold`，`DarkBar` |
| 副标题 / 说明 | `bodySmall`，次级文字灰 |
| 正文 | `bodyMedium` |
| 按钮文字 | `titleMedium`，`FontWeight.Medium` |

不使用自定义字体，统一 Material 3 默认 Typography。

## 7. 组件范式

- **顶部栏**：左侧 `IconButton` 返回（`DarkBar`），中间标题居中，右侧留 48dp 占位保持对称
- **主 CTA**：全宽、杏橙底、深棕文字、`RoundedCornerShape(24.dp)`、高 58dp、无阴影
- **列表项**：白卡（圆角 18、白底、2dp 阴影），左侧标题 + 副标题，右侧 20dp 浅灰箭头
- **空状态**：居中 `bodyMedium` 灰字，多行文本居中，不用插画
- **弹窗**：白底 20dp 圆角，高度上限 78% 屏高，列表区独立滚动、底部按钮固定，打开时收起键盘
- **底部导航**：三 Tab（日历 / 统计 / 我的），深色底 `NavDark`，选中态用杏橙
- **图表**：Compose 自绘（柱状图、进度条），主色 `OrangePrimary` / `ApricotOrange`，不引第三方图表库
- **图标**：优先 Material Icons Extended；自定义图标用 Canvas 自绘，线宽 1.6dp、圆角端点，风格统一为线性

## 8. 交互约定

- 破坏性操作（删除计划组、清空）必须二次确认；完成/取消完成等轻量操作不加确认
- 列表左滑露出操作层，底层不与卡片同色以免误判
- 日历展开面板：点击未覆盖区域收起，不做上滑手势
- AI 生成/导入类操作需二次确认，避免误触

## 9. 改 UI 的标准流程

1. 若 `design/` 有对应设计稿，先看图确认构图与层级
2. 复用第 2–7 节的 token 与组件范式，不引入新风格
3. 需要新颜色/新圆角时，先落 `Color.kt` 与本文件，再写页面
4. `.\gradlew.bat assembleDebug` 编译通过后装模拟器验证
5. 联想模拟器 `screencap` 返回白屏，界面校验用 `uiautomator dump` 看层级与文本
6. 视觉规范有变化时同步更新本文件
