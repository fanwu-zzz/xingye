package com.fanwu.studyplanner.ui.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.repository.CalendarRepository
import com.fanwu.studyplanner.domain.CalendarEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TasksViewModel(private val repository: CalendarRepository) : ViewModel() {

    /** 未完成任务，已按截止时间升序 */
    val pendingTasks: StateFlow<List<CalendarEvent>> =
        repository.observePending(LocalDate.now().toString())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDone(id: Long, done: Boolean) {
        viewModelScope.launch { repository.setDone(id, done) }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            val appContext = context.applicationContext
            initializer {
                val dao = AppDatabase.getInstance(appContext).calendarEventDao()
                TasksViewModel(CalendarRepository(dao))
            }
        }
    }
}
