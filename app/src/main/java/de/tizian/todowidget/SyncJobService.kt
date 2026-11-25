package de.tizian.todowidget

import android.app.job.JobParameters
import android.app.job.JobService
import kotlin.concurrent.thread

/** Holt ausstehende Aktionen nach, sobald wieder Netz da ist, und aktualisiert regelmäßig. */
class SyncJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        if (params.jobId == Sync.JOB_PERIODIC) {
            if (!Prefs.isConfigured(applicationContext)) return false
            thread {
                try {
                    Sync.run(applicationContext)
                } catch (e: Exception) {
                    Prefs.setError(applicationContext, e.message ?: "Fehler")
                } finally {
                    TodoWidgetProvider.updateAll(applicationContext)
                    jobFinished(params, false)  // läuft ohnehin periodisch weiter
                }
            }
            return true
        }
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
        params.jobId != Sync.JOB_PERIODIC && Prefs.pending(applicationContext).isNotEmpty()
}
