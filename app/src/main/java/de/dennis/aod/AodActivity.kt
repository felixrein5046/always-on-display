package de.dennis.aod

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * Die eigentliche Always-On-Anzeige: schwarzer Bildschirm mit Uhr, Datum, Akku und
 * Benachrichtigungssymbolen. Wird über dem Sperrbildschirm angezeigt.
 */
class AodActivity : Activity() {

    private lateinit var content: View
    private lateinit var battery: TextView
    private lateinit var icons: LinearLayout
    private lateinit var gestures: GestureDetector
    private lateinit var prefs: Prefs

    private val handler = Handler(Looper.getMainLooper())
    private var proximityLock: PowerManager.WakeLock? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0) level * 100 / scale else 0
            battery.text = if (charging) "⚡ $pct %" else "$pct %"
        }
    }

    /** Verschiebt den Inhalt jede Minute leicht, um Einbrennen (Burn-in) zu vermeiden. */
    private val shiftTask = object : Runnable {
        override fun run() {
            val maxX = (resources.displayMetrics.widthPixels * 0.12f)
            val maxY = (resources.displayMetrics.heightPixels * 0.15f)
            content.animate()
                .translationX(Random.nextFloat() * 2 * maxX - maxX)
                .translationY(Random.nextFloat() * 2 * maxY - maxY)
                .setDuration(1500)
                .start()
            handler.postDelayed(this, 60_000)
        }
    }

    private val timeoutTask = Runnable {
        AodService.suppressUntilUnlock = true
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        prefs = Prefs(this)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = prefs.brightness.coerceIn(1, 100) / 100f
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_aod)
        content = findViewById(R.id.content)
        battery = findViewById(R.id.battery)
        icons = findViewById(R.id.icons)
        hideSystemBars()

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                unlock()
                return true
            }
        })
        findViewById<View>(R.id.root).setOnTouchListener { _, ev -> gestures.onTouchEvent(ev) }

        // Bildschirm aus, wenn das Handy in der Tasche/umgedreht liegt (Näherungssensor).
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "aod:proximity")
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        NotificationWatcher.listener = { runOnUiThread { refreshIcons() } }
        refreshIcons()
        handler.post(shiftTask)
        if (prefs.timeoutMinutes > 0) handler.postDelayed(timeoutTask, prefs.timeoutMinutes * 60_000L)
        proximityLock?.takeIf { !it.isHeld }?.acquire(12 * 60 * 60 * 1000L)
    }

    override fun onPause() {
        super.onPause()
        lastVisibleAt = SystemClock.elapsedRealtime()
        runCatching { unregisterReceiver(batteryReceiver) }
        NotificationWatcher.listener = null
        handler.removeCallbacksAndMessages(null)
        proximityLock?.takeIf { it.isHeld }?.release()
    }

    override fun onStop() {
        super.onStop()
        // Sobald etwas anderes im Vordergrund ist (Entsperren, Anruf …) AOD schließen.
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        lastVisibleAt = SystemClock.elapsedRealtime()
        if (current === this) current = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Zurück-Taste ignorieren
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, null)
        finish()
    }

    private fun refreshIcons() {
        icons.removeAllViews()
        if (!prefs.showNotifications) return
        val list = NotificationWatcher.instance?.currentIcons() ?: return
        val size = (resources.displayMetrics.density * 22).toInt()
        val margin = (resources.displayMetrics.density * 6).toInt()
        for (icon in list.take(8)) {
            val iv = ImageView(this)
            iv.setImageIcon(icon)
            iv.imageTintList = ColorStateList.valueOf(Color.parseColor("#BBBBBB"))
            iv.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = margin; marginEnd = margin
            }
            icons.addView(iv)
        }
    }

    private fun hideSystemBars() {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        @Volatile var current: AodActivity? = null
        @Volatile var lastVisibleAt: Long = 0
    }
}
