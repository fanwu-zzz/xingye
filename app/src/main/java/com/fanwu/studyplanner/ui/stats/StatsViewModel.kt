package com.fanwu.studyplanner.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.repository.CalendarRepository
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.durationMinutes
import com.fanwu.studyplanner.domain.formatHours
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate

/** 统计页展示用的汇总数据 */
data class StatsUiState(
    val pendingCount: Int = 0,
    /** 截止最近的待完成任务（最多 2 条，用于橙色摘要卡） */
    val upcoming: List<CalendarEvent> = emptyList(),
    val weekHoursText: String = "0h",
    val completionText: String = "--",
    val streakDays: Int = 0,
    /** 周一~周日每天的学习分钟数 */
    val dailyMinutes: List<Int> = List(7) { 0 }
)

class StatsViewModel(repository: CalendarRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val weekStart: LocalDate = today.with(DayOfWeek.MONDAY)
    private val weekEnd: LocalDate = weekStart.plusDays(6)

    private val pending = repository.observePending(today.toString())
    private val weekEvents =
        repository.observeBetween(weekStart.toString(), weekEnd.toString())

    // 近 60 天事件用于计算“连续学习”天数
    private val recentEvents =
        repository.observeBetween(today.minusDays(60).toString(), today.toString())

    val uiState: StateFlow<StatsUiState> =
        combine(pending, weekEvents, recentEvents) { pendingList, week, recent ->
            StatsUiState(
                pendingCount = pendingList.size,
                upcoming = pendingList.take(2),
                weekHoursText = formatHours(week.sumOf { it.durationMinutes() }),
                completionText = formatCompletion(week),
                streakDays = computeStreak(recent),
                dailyMinutes = (0..6).map { offset ->
                    val date = weekStart.plusDays(offset.toLong()).toString()
                    week.filter { it.date == date }.sumOf { it.durationMinutes() }
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    companion object {
        internal fun formatCompletion(week: List<CalendarEvent>): String {
            if (week.isEmpty()) return "--"
            val done = week.count { it.isDone }
            return "${done * 100 / week.size}%"
        }

        /** 从今天（或昨天）往前数，连续有安排的天数 */
        internal fun computeStreak(recent: List<CalendarEvent>): Int {
            val daysWithEvents = recent.map { it.date }.toSet()
            var day = LocalDate.now()
            if (day.toString() !in daysWithEvents) day = day.minusDays(1)
            var streak = 0
            while (day.toString() in daysWithEvents) {
                streak++
                day = day.minusDays(1)
            }
            return streak
        }

        fun factory(context: Context) = viewModelFactory {
            val appContext = context.applicationContext
            initializer {
                val dao = AppDatabase.getInstance(appContext).calendarEventDao()
                StatsViewModel(CalendarRepository(dao))
            }
        }
    }
}
