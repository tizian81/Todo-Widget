package de.tizian.todowidget

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * @param retry true = vorübergehendes Problem (Netz, Server, Einstellungen),
 *              die Aktion bleibt in der Warteschlange und wird später wiederholt.
 */
class ApiException(message: String, val retry: Boolean = true) : Exception(message)

/**
 * Spricht mit dem Google Apps Script. Jede Antwort enthält die aktuelle
 * Liste der offenen Aufgaben. Gespeichert wird sie von [Sync].
 */
object Api {

    fun list(ctx: Context): List<String> = call(ctx, mapOf("action" to "list"))

    /** action = add | complete | uncomplete */
    fun send(ctx: Context, action: String, text: String): List<String> =
        call(ctx, mapOf("action" to action, "text" to text))

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun call(ctx: Context, params: Map<String, String>): List<String> {
        val base = Prefs.url(ctx)
        val key = Prefs.key(ctx)
        if (base.isBlank() || key.isBlank()) throw ApiException("Nicht eingerichtet")

        val query = (params + ("key" to key)).entries
            .joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val sep = if (base.contains("?")) "&" else "?"

        val conn = try {
            URL(base + sep + query).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ApiException("Ungültige Script-URL")
        }

        try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException("Serverfehler (HTTP $code)")

            val json = try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw ApiException("Keine gültige Antwort – Freigabe „Jeder“ prüfen")
            }
            if (!json.optBoolean("ok", false)) {
                val err = json.optString("error", "Unbekannter Fehler")
                if (err == "unauthorized") throw ApiException("Falscher Schlüssel")
                // Fachlicher Fehler (z. B. leere Aufgabe): Wiederholen bringt nichts
                throw ApiException(err, retry = false)
            }

            val arr = json.getJSONArray("open")
            return (0 until arr.length()).map { arr.getString(it) }
        } catch (e: IOException) {
            throw ApiException("Keine Verbindung")
        } finally {
            conn.disconnect()
        }
    }
}
