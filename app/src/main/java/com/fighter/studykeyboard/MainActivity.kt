package com.fighter.studykeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.widget.EditText
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.fighter.studykeyboard.data.Note
import com.fighter.studykeyboard.data.NoteStore
import com.fighter.studykeyboard.data.PlayerPrefs
import com.fighter.studykeyboard.play.AutoSendService
import com.fighter.studykeyboard.play.StudyPlaybackService
import com.fighter.studykeyboard.play.TextTools
import com.fighter.studykeyboard.ui.NotesAdapter
import com.fighter.studykeyboard.util.DebugLog

/**
 * Setup + note manager. The keyboard itself lives in [FighterKeyboardService],
 * this screen is where the user enables it and manages the unlimited notes.
 */
class MainActivity : Activity(), NotesAdapter.Callback {

    private lateinit var store: NoteStore
    private lateinit var prefs: PlayerPrefs
    private lateinit var adapter: NotesAdapter

    private lateinit var tvStatus: TextView
    private lateinit var tvNotesTitle: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var lvNotes: ListView
    private lateinit var etPractice: EditText
    private lateinit var seekSpeed: SeekBar
    private lateinit var tvSpeedValue: TextView
    private lateinit var tgLoop: TextView
    private lateinit var tgVoice: TextView
    private lateinit var tgLetters: TextView
    private lateinit var tgCaps: TextView
    private lateinit var tgAutoSend: TextView
    private lateinit var tgShuffle: TextView
    private lateinit var tvAccessibilityState: TextView
    private lateinit var tvDiag: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var previewWords: List<String> = emptyList()
    private var previewIndex = 0
    private var previewRunning = false

    private val previewStep = object : Runnable {
        override fun run() {
            if (!previewRunning) return
            if (previewIndex >= previewWords.size) {
                previewRunning = false
                return
            }
            val word = previewWords[previewIndex]
            etPractice.append(if (previewIndex == 0) word else " $word")
            previewIndex++
            handler.postDelayed(this, prefs.wordDelayMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = NoteStore.get(this)
        prefs = PlayerPrefs(this)

        tvStatus = findViewById(R.id.tvKeyboardStatus)
        tvNotesTitle = findViewById(R.id.tvNotesTitle)
        tvEmpty = findViewById(R.id.tvEmpty)
        lvNotes = findViewById(R.id.lvNotes)
        etPractice = findViewById(R.id.etPractice)

        seekSpeed = findViewById(R.id.seekSpeed)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)
        tgLoop = findViewById(R.id.tgLoop)
        tgVoice = findViewById(R.id.tgVoice)
        tgLetters = findViewById(R.id.tgLetters)
        tgCaps = findViewById(R.id.tgCaps)
        tgAutoSend = findViewById(R.id.tgAutoSend)
        tgShuffle = findViewById(R.id.tgShuffle)
        tvAccessibilityState = findViewById(R.id.tvAccessibilityState)
        tvDiag = findViewById(R.id.tvDiag)

        adapter = NotesAdapter(R.layout.item_note, this)
        lvNotes.adapter = adapter

        findViewById<View>(R.id.btnEnable).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                toast(getString(R.string.enable_hint))
            } catch (error: Exception) {
                toast(getString(R.string.enable_hint))
            }
        }
        findViewById<View>(R.id.btnPick).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        findViewById<View>(R.id.btnStudyMode).setOnClickListener {
            stopPreview()
            startActivity(Intent(this, StudyActivity::class.java))
        }
        findViewById<View>(R.id.btnRestrictedHelp).setOnClickListener { showRestrictedHelp() }
        findViewById<View>(R.id.btnBattery).setOnClickListener { requestBatteryExemption() }
        findViewById<View>(R.id.btnOpenLog).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        findViewById<View>(R.id.btnVoiceTest).setOnClickListener {
            StudyPlaybackService.command(this, StudyPlaybackService.ACTION_TEST)
            toast(getString(R.string.study_testing_voice))
        }
        findViewById<View>(R.id.btnBackgroundPlay).setOnClickListener {
            StudyPlaybackService.play(this, playAll = true, intro = true)
            toast(getString(R.string.background_started))
        }
        findViewById<View>(R.id.btnBrowser).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
        findViewById<View>(R.id.btnAccessibility).setOnClickListener {
            if (AutoSendService.isEnabled(this)) {
                toast(getString(R.string.accessibility_on))
                return@setOnClickListener
            }
            // Sideloaded apps hit "Restricted setting" on Android 13+, so explain first.
            showRestrictedHelp()
        }
        findViewById<View>(R.id.btnAddNote).setOnClickListener { openNoteEditor(null) }
        findViewById<View>(R.id.btnExport).setOnClickListener { exportNotes() }
        findViewById<View>(R.id.btnImport).setOnClickListener { importNotes() }
        findViewById<View>(R.id.btnDeleteAll).setOnClickListener { confirmDeleteAll() }

