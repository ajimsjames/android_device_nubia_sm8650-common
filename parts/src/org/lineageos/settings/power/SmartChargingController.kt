/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.lineageos.settings.trigger.TriggerController
import org.lineageos.settings.utils.FileUtils
import org.lineageos.settings.utils.SettingsUtils

object SmartChargingController {

    const val KEY_AUTO_BYPASS_GAME = "smart_charge_auto_bypass_game"
    const val KEY_CHARGE_LIMIT_ENABLE = "smart_charge_limit_enable"
    const val KEY_CHARGE_LIMIT_LEVEL = "smart_charge_limit_level"

    fun isAutoBypassOnGameEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_AUTO_BYPASS_GAME, 0) == 1
    }

    fun setAutoBypassOnGameEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_AUTO_BYPASS_GAME, if (enabled) 1 else 0)
    }

    fun isChargeLimitEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_CHARGE_LIMIT_ENABLE, 0) == 1
    }

    fun setChargeLimitEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_CHARGE_LIMIT_ENABLE, if (enabled) 1 else 0)
    }

    fun getChargeLimitLevel(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_CHARGE_LIMIT_LEVEL, 80)
    }

    fun setChargeLimitLevel(context: Context, level: Int) {
        SettingsUtils.putInt(context, KEY_CHARGE_LIMIT_LEVEL, level)
    }

    fun checkBatteryLevel(context: Context) {
        if (!isChargeLimitEnabled(context)) return

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return

        val batteryPct = (level * 100) / scale
        val targetLimit = getChargeLimitLevel(context)

        if (batteryPct >= targetLimit && !ChargeBypassController.isBypassEnabled(context)) {
            ChargeBypassController.setBypassEnabled(context, true)
        }
    }
}
