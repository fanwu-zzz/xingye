package com.fanwu.studyplanner.data.schedule

import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.domain.CourseSegment
import com.fanwu.studyplanner.domain.EventType
import java.time.LocalDate
import java.time.LocalTime

/**
 * 把 AI 识别出的课程段展开成具体的日历事件。
 * 周次展开、节次换时间、日期计算都在这里完成——这部分必须由代码做，交给模型一定会算错。
 */
object ScheduleExpander {

    /** 默认作息：第 n 节的起止时间，用户没提供时用它兜底 */
    private val DEFAULT_START = listOf(
        "08:00", "08:55", "10:00", "10:55", "11:50", "14:00", "14:55",
        "16:00", "16:55", "18:30", "19:25", "20:20", "21:15"
    )
    private val DEFAULT_END = listOf(
        "08:45", "09:40", "10:45", "11:40", "12:35", "14:45", "15:40",
        "16:45", "17:40", "19:15", "20:10", "21:05", "22:00"
    )

    /**
     * @param startMonday 学期第 1 周的周一
     * @param periodTimes 节次到时间的映射，如 {"6-7": "14:00-15:40"}
     * @param today 早于今天的课程不再生成，避免一学期几百条历史数据
     * @return 按日期时间排序、已去重的事件列表
     */
    fun expand(
        courses: List<CourseSegment>,
        startMonday: LocalDate,
        periodTimes: Map<String, String>,
        planName: String,
        today: LocalDate = LocalDate.now()
    ): List<CalendarEvent> {
        val events = LinkedHashMap<String, CalendarEvent>()

        for (course in courses) {
            val name = course.name.trim()
            if (name.isEmpty()) continue
            val day = course.dayOfWeek.takeIf { it in 1..7 } ?: continue

            val weeks = parseWeeks(course.weeks).filter { it in 1..30 }
            val periods = parsePeriods(course.periods)
            if (weeks.isEmpty() || periods.isEmpty()) continue

            val (start, end) = resolveTime(periods, periodTimes) ?: continue
            val room = course.room.trim()

            for (week in weeks) {
                val date = startMonday.plusDays((week - 1) * 7L + (day - 1))
                if (date.isBefore(today)) continue
                val key = "$date|$start|$name"
                if (events.containsKey(key)) continue
                events[key] = CalendarEvent(
                    title = name,
                    date = date.toString(),
                    startTime = start,
                    endTime = end,
                    type = EventType.CLASS,
                    note = room.takeIf { it.isNotEmpty() },
                    planName = planName
                )
            }
        }

        return events.values.sortedWith(compareBy({ it.date }, { it.startTime }))
    }

    /** "1-4,6-14" -> [1,2,3,4,6,...,14]，同时支持「1-15单周」「2-16双周」 */
    fun parseWeeks(raw: String): List<Int> {
        val cleaned = raw.replace("周", "").trim()
        if (cleaned.isEmpty()) return emptyList()

        val oddOnly = cleaned.contains("单")
        val evenOnly = cleaned.contains("双")

        val result = mutableListOf<Int>()
        cleaned.split(',', '，', '、').forEach { part ->
            val token = part.replace("单", "").replace("双", "").trim()
            if (token.isEmpty()) return@forEach
            val range = Regex("^(\\d+)\\s*-\\s*(\\d+)$").find(token)
            if (range != null) {
                val from = range.groupValues[1].toInt()
                val to = range.groupValues[2].toInt()
                if (from <= to && to - from <= 60) {
                    for (w in from..to) {
                        if (!matchesParity(w, oddOnly, evenOnly)) continue
                        result += w
                    }
                }
            } else {
                token.toIntOrNull()?.let { if (matchesParity(it, oddOnly, evenOnly)) result += it }
            }
        }
        return result.distinct().sorted()
    }

    private fun matchesParity(week: Int, oddOnly: Boolean, evenOnly: Boolean): Boolean = when {
        oddOnly && evenOnly -> true
        oddOnly -> week % 2 == 1
        evenOnly -> week % 2 == 0
        else -> true
    }

    /** "6-9" -> [6,7,8,9] */
    fun parsePeriods(raw: String): List<Int> {
        val cleaned = raw.replace("节", "").replace("第", "").trim()
        if (cleaned.isEmpty()) return emptyList()

        val result = mutableListOf<Int>()
        cleaned.split(',', '，', '、').forEach { part ->
            val token = part.trim()
            if (token.isEmpty()) return@forEach
            val range = Regex("^(\\d+)\\s*-\\s*(\\d+)$").find(token)
            if (range != null) {
                val from = range.groupValues[1].toInt()
                val to = range.groupValues[2].toInt()
                if (from in 1..24 && to in 1..24 && from <= to) result += (from..to)
            } else {
                token.toIntOrNull()?.takeIf { it in 1..24 }?.let { result += it }
            }
        }
        return result.distinct().sorted()
    }

    private fun resolveTime(periods: List<Int>, table: Map<String, String>): Pair<String, String>? {
        val first = periods.first()
        val last = periods.last()

        parseRange(table["$first-$last"])?.let { return it }

        // 连堂课程可以跨多个完整作息块，但不能推测块内某一节的时间。
        val blocks = table.mapNotNull { (key, value) ->
            val numbers = parsePeriods(key)
            val time = parseRange(value)
            if (numbers.isEmpty() || time == null) null else numbers to time
        }
        val blockStart = blocks.firstOrNull { it.first.first() == first }?.second?.first
        val blockEnd = blocks.firstOrNull { it.first.last() == last }?.second?.second
        if (blockStart != null && blockEnd != null && blockEnd > blockStart) return blockStart to blockEnd

        val start = parseRange(table[first.toString()])?.first ?: DEFAULT_START.getOrNull(first - 1)
            ?: return null
        val end = parseRange(table[last.toString()])?.second ?: DEFAULT_END.getOrNull(last - 1)
            ?: return null
        return if (end > start) start to end else null
    }

    /** "08:00-09:40" -> ("08:00", "09:40")，非法格式返回 null */
    private fun parseRange(raw: String?): Pair<String, String>? {
        if (raw == null) return null
        val parts = raw.split('-', '~', '–').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val start = runCatching { LocalTime.parse(parts[0]) }.getOrNull() ?: return null
        val end = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return null
        if (!end.isAfter(start)) return null
        return "%02d:%02d".format(start.hour, start.minute) to
            "%02d:%02d".format(end.hour, end.minute)
    }
}
