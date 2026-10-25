package com.fighter.studykeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.fighter.studykeyboard.data.Note
import com.fighter.studykeyboard.data.NoteStore
import com.fighter.studykeyboard.data.PlayerPrefs
import com.fighter.studykeyboard.play.AutoSendService
import com.fighter.studykeyboard.play.PlaybackService
import com.fighter.studykeyboard.play.ReadingPlayer
import com.fighter.studykeyboard.play.SpeechSpeaker
import com.fighter.studykeyboard.play.TextTools
import com.fighter.studykeyboard.ui.NotesAdapter

/**
 * Fighter Study Keyboard.
 *
 *  - unlimited notes saved on the phone
 *  - "+ Add note" adds one more note (paste any study text)
 *  - Play types the note into whatever app is focused, one word at a time
 *  - speed slider controls how fast the words keep coming
 *  - optional voice mode reads it aloud while it is typed
 */
class FighterKeyboardService : InputMethodService(),
    ReadingPlayer.Listener,
    SpeechSpeaker.Listener,
    NotesAdapter.Callback {

    private lateinit var store: NoteStore
    private lateinit var prefs: PlayerPrefs
    private lateinit var player: ReadingPlayer
    private lateinit var speaker: SpeechSpeaker
    private lateinit var notesAdapter: NotesAdapter

    private var rootView: View? = null
    private var frameMain: FrameLayout? = null
    private var keyboardArea: View? = null
    private var lettersArea: View? = null
    private var symbolsArea: View? = null
    private var notesPanel: View? = null
    private var suggestionsScroll: View? = null
    private var suggestionsBar: LinearLayout? = null

    private var btnNotes: TextView? = null
    private var btnPlay: TextView? = null
    private var btnPause: TextView? = null
    private var btnStop: TextView? = null
    private var btnCaps: TextView? = null
    private var btnShuffle: TextView? = null
    private var btnVoice: TextView? = null
    private var btnShift: TextView? = null

    private var tvStatus: TextView? = null
    private var tvSpeedValue: TextView? = null
    private var seekSpeed: SeekBar? = null
    private var lvNotes: ListView? = null
    private var tvEmptyHint: View? = null

    private val letterKeys = ArrayList<TextView>()
    private var dictionary: List<String> = emptyList()

    private var shiftOn = false
    private var capsLock = false
    private var lastShiftTapAt = 0L
    private var notesOpen = false

    private var idleStatus: String = ""
    private var wordStatus: String = ""

    private var spokenText = ""
    private var spokenPos = 0

    private val handler = Handler(Looper.getMainLooper())
    private val queueHandler = Handler(Looper.getMainLooper())
    private var deleteRepeat: Runnable? = null

    // Playback queue: every note is sent on its own, one after another.
    private val queue = ArrayList<String>()
    private var queueIndex = 0
    private var itemFinished = false
    private var playbackActive = false

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        store = NoteStore.get(this)
        prefs = PlayerPrefs(this)
        player = ReadingPlayer(prefs) { currentInputConnection }
        player.listener = this
        speaker = SpeechSpeaker(this)
        speaker.listener = this
        refreshDictionary()
        refreshIdleStatus()
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        rootView = view
        frameMain = view.findViewById(R.id.frameMain)
        keyboardArea = view.findViewById(R.id.keyboardArea)
        lettersArea = view.findViewById(R.id.lettersArea)
        symbolsArea = view.findViewById(R.id.symbolsArea)
        notesPanel = view.findViewById(R.id.notesPanel)
        suggestionsScroll = view.findViewById(R.id.suggestionsScroll)
        suggestionsBar = view.findViewById(R.id.suggestionsBar)

        btnNotes = view.findViewById(R.id.btnNotes)
        btnPlay = view.findViewById(R.id.btnPlay)
        btnPause = view.findViewById(R.id.btnPause)
        btnStop = view.findViewById(R.id.btnStop)
        btnCaps = view.findViewById(R.id.btnCaps)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnVoice = view.findViewById(R.id.btnVoice)
        btnShift = view.findViewById(R.id.btnShift)

        tvStatus = view.findViewById(R.id.tvStatus)
        tvSpeedValue = view.findViewById(R.id.tvSpeedValue)
        seekSpeed = view.findViewById(R.id.seekSpeed)
        lvNotes = view.findViewById(R.id.lvNotes)
        tvEmptyHint = view.findViewById(R.id.tvEmptyHint)

        notesAdapter = NotesAdapter(R.layout.item_note_dark, this)
        lvNotes?.adapter = notesAdapter

        letterKeys.clear()
        bindKeys(view)
        wireToolbar(view)
        wireSpeed(view)
        wireNotesPanel(view)

        refreshNotes()
        updateSpeedLabel()
        updateToolbar()
        applyShiftUi()
        refreshStatus()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (playbackActive && !speaker.isRunning) {
            player.resumeTimers()
            updatePlaybackNotification()
        }
        refreshDictionary()
        refreshNotes()
        updateSpeedLabel()
        updateToolbar()
        refreshStatus()
        updateSuggestions()
        applyShiftUi()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Voice keeps talking in the background. Typing needs a visible text field,
        // so it pauses here and resumes by itself when the keyboard is back.
        if (playbackActive) {
            player.suspendTimers()
            PlaybackService.update(this, getString(R.string.notif_paused_hidden), playbackMode())
        } else {
            stopPlayback()
        }
        clearSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        deleteRepeat?.let { handler.removeCallbacks(it) }
        player.stop()
        speaker.shutdown()
        PlaybackService.stop(this)
        PlaybackService.stopRequested = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ wiring

    private fun wireToolbar(view: View) {
        btnPlay?.setOnClickListener { startPlayback() }
        btnPlay?.setOnLongClickListener {
            startPlayback(playAll = true)
            true
        }
        btnPause?.setOnClickListener { togglePause() }
        btnStop?.setOnClickListener { stopPlayback() }
        btnCaps?.setOnClickListener {
            prefs.capsMode = !prefs.capsMode
            applyShiftUi()
            updateToolbar()
            toast(getString(if (prefs.capsMode) R.string.caps_on else R.string.caps_off))
        }
        btnShuffle?.setOnClickListener {
            prefs.shuffle = !prefs.shuffle
            updateToolbar()
            toast(getString(if (prefs.shuffle) R.string.shuffle_on else R.string.shuffle_off))
        }
        btnVoice?.setOnClickListener {
            prefs.voiceEnabled = !prefs.voiceEnabled
            updateSpeedLabel()
            updateToolbar()
            toast(getString(if (prefs.voiceEnabled) R.string.tb_voice_on else R.string.tb_voice_off))
        }
        view.findViewById<View>(R.id.btnMore)?.setOnClickListener { openSettings() }
        btnNotes?.setOnClickListener { openNotesPanel(!notesOpen) }
        btnNotes?.setOnLongClickListener {
            openNoteEditor(null)
            true
        }
    }

    private fun wireNotesPanel(view: View) {
        view.findViewById<View>(R.id.btnCloseNotes)?.setOnClickListener { openNotesPanel(false) }
        view.findViewById<View>(R.id.btnAddNote)?.setOnClickListener { openNoteEditor(null) }
        view.findViewById<View>(R.id.btnPlayAll)?.setOnClickListener { startPlayback(playAll = true) }
    }

    private fun wireSpeed(view: View) {
        val seek = seekSpeed ?: return
        seek.max = 100
        seek.progress = prefs.speedProgress
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs.speedProgress = progress
                updateSpeedLabel()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
    }

    private fun bindKeys(view: View) {
        if (view is TextView) {
            val tag = view.tag as? String
            if (tag != null) {
                if (tag == "key:del") {
                    bindDeleteKey(view)
                } else {
                    view.setOnClickListener { onKey(tag, it) }
                }
                if (tag.startsWith("c:") && tag.length == 3) letterKeys.add(view)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) bindKeys(view.getChildAt(index))
        }
    }

    private fun bindDeleteKey(key: View) {
        key.setOnTouchListener { source, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    source.isPressed = true
                    source.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    deleteOne()
                    val repeat = object : Runnable {
                        override fun run() {
                            deleteOne()
                            handler.postDelayed(this, 55L)
                        }
                    }
                    deleteRepeat = repeat
                    handler.postDelayed(repeat, 380L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    source.isPressed = false
                    stopDeleteRepeat()
                    true
                }
                else -> false
            }
        }
    }

    private fun stopDeleteRepeat() {
        deleteRepeat?.let { handler.removeCallbacks(it) }
        deleteRepeat = null
    }

    private fun onKey(tag: String, source: View) {
        source.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (tag) {
            "key:shift" -> toggleShift()
            "key:del" -> deleteOne()
            "key:space" -> {
                currentInputConnection?.commitText(" ", 1)
                updateSuggestions()
            }
            "key:enter" -> {
                if (sendEnter()) updateSuggestions()
            }
            "key:mode" -> setSymbols(true)
            "key:letters" -> setSymbols(false)
            else -> if (tag.startsWith("c:")) typeChar(tag.substring(2))
        }
    }

    // ------------------------------------------------------------------ typing

    private fun typeChar(raw: String) {
        val ic = currentInputConnection ?: return
        var text = raw
        if (text.length == 1 && text[0].isLetter() && upperCaseActive()) {
            text = if (prefs.capsMode) TextTools.capsText(text) else text.uppercase()
        }
        ic.commitText(text, 1)
        if (shiftOn && !capsLock) {
            shiftOn = false
            applyShiftUi()
        }
        updateSuggestions()
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        updateSuggestions()
    }

    private fun toggleShift() {
        val now = System.currentTimeMillis()
        if (now - lastShiftTapAt < 350L) {
            capsLock = !capsLock
            shiftOn = capsLock
        } else {
            shiftOn = !shiftOn
            if (!shiftOn) capsLock = false
        }
        lastShiftTapAt = now
        applyShiftUi()
    }

    /** T-mode = always capitals; SHIFT flips it to small letters while held/toggled. */
    private fun upperCaseActive(): Boolean {
        val shiftActive = shiftOn || capsLock
        return if (prefs.capsMode) !shiftActive else shiftActive
    }

    private fun applyShiftUi() {
        val upper = upperCaseActive()
        for (key in letterKeys) {
            val base = (key.tag as? String)?.substring(2) ?: continue
            key.text = if (upper) base.uppercase() else base
        }
        btnShift?.isSelected = upper
        btnCaps?.isSelected = prefs.capsMode
        btnShuffle?.isSelected = prefs.shuffle
    }

    private fun setSymbols(show: Boolean) {
        lettersArea?.visibility = if (show) View.GONE else View.VISIBLE
        symbolsArea?.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            shiftOn = false
            capsLock = false
            applyShiftUi()
        }
    }

    /** Enter sends the current editor action when the app asks for one, else inserts a newline. */
    private fun sendEnter(): Boolean {
        if (triggerEditorSend()) return true
        currentInputConnection?.commitText("\n", 1)
        return true
    }

    /**
     * Presses the app's own send/post action. Works even in multiline fields
     * (WhatsApp, comment boxes) where Enter alone would only add a new line.
     */
    private fun triggerEditorSend(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val sendLike = action == EditorInfo.IME_ACTION_SEND ||
            action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_DONE ||
            action == EditorInfo.IME_ACTION_NEXT
        if (!sendLike) return false
        return sendDefaultEditorAction(true)
    }

    /**
     * One tap on the app's real "Send" button (accessibility helper), else the
     * IME send action, else a plain newline as the very last resort.
     */
    private fun performAutoSend() {
        if (!playbackActive) return
        if (AutoSendService.isEnabled(this) && AutoSendService.requestSend()) return
        if (triggerEditorSend()) return
        currentInputConnection?.commitText("\n", 1)
    }

    // ------------------------------------------------------------------ suggestions

    private fun refreshDictionary() {
        dictionary = TextTools.dictionary(store.all())
    }

    private fun updateSuggestions() {
        val bar = suggestionsBar ?: return
        if (notesOpen) {
            clearSuggestions()
            return
        }
        val ic = currentInputConnection
        val before = ic?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val prefix = TextTools.currentWordPrefix(before)
        val words = TextTools.suggestionsFor(prefix, dictionary, SUGGESTION_LIMIT)
        bar.removeAllViews()
        val horizontal = dp(8)
        val vertical = dp(3)
        for (word in words) {
            val chip = TextView(this)
            chip.text = word
            chip.textSize = 13f
            chip.setTextColor(colorRes(R.color.kb_text))
            chip.setPadding(horizontal, vertical, horizontal, vertical)
            chip.background = getDrawable(R.drawable.suggestion_chip)
            chip.isClickable = true
            chip.isFocusable = false
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dp(5)
            chip.layoutParams = params
            chip.setOnClickListener { commitSuggestion(word) }
            bar.addView(chip)
        }
    }

    private fun clearSuggestions() {
        suggestionsBar?.removeAllViews()
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val prefix = TextTools.currentWordPrefix(before)
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        updateSuggestions()
    }

    // ------------------------------------------------------------------ playback

    private fun startPlayback(note: Note? = null, playAll: Boolean = false) {
        stopPlayback()
        val notes = store.all()
        if (notes.isEmpty()) {
            toast(getString(R.string.no_notes_hint))
            openNotesPanel(true)
            return
        }
        openNotesPanel(false)

        // Order: Play-all = every note (shuffled when enabled),
        // Play = one note, randomised so the same message is not sent again and again.
        val ordered: List<Note> = when {
            playAll -> if (prefs.shuffle) notes.shuffled() else notes
            note != null -> listOf(note)
            prefs.shuffle && notes.size > 1 -> listOf(
                notes.filter { it.id != prefs.lastNoteId }.random()
            )
            else -> {
                val last = prefs.lastNoteId?.let { id -> notes.firstOrNull { it.id == id } }
                listOf(last ?: notes.first())
            }
        }

        queue.clear()
        ordered.forEach { queue.add(applyCapsMode(it.text)) }
        queueIndex = 0
        itemFinished = false
        playbackActive = true
        if (!playAll) prefs.lastNoteId = ordered.first().id

        currentInputConnection?.finishComposingText()
        spokenText = queue.first()
        spokenPos = 0
        wordStatus = ""

        PlaybackService.stopRequested = { stopPlayback() }
        PlaybackService.start(this, playbackStatusText(), playbackMode())
        beginQueueItem()
    }

    /** Starts the note that is currently at [queueIndex]. */
    private fun beginQueueItem() {
        if (!playbackActive || queueIndex >= queue.size) return
        itemFinished = false
        val text = queue[queueIndex]
        spokenText = text
        spokenPos = 0
        wordStatus = ""

        val voice = prefs.voiceEnabled
        if (voice && speaker.supportsRangeSync()) {
            speaker.start(text, prefs.speechRate, false)
        } else if (voice) {
            // Older Android: speak and type at the same time.
            player.start(text, loopAllowed = false)
            speaker.start(text, prefs.speechRate, false)
        } else {
            player.start(text, loopAllowed = false)
        }
        updatePlaybackNotification()
        updateToolbar()
        refreshStatus()
    }

    /** Called when one note of the queue is fully typed / spoken. */
    private fun onQueueItemFinished() {
        if (!playbackActive || itemFinished) return
        itemFinished = true

        if (prefs.autoSend) {
            queueHandler.postDelayed({ performAutoSend() }, AUTO_SEND_DELAY_MS)
        }

        queueIndex++
        when {
            queueIndex < queue.size -> queueHandler.postDelayed({ beginQueueItem() }, NEXT_NOTE_DELAY_MS)
            prefs.loop -> queueHandler.postDelayed({ restartQueue() }, NEXT_NOTE_DELAY_MS)
            else -> queueHandler.postDelayed({ finishPlayback() }, NEXT_NOTE_DELAY_MS)
        }
    }

    private fun restartQueue() {
        if (!playbackActive) return
        if (prefs.shuffle) queue.shuffle()
        queueIndex = 0
        beginQueueItem()
    }

    private fun finishPlayback() {
        playbackActive = false
        PlaybackService.stop(this)
        PlaybackService.stopRequested = null
        wordStatus = ""
        updateToolbar()
        refreshStatus()
    }

    private fun applyCapsMode(text: String): String =
        if (prefs.capsMode) TextTools.capsText(text) else text

    private fun playbackMode(): String =
        if (prefs.voiceEnabled) PlaybackService.MODE_VOICE else PlaybackService.MODE_TYPING

    private fun playbackStatusText(): String =
        getString(R.string.notif_queue, (queueIndex + 1).coerceAtMost(queue.size), queue.size)

    private fun updatePlaybackNotification() {
        if (!playbackActive) return
        PlaybackService.update(this, playbackStatusText(), playbackMode())
    }

    private fun togglePause() {
        if (player.isRunning) {
            player.togglePause()
            refreshStatus()
        }
    }

    private fun stopPlayback() {
        playbackActive = false
        queueHandler.removeCallbacksAndMessages(null)
        player.stop()
        speaker.stop()
        PlaybackService.stop(this)
        PlaybackService.stopRequested = null
        queue.clear()
        queueIndex = 0
        spokenPos = 0
        wordStatus = ""
        updateToolbar()
        refreshStatus()
    }

    // ------------------------------------------------------------------ player callbacks

    override fun onPlayerStarted(totalWords: Int) {
        refreshStatus()
        updateToolbar()
    }

    override fun onPlayerWord(index: Int, total: Int, word: String) {
        wordStatus = getString(R.string.player_status, index + 1, total, word)
        tvStatus?.text = wordStatus
    }

    override fun onPlayerFinished() {
        wordStatus = ""
        refreshStatus()
        updateToolbar()
        // In "speak + type" mode the speech drives the queue (avoids double advance).
        if (!speaker.isRunning) onQueueItemFinished()
    }

    override fun onPlayerStateChanged() {
        updateToolbar()
        refreshStatus()
    }

    // ------------------------------------------------------------------ voice callbacks

    override fun onSpeechStarted(charCount: Int) {
        if (spokenPos > 0) currentInputConnection?.commitText("\n", 1)
        spokenPos = 0
        refreshStatus()
        updateToolbar()
    }

    override fun onSpeechRange(start: Int, end: Int) {
        val ic = currentInputConnection ?: return
        val safeEnd = end.coerceIn(0, spokenText.length)
        if (safeEnd <= spokenPos) return
        ic.commitText(spokenText.substring(spokenPos, safeEnd), 1)
        spokenPos = safeEnd
        val rest = spokenText.substring(safeEnd).trimStart()
        val current = rest.takeWhile { !it.isWhitespace() }
        if (current.isNotEmpty()) tvStatus?.text = getString(R.string.status_speaking) + " " + current
    }

    override fun onSpeechDone() {
        if (spokenPos < spokenText.length) {
            currentInputConnection?.commitText(spokenText.substring(spokenPos), 1)
            spokenPos = spokenText.length
        }
        refreshStatus()
        updateToolbar()
        onQueueItemFinished()
    }

    override fun onSpeechUnavailable(reason: String) {
        toast(getString(R.string.voice_unavailable))
        refreshStatus()
        updateToolbar()
    }

    override fun onSpeechStateChanged() {
        updateToolbar()
        refreshStatus()
    }

    // ------------------------------------------------------------------ status / toolbar

    private fun updateToolbar() {
        val running = player.isRunning || speaker.isRunning
        btnPause?.isSelected = player.isPaused
        btnPause?.isEnabled = player.isRunning
        btnPause?.alpha = if (player.isRunning) 1f else 0.4f
        btnStop?.isEnabled = running
        btnStop?.alpha = if (running) 1f else 0.4f
        btnCaps?.isSelected = prefs.capsMode
        btnShuffle?.isSelected = prefs.shuffle
        btnVoice?.isSelected = prefs.voiceEnabled
        btnNotes?.isSelected = notesOpen
        btnPlay?.isSelected = running
        btnNotes?.isSelected = notesOpen
    }

    private fun refreshStatus() {
        tvStatus?.text = when {
            speaker.isRunning -> {
                val rest = spokenText.substring(spokenPos.coerceIn(0, spokenText.length)).trimStart()
                val current = rest.takeWhile { !it.isWhitespace() }
                if (current.isEmpty()) getString(R.string.status_speaking)
                else getString(R.string.status_speaking) + " " + current
            }
            player.isRunning && player.isPaused -> getString(R.string.status_paused)
            player.isRunning || playbackActive -> {
                val progress = if (queue.size > 1) {
                    getString(R.string.notif_queue, (queueIndex + 1).coerceAtMost(queue.size), queue.size) + " • "
                } else ""
                if (wordStatus.isNotEmpty()) progress + wordStatus else progress + idleStatus
            }
            else -> if (idleStatus.isEmpty()) getString(R.string.status_checking) else idleStatus
        }
    }

    private fun refreshIdleStatus() {
        val notes = store.all()
        var words = 0
        for (note in notes) words += note.wordCount
        idleStatus = getString(R.string.status_idle, notes.size, words)
    }

    private fun updateSpeedLabel() {
        tvSpeedValue?.text = if (prefs.voiceEnabled) {
            getString(R.string.speed_voice_value, prefs.speechRate)
        } else {
            getString(R.string.speed_type_value, prefs.wordDelayMs)
        }
        btnVoice?.isSelected = prefs.voiceEnabled
    }

    // ------------------------------------------------------------------ notes panel

    private fun refreshNotes() {
        val notes = store.all()
        notesAdapter.submit(notes)
        lvNotes?.visibility = if (notes.isEmpty()) View.INVISIBLE else View.VISIBLE
        tvEmptyHint?.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        btnNotes?.text = getString(R.string.tb_notes_count, notes.size)
        refreshIdleStatus()
    }

    private fun openNotesPanel(open: Boolean) {
        notesOpen = open
        if (open) {
            stopPlayback()
            refreshNotes()
        }
        notesPanel?.visibility = if (open) View.VISIBLE else View.GONE
        keyboardArea?.visibility = if (open) View.GONE else View.VISIBLE
        suggestionsScroll?.visibility = if (open) View.GONE else View.VISIBLE
        btnNotes?.isSelected = open

        val frame = frameMain
        if (frame != null) {
            val params = frame.layoutParams
            if (params != null) {
                val wanted = if (open) {
                    val maxForScreen = (resources.displayMetrics.heightPixels * 0.55f).toInt()
                    minOf(
                        resources.getDimensionPixelSize(R.dimen.keyboard_area_expanded),
                        maxForScreen
                    )
                } else {
                    resources.getDimensionPixelSize(R.dimen.keyboard_area_height)
                }
                params.height = wanted
                frame.layoutParams = params
            }
            frame.requestLayout()
        }
        rootView?.requestLayout()
        if (!open) updateSuggestions()
    }

    override fun onPlay(note: Note) {
        startPlayback(note = note)
    }

    override fun onEdit(note: Note) {
        openNoteEditor(note.id)
    }

    override fun onDelete(note: Note) {
        store.delete(note.id)
        if (prefs.lastNoteId == note.id) prefs.lastNoteId = null
        refreshDictionary()
        refreshNotes()
        updateToolbar()
        toast(getString(R.string.note_deleted))
    }

    private fun openNoteEditor(noteId: String?) {
        stopPlayback()
        val intent = NoteEditActivity.intentFor(this, noteId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // ------------------------------------------------------------------ helpers

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun colorRes(id: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) resources.getColor(id, theme)
        else resources.getColor(id)

    private companion object {
        const val SUGGESTION_LIMIT = 20
        const val AUTO_SEND_DELAY_MS = 320L
        const val NEXT_NOTE_DELAY_MS = 1100L
    }
}
