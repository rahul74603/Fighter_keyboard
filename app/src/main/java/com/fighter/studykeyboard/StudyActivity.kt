package com.fighter.studykeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.fighter.studykeyboard.data.NoteStore
import com.fighter.studykeyboard.data.PlayerPrefs
import com.fighter.studykeyboard.play.PlaybackBridge
import com.fighter.studykeyboard.play.StudyPlaybackService

/**
 * Study / test mode screen - a *controller* only.
 *
 * All the playback (queue + text-to-speech) runs inside [StudyPlaybackService],
 * so notes keep playing when this screen is closed, the app is minimised, the
 * phone is locked or the screen is off. This screen just shows what is playing,
 * highlights the word being spoken and sends play/pause/next/prev commands.
 */
class StudyActivity : Activity(), PlaybackBridge.Listener {

    private lateinit var store: NoteStore
    private lateinit var prefs: PlayerPrefs

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

    private var hiddenForTest = false
    private var revealed = false
    private var keepScreenOn = true
    private var lastState: PlaybackBridge.State = PlaybackBridge.State()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study)

        store = NoteStore.get(this)
        prefs = PlayerPrefs(this)

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

        if (store.all().isEmpty()) {
            Toast.makeText(this, getString(R.string.no_notes_hint), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        ensureNotificationPermission()

        seekSpeed.max = 100
        seekSpeed.progress = prefs.speedProgress
        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs.speedProgress = progress
                updateSpeedLabel()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                StudyPlaybackService.command(this@StudyActivity, StudyPlaybackService.ACTION_SPEED)
            }
        })

        findViewById<View>(R.id.btnStudyPrev).setOnClickListener {
            revealed = !hiddenForTest
            StudyPlaybackService.command(this, StudyPlaybackService.ACTION_PREV)
        }
        findViewById<View>(R.id.btnStudyNext).setOnClickListener {
            revealed = !hiddenForTest
            StudyPlaybackService.command(this, StudyPlaybackService.ACTION_NEXT)
        }
        findViewById<View>(R.id.btnStudyRepeat).setOnClickListener {
            revealed = !hiddenForTest
            StudyPlaybackService.command(this, StudyPlaybackService.ACTION_REPEAT)
        }
        findViewById<View>(R.id.btnStudyClose).setOnClickListener {
            StudyPlaybackService.command(this, StudyPlaybackService.ACTION_STOP)
            finish()
        }
        revealOverlay.setOnClickListener { revealText() }
        tvNote.setOnClickListener { revealText() }

        btnPlay.setOnClickListener {
            val state = PlaybackBridge.state
            when {
                !state.hasSession -> playAll()
                state.playing -> StudyPlaybackService.command(this, StudyPlaybackService.ACTION_PAUSE)
                else -> StudyPlaybackService.command(this, StudyPlaybackService.ACTION_RESUME)
            }
        }
        btnHide.setOnClickListener {
            hiddenForTest = !hiddenForTest
            revealed = false
            updateToggles()
            render(lastState)
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
    }

    private fun playAll() {
        revealed = !hiddenForTest
        StudyPlaybackService.play(this, playAll = true)
    }

    override fun onStart() {
        super.onStart()
        PlaybackBridge.register(this)
        val state = PlaybackBridge.state
        if (!state.hasSession) {
            // Opening this screen starts (or continues) the background session.
            playAll()
        } else {
            render(state)
        }
    }

    override fun onStop() {
        PlaybackBridge.unregister(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn()
    }

    /**
     * Back = app background me (session band nahi hota), taaki padhai chalti rahe.
     * Band karna ho to ✕ ya notification ka Stop.
     */
    @Deprecated("Handled explicitly so background playback survives")
    override fun onBackPressed() {
        val state = PlaybackBridge.state
        if (state.hasSession && (state.playing || state.paused)) {
            moveTaskToBack(true)
            Toast.makeText(this, getString(R.string.study_background), Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ rendering

    override fun onPlaybackState(state: PlaybackBridge.State) {
        lastState = state
        render(state)
    }

    private fun render(state: PlaybackBridge.State) {
        tvProgress.text = if (state.hasSession) {
            getString(
                R.string.study_progress,
                state.noteIndex + 1,
                state.noteCount,
                com.fighter.studykeyboard.play.TextTools.words(state.text).size
            )
        } else {
            getString(R.string.study_idle)
        }

        val hide = hiddenForTest && !revealed
        revealOverlay.visibility = if (hide) View.VISIBLE else View.GONE
        if (!hide) {
            tvNote.text = state.text
            if (state.highlightEnd > state.highlightStart) {
                highlight(state.text, state.highlightStart, state.highlightEnd)
            }
        }
        tvStatus.text = state.status
        btnPlay.text = getString(if (state.playing) R.string.tb_pause else R.string.tb_play)
        btnPlay.isSelected = state.playing
    }

    private fun highlight(text: String, start: Int, end: Int) {
        if (text.isEmpty()) return
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        if (safeEnd <= safeStart) return
        val spannable = SpannableString(text)
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

    private fun revealText() {
        if (!hiddenForTest || revealed) return
        revealed = true
        revealOverlay.visibility = View.GONE
        render(lastState)
        tvStatus.text = getString(R.string.study_check)
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

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private companion object {
        const val HIGHLIGHT_COLOR = 0x807FB2FF.toInt()
    }
}
