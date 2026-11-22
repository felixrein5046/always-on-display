package de.dennis.aod

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.media.AudioManager
import android.os.SystemClock

/**
 * Läuft im Hintergrund und startet die AOD-Anzeige, sobald der Bildschirm ausgeschaltet wird.
 */
class AodService : Service() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_USER_PRESENT -> {
                    suppressUntilUnlock = false
                    AodActivity.current?.finish()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs(this).enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onScreenOff() {
        val running = AodActivity.current
        val justClosed = SystemClock.elapsedRealtime() - AodActivity.lastVisibleAt < 2000
        if (running != null || justClosed) {
            // AOD war sichtbar und der Nutzer hat die Power-Taste gedrückt
            // -> Bildschirm wirklich aus lassen.
            running?.finish()
            return
        }
        if (!shouldShow()) return

        val i = Intent(this, AodActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
        startActivity(i)
    }

    private fun shouldShow(): Boolean {
        val prefs = Prefs(this)
        if (!prefs.enabled) return false
        if (!Settings.canDrawOverlays(this)) return false // nötig für Start aus dem Hintergrund

        if (suppressUntilUnlock) return false

        // Nicht während eines Telefonats (Näherungssensor schaltet Bildschirm aus)
        val mode = getSystemService(AudioManager::class.java).mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE) return false

        val bm = getSystemService(BatteryManager::class.java)
        val charging = bm.isCharging
        if (prefs.onlyWhileCharging && !charging) return false
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (!charging && level in 0 until prefs.minBattery) return false
        return true
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.service_running))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    companion object {
        private const val CHANNEL = "aod_service"

        /** Nach Ablauf der Anzeigedauer erst nach dem nächsten Entsperren wieder anzeigen. */
        @Volatile var suppressUntilUnlock = false

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AodService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AodService::class.java))
        }
    }
}
