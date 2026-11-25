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

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, buildViews(ctx, it)) }
        runAsync(ctx) { Sync.run(ctx) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_REFRESH -> runAsync(ctx) { Sync.run(ctx) }

            ACTION_CHECK -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return
                // Sofort aus dem Widget entfernen, dann im Doc abhaken
                Prefs.pushDone(ctx, text)
                Sync.enqueue(ctx, "complete", text)
                updateAll(ctx)
                runAsync(ctx) { Sync.run(ctx) }
            }

            ACTION_UNDO -> {
                val text = Prefs.popDone(ctx) ?: return
                // Sofort wieder anzeigen, dann im Doc zurück nach „Offen“
                Sync.enqueue(ctx, "uncomplete", text)
                updateAll(ctx)
                runAsync(ctx) { Sync.run(ctx) }
            }
        }
    }

    private fun runAsync(ctx: Context, block: () -> Unit) {
        if (!Prefs.isConfigured(ctx)) return
        val pending = goAsync()
        thread {
            try {
                block()
            } catch (e: Exception) {
                Prefs.setError(ctx, e.message ?: "Fehler")
            } finally {
                updateAll(ctx)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "de.tizian.todowidget.REFRESH"
        const val ACTION_CHECK = "de.tizian.todowidget.CHECK"
        const val ACTION_UNDO = "de.tizian.todowidget.UNDO"
        const val EXTRA_TEXT = "text"

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { mgr.updateAppWidget(it, buildViews(ctx, it)) }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.list)
        }

        private fun statusText(ctx: Context): String {
            if (!Prefs.isConfigured(ctx)) return ""
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

            // Antippen einer Aufgabe = abhaken
            val checkTemplate = PendingIntent.getBroadcast(
                ctx, 1,
                Intent(ctx, TodoWidgetProvider::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.list, checkTemplate)

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
