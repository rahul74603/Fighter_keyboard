package com.fighter.studykeyboard.play

import com.fighter.studykeyboard.data.Note

/**
 * Word helpers: splitting notes into playable words and building the
 * suggestion strip straight out of the user's own notes.
 */
object TextTools {

    private val WHITESPACE = Regex("\\s+")

    private val BASE_WORDS = listOf(
        "the", "this", "that", "these", "those", "and", "because", "therefore", "important",
        "definition", "example", "chapter", "question", "answer", "revision", "concept",
        "formula", "theory", "explain", "diagram", "notes", "study", "meaning", "difference",
        "between", "process", "result", "reason", "effect", "function", "structure", "types",
        "method", "points", "topic", "summary", "conclusion", "introduction", "advantages",
        "disadvantages", "application", "properties", "derive", "prove", "state", "define",
        "describe", "write", "short", "long", "also", "with", "from", "about", "which",
        "what", "when", "where", "why", "how", "there", "their", "have", "will", "would"
    )

    /**
     * T-mode output: everything CAPITAL + leet digits, exactly as the user asked:
     * A=4, B=8, T=7, O=0, E=3, I=1, S=5, Z=2.
     */
    private val T_MODE_DIGITS = mapOf(
        'A' to '4', 'B' to '8', 'T' to '7', 'O' to '0',
        'E' to '3', 'I' to '1', 'S' to '5', 'Z' to '2'
    )

    fun capsText(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text) {
            val upper = ch.uppercaseChar()
            builder.append(T_MODE_DIGITS[upper] ?: upper)
        }
        return builder.toString()
    }

    fun words(text: String): List<String> =
        text.split(WHITESPACE).filter { it.isNotBlank() }

    /** Word currently being typed, used for suggestions. */
    fun currentWordPrefix(before: String): String {
        val builder = StringBuilder()
        for (index in before.length - 1 downTo 0) {
            val ch = before[index]
            if (ch.isLetter() || ch == '\'' || ch == '-') builder.append(ch) else break
        }
        return builder.reverse().toString()
    }

    /** Dictionary = words from the user's notes first, then a small base list. */
    fun dictionary(notes: List<Note>): List<String> {
        val result = LinkedHashSet<String>()
        for (note in notes) {
            for (word in words(note.text)) {
                val clean = cleanWord(word)
                if (clean.length >= 3) result.add(clean)
            }
        }
        BASE_WORDS.forEach { result.add(it) }
        return result.toList()
    }

    fun suggestionsFor(prefix: String, dictionary: List<String>, limit: Int = 20): List<String> {
        val cleanPrefix = prefix.lowercase()
        if (cleanPrefix.isEmpty()) return emptyList()
        val result = ArrayList<String>(limit)
        for (word in dictionary) {
            if (result.size >= limit) break
            if (word.length > cleanPrefix.length &&
                word.startsWith(cleanPrefix) &&
                !word.equals(cleanPrefix, ignoreCase = true)
            ) {
                result.add(word)
            }
        }
        return result
    }

    private fun cleanWord(raw: String): String {
        var start = 0
        var end = raw.length
        while (start < end && !raw[start].isLetter()) start++
        while (end > start && !raw[end - 1].isLetter()) end--
        return raw.substring(start, end).lowercase()
    }
}
