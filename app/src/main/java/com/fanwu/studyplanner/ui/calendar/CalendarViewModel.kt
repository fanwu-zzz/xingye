package com.fanwu.studyplanner.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.repository.CalendarRepository
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.effectiveDone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
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

    /**
     * 每分钟刷新一次的当前时间，用于自动判定课程是否已下课。
     * 页面离开后停止计时，重新进入时立即取一次最新时间。
     */
    private val nowTick: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    /** 选中日期的事件，按开始时间排序；已下课的课程自动标记为完成 */
    val selectedDayEvents: StateFlow<List<CalendarEvent>> =
        combine(monthEvents, _selectedDate, nowTick) { events, date, now ->
            events.filter { it.date == date.toString() }
                .map { it.copy(isDone = it.effectiveDone(now)) }
                .sortedBy { it.startTime }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 选中日期所在周（周一~周日）的事件，供首页 Summary 卡使用；课程同样按时间自动完成 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weekEvents: StateFlow<List<CalendarEvent>> = _selectedDate
        .flatMapLatest { d ->
            val weekStart = d.with(java.time.DayOfWeek.MONDAY)
            repository.observeBetween(weekStart.toString(), weekStart.plusDays(6).toString())
        }
        .combine(nowTick) { events, now ->
            events.map { it.copy(isDone = it.effectiveDone(now)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        // 同步显示月份，保证事件查询范围覆盖所选日期
        _month.value = YearMonth.from(date)
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

    /** 标记任务完成 / 取消完成 */
    fun setDone(id: Long, done: Boolean) {
        viewModelScope.launch { repository.setDone(id, done) }
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
