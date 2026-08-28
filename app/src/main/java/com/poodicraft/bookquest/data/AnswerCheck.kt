package com.poodicraft.bookquest.data

/**
 * Marks a typed answer. Students type on a phone keyboard, often in a language
 * that carries vowel marks, so an exact string match would fail people who
 * actually knew the answer. Comparison drops everything that is not a letter or
 * a digit, folds the marks Hebrew and Arabic add, and then allows a small number
 * of typos scaled to how long the answer is.
 */
object AnswerCheck {

    fun isCorrect(given: String, expected: String, alternatives: List<String>): Boolean {
        val typed = normalise(given)
        if (typed.isEmpty()) return false
        val accepted = (listOf(expected) + alternatives)
            .map { normalise(it) }
            .filter { it.isNotEmpty() }
        if (accepted.isEmpty()) return false
        return accepted.any { candidate ->
            typed == candidate || distance(typed, candidate) <= tolerance(candidate)
        }
    }

    private fun tolerance(answer: String): Int = when {
        answer.length <= 4 -> 0
        answer.length <= 9 -> 1
        else -> 2
    }

    fun normalise(value: String): String {
        val builder = StringBuilder(value.length)
        for (raw in value.lowercase().trim()) {
            val ch = fold(raw)
            when {
                ch.isLetterOrDigit() -> builder.append(ch)
                ch.isWhitespace() -> if (builder.isNotEmpty() && builder.last() != ' ') {
                    builder.append(' ')
                }
            }
        }
        return builder.toString().trim()
    }

    /** Folds away marks and letter variants that carry no meaning for an answer. */
    private fun fold(ch: Char): Char = when (ch) {
        // Hebrew niqqud and cantillation marks.
        in '֑'..'ׇ' -> ' '
        // Arabic harakat, plus the superscript alef and the tatweel stretcher.
        in 'ً'..'ٟ' -> ' '
        'ٰ', 'ـ' -> ' '
        // Arabic letter variants students type interchangeably.
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ى' -> 'ي'
        'ة' -> 'ه'
        else -> ch
    }

    /** Plain Levenshtein distance, kept to two rows. */
    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                val insertion = current[j - 1] + 1
                val deletion = previous[j] + 1
                current[j] = minOf(substitution, insertion, deletion)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
