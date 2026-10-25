package com.fighter.studykeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.fighter.studykeyboard.data.Note
import com.fighter.studykeyboard.data.NoteStore
import com.fighter.studykeyboard.data.PlayerPrefs
import com.fighter.studykeyboard.play.PlaybackService
import com.fighter.studykeyboard.play.SpeechSpeaker
import com.fighter.studykeyboard.play.TextTools

/**
 * Study / test mode ("aankh band karke suno, phir kholke check karo").
 *
 *  - reads the notes aloud (Hinglish/Hindi notes included)
 *  - the text is on screen and the word being spoken is highlighted
 *  - "Hide text" ke saath self test: pehle sirf suno, tap karke answer dekho
 *  - auto-next, shuffle, speed slider, keep-screen-on
 *  - screen off / phone lock hone par bhi voice chalti rehti hai (foreground service)
 */
class StudyActivity : Activity(), SpeechSpeaker.Listener {

    private lateinit var store: NoteStore
    private lateinit var prefs: PlayerPrefs
    private lateinit var speaker: SpeechSpeaker

    private lateinit var tvProgress: TextView
    private lateinit var tvNote: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvStatus: TextView
    private lateinit var seekSpeed: SeekBar
    private lateinit var btnPlay: TextView
    private lateinit var btnHide: TextView
    private lateinit var btnAuto: TextView
    private lateinit var btnShuffle: TextView
    private lateinit var btnScreen: TextView
    private lateinit var revealOverlay: View

    private val handler = Handler(Looper.getMainLooper())
    private val notes = ArrayList<Note>()

