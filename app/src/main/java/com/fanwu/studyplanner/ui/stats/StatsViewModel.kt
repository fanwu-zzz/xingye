package com.fanwu.studyplanner.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.AppDatabase
import com.fanwu.studyplanner.data.repository.CalendarRepository
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.EventType
import com.fanwu.studyplanner.domain.durationMinutes
import com.fanwu.studyplanner.domain.formatHours
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** 统计页里的一个计划组：一轮对话生成的整组计划 */
data class PlanGroup(
    /** 计划组名（对话名），null 表示手动创建、未分组的事件 */
    val planName: String?,
    val count: Int,
    /** 组内最近的截止日期，用于展示 */
    val nearestDeadline: String
)

/** 统计页展示用的汇总数据 */
data class StatsUiState(
    val pendingCount: Int = 0,
    /** 待完成任务按计划组打包，按最近截止时间排序 */
    val groups: List<PlanGroup> = emptyList(),
    val weekHoursText: String = "0h",
    val completionText: String = "--",
    val streakDays: Int = 0,
    /** 周一~周日每天的学习分钟数 */
    val dailyMinutes: List<Int> = List(7) { 0 }
)

class StatsViewModel(private val repository: CalendarRepository) : ViewModel() {

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
            // 课表只用于日历展示，不计入学习时长、完成率与连续天数
            val studyWeek = week.filter { it.type != EventType.CLASS }
            val studyRecent = recent.filter { it.type != EventType.CLASS }
            StatsUiState(
                pendingCount = pendingList.size,
                groups = groupPending(pendingList),
                weekHoursText = formatHours(studyWeek.sumOf { it.durationMinutes() }),
                completionText = formatCompletion(studyWeek),
                streakDays = computeStreak(studyRecent),
                dailyMinutes = (0..6).map { offset ->
                    val date = weekStart.plusDays(offset.toLong()).toString()
                    studyWeek.filter { it.date == date }.sumOf { it.durationMinutes() }
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** 删除整个计划组，日历上对应日期的日程一并删除 */
    fun deletePlan(planName: String) {
        viewModelScope.launch { repository.deletePlan(planName) }
    }

    companion object {
        /** 按 planName 打包，组间按最近截止时间排序 */
        internal fun groupPending(events: List<CalendarEvent>): List<PlanGroup> =
            events.groupBy { it.planName }
                .map { (name, list) ->
                    PlanGroup(
                        planName = name,
                        count = list.size,
                        nearestDeadline = list.minOf { it.date }
                    )
                }
                .sortedBy { it.nearestDeadline }

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
