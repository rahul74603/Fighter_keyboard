package com.fighter.studykeyboard.play

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.fighter.studykeyboard.BrowserActivity
import com.fighter.studykeyboard.R
import com.fighter.studykeyboard.data.PlayerPrefs
import com.fighter.studykeyboard.util.DebugLog

/**
 * The real background bot.
 *
 * A WebView that lives in the app's *active* process is frozen by Chromium as soon
 * as the activity is not visible ("background tab"), which is why the bot used to
 * pause on minimise / lock and continued only after the screen came back.
 *
 * This service solves that by putting its own WebView into a small **floating
 * overlay window** (TYPE_APPLICATION_OVERLAY). An overlay window stays visible, so
 * its JavaScript keeps running while the app is in the background, the phone is
 * locked or another app is in front. It shares the same cookie jar, so the login
 * done once in the in-app browser works here.
 *
 * Needs the "Display over other apps" permission (one toggle) - the app walks the
 * user through it.
 */
class OverlayBotService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var webView: WebView? = null
    private var statusView: TextView? = null
    private lateinit var prefs: PlayerPrefs
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PlayerPrefs(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        ensureChannel()
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.overlay_bot_running))
            )
        } catch (error: Exception) {
            DebugLog.log("OverlayBot", error)
        }
        val url = intent?.getStringExtra(EXTRA_URL)
            ?: prefs.botUrl
            ?: DEFAULT_URL
        prefs.botUrl = url
        showOverlay(url)
        return START_STICKY
    }

    // ------------------------------------------------------------------ overlay

    private fun showOverlay(url: String) {
        if (!Settings.canDrawOverlays(this)) {
            DebugLog.log("OverlayBot", "no overlay permission")
            shutdown()
            return
        }
        if (rootView == null) createOverlay()

        val view = webView
        if (view == null) {
            shutdown()
            return
        }
        if (view.url.isNullOrBlank() || started) {
            started = true
            view.loadUrl(url)
        }

        // Hand the overlay WebView to the shared bot + start the queue engine.
        BrowserBot.attach(view, this)
        if (!BrowserBot.running) BrowserBotService.start(this)
        updateStatus(getString(R.string.overlay_bot_ready))
    }

    private fun createOverlay() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#F0181B23"))
                setStroke(dp(1), Color.parseColor("#7FB2FF"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(6))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = getString(R.string.overlay_bot_title)
            setTextColor(Color.parseColor("#7FB2FF"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val status = TextView(this).apply {
            text = getString(R.string.overlay_bot_loading)
            setTextColor(Color.parseColor("#98A2B8"))
            textSize = 10f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
        }
        val minimize = TextView(this).apply {
            text = "▁"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                val target = webView ?: return@setOnClickListener
                target.visibility =
                    if (target.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener { shutdown() }
        }
        header.addView(title)
        header.addView(status)
        header.addView(minimize)
        header.addView(close)
        statusView = status

        val overlayWebView = WebView(this)
        val view = overlayWebView.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(260)
            )
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                cacheMode = WebSettings.LOAD_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    CookieManager.getInstance().setAcceptThirdPartyCookies(overlayWebView, true)
                }
            }
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(w: WebView?, u: String?) {
                    super.onPageFinished(w, u)
                    CookieManager.getInstance().flush()
                    updateStatus(getString(R.string.overlay_bot_page_ready))
                }
            }
            webChromeClient = WebChromeClient()
        }
        webView = view

        container.addView(header)
        container.addView(view)
        rootView = container

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(70)
        }
        try {
            manager.addView(container, params)
            DebugLog.log("OverlayBot", "overlay added")
        } catch (error: Exception) {
            DebugLog.log("OverlayBot", error)
            shutdown()
        }
    }

    private fun updateStatus(text: String) {
        statusView?.text = text
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (error: Exception) {
            // ignore
        }
    }

    private fun shutdown() {
        DebugLog.log("OverlayBot", "shutdown")
        BrowserBotService.stop(this)
        BrowserBot.detach(webView ?: return finishShutdown())
        try {
            webView?.stopLoading()
            webView?.destroy()
        } catch (error: Exception) {
            // ignore
        }
        webView = null
        finishShutdown()
    }

    private fun finishShutdown() {
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (error: Exception) {
            // ignore
        }
        rootView = null
        started = false
        try {
            stopForeground(true)
        } catch (error: Exception) {
            // ignore
        }
        stopSelf()
    }

    override fun onDestroy() {
        BrowserBot.detach(webView ?: return super.onDestroy())
        try {
            webView?.destroy()
        } catch (error: Exception) {
            // ignore
        }
        webView = null
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (error: Exception) {
            // ignore
        }
        rootView = null
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
                getString(R.string.channel_overlay),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_overlay_desc)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, BrowserActivity::class.java), flags())
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayBotService::class.java).setAction(ACTION_STOP),
            flags()
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setContentTitle(getString(R.string.overlay_bot_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_keyboard)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        @Suppress("DEPRECATION")
        builder.addAction(Notification.Action.Builder(0, getString(R.string.notif_stop), stopIntent).build())
        return builder.build()
    }

    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    companion object {
        private const val CHANNEL_ID = "fighter_overlay_bot"
        private const val NOTIFICATION_ID = 4740
        private const val DEFAULT_URL = "https://m.facebook.com/"
        const val ACTION_STOP = "com.fighter.studykeyboard.OVERLAY_STOP"
        const val EXTRA_URL = "extra_url"

        fun hasPermission(context: Context): Boolean = try {
            Settings.canDrawOverlays(context)
        } catch (error: Exception) {
            false
        }

        fun start(context: Context, url: String?) {
            val intent = Intent(context, OverlayBotService::class.java)
                .putExtra(EXTRA_URL, url)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayBotService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
