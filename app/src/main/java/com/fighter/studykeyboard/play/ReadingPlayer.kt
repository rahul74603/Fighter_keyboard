package com.fighter.studykeyboard.play

import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputConnection
import com.fighter.studykeyboard.data.PlayerPrefs

/**
 * Types a note into the focused text field, one word at a time, with a
 * user controlled delay. This is the "auto play" heart of the keyboard:
 * whatever app the user is in (message, comment, notes) the words keep
 * landing there until the note finishes or the user stops it.
 */
class ReadingPlayer(
    private val prefs: PlayerPrefs,
    private val connection: () -> InputConnection?
) {

    interface Listener {
        fun onPlayerStarted(totalWords: Int)
        fun onPlayerWord(index: Int, total: Int, word: String)
        fun onPlayerFinished()
        fun onPlayerStateChanged()
    }

    var listener: Listener? = null

    private val main = Handler(Looper.getMainLooper())
    private val stepRunnable = Runnable { step() }
    private val letterRunnable = Runnable { stepLetter() }

    private var words: List<String> = emptyList()
    private var index = 0
    private var passes = 0

    private var allowLoop: Boolean = true
    private var letters: String = ""
    private var letterPos = 0

    var isRunning: Boolean = false
        private set

    var isPaused: Boolean = false
        private set

    fun start(text: String, loopAllowed: Boolean = true) {
        stopInternal()
        allowLoop = loopAllowed
        words = TextTools.words(text)
        passes = 0
        index = 0
        if (words.isEmpty()) {
            listener?.onPlayerFinished()
            return
        }
        isRunning = true
        isPaused = false
        listener?.onPlayerStarted(words.size)
        listener?.onPlayerStateChanged()
        main.postDelayed(stepRunnable, 300L)
    }

    fun togglePause() {
        if (!isRunning) return
        isPaused = !isPaused
        listener?.onPlayerStateChanged()
    }

    fun stop() {
        val wasRunning = isRunning
        stopInternal()
        if (wasRunning) listener?.onPlayerStateChanged()
    }

    private fun stopInternal() {
        isRunning = false
        isPaused = false
        main.removeCallbacks(stepRunnable)
        main.removeCallbacks(letterRunnable)
        letters = ""
        letterPos = 0
    }

    private fun step() {
        if (!isRunning) return
        if (isPaused) {
            main.postDelayed(stepRunnable, 120L)
            return
        }
        val ic = connection()
        if (ic == null) {
            finish()
            return
        }
        if (index >= words.size) {
            if (!allowLoop || !prefs.loop) {
                finish()
                return
            }
            index = 0
            if (passes > 0) ic.commitText("\n", 1)
            passes++
            main.postDelayed(stepRunnable, 200L)
            return
        }
        val word = words[index]
        listener?.onPlayerWord(index, words.size, word)
        index++
        if (prefs.letterByLetter) {
            letters = word
            letterPos = 0
            stepLetter()
        } else {
            ic.commitText("$word ", 1)
            scheduleNextWord()
        }
    }

    private fun stepLetter() {
        if (!isRunning) return
        if (isPaused) {
            main.postDelayed(letterRunnable, 120L)
            return
        }
        val ic = connection() ?: run {
            finish()
            return
        }
        if (letterPos < letters.length) {
            ic.commitText(letters[letterPos].toString(), 1)
            letterPos++
            val perChar = (prefs.wordDelayMs / (letters.length + 1)).coerceAtLeast(25L)
            main.postDelayed(letterRunnable, perChar)
        } else {
            ic.commitText(" ", 1)
            scheduleNextWord()
        }
    }

    private fun scheduleNextWord() {
        if (!isRunning) return
        main.postDelayed(stepRunnable, prefs.wordDelayMs)
    }

    private fun finish() {
        stopInternal()
        listener?.onPlayerFinished()
        listener?.onPlayerStateChanged()
    }
}
