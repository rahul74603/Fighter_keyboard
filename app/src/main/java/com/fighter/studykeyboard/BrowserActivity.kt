package com.fighter.studykeyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.fighter.studykeyboard.data.NoteStore
import com.fighter.studykeyboard.data.PlayerPrefs
import com.fighter.studykeyboard.play.BrowserBot
import com.fighter.studykeyboard.play.BrowserBotService
import com.fighter.studykeyboard.util.DebugLog

/**
 * In-app browser: log in once (Facebook / YouTube / any site) and use the
 * Fighter keyboard's Play right here.
 *
 * Notes for reliability:
 *  - target="_blank" / window.open links load in the same WebView (multi-window is off)
 *  - WebView is explicitly focusable, otherwise the keyboard never opens inside it
 *  - load errors are shown instead of a blank page
 *  - if the system WebView is missing/broken, the page is opened in the normal browser
 */
class BrowserActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tvHint: TextView
    private lateinit var btnDesktop: TextView

    private var desktopMode = false
    private var prefs: PlayerPrefs? = null
    private var botPanel: View? = null
    private var tvBotStatus: TextView? = null
    private var learnKind: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        addressBar = findViewById(R.id.etAddress)
        progress = findViewById(R.id.progressBar)
        tvHint = findViewById(R.id.tvBrowserHint)
        btnDesktop = findViewById(R.id.btnDesktop)

        if (!isWebViewAvailable()) {
            Toast.makeText(this, getString(R.string.br_webview_missing), Toast.LENGTH_LONG).show()
            openExternally(intent.getStringExtra(EXTRA_URL) ?: HOME_URL)
            finish()
            return
        }

        prefs = PlayerPrefs(this)
        configureWebView()
        setupBot()

        findViewById<View>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<View>(R.id.btnForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<View>(R.id.btnReload).setOnClickListener { webView.reload() }
        findViewById<View>(R.id.btnHome).setOnClickListener { loadUrl(HOME_URL) }
        findViewById<View>(R.id.btnQuickFb).setOnClickListener { loadUrl("https://m.facebook.com/") }
        findViewById<View>(R.id.btnQuickYt).setOnClickListener { loadUrl("https://m.youtube.com/") }
        findViewById<View>(R.id.btnQuickGoogle).setOnClickListener { loadUrl("https://www.google.com/") }
        btnDesktop.setOnClickListener {
            desktopMode = !desktopMode
            applyUserAgent()
            updateDesktopChip()
            webView.reload()
        }
        findViewById<View>(R.id.btnOpenExternal).setOnClickListener {
            openExternally(webView.url ?: HOME_URL)
        }

        addressBar.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (isGo) {
                loadUrl(addressBar.text.toString())
                hideKeyboard()
                true
            } else {
                false
            }
        }

        BrowserBot.attach(webView, this)
        updateDesktopChip()
        DebugLog.log("Browser", "opened")
        val target = intent.getStringExtra(EXTRA_URL) ?: HOME_URL
        if (!hasNetwork()) showHint(getString(R.string.br_no_network))
        loadUrl(target)
    }

    // ------------------------------------------------------------------ bot

    private fun setupBot() {
        botPanel = findViewById(R.id.botPanel)
        tvBotStatus = findViewById(R.id.tvBotStatus)

        findViewById<View>(R.id.btnBotPanel).setOnClickListener {
            val panel = botPanel ?: return@setOnClickListener
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.btnBotPickInput).setOnClickListener {
            learnKind = "input"
            BrowserBot.learn("input")
            toast(getString(R.string.bot_learn_input))
        }
        findViewById<View>(R.id.btnBotPickSend).setOnClickListener {
            learnKind = "send"
            BrowserBot.learn("send")
            toast(getString(R.string.bot_learn_send))
        }
        findViewById<View>(R.id.btnBotTest).setOnClickListener { runBotTest() }
        findViewById<View>(R.id.btnBotStart).setOnClickListener {
            if (NoteStore.get(this).all().isEmpty()) {
                toast(getString(R.string.no_notes_hint))
                return@setOnClickListener
            }
            BrowserBotService.start(this)
            toast(getString(R.string.bot_started, NoteStore.get(this).count()))
            updateBotStatus()
        }
        findViewById<View>(R.id.btnBotStop).setOnClickListener {
            BrowserBotService.stop(this)
            updateBotStatus()
        }
        updateBotStatus()
    }

    /** Sends exactly one note so the user can check the selectors. */
    private fun runBotTest() {
        val notes = NoteStore.get(this).all()
        if (notes.isEmpty()) {
            toast(getString(R.string.no_notes_hint))
            return
        }
        val text = notes.first().text
        BrowserBot.runOnce(text) { ok, message ->
            DebugLog.log("Browser", "bot test ok=$ok $message")
            toast(getString(if (ok) R.string.bot_test_ok else R.string.bot_test_fail))
            tvBotStatus?.text = getString(R.string.bot_test_result, message.take(80))
        }
    }

    private fun updateBotStatus() {
        val notes = NoteStore.get(this).count()
        val state = if (BrowserBot.running) getString(R.string.bot_stop) else getString(R.string.bot_start)
        tvBotStatus?.text = "$notes notes • $state"
    }

    /** JS -> Kotlin bridge: learn mode + logs. */
    inner class BotBridge {
        @JavascriptInterface
        fun pick(kind: String, selector: String) {
            runOnUiThread {
                val p = prefs ?: return@runOnUiThread
                if (kind == "input") {
                    BrowserBot.saveSelectors(this@BrowserActivity, selector, null)
                    p.botInputSelector = selector
                    toast(getString(R.string.bot_saved_input))
                } else {
                    BrowserBot.saveSelectors(this@BrowserActivity, null, selector)
                    p.botSendSelector = selector
                    toast(getString(R.string.bot_saved_send))
                }
                DebugLog.log("Browser", "picked $kind -> $selector")
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            DebugLog.log("BrowserJS", message)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun configureWebView() {
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        try {
            webView.addJavascriptInterface(BotBridge(), "FighterBot")
        } catch (error: Exception) {
            DebugLog.log("Browser", error)
        }
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        applyUserAgent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank() && !url.startsWith("about:")) addressBar.setText(url)
                progress.visibility = View.VISIBLE
                tvHint.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                DebugLog.log("Browser", "finished ${url?.take(60)}")
                progress.visibility = View.GONE
                CookieManager.getInstance().flush()
                if (!url.isNullOrBlank() && !url.startsWith("about:")) addressBar.setText(url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = handleUrl(request?.url?.toString())

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handleUrl(url)

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                progress.visibility = View.GONE
                if (request?.isForMainFrame == true) {
                    DebugLog.log("Browser", "error ${error?.errorCode} ${error?.description}")
                    showHint(getString(R.string.br_load_error) + " (${error?.errorCode})")
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                progress.visibility = View.GONE
                showHint(getString(R.string.br_load_error))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false
        }
    }

    /** intents://, whatsapp://, upi:// etc. go to the real app; http(s) stays here. */
    private fun handleUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")) {
            return false
        }
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (error: ActivityNotFoundException) {
            false
        }
    }

    private fun applyUserAgent() {
        val settings = webView.settings
        if (desktopMode) {
            settings.userAgentString =
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36"
        } else {
            settings.userAgentString = null
        }
    }

    private fun updateDesktopChip() {
        btnDesktop.isSelected = desktopMode
    }

    private fun loadUrl(raw: String) {
        var url = raw.trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = if (url.contains('.') && !url.contains(' ')) "https://$url" else searchUrl(url)
        }
        addressBar.setText(url)
        tvHint.visibility = View.GONE
        DebugLog.log("Browser", "load $url (net=${hasNetwork()})")
        try {
            webView.loadUrl(url)
        } catch (error: Exception) {
            showHint(getString(R.string.br_load_error))
            openExternally(url)
        }
    }

    private fun hasNetwork(): Boolean {
        return try {
            val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (error: Exception) {
            true
        }
    }

    private fun showHint(message: String) {
        tvHint.text = message
        tvHint.visibility = View.VISIBLE
    }

    private fun searchUrl(query: String): String =
        "https://www.google.com/search?q=" + Uri.encode(query)

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(addressBar.windowToken, 0)
        webView.requestFocus()
    }

    private fun openExternally(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: Exception) {
            Toast.makeText(this, getString(R.string.br_load_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isWebViewAvailable(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WebView.getCurrentWebViewPackage() != null
        } else {
            true
        }
    } catch (error: Exception) {
        false
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return
        }
        if (BrowserBot.running) {
            moveTaskToBack(true)
            toast(getString(R.string.bot_background))
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        // While the bot runs the WebView must keep executing JavaScript in the
        // background, so it is not paused (foreground service keeps us alive).
        if (this::webView.isInitialized && !BrowserBot.running) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            BrowserBot.detach(webView)
            if (!BrowserBot.running) BrowserBotService.stop(this)
        }
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (error: Exception) {
            // ignore
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val HOME_URL = "https://m.facebook.com/"
    }
}
