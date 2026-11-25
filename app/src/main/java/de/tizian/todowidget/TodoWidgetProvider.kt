package de.tizian.todowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class TodoWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(ctx: Context) {
        Sync.schedulePeriodic(ctx)
    }

    override fun onDisabled(ctx: Context) {
        Sync.cancelPeriodic(ctx)
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, buildViews(ctx, it)) }
        Sync.schedulePeriodic(ctx)
        runAsync(ctx) { Sync.run(ctx) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_REFRESH -> runAsync(ctx) { Sync.run(ctx) }

            ACTION_CHECK -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return
                val n = intent.getIntExtra(EXTRA_INDEX, 0)
                // Sofort aus dem Widget entfernen, dann im Doc abhaken
                Prefs.pushDone(ctx, text)
                Sync.enqueue(ctx, Prefs.Op("complete", text, n))
                updateAll(ctx)
                runAsync(ctx) { Sync.run(ctx) }
            }

            ACTION_UNDO -> {
                val text = Prefs.popDone(ctx) ?: return
                // Sofort wieder anzeigen, dann im Doc zurück nach „Offen“
                Sync.enqueue(ctx, Prefs.Op("uncomplete", text))
                updateAll(ctx)
                runAsync(ctx) { Sync.run(ctx) }
            }

            ACTION_EDIT_MODE -> {
                Prefs.setEditMode(ctx, !Prefs.editMode(ctx))
                updateAll(ctx)
            }
        }
    }

    private fun runAsync(ctx: Context, block: () -> Unit) {
        if (!Prefs.isConfigured(ctx)) return
        val pending = goAsync()
        thread {
            try {
                block()
            } catch (e: Throwable) {
                Prefs.setError(ctx, App.describe(e))
            } finally {
                updateAllSafe(ctx)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "de.tizian.todowidget.REFRESH"
        const val ACTION_CHECK = "de.tizian.todowidget.CHECK"
        const val ACTION_UNDO = "de.tizian.todowidget.UNDO"
        const val ACTION_EDIT_MODE = "de.tizian.todowidget.EDIT_MODE"
        const val EXTRA_TEXT = "text"
        const val EXTRA_INDEX = "index"

        /** Wie [updateAll], aber ein Fehler beim Zeichnen beendet nie die App. */
        fun updateAllSafe(ctx: Context) {
            try {
                updateAll(ctx)
            } catch (ignored: Throwable) {
            }
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { mgr.updateAppWidget(it, buildViews(ctx, it)) }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.list)
        }

        private fun statusText(ctx: Context): String {
            if (!Prefs.isConfigured(ctx)) return ""
            if (Prefs.editMode(ctx)) return ctx.getString(R.string.edit_hint)
            Prefs.error(ctx)?.let { return "⚠ $it" }
            val n = Prefs.pending(ctx).size
            if (n > 0) return "$n ausstehend"
            val t = Prefs.updatedAt(ctx)
            if (t == 0L) return ""
            return SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(t))
        }

        @Suppress("DEPRECATION")
        private fun buildViews(ctx: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_todo)

            // Liste
            val svc = Intent(ctx, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.list, svc)
            views.setEmptyView(R.id.list, R.id.empty)
            views.setTextViewText(
                R.id.empty,
                ctx.getString(if (Prefs.isConfigured(ctx)) R.string.empty_list else R.string.not_configured)
            )

            // Antippen einer Aufgabe = abhaken, im Bearbeiten-Modus = Editor öffnen
            val editMode = Prefs.editMode(ctx)
            val itemTemplate = if (editMode) {
                PendingIntent.getActivity(
                    ctx, 6,
                    Intent(ctx, AddTaskActivity::class.java)
                        .putExtra(AddTaskActivity.EXTRA_EDIT, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    ctx, 1,
                    Intent(ctx, TodoWidgetProvider::class.java).setAction(ACTION_CHECK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            }
            views.setPendingIntentTemplate(R.id.list, itemTemplate)

            // Bearbeiten-Modus umschalten
            val editToggle = PendingIntent.getBroadcast(
                ctx, 7,
                Intent(ctx, TodoWidgetProvider::class.java).setAction(ACTION_EDIT_MODE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_edit, editToggle)
            views.setImageViewResource(
                R.id.btn_edit,
                if (editMode) R.drawable.ic_edit_active else R.drawable.ic_edit
            )

            // Aktualisieren
            val refresh = PendingIntent.getBroadcast(
                ctx, 2,
                Intent(ctx, TodoWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refresh)

            // Rückgängig (nur sichtbar, wenn etwas abgehakt wurde)
            val undo = PendingIntent.getBroadcast(
                ctx, 5,
                Intent(ctx, TodoWidgetProvider::class.java).setAction(ACTION_UNDO),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_undo, undo)
            views.setViewVisibility(
                R.id.btn_undo,
                if (Prefs.recentlyDone(ctx).isEmpty()) View.GONE else View.VISIBLE
            )

            // Hinzufügen
            val add = PendingIntent.getActivity(
                ctx, 3,
                Intent(ctx, AddTaskActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_add, add)

            // Titel öffnet die Einstellungen
            val open = PendingIntent.getActivity(
                ctx, 4,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.title, open)

            views.setTextViewText(R.id.status, statusText(ctx))
            return views
        }
    }
}
