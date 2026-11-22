package de.dennis.aod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Startet den AOD-Dienst nach einem Neustart bzw. App-Update wieder. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs(context).enabled) AodService.start(context)
    }
}
