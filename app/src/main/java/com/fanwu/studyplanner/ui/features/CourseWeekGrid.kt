package com.fanwu.studyplanner.ui.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime

private fun minutes(value: String) = LocalTime.parse(value).let { it.hour * 60 + it.minute }

/** 每天独立分列，重叠课程保持可读且可分别点击。 */
@Composable
fun CourseWeekGrid(monday: LocalDate, courses: List<CalendarEvent>, open: (CalendarEvent) -> Unit) {
    val start = minOf(8 * 60, courses.minOfOrNull { minutes(it.startTime) } ?: 480) / 60 * 60
    val end = maxOf(18 * 60, courses.maxOfOrNull { minutes(it.endTime) } ?: 1080).let { (it + 59) / 60 * 60 }
    Text("周视图 · 可横向滚动，重叠课程分列；点击课程查看详情。", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).border(2.dp, BrutalBlack).padding(2.dp)) {
        Column(Modifier.width(56.dp)) {
            Spacer(Modifier.height(56.dp))
            Box(Modifier.height(((end - start) * 1.5f).dp)) {
                (start..end step 60).forEach { minute -> Text("%02d:00".format(minute / 60), Modifier.offset(y = ((minute - start) * 1.5f).dp), style = MaterialTheme.typography.labelSmall) }
            }
        }
        repeat(7) { index ->
            val date = monday.plusDays(index.toLong())
            val daily = courses.filter { it.date == date.toString() }.sortedBy { it.startTime }
            val ends = mutableListOf<Int>()
            val positioned = daily.map { event ->
                val lane = ends.indexOfFirst { it <= minutes(event.startTime) }.let { if (it < 0) { ends.add(0); ends.lastIndex } else it }
                ends[lane] = minutes(event.endTime)
                event to lane
            }
            val laneCount = ends.size.coerceAtLeast(1)
            val width = (160 * laneCount).dp
            Column(Modifier.width(width)) {
                Text("周${listOf("一", "二", "三", "四", "五", "六", "日")[index]} ${date.monthValue}/${date.dayOfMonth}", Modifier.fillMaxWidth().height(56.dp).background(BrutalYellow).border(1.dp, BrutalBlack).padding(8.dp))
                Box(Modifier.fillMaxWidth().height(((end - start) * 1.5f).dp).background(BrutalWhite)) {
                    (start until end step 60).forEach { minute -> HorizontalDivider(Modifier.offset(y = ((minute - start) * 1.5f).dp), color = BrutalBlack) }
                    positioned.forEach { (event, lane) ->
                        Column(Modifier.offset(x = (160 * lane).dp, y = ((minutes(event.startTime) - start) * 1.5f).dp)
                            .width(160.dp).height(((minutes(event.endTime) - minutes(event.startTime)) * 1.5f).dp)
                            .background(BrutalCyan).border(2.dp, BrutalBlack).clickable(role = Role.Button) { open(event) }.padding(8.dp)) {
                            Text(event.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text("${event.startTime}—${event.endTime}", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            event.note?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}
