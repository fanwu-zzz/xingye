package com.fanwu.studyplanner.ui.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.domain.CalendarEvent
import com.fanwu.studyplanner.ui.components.BrutalDialog
import com.fanwu.studyplanner.ui.components.BrutalLinkRow
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime

private fun minutes(value: String) = LocalTime.parse(value).let { it.hour * 60 + it.minute }
private val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")

/** 相连的重叠时段归为一组；窄屏通过选择列表查看每门课程。 */
internal fun overlappingCourseGroups(courses: List<CalendarEvent>): List<List<CalendarEvent>> {
    val groups = mutableListOf<MutableList<CalendarEvent>>()
    var groupEnd = -1
    courses.sortedWith(compareBy<CalendarEvent> { it.startTime }.thenBy { it.endTime }).forEach { event ->
        if (minutes(event.startTime) >= groupEnd) {
            groups.add(mutableListOf())
            groupEnd = -1
        }
        groups.last().add(event)
        groupEnd = maxOf(groupEnd, minutes(event.endTime))
    }
    return groups
}

/** 七天始终等宽铺满，纵向保持真实时间比例。 */
@Composable
fun CourseWeekGrid(monday: LocalDate, courses: List<CalendarEvent>, open: (CalendarEvent) -> Unit) {
    val start = minOf(8 * 60, courses.minOfOrNull { minutes(it.startTime) } ?: 480) / 60 * 60
    val end = maxOf(18 * 60, courses.maxOfOrNull { minutes(it.endTime) } ?: 1080).let { (it + 59) / 60 * 60 }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val minuteHeight = 0.8f * fontScale
    val headerHeight = (48 * fontScale).dp
    val bodyHeight = ((end - start) * minuteHeight + 16).dp
    var choosing by remember(monday, courses) { mutableStateOf<List<CalendarEvent>?>(null) }

    Text("一屏看全周 · 点击课程查看详情，重叠时可选择课程。", style = MaterialTheme.typography.bodySmall)
    BoxWithConstraints(Modifier.fillMaxWidth().border(2.dp, BrutalBlack).padding(2.dp).clipToBounds()) {
        val timeWidth = (32 * fontScale).dp
        val dayWidth = (maxWidth - timeWidth) / 7
        Column {
            Row(Modifier.fillMaxWidth().height(headerHeight).drawWithContent {
                drawContent()
                val axis = timeWidth.toPx()
                repeat(7) { index ->
                    val x = axis + dayWidth.toPx() * index
                    drawLine(BrutalBlack, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
                drawLine(BrutalBlack, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }) {
                Box(Modifier.width(timeWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("时间", style = TimetableTypography.detail)
                }
                repeat(7) { index ->
                    val date = monday.plusDays(index.toLong())
                    val today = date == LocalDate.now()
                    Column(Modifier.weight(1f).fillMaxHeight().background(if (today) BrutalYellow else BrutalPaper),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("周${weekdays[index]}", style = TimetableTypography.courseName)
                        Text(if (today) "今·${date.dayOfMonth}" else date.dayOfMonth.toString(), style = TimetableTypography.detail)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(bodyHeight).background(BrutalWhite).drawBehind {
                val axis = timeWidth.toPx()
                repeat(7) { index ->
                    val x = axis + dayWidth.toPx() * index
                    drawLine(BrutalBlack, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
                (start..end step 60).forEach { minute ->
                    val y = ((minute - start) * minuteHeight).dp.toPx()
                    drawLine(BrutalBlack, Offset(axis, y), Offset(size.width, y), 1.dp.toPx())
                }
            }) {
                Box(Modifier.width(timeWidth).fillMaxHeight().background(BrutalPaper)) {
                    (start..end step 60).forEach { minute ->
                        Text("%02d:00".format(minute / 60), Modifier.offset(y = ((minute - start) * minuteHeight).dp),
                            style = TimetableTypography.detail, maxLines = 1, softWrap = false)
                    }
                }
                repeat(7) { index ->
                    val date = monday.plusDays(index.toLong())
                    val daily = courses.filter { it.date == date.toString() }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        overlappingCourseGroups(daily).forEach { group ->
                            val groupStart = group.minOf { minutes(it.startTime) }
                            val groupEnd = group.maxOf { minutes(it.endTime) }
                            if (group.size > 1 && dayWidth < 96.dp) {
                                CourseGridBlock(group, Modifier.offset(y = ((groupStart - start) * minuteHeight).dp)
                                    .fillMaxWidth().height(((groupEnd - groupStart) * minuteHeight).dp), fontScale) { choosing = group }
                            } else {
                                val laneEnds = mutableListOf<Int>()
                                val positioned = group.map { event ->
                                    val lane = laneEnds.indexOfFirst { it <= minutes(event.startTime) }.let {
                                        if (it < 0) { laneEnds.add(0); laneEnds.lastIndex } else it
                                    }
                                    laneEnds[lane] = minutes(event.endTime)
                                    event to lane
                                }
                                val width = dayWidth / laneEnds.size.coerceAtLeast(1)
                                positioned.forEach { (event, lane) ->
                                    CourseGridBlock(listOf(event), Modifier.offset(x = width * lane, y = ((minutes(event.startTime) - start) * minuteHeight).dp)
                                        .width(width).height(((minutes(event.endTime) - minutes(event.startTime)) * minuteHeight).dp), fontScale) { open(event) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    choosing?.let { group ->
        BrutalDialog("选择重叠课程", { choosing = null }) {
            Text("${group.first().date} · 共 ${group.size} 门，点击查看完整信息。", style = MaterialTheme.typography.bodySmall)
            group.forEach { event ->
                BrutalLinkRow(event.title, "${event.startTime}—${event.endTime}" + event.note?.let { " · $it" }.orEmpty(),
                    { choosing = null; open(event) })
            }
        }
    }
}

@Composable
private fun CourseGridBlock(events: List<CalendarEvent>, modifier: Modifier, fontScale: Float, onClick: () -> Unit) {
    val event = events.first()
    BoxWithConstraints(modifier.padding(1.dp).background(BrutalCyan).border(1.dp, BrutalBlack)
        .semantics { contentDescription = events.joinToString("；") { "${it.title}，${it.date}，${it.startTime}至${it.endTime}，${it.note.orEmpty()}" } }
        .clickable(role = Role.Button, onClickLabel = if (events.size > 1) "选择重叠课程" else "查看课程详情", onClick = onClick)
        .clipToBounds().padding(horizontal = 2.dp, vertical = 4.dp)) {
        val lines = (maxHeight.value / (16 * fontScale)).toInt().coerceAtLeast(1)
        val roomLines = if (events.size == 1 && !event.note.isNullOrBlank() && lines >= 3) minOf(2, lines - 2) else 0
        Column {
            Text(if (events.size > 1) "${events.size}门\n重叠" else event.title,
                style = TimetableTypography.courseName, maxLines = (lines - roomLines).coerceIn(1, 6), overflow = TextOverflow.Ellipsis)
            if (roomLines > 0) {
                Text(event.note.orEmpty(), style = TimetableTypography.detail, maxLines = roomLines, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
