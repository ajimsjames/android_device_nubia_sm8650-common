/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import org.lineageos.settings.utils.FileUtils
import org.lineageos.settings.utils.SettingsUtils

object ChargeBypassController {

    private const val TAG = "ChargeBypassController"

    const val KEY_CHARGE_BYPASS_ENABLE = "charge_bypass_enable"

    private val CHARGE_NODES = arrayOf(
        "/sys/class/qcom-battery/battery_charging_enabled",
        "/sys/class/qcom-battery/charging_enabled",
        "/sys/class/zte_power_supply/zte_battery/battery_charging_enabled",
        "/sys/class/zte_power_supply/zte_battery/charging_enabled",
        "/sys/class/power_supply/battery/charging_enabled",
        "/sys/class/power_supply/battery/charge_control_limit"
    )

    const val USB_ONLINE_NODE = "/sys/class/power_supply/usb/online"
    const val ACTION_DISABLE_BYPASS = "org.lineageos.settings.power.ACTION_DISABLE_BYPASS"

    fun isBypassSupported(): Boolean {
        return CHARGE_NODES.any { FileUtils.fileExists(it) }
    }

    fun isDeviceCharging(context: Context): Boolean {
        val usbOnline = FileUtils.readOneLine(USB_ONLINE_NODE)?.trim()
        if (usbOnline == "1") return true

        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged > 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun isBypassEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_CHARGE_BYPASS_ENABLE, 0) == 1
    }

    fun setBypassEnabled(context: Context, enabled: Boolean) {
        val isCharging = isDeviceCharging(context)
        Log.i(TAG, "setBypassEnabled: requested=$enabled, isCharging=$isCharging")

        if (enabled && !isCharging) {
            // Do not enable bypass when disconnected from charger
            SettingsUtils.putInt(context, KEY_CHARGE_BYPASS_ENABLE, 0)
            restoreNormalCharging()
            return
        }

        SettingsUtils.putInt(context, KEY_CHARGE_BYPASS_ENABLE, if (enabled) 1 else 0)

        if (enabled && isCharging) {
            // Disable battery charging -> system runs directly off USB input
            applyBypassState(bypass = true)
        } else {
            restoreNormalCharging()
        }
    }

    fun onPowerConnectionChanged(context: Context, isConnected: Boolean) {
        val userWantsBypass = isBypassEnabled(context)
        if (!isConnected) {
            // Charger disconnected: always restore hardware charging registers
            restoreNormalCharging()
        } else if (userWantsBypass) {
            // Charger plugged back in: engage bypass if user setting is active
            applyBypassState(bypass = true)
        }
    }

    private fun applyBypassState(bypass: Boolean) {
        val valToWrite = if (bypass) "0" else "1"
        for (node in CHARGE_NODES) {
            if (FileUtils.fileExists(node)) {
                FileUtils.writeLine(node, valToWrite)
            }
        }
    }

    private fun restoreNormalCharging() {
        applyBypassState(bypass = false)
    }

    fun restoreSettings(context: Context) {
        val enabled = isBypassEnabled(context)
        setBypassEnabled(context, enabled)
    }
}
