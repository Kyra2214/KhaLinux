package com.khalinux.runtime

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

data class TerminalStyle(
    val foreground: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val strikeThrough: Boolean = false
)

data class TerminalCell(
    val text: String = " ",
    val style: TerminalStyle = TerminalStyle()
)

data class TerminalSnapshot(
    val lines: List<List<TerminalCell>>,
    val cursorRow: Int,
    val cursorColumn: Int,
    val cursorVisible: Boolean,
    val rows: Int,
    val columns: Int
) {
    fun displayRange(): IntRange {
        val firstContent = lines.indexOfFirst { line -> line.any { it.text != " " || it.style != TerminalStyle() } }
        val cursor = cursorRow.coerceIn(0, lines.lastIndex)
        val start = if (firstContent >= 0) min(firstContent, cursor) else cursor
        val lastContent = lines.indexOfLast { line -> line.any { it.text != " " || it.style != TerminalStyle() } }
        val end = max(lastContent, cursor).coerceIn(start, lines.lastIndex)
        return start..end
    }

    fun plainText(): String {
        val range = displayRange()
        return range.joinToString("\n") { rowIndex ->
            val line = lines[rowIndex]
            val end = line.indexOfLast { it.text != " " || it.style != TerminalStyle() } + 1
            if (end <= 0) "" else line.take(end).joinToString("") { it.text }
        }
    }

    companion object {
        fun empty(rows: Int = 24, columns: Int = 80): TerminalSnapshot {
            val blank = List(columns) { TerminalCell() }
            return TerminalSnapshot(List(rows) { blank }, 0, 0, true, rows, columns)
        }
    }
}

/**
 * Bounded VT/ANSI terminal subset for shell output.
 * Supports UTF-8 streaming, cursor movement, erase, SGR colors/styles and common controls.
 * It is not a complete xterm emulator.
 */
