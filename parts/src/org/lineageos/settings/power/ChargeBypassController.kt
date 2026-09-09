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

    const val CHARGE_CONTROL_LIMIT_NODE = "/sys/class/power_supply/battery/charge_control_limit"
    const val CHARGE_CONTROL_LIMIT_MAX_NODE = "/sys/class/power_supply/battery/charge_control_limit_max"
    const val CHARGE_ENABLED_NODE = "/sys/class/power_supply/battery/charging_enabled"
    const val USB_ONLINE_NODE = "/sys/class/power_supply/usb/online"

    const val ACTION_DISABLE_BYPASS = "org.lineageos.settings.power.ACTION_DISABLE_BYPASS"

    fun isBypassSupported(): Boolean {
        return FileUtils.fileExists(CHARGE_CONTROL_LIMIT_NODE) || FileUtils.fileExists(CHARGE_ENABLED_NODE)
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
            // Set charge limit to 0 (bypass direct USB power)
            if (FileUtils.fileExists(CHARGE_CONTROL_LIMIT_NODE)) {
                FileUtils.writeLine(CHARGE_CONTROL_LIMIT_NODE, "0")
            }
            if (FileUtils.fileExists(CHARGE_ENABLED_NODE)) {
                FileUtils.writeLine(CHARGE_ENABLED_NODE, "0")
            }
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
            if (FileUtils.fileExists(CHARGE_CONTROL_LIMIT_NODE)) {
                FileUtils.writeLine(CHARGE_CONTROL_LIMIT_NODE, "0")
            }
            if (FileUtils.fileExists(CHARGE_ENABLED_NODE)) {
                FileUtils.writeLine(CHARGE_ENABLED_NODE, "0")
            }
        }
    }

    private fun restoreNormalCharging() {
        var maxLimit = "40"
        if (FileUtils.fileExists(CHARGE_CONTROL_LIMIT_MAX_NODE)) {
            maxLimit = FileUtils.readOneLine(CHARGE_CONTROL_LIMIT_MAX_NODE) ?: "40"
        }
        if (FileUtils.fileExists(CHARGE_CONTROL_LIMIT_NODE)) {
            FileUtils.writeLine(CHARGE_CONTROL_LIMIT_NODE, maxLimit)
        }
        if (FileUtils.fileExists(CHARGE_ENABLED_NODE)) {
            FileUtils.writeLine(CHARGE_ENABLED_NODE, "1")
        }
    }

    fun restoreSettings(context: Context) {
        val enabled = isBypassEnabled(context)
        setBypassEnabled(context, enabled)
    }
}
