package com.fighter.studykeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.fighter.studykeyboard.play.TextTools
import com.fighter.studykeyboard.ui.NotesAdapter

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
        updateToggles()
        updateSpeedLabel()
    }

    private fun updateToggles() {
        tgLoop.isSelected = prefs.loop
        tgVoice.isSelected = prefs.voiceEnabled
        tgLetters.isSelected = prefs.letterByLetter
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
