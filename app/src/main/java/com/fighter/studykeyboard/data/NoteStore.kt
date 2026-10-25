package com.fighter.studykeyboard.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny local store for an unlimited number of notes.
 * Everything is kept in SharedPreferences as JSON (no server, no login, works offline).
 */
class NoteStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<Note> = synchronized(LOCK) { read() }

    fun count(): Int = all().size

    fun get(id: String): Note? = all().firstOrNull { it.id == id }

    fun add(text: String): Note {
        val note = Note(text = text.trim())
        synchronized(LOCK) {
            val list = read().toMutableList()
            list.add(0, note)
            write(list)
        }
        return note
    }

    fun update(id: String, text: String) {
        synchronized(LOCK) {
            val list = read().toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(text = text.trim(), updatedAt = System.currentTimeMillis())
                write(list)
            }
        }
    }

    fun delete(id: String) {
        synchronized(LOCK) {
            val list = read().toMutableList()
            list.removeAll { it.id == id }
            write(list)
        }
    }

    fun deleteAll() {
        synchronized(LOCK) { write(emptyList()) }
    }

    /** All notes joined together, used by the "Play all" button. */
    fun combinedText(): String = all().joinToString("\n\n") { it.text }

    fun exportJson(): String {
        val array = JSONArray()
        all().forEach { array.put(it.toJson()) }
        return JSONObject()
            .put("app", "FighterStudyKeyboard")
            .put("version", 1)
            .put("notes", array)
            .toString(2)
    }

    /**
     * Accepts either {"notes":[...]}, a plain array of notes, or a single note object.
     * Returns how many new notes were added (duplicates are skipped).
     */
    fun importJson(raw: String): Int {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0
        val incoming = ArrayList<Note>()
        try {
            when (val parsed = org.json.JSONTokener(trimmed).nextValue()) {
                is JSONArray -> {
                    for (i in 0 until parsed.length()) {
                        parsed.optJSONObject(i)?.let { incoming.add(Note.fromJson(it)) }
                    }
                }
                is JSONObject -> {
                    val notesArray = parsed.optJSONArray("notes")
                    if (notesArray != null) {
                        for (i in 0 until notesArray.length()) {
                            notesArray.optJSONObject(i)?.let { incoming.add(Note.fromJson(it)) }
                        }
                    } else if (parsed.has("text")) {
                        incoming.add(Note.fromJson(parsed))
                    }
                }
                else -> Unit
            }
        } catch (error: Exception) {
            return 0
        }

        val fresh = incoming.filter { it.text.isNotBlank() }
        if (fresh.isEmpty()) return 0

        var added = 0
        synchronized(LOCK) {
            val list = read().toMutableList()
            val existing = list.map { it.text.trim() }.toHashSet()
            for (note in fresh) {
                val key = note.text.trim()
                if (key.isNotEmpty() && key !in existing) {
                    list.add(0, note.copy(id = note.id, createdAt = note.createdAt))
                    existing.add(key)
                    added++
                }
            }
            if (added > 0) write(list)
        }
        return added
    }

    private fun read(): List<Note> {
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<Note>(array.length())
            for (i in 0 until array.length()) {
                val object0 = array.optJSONObject(i) ?: continue
                val note = Note.fromJson(object0)
                if (note.text.isNotBlank()) result.add(note)
            }
            result
        } catch (error: Exception) {
            emptyList()
        }
    }

    private fun write(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_NOTES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "fighter_study_keyboard_notes"
        private const val KEY_NOTES = "notes_v1"
        private val LOCK = Any()

        @Volatile
        private var instance: NoteStore? = null

        fun get(context: Context): NoteStore =
            instance ?: synchronized(this) {
                instance ?: NoteStore(context).also { instance = it }
            }
    }
}
