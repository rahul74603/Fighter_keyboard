package com.fighter.studykeyboard.data

import org.json.JSONObject
import java.util.UUID

/**
 * One study note. Notes are unlimited, each one can be as long as the user wants.
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {

    val wordCount: Int
        get() = text.trim().split(WHITESPACE).count { it.isNotBlank() }

    fun preview(maxChars: Int = 220): String {
        val flat = text.replace('\n', ' ').replace(WHITESPACE, " ").trim()
        return if (flat.length <= maxChars) flat else flat.substring(0, maxChars) + "…"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun fromJson(json: JSONObject): Note {
            val created = json.optLong("createdAt", System.currentTimeMillis())
            return Note(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                text = json.optString("text"),
                createdAt = created,
                updatedAt = json.optLong("updatedAt", created)
            )
        }
    }
}
