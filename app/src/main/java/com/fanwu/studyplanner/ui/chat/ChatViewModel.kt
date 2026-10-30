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
import com.fanwu.studyplanner.data.agent.*
import com.fanwu.studyplanner.BuildConfig
import com.fanwu.studyplanner.data.repository.ChatRepository
import com.fanwu.studyplanner.data.schedule.XlsxReader
import com.fanwu.studyplanner.data.schedule.managedScheduleImport
import com.fanwu.studyplanner.data.repository.LearningRepository
import com.fanwu.studyplanner.data.repository.CoursePreview
import com.fanwu.studyplanner.data.repository.decode
import com.fanwu.studyplanner.domain.ChatMessage
import com.fanwu.studyplanner.domain.Conversation
import com.fanwu.studyplanner.domain.ConversationMode
import com.fanwu.studyplanner.domain.ScheduleImport
import com.fanwu.studyplanner.domain.StudyPlan
import com.fanwu.studyplanner.domain.Semester
import com.fanwu.studyplanner.domain.RecurringCourse
import com.fanwu.studyplanner.domain.expandCourse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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
    val coursePreview: CoursePreview? = null,
    val agentRunId: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val appContext: Context,
    private val agents: StudyAgentRepository = StudyAgentRepository(AppDatabase.getInstance(appContext), DeepSeekClient.api) {
        ApiKeyStore(appContext).apiKey.ifBlank { BuildConfig.AI_API_KEY }
    }
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

    private val _failedInput = MutableStateFlow<String?>(null)
    val failedInput: StateFlow<String?> = _failedInput

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying

    private var importIssue: String? = null
    private var agentJob: Job? = null
    private val _agentRun = MutableStateFlow<AgentRun?>(null)
    val agentRun: StateFlow<AgentRun?> = _agentRun
    private val _agentPreferences = MutableStateFlow(AgentPreferences())
    val agentPreferences: StateFlow<AgentPreferences> = _agentPreferences

    init { viewModelScope.launch { _agentPreferences.value = agents.preferences() } }

    fun saveAgentPreferences(text: String, maximum: String, allowWebSearch: Boolean = _agentPreferences.value.allowWebSearch) {
        viewModelScope.launch {
            runCatching {
                val limit = if (maximum.isBlank()) null else maximum.toIntOrNull() ?: error("每日分钟数应为整数")
                val value = AgentPreferences(text.trim(), limit, allowWebSearch)
                agents.savePreferences(value)
                _agentPreferences.value = value
            }.onSuccess { _notice.value = "学习偏好已保存，将在下次规划及确认时使用。" }
                .onFailure { _error.value = it.message }
        }
    }

    private fun showAgent(run: AgentRun, open: Boolean = false) {
        if (_currentConversationId.value != run.conversationId) return
        _agentRun.value = run
        _error.value = run.error
        if (run.status == AgentStatus.WAITING_CONFIRMATION || run.status == AgentStatus.APPLIED) {
            run.draft?.let { draft ->
                val plan = PlanDraft(run.conversationId, StudyPlan(draft.events, if (run.status == AgentStatus.APPLIED) "已实际保存${run.appliedIds.size}项到日历。" else "已检查重复、时间冲突与每日学习上限；尚未写入日历。"),
                    draft.name, applied = run.status == AgentStatus.APPLIED, agentRunId = run.id)
                _latestPlan.value = plan
                if (open && !plan.applied) _pendingPlan.value = plan
            }
        }
    }

    private fun launchAgent(text: String? = null): Boolean {
        val id = _currentConversationId.value ?: return false
        if (_isSending.value || _isGenerating.value || _isApplying.value) return false
        if (text != null && text.length > 8000) { _error.value = "消息最多8000字，请拆分后发送"; return false }
        _isSending.value = true
        _error.value = null; _notice.value = null; _failedInput.value = null
        _pendingPlan.value = null; _latestPlan.value = null
        agentJob = viewModelScope.launch {
            try {
                val run = if (text == null) agents.resume(id) { if (_currentConversationId.value == id) _agentRun.value = it }
                    else agents.start(id, text) { if (_currentConversationId.value == id) _agentRun.value = it }
                showAgent(run, open = true)
            } catch (_: CancellationException) {
                withContext(NonCancellable) { if (_currentConversationId.value == id) agents.load(id)?.let { showAgent(it) } }
            } catch (e: Exception) {
                if (_currentConversationId.value == id) { _error.value = e.message ?: "智能体处理失败"; _failedInput.value = text }
            } finally { _isSending.value = false }
        }
        return true
    }

    fun cancelAgent() { agentJob?.cancel() }
    fun resumeAgent() { launchAgent() }

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
            _error.value = null
            _notice.value = null
            _failedInput.value = null
            _pendingPlan.value = null
            _latestPlan.value = null
            importIssue = null
            _agentRun.value = null
            _agentPreferences.value = agents.preferences()
            _currentConversationId.value = repository.createConversation(trimmed, mode)
        }
    }

    fun openConversation(id: Long) {
        _currentConversationId.value = id
        _error.value = null
        _notice.value = null
        _failedInput.value = null
        _pendingPlan.value = null
        _agentRun.value = null
        importIssue = null
        if (_latestPlan.value?.conversationId != id) _latestPlan.value = null
        viewModelScope.launch {
            if (conversations.value.firstOrNull { it.id == id }?.mode == ConversationMode.IMPORT_SCHEDULE) {
                val result = repository.latestScheduleImport(id)
                if (_currentConversationId.value == id && result != null) handleImportResult(id, result, showPreview = false,
                    planName = _latestPlan.value?.takeIf { it.conversationId == id }?.name ?: currentConversationName())
            } else {
                _agentPreferences.value = agents.preferences()
                val run = agents.load(id)
                if (_currentConversationId.value == id && run != null) showAgent(run)
            }
        }
    }

    /** 删除一轮对话及其消息；若正在查看该会话则退回列表 */
    fun deleteConversation(id: Long) {
        if (_currentConversationId.value == id) cancelAgent()
        viewModelScope.launch {
            agentJob?.join()
            agents.clear(id)
            repository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
                _pendingPlan.value = null
                _latestPlan.value = null
            }
        }
    }

    fun backToList() {
        cancelAgent()
        _currentConversationId.value = null
        _error.value = null
        _notice.value = null
    }

    fun currentConversationName(): String =
        conversations.value.firstOrNull { it.id == _currentConversationId.value }?.name
            ?: "学习计划"

    fun send(text: String): Boolean {
        val conversationId = _currentConversationId.value ?: return false
        val content = text.trim()
        if (content.isEmpty() || _isSending.value || _isGenerating.value || _isApplying.value) return false
        val importMode = currentConversation.value?.mode == ConversationMode.IMPORT_SCHEDULE
        if (!importMode) return launchAgent(content)
        val applicationState = importApplicationState()

        _isSending.value = true
        _error.value = null
        _failedInput.value = null

        viewModelScope.launch {
            if (importMode) {
                repository.importSchedule(conversationId, content, applicationState)
                    .onSuccess { handleImportResult(conversationId, it) }
                    .onFailure { _error.value = it.message ?: "解析失败"; _failedInput.value = content }
            } else {
                repository.sendMessage(conversationId, content)
                    .onFailure { _error.value = it.message ?: "发送失败"; _failedInput.value = content }
            }
            _isSending.value = false
        }
        return true
    }

    fun clearFailedInput() { _failedInput.value = null }

    private fun importApplicationState(): String {
        val draft = _latestPlan.value?.takeIf { it.conversationId == _currentConversationId.value }
        return when {
            draft?.applied == true -> "已确认保存课表，共 ${draft.plan.events.size} 次课程。可在课表或日历对应日期查看。"
            importIssue != null -> "尚未完成本轮导入，本地校验提示：$importIssue"
            draft != null -> "尚未导入。已有 ${draft.plan.events.size} 次课程待预览，需点击预览并确认导入。"
            else -> "尚未导入。${_notice.value.orEmpty()} 需补齐信息、预览并确认后才写入。"
        }
    }

    /** 选择课表文件后：本地转成纯文本，再交给 AI 识别 */
    fun importScheduleFile(uri: Uri, fileName: String) {
        val conversationId = _currentConversationId.value ?: return
        if (_isSending.value || _isGenerating.value || _isApplying.value) return

        _isSending.value = true
        _latestPlan.value = null
        _pendingPlan.value = null
        importIssue = null

        viewModelScope.launch {
            _error.value = null
            _notice.value = null
            _failedInput.value = null

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
                repository.importSchedule(conversationId, text, importApplicationState())
                    .onSuccess { handleImportResult(conversationId, it) }
                    .onFailure { _error.value = it.message ?: "导入失败" }
            }
            _isSending.value = false
        }
    }

    /** 处理一次课表解析结果：缺参数就提示用户，识别完成就生成待预览的计划 */
    private suspend fun handleImportResult(conversationId: Long, result: ScheduleImport, showPreview: Boolean = true,
                                           planName: String = currentConversationName()) {
        if (_currentConversationId.value != conversationId) return
        if (result.isChatReply) {
            _error.value = importIssue
            return
        }
        importIssue = null
        _pendingPlan.value = null
        _latestPlan.value = null
        if (result.needsMoreInfo) {
            val names = result.missing.joinToString("、") { param ->
                when (param) {
                    "semesterStartMonday" -> "学期第 1 周的周一日期"
                    "periodTimeTable" -> "作息时间（各节次的起止时间）"
                    else -> param
                }
            }
            _notice.value = "尚未导入。" + if (names.isBlank()) result.message else "还需要补充：$names"
            return
        }

        val startMonday = result.semesterStartMonday
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (startMonday == null) {
            _error.value = "缺少学期开始日期，无法换算成具体日期"
            return
        }

        val managed = runCatching {
            val (semester, courses) = managedScheduleImport(result, conversationId, planName)
            LearningRepository(AppDatabase.getInstance(appContext)).preview(semester, courses)
        }.getOrElse {
            _notice.value = "尚未导入，预览尚未就绪。请补充下方提示的信息后发送。"
            _error.value = it.message ?: "无法生成课表预览，请补充节次"
            importIssue = _error.value
            return
        }
        if (_currentConversationId.value != conversationId) return
        val occurrences = managed.courses.flatMap { expandCourse(it, managed.semester) }
        if (occurrences.isEmpty()) {
            _notice.value = "尚未导入。学期首周为 $startMonday，但课程均早于今天，请核对年份和周次。"
            return
        }
        val savedSemester = managed.beforeDocs.firstOrNull { it.id == managed.semester.id && it.kind == "semester" }?.decode<Semester>()
        val savedCourses = managed.beforeDocs.filter { it.kind == "course" }.map { it.decode<RecurringCourse>() }
            .filter { it.semesterId == managed.semester.id }
        fun RecurringCourse.rule() = copy(from = null, source = null, exceptions = emptyList(), exceptionWeeks = emptyList())
        val applied = savedSemester?.copy(name = managed.semester.name) == managed.semester &&
            savedCourses.map { it.rule() }.toSet() == managed.courses.map { it.rule() }.toSet()
        val summary = buildString {
            append("学期第 1 周周一：${managed.semester.startMonday}。仅安排今天及以后的课程。")
            if (applied) append("\n已保存 ${savedCourses.size} 个课程段，今天及以后对应 ${occurrences.size} 次课程。")
            else append("\n识别 ${result.courses.size} 个课程段，本次新增 ${managed.generated.size} 次，更新旧课程 ${managed.removed.size} 次，跳过重复 ${managed.duplicates} 次。")
            if (result.dropped.isNotEmpty()) {
                append("\n另有 ${result.dropped.size} 条无法排入日历：")
                append(result.dropped.joinToString("；") { "${it.raw}（${it.reason}）" })
            }
            append("\n时间冲突 ${managed.conflicts.size} 处。")
            if (managed.conflicts.isNotEmpty()) append("\n" + managed.conflicts.joinToString("\n"))
        }
        val draft = PlanDraft(
            conversationId = conversationId,
            plan = StudyPlan(events = if (applied) occurrences else managed.generated, summary = summary),
            name = if (applied) savedSemester!!.name else planName,
            applied = applied,
            replaceExisting = true,
            coursePreview = managed
        )
        _error.value = null
        _notice.value = if (applied) "课表已保存，可在课表或日历对应日期查看。" else "尚未导入 · 预览已就绪，确认后才会写入课程。"
        if (showPreview && !applied) _pendingPlan.value = draft
        _latestPlan.value = draft
    }

    fun generatePlan() {
        if (currentConversation.value?.mode != ConversationMode.IMPORT_SCHEDULE) {
            launchAgent("请根据当前对话，查询真实日历和待办，生成可预览确认的学习计划。")
            return
        }
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
        current.agentRunId?.let { runId ->
            viewModelScope.launch {
                runCatching { agents.renameDraft(current.conversationId, runId, trimmed) }
                    .onFailure { _error.value = it.message }
            }
        }
    }

    fun confirmPlan() {
        val draft = _pendingPlan.value ?: return
        if (_isApplying.value) return
        if (draft.applied) {
            _pendingPlan.value = null
            return
        }

        _isApplying.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                if (draft.agentRunId != null) {
                    runCatching { agents.apply(draft.conversationId, draft.agentRunId, draft.name) }
                        .onSuccess {
                            showAgent(it)
                            _notice.value = "已实际保存${it.appliedIds.size}项到日历。"
                            _pendingPlan.value = null
                        }.onFailure { _error.value = it.message ?: "保存失败，请重新核对草稿" }
                    return@launch
                }
                draft.coursePreview?.let { preview ->
                    runCatching { LearningRepository(AppDatabase.getInstance(appContext)).apply(preview) }
                        .onSuccess {
                            _notice.value = "已导入 ${preview.generated.size} 次课程；更新旧课程 ${preview.removed.size} 次，跳过重复 ${preview.duplicates} 次。请到课表或日历对应日期查看。"
                            _latestPlan.value = draft.copy(applied = true)
                            _pendingPlan.value = null
                        }
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
            } finally { _isApplying.value = false }
        }
    }

    /** 关闭预览，但保留在 latestPlan 中供之后查看 */
    fun dismissPlan() {
        if (_isApplying.value) return
        _pendingPlan.value = null
    }

    /** 重新打开最近一次生成的计划 */
    fun reopenLatestPlan() {
        val draft = _latestPlan.value?.takeIf { it.conversationId == _currentConversationId.value } ?: return
        if (draft.agentRunId != null) {
            if (_isSending.value || _isApplying.value) return
            viewModelScope.launch {
                runCatching { agents.recheck(draft.conversationId) }.onSuccess {
                    showAgent(it)
                    _pendingPlan.value = _latestPlan.value
                }.onFailure { _error.value = it.message ?: "草稿已过期，请重新规划" }
            }
            return
        }
        if (draft.coursePreview == null || draft.applied) {
            _pendingPlan.value = draft
            return
        }
        if (_isSending.value || _isGenerating.value || _isApplying.value) return
        _isGenerating.value = true
        viewModelScope.launch {
            try {
                val result = repository.latestScheduleImport(draft.conversationId) ?: return@launch
                handleImportResult(draft.conversationId, result, planName = draft.name)
            } finally { _isGenerating.value = false }
        }
    }

    fun clearHistory() {
        val conversationId = _currentConversationId.value ?: return
        cancelAgent()
        viewModelScope.launch {
            agentJob?.join()
            agents.clear(conversationId)
            _agentRun.value = null
            repository.clearHistory(conversationId)
            _error.value = null
            _notice.value = null
            _failedInput.value = null
            _pendingPlan.value = null
            _latestPlan.value = null
            importIssue = null
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
