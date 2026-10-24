package com.fanwu.studyplanner.data.schedule

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * xlsx 表格内容
 * @param rows 已按列补齐的二维文本，空单元格为空串
 * @param columnCount 列数
 */
data class SheetGrid(
    val sheetName: String,
    val rows: List<List<String>>,
    val columnCount: Int
)

private data class SheetRef(val name: String, val relId: String)

/**
 * 不依赖第三方库的 xlsx 读取器。
 * xlsx 本质是 zip 包，这里只解出自己需要的那几个 XML，不做任何语义判断——
 * 语义识别由 AI 完成，本类只负责把表格还原成二维文本。
 */
object XlsxReader {

    /** 单次最多发给 AI 的行数，避免异常文件把对话撑爆 */
    private const val MAX_ROWS_IN_PROMPT = 80

    fun read(input: InputStream): SheetGrid {
        val files = try {
            readZipEntries(input)
        } catch (e: Exception) {
            // 旧版 .xls 或损坏文件不是 zip 结构，给用户一个可操作的提示
            throw IllegalStateException("只支持 .xlsx 格式，请把课表另存为 .xlsx 后再导入", e)
        }
        val shared = files["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheets = files["xl/workbook.xml"]?.let(::parseSheetRefs).orEmpty()
        val rels = files["xl/_rels/workbook.xml.rels"]?.let(::parseRels).orEmpty()

        val firstSheet = sheets.firstOrNull()
        val sheetPath = firstSheet?.relId?.let { rels[it] }?.let(::resolvePath)
            ?: files.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") }
            ?: throw IllegalStateException("这个文件里没有找到工作表，请确认是 Excel 课表")

        val xml = files[sheetPath]
            ?: throw IllegalStateException("工作表内容读取失败，请重新导出后再试")

        val (rows, columnCount) = parseSheet(xml, shared)
        return SheetGrid(
            sheetName = firstSheet?.name ?: "Sheet1",
            rows = rows,
            columnCount = columnCount
        )
    }

    /** 把表格转成发给 AI 的纯文本：TSV 正文 + 行序号，保留列对齐 */
    fun promptText(grid: SheetGrid, fileName: String): String {
        val body = grid.rows.take(MAX_ROWS_IN_PROMPT)
        val builder = StringBuilder()
        builder.append("【课表文件】").append(fileName)
            .append(" ｜ 工作表：").append(grid.sheetName)
            .append(" ｜ ").append(grid.rows.size).append(" 行 × ")
            .append(grid.columnCount).append(" 列\n")
        builder.append("TSV 正文（单元格以制表符分隔，空单元格为空，单元格内换行用 ⏎ 表示）：\n\n")
        body.forEachIndexed { index, cells ->
            builder.append('#').append(index + 1)
            cells.forEach { cell ->
                builder.append('\t').append(cell.toSingleLine())
            }
            builder.append('\n')
        }
        if (grid.rows.size > MAX_ROWS_IN_PROMPT) {
            builder.append("\n（表格过长，仅提供前 $MAX_ROWS_IN_PROMPT 行）")
        }
        return builder.toString().trimEnd()
    }

    private fun String.toSingleLine(): String =
        replace(Regex("\\r\\n|\\n|\\r"), " ⏎ ")
            .replace('\t', ' ')
            .trim()

    private fun readZipEntries(input: InputStream): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith("xl/") && name.endsWith(".xml")) {
                    result[name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun document(xml: String) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun org.w3c.dom.Document.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /** 读取 workbook.xml 里的工作表清单（名称 + r:id） */
    private fun parseSheetRefs(xml: String): List<SheetRef> =
        document(xml).children("sheet").mapNotNull { element ->
            val name = element.getAttribute("name")
            val relId = element.getAttribute("r:id")
            if (name.isNotEmpty() && relId.isNotEmpty()) SheetRef(name, relId) else null
        }

    /** 读取 workbook.xml.rels，把 rId 映射到实际的 sheet 路径 */
    private fun parseRels(xml: String): Map<String, String> =
        document(xml).children("Relationship").mapNotNull { element ->
            val id = element.getAttribute("Id")
            val target = element.getAttribute("Target")
            if (id.isNotEmpty() && target.isNotEmpty()) id to target else null
        }.toMap()

    private fun resolvePath(target: String): String {
        val normalized = target.removePrefix("/")
        return if (normalized.startsWith("xl/")) normalized else "xl/$normalized"
    }

    /** 读取 sharedStrings.xml：xlsx 里文本单元格按索引存在这里 */
    private fun parseSharedStrings(xml: String): List<String> =
        document(xml).children("si").map { element ->
            element.children("t").joinToString("") { it.textContent.orEmpty() }
        }

    /** 解析工作表：返回二维文本与列数 */
    private fun parseSheet(xml: String, shared: List<String>): Pair<List<List<String>>, Int> {
        val cells = HashMap<Int, HashMap<Int, String>>()
        var maxRow = 0
        var maxCol = 0

        document(xml).children("row").forEachIndexed { rowIndex, rowElement ->
            val row = rowElement.getAttribute("r").toIntOrNull() ?: (rowIndex + 1)
            rowElement.children("c").forEachIndexed { cellIndex, cellElement ->
                val col = columnIndex(cellElement.getAttribute("r")) ?: (cellIndex + 1)
                val type = cellElement.getAttribute("t")
                val text = when (type) {
                    "s" -> cellElement.children("v").firstOrNull()
                        ?.textContent.orEmpty()
                        .toIntOrNull()
                        ?.let { shared.getOrNull(it) }
                        .orEmpty()

                    "inlineStr" -> cellElement.children("t")
                        .joinToString("") { it.textContent.orEmpty() }

                    else -> cellElement.children("v").firstOrNull()?.textContent.orEmpty()
                }
                if (text.isNotBlank()) {
                    cells.getOrPut(row) { HashMap() }[col] = text
                    if (col > maxCol) maxCol = col
                    if (row > maxRow) maxRow = row
                }
            }
        }

        if (maxRow == 0 || maxCol == 0) {
            throw IllegalStateException("工作表是空的，请确认导出的文件里有课表内容")
        }

        val rows = (1..maxRow).map { r ->
            val rowCells = cells[r]
            (1..maxCol).map { c -> rowCells?.get(c).orEmpty() }
        }

        // 去掉右侧与底部的整片空白，减少无效内容
        var lastCol = maxCol
        while (lastCol > 1 && rows.all { it[lastCol - 1].isBlank() }) lastCol--
        val trimmed = rows.map { it.take(lastCol) }
            .dropLastWhile { row -> row.all { it.isBlank() } }

        return trimmed to lastCol
    }

    /** "AB12" -> 28 */
    private fun columnIndex(reference: String): Int? {
        val letters = reference.takeWhile { it.isLetter() }
        if (letters.isEmpty()) return null
        var index = 0
        letters.uppercase().forEach { ch -> index = index * 26 + (ch - 'A' + 1) }
        return index
    }
}
