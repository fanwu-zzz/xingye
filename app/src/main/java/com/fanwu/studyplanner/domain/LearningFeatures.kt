package com.fanwu.studyplanner.domain

import com.fanwu.studyplanner.data.schedule.ScheduleExpander
import kotlinx.serialization.Serializable
import java.time.*
import java.util.UUID

fun mondayOf(date: LocalDate) = date.with(DayOfWeek.MONDAY)

@Serializable
data class GoalRevision(
    val id: String = UUID.randomUUID().toString(), val goalId: String = UUID.randomUUID().toString(),
    val effectiveWeek: String, val name: String, val minutes: Int, val planName: String? = null,
    val deadline: String? = null, val state: String = "ACTIVE", val createdAt: Long = System.currentTimeMillis()
)

fun GoalRevision.validate() {
    require(id.isNotBlank() && goalId.isNotBlank() && createdAt > 0) { "目标版本信息无效" }
    require(name.isNotBlank() && name.length <= 100) { "目标名称应为 1—100 字" }
    require(minutes in 1..10080) { "每周目标应在 1 分钟至 168 小时之间" }
    require(LocalDate.parse(effectiveWeek).dayOfWeek == DayOfWeek.MONDAY) { "生效日期必须为周一" }
    require(state in listOf("ACTIVE", "PAUSED", "ARCHIVED"))
    deadline?.let { require(!LocalDate.parse(it).isBefore(LocalDate.parse(effectiveWeek))) { "截止日期不能早于生效周" } }
}

fun goalsForWeek(revisions: List<GoalRevision>, monday: LocalDate): List<GoalRevision> = revisions
    .filter { it.effectiveWeek <= monday.toString() }.groupBy { it.goalId }.values
    .map { rows -> rows.maxWith(compareBy<GoalRevision> { it.effectiveWeek }.thenBy { it.createdAt }.thenBy { it.id }) }
    .sortedBy { it.name }

/** 按时间片的本地日期截取本周投入，不计计划时长和暂停时间。 */
fun focusedInWeek(spans: List<FocusSpan>, monday: LocalDate): Long = focusByDate(spans)
    .filterKeys { it >= monday && it < monday.plusWeeks(1) }.values.sum()

@Serializable
data class PeriodTime(val number: Int, val start: String, val end: String, val through: Int = number)

@Serializable
data class Semester(
    val id: String = UUID.randomUUID().toString(), val name: String, val startMonday: String,
    val weeks: Int, val periods: List<PeriodTime>
)

fun Semester.validate() {
    require(name.isNotBlank()) { "请输入学期名称" }
    require(LocalDate.parse(startMonday).dayOfWeek == DayOfWeek.MONDAY) { "第一教学周必须从周一开始" }
    require(weeks in 1..30) { "学期周数应为 1—30" }
    require(periods.isNotEmpty() && periods.size <= 24) { "请设置 1—24 个节次" }
    require(periods.all { it.number in 1..24 && it.through in it.number..24 }) { "节次编号应为 1—24" }
    val numbers = periods.flatMap { (it.number..it.through).toList() }
    require(numbers.distinct().size == numbers.size) { "节次编号重复" }
    var end = LocalTime.MIN
    periods.sortedBy { it.number }.forEach {
        require(it.number in 1..24 && it.through in it.number..24) { "节次编号应为 1—24" }
        val a = LocalTime.parse(it.start); val b = LocalTime.parse(it.end)
        require(a < b && a >= end) { "节次必须按编号递增，且时间不能重叠" }; end = b
    }
}

@Serializable
data class RecurringCourse(
    val id: String = UUID.randomUUID().toString(), val semesterId: String, val name: String,
    val day: Int, val weeks: String, val periods: String, val room: String = "",
    val from: String? = null, val until: String? = null,
    val exceptions: List<String> = emptyList(), val source: String? = null,
    val exceptionWeeks: List<Int> = emptyList()
)

