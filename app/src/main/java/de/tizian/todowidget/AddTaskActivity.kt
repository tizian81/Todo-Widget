package de.tizian.todowidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Dialog zum Hinzufügen (auch über „Teilen“) und Bearbeiten einer Aufgabe. */
class AddTaskActivity : Activity() {

    companion object {
        const val EXTRA_EDIT = "edit"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)
        setFinishOnTouchOutside(true)

        val input = findViewById<EditText>(R.id.input)
        val ok = findViewById<Button>(R.id.btn_ok)
        val error = findViewById<TextView>(R.id.error)

        // Bearbeiten: Originaltext und Vorkommen aus dem Widget
        val editing = intent?.getBooleanExtra(EXTRA_EDIT, false) == true
        val original = intent?.getStringExtra(TodoWidgetProvider.EXTRA_TEXT) ?: ""
        val index = intent?.getIntExtra(TodoWidgetProvider.EXTRA_INDEX, 0) ?: 0
        if (editing) {
            // Bearbeiten-Modus gilt nur für einen Eintrag
            Prefs.setEditMode(this, false)
            TodoWidgetProvider.updateAll(this)
            if (original.isEmpty()) { finish(); return }
            findViewById<TextView>(R.id.dialog_title).setText(R.string.edit_title)
            ok.setText(R.string.save)
            input.setText(original)
            input.setSelection(original.length)
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }

        // Über „Teilen“ aus einer anderen App aufgerufen: Text vorbelegen
        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!shared.isNullOrBlank()) {
                val oneLine = TaskLogic.normalize(shared)
                input.setText(oneLine)
                input.setSelection(oneLine.length)
            }
        }

        var confirmedDuplicate: String? = null

        fun submit() {
            val text = TaskLogic.normalize(input.text.toString())
            if (text.isEmpty()) return
            if (editing && text == original) { finish(); return }
            if (!Prefs.isConfigured(this)) {
                error.text = getString(R.string.not_configured)
                error.visibility = View.VISIBLE
                return
            }

            // Doppelte Aufgabe? Erst warnen, beim zweiten Tippen trotzdem eintragen
            val duplicate = TaskLogic.isDuplicate(Prefs.tasks(this), text, if (editing) original else null)
            if (duplicate && confirmedDuplicate != text) {
                confirmedDuplicate = text
                error.text = getString(R.string.duplicate_warning)
                error.visibility = View.VISIBLE
                return
            }

            // Sofort lokal übernehmen; gesendet wird im Hintergrund (auch offline)
            val op = if (editing) Prefs.Op("edit", original, index, text) else Prefs.Op("add", text)
            Sync.enqueue(this, op)
            TodoWidgetProvider.updateAll(this)
            sendBroadcast(
                Intent(this, TodoWidgetProvider::class.java)
                    .setAction(TodoWidgetProvider.ACTION_REFRESH)
            )
            finish()
        }

        ok.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        input.requestFocus()
    }
}
