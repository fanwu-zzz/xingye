package com.fanwu.studyplanner.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.ApiKeyStore
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.remote.DeepSeekClient
import com.fanwu.studyplanner.data.repository.ChatRepository
import com.fanwu.studyplanner.data.schedule.ScheduleExpander
import com.fanwu.studyplanner.data.schedule.XlsxReader
import com.fanwu.studyplanner.data.schedule.managedScheduleImport
import com.fanwu.studyplanner.data.repository.LearningRepository
import com.fanwu.studyplanner.data.repository.CoursePreview
import com.fanwu.studyplanner.domain.ChatMessage
import com.fanwu.studyplanner.domain.Conversation
import com.fanwu.studyplanner.domain.ConversationMode
import com.fanwu.studyplanner.domain.ScheduleImport
import com.fanwu.studyplanner.domain.StudyPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 一份生成好的计划：所属会话、组名、是否已写入日历 */
data class PlanDraft(
    val conversationId: Long,
    val plan: StudyPlan,
    val name: String,
    val applied: Boolean = false,
    /** 应用前是否先删除同名计划组，课表重复导入时避免产生重复事件 */
    val replaceExisting: Boolean = false,
    val coursePreview: CoursePreview? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val appContext: Context
) : ViewModel() {

    /** 会话列表（新的在前） */
    val conversations: StateFlow<List<Conversation>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前打开的会话 id，null 表示停留在会话列表 */
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId

    /** 当前会话对象，用于判断是「学习计划」还是「导入课表」 */
    val currentConversation: StateFlow<Conversation?> =
        combine(conversations, _currentConversationId) { list, id ->
            list.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<ChatMessage>> = _currentConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    /** 当前正在预览的计划（弹窗打开时非空） */
    private val _pendingPlan = MutableStateFlow<PlanDraft?>(null)
    val pendingPlan: StateFlow<PlanDraft?> = _pendingPlan

    /** 最近一次生成的计划，关闭弹窗后仍然保留，可随时重新打开查看 */
    private val _latestPlan = MutableStateFlow<PlanDraft?>(null)
    val latestPlan: StateFlow<PlanDraft?> = _latestPlan

    /** 新建一轮对话并直接进入 */
    fun createConversation(name: String, mode: ConversationMode = ConversationMode.STUDY_PLAN) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _currentConversationId.value = repository.createConversation(trimmed, mode)
        }
    }

    fun openConversation(id: Long) {
        _currentConversationId.value = id
        _error.value = null
        _notice.value = null
    }

    /** 删除一轮对话及其消息；若正在查看该会话则退回列表 */
    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
                _pendingPlan.value = null
                _latestPlan.value = null
            }
        }
    }

    fun backToList() {
        _currentConversationId.value = null
        _error.value = null
        _notice.value = null
    }

    fun currentConversationName(): String =
        conversations.value.firstOrNull { it.id == _currentConversationId.value }?.name
            ?: "学习计划"

    fun send(text: String) {
        val conversationId = _currentConversationId.value ?: return
        val content = text.trim()
        if (content.isEmpty() || _isSending.value) return
        val importMode = currentConversation.value?.mode == ConversationMode.IMPORT_SCHEDULE

        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            _notice.value = null
            if (importMode) {
                // 导入课表模式下，用户输入都是对 AI 追问的回答，继续走解析流程
                repository.importSchedule(conversationId, content)
                    .onSuccess { handleImportResult(conversationId, it) }
                    .onFailure { _error.value = it.message ?: "导入失败" }
            } else {
                repository.sendMessage(conversationId, content)
                    .onFailure { _error.value = it.message ?: "发送失败" }
            }
            _isSending.value = false
        }
    }

    /** 选择课表文件后：本地转成纯文本，再交给 AI 识别 */
    fun importScheduleFile(uri: Uri, fileName: String) {
        val conversationId = _currentConversationId.value ?: return
        if (_isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            _notice.value = null

            val fileText = runCatching {
                withContext(Dispatchers.IO) {
                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("无法读取这个文件")
                    input.use { XlsxReader.promptText(XlsxReader.read(it), fileName) }
                }
            }

            fileText.onFailure {
                _error.value = it.message ?: "读取文件失败"
            }.onSuccess { text ->
                repository.importSchedule(conversationId, text)
                    .onSuccess { handleImportResult(conversationId, it) }
                    .onFailure { _error.value = it.message ?: "导入失败" }
            }
            _isSending.value = false
        }
    }

    /** 处理一次课表解析结果：缺参数就提示用户，识别完成就生成待预览的计划 */
    private suspend fun handleImportResult(conversationId: Long, result: ScheduleImport) {
        if (result.needsMoreInfo) {
            val names = result.missing.joinToString("、") { param ->
                when (param) {
                    "semesterStartMonday" -> "学期第 1 周的周一日期"
                    "periodTimeTable" -> "作息时间（各节次的起止时间）"
                    else -> param
                }
            }
            _notice.value = "还需要补充：$names"
            return
        }

        val startMonday = result.semesterStartMonday
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (startMonday == null) {
            _error.value = "缺少学期开始日期，无法换算成具体日期"
            return
        }

        val events = ScheduleExpander.expand(
            courses = result.courses,
            startMonday = startMonday,
            periodTimes = result.periodTimeTable.orEmpty(),
            planName = currentConversationName()
        )
        if (events.isEmpty()) {
            _error.value = "识别到课程，但没有落在今天之后的时间段"
            return
        }

        val summary = buildString {
            append(result.message.ifBlank { "已识别 ${result.courses.size} 个课程段" })
            if (result.dropped.isNotEmpty()) {
                append("\n未导入 ${result.dropped.size} 条：")
                append(result.dropped.joinToString("；") { "${it.raw}（${it.reason}）" })
            }
            append("\n共生成 ${events.size} 个课程事件（仅保留今天之后的课程）")
        }

        val managed = runCatching {
            val (semester, courses) = managedScheduleImport(result, conversationId, currentConversationName())
            LearningRepository(AppDatabase.getInstance(appContext)).preview(semester, courses)
        }.getOrElse { _error.value = it.message ?: "无法保存课表规则，请补充节次"; return }
        val draft = PlanDraft(
            conversationId = conversationId,
            plan = StudyPlan(events = managed.generated, summary = summary + "\n保存学期与原始周次规则；将更新关联旧课程 ${managed.removed.size} 次，跳过重复 ${managed.duplicates} 次。时间冲突 ${managed.conflicts.size} 处：\n" + managed.conflicts.joinToString("\n") + "\n" + managed.courses.joinToString("\n") { "${it.name} 周${it.day} ${it.weeks}周 ${it.periods}节" }),
            name = currentConversationName(),
            replaceExisting = true,
            coursePreview = managed
        )
        _pendingPlan.value = draft
        _latestPlan.value = draft
    }

    fun generatePlan() {
        val conversationId = _currentConversationId.value ?: return
        if (_isGenerating.value || _isSending.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _notice.value = null
            repository.generatePlan(conversationId)
                .onSuccess { plan ->
                    val draft = PlanDraft(
                        conversationId = conversationId,
                        plan = plan,
                        name = currentConversationName()
                    )
                    _pendingPlan.value = draft
                    _latestPlan.value = draft
                }
                .onFailure { _error.value = it.message ?: "生成计划失败" }
            _isGenerating.value = false
        }
    }

    fun renamePlan(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = _pendingPlan.value ?: return
        val coursePreview = current.coursePreview?.let { preview ->
            preview.copy(semester = preview.semester.copy(name = trimmed), courses = preview.courses.map { it.copy(source = trimmed) },
                generated = preview.generated.map { it.copy(planName = trimmed) })
        }
        val updated = current.copy(name = trimmed, coursePreview = coursePreview,
            plan = if (coursePreview == null) current.plan else current.plan.copy(events = coursePreview.generated))
        _pendingPlan.value = updated
        if (_latestPlan.value?.plan == current.plan) _latestPlan.value = updated
    }

    fun confirmPlan() {
        val draft = _pendingPlan.value ?: return
        if (draft.applied) {
            _pendingPlan.value = null
            return
        }

        viewModelScope.launch {
            draft.coursePreview?.let { preview ->
                runCatching { LearningRepository(AppDatabase.getInstance(appContext)).apply(preview) }
                    .onSuccess { _notice.value = "课表与重复课程规则已保存"; _latestPlan.value = draft.copy(applied = true); _pendingPlan.value = null }
                    .onFailure { _error.value = it.message ?: "应用失败，请重新预览" }
                return@launch
            }
            repository.applyPlan(draft.plan, draft.name, draft.replaceExisting)
                .onSuccess { count ->
                    _notice.value = "已应用 $count 个事件到日历"
                    _error.value = null
                    val applied = draft.copy(applied = true)
                    if (_latestPlan.value?.plan == draft.plan) _latestPlan.value = applied
                    _pendingPlan.value = null
                }
                .onFailure { _error.value = it.message ?: "写入日历失败" }
        }
    }

    /** 关闭预览，但保留在 latestPlan 中供之后查看 */
    fun dismissPlan() {
        _pendingPlan.value = null
    }

    /** 重新打开最近一次生成的计划 */
    fun reopenLatestPlan() {
        _latestPlan.value?.let { _pendingPlan.value = it }
    }

    fun clearHistory() {
        val conversationId = _currentConversationId.value ?: return
        viewModelScope.launch {
            repository.clearHistory(conversationId)
            _error.value = null
            _notice.value = null
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            val appContext = context.applicationContext
            initializer {
                val database = AppDatabase.getInstance(appContext)
                ChatViewModel(
                    repository = ChatRepository(
                        api = DeepSeekClient.api,
                        dao = database.chatMessageDao(),
                        conversationDao = database.conversationDao(),
                        calendarDao = database.calendarEventDao(),
                        apiKeyStore = ApiKeyStore(appContext)
                    ),
                    appContext = appContext
                )
            }
        }
    }
}
