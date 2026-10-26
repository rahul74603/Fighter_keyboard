package com.fighter.studykeyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
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

        configureWebView()

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

        updateDesktopChip()
        loadUrl(intent.getStringExtra(EXTRA_URL) ?: HOME_URL)
    }

    private fun configureWebView() {
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        val settings = webView.settings
        settings.javaScriptEnabled = true
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
                    showHint(getString(R.string.br_load_error))
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
        try {
            webView.loadUrl(url)
        } catch (error: Exception) {
            showHint(getString(R.string.br_load_error))
            openExternally(url)
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
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
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
