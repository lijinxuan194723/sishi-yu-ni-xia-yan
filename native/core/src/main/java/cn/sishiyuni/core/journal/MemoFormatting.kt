package cn.sishiyuni.core.journal

/** Selection offsets use UTF-16, matching Android TextFieldValue. */
data class MemoSelection(val text: String, val start: Int, val end: Int)
object MemoFormatting {
    fun bold(text: String, start: Int, end: Int): MemoSelection {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(a, text.length)
        if (a >= 2 && b + 2 <= text.length && text.substring(a - 2, a) == "**" && text.substring(b, b + 2) == "**") {
            return MemoSelection(text.removeRange(b, b + 2).removeRange(a - 2, a), a - 2, b - 2)
        }
        return MemoSelection(text.substring(0, a) + "**" + text.substring(a, b) + "**" + text.substring(b), a + 2, b + 2)
    }
    fun lines(text: String, start: Int, end: Int, prefix: String): MemoSelection {
        require(prefix in setOf("## ", "- ", "- [ ] ", "> "))
        val lo = minOf(start, end).coerceIn(0, text.length)
        val hi = maxOf(start, end).coerceIn(lo, text.length)
        val a = if (lo == 0) 0 else text.lastIndexOf('\n', lo - 1) + 1
        // Selecting up to the start of a following line must not format that line.
        val boundary = if (hi > lo && hi > 0 && text[hi - 1] == '\n') hi - 1 else hi
        val b = text.indexOf('\n', boundary).let { if (it < 0) text.length else it }
        val old = text.substring(a, b).split('\n')
        val remove = old.all { it.startsWith(prefix) }
        val changed = old.joinToString("\n") { if (remove) it.removePrefix(prefix) else prefix + it }
        return MemoSelection(text.substring(0, a) + changed + text.substring(b), a, a + changed.length)
    }
}
