/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.lineageos.settings.fan.FanController
import org.lineageos.settings.power.ChargeBypassController
import org.lineageos.settings.power.PowerProfileController
import org.lineageos.settings.trigger.TriggerController

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PowerProfileController.restoreSettings(context)
            FanController.restoreSettings(context)
            TriggerController.restoreSettings(context)
            ChargeBypassController.restoreSettings(context)
            org.lineageos.settings.rgb.RgbController.restoreSettings(context)
            val sliderState = org.lineageos.settings.utils.SettingsUtils.getInt(context, org.lineageos.settings.slider.SliderController.KEY_SLIDER_STATE, 0)
            org.lineageos.settings.slider.SliderController.onSliderToggled(context, sliderState == 1)
            org.lineageos.settings.touch.TouchEnhancer.restoreSettings(context)
            try {
                context.startService(Intent(context, org.lineageos.settings.slider.SliderSwitchService::class.java))
            } catch (e: Exception) {
                android.util.Log.e("BootReceiver", "Failed to start SliderSwitchService", e)
            }
        }
    }
}
