package com.fighter.studykeyboard

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.fighter.studykeyboard.data.NoteStore
import com.fighter.studykeyboard.play.TextTools

/**
 * Small dialog-like screen for adding or editing one note.
 *
 * It is an Activity (not a Dialog thrown from the keyboard service) because
 * an InputMethodService has no window token for dialogs, and it also makes the
 * soft keyboard behave normally while writing a note.
 */
class NoteEditActivity : Activity() {

    private lateinit var store: NoteStore
    private lateinit var etNote: EditText
    private lateinit var tvTitle: TextView
    private lateinit var tvWordCount: TextView
    private lateinit var btnDelete: View

    private var noteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_edit)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        store = NoteStore.get(this)
        etNote = findViewById(R.id.etNote)
        tvTitle = findViewById(R.id.tvTitle)
        tvWordCount = findViewById(R.id.tvWordCount)
        btnDelete = findViewById(R.id.btnDelete)

        noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        val existing = noteId?.let { store.get(it) }
        // New note = completely blank field. Only an edited note is pre-filled.
        etNote.setText(existing?.text.orEmpty())
        etNote.setSelection(etNote.text.length)
        tvTitle.text = getString(if (existing == null) R.string.add_note else R.string.edit_note)
        btnDelete.visibility = if (existing == null) View.GONE else View.VISIBLE

        etNote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateWordCount()
            }
        })
        updateWordCount()

        findViewById<View>(R.id.btnPaste).setOnClickListener { pasteFromClipboard() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }
        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        btnDelete.setOnClickListener { deleteNote() }

        etNote.requestFocus()
    }

    /** Paste button: only pastes when the user asks for it. */
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        } else {
            ""
        }
        if (text.isBlank()) {
            Toast.makeText(this, getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val current = etNote.text.toString()
        if (current.isBlank()) {
            etNote.setText(text)
        } else {
            val separator = if (current.endsWith(" ") || current.endsWith("\n")) "" else " "
            etNote.append(separator + text)
        }
        etNote.setSelection(etNote.text.length)
    }

    private fun updateWordCount() {
        val words = TextTools.words(etNote.text.toString()).size
        tvWordCount.text = resources.getQuantityString(R.plurals.note_word_count, words, words)
    }

    private fun save() {
        val text = etNote.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, getString(R.string.note_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val id = noteId
        if (id == null) store.add(text) else store.update(id, text)
        setResult(RESULT_OK)
        finish()
    }

    private fun deleteNote() {
        val id = noteId
        if (id != null) {
            store.delete(id)
            Toast.makeText(this, getString(R.string.note_deleted), Toast.LENGTH_SHORT).show()
        }
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"

        fun intentFor(context: Context, noteId: String?): Intent =
            Intent(context, NoteEditActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
