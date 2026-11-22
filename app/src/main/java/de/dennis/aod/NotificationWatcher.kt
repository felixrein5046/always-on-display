package de.dennis.aod

import android.app.Notification
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Liest die aktiven Benachrichtigungen, damit die AOD-Anzeige deren App-Symbole zeigen kann.
 * Der Nutzer muss den Zugriff einmalig in den Einstellungen erlauben.
 */
class NotificationWatcher : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        notifyChanged()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = notifyChanged()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = notifyChanged()

    private fun notifyChanged() {
        listener?.invoke()
    }

    /** Ein Symbol pro App, ohne laufende/stille Hintergrund-Benachrichtigungen. */
    fun currentIcons(): List<Icon> {
        val list = runCatching { activeNotifications }.getOrNull() ?: return emptyList()
        val seen = HashSet<String>()
        val icons = ArrayList<Icon>()
        for (sbn in list) {
            if (sbn.packageName == packageName) continue
            val n = sbn.notification
            if (sbn.isOngoing && n.category != Notification.CATEGORY_CALL) continue
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            val ranking = Ranking()
            if (currentRanking?.getRanking(sbn.key, ranking) == true && ranking.isAmbient) continue
            if (!seen.add(sbn.packageName)) continue
            n.smallIcon?.let { icons += it }
        }
        return icons
    }

    companion object {
        @Volatile var instance: NotificationWatcher? = null
        @Volatile var listener: (() -> Unit)? = null
    }
}
