package de.dennis.aod

import android.Manifest
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
}
