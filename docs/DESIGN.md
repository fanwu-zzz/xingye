# StudyPlanner 设计文档

> 本文档记录需求范围、技术选型、数据模型和 AI 工具调用协议，作为编码阶段的依据。

## 1. 需求

### 1.1 软件定位

个人使用的 Android 学习计划助手。用户与 AI 进行几轮对话后点击"生成计划"，AI 总结对话并给出结构化学习计划；用户确认后，AI 通过工具调用把计划写入本地日历。

### 1.2 功能清单（MVP）

| # | 模块 | 功能点 |
|---|------|--------|
| 1 | 日历 | 月视图网格；点击日期查看 / 编辑事件；事件包含标题、日期、开始 / 结束时间、类型、备注 |
| 2 | 课表 | 手动录入课程：周几、节次、教室；支持查看 |
| 3 | AI 对话 | 多轮对话界面；支持清空历史；对话上下文完整传给模型 |
| 4 | 计划生成 | 点击"生成计划"→ AI 总结对话 → 输出结构化计划 → 用户确认 |
| 5 | 工具调用 | 用户确认后，AI 通过 Function Calling 调用添加 / 修改 / 删除事件 |
| 6 | 本地存储 | 日历事件、课表、对话历史、API Key 均保存在手机本地 |

### 1.3 非功能需求

- 仅支持 Android（`minSdk 26`，Android 8.0 及以上）。
- 除调用 AI 接口外，所有功能离线可用。
- 开源友好：API Key 不进入版本库。
- 单用户、单设备，不做账号体系。

### 1.4 本期明确不做

- 多用户、云同步、跨设备同步
- iOS / 网页版
- 打卡社区、好友监督
- 基于遗忘曲线的自动复习排程（后续迭代再考虑）
- 复杂 `.ics` 课表导入（先手动录入）

## 2. 技术选型

| 层 | 技术 | 说明 |
|----|------|------|
| 语言 | Kotlin 2.0.21 | Android 官方推荐 |
| UI | Jetpack Compose + Material 3 | 声明式 UI，状态驱动 |
| 构建 | Gradle Kotlin DSL + AGP 8.12.0 | 当前项目已使用 |
| 数据库 | Room | SQLite 封装，编译期校验 SQL |
| 网络 | OkHttp + Retrofit | 调用 DeepSeek API |
| JSON | kotlinx.serialization | 类型安全，配合 Retrofit Converter |
| 架构 | MVVM + Repository | UI 与数据分离 |
| 大模型 | DeepSeek API（`deepseek-chat`） | OpenAI 兼容接口，原生支持 Function Calling |

## 3. 架构

```
app/src/main/java/com/fanwu/studyplanner/
├── MainActivity.kt
├── data/
│   ├── local/          # Room：Entity、DAO、Database
│   ├── remote/         # Retrofit 接口、DeepSeek 请求 / 响应模型
│   └── repository/     # 协调本地与远程数据
├── domain/             # 纯业务模型（不依赖 Android）
└── ui/
    ├── theme/          # 主题、颜色、字体
    ├── calendar/       # 日历页面
    ├── chat/           # AI 对话页面
    ├── plan/           # 计划确认页面
    └── settings/       # 设置页（API Key）
```

数据流：`UI → ViewModel → Repository → Room / Retrofit`，UI 只观察 ViewModel 暴露的状态。

## 4. 数据模型

```kotlin
enum class EventType {
    EXAM,        // 考试
    CLASS,       // 课程
    STUDY_TASK   // 学习任务
}

data class CalendarEvent(
    val id: Long = 0,
    val title: String,
    val date: String,          // YYYY-MM-DD
    val startTime: String,     // HH:mm
    val endTime: String,       // HH:mm
    val type: EventType,
    val note: String? = null
)

data class ChatMessage(
    val id: Long = 0,
    val role: String,          // "user" / "assistant" / "system"
    val content: String,
    val timestamp: Long
)

data class StudyPlan(
    val events: List<CalendarEvent>,
    val summary: String        // AI 对计划的说明
)
```

存储约定：

- 日期和时间以字符串存储（`YYYY-MM-DD`、`HH:mm`），便于排序和按日期查询。
- `EventType` 在 Room 中通过 `TypeConverter` 转成字符串。
- `CalendarEvent` 落库时作为 `@Entity`，`id` 自增。

## 5. AI 工具调用协议

模型可调用的工具：

| 工具名 | 功能 | 参数 |
|--------|------|------|
| `add_event` | 添加日历事件 | `title, date, startTime, endTime, type, note?` |
| `update_event` | 修改已有事件 | `eventId, updates{title?, date?, startTime?, endTime?, type?, note?}` |
| `delete_event` | 删除事件 | `eventId` |
| `get_events_by_date` | 查询某天事件（避免时间冲突） | `date` |

模型返回的工具调用示例：

```json
{
  "tool_calls": [
    {
      "id": "call_1",
      "type": "function",
      "function": {
        "name": "add_event",
        "arguments": "{\"title\":\"复习线性代数\",\"date\":\"2026-10-25\",\"startTime\":\"19:00\",\"endTime\":\"20:30\",\"type\":\"STUDY_TASK\"}"
      }
    }
  ]
}
```

执行流程：

1. 用户与模型多轮对话。
2. 用户点击"生成计划"，App 把对话历史 + 当前日历摘要发给模型。
3. 模型返回计划文本和工具调用指令。
4. App 展示计划预览，**等待用户确认**。
5. 用户确认后，App 逐条执行工具调用并写库；用户取消则丢弃。
6. 执行结果（成功 / 失败条数）反馈给用户。

安全约束：

- 模型不直接操作数据库，只产生指令。
- 任何写操作必须经过用户确认。
- 执行前校验日期、时间格式和事件冲突。

## 6. API Key 安全方案

`local.properties`（已在 `.gitignore` 中）：

```properties
sdk.dir=D\:\\Android\\Sdk
AI_API_KEY=sk-xxxxxxxx
```

`app/build.gradle.kts` 读取并注入 `BuildConfig`：

```kotlin
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        buildConfigField(
            "String",
            "AI_API_KEY",
            "\"${localProperties.getProperty("AI_API_KEY") ?: ""}\""
        )
    }
}
```

代码中通过 `BuildConfig.AI_API_KEY` 引用。同时建议在设置页提供手动输入 Key 的入口，存入 `EncryptedSharedPreferences` 或 Room，避免必须重新编译。

## 7. 实现顺序

1. [x] 配置 Gradle：Room、Retrofit、OkHttp、kotlinx.serialization、ViewModel
2. [x] 定义 `domain` 数据模型
3. [x] Room 层：Entity、DAO、Database、TypeConverter
4. [x] API Key 注入 `BuildConfig` + 设置页
5. [x] 日历页：月视图 + 事件增删改
6. [x] DeepSeek 接口 + 对话页
7. [x] 计划生成、确认、工具调用写库（MVP 先实现 `add_event`）

## 8. 待定事项

- 课表是否在 MVP 中实现，或放到日历事件之后。
- 事件时间冲突的处理策略（提示 / 阻止 / 允许）。
- 对话历史是否需要按会话分组，还是只保留一条连续会话。
- 开源 License 的选择（MIT / Apache-2.0）。
