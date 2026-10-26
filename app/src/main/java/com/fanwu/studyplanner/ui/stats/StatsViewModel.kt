package com.fanwu.studyplanner.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.repository.CalendarRepository
import com.fanwu.studyplanner.data.repository.FocusRepository
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate

data class PlanGroup(val planName: String?, val count: Int, val nearestDeadline: String)

data class StatsUiState(
    val pendingCount: Int = 0, val groups: List<PlanGroup> = emptyList(),
    val weekHoursText: String = "0h", val completedHoursText: String = "0h",
    val completionText: String = "--", val streakDays: Int = 0,
    val dailyMinutes: List<Int> = List(7) { 0 }, val dayLabels: List<String> = emptyList(),
    val periodLabel: String = "", val completedCount: Int = 0, val plannedCount: Int = 0,
    val actualFocusMs: Long = 0, val dailyFocusMs: List<Long> = emptyList()
)

/** 计划来自日历，实际专注只来自专注服务持久化的有效时间片。 */
class StatsViewModel(private val repository: CalendarRepository, focus: FocusRepository) : ViewModel() {
    private val _period = MutableStateFlow("week")
    val period: StateFlow<String> = _period
    fun selectPeriod(period: String) { _period.value = period }

    private val currentDay = flow {
        while (true) { emit(LocalDate.now()); delay(30_000) }
    }.distinctUntilChanged()
    val uiState = combine(repository.observeAllTasks(), focus.intervals, _period, currentDay) { tasks, spans, period, today ->
        buildStatsState(tasks, today, period, spans)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun deletePlan(planName: String) {
        viewModelScope.launch { repository.deletePlan(planName) }
    }

    companion object {
        internal fun groupPending(events: List<CalendarEvent>): List<PlanGroup> =
            events.groupBy { it.planName }.map { (name, list) -> PlanGroup(name, list.size, list.minOf { it.date }) }
                .sortedBy { it.nearestDeadline }

        internal fun formatCompletion(events: List<CalendarEvent>): String =
            if (events.isEmpty()) "--" else "${events.count { it.isDone } * 100 / events.size}%"

        internal fun computeStreak(events: List<CalendarEvent>, today: LocalDate = LocalDate.now()): Int {
            val dates = events.filter { it.isDone }.map { it.date }.toSet()
            var day = if (today.toString() in dates) today else today.minusDays(1)
            var count = 0
            while (day.toString() in dates) { count++; day = day.minusDays(1) }
            return count
        }

        fun factory(context: Context) = viewModelFactory {
            initializer { StatsViewModel(CalendarRepository(AppDatabase.getInstance(context.applicationContext).calendarEventDao(), AppDatabase.getInstance(context.applicationContext)), FocusRepository.get(context)) }
        }
    }
}

internal fun buildStatsState(tasks: List<CalendarEvent>, today: LocalDate, period: String, spans: List<FocusSpan> = emptyList()): StatsUiState {
    val start = if (period == "month") today.withDayOfMonth(1) else today.with(DayOfWeek.MONDAY)
    val end = if (period == "month") start.plusMonths(1).minusDays(1) else start.plusDays(6)
    val study = tasks.filter { it.type != EventType.CLASS }
    val selected = study.filter { it.date >= start.toString() && it.date <= end.toString() }
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
    val pending = study.filter { !it.isDone }
    val actualByDay = focusByDate(spans)
    val dailyFocus = (0 until days).map { actualByDay[start.plusDays(it.toLong())] ?: 0L }
    return StatsUiState(
        pendingCount = pending.size, groups = StatsViewModel.groupPending(pending),
        weekHoursText = formatHours(selected.sumOf { it.durationMinutes() }),
        completedHoursText = formatHours(selected.filter { it.isDone }.sumOf { it.durationMinutes() }),
        completionText = StatsViewModel.formatCompletion(selected),
        streakDays = StatsViewModel.computeStreak(study, today),
        dailyMinutes = (0 until days).map { offset -> selected.filter { it.date == start.plusDays(offset.toLong()).toString() }.sumOf { it.durationMinutes() } },
        dayLabels = (0 until days).map { offset -> if (period == "month") start.plusDays(offset.toLong()).dayOfMonth.toString() else listOf("一", "二", "三", "四", "五", "六", "日")[offset] },
        periodLabel = "${start.monthValue}/${start.dayOfMonth} — ${end.monthValue}/${end.dayOfMonth}",
        completedCount = selected.count { it.isDone }, plannedCount = selected.size,
        actualFocusMs = dailyFocus.sum(), dailyFocusMs = dailyFocus
    )
}
