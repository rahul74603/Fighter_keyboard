package com.fighter.studykeyboard.play

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Single source of truth for the background study playback.
 * The service writes into it, screens (Study mode / app) only read from it,
 * so the playback keeps running perfectly even when no screen is open.
 */
object PlaybackBridge {

    data class State(
        val noteIndex: Int = 0,
        val noteCount: Int = 0,
        val text: String = "",
        val highlightStart: Int = 0,
        val highlightEnd: Int = 0,
        val playing: Boolean = false,
        val paused: Boolean = false,
        val finished: Boolean = false,
        val status: String = ""
    ) {
        val hasSession: Boolean get() = noteCount > 0
    }

    interface Listener {
        fun onPlaybackState(state: State)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    var state: State = State()
        private set

    fun register(listener: Listener) {
        listeners.addIfAbsent(listener)
        listener.onPlaybackState(state)
    }

    fun unregister(listener: Listener) {
        listeners.remove(listener)
    }

    fun publish(newState: State) {
        state = newState
        for (listener in listeners) {
            listener.onPlaybackState(newState)
        }
    }

    fun clear() = publish(State())
}
