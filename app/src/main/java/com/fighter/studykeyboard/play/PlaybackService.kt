package com.fighter.studykeyboard.play

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.fighter.studykeyboard.MainActivity
import com.fighter.studykeyboard.R

/**
 * Foreground service that keeps a play session alive while the screen is off,
 * the phone is locked or the user minimises the app.
 *
 * Typing into another app's text field needs the keyboard to be visible, so for
 * typing mode the service simply holds the session (status + controls) and the
 * queue resumes as soon as the keyboard is back. Voice mode (text-to-speech)
 * keeps speaking in the background without any interruption.
 */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val status = intent.getStringExtra(EXTRA_STATUS).orEmpty()
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(status, intent.getStringExtra(EXTRA_MODE)))
            }
            else -> {
                val status = intent?.getStringExtra(EXTRA_STATUS).orEmpty()
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(status, intent?.getStringExtra(EXTRA_MODE))
                )
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_playback),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_playback_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String, mode: String?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingFlags()
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            pendingFlags()
        )
        val text = if (status.isNotBlank()) status else getString(R.string.notif_playing)
        val subtitle = when (mode) {
            MODE_VOICE -> getString(R.string.notif_mode_voice)
            else -> getString(R.string.notif_mode_typing)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSubText(subtitle)
            .setSmallIcon(R.drawable.ic_stat_keyboard)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.notif_stop),
                    stopIntent
                ).build()
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setColorized(false)
        }
        return builder.build()
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    override fun onDestroy() {
        super.onDestroy()
        stopRequested = null
    }

    companion object {
        private const val CHANNEL_ID = "fighter_playback"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_START = "com.fighter.studykeyboard.START_PLAYBACK"
        private const val ACTION_STOP = "com.fighter.studykeyboard.STOP_PLAYBACK"
        private const val ACTION_UPDATE = "com.fighter.studykeyboard.UPDATE_PLAYBACK"
        private const val EXTRA_STATUS = "extra_status"
        private const val EXTRA_MODE = "extra_mode"
        const val MODE_TYPING = "typing"
        const val MODE_VOICE = "voice"

        /** Set by the keyboard while it is playing; used by the Stop button. */
        @Volatile
        var stopRequested: (() -> Unit)? = null

        fun start(context: Context, status: String, mode: String) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_MODE, mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, status: String, mode: String) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_MODE, mode)
            try {
                context.startService(intent)
            } catch (error: Exception) {
                // Service not running (e.g. notifications blocked) - nothing to update.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
