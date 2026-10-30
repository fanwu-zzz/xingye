package com.fanwu.studyplanner.data.schedule

import com.fanwu.studyplanner.domain.*
import java.time.LocalDate
import java.util.UUID

/** 同时保存识别出的原始周次和节次规则，避免展开后丢失重复课程关系。 */
fun managedScheduleImport(value: ScheduleImport, conversationId: Long, name: String,
                          today: LocalDate = LocalDate.now()): Pair<Semester, List<RecurringCourse>> {
    fun identity(text: String) = UUID.nameUUIDFromBytes(text.toByteArray(Charsets.UTF_8)).toString()
    val id = identity("schedule:$conversationId")
    val periods = value.periodTimeTable.orEmpty().map { (key, time) ->
        val range = ScheduleExpander.parsePeriods(key)
        val times = time.split('-', '~', '–').map(String::trim)
        require(range.isNotEmpty() && times.size == 2) { "作息表格式无效，请补充各节起止时间" }
        PeriodTime(range.first(), times[0], times[1], range.last())
    }.distinct().sortedBy { it.number }
    val semester = Semester(id, name, value.semesterStartMonday ?: error("缺少开学日期"),
        value.courses.flatMap { ScheduleExpander.parseWeeks(it.weeks) }.maxOrNull() ?: 18, periods)
    semester.validate()
    val courses = value.courses.distinct().map { c ->
        RecurringCourse(identity("$id|${c.name}|${c.dayOfWeek}|${c.weeks}|${c.periods}|${c.room}"), id,
            c.name, c.dayOfWeek, c.weeks, c.periods, c.room, from = today.toString(), source = name)
    }
    courses.forEach { expandCourse(it, semester) }
    return semester to courses
}
