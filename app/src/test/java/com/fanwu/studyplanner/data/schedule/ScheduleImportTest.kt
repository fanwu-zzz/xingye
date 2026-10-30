package com.fanwu.studyplanner.data.schedule

import com.fanwu.studyplanner.domain.CourseSegment
import com.fanwu.studyplanner.domain.ScheduleImport
import com.fanwu.studyplanner.domain.expandCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 课表导入的核心逻辑测试：表格还原与周次展开都不依赖 Android 运行时
 */
class ScheduleImportTest {

    // ---------- ScheduleExpander ----------

    @Test
    fun `周次解析支持区间与逗号混排`() {
        assertEquals(listOf(5, 6, 8, 10, 12), ScheduleExpander.parseWeeks("5-6,8,10,12"))
        assertEquals((1..4) + (6..14), ScheduleExpander.parseWeeks("1-4,6-14周"))
    }

    @Test
    fun `周次解析支持单双周`() {
        assertEquals(listOf(1, 3, 5), ScheduleExpander.parseWeeks("1-5单周"))
        assertEquals(listOf(2, 4, 6), ScheduleExpander.parseWeeks("2-6双周"))
    }

    @Test
    fun `节次解析支持连堂`() {
        assertEquals(listOf(6, 7, 8, 9), ScheduleExpander.parsePeriods("6-9"))
        assertEquals(listOf(10, 11, 12, 13), ScheduleExpander.parsePeriods("10-13"))
    }

    @Test
    fun `课程按周次展开成具体日期`() {
        val course = CourseSegment(
            name = "计算机网络",
            dayOfWeek = 3,
            weeks = "1-4,6-14",
            periods = "6-7",
            room = "D1219"
        )
        val events = ScheduleExpander.expand(
            courses = listOf(course),
            startMonday = LocalDate.of(2025, 9, 1),
            periodTimes = emptyMap(),
            planName = "课表",
            today = LocalDate.of(2025, 9, 1)
        )

        assertEquals(13, events.size)
        // 第 1 周周三
        assertEquals("2025-09-03", events.first().date)
        assertEquals("14:00", events.first().startTime)
        assertEquals("15:40", events.first().endTime)
        assertEquals("D1219", events.first().note)
        assertEquals("课表", events.first().planName)
    }

    @Test
    fun `自定义作息覆盖默认值`() {
        val course = CourseSegment("操作系统", 2, "1", "3-4", "D1210")
        val events = ScheduleExpander.expand(
            courses = listOf(course),
            startMonday = LocalDate.of(2025, 9, 1),
            periodTimes = mapOf("3-4" to "10:10-11:50"),
            planName = "课表",
            today = LocalDate.of(2025, 9, 1)
        )

        assertEquals("10:10", events.single().startTime)
        assertEquals("11:50", events.single().endTime)
    }

    @Test
    fun `重复课程条目只生成一次事件`() {
        val course = CourseSegment("软件工程", 5, "1-2", "6-7", "D1340")
        val events = ScheduleExpander.expand(
            courses = listOf(course, course),
            startMonday = LocalDate.of(2025, 9, 1),
            periodTimes = emptyMap(),
            planName = "课表",
            today = LocalDate.of(2025, 9, 1)
        )

        assertEquals(2, events.size)
    }

    @Test
    fun `过去的课程不再生成事件`() {
        val course = CourseSegment("JAVA企业级应用", 4, "1-14", "1-2", "D1219")
        val events = ScheduleExpander.expand(
            courses = listOf(course),
            startMonday = LocalDate.of(2025, 9, 1),
            periodTimes = emptyMap(),
            planName = "课表",
            today = LocalDate.of(2025, 10, 1)
        )

        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.date >= "2025-10-01" })
    }

    // ---------- XlsxReader ----------

    @Test fun `导入连堂课保留原始规则并使用完整作息块边界`() {
        val value = ScheduleImport(semesterStartMonday = "2026-09-07",
            periodTimeTable = mapOf("6-7" to "14:10-15:50", "8-9" to "16:10-17:50"),
            courses = listOf(CourseSegment("操作系统", 1, "1-4,6-14", "6-9", "A101")))
        val (semester, courses) = managedScheduleImport(value, 42, "测试课表", LocalDate.parse("2026-10-26"))
        assertEquals("1-4,6-14", courses.single().weeks)
        val events = expandCourse(courses.single(), semester)
        assertEquals(7, events.size)
        assertEquals("2026-10-26", events.first().date)
        assertEquals("14:10", events.first().startTime)
        assertEquals("17:50", events.first().endTime)
    }

    @Test fun `不推测合并时间块内部单节作息`() {
        val value = ScheduleImport(semesterStartMonday = "2026-09-07",
            periodTimeTable = mapOf("6-9" to "14:10-17:50"),
            courses = listOf(CourseSegment("操作系统", 1, "1-14", "6-7")))
        val failure = runCatching { managedScheduleImport(value, 42, "测试课表") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message.orEmpty().contains("操作系统"))
        assertTrue(failure.message.orEmpty().contains("第 7 节结束"))
    }

    @Test
    fun `读取 xlsx 还原表格并保留空单元格对齐`() {
        val grid = XlsxReader.read(ByteArrayInputStream(sampleXlsx()))

        assertEquals("Sheet0", grid.sheetName)
        assertEquals(4, grid.columnCount)
        // 第 2 行：B 列空，C 列是课程，D 列空
        assertEquals(listOf("节次", "星期一", "星期二", "星期三"), grid.rows[0])
        assertEquals(listOf("一", "", "操作系统  [1-2周] [6-9] D1219", ""), grid.rows[1])
        assertEquals("二", grid.rows[2][0])
    }

    @Test
    fun `单元格内换行在提示词里用符号表示`() {
        val grid = XlsxReader.read(ByteArrayInputStream(sampleXlsx()))
        val text = XlsxReader.promptText(grid, "课表详情.xlsx")

        assertTrue(text.startsWith("【课表文件】课表详情.xlsx"))
        assertTrue(text.contains("⏎"))
        assertTrue(text.contains("#1"))
    }

    /** 构造一个结构最小但完整的 xlsx，覆盖 sharedStrings / inlineStr 两种文本存储 */
    private fun sampleXlsx(): ByteArray {
        val sharedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <si><t>节次</t></si>
              <si><t>星期一</t></si>
              <si><t>星期二</t></si>
              <si><t>星期三</t></si>
              <si><t>一</t></si>
              <si><t>二</t></si>
              <si><t>操作系统  [1-2周] [6-9] D1219</t></si>
              <si><t>JAVA企业级应用  [1-2周] [1-2] D1219&#10;计算机网络  [3周] [1-2] DS1404</t></si>
            </sst>
        """.trimIndent()

        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                  <c r="C1" t="s"><v>2</v></c>
                  <c r="D1" t="s"><v>3</v></c>
                </row>
                <row r="2">
                  <c r="A2" t="s"><v>4</v></c>
                  <c r="C2" t="s"><v>6</v></c>
                </row>
                <row r="3">
                  <c r="A3" t="s"><v>5</v></c>
                  <c r="B3" t="inlineStr"><is><t>inline 文本</t></is></c>
                  <c r="C3" t="s"><v>7</v></c>
                  <c r="D3"><v>42</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="Sheet0" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
        """.trimIndent()

        val relsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(workbookXml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(relsXml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedXml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