class TerminalEmulator(
    rows: Int = 24,
    columns: Int = 80,
    private val maxScrollbackLines: Int = 1_000
) {
    private enum class ParserState { NORMAL, ESCAPE, CSI, OSC, OSC_ESCAPE, CHARSET }

    private var screenRows = rows
    private var screenColumns = columns
    private val lines = mutableListOf<MutableList<TerminalCell>>()
    private var cursorRow = 0
    private var cursorColumn = 0
    private var savedCursorRow = 0
    private var savedCursorColumn = 0
    private var cursorVisible = true
    private var autoWrap = true
    private var currentStyle = TerminalStyle()
    private var parserState = ParserState.NORMAL
    private val csi = StringBuilder()
    private var pendingHighSurrogate: Char? = null

    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pendingUtf8 = ByteArray(0)

    init {
        require(rows in 1..200) { "rows fora do intervalo suportado" }
        require(columns in 1..400) { "columns fora do intervalo suportado" }
        require(maxScrollbackLines >= 0) { "maxScrollbackLines não pode ser negativo" }
        repeat(rows) { lines += blankLine() }
    }

    @Synchronized
    fun append(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val combined = ByteArray(pendingUtf8.size + bytes.size)
        pendingUtf8.copyInto(combined)
        bytes.copyInto(combined, pendingUtf8.size)

        val input = ByteBuffer.wrap(combined)
        val output = CharBuffer.allocate(combined.size + 4)
        while (true) {
            val result = decoder.decode(input, output, false)
            if (result.isError) result.throwException()
            if (result.isOverflow) {
                output.flip()
                processChars(output)
                output.clear()
                continue
            }
            break
        }
        output.flip()
        processChars(output)
        pendingUtf8 = ByteArray(input.remaining())
        input.get(pendingUtf8)
    }

    @Synchronized
    fun appendText(text: String) {
        finishInput()
        processChars(CharBuffer.wrap(text))
    }

    @Synchronized
    fun finishInput() {
        val input = ByteBuffer.wrap(pendingUtf8)
        val output = CharBuffer.allocate(pendingUtf8.size + 4)
        while (true) {
            val result = decoder.decode(input, output, true)
            if (result.isError) result.throwException()
            if (!result.isOverflow) break
            output.flip()
            processChars(output)
            output.clear()
        }
        while (true) {
            val result = decoder.flush(output)
            if (result.isError) result.throwException()
            if (!result.isOverflow) break
            output.flip()
            processChars(output)
            output.clear()
        }
        output.flip()
        processChars(output)
        pendingUtf8 = ByteArray(0)
        decoder.reset()
        pendingHighSurrogate?.let {
            putText("\uFFFD")
            pendingHighSurrogate = null
        }
    }

    @Synchronized
    fun resize(rows: Int, columns: Int) {
        require(rows in 1..200) { "rows fora do intervalo suportado" }
        require(columns in 1..400) { "columns fora do intervalo suportado" }
        screenRows = rows
        screenColumns = columns
        lines.forEach { line ->
            if (line.size > columns) line.subList(columns, line.size).clear()
            while (line.size < columns) line += TerminalCell()
        }
        while (lines.size < rows) lines += blankLine()
        trimScrollback()
        cursorRow = cursorRow.coerceIn(screenStart(), lines.lastIndex)
        cursorColumn = cursorColumn.coerceIn(0, columns - 1)
    }

    @Synchronized
    fun reset() {
        lines.clear()
        repeat(screenRows) { lines += blankLine() }
        cursorRow = 0
        cursorColumn = 0
        savedCursorRow = 0
        savedCursorColumn = 0
        cursorVisible = true
        autoWrap = true
        currentStyle = TerminalStyle()
        parserState = ParserState.NORMAL
        csi.setLength(0)
        pendingHighSurrogate = null
        pendingUtf8 = ByteArray(0)
        decoder.reset()
    }

    @Synchronized
    fun snapshot(): TerminalSnapshot = TerminalSnapshot(
        lines = lines.map { it.toList() },
        cursorRow = cursorRow,
        cursorColumn = cursorColumn,
        cursorVisible = cursorVisible,
        rows = screenRows,
        columns = screenColumns
    )

    @Synchronized
    fun plainText(): String = snapshot().plainText()

    private fun processChars(chars: CharBuffer) {
        while (chars.hasRemaining()) processChar(chars.get())
    }

    private fun processChar(char: Char) {
        val high = pendingHighSurrogate
        if (high != null) {
            pendingHighSurrogate = null
            if (Character.isLowSurrogate(char)) {
                putText("$high$char")
                return
            }
            putText("\uFFFD")
        }
        if (Character.isHighSurrogate(char)) {
            pendingHighSurrogate = char
            return
        }
        if (Character.isLowSurrogate(char)) {
            putText("\uFFFD")
            return
        }

        when (parserState) {
            ParserState.NORMAL -> processNormal(char)
            ParserState.ESCAPE -> processEscape(char)
            ParserState.CSI -> processCsi(char)
            ParserState.OSC -> {
                if (char == '\u0007') parserState = ParserState.NORMAL
                else if (char == '\u001B') parserState = ParserState.OSC_ESCAPE
            }
            ParserState.OSC_ESCAPE -> {
                parserState = if (char == '\\') ParserState.NORMAL else ParserState.OSC
            }
            ParserState.CHARSET -> parserState = ParserState.NORMAL
        }
    }

    private fun processNormal(char: Char) {
        when (char) {
            '\u001B' -> parserState = ParserState.ESCAPE
            '\r' -> cursorColumn = 0
            '\n' -> lineFeed()
            '\b' -> cursorColumn = max(0, cursorColumn - 1)
            '\t' -> cursorColumn = min(screenColumns - 1, ((cursorColumn / 8) + 1) * 8)
            '\u0007' -> Unit
            else -> if (char >= ' ') putText(char.toString())
        }
    }

    private fun processEscape(char: Char) {
        parserState = ParserState.NORMAL
        when (char) {
            '[' -> {
                csi.setLength(0)
                parserState = ParserState.CSI
            }
            ']' -> parserState = ParserState.OSC
            '7' -> saveCursor()
            '8' -> restoreCursor()
            'c' -> reset()
            'D' -> lineFeed()
            'E' -> {
                cursorColumn = 0
                lineFeed()
            }
            'M' -> reverseIndex()
            '(', ')' -> parserState = ParserState.CHARSET
            else -> Unit
        }
    }

    private fun processCsi(char: Char) {
        if (char in '@'..'~') {
            executeCsi(csi.toString(), char)
            csi.setLength(0)
            parserState = ParserState.NORMAL
        } else if (csi.length < 80 && char >= ' ') {
            csi.append(char)
        } else {
            csi.setLength(0)
            parserState = ParserState.NORMAL
        }
    }

    private fun executeCsi(raw: String, final: Char) {
        val privateMode = raw.startsWith("?")
        val body = raw.removePrefix("?").removePrefix(">")
        val params = if (body.isEmpty()) listOf(0) else body.split(';').map { it.toIntOrNull() ?: 0 }
        fun param(index: Int, default: Int = 1): Int =
            params.getOrNull(index)?.let { if (it == 0) default else it } ?: default

        when (final) {
            'A' -> cursorRow = max(screenStart(), cursorRow - param(0))
            'B' -> cursorRow = min(lines.lastIndex, cursorRow + param(0))
            'C' -> cursorColumn = min(screenColumns - 1, cursorColumn + param(0))
            'D' -> cursorColumn = max(0, cursorColumn - param(0))
            'E' -> {
                cursorRow = min(lines.lastIndex, cursorRow + param(0))
                cursorColumn = 0
            }
            'F' -> {
                cursorRow = max(screenStart(), cursorRow - param(0))
                cursorColumn = 0
            }
            'G' -> cursorColumn = (param(0) - 1).coerceIn(0, screenColumns - 1)
            'd' -> setCursor(param(0), cursorColumn + 1)
            'H', 'f' -> setCursor(param(0), param(1))
            'J' -> eraseDisplay(params.firstOrNull() ?: 0)
            'K' -> eraseLine(params.firstOrNull() ?: 0)
            'm' -> applySgr(params)
            's' -> saveCursor()
            'u' -> restoreCursor()
            '@' -> insertCharacters(param(0))
            'P' -> deleteCharacters(param(0))
            'X' -> eraseCharacters(param(0))
            'L' -> insertLines(param(0))
            'M' -> deleteLines(param(0))
            'S' -> repeat(param(0)) { scrollUp() }
            'T' -> repeat(param(0)) { scrollDown() }
            'h', 'l' -> if (privateMode) {
                if (25 in params) cursorVisible = final == 'h'
                if (7 in params) autoWrap = final == 'h'
            }
            'c', 'n', 'r', 'q' -> Unit
            else -> Unit
        }
    }

    private fun applySgr(params: List<Int>) {
        var index = 0
        while (index < params.size) {
            when (val code = params[index]) {
                0 -> currentStyle = TerminalStyle()
                1 -> currentStyle = currentStyle.copy(bold = true)
                2 -> currentStyle = currentStyle.copy(dim = true)
                3 -> currentStyle = currentStyle.copy(italic = true)
                4 -> currentStyle = currentStyle.copy(underline = true)
                7 -> currentStyle = currentStyle.copy(inverse = true)
                9 -> currentStyle = currentStyle.copy(strikeThrough = true)
                22 -> currentStyle = currentStyle.copy(bold = false, dim = false)
                23 -> currentStyle = currentStyle.copy(italic = false)
                24 -> currentStyle = currentStyle.copy(underline = false)
                27 -> currentStyle = currentStyle.copy(inverse = false)
                29 -> currentStyle = currentStyle.copy(strikeThrough = false)
                39 -> currentStyle = currentStyle.copy(foreground = null)
                49 -> currentStyle = currentStyle.copy(background = null)
                in 30..37 -> currentStyle = currentStyle.copy(foreground = ansiPalette(code - 30))
                in 40..47 -> currentStyle = currentStyle.copy(background = ansiPalette(code - 40))
                in 90..97 -> currentStyle = currentStyle.copy(foreground = ansiPalette(code - 90 + 8))
                in 100..107 -> currentStyle = currentStyle.copy(background = ansiPalette(code - 100 + 8))
                38, 48 -> {
                    val foreground = code == 38
                    val mode = params.getOrNull(index + 1)
                    val color = when (mode) {
                        5 -> params.getOrNull(index + 2)?.let(::color256).also { index += 2 }
                        2 -> {
                            val red = params.getOrNull(index + 2)
                            val green = params.getOrNull(index + 3)
                            val blue = params.getOrNull(index + 4)
                            index += 4
                            if (red != null && green != null && blue != null) rgb(red, green, blue) else null
                        }
                        else -> null
                    }
                    if (color != null) {
                        currentStyle = if (foreground) currentStyle.copy(foreground = color)
                        else currentStyle.copy(background = color)
                    }
                }
            }
            index++
        }
    }

    private fun ansiPalette(index: Int): Int = ANSI_COLORS[index.coerceIn(0, 15)]

    private fun color256(index: Int): Int {
        val value = index.coerceIn(0, 255)
        if (value < 16) return ansiPalette(value)
        if (value >= 232) {
            val gray = 8 + (value - 232) * 10
            return rgb(gray, gray, gray)
        }
        val cube = value - 16
        val red = cube / 36
        val green = (cube % 36) / 6
        val blue = cube % 6
        val levels = intArrayOf(0, 95, 135, 175, 215, 255)
        return rgb(levels[red], levels[green], levels[blue])
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

    private fun putText(text: String) {
        if (cursorColumn >= screenColumns) {
            if (autoWrap) {
                cursorColumn = 0
                lineFeed()
            } else {
                cursorColumn = screenColumns - 1
            }
        }
        val line = lines[cursorRow]
        line[cursorColumn] = TerminalCell(text, currentStyle)
        cursorColumn++
    }

    private fun lineFeed() {
        if (cursorRow < lines.lastIndex) {
            cursorRow++
        } else {
            lines += blankLine()
            cursorRow = lines.lastIndex
            trimScrollback()
        }
    }

    private fun reverseIndex() {
        val start = screenStart()
        if (cursorRow > start) {
            cursorRow--
        } else {
            lines.add(start, blankLine())
            lines.removeAt(start + screenRows)
        }
    }

    private fun scrollUp() {
        lines += blankLine()
        cursorRow = min(lines.lastIndex, cursorRow + 1)
        trimScrollback()
    }

    private fun scrollDown() {
        val start = screenStart()
        lines.add(start, blankLine())
        lines.removeAt(start + screenRows)
    }

    private fun setCursor(row: Int, column: Int) {
        cursorRow = (screenStart() + row - 1).coerceIn(screenStart(), lines.lastIndex)
        cursorColumn = (column - 1).coerceIn(0, screenColumns - 1)
    }

    private fun eraseDisplay(mode: Int) {
        val start = screenStart()
        when (mode) {
            0 -> {
                eraseRange(cursorRow, cursorColumn, screenColumns)
                for (row in cursorRow + 1..lines.lastIndex) eraseRange(row, 0, screenColumns)
            }
            1 -> {
                for (row in start until cursorRow) eraseRange(row, 0, screenColumns)
                eraseRange(cursorRow, 0, cursorColumn + 1)
            }
            2 -> for (row in start..lines.lastIndex) eraseRange(row, 0, screenColumns)
            3 -> {
                lines.clear()
                repeat(screenRows) { lines += blankLine() }
                cursorRow = 0
                cursorColumn = 0
            }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> eraseRange(cursorRow, cursorColumn, screenColumns)
            1 -> eraseRange(cursorRow, 0, cursorColumn + 1)
            2 -> eraseRange(cursorRow, 0, screenColumns)
        }
    }

    private fun eraseCharacters(count: Int) =
        eraseRange(cursorRow, cursorColumn, min(screenColumns, cursorColumn + count))

    private fun eraseRange(row: Int, start: Int, end: Int) {
        if (row !in lines.indices) return
        val line = lines[row]
        for (column in start.coerceIn(0, screenColumns) until end.coerceIn(0, screenColumns)) {
            line[column] = TerminalCell()
        }
    }

    private fun insertCharacters(count: Int) {
        val line = lines[cursorRow]
        val column = cursorColumn.coerceIn(0, screenColumns - 1)
        repeat(count.coerceIn(1, screenColumns)) {
            line.add(column, TerminalCell())
            line.removeAt(line.lastIndex)
        }
    }

    private fun deleteCharacters(count: Int) {
        val line = lines[cursorRow]
        val column = cursorColumn.coerceIn(0, screenColumns - 1)
        repeat(count.coerceIn(1, screenColumns)) {
            if (column < line.size) line.removeAt(column)
            line += TerminalCell()
        }
    }

    private fun insertLines(count: Int) {
        val start = screenStart()
        repeat(count.coerceIn(1, screenRows)) {
            lines.add(cursorRow, blankLine())
            lines.removeAt(start + screenRows)
        }
    }

    private fun deleteLines(count: Int) {
        val start = screenStart()
        repeat(count.coerceIn(1, screenRows)) {
            if (cursorRow < lines.size) lines.removeAt(cursorRow)
            lines.add(start + screenRows - 1, blankLine())
        }
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorColumn = cursorColumn
    }

    private fun restoreCursor() {
        cursorRow = savedCursorRow.coerceIn(screenStart(), lines.lastIndex)
        cursorColumn = savedCursorColumn.coerceIn(0, screenColumns - 1)
    }

    private fun trimScrollback() {
        val maximum = maxScrollbackLines + screenRows
        while (lines.size > maximum) {
            lines.removeAt(0)
            cursorRow = max(0, cursorRow - 1)
            savedCursorRow = max(0, savedCursorRow - 1)
        }
    }

    private fun screenStart(): Int = max(0, lines.size - screenRows)

    private fun blankLine(): MutableList<TerminalCell> =
        MutableList(screenColumns) { TerminalCell() }

    companion object {
        private val ANSI_COLORS = intArrayOf(
            0xff000000.toInt(), 0xffcd0000.toInt(), 0xff00cd00.toInt(), 0xffcdcd00.toInt(),
            0xff0000ee.toInt(), 0xffcd00cd.toInt(), 0xff00cdcd.toInt(), 0xffe5e5e5.toInt(),
            0xff7f7f7f.toInt(), 0xffff0000.toInt(), 0xff00ff00.toInt(), 0xffffff00.toInt(),
            0xff5c5cff.toInt(), 0xffff00ff.toInt(), 0xff00ffff.toInt(), 0xffffffff.toInt()
        )
    }
}
