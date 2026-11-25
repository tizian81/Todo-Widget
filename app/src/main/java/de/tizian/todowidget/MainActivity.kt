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

        // Gab es einen Absturz? Bericht anzeigen (lange drücken → Kopieren)
        Prefs.crash(this)?.let {
            result.setTextIsSelectable(true)
            result.text = "Fehlerbericht vom letzten Absturz (lange drücken → alles markieren → Kopieren):\n\n$it"
        }

        save.setOnClickListener {
            Prefs.saveConfig(this, url.text.toString(), key.text.toString())
            Prefs.clearCrash(this)
            Sync.schedulePeriodic(this)
            save.isEnabled = false
            result.text = "Verbinde …"
            thread {
                val msg = try {
                    val tasks = Sync.run(this)
                    "✓ Verbunden – ${tasks.size} offene Aufgabe(n)"
                } catch (e: Throwable) {
                    "✗ ${App.describe(e)}"
                }
                runOnUiThread {
                    result.text = msg
                    save.isEnabled = true
                    TodoWidgetProvider.updateAllSafe(this)
                }
            }
        }
    }
}
