package de.tizian.todowidget

import android.app.Application
import android.util.Log

/**
 * Merkt sich einen Absturz, damit die App ihn beim nächsten Öffnen anzeigen kann.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                Prefs.setCrash(this, Log.getStackTraceString(e))
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        /** Kurze, lesbare Fehlermeldung, auch für unerwartete Fehler. */
        fun describe(e: Throwable): String =
            if (e is ApiException) e.message ?: "Fehler"
            else "${e.javaClass.simpleName}: ${e.message ?: ""}".trimEnd(':', ' ')
    }
}
