package com.fanwu.studyplanner.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.ApiKeyStore
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.remote.DeepSeekClient
import com.fanwu.studyplanner.data.repository.ChatRepository
import com.fanwu.studyplanner.domain.ChatMessage
import com.fanwu.studyplanner.domain.StudyPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val _pendingPlan = MutableStateFlow<StudyPlan?>(null)
    val pendingPlan: StateFlow<StudyPlan?> = _pendingPlan

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            _notice.value = null
            repository.sendMessage(content)
                .onFailure { _error.value = it.message ?: "发送失败" }
            _isSending.value = false
        }
    }

    fun generatePlan() {
        if (_isGenerating.value || _isSending.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _notice.value = null
            repository.generatePlan()
                .onSuccess { _pendingPlan.value = it }
                .onFailure { _error.value = it.message ?: "生成计划失败" }
            _isGenerating.value = false
        }
    }

    fun confirmPlan() {
        val plan = _pendingPlan.value ?: return

        viewModelScope.launch {
            repository.applyPlan(plan)
                .onSuccess { count ->
                    _notice.value = "已写入 $count 个事件到日历"
                    _error.value = null
                }
                .onFailure { _error.value = it.message ?: "写入日历失败" }
            _pendingPlan.value = null
        }
    }

    fun dismissPlan() {
        _pendingPlan.value = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
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
                    ChatRepository(
                        api = DeepSeekClient.api,
                        dao = database.chatMessageDao(),
                        calendarDao = database.calendarEventDao(),
                        apiKeyStore = ApiKeyStore(appContext)
                    )
                )
            }
        }
    }
}
