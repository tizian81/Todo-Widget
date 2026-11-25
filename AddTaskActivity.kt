package de.tizian.todowidget

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlin.concurrent.thread

class AddTaskActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)
        setFinishOnTouchOutside(true)

        val input = findViewById<EditText>(R.id.input)
        val ok = findViewById<Button>(R.id.btn_ok)
        val error = findViewById<TextView>(R.id.error)

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }

        fun submit() {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return
            if (!Prefs.isConfigured(this)) {
                error.text = getString(R.string.not_configured)
                error.visibility = View.VISIBLE
                return
            }
            ok.isEnabled = false
            input.isEnabled = false
            error.visibility = View.GONE
            thread {
                try {
                    Api.add(this, text)
                    runOnUiThread {
                        TodoWidgetProvider.updateAll(this)
                        finish()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        error.text = e.message ?: "Fehler"
                        error.visibility = View.VISIBLE
                        ok.isEnabled = true
                        input.isEnabled = true
                    }
                }
            }
        }

        ok.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        input.requestFocus()
    }
}