fun expandCourse(course: RecurringCourse, semester: Semester): List<CalendarEvent> {
    semester.validate()
    require(course.semesterId == semester.id && course.name.isNotBlank() && course.day in 1..7) { "课程名称、星期或学期无效" }
    require(Regex("[0-9,，、\\s单双周-]+").matches(course.weeks)) { "周次含无法识别的字符" }
    require(!(course.weeks.contains("单") && course.weeks.contains("双"))) { "请分别设置单周和双周课程规则" }
    require(course.exceptionWeeks.all { it in 1..30 }) { "课程例外周次无效" }
    require(Regex("[0-9,，、\\s第节-]+").matches(course.periods)) { "节次含无法识别的字符" }
    val weeks = ScheduleExpander.parseWeeks(course.weeks)
    require(weeks.isNotEmpty() && weeks.all { it in 1..semester.weeks }) { "周次必须位于学期内，例如 1-16单周 或 1-4,6-16" }
    val periods = ScheduleExpander.parsePeriods(course.periods)
    require(periods.zipWithNext().all { (a, b) -> b == a + 1 }) { "一次课程的节次必须连续；不连续节次请分成两个课程段" }
    require(periods.isNotEmpty() && periods.all { n -> semester.periods.any { n in it.number..it.through } }) { "课程引用了未设置的节次" }
    require(semester.periods.any { it.number == periods.first() && it.through == periods.last() } ||
        (semester.periods.any { it.number == periods.first() && it.through == it.number } && semester.periods.any { it.number == periods.last() && it.through == it.number })) { "只有合并节次的总时间，请补充单节作息后再拆分课程" }
    val table = semester.periods.associate { (if (it.number == it.through) it.number.toString() else "${it.number}-${it.through}") to "${it.start}-${it.end}" }
    val activeWeeks = weeks.filterNot { it in course.exceptionWeeks }
    return ScheduleExpander.expand(listOf(CourseSegment(course.name, course.day, activeWeeks.joinToString(","), course.periods, course.room)),
        LocalDate.parse(semester.startMonday), table, course.source.orEmpty(), LocalDate.MIN)
        .filter { (course.from == null || it.date >= course.from) && (course.until == null || it.date <= course.until) && it.date !in course.exceptions }
        .map { it.copy(recurringCourseId = course.id, originalOccurrenceDate = it.date, subject = course.name) }
}

fun overlaps(a: CalendarEvent, b: CalendarEvent) = a.date == b.date && a.startTime < b.endTime && b.startTime < a.endTime

data class WeekReview(val monday: LocalDate, val text: String, val taskCount: Int, val actualMs: Long)

fun buildWeekReview(monday: LocalDate, events: List<CalendarEvent>, spans: List<Pair<FocusSpan, FocusSession?>>,
                    now: LocalDateTime = LocalDateTime.now()): WeekReview {
    val end = monday.plusDays(6)
    val tasks = events.filter { it.type == EventType.STUDY_TASK && it.date >= monday.toString() && it.date <= end.toString() }
    val actual = focusedInWeek(spans.map { it.first }, monday)
    val done = tasks.filter { it.isDone }
    val lateDone = done.count { e -> e.completedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() > LocalDate.parse(e.date).atTime(LocalTime.parse(e.endTime)) } == true }
    val overdue = tasks.count { !it.isDone && LocalDate.parse(it.date).atTime(LocalTime.parse(it.endTime)) < now }
    val subjects = spans.groupBy { it.second?.subject?.takeIf(String::isNotBlank) ?: "未标注科目 / 自由专注" }
        .mapValues { (_, rows) -> focusedInWeek(rows.map { it.first }, monday) }.filterValues { it > 0 }
    val completions = events.count { it.type == EventType.STUDY_TASK && it.isDone && it.completedAt?.let { ms ->
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().let { it >= monday && it <= end }
    } == true }
    val text = buildString {
        append("复盘范围：$monday 至 $end\n")
        append("本周计划学习：${tasks.sumOf { it.durationMinutes() }} 分钟；实际专注：${actual / 60000} 分钟。\n")
        append("本周安排任务 ${tasks.size} 项，当前已完成 ${done.size} 项，未完成且已过截止时间 $overdue 项。\n")
        append("有完成时间的延期完成 $lateDone 项；实际在本周完成（含往周任务）$completions 项。\n")
        append("科目实际投入：" + if (subjects.isEmpty()) "暂无专注记录。\n" else subjects.entries.sortedByDescending { it.value }.joinToString("；") { "${it.key} ${it.value / 60000} 分钟" } + "。\n")
        append("口径：实际时长仅含已结算专注时间片，暂停和休息不计；未关联/旧记录不推测科目。")
        append("任务完成状态为当前快照；历史旧任务 ${done.count { it.completedAt == null }} 项缺少完成时间，不推测完成日期。")
        append("计划量只统计学习任务，不计课程与考试；没有记录表示暂无数据，不代表没有学习。")
    }
    return WeekReview(monday, text, tasks.size, actual)
}

@Serializable
data class ReviewDraft(val week: String, val summary: String, val reply: String, val events: List<CalendarEvent> = emptyList(),
                       val applied: Boolean = false, val id: String = UUID.randomUUID().toString())
