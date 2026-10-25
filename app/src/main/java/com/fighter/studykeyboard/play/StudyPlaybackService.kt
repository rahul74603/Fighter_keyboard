package com.fighter.studykeyboard.play

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.fighter.studykeyboard.R
import com.fighter.studykeyboard.StudyActivity
import com.fighter.studykeyboard.data.Note
import com.fighter.studykeyboard.data.NoteStore
import com.fighter.studykeyboard.data.PlayerPrefs

/**
 * Full background study playback.
 *
 * The whole engine (queue, text-to-speech, next/previous, pause) lives here in a
 * foreground service, not in a screen. So the notes keep playing when the app is
 * minimised, the keyboard is closed, the screen is off or the phone is locked.
 *
 * Notification me Prev / Play-Pause / Next / Stop controls hain.
 */
class StudyPlaybackService : Service(), SpeechSpeaker.Listener {

    private lateinit var store: NoteStore
    private lateinit var prefs: PlayerPrefs

    private var speaker: SpeechSpeaker? = null
    private var notes: List<Note> = emptyList()
    private var index = 0
    private var playing = false
    private var paused = false
    private var currentText = ""
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = NoteStore.get(this)
        prefs = PlayerPrefs(this)
        speaker = SpeechSpeaker(this).also { it.listener = this }
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        ensureForeground()
        when (intent?.action) {
            ACTION_PLAY -> startSession(
                playAll = intent.getBooleanExtra(EXTRA_PLAY_ALL, true),
                noteId = intent.getStringExtra(EXTRA_NOTE_ID)
            )
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_NEXT -> step(1)
            ACTION_PREV -> step(-1)
            ACTION_REPEAT -> repeatCurrent()
            ACTION_SPEED -> repeatCurrent()
            ACTION_STOP -> {
                stopSession()
                return START_NOT_STICKY
            }
            else -> Unit
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ session

    private fun startSession(playAll: Boolean, noteId: String?) {
        val all = store.all()
        if (all.isEmpty()) {
            PlaybackBridge.publish(PlaybackBridge.State(status = getString(R.string.no_notes_hint)))
            stopSession()
            return
        }
        val ordered = if (prefs.shuffle) all.shuffled() else all
        notes = if (playAll || noteId != null) ordered else {
            val lastId = prefs.lastNoteId
            val start = ordered.indexOfFirst { it.id == lastId }
            if (start > 0) ordered.drop(start) + ordered.take(start) else ordered
        }
        index = when {
            noteId != null -> notes.indexOfFirst { it.id == noteId }.coerceAtLeast(0)
            else -> 0
        }
        speakCurrent()
    }

    private fun speakCurrent() {
        if (notes.isEmpty()) return
        val note = notes[index.coerceIn(0, notes.size - 1)]
        currentText = if (prefs.capsMode) TextTools.capsText(note.text) else note.text
        prefs.lastNoteId = note.id
        playing = true
        paused = false
        acquireWakeLock()
        publish(getString(R.string.study_listening), 0, 0)
        updateNotification(getString(R.string.study_listening))
        speaker?.start(currentText, prefs.speechRate, false)
    }

    private fun repeatCurrent() = speakCurrent()

    private fun step(direction: Int) {
        if (notes.isEmpty()) {
            startSession(playAll = true, noteId = null)
            return
        }
        if (notes.size == 1) {
            speakCurrent()
            return
        }
        index = (index + direction + notes.size) % notes.size
        if (direction > 0 && index == 0 && prefs.shuffle) {
            notes = notes.shuffled()
            index = 0
        }
        speakCurrent()
    }

    private fun pausePlayback() {
        if (notes.isEmpty()) return
        speaker?.stop()
        playing = false
        paused = true
        releaseWakeLock()
        publish(getString(R.string.study_paused), 0, 0)
        updateNotification(getString(R.string.study_paused))
    }

    private fun resumePlayback() {
        if (notes.isEmpty()) {
            startSession(playAll = true, noteId = null)
            return
        }
        speakCurrent()
    }

    private fun stopSession() {
        handler.removeCallbacksAndMessages(null)
        playing = false
        paused = false
        speaker?.stop()
        releaseWakeLock()
        PlaybackBridge.clear()
        try {
            stopForeground(true)
        } catch (error: Exception) {
            // ignore
        }
        stopSelf()
    }

    // ------------------------------------------------------------------ speech callbacks

    override fun onSpeechStarted(charCount: Int) {
        playing = true
        paused = false
        publish(getString(R.string.study_listening), 0, 0)
        updateNotification(getString(R.string.study_listening))
    }

    override fun onSpeechRange(start: Int, end: Int) {
        publish(getString(R.string.study_listening), start, end)
    }

    override fun onSpeechDone() {
        if (!playing) return
        if (prefs.studyAutoNext && notes.size > 1) {
            publish(getString(R.string.study_check), 0, 0)
            updateNotification(getString(R.string.study_check))
            handler.postDelayed({ if (playing) step(1) }, AUTO_NEXT_DELAY_MS)
        } else {
            playing = false
            paused = true
            releaseWakeLock()
            publish(getString(R.string.study_check), 0, 0)
            updateNotification(getString(R.string.study_check))
        }
    }

    override fun onSpeechUnavailable(reason: String) {
        playing = false
        publish(getString(R.string.voice_unavailable), 0, 0)
        updateNotification(getString(R.string.voice_unavailable))
    }

    override fun onSpeechStateChanged() = Unit

    // ------------------------------------------------------------------ state / notification

    private fun publish(status: String, highlightStart: Int, highlightEnd: Int) {
        PlaybackBridge.publish(
            PlaybackBridge.State(
                noteIndex = index,
                noteCount = notes.size,
                text = currentText,
                highlightStart = highlightStart,
                highlightEnd = highlightEnd,
                playing = playing,
                paused = paused,
                finished = !playing && paused,
                status = status
            )
        )
    }

    private fun ensureForeground() {
        val notification = buildNotification(getString(R.string.notif_study_ready))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(status))
        } catch (error: Exception) {
            // Notification blocked - playback still works.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_study),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_study_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 10, Intent(this, StudyActivity::class.java), pendingFlags()
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val snippet = notes.getOrNull(index)?.let { snippet(it) }.orEmpty()
        builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(if (snippet.isBlank()) status else "$status\n$snippet")
            .setStyle(Notification.BigTextStyle().bigText(
                if (snippet.isBlank()) status else "$status\n$snippet"
            ))
            .setSmallIcon(R.drawable.ic_stat_keyboard)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (notes.isNotEmpty()) {
            builder.setSubText(getString(R.string.notif_queue, index + 1, notes.size))
        }
        builder
            .addAction(action("prev", android.R.drawable.ic_media_previous, getString(R.string.study_prev), ACTION_PREV, 21))
            .addAction(
                if (playing) {
                    action("pause", android.R.drawable.ic_media_pause, getString(R.string.tb_pause), ACTION_PAUSE, 22)
                } else {
                    action("resume", android.R.drawable.ic_media_play, getString(R.string.tb_play), ACTION_RESUME, 23)
                }
            )
            .addAction(action("next", android.R.drawable.ic_media_next, getString(R.string.study_next), ACTION_NEXT, 24))
            .addAction(action("stop", android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_stop), ACTION_STOP, 25))
        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun action(id: String, icon: Int, title: String, action: String, code: Int): Notification.Action {
        val intent = Intent(this, StudyPlaybackService::class.java).setAction(action)
        val pending = PendingIntent.getService(this, code, intent, pendingFlags())
        return Notification.Action.Builder(icon, title, pending).build()
    }

