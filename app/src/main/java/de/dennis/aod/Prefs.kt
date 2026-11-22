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
}
