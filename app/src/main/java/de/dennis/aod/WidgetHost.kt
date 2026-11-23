package de.dennis.aod

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context

/** Gemeinsamer Zugriff auf den Widget-Host (für echte Android-Widgets auf dem AOD). */
object WidgetHost {
    private const val HOST_ID = 4711

    fun host(context: Context) = AppWidgetHost(context.applicationContext, HOST_ID)

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

    /** Entfernt das aktuell eingebundene Widget (falls vorhanden). */
    fun remove(context: Context) {
        val prefs = Prefs(context)
        val id = prefs.hostedWidgetId
        if (id != -1) {
            runCatching { host(context).deleteAppWidgetId(id) }
            prefs.hostedWidgetId = -1
        }
    }
}
