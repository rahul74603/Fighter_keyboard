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
import com.fighter.studykeyboard.BrowserActivity
import com.fighter.studykeyboard.R
import com.fighter.studykeyboard.data.Note
import com.fighter.studykeyboard.data.NoteStore
import com.fighter.studykeyboard.data.PlayerPrefs
import com.fighter.studykeyboard.util.DebugLog

/**
 * Keeps the in-app browser bot alive while the app is in the background or the
 * screen is locked: foreground notification + wake lock + the queue of notes.
 *
 * Everything happens inside our own WebView (see [BrowserBot]); no generated
 * server / account data leaves the phone.
 */
class BrowserBotService : Service() {

    private lateinit var store: NoteStore
    private lateinit var prefs: PlayerPrefs

    private val handler = Handler(Looper.getMainLooper())

    private var queue: MutableList<Note> = mutableListOf()
    private var index = 0
    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = NoteStore.get(this)
        prefs = PlayerPrefs(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.bot_starting), 0, 0))
        } catch (error: Exception) {
            DebugLog.log("BotService", error)
        }
        when (intent?.action) {
            ACTION_STOP -> {
                stopBot()
                return START_NOT_STICKY
            }
            ACTION_START -> startBot()
            else -> if (running) schedule(1000L) else stopBot()
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ bot

    private fun startBot() {
        val notes = store.all()
        if (notes.isEmpty()) {
            notify(getString(R.string.no_notes_hint), 0, 0)
            stopBot()
            return
        }
        queue = (if (prefs.shuffle) notes.shuffled() else notes).toMutableList()
        index = 0
        running = true
        BrowserBot.running = true
        acquireWakeLock()
        DebugLog.log("BotService", "start notes=${queue.size} shuffle=${prefs.shuffle} loop=${prefs.loop}")
        notify(getString(R.string.bot_started, queue.size), 0, queue.size)
        schedule(1500L)
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ tick() }, delay)
    }

    private fun tick() {
        if (!running) return
        if (index >= queue.size) {
            if (prefs.loop) {
                val all = store.all()
                if (all.isEmpty()) {
                    stopBot()
                    return
                }
                queue = (if (prefs.shuffle) all.shuffled() else all).toMutableList()
                index = 0
                DebugLog.log("BotService", "loop restart (${queue.size})")
            } else {
                notify(getString(R.string.all_notes_sent, queue.size), queue.size, queue.size)
                stopBot()
                return
            }
        }
        if (!BrowserBot.hasPage()) {
            notify(getString(R.string.bot_waiting_page), index, queue.size)
            schedule(RETRY_MS)
            return
        }
        val note = queue[index]
        val text = if (prefs.capsMode) TextTools.capsText(note.text) else note.text
        notify(getString(R.string.bot_sending, index + 1, queue.size), index, queue.size)
        BrowserBot.runOnce(text) { ok, message ->
            if (!running) return@runOnce
            if (ok) {
                DebugLog.log("BotService", "sent ${index + 1}/${queue.size}")
                index++
                notify(getString(R.string.bot_sent, index, queue.size), index, queue.size)
                if (index >= queue.size && !prefs.loop) {
                    notify(getString(R.string.all_notes_sent, queue.size), queue.size, queue.size)
                    stopBot()
                    return@runOnce
                }
                schedule(intervalMs())
            } else {
                DebugLog.log("BotService", "send failed: $message")
                notify(getString(R.string.bot_failed), index, queue.size)
                schedule(FAIL_RETRY_MS)
            }
        }
    }

    /** 4s (fast) .. 10s (slow) between two messages - keeps accounts safer. */
    private fun intervalMs(): Long = 4000L + (100 - prefs.speedProgress) * 60L

    private fun stopBot() {
        running = false
        BrowserBot.running = false
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        DebugLog.log("BotService", "stopped")
        try {
            stopForeground(true)
        } catch (error: Exception) {
            // ignore
        }
        stopSelf()
    }

    /** Swiping the app away must not stop the bot. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (running) {
            DebugLog.log("BotService", "task removed, keeping bot alive")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        BrowserBot.running = false
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ notification

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_bot),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_bot_desc)
                setShowBadge(false)
            }
        )
    }

    private fun notify(text: String, sent: Int, total: Int) {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(text, sent, total))
        } catch (error: Exception) {
            DebugLog.log("BotService", error)
        }
    }

    private fun buildNotification(text: String, sent: Int, total: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BrowserActivity::class.java),
            pendingFlags()
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BrowserBotService::class.java).setAction(ACTION_STOP),
            pendingFlags()
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setContentTitle(getString(R.string.bot_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_keyboard)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (total > 0) builder.setSubText("$sent/$total")
        @Suppress("DEPRECATION")
        builder.addAction(
            Notification.Action.Builder(
                0,
                getString(R.string.notif_stop),
                stopIntent
            ).build()
        )
        return builder.build()
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    // ------------------------------------------------------------------ wake lock

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val power = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = power.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "FighterStudy::Bot"
                ).apply { setReferenceCounted(false) }
            }
            wakeLock?.takeIf { !it.isHeld }?.acquire(MAX_WAKE_LOCK_MS)
        } catch (error: Exception) {
            DebugLog.log("BotService", error)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (error: Exception) {
            // ignore
        }
    }

    companion object {
        private const val CHANNEL_ID = "fighter_browser_bot"
        private const val NOTIFICATION_ID = 4730
        private const val RETRY_MS = 5000L
        private const val FAIL_RETRY_MS = 4000L
        private const val MAX_WAKE_LOCK_MS = 2 * 60 * 60 * 1000L

        const val ACTION_START = "com.fighter.studykeyboard.BOT_START"
        const val ACTION_STOP = "com.fighter.studykeyboard.BOT_STOP"

        fun start(context: Context) {
            val intent = Intent(context, BrowserBotService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BrowserBotService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
