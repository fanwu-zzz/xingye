package com.fanwu.studyplanner.ui.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.focus.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class FocusViewModel(private val context: Context, private val repository: FocusRepository) : ViewModel() {
    val active = repository.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tasks = CalendarRepository(AppDatabase.getInstance(context).calendarEventDao()).observeAllTasks()
        .map { tasks -> tasks.filter { it.type == EventType.STUDY_TASK && !it.isDone } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val now = flow {
        while (true) { emit(repository.clock.now()); delay(500) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.clock.now())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    init {
        viewModelScope.launch {
            try { recover() }
            catch (e: Exception) { _error.value = e.message ?: "无法恢复专注记录" }
            finally { _loaded.value = true }
        }
    }

    fun refresh() = execute { recover() }

    private suspend fun recover() {
        val change = repository.act()
        FocusNotifications.boundary(context, change)
        repository.current()?.let { sync(it) }
    }

    fun start(focusMinutes: Int, breakMinutes: Int, taskId: Long?) = execute {
        val session = repository.start(focusMinutes, breakMinutes, taskId)
        try { sync(session) }
        catch (e: Exception) {
            repository.act(session.id, FocusAction.PAUSE)
            throw IllegalStateException("后台计时未能启动，已暂停并保留本轮。请返回页面后重试。", e)
        }
    }

    fun act(action: FocusAction, completeTask: Boolean = false) = execute {
        val session = repository.current() ?: return@execute
        val change = repository.act(session.id, action, completeTask) ?: return@execute
        FocusNotifications.boundary(context, change)
        sync(change.session)
        if (action == FocusAction.FINISH) _notice.value = "已保存本轮专注：${formatFocusDuration(change.session.focusedMs)}"
    }

    private fun sync(session: FocusSession) {
        FocusAlarm.sync(context, session)
        if (session.phase.isRunning) FocusService.start(context)
        else {
            // 服务可能仍在处理 startForegroundService；由其循环读到暂停/结束后自行退出，
            // 避免在尚未完成前台提升时 stopService 导致系统抛出 RemoteServiceException。
            FocusNotifications.show(context, session)
        }
    }

    private fun execute(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "操作失败，请重试" }
            finally { _busy.value = false }
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                FocusViewModel(app, FocusRepository.get(app))
            }
        }
    }
}
