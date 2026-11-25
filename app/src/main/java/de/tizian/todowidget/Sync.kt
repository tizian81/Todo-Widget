package de.tizian.todowidget

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/**
 * Offline-Warteschlange: Änderungen werden erst lokal gespeichert und sofort
 * im Widget angezeigt, dann der Reihe nach ans Apps Script geschickt.
 * Ohne Netz bleiben sie liegen und werden nachgeholt, sobald wieder Netz da ist.
 */
object Sync {
    private const val JOB_ID = 1001
    private val lock = Any()

    /** Neue Aktion merken, lokal anwenden und Nachholen einplanen. */
    fun enqueue(ctx: Context, action: String, text: String) {
        val op = Prefs.Op(action, text)
        Prefs.addPending(ctx, op)
        Prefs.setTasksLocal(ctx, apply(Prefs.tasks(ctx), op))
        schedule(ctx)
    }

    /**
     * Schickt alle ausstehenden Aktionen und speichert die aktuelle Liste.
     * Wirft [ApiException], wenn etwas nicht geklappt hat.
     * @param onlyIfPending true = nichts tun, wenn die Warteschlange leer ist
     */
    fun run(ctx: Context, onlyIfPending: Boolean = false): List<String> {
        synchronized(lock) { return runLocked(ctx, onlyIfPending) }
    }

    private fun runLocked(ctx: Context, onlyIfPending: Boolean): List<String> {
        if (onlyIfPending && Prefs.pending(ctx).isEmpty()) return Prefs.tasks(ctx)

        var latest: List<String>? = null
        var dropped: ApiException? = null
        while (true) {
            val op = Prefs.pending(ctx).firstOrNull() ?: break
            try {
                latest = Api.send(ctx, op.action, op.text)
                Prefs.removeFirstPending(ctx, op)
            } catch (e: ApiException) {
                if (e.retry) {
                    val n = Prefs.pending(ctx).size
                    throw ApiException("${e.message} – $n ausstehend")
                }
                // Dauerhafter Fehler: Aktion verwerfen, Rest weiter abarbeiten
                Prefs.removeFirstPending(ctx, op)
                dropped = e
            }
        }

        val list = latest ?: Api.list(ctx)
        // Was inzwischen neu hinzukam, lokal oben drauf anwenden
        val shown = Prefs.pending(ctx).fold(list) { acc, op -> apply(acc, op) }
        Prefs.saveTasks(ctx, shown)
        dropped?.let { throw it }
        return shown
    }

    private fun apply(list: List<String>, op: Prefs.Op): List<String> = when (op.action) {
        "add" -> list + op.text
        "complete" -> list - op.text
        "uncomplete" -> if (op.text in list) list else list + op.text
        else -> list
    }

    /** Lässt [SyncJobService] laufen, sobald eine Netzverbindung besteht. */
    private fun schedule(ctx: Context) {
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, SyncJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setPersisted(true)
            .build()
        ctx.getSystemService(JobScheduler::class.java)?.schedule(job)
    }
}
