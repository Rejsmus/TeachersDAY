package com.example.teacherday

import android.os.Environment
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.FileInputStream

class ScheduleParser {

    private val targetClasses = listOf(
        "5а", "5б", "5в",
        "6а", "6б", "6в", "6г",
        "7а", "7б", "7в",
        "8а", "8б", "8в",
        "9а", "9б", "9в", "9г",
        "10а", "11а"
    ).map { it.lowercase() }

    private val classForSpecialSubjects = "5б".lowercase()
    private val subjectKeywords = listOf("Труд", "Инф", "Разговоры", "Смелей")

    fun parseLatest(): Result {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val maxDir = File(downloadDir, "MAX")
        if (!maxDir.exists() || !maxDir.isDirectory) {
            return Result.Error("Папка MAX не найдена в Загрузках")
        }

        val pdfFiles = maxDir.listFiles { file ->
            file.isFile && file.name.lowercase().endsWith(".pdf")
        } ?: return Result.Error("Нет PDF-файлов в папке MAX")

        val latestFile = pdfFiles.maxByOrNull { it.lastModified() }
            ?: return Result.Error("Нет PDF-файлов в папке MAX")

        return try {
            val lessons = parsePdf(latestFile)
            Result.Success(lessons)
        } catch (e: Exception) {
            Result.Error("Ошибка парсинга: ${e.message}")
        }
    }

