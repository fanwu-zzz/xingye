package com.fanwu.studyplanner.ui.features

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.ui.components.*
import com.fanwu.studyplanner.ui.theme.*
import java.time.LocalDate
import java.util.UUID

@Composable
fun SemesterScreen(onBack: () -> Unit, vm: LearningViewModel = viewModel(factory = LearningViewModel.factory(LocalContext.current))) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.coursePreview.collectAsStateWithLifecycle()
    val semesters = docs.filter { it.kind == "semester" }.map { it.decode<Semester>() }.sortedByDescending { it.startMonday }
    val allCourses = docs.filter { it.kind == "course" }.map { it.decode<RecurringCourse>() }
    var selected by remember { mutableStateOf<String?>(null) }
    val semester = semesters.firstOrNull { it.id == selected } ?: semesters.firstOrNull()
    val courses = allCourses.filter { it.semesterId == semester?.id }
    var semesterEditor by remember { mutableStateOf(false) }
    var newSemester by remember { mutableStateOf(false) }
    var courseEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringCourse?>(null) }
    var deleting by remember { mutableStateOf<RecurringCourse?>(null) }
    BrutalPage("学期与重复课程", "预览后应用，支持单次调课和整门课程调整", onBack, modifier = Modifier.navigationBarsPadding()) {
        FeatureFeedback(vm)
        if (semesters.isEmpty()) BrutalEmpty("还没有学期", "先设置第一周周一和节次，再添加单双周课程。已有手动课程保持原样。")
        semesters.forEach { s -> BrutalChoice(s.name, s.id == semester?.id, { selected = s.id }, Modifier.fillMaxWidth()) }
        BrutalButton("新建学期", { newSemester = true; semesterEditor = true }, enabled = !busy)
        semester?.let { s ->
            BrutalPanel(color = BrutalCyan) {
                Text(s.name, style = MaterialTheme.typography.titleLarge)
                Text("第一周周一 ${s.startMonday} · ${s.weeks} 周")
                s.periods.sortedBy { it.number }.forEach { Text("第 ${if (it.number == it.through) it.number.toString() else "${it.number}-${it.through}"} 节  ${it.start}—${it.end}") }
            }
            BrutalButton("修改学期与节次", { newSemester = false; semesterEditor = true }, color = BrutalWhite, enabled = !busy)
            BrutalButton("添加重复课程", { editing = null; courseEditor = true }, color = BrutalWhite, enabled = !busy)
            if (courses.isEmpty()) Text("此学期没有重复课程；Excel 已展开的旧课程可以继续查看、单独编辑。")
            courses.forEach { c ->
                BrutalPanel {
                    Text(c.name, style = MaterialTheme.typography.titleLarge)
                    Text("周${c.day} · ${c.weeks} 周 · 第 ${c.periods} 节\n${c.room}")
                    if (c.from != null || c.until != null) Text("生效范围 ${c.from ?: "学期初"} 至 ${c.until ?: "学期末"}")
                    if (c.exceptions.isNotEmpty()) Text("已单独处理 ${c.exceptions.size} 次；批量重排保留单次调整。")
                    TextButton({ editing = c; courseEditor = true }, enabled = !busy) { Text("编辑整门 / 此次及以后", color = BrutalBlack) }
                    TextButton({ deleting = c }, enabled = !busy) { Text("删除整门课程", color = BrutalDanger) }
                }
            }
        }
        Text("单次调课或停课：在课表或日历中编辑 / 删除当天课程。此页批量操作只修改关联规则的课程，不修改手动任务和考试。")
    }
    if (semesterEditor) SemesterDialog(if (newSemester) null else semester, { semesterEditor = false }) { s ->
        vm.run { vm.coursePreview.value = vm.repository.preview(s, if (newSemester) emptyList() else courses) }
        selected = s.id; semesterEditor = false
    }
    if (courseEditor && semester != null) CourseDialog(editing, semester, { courseEditor = false }) { c, pivot ->
        val old = editing
        val next = if (old != null && pivot != null) courses.filterNot { it.id == old.id } + old.copy(until = LocalDate.parse(pivot).minusDays(1).toString()) + c.copy(id = UUID.randomUUID().toString(), from = pivot)
            else courses.filterNot { it.id == c.id } + c
        vm.run { vm.coursePreview.value = vm.repository.preview(semester, next) }
        courseEditor = false
    }
    deleting?.let { c -> BrutalConfirm("删除整门课程？", "将预览移除「${c.name}」及其关联的单次调整。其他课程不受影响。", "查看删除预览", {
        semester?.let { s -> vm.run { vm.coursePreview.value = vm.repository.preview(s, courses.filterNot { it.id == c.id }) } }; deleting = null
    }, { deleting = null }, true) }
    preview?.let { p -> CoursePreviewDialog(p, busy, { vm.coursePreview.value = null }) {
        vm.run { vm.repository.apply(p); vm.coursePreview.value = null; vm.message.value = "课表已更新" }
    } }
}

