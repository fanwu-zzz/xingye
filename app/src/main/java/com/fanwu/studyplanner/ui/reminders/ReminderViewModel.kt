package com.fanwu.studyplanner.ui.reminders

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.domain.ReminderSettings
import com.fanwu.studyplanner.reminders.ReminderRuntime
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReminderViewModel : ViewModel() {
    private val engine = ReminderRuntime.engine
    val settings = engine.settings.map { it?.toDomain() ?: ReminderSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val pending = engine.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val schedulingError = ReminderRuntime.error
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)

    fun save(settings: ReminderSettings) {
        if (busy.value) return
        busy.value = true
        error.value = null
        notice.value = null
        viewModelScope.launch {
            try {
                engine.save(settings)
                notice.value = "提醒设置已保存，日程提醒已更新"
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "保存失败，请重试" }
            finally { busy.value = false }
        }
    }

    fun refresh() { viewModelScope.launch { ReminderRuntime.reconcileSafely() } }
    companion object { val factory = viewModelFactory { initializer { ReminderViewModel() } } }
}
