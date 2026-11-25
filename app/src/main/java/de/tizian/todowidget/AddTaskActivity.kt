package de.tizian.todowidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class AddTaskActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)
        setFinishOnTouchOutside(true)

        val input = findViewById<EditText>(R.id.input)
        val ok = findViewById<Button>(R.id.btn_ok)
        val error = findViewById<TextView>(R.id.error)

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }

        // Über „Teilen“ aus einer anderen App aufgerufen: Text vorbelegen
        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!shared.isNullOrBlank()) {
                val oneLine = shared.trim().replace(Regex("\\s+"), " ")
                input.setText(oneLine)
                input.setSelection(oneLine.length)
            }
        }

        fun submit() {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return
            if (!Prefs.isConfigured(this)) {
                error.text = getString(R.string.not_configured)
                error.visibility = View.VISIBLE
                return
            }
            // Sofort lokal übernehmen; gesendet wird im Hintergrund (auch offline)
            Sync.enqueue(this, "add", text)
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
