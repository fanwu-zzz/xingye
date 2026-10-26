package com.fanwu.studyplanner.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.repository.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertTrue
import java.time.LocalDate

/** 仅写入可卸载的 .f01test 应用，供界面检查使用的临时数据。 */
class FeatureVisualFixture {
    @Test fun prepareDisposableVisualData() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".f01test"))
        val db = AppDatabase.getInstance(context)
        val week = mondayOf(LocalDate.now())
        val semester = Semester("visual-semester", "界面验收学期", week.toString(), 4,
            listOf(PeriodTime(1, "08:00", "08:45"), PeriodTime(2, "08:55", "09:40")))
        val courses = listOf(RecurringCourse("visual-course-1", semester.id, "界面验收：数学", 1, "1-4", "1-2", "A101"),
            RecurringCourse("visual-course-2", semester.id, "界面验收：物理", 1, "1-4单周", "1-2", "B202"))
        val repo = LearningRepository(db)
        repo.apply(repo.preview(semester, courses))
        repo.saveGoal(GoalRevision(id = "visual-goal", goalId = "visual-goal", effectiveWeek = week.toString(), name = "界面验收：每周投入", minutes = 600))
        val draft = ReviewDraft(week.toString(), "界面验收：本地摘要", "界面验收示例建议；未调用 AI 服务。", listOf(CalendarEvent(title = "界面验收：复习数学", date = week.plusWeeks(1).toString(), startTime = "14:00", endTime = "15:00", type = EventType.STUDY_TASK)), id = "visual-review")
        db.documentDao().put(document(draft.id, "review", draft))
        assertTrue(db.calendarEventDao().getAll().any { it.recurringCourseId == "visual-course-1" })
    }
}