    private fun parsePdf(file: File): List<Lesson> {
        val foundLessons = mutableListOf<Lesson>()

        FileInputStream(file).use { inputStream ->
            PDDocument.load(inputStream).use { document ->
                for (pageIndex in 0 until document.numberOfPages) {
                    val textPositions = extractTextPositions(document, pageIndex + 1)
                    val lines = groupIntoLines(textPositions)

                    val headerIndices = mutableListOf<Int>()
                    for ((idx, line) in lines.withIndex()) {
                        if (isHeaderLine(line)) {
                            headerIndices.add(idx)
                        }
                    }

                    for (i in headerIndices.indices) {
                        val headerIdx = headerIndices[i]
                        val headerLine = lines[headerIdx]

                        val classColumns = getClassColumnsFromHeader(headerLine)
                        if (classColumns.isEmpty()) continue

                        val startIdx = headerIdx + 1
                        val endIdx = if (i + 1 < headerIndices.size) {
                            headerIndices[i + 1] - 1
                        } else {
                            lines.size - 1
                        }
                        val lessonLines = lines.subList(startIdx, endIdx + 1)
                        val lessonRows = detectLessonRows(lessonLines)

                        for (lessonRow in lessonRows) {
                            for ((className, bounds) in classColumns) {
                                val cellText = extractCellText(
                                    lessonRow.yTop, lessonRow.yBottom,
                                    bounds.left, bounds.right,
                                    lessonLines
                                )
                                if (cellText.isNotBlank()) {
                                    val lowerClassName = className.lowercase()
                                    if (lowerClassName in targetClasses) {
                                        val subject = findSubject(cellText, lowerClassName)
                                        if (subject != null) {
                                            foundLessons.add(Lesson(lowerClassName, lessonRow.number, subject))
                                            Log.d("ScheduleParser", "Найден урок: $lowerClassName, $subject, ${lessonRow.number}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        foundLessons.sortWith(compareBy<Lesson> { it.className }.thenBy { it.lessonNumber })
        return foundLessons
    }

    private fun isHeaderLine(line: Line): Boolean {
        val trimmed = line.text.trim()
        return trimmed.startsWith("№") && Regex("\\d{1,2}[а-я]").containsMatchIn(trimmed.lowercase())
    }

    private fun getClassColumnsFromHeader(headerLine: Line): Map<String, ColumnBounds> {
        val classPattern = Regex("\\d{1,2}[а-я]")
        val classMatches = classPattern.findAll(headerLine.text.lowercase()).toList()
        if (classMatches.size < 2) {
            Log.e("ScheduleParser", "Недостаточно классов в заголовке")
            return emptyMap()
        }

        val classNames = classMatches.map { it.value }
        Log.d("ScheduleParser", "Заголовок: '${headerLine.text}'")
        Log.d("ScheduleParser", "Найденные классы: $classNames")

        val firstPos = findExactPosition(headerLine, classNames[0]) ?: return emptyMap()
        val secondPos = findExactPosition(headerLine, classNames[1]) ?: return emptyMap()

        val firstMid = firstPos.x + firstPos.width / 2
        val secondMid = secondPos.x + secondPos.width / 2
        val cellWidth = secondMid - firstMid
        if (cellWidth <= 0) {
            Log.e("ScheduleParser", "cellWidth <= 0")
            return emptyMap()
        }

        val result = mutableMapOf<String, ColumnBounds>()
        for ((index, className) in classNames.withIndex()) {
            val pos = findExactPosition(headerLine, className) ?: continue
            val mid = pos.x + pos.width / 2
            val left = mid - cellWidth / 2
            val right = left + cellWidth
            result[className] = ColumnBounds(left, right)
            Log.d("ScheduleParser", "Класс $className: left=$left, right=$right")
        }
        return result
    }

    private fun findExactPosition(line: Line, text: String): TextPosition? {
        val lowerText = text.lowercase()
        val positions = line.positions
        for (i in positions.indices) {
            if (i + text.length <= positions.size) {
                val candidate = positions.subList(i, i + text.length)
                val candidateText = candidate.joinToString("") { it.unicode.lowercase() }
                if (candidateText == lowerText) {
                    val beforeOk = i == 0 || positions[i - 1].unicode.matches(Regex("\\s"))
                    val afterOk = i + text.length == positions.size || positions[i + text.length].unicode.matches(Regex("\\s"))
                    if (beforeOk && afterOk) {
                        return candidate.first()
                    }
                }
            }
        }
        return null
    }

    private fun extractTextPositions(document: PDDocument, pageNum: Int): List<TextPosition> {
        val positions = mutableListOf<TextPosition>()
        val stripper = object : PDFTextStripper() {
            init {
                setSortByPosition(true)
                setStartPage(pageNum)
                setEndPage(pageNum)
            }

            override fun writeString(text: String, textPositions: List<TextPosition>) {
                positions.addAll(textPositions)
            }
        }
        stripper.getText(document)
        return positions
    }

    private fun groupIntoLines(positions: List<TextPosition>): List<Line> {
        val sorted = positions.sortedBy { it.y }
        val lines = mutableListOf<Line>()
        val yThreshold = 5f

        var currentLine = mutableListOf<TextPosition>()
        for (pos in sorted) {
            if (currentLine.isEmpty()) {
                currentLine.add(pos)
            } else {
                val avgY = currentLine.map { it.y }.average().toFloat()
                if (Math.abs(pos.y - avgY) <= yThreshold) {
                    currentLine.add(pos)
                } else {
                    lines.add(createLine(currentLine))
                    currentLine = mutableListOf(pos)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(createLine(currentLine))
        }

        return lines.sortedBy { it.yTop }
    }

    private fun createLine(positions: List<TextPosition>): Line {
        val sortedByX = positions.sortedBy { it.x }
        val text = sortedByX.joinToString("") { it.unicode }
        val yTop = positions.minOf { it.y }
        val yBottom = positions.maxOf { it.y + it.height }
        return Line(yTop, yBottom, sortedByX, text)
    }

    private fun detectLessonRows(lines: List<Line>): List<LessonRow> {
        val lessonRows = mutableListOf<LessonRow>()
        for (line in lines) {
            val trimmed = line.text.trim()
            val match = Regex("^(\\d+)\\s").find(trimmed)
            if (match != null) {
                val number = match.groupValues[1].toInt()
                lessonRows.add(LessonRow(number, line.yTop, line.yBottom))
            }
        }
        return lessonRows
    }

    private fun extractCellText(
        yTop: Float, yBottom: Float,
        left: Float, right: Float,
        lines: List<Line>
    ): String {
        val cellChars = mutableListOf<TextPosition>()
        for (line in lines) {
            if (line.yBottom >= yTop && line.yTop <= yBottom) {
                for (pos in line.positions) {
                    if (pos.x >= left && pos.x <= right) {
                        cellChars.add(pos)
                    }
                }
            }
        }
        val sorted = cellChars.sortedWith(compareBy<TextPosition> { it.y }.thenBy { it.x })
        return sorted.joinToString("") { it.unicode }.trim()
    }

    private fun findSubject(cellText: String, className: String): String? {
        val cleaned = cellText.trim()
        for (keyword in subjectKeywords) {
            if (cleaned.contains(keyword, ignoreCase = false)) {
                when (keyword) {
                    "Труд" -> {
                        return if (className in listOf("5а", "5б", "5в", "6а", "6б", "6в", "6г")) "Труд"
                        else null
                    }
                    "Разговоры", "Смелей" -> {
                        return if (className == classForSpecialSubjects) {
                            when (keyword) {
                                "Разговоры" -> "Разговоры о важном"
                                "Смелей" -> "Смелей в профессию"
                                else -> keyword
                            }
                        } else null
                    }
                    "Инф" -> {
                        return "Информатика"
                    }
                }
            }
        }
        if (cleaned.contains("Информ", ignoreCase = false)) {
            return "Информатика"
        }
        return null
    }

    private data class Line(
        val yTop: Float,
        val yBottom: Float,
        val positions: List<TextPosition>,
        val text: String
    )

    private data class LessonRow(
        val number: Int,
        val yTop: Float,
        val yBottom: Float
    )

    private data class ColumnBounds(
        val left: Float,
        val right: Float
    )
}