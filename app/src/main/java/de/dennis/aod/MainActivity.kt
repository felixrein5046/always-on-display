package de.dennis.aod

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var btnOverlay: Button
    private lateinit var btnNotif: Button
    private lateinit var btnBattery: Button
    private lateinit var btnWidget: Button
    private lateinit var switchCalendar: MaterialSwitch
    private var pendingWidgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        switchEnabled = findViewById(R.id.switchEnabled)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnNotif = findViewById(R.id.btnNotifAccess)
        btnBattery = findViewById(R.id.btnBattery)

        switchEnabled.setOnCheckedChangeListener { sw, checked ->
            if (checked && !Settings.canDrawOverlays(this)) {
                sw.isChecked = false
                Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
                openOverlaySettings()
                return@setOnCheckedChangeListener
            }
            prefs.enabled = checked
            if (checked) AodService.start(this) else AodService.stop(this)
        }

        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnNotif.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btnBattery.setOnClickListener {
            @Suppress("BatteryLife")
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }

        bindSwitch(R.id.switchCharging, prefs.onlyWhileCharging) { prefs.onlyWhileCharging = it }
        bindSwitch(R.id.switchNotifications, prefs.showNotifications) { prefs.showNotifications = it }
        bindSwitch(R.id.switchSingleTap, prefs.singleTapToExit) { prefs.singleTapToExit = it }

        // Widgets
        bindSwitch(R.id.switchAlarm, prefs.showAlarm) { prefs.showAlarm = it }
        bindSwitch(R.id.switchMedia, prefs.showMedia) { prefs.showMedia = it }
        switchCalendar = findViewById(R.id.switchCalendar)
        bindSwitch(R.id.switchCalendar, prefs.showCalendar) { on ->
            prefs.showCalendar = on
            if (on && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
            }
        }
        btnWidget = findViewById(R.id.btnWidget)
        btnWidget.setOnClickListener {
            if (prefs.hostedWidgetId != -1) {
                WidgetHost.remove(this)
                updateWidgetButton()
            } else {
                pickWidget()
            }
        }

        val labelBrightness = findViewById<TextView>(R.id.labelBrightness)
        bindSlider(R.id.sliderBrightness, prefs.brightness) {
            prefs.brightness = it
            labelBrightness.text = getString(R.string.brightness, it)
        }
        val labelTimeout = findViewById<TextView>(R.id.labelTimeout)
        bindSlider(R.id.sliderTimeout, prefs.timeoutMinutes) {
            prefs.timeoutMinutes = it
            labelTimeout.text = if (it == 0) getString(R.string.timeout_never)
            else getString(R.string.timeout_minutes, it)
        }
        val labelMin = findViewById<TextView>(R.id.labelMinBattery)
        bindSlider(R.id.sliderMinBattery, prefs.minBattery) {
            prefs.minBattery = it
            labelMin.text = getString(R.string.min_battery, it)
        }

        findViewById<Button>(R.id.btnPreview).setOnClickListener {
            startActivity(Intent(this, AodActivity::class.java))
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        val overlayOk = Settings.canDrawOverlays(this)
        val notifOk = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        btnOverlay.text = label(R.string.perm_overlay, overlayOk)
        btnNotif.text = label(R.string.perm_notif, notifOk)
        btnBattery.text = label(R.string.perm_battery, batteryOk)
        btnBattery.isEnabled = !batteryOk

        updateWidgetButton()

        // Kalender-Widget ausschalten, wenn die Kalender-Berechtigung abgelehnt wurde.
        if (prefs.showCalendar &&
            checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            switchCalendar.isChecked = false
        }

        // Falls die Overlay-Berechtigung entzogen wurde, Dienst ausschalten.
        if (prefs.enabled && !overlayOk) prefs.enabled = false
        switchEnabled.isChecked = prefs.enabled
        if (prefs.enabled) AodService.start(this)

        // Benachrichtigungs-Listener neu verbinden, falls Android ihn getrennt hat.
        if (notifOk && NotificationWatcher.instance == null) {
            runCatching {
                android.service.notification.NotificationListenerService.requestRebind(
                    ComponentName(this, NotificationWatcher::class.java)
                )
            }
        }
    }

    // ---------------- Android-Widget auswählen ----------------

    private fun updateWidgetButton() {
        val id = prefs.hostedWidgetId
        val info = if (id != -1) WidgetHost.manager(this).getAppWidgetInfo(id) else null
        if (id != -1 && info == null) prefs.hostedWidgetId = -1 // App des Widgets wurde deinstalliert
        btnWidget.text = if (info != null) getString(R.string.widget_remove, info.loadLabel(packageManager))
        else getString(R.string.widget_add)
    }

    private fun pickWidget() {
        val providers = WidgetHost.manager(this).installedProviders
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (providers.isEmpty()) {
            Toast.makeText(this, R.string.widget_none, Toast.LENGTH_LONG).show()
            return
        }
        val labels = providers.map { p ->
            val app = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(p.provider.packageName, 0)
                ).toString()
            }.getOrDefault(p.provider.packageName)
            val name = p.loadLabel(packageManager).toString()
            if (name == app) name else "$app – $name"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.widget_pick)
            .setItems(labels) { _, which -> bindWidget(providers[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindWidget(info: AppWidgetProviderInfo) {
        val host = WidgetHost.host(this)
        val id = host.allocateAppWidgetId()
        pendingWidgetId = id
        if (WidgetHost.manager(this).bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrSave(id, info)
        } else {
            // Android fragt den Nutzer einmalig, ob die App Widgets anzeigen darf.
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_BIND)
        }
    }

    private fun configureOrSave(id: Int, info: AppWidgetProviderInfo?) {
        if (info?.configure != null) {
            val started = runCatching {
                WidgetHost.host(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            }.isSuccess
            if (started) return
        }
        saveWidget(id)
    }

    private fun saveWidget(id: Int) {
        WidgetHost.remove(this)
        prefs.hostedWidgetId = id
        pendingWidgetId = -1
        updateWidgetButton()
    }

    private fun discardPendingWidget() {
        if (pendingWidgetId != -1) {
            runCatching { WidgetHost.host(this).deleteAppWidgetId(pendingWidgetId) }
            pendingWidgetId = -1
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = pendingWidgetId
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK && id != -1) {
                configureOrSave(id, WidgetHost.manager(this).getAppWidgetInfo(id))
            } else discardPendingWidget()
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK && id != -1) saveWidget(id) else discardPendingWidget()
        }
    }

    private fun label(res: Int, ok: Boolean) = (if (ok) "✓  " else "✗  ") + getString(res)

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<MaterialSwitch>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun bindSlider(id: Int, initial: Int, onChange: (Int) -> Unit) {
        val s = findViewById<Slider>(id)
        s.value = initial.toFloat().coerceIn(s.valueFrom, s.valueTo)
        onChange(s.value.toInt())
        s.addOnChangeListener { _, v, _ -> onChange(v.toInt()) }
    }

    companion object {
        private const val REQ_CALENDAR = 2
        private const val REQ_BIND = 10
        private const val REQ_CONFIGURE = 11
    }
}
