package com.fighter.studykeyboard.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny in-app debug log. Everything important (service start, TTS state, send
 * attempts, browser loads) is written here so the user can open "Log" in the app
 * and see exactly where something failed - no PC/ADB needed.
 */
object DebugLog {

    private const val MAX_LINES = 300
    private const val FILE_NAME = "fighter_log.txt"

    private val lines = ArrayDeque<String>()
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val lock = Any()

    fun log(tag: String, message: String) {
        val line = "${formatter.format(Date())} [$tag] $message"
        Log.i("FighterKB", line)
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    fun log(tag: String, throwable: Throwable) {
        log(tag, "${throwable.javaClass.simpleName}: ${throwable.message}")
    }

    fun entries(): List<String> = synchronized(lock) { lines.toList() }

    fun dump(): String = entries().joinToString("\n")

    fun clear() {
        synchronized(lock) { lines.clear() }
    }

    /** Mirror to a file so the log survives an app restart (useful after crashes). */
    fun persist(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).writeText(dump())
        } catch (error: Exception) {
            Log.w("FighterKB", "persist failed", error)
        }
    }
}
