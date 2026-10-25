package com.fighter.studykeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.fighter.studykeyboard.R
import com.fighter.studykeyboard.data.Note

/**
 * List adapter used both by the keyboard panel (dark layout) and by the
 * settings screen (light layout).
 */
class NotesAdapter(
    private val itemLayout: Int,
    private val callback: Callback
) : BaseAdapter() {

    interface Callback {
        fun onPlay(note: Note)
        fun onEdit(note: Note)
        fun onDelete(note: Note)
    }

    private val items = ArrayList<Note>()

    fun submit(notes: List<Note>) {
        items.clear()
        items.addAll(notes)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Note = items[position]

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(parent.context).inflate(itemLayout, parent, false)
        val note = items[position]
        view.findViewById<TextView>(R.id.tvPreview).text = note.preview()
        view.findViewById<TextView>(R.id.tvMeta).text =
            parent.context.getString(R.string.note_meta, note.wordCount)
        view.findViewById<View>(R.id.btnPlayNote).setOnClickListener { callback.onPlay(note) }
        view.findViewById<View>(R.id.btnEditNote).setOnClickListener { callback.onEdit(note) }
        view.findViewById<View>(R.id.btnDeleteNote).setOnClickListener { callback.onDelete(note) }
        return view
    }
}
