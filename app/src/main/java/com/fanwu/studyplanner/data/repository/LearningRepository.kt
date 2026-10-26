package com.fanwu.studyplanner.data.repository

import androidx.room.withTransaction
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.reminders.ReminderRuntime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val plannerJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }
inline fun <reified T> PlannerDocument.decode(): T = plannerJson.decodeFromString(payload)
inline fun <reified T> document(id: String, kind: String, value: T) = PlannerDocument(id, kind, plannerJson.encodeToString(value))

data class CoursePreview(val semester: Semester, val courses: List<RecurringCourse>, val generated: List<CalendarEvent>,
                         val removed: List<CalendarEventEntity>, val conflicts: List<String>, val duplicates: Int,
                         val before: List<CalendarEventEntity>, val beforeDocs: List<PlannerDocument>)

class LearningRepository(val db: AppDatabase) {
    suspend fun saveGoal(goal: GoalRevision) {
        goal.validate()
        require(LocalDateGuard.notPastWeek(goal.effectiveWeek)) { "历史周只读；调整从本周或未来生效" }
        db.withTransaction {
            val previous = db.documentDao().all().filter { it.kind == "goal" }.map { it.decode<GoalRevision>() }
            val same = previous.filter { it.goalId == goal.goalId && it.effectiveWeek == goal.effectiveWeek }
            same.forEach { db.documentDao().delete(it.id) }
            db.documentDao().put(document(goal.id, "goal", goal))
        }
    }

    suspend fun preview(semester: Semester, courses: List<RecurringCourse>): CoursePreview = db.withTransaction {
        semester.validate()
        val all = db.calendarEventDao().getAll()
        val docs = db.documentDao().all()
        val previousRules = docs.filter { it.kind == "course" }.map { it.decode<RecurringCourse>() }.filter { it.semesterId == semester.id }
        val previousSemester = docs.firstOrNull { it.id == semester.id && it.kind == "semester" }?.decode<Semester>() ?: semester
        val effectiveCourses = courses.map { c ->
            val old = previousRules.firstOrNull { it.id == c.id }
            val dates = (c.exceptions + old?.exceptions.orEmpty()).distinct()
            val knownWeeks = (c.exceptionWeeks + old?.exceptionWeeks.orEmpty()).distinct()
            val weeks = if (knownWeeks.isNotEmpty()) knownWeeks else dates.map { occurrenceWeek(previousSemester, it) }.filter { it in 1..30 }.distinct()
            c.copy(exceptions = dates, exceptionWeeks = weeks)
        }
        val affectedIds = (previousRules.map { it.id } + courses.map { it.id }).toSet()
        val exceptions = effectiveCourses.associate { it.id to it.exceptions }
        val removed = all.filter { it.recurringCourseId in affectedIds && it.originalOccurrenceDate !in exceptions[it.recurringCourseId].orEmpty() }
        val keep = all - removed.toSet()
        val raw = effectiveCourses.flatMap { expandCourse(it, semester) }
        val seen = mutableSetOf<String>()
        fun key(e: CalendarEvent) = "${e.type}|${e.title}|${e.date}|${e.startTime}|${e.endTime}|${e.note.orEmpty()}"
        val generated = raw.filter { e -> seen.add(key(e)) && keep.none { key(it.toDomain()) == key(e) } }
        val conflicts = generated.flatMapIndexed { i, e ->
            (keep.map { it.toDomain() } + generated.take(i)).filter { overlaps(e, it) }.map { "${e.date} ${e.startTime} ${e.title} ↔ ${it.title} ${it.startTime}—${it.endTime}" }
        }.distinct()
        CoursePreview(semester, effectiveCourses, generated, removed, conflicts, raw.size - generated.size, all, docs)
    }

    suspend fun apply(preview: CoursePreview) = ReminderRuntime.calendarMutation {
        db.withTransaction {
            check(db.calendarEventDao().getAll() == preview.before && db.documentDao().all() == preview.beforeDocs) { "数据已经变化，请重新生成预览" }
            preview.removed.forEach { db.calendarEventDao().delete(it) }
            db.calendarEventDao().insertAll(preview.generated.map { it.toEntity() })
            db.documentDao().put(document(preview.semester.id, "semester", preview.semester))
            preview.beforeDocs.filter { it.kind == "course" && it.decode<RecurringCourse>().semesterId == preview.semester.id }.forEach { db.documentDao().delete(it.id) }
            preview.courses.forEach { db.documentDao().put(document(it.id, "course", it)) }
        }
    }

    /** 单次调课或停课先记录例外，批量重排不能复活或覆盖该次课程。 */
    suspend fun except(event: CalendarEvent, replacement: CalendarEvent?) = ReminderRuntime.calendarMutation {
        db.withTransaction {
            event.recurringCourseId?.let { id -> db.documentDao().get(id)?.let { row ->
                val rule = row.decode<RecurringCourse>()
                val semester = db.documentDao().get(rule.semesterId)?.decode<Semester>() ?: error("课程所属学期不存在")
                db.documentDao().put(document(id, "course", addExceptions(rule, semester, listOf(event.originalOccurrenceDate ?: event.date))))
            } }
            if (replacement == null) db.calendarEventDao().deleteById(event.id)
            else db.calendarEventDao().update(replacement.copy(recurringCourseId = event.recurringCourseId, originalOccurrenceDate = event.originalOccurrenceDate).toEntity())
        }
    }

    suspend fun removePlan(name: String) = ReminderRuntime.calendarMutation {
        db.withTransaction {
            val removed = db.calendarEventDao().getAll().filter { it.planName == name }
            removed.groupBy { it.recurringCourseId }.forEach { (id, rows) ->
                if (id != null) db.documentDao().get(id)?.let { row ->
                    val c = row.decode<RecurringCourse>()
                    val semester = db.documentDao().get(c.semesterId)?.decode<Semester>() ?: error("课程所属学期不存在")
                    db.documentDao().put(document(id, "course", addExceptions(c, semester, rows.map { it.originalOccurrenceDate ?: it.date })))
                }
            }
            db.calendarEventDao().deleteByPlanName(name)
        }
    }

    private fun occurrenceWeek(semester: Semester, date: String): Int =
        Math.floorDiv(java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(semester.startMonday), java.time.LocalDate.parse(date)), 7L).toInt() + 1

    private fun addExceptions(course: RecurringCourse, semester: Semester, dates: List<String>): RecurringCourse {
        val newDates = dates.filterNot { it in course.exceptions }
        val legacyWeeks = if (course.exceptionWeeks.isEmpty()) course.exceptions.map { occurrenceWeek(semester, it) } else emptyList()
        return course.copy(exceptions = (course.exceptions + dates).distinct(),
            exceptionWeeks = (course.exceptionWeeks + legacyWeeks + newDates.map { occurrenceWeek(semester, it) }).filter { it in 1..30 }.distinct())
    }
}

private object LocalDateGuard {
    fun notPastWeek(value: String) = java.time.LocalDate.parse(value) >= mondayOf(java.time.LocalDate.now())
}