    private var index = 0
    private var playing = false
    private var hiddenForTest = false
    private var revealed = false
    private var currentText = ""
    private var keepScreenOn = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study)

        store = NoteStore.get(this)
        prefs = PlayerPrefs(this)
        speaker = SpeechSpeaker(this)
        speaker.listener = this

        tvProgress = findViewById(R.id.tvStudyProgress)
        tvNote = findViewById(R.id.tvStudyNote)
        tvSpeed = findViewById(R.id.tvStudySpeed)
        tvStatus = findViewById(R.id.tvStudyStatus)
        seekSpeed = findViewById(R.id.seekStudySpeed)
        btnPlay = findViewById(R.id.btnStudyPlay)
        btnHide = findViewById(R.id.btnStudyHide)
        btnAuto = findViewById(R.id.btnStudyAuto)
        btnShuffle = findViewById(R.id.btnStudyShuffle)
        btnScreen = findViewById(R.id.btnStudyScreen)
        revealOverlay = findViewById(R.id.revealOverlay)

        notes.addAll(loadOrderedNotes())
        if (notes.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_notes_hint), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

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

        findViewById<View>(R.id.btnStudyPrev).setOnClickListener { move(-1) }
        findViewById<View>(R.id.btnStudyNext).setOnClickListener { move(1) }
        findViewById<View>(R.id.btnStudyRepeat).setOnClickListener { speakCurrent(reveal = false) }
        findViewById<View>(R.id.btnStudyClose).setOnClickListener { finish() }
        revealOverlay.setOnClickListener { revealText() }
        tvNote.setOnClickListener { revealText() }

        btnPlay.setOnClickListener { if (playing) pausePlayback() else speakCurrent(reveal = false) }
        btnHide.setOnClickListener {
            hiddenForTest = !hiddenForTest
            revealed = false
            updateToggles()
            renderNote()
        }
        btnAuto.setOnClickListener {
            prefs.studyAutoNext = !prefs.studyAutoNext
            updateToggles()
        }
        btnShuffle.setOnClickListener {
            prefs.shuffle = !prefs.shuffle
            updateToggles()
            Toast.makeText(
                this,
                getString(if (prefs.shuffle) R.string.shuffle_on else R.string.shuffle_off),
                Toast.LENGTH_SHORT
            ).show()
        }
        btnScreen.setOnClickListener {
            keepScreenOn = !keepScreenOn
            applyKeepScreenOn()
            updateToggles()
        }

        updateSpeedLabel()
        updateToggles()
        applyKeepScreenOn()
        renderNote()
        // Start reading shortly after opening, so the screen is ready.
        handler.postDelayed({ speakCurrent(reveal = false) }, 450L)
    }

    private fun loadOrderedNotes(): List<Note> {
        val all = store.all()
        val startId = prefs.lastNoteId
        val ordered = if (prefs.shuffle) all.shuffled() else all
        if (startId == null) return ordered
        val startIndex = ordered.indexOfFirst { it.id == startId }
        if (startIndex <= 0) return ordered
        // Continue from the note that the keyboard played last.
        return ordered.drop(startIndex) + ordered.take(startIndex)
    }

    // ------------------------------------------------------------------ playback

    private fun speakCurrent(reveal: Boolean) {
        if (notes.isEmpty()) return
        val note = notes[index.coerceIn(0, notes.size - 1)]
        currentText = if (prefs.capsMode) TextTools.capsText(note.text) else note.text
        prefs.lastNoteId = note.id
        playing = true
        if (reveal) revealed = true else revealed = !hiddenForTest
        renderNote()
        updateControls()

        PlaybackService.stopRequested = { finishSession() }
        PlaybackService.start(this, statusLine(), PlaybackService.MODE_VOICE)
        speaker.start(currentText, prefs.speechRate, false)
        tvStatus.text = getString(R.string.study_listening)
    }

    private fun pausePlayback() {
        playing = false
        speaker.stop()
        PlaybackService.update(this, getString(R.string.study_paused), PlaybackService.MODE_VOICE)
        tvStatus.text = getString(R.string.study_paused)
        updateControls()
    }

    private fun finishSession() {
        playing = false
        speaker.stop()
        PlaybackService.stop(this)
        PlaybackService.stopRequested = null
        finish()
    }

    private fun move(direction: Int) {
        if (notes.isEmpty()) return
        speaker.stop()
        index = (index + direction + notes.size) % notes.size
        if (playing) speakCurrent(reveal = false) else {
            revealed = !hiddenForTest
            renderNote()
        }
    }

    private fun advance() {
        if (notes.size <= 1) {
            speakCurrent(reveal = false)
            return
        }
        index = (index + 1) % notes.size
        if (prefs.shuffle && index == 0) notes.shuffle()
        speakCurrent(reveal = false)
    }

    // ------------------------------------------------------------------ ui

    private fun renderNote() {
        if (notes.isEmpty()) return
        val note = notes[index.coerceIn(0, notes.size - 1)]
        val text = if (prefs.capsMode) TextTools.capsText(note.text) else note.text
        currentText = text
        tvProgress.text = getString(
            R.string.study_progress,
            index + 1,
            notes.size,
            TextTools.words(text).size
        )

        val hide = hiddenForTest && !revealed
        revealOverlay.visibility = if (hide) View.VISIBLE else View.GONE
        tvNote.text = text
    }

    private fun revealText() {
        if (!hiddenForTest || revealed) return
        revealed = true
        revealOverlay.visibility = View.GONE
        tvStatus.text = getString(R.string.study_check)
    }

    private fun updateControls() {
        btnPlay.text = getString(if (playing) R.string.tb_pause else R.string.tb_play)
        btnPlay.isSelected = playing
    }

    private fun updateToggles() {
        btnHide.isSelected = hiddenForTest
        btnAuto.isSelected = prefs.studyAutoNext
        btnShuffle.isSelected = prefs.shuffle
        btnScreen.isSelected = keepScreenOn
    }

    private fun updateSpeedLabel() {
        tvSpeed.text = getString(R.string.speed_voice_value, prefs.speechRate)
    }

    private fun applyKeepScreenOn() {
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun statusLine(): String = getString(R.string.notif_study, index + 1, notes.size)

    private fun highlight(start: Int, end: Int) {
        if (currentText.isEmpty()) return
        val safeStart = start.coerceIn(0, currentText.length)
        val safeEnd = end.coerceIn(safeStart, currentText.length)
        if (safeEnd <= safeStart) return
        val spannable = SpannableString(currentText)
        spannable.setSpan(
            BackgroundColorSpan(HIGHLIGHT_COLOR),
            safeStart,
            safeEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            safeStart,
            safeEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvNote.text = spannable
    }

    // ------------------------------------------------------------------ speaker callbacks

    override fun onSpeechStarted(charCount: Int) {
        playing = true
        updateControls()
        renderNote()
        tvStatus.text = getString(R.string.study_listening)
    }

    override fun onSpeechRange(start: Int, end: Int) {
        highlight(start, end)
    }

    override fun onSpeechDone() {
        if (!playing) return
        PlaybackService.update(this, statusLine(), PlaybackService.MODE_VOICE)
        tvStatus.text = getString(R.string.study_check)
        if (hiddenForTest && !revealed) {
            // Let the student think, then tap to check.
            return
        }
        if (prefs.studyAutoNext && notes.size > 1) {
            handler.postDelayed({ if (playing) advance() }, AUTO_NEXT_DELAY_MS)
        } else {
            playing = false
            updateControls()
        }
    }

    override fun onSpeechUnavailable(reason: String) {
        playing = false
        updateControls()
        tvStatus.text = getString(R.string.voice_unavailable)
        Toast.makeText(this, getString(R.string.voice_unavailable), Toast.LENGTH_LONG).show()
    }

    override fun onSpeechStateChanged() = Unit

    // ------------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        speaker.stop()
        speaker.shutdown()
        PlaybackService.stop(this)
        PlaybackService.stopRequested = null
        super.onDestroy()
    }

    private companion object {
        const val AUTO_NEXT_DELAY_MS = 1500L
        const val HIGHLIGHT_COLOR = 0x807FB2FF.toInt()
    }
}
