package de.tizian.todowidget

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlin.concurrent.thread

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val url = findViewById<EditText>(R.id.url)
        val key = findViewById<EditText>(R.id.key)
        val save = findViewById<Button>(R.id.btn_save)
        val result = findViewById<TextView>(R.id.result)

        url.setText(Prefs.url(this))
        key.setText(Prefs.key(this))

        save.setOnClickListener {
            Prefs.saveConfig(this, url.text.toString(), key.text.toString())
            Sync.schedulePeriodic(this)
            save.isEnabled = false
            result.text = "Verbinde …"
            thread {
                val msg = try {
                    val tasks = Sync.run(this)
                    "✓ Verbunden – ${tasks.size} offene Aufgabe(n)"
                } catch (e: Exception) {
                    "✗ ${e.message}"
                }
                runOnUiThread {
                    result.text = msg
                    save.isEnabled = true
                    TodoWidgetProvider.updateAll(this)
                }
            }
        }
    }
}