@Composable
private fun SemesterDialog(initial: Semester?, dismiss: () -> Unit, save: (Semester) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "新学期") }
    var start by remember { mutableStateOf(initial?.startMonday ?: mondayOf(LocalDate.now()).toString()) }
    var weeks by remember { mutableStateOf(initial?.weeks?.toString() ?: "18") }
    var periods by remember { mutableStateOf(initial?.periods?.joinToString("\n") { "${if (it.number == it.through) it.number.toString() else "${it.number}-${it.through}"},${it.start},${it.end}" } ?: "1,08:00,08:45\n2,08:55,09:40\n3,10:00,10:45\n4,10:55,11:40\n5,14:00,14:45\n6,14:55,15:40\n7,16:00,16:45\n8,16:55,17:40") }
    var error by remember { mutableStateOf<String?>(null) }
    BrutalDialog("学期与节次", dismiss, actions = {
        BrutalButton("预览学期变更", {
            runCatching {
                val rows = periods.lines().filter { it.isNotBlank() }.map { line ->
                    val fields = line.replace('，', ',').split(',').map(String::trim)
                    require(fields.size == 3) { "每行格式为：编号,开始时间,结束时间" }
                    val range = fields[0].split('-').map { it.toInt() }
                    require(range.size in 1..2)
                    PeriodTime(range.first(), fields[1], fields[2], range.last())
                }
                Semester(initial?.id ?: UUID.randomUUID().toString(), name.trim(), start, weeks.toInt(), rows.sortedBy { it.number }).also { it.validate() }
            }.onSuccess(save).onFailure { error = it.message ?: "输入有误" }
        })
    }) {
        BrutalField(name, { name = it }, "学期名称")
        BrutalButton("选择开学日期：$start", {
            val d = LocalDate.parse(start)
            DatePickerDialog(context, { _, y, m, day -> start = mondayOf(LocalDate.of(y, m + 1, day)).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }, color = BrutalWhite)
        Text("选择日期后，自动定位到所在教学周的周一。")
        BrutalField(weeks, { weeks = it }, "学期周数 1—30")
        BrutalField(periods, { periods = it }, "节次：每行 编号,开始,结束", singleLine = false)
        Text("可增删或修改任意行；保存按编号排序，重复编号、时间重叠会阻止保存。修改将重新预览关联课程。")
        error?.let { Text(it, color = BrutalDanger) }
    }
}

@Composable
private fun CourseDialog(initial: RecurringCourse?, semester: Semester, dismiss: () -> Unit, save: (RecurringCourse, String?) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var day by remember { mutableStateOf(initial?.day?.toString() ?: "1") }
    var weeks by remember { mutableStateOf(initial?.weeks ?: "1-${semester.weeks}") }
    var periods by remember { mutableStateOf(initial?.periods ?: "1-2") }
    var room by remember { mutableStateOf(initial?.room.orEmpty()) }
    var future by remember { mutableStateOf(false) }
    var pivot by remember { mutableStateOf(LocalDate.now().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    BrutalDialog("重复课程", dismiss, actions = {
        BrutalButton("预览课程安排", {
            runCatching {
                val c = RecurringCourse(initial?.id ?: UUID.randomUUID().toString(), semester.id, name.trim(), day.toInt(), weeks.trim(), periods.trim(), room.trim(),
                    initial?.from, initial?.until, initial?.exceptions.orEmpty(), initial?.source, initial?.exceptionWeeks.orEmpty())
                expandCourse(c, semester)
                if (future) {
                    val date = LocalDate.parse(pivot)
                    require(date >= LocalDate.parse(semester.startMonday) && date < LocalDate.parse(semester.startMonday).plusWeeks(semester.weeks.toLong())) { "调整日期必须位于学期内" }
                    require((initial?.from == null || pivot >= initial.from) && (initial?.until == null || pivot <= initial.until)) { "调整日期必须位于此段课程的生效范围内" }
                }
                c
            }.onSuccess { save(it, pivot.takeIf { future }) }.onFailure { error = it.message ?: "输入有误" }
        })
    }) {
        BrutalField(name, { name = it }, "课程名称")
        BrutalField(day, { day = it }, "星期 1—7（周一至周日）")
        BrutalField(weeks, { weeks = it }, "周次，例如 1-16单周 / 2-16双周 / 1-4,6-16")
        BrutalField(periods, { periods = it }, "节次，例如 1-2")
        BrutalField(room, { room = it }, "教室（可选）")
        if (initial != null) {
            BrutalChoice("整门课程", !future, { future = false }, Modifier.fillMaxWidth())
            BrutalChoice("指定日期及以后", future, { future = true }, Modifier.fillMaxWidth())
            if (future) BrutalField(pivot, { pivot = it }, "从 YYYY-MM-DD 起调整")
        }
        error?.let { Text(it, color = BrutalDanger) }
    }
}

@Composable
fun CoursePreviewDialog(p: CoursePreview, busy: Boolean, dismiss: () -> Unit, apply: () -> Unit) {
    BrutalDialog("课表变更预览", dismiss, actions = {
        BrutalButton("确认应用课表", apply, enabled = !busy)
        TextButton(dismiss, enabled = !busy) { Text("取消，不保存", color = BrutalBlack) }
    }) {
        Text("${p.semester.name} · 将移除关联旧课程 ${p.removed.size} 次，生成 ${p.generated.size} 次；完全重复跳过 ${p.duplicates} 次。")
        Text("检测到 ${p.conflicts.size} 处时间冲突；确认后保留冲突课程并在课表并列显示。已有任务、考试及未关联课程不会被覆盖。")
        p.conflicts.take(30).forEach { Text(it, color = BrutalDanger) }
        if (p.conflicts.size > 30) Text("其余 ${p.conflicts.size - 30} 处冲突，请在应用后课表查看；可取消并调整规则。")
        p.courses.forEach { Text("原始规则：${it.name} · 周${it.day} · ${it.weeks}周 · ${it.periods}节") }
        p.generated.forEach { Text("${it.date} ${it.startTime}—${it.endTime} ${it.title} ${it.note.orEmpty()}") }
    }
}
