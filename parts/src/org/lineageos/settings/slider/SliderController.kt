/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.slider

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import org.lineageos.settings.fan.FanController
import org.lineageos.settings.power.PowerProfileController
import org.lineageos.settings.trigger.TriggerController
import org.lineageos.settings.utils.SettingsUtils

object SliderController {

    const val KEY_SLIDER_ACTION = "slider_switch_action"
    const val KEY_SLIDER_STATE = "slider_switch_state"

    const val ACTION_DIABLO_MODE = 0    // Diablo Mode + Max Turbo Fan + Triggers + Notification
    const val ACTION_GAME_MODE = 1      // Performance profile + Fan Auto + Triggers On
    const val ACTION_COOLING_TURBO = 2  // Force Fan Max 14,000 RPM
    const val ACTION_DND_SILENT = 3     // Do Not Disturb + Total Silence

    fun getSliderAction(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_SLIDER_ACTION, ACTION_DIABLO_MODE)
    }

    fun setSliderAction(context: Context, action: Int) {
        SettingsUtils.putInt(context, KEY_SLIDER_ACTION, action)
    }

    fun onSliderToggled(context: Context, isCompetitiveOn: Boolean) {
        SettingsUtils.putInt(context, KEY_SLIDER_STATE, if (isCompetitiveOn) 1 else 0)
        val action = getSliderAction(context)

        if (isCompetitiveOn) {
            when (action) {
                ACTION_DIABLO_MODE, ACTION_GAME_MODE -> {
                    // Engage Extreme Diablo Mode Clocks
                    PowerProfileController.setProfile(context, PowerProfileController.PROFILE_DIABLO)
                    // Engage Full Turbo Fan
                    FanController.setFanEnabled(context, true)
                    FanController.setAutoMode(context, false)
                    FanController.setFanSpeed(context, 5) // Max 20,000 RPM
                    // Turn on Trigger buttons
                    TriggerController.setTriggerEnabled(context, true)
                    // Show Silent Ongoing Notification with Live Hardware Stats
                    DiabloNotificationService.start(context)
                }
                ACTION_COOLING_TURBO -> {
                    FanController.setFanEnabled(context, true)
                    FanController.setAutoMode(context, false)
                    FanController.setFanSpeed(context, 5)
                }
                ACTION_DND_SILENT -> {
                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    } catch (ignored: Exception) {}
                }
            }
        } else {
            // Slider OFF: Restore Balanced state and dismiss Diablo notification
            PowerProfileController.setProfile(context, PowerProfileController.PROFILE_BALANCED)
            FanController.setAutoMode(context, true)
            DiabloNotificationService.stop(context)

            if (action == ACTION_DND_SILENT) {
                try {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                } catch (ignored: Exception) {}
            }
        }
    }
}
