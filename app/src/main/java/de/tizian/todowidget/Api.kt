package de.tizian.todowidget

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * @param retry true = vorübergehendes Problem (Netz, Server, Einstellungen, veraltetes Script),
 *              die Aktion bleibt in der Warteschlange und wird später wiederholt.
 */
class ApiException(message: String, val retry: Boolean = true) : Exception(message)

/**
 * Spricht mit dem Google Apps Script. Jede Antwort enthält die aktuelle
 * Liste der offenen Aufgaben. Gespeichert wird sie von [Sync].
 *
 * Gesendet wird per POST, damit der Schlüssel nicht in der URL (und damit
 * nicht in Server-Logs) steht.
 */
object Api {

    /** Mindestversion des Apps Scripts, die diese App braucht (SCRIPT_VERSION in Code.gs). */
    const val REQUIRED_SCRIPT_VERSION = 2

    fun list(ctx: Context): List<String> = call(ctx, mapOf("action" to "list"))

    /** op.action = add | complete | uncomplete | edit */
    fun send(ctx: Context, op: Prefs.Op): List<String> {
        val params = mutableMapOf("action" to op.action, "text" to op.text, "n" to op.n.toString())
        if (op.action == "edit") params["new"] = op.newText
        if (op.id.isNotEmpty()) params["id"] = op.id
        return call(ctx, params)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun call(ctx: Context, params: Map<String, String>): List<String> {
        val base = Prefs.url(ctx)
        val key = Prefs.key(ctx)
        if (base.isBlank() || key.isBlank()) throw ApiException("Nicht eingerichtet")

        val form = (params + ("key" to key)).entries
            .joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

        val body = try {
            request(base, form)
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("Keine Verbindung")
        } catch (e: Exception) {
            throw ApiException("Ungültige Script-URL")
        }

        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw ApiException("Keine gültige Antwort – Freigabe „Jeder“ prüfen")
        }

        if (json.optString("error") == "unauthorized") throw ApiException("Falscher Schlüssel")
        // Veraltetes Script: Aktionen behalten, bis es aktualisiert ist
        if (json.optInt("version", 0) < REQUIRED_SCRIPT_VERSION) {
            throw ApiException("Apps Script aktualisieren")
        }
        if (!json.optBoolean("ok", false)) {
            // Fachlicher Fehler (z. B. leere Aufgabe): Wiederholen bringt nichts
            throw ApiException(json.optString("error", "Unbekannter Fehler"), retry = false)
        }

        val arr = json.getJSONArray("open")
        return (0 until arr.length()).map { arr.getString(it) }
    }

    /**
     * POST an das Script. Apps Script antwortet mit einer Weiterleitung auf die
     * eigentliche Antwort, die per GET abgeholt wird.
     */
    private fun request(url: String, form: String): String {
        var target = URL(url)
        var post = true
        repeat(5) {
            val conn = target.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (post) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                }

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw ApiException("Serverfehler (Weiterleitung ohne Ziel)")
                    target = URL(target, location)
                    post = false
                    return@repeat
                }
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw ApiException("Serverfehler (HTTP $code)")
                return text
            } finally {
                conn.disconnect()
            }
        }
        throw ApiException("Serverfehler (zu viele Weiterleitungen)")
    }
}
