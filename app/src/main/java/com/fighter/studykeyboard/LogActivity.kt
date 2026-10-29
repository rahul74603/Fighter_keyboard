package com.fighter.studykeyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.fighter.studykeyboard.play.AutoSendService
import com.fighter.studykeyboard.util.DebugLog

/**
 * Plain text log of everything the app did, so a problem can be reported
 * (screenshot / copy) without a computer.
 */
class LogActivity : Activity() {

    private lateinit var tvLog: TextView
    private lateinit var scroll: ScrollView
    private lateinit var tvFacts: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tvLog = findViewById(R.id.tvLog)
        scroll = findViewById(R.id.scrollLog)
        tvFacts = findViewById(R.id.tvFacts)

        findViewById<View>(R.id.btnRefresh).setOnClickListener { render() }
        findViewById<View>(R.id.btnCopy).setOnClickListener { copyLog() }
        findViewById<View>(R.id.btnClear).setOnClickListener {
            DebugLog.clear()
            render()
            Toast.makeText(this, getString(R.string.log_cleared), Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnBackLog).setOnClickListener { finish() }

        render()
    }

    private fun render() {
        tvFacts.text = getString(
            R.string.log_facts,
            BuildFacts.version(this),
            getString(if (AutoSendService.instance != null) R.string.diag_yes else R.string.diag_no),
            getString(if (AutoSendService.isEnabled(this)) R.string.diag_yes else R.string.diag_no)
        )
        val body = DebugLog.entries().joinToString("\n")
        tvLog.text = if (body.isBlank()) getString(R.string.log_empty) else body
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copyLog() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = tvFacts.text.toString() + "\n\n" + DebugLog.dump()
        clipboard.setPrimaryClip(ClipData.newPlainText("Fighter log", text))
        Toast.makeText(this, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
    }
}

/** Small helper so the log screen can show the app version. */
object BuildFacts {
    fun version(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "v${info.versionName} (${info.versionCode})"
    } catch (error: Exception) {
        "?"
    }
}
