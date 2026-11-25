package de.tizian.todowidget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object Prefs {
    private const val FILE = "todo_widget"
    private const val URL = "script_url"
    private const val KEY = "secret_key"
    private const val TASKS = "tasks"
    private const val UPDATED = "updated_at"
    private const val ERROR = "last_error"

    private fun p(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun url(ctx: Context): String = p(ctx).getString(URL, "") ?: ""
    fun key(ctx: Context): String = p(ctx).getString(KEY, "") ?: ""
    fun isConfigured(ctx: Context) = url(ctx).isNotBlank() && key(ctx).isNotBlank()

    fun saveConfig(ctx: Context, url: String, key: String) {
        p(ctx).edit().putString(URL, url.trim()).putString(KEY, key.trim()).apply()
    }

    fun tasks(ctx: Context): List<String> {
        val raw = p(ctx).getString(TASKS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Speichert eine frisch vom Server geladene Liste. */
    fun saveTasks(ctx: Context, tasks: List<String>) {
        p(ctx).edit()
            .putString(TASKS, JSONArray(tasks).toString())
            .putLong(UPDATED, System.currentTimeMillis())
            .remove(ERROR)
            .commit()
    }

    /** Lokale Änderung (z. B. sofort abgehakt), ohne Zeitstempel zu ändern. */
    fun setTasksLocal(ctx: Context, tasks: List<String>) {
        p(ctx).edit().putString(TASKS, JSONArray(tasks).toString()).commit()
    }

    fun updatedAt(ctx: Context): Long = p(ctx).getLong(UPDATED, 0L)
    fun error(ctx: Context): String? = p(ctx).getString(ERROR, null)
    fun setError(ctx: Context, msg: String) {
        p(ctx).edit().putString(ERROR, msg).commit()
    }
}
