package de.tizian.todowidget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val FILE = "todo_widget"
    private const val URL = "script_url"
    private const val KEY = "secret_key"
    private const val TASKS = "tasks"
    private const val UPDATED = "updated_at"
    private const val ERROR = "last_error"
    private const val DONE = "recently_done"
    private const val DONE_MAX = 10
    private const val PENDING = "pending_ops"
    private const val EDIT_MODE = "edit_mode"

    /**
     * Eine Aktion für das Script.
     * @param n       welches Vorkommen bei gleichlautenden Aufgaben (0 = erstes)
     * @param newText neuer Text (nur bei „edit“)
     */
    data class Op(val action: String, val text: String, val n: Int = 0, val newText: String = "")

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

    /** Zuletzt abgehakte Aufgaben (neueste am Ende) für „Rückgängig“. */
    fun recentlyDone(ctx: Context): List<String> {
        val raw = p(ctx).getString(DONE, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun pushDone(ctx: Context, text: String) {
        val list = (recentlyDone(ctx) + text).takeLast(DONE_MAX)
        p(ctx).edit().putString(DONE, JSONArray(list).toString()).commit()
    }

    /** Nimmt die zuletzt abgehakte Aufgabe vom Stapel (null, wenn leer). */
    fun popDone(ctx: Context): String? {
        val list = recentlyDone(ctx)
        if (list.isEmpty()) return null
        p(ctx).edit().putString(DONE, JSONArray(list.dropLast(1)).toString()).commit()
        return list.last()
    }

    /** Noch nicht ans Script geschickte Aktionen, älteste zuerst. */
    @Synchronized
    fun pending(ctx: Context): List<Op> {
        val raw = p(ctx).getString(PENDING, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Op(o.getString("a"), o.getString("t"), o.optInt("n", 0), o.optString("x", ""))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePending(ctx: Context, ops: List<Op>) {
        val arr = JSONArray()
        ops.forEach {
            arr.put(JSONObject().put("a", it.action).put("t", it.text).put("n", it.n).put("x", it.newText))
        }
        p(ctx).edit().putString(PENDING, arr.toString()).commit()
    }

    @Synchronized
    fun addPending(ctx: Context, op: Op) = savePending(ctx, pending(ctx) + op)

    /** Entfernt die erste Aktion, sofern sie noch [op] ist. */
    @Synchronized
    fun removeFirstPending(ctx: Context, op: Op) {
        val ops = pending(ctx)
        if (ops.firstOrNull() == op) savePending(ctx, ops.drop(1))
    }

    /** Bearbeiten-Modus im Widget: Antippen öffnet den Editor statt abzuhaken. */
    fun editMode(ctx: Context): Boolean = p(ctx).getBoolean(EDIT_MODE, false)
    fun setEditMode(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean(EDIT_MODE, on).commit()
    }

    fun updatedAt(ctx: Context): Long = p(ctx).getLong(UPDATED, 0L)
    fun error(ctx: Context): String? = p(ctx).getString(ERROR, null)
    fun setError(ctx: Context, msg: String) {
        p(ctx).edit().putString(ERROR, msg).commit()
    }
}
