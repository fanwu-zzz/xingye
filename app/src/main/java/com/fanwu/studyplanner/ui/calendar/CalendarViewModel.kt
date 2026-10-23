package com.fanwu.studyplanner.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.repository.CalendarRepository
import com.fanwu.studyplanner.domain.CalendarEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(private val repository: CalendarRepository) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    /** 当前显示月份内的全部事件 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthEvents: StateFlow<List<CalendarEvent>> = _month
        .flatMapLatest { m ->
            repository.observeBetween(m.atDay(1).toString(), m.atEndOfMonth().toString())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 选中日期的事件，按开始时间排序 */
    val selectedDayEvents: StateFlow<List<CalendarEvent>> =
        combine(monthEvents, _selectedDate) { events, date ->
            events.filter { it.date == date.toString() }.sortedBy { it.startTime }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 选中日期所在周（周一~周日）的事件，供首页 Summary 卡使用 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weekEvents: StateFlow<List<CalendarEvent>> = _selectedDate
        .flatMapLatest { d ->
            val weekStart = d.with(java.time.DayOfWeek.MONDAY)
            repository.observeBetween(weekStart.toString(), weekStart.plusDays(6).toString())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousMonth() {
        val target = _month.value.minusMonths(1)
        _month.value = target
        _selectedDate.value = target.atDay(1)
    }

    fun nextMonth() {
        val target = _month.value.plusMonths(1)
        _month.value = target
        _selectedDate.value = target.atDay(1)
    }

    fun saveEvent(event: CalendarEvent) {
        viewModelScope.launch {
            if (event.id == 0L) repository.insert(event) else repository.update(event)
        }
    }

    fun deleteEvent(event: CalendarEvent) {
        viewModelScope.launch { repository.delete(event) }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            val appContext = context.applicationContext
            initializer {
                val dao = AppDatabase.getInstance(appContext).calendarEventDao()
                CalendarViewModel(CalendarRepository(dao))
            }
        }
    }
}