    private fun snippet(note: Note): String {
        val flat = note.text.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= 90) flat else flat.take(90) + "…"
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    // ------------------------------------------------------------------ wake lock

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FighterStudy::Playback"
            ).apply { setReferenceCounted(false) }
        }
        try {
            wakeLock?.acquire(MAX_WAKE_LOCK_MS)
        } catch (error: Exception) {
            // ignore
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (error: Exception) {
            // ignore
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        speaker?.stop()
        speaker?.shutdown()
        speaker = null
        releaseWakeLock()
        PlaybackBridge.clear()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "fighter_study_playback"
        private const val NOTIFICATION_ID = 4720
        private const val AUTO_NEXT_DELAY_MS = 1500L
        private const val MAX_WAKE_LOCK_MS = 2 * 60 * 60 * 1000L

        const val ACTION_PLAY = "com.fighter.studykeyboard.STUDY_PLAY"
        const val ACTION_PAUSE = "com.fighter.studykeyboard.STUDY_PAUSE"
        const val ACTION_RESUME = "com.fighter.studykeyboard.STUDY_RESUME"
        const val ACTION_NEXT = "com.fighter.studykeyboard.STUDY_NEXT"
        const val ACTION_PREV = "com.fighter.studykeyboard.STUDY_PREV"
        const val ACTION_REPEAT = "com.fighter.studykeyboard.STUDY_REPEAT"
        const val ACTION_SPEED = "com.fighter.studykeyboard.STUDY_SPEED"
        const val ACTION_STOP = "com.fighter.studykeyboard.STUDY_STOP"
        private const val EXTRA_PLAY_ALL = "extra_play_all"
        private const val EXTRA_NOTE_ID = "extra_note_id"

        fun play(context: Context, playAll: Boolean = true, noteId: String? = null) {
            val intent = Intent(context, StudyPlaybackService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_PLAY_ALL, playAll)
                .putExtra(EXTRA_NOTE_ID, noteId)
            start(context, intent)
        }

        fun command(context: Context, action: String) {
            start(context, Intent(context, StudyPlaybackService::class.java).setAction(action))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StudyPlaybackService::class.java))
        }

        private fun start(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
