package de.dennis.aod

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Die eigentliche Always-On-Anzeige: schwarzer Bildschirm mit Uhr, Datum, Akku,
 * Benachrichtigungssymbolen und Widgets. Wird über dem Sperrbildschirm angezeigt.
 */
class AodActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var content: View
    private lateinit var battery: TextView
    private lateinit var icons: LinearLayout
    private lateinit var alarm: TextView
    private lateinit var calendar: TextView
    private lateinit var media: View
    private lateinit var mediaTitle: TextView
    private lateinit var mediaArtist: TextView
    private lateinit var mediaPlay: ImageButton
    private lateinit var hostedWidget: FrameLayout
    private lateinit var gestures: GestureDetector

    private val handler = Handler(Looper.getMainLooper())
    private var proximityLock: PowerManager.WakeLock? = null
    private var widgetHost: AppWidgetHost? = null

    private var mediaController: MediaController? = null
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = updateMedia()
        override fun onSessionDestroyed() = pickMediaSession(activeSessions())
    }
    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { pickMediaSession(it) }

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

    /** Jede Minute: Inhalt leicht verschieben (gegen Einbrennen) und Widgets aktualisieren. */
    private val minuteTask = object : Runnable {
        override fun run() {
            val maxX = resources.displayMetrics.widthPixels * 0.06f
            val maxY = resources.displayMetrics.heightPixels * 0.08f
            content.animate()
                .translationX(Random.nextFloat() * 2 * maxX - maxX)
                .translationY(Random.nextFloat() * 2 * maxY - maxY)
                .setDuration(1500)
                .start()
            refreshAlarm()
            refreshCalendar()
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
        alarm = findViewById(R.id.alarm)
        calendar = findViewById(R.id.calendar)
        media = findViewById(R.id.media)
        mediaTitle = findViewById(R.id.mediaTitle)
        mediaArtist = findViewById(R.id.mediaArtist)
        mediaPlay = findViewById(R.id.mediaPlay)
        hostedWidget = findViewById(R.id.hostedWidget)
        hideSystemBars()

        findViewById<TextView>(R.id.hint).setText(
            if (prefs.singleTapToExit) R.string.tap_hint else R.string.double_tap_hint
        )

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!prefs.singleTapToExit) return false
                unlock()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                unlock()
                return true
            }
        })
        findViewById<View>(R.id.root).setOnTouchListener { _, ev -> gestures.onTouchEvent(ev) }

        setupMediaButtons()
        setupHostedWidget()

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
        startMedia()
        runCatching { widgetHost?.startListening() }
        handler.post(minuteTask)
        if (prefs.timeoutMinutes > 0) handler.postDelayed(timeoutTask, prefs.timeoutMinutes * 60_000L)
        proximityLock?.takeIf { !it.isHeld }?.acquire(12 * 60 * 60 * 1000L)
    }

    override fun onPause() {
        super.onPause()
        lastVisibleAt = SystemClock.elapsedRealtime()
        runCatching { unregisterReceiver(batteryReceiver) }
        NotificationWatcher.listener = null
        stopMedia()
        runCatching { widgetHost?.stopListening() }
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

    // ---------------- Benachrichtigungen ----------------

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

    // ---------------- Widget: Wecker ----------------

    private fun refreshAlarm() {
        val next = getSystemService(AlarmManager::class.java).nextAlarmClock
        if (!prefs.showAlarm || next == null) {
            alarm.visibility = View.GONE
            return
        }
        val pattern = if (DateFormat.is24HourFormat(this)) "EEE HH:mm" else "EEE h:mm a"
        alarm.text = "⏰  " + SimpleDateFormat(pattern, Locale.getDefault()).format(Date(next.triggerTime))
        alarm.visibility = View.VISIBLE
    }

    // ---------------- Widget: Kalender ----------------

    private fun refreshCalendar() {
        if (!prefs.showCalendar ||
            checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            calendar.visibility = View.GONE
            return
        }
        val now = System.currentTimeMillis()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, now + 24 * 60 * 60 * 1000L)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        val timeFmt = SimpleDateFormat(
            if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a", Locale.getDefault()
        )
        var text: String? = null
        runCatching {
            contentResolver.query(
                builder.build(), projection, null, null, "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(0)
                    if (title.isNullOrBlank()) continue
                    val begin = c.getLong(1)
                    val end = c.getLong(2)
                    val allDay = c.getInt(3) == 1
                    if (end <= now) continue
                    text = if (allDay) "📅  $title" else "📅  ${timeFmt.format(Date(begin))}  $title"
                    break
                }
            }
        }
        calendar.text = text ?: ""
        calendar.visibility = if (text != null) View.VISIBLE else View.GONE
    }

    // ---------------- Widget: Musik ----------------

    private fun setupMediaButtons() {
        findViewById<ImageButton>(R.id.mediaPrev).setOnClickListener {
            mediaController?.transportControls?.skipToPrevious()
        }
        findViewById<ImageButton>(R.id.mediaNext).setOnClickListener {
            mediaController?.transportControls?.skipToNext()
        }
        mediaPlay.setOnClickListener {
            val c = mediaController ?: return@setOnClickListener
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        }
        val tint = ColorStateList.valueOf(Color.parseColor("#AAAAAA"))
        for (id in intArrayOf(R.id.mediaPrev, R.id.mediaPlay, R.id.mediaNext)) {
            findViewById<ImageButton>(id).imageTintList = tint
        }
    }

    private fun listenerComponent() = ComponentName(this, NotificationWatcher::class.java)

    private fun activeSessions(): List<MediaController>? = runCatching {
        getSystemService(MediaSessionManager::class.java).getActiveSessions(listenerComponent())
    }.getOrNull()

    private fun startMedia() {
        if (!prefs.showMedia) {
            media.visibility = View.GONE
            return
        }
        // Funktioniert nur mit erlaubtem Benachrichtigungszugriff, sonst SecurityException.
        runCatching {
            getSystemService(MediaSessionManager::class.java)
                .addOnActiveSessionsChangedListener(sessionsListener, listenerComponent(), handler)
        }
        pickMediaSession(activeSessions())
    }

    private fun stopMedia() {
        runCatching {
            getSystemService(MediaSessionManager::class.java)
                .removeOnActiveSessionsChangedListener(sessionsListener)
        }
        mediaController?.unregisterCallback(mediaCallback)
        mediaController = null
    }

    private fun pickMediaSession(list: List<MediaController>?) {
        mediaController?.unregisterCallback(mediaCallback)
        mediaController = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        mediaController?.registerCallback(mediaCallback, handler)
        updateMedia()
    }

    private fun updateMedia() {
        val c = mediaController
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (!prefs.showMedia || c == null || md == null || title.isNullOrBlank()) {
            media.visibility = View.GONE
            return
        }
        mediaTitle.text = title
        mediaArtist.text = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        mediaPlay.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        media.visibility = View.VISIBLE
    }

    // ---------------- Beliebiges Android-Widget ----------------

    private fun setupHostedWidget() {
        hostedWidget.removeAllViews()
        val id = prefs.hostedWidgetId
        val info = if (id != -1) WidgetHost.manager(this).getAppWidgetInfo(id) else null
        if (info == null) {
            hostedWidget.visibility = View.GONE
            return
        }
        val host = WidgetHost.host(this)
        widgetHost = host
        val view = host.createView(this, id, info)
        val minHeight = (120 * resources.displayMetrics.density).toInt()
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, maxOf(info.minHeight, minHeight)
        )
        hostedWidget.addView(view)
        hostedWidget.visibility = View.VISIBLE
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
