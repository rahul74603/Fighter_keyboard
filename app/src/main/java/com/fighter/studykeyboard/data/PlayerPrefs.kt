package com.fighter.studykeyboard.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings for the auto-play engine. One single "speed" slider drives both modes:
 *  - typing mode -> milliseconds between words
 *  - voice mode  -> text-to-speech rate
 */
class PlayerPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 0 = slowest, 100 = fastest. */
    var speedProgress: Int
        get() = prefs.getInt(KEY_SPEED, 60).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_SPEED, value.coerceIn(0, 100)).apply()

    var loop: Boolean
        get() = prefs.getBoolean(KEY_LOOP, false)
        set(value) = prefs.edit().putBoolean(KEY_LOOP, value).apply()

    var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE, value).apply()

    /** true = type letter by letter, false = one clean commit per word. */
    var letterByLetter: Boolean
        get() = prefs.getBoolean(KEY_LETTERS, false)
        set(value) = prefs.edit().putBoolean(KEY_LETTERS, value).apply()

    /** T-mode: sab kuch CAPITAL me jata hai jab tak switch band na ho. */
    var capsMode: Boolean
        get() = prefs.getBoolean(KEY_CAPS, false)
        set(value) = prefs.edit().putBoolean(KEY_CAPS, value).apply()

    /** Har note ke baad Enter (send) khud dab jaye. */
    var autoSend: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEND, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SEND, value).apply()

    /** Random note / random order, taaki har baar same message na jaye. */
    var shuffle: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    var lastNoteId: String?
        get() = prefs.getString(KEY_LAST_NOTE, null)
        set(value) = prefs.edit().putString(KEY_LAST_NOTE, value).apply()

    /** Milliseconds between two words: slow end 1500 ms, fast end 200 ms. */
    val wordDelayMs: Long
        get() = (1500L - speedProgress * 13L).coerceAtLeast(200L)

    /** Text-to-speech rate: 0.5x (slow) .. 1.6x (fast). */
    val speechRate: Float
        get() = 0.5f + speedProgress * 0.011f

    companion object {
        private const val PREFS_NAME = "fighter_study_keyboard_settings"
        private const val KEY_SPEED = "speed_progress"
        private const val KEY_LOOP = "loop_playback"
        private const val KEY_VOICE = "voice_enabled"
        private const val KEY_LETTERS = "letter_by_letter"
        private const val KEY_LAST_NOTE = "last_note_id"
        private const val KEY_CAPS = "caps_mode"
        private const val KEY_AUTO_SEND = "auto_send"
        private const val KEY_SHUFFLE = "shuffle"
    }
}
