package de.tizian.todowidget

import android.app.job.JobParameters
import android.app.job.JobService
import kotlin.concurrent.thread

/** Holt ausstehende Aktionen nach, sobald wieder Netz da ist. */
class SyncJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            var retry = false
            try {
                Sync.run(applicationContext, onlyIfPending = true)
            } catch (e: ApiException) {
                Prefs.setError(applicationContext, e.message ?: "Fehler")
                retry = e.retry && Prefs.pending(applicationContext).isNotEmpty()
            } catch (e: Exception) {
                Prefs.setError(applicationContext, e.message ?: "Fehler")
                retry = Prefs.pending(applicationContext).isNotEmpty()
            } finally {
                TodoWidgetProvider.updateAll(applicationContext)
                jobFinished(params, retry)
            }
        }
        return true
    }

    // Abgebrochen (z. B. Netz weg): später erneut versuchen
    override fun onStopJob(params: JobParameters): Boolean =
        Prefs.pending(applicationContext).isNotEmpty()
}
