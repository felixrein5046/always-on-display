package de.dennis.aod

import android.content.Context

/** Einfache Einstellungen der App. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("aod", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** Nur anzeigen, wenn das Handy lädt. */
    var onlyWhileCharging: Boolean
        get() = sp.getBoolean("only_charging", false)
        set(v) = sp.edit().putBoolean("only_charging", v).apply()

    /** Helligkeit 1..100 (%) */
    var brightness: Int
        get() = sp.getInt("brightness", 10)
        set(v) = sp.edit().putInt("brightness", v).apply()

    /** Automatisch ausschalten nach X Minuten (0 = nie). */
    var timeoutMinutes: Int
        get() = sp.getInt("timeout", 0)
        set(v) = sp.edit().putInt("timeout", v).apply()

    /** Nicht anzeigen, wenn der Akku unter diesem Wert liegt (und nicht lädt). */
    var minBattery: Int
        get() = sp.getInt("min_battery", 15)
        set(v) = sp.edit().putInt("min_battery", v).apply()

    var showNotifications: Boolean
        get() = sp.getBoolean("show_notifications", true)
        set(v) = sp.edit().putBoolean("show_notifications", v).apply()

    /** true = einmal tippen beendet das AOD, false = doppelt tippen. */
    var singleTapToExit: Boolean
        get() = sp.getBoolean("single_tap", true)
        set(v) = sp.edit().putBoolean("single_tap", v).apply()

    // --- Widgets ---
    var showAlarm: Boolean
        get() = sp.getBoolean("w_alarm", true)
        set(v) = sp.edit().putBoolean("w_alarm", v).apply()

    var showCalendar: Boolean
        get() = sp.getBoolean("w_calendar", false)
        set(v) = sp.edit().putBoolean("w_calendar", v).apply()

    var showMedia: Boolean
        get() = sp.getBoolean("w_media", true)
        set(v) = sp.edit().putBoolean("w_media", v).apply()

    /** ID eines eingebundenen Android-Widgets, -1 = keins. */
    var hostedWidgetId: Int
        get() = sp.getInt("w_host_id", -1)
        set(v) = sp.edit().putInt("w_host_id", v).apply()
}
