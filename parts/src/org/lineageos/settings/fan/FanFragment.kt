/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import org.lineageos.settings.R
import org.lineageos.settings.power.PowerProfileController
import org.lineageos.settings.utils.SettingsUtils

class FanFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    private var fanSwitch: MainSwitchPreference? = null
    private var fanAutoPref: SwitchPreferenceCompat? = null
    private var fanProfilePref: ListPreference? = null
    private var fanSpeedPref: SeekBarPreference? = null
    private var fanStatusPref: Preference? = null

    private val handler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val context = context ?: return
            val isEnabled = FanController.isFanEnabled(context)
            fanSwitch?.isChecked = isEnabled
            val speed = FanController.getFanSpeed(context)
            fanSpeedPref?.value = speed
            val isAuto = FanController.isAutoMode(context)
            fanAutoPref?.isChecked = isAuto
            updateControlsState(isEnabled, isAuto)
            updateStatus()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fan_preferences, rootKey)

        val context = requireContext()

        fanSwitch = findPreference(FanController.KEY_FAN_ENABLE)
        val isEnabled = FanController.isFanEnabled(context)
        fanSwitch?.isChecked = isEnabled
        fanSwitch?.onPreferenceChangeListener = this

        fanStatusPref = findPreference("fan_status")

        fanAutoPref = findPreference(FanController.KEY_FAN_AUTO_MODE)
        val isAuto = FanController.isAutoMode(context)
        fanAutoPref?.isChecked = isAuto
        fanAutoPref?.onPreferenceChangeListener = this

        fanProfilePref = findPreference(FanController.KEY_FAN_PROFILE)
        fanProfilePref?.value = FanController.getProfile(context).toString()
        fanProfilePref?.onPreferenceChangeListener = this

        fanSpeedPref = findPreference(FanController.KEY_FAN_SPEED)
        fanSpeedPref?.value = FanController.getFanSpeed(context)
        fanSpeedPref?.onPreferenceChangeListener = this

        val reverseCleanPref: Preference? = findPreference("fan_reverse_clean")
        reverseCleanPref?.setOnPreferenceClickListener {
            if (FanController.isCleaningInProgress) {
                android.widget.Toast.makeText(context, "Dust cleaning cycle already running...", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Starting Reverse Spin Dust Cleaning Cycle (15s)...", android.widget.Toast.LENGTH_LONG).show()
                reverseCleanPref.summary = "Cleaning in progress... High-power centrifugal pulse active"
                FanController.startDustCleaningCycle(context) {
                    handler.post {
                        reverseCleanPref.summary = "Execute a 15-second high-power centrifugal pulse cycle to eject accumulated dust from the cooling channel"
                        android.widget.Toast.makeText(context, "Fan Dust Cleaning Complete!", android.widget.Toast.LENGTH_SHORT).show()
                        updateStatus()
                    }
                }
            }
            true
        }

        updateControlsState(isEnabled, isAuto)
        updateStatus()
    }

    override fun onStart() {
        super.onStart()
        val context = context ?: return
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_" + FanController.KEY_FAN_ENABLE),
            false,
            settingsObserver
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_" + FanController.KEY_FAN_SPEED),
            false,
            settingsObserver
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_" + FanController.KEY_FAN_AUTO_MODE),
            false,
            settingsObserver
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_" + PowerProfileController.KEY_POWER_PROFILE),
            false,
            settingsObserver
        )
    }

    override fun onStop() {
        super.onStop()
        context?.contentResolver?.unregisterContentObserver(settingsObserver)
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusUpdateRunnable)
    }

    private fun updateStatus() {
        val context = context ?: return
        val rpm = FanController.getFanRpm(context)
        val battTemp = FanController.getBatteryTemp(context)
        val cpuTemp = FanController.getCpuTemp()
        val isEnabled = FanController.isFanEnabled(context)
        val currentSpeed = FanController.getFanSpeed(context)

        // Live reflect on controls
        fanSwitch?.isChecked = isEnabled
        fanSpeedPref?.value = currentSpeed

        if (isEnabled && rpm > 0) {
            fanStatusPref?.summary = "Speed: $rpm RPM (Level $currentSpeed)  |  CPU: " + String.format("%.1f", cpuTemp) + "°C  |  Battery: " + String.format("%.1f", battTemp) + "°C"
        } else if (isEnabled) {
            fanStatusPref?.summary = "Spinning Up (Level $currentSpeed)...  |  CPU: " + String.format("%.1f", cpuTemp) + "°C  |  Battery: " + String.format("%.1f", battTemp) + "°C"
        } else {
            fanStatusPref?.summary = "Fan Stopped (0 RPM)  |  CPU: " + String.format("%.1f", cpuTemp) + "°C  |  Battery: " + String.format("%.1f", battTemp) + "°C"
        }
    }

    private fun updateControlsState(isEnabled: Boolean, isAuto: Boolean) {
        fanAutoPref?.isEnabled = isEnabled
        fanProfilePref?.isEnabled = isEnabled
        fanSpeedPref?.isEnabled = isEnabled
        fanProfilePref?.isVisible = isAuto
        fanSpeedPref?.isVisible = !isAuto
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val context = requireContext()
        when (preference.key) {
            FanController.KEY_FAN_ENABLE -> {
                val enabled = newValue as Boolean
                FanController.setFanEnabled(context, enabled)
                updateControlsState(enabled, FanController.isAutoMode(context))
                updateStatus()
                return true
            }
            FanController.KEY_FAN_AUTO_MODE -> {
                val auto = newValue as Boolean
                FanController.setAutoMode(context, auto)
                updateControlsState(FanController.isFanEnabled(context), auto)
                updateStatus()
                return true
            }
            FanController.KEY_FAN_PROFILE -> {
                val profile = (newValue as String).toInt()
                FanController.setProfile(context, profile)
                return true
            }
            FanController.KEY_FAN_SPEED -> {
                val speed = newValue as Int
                FanController.setFanSpeed(context, speed)
                updateStatus()
                return true
            }
        }
        return false
    }
}
