package com.fighter.studykeyboard.play

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Optional "voice" mode: the note is read aloud by the system text-to-speech
 * engine while the same text is typed into the focused field, word by word,
 * in sync with the voice (Android 8.0+ exposes speech ranges).
 */
class SpeechSpeaker(context: Context) {

    interface Listener {
        fun onSpeechStarted(charCount: Int)
        fun onSpeechRange(start: Int, end: Int)
        fun onSpeechDone()
        fun onSpeechUnavailable(reason: String)
        fun onSpeechStateChanged()
    }

    var listener: Listener? = null

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null

    private var ready = false
    private var text = ""
    private var rate = 1.0f
    private var loop = false
    private var utteranceId = ""
    private var counter = 0

    var isRunning: Boolean = false
        private set

    init {
        engine = TextToSpeech(context.applicationContext) { status -> main.post { onEngineReady(status) } }
    }

    private fun onEngineReady(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ready = false
            listener?.onSpeechUnavailable("Text-to-speech is not available on this device")
            return
        }
        ready = true
        chooseLanguage()
        engine?.setSpeechRate(rate)
        engine?.setOnUtteranceProgressListener(progressListener)
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {
            main.post { listener?.onSpeechStateChanged() }
        }

        override fun onDone(id: String?) {
            main.post {
                if (id != utteranceId) return@post
                listener?.onSpeechDone()
                if (loop && text.isNotEmpty()) {
                    speakNow()
                } else {
                    isRunning = false
                    listener?.onSpeechStateChanged()
                }
            }
        }

        @Deprecated("Required by the abstract Java contract", ReplaceWith("onError(id, -1)"))
        override fun onError(id: String?) {
            onError(id, -1)
        }

        override fun onError(id: String?, errorCode: Int) {
            main.post {
                if (id != utteranceId) return@post
                isRunning = false
                listener?.onSpeechUnavailable("Voice error ($errorCode)")
                listener?.onSpeechStateChanged()
            }
        }

        override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            main.post {
                if (id != utteranceId) return@post
                listener?.onSpeechRange(start, end)
            }
        }
    }

    /** True when the platform can report speech ranges (Android 8.0 and newer). */
    fun supportsRangeSync(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private fun chooseLanguage() {
        val tts = engine ?: return
        val candidates = listOf(Locale("en", "IN"), Locale.US, Locale.UK)
        for (locale in candidates) {
            val result = tts.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) return
        }
        tts.setLanguage(Locale.getDefault())
    }

    fun start(content: String, speechRate: Float, loopPlayback: Boolean) {
        text = content
        rate = speechRate.coerceIn(0.3f, 3.0f)
        loop = loopPlayback
        if (text.isBlank()) return
        if (!ready) {
            listener?.onSpeechUnavailable("Voice engine is still starting, please try again")
            return
        }
        speakNow()
    }

    private fun speakNow() {
        val tts = engine ?: return
        if (text.isBlank()) return
        counter++
        utteranceId = "fk-$counter"
        tts.setSpeechRate(rate)
        isRunning = true
        listener?.onSpeechStarted(text.length)
        listener?.onSpeechStateChanged()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        val wasRunning = isRunning
        isRunning = false
        utteranceId = ""
        engine?.stop()
        if (wasRunning) listener?.onSpeechStateChanged()
    }

    fun shutdown() {
        isRunning = false
        utteranceId = ""
        engine?.stop()
        engine?.shutdown()
        engine = null
        listener = null
    }
}