        seekSpeed.max = 100
        seekSpeed.progress = prefs.speedProgress
        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs.speedProgress = progress
                updateSpeedLabel()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        tgLoop.setOnClickListener {
            prefs.loop = !prefs.loop
            updateToggles()
        }
        tgVoice.setOnClickListener {
            prefs.voiceEnabled = !prefs.voiceEnabled
            updateToggles()
            updateSpeedLabel()
        }
        tgLetters.setOnClickListener {
            prefs.letterByLetter = !prefs.letterByLetter
            updateToggles()
        }
        tgCaps.setOnClickListener {
            prefs.capsMode = !prefs.capsMode
            updateToggles()
            toast(getString(if (prefs.capsMode) R.string.caps_on else R.string.caps_off))
        }
        tgAutoSend.setOnClickListener {
            prefs.autoSend = !prefs.autoSend
            updateToggles()
            toast(getString(if (prefs.autoSend) R.string.auto_send_on else R.string.auto_send_off))
        }
        tgShuffle.setOnClickListener {
            prefs.shuffle = !prefs.shuffle
            updateToggles()
            toast(getString(if (prefs.shuffle) R.string.shuffle_on else R.string.shuffle_off))
        }
        updateToggles()
        updateSpeedLabel()
    }

    private fun updateToggles() {
        tgLoop.isSelected = prefs.loop
        tgVoice.isSelected = prefs.voiceEnabled
        tgLetters.isSelected = prefs.letterByLetter
        tgCaps.isSelected = prefs.capsMode
        tgAutoSend.isSelected = prefs.autoSend
        tgShuffle.isSelected = prefs.shuffle
    }

    /** Android 13+ needs a runtime permission for the playback notification. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIF)
    }

    private fun updateSpeedLabel() {
        tvSpeedValue.text = if (prefs.voiceEnabled) {
            getString(R.string.speed_voice_value, prefs.speechRate)
        } else {
            getString(R.string.speed_type_value, prefs.wordDelayMs)
        }
    }

    override fun onResume() {
        super.onResume()
        askNotificationPermission()
        DebugLog.log("App", "resumed")
        refresh()
    }

    override fun onDestroy() {
        previewRunning = false
        handler.removeCallbacks(previewStep)
        super.onDestroy()
    }

    private fun refresh() {
        val notes = store.all()
        adapter.submit(notes)
        tvNotesTitle.text = getString(R.string.notes_count_title, notes.size)
        tvEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        lvNotes.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        seekSpeed.progress = prefs.speedProgress
        updateToggles()
        updateSpeedLabel()
        tvAccessibilityState.text = getString(
            if (AutoSendService.isEnabled(this)) R.string.accessibility_on
            else R.string.accessibility_off
        )
        val notificationsOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val accessibilityOn = AutoSendService.isEnabled(this)
        val batteryFree = isBatteryUnrestricted()
        tvDiag.text = getString(
            R.string.diag_status,
            getString(if (notificationsOn) R.string.diag_yes else R.string.diag_no),
            getString(if (accessibilityOn) R.string.diag_yes else R.string.diag_no),
            getString(if (batteryFree) R.string.diag_yes else R.string.diag_no)
        )
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isKeyboardEnabled()
        val selected = isKeyboardSelected()
        tvStatus.text = when {
            selected -> getString(R.string.status_selected)
            enabled -> getString(R.string.status_enabled_only)
            else -> getString(R.string.status_disabled)
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isKeyboardSelected(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: return false
        return current.startsWith(packageName)
    }

    // ------------------------------------------------------------- notes

    override fun onPlay(note: Note) {
        stopPreview()
        val words = TextTools.words(note.text)
        etPractice.setText("")
        etPractice.requestFocus()
        previewWords = words
        previewIndex = 0
        previewRunning = true
        handler.postDelayed(previewStep, 250L)
        toast(getString(R.string.preview_started))
    }

    override fun onEdit(note: Note) {
        openNoteEditor(note.id)
    }

    private fun openNoteEditor(noteId: String?) {
        stopPreview()
        startActivity(NoteEditActivity.intentFor(this, noteId))
    }

    override fun onDelete(note: Note) {
        val themed = ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        AlertDialog.Builder(themed)
            .setTitle(R.string.delete_note)
            .setMessage(note.preview(140))
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(note.id)
                if (prefs.lastNoteId == note.id) prefs.lastNoteId = null
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun stopPreview() {
        previewRunning = false
        handler.removeCallbacks(previewStep)
    }

    private fun confirmDeleteAll() {
        val themed = ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        AlertDialog.Builder(themed)
            .setTitle(R.string.delete_all)
            .setMessage(R.string.delete_all_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.deleteAll()
                prefs.lastNoteId = null
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------- import / export

    private fun exportNotes() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val json = store.exportJson()
        clipboard.setPrimaryClip(ClipData.newPlainText("Fighter notes", json))
        toast(getString(R.string.export_done))
    }

    private fun importNotes() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            toast(getString(R.string.clipboard_empty))
            return
        }
        val raw = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (raw.isBlank()) {
            toast(getString(R.string.clipboard_empty))
            return
        }
        val added = store.importJson(raw)
        if (added == 0) toast(getString(R.string.import_failed)) else toast(getString(R.string.import_done, added))
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIF && grantResults.firstOrNull() != 0) {
            toast(getString(R.string.notif_permission_hint))
        }
    }

    /**
     * Android 13+ blocks accessibility for sideloaded APKs ("Restricted setting").
     * Show the exact way out, plus the App-info shortcut where the toggle lives.
     */
    private fun showRestrictedHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.restricted_title)
            .setMessage(R.string.restricted_msg)
            .setPositiveButton(R.string.open_app_info) { _, _ ->
                try {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (error: Exception) {
                    toast(getString(R.string.accessibility_hint))
                }
            }
            .setNegativeButton(R.string.accessibility_button) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (error: Exception) {
                    toast(getString(R.string.accessibility_hint))
                }
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    /** Vivo/oppo/mi jaise phones background service kill karte hain - isse chhoot milti hai. */
    private fun requestBatteryExemption() {
        if (isBatteryUnrestricted()) {
            toast(getString(R.string.battery_ok))
            return
        }
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (error: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                toast(getString(R.string.battery_hint))
            } catch (error2: Exception) {
                toast(getString(R.string.battery_hint))
            }
        }
    }

    private fun isBatteryUnrestricted(): Boolean {
        return try {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            power.isIgnoringBatteryOptimizations(packageName)
        } catch (error: Exception) {
            false
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_NOTIF = 1001
    }
}
