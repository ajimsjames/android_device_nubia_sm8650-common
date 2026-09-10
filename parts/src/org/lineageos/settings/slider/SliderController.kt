/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.slider

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.MediaStore
import android.util.Log
import org.lineageos.settings.fan.FanController
import org.lineageos.settings.power.PowerProfileController
import org.lineageos.settings.trigger.TriggerController
import org.lineageos.settings.utils.SettingsUtils

object SliderController {

    private const val TAG = "SliderController"

    const val KEY_SLIDER_ACTION = "slider_switch_action"
    const val KEY_SLIDER_STATE = "slider_switch_state"

    const val ACTION_DIABLO_MODE = 0    // Diablo Mode + Turbo Fan + Triggers + Live Stats
    const val ACTION_LAUNCH_CAMERA = 1  // Open Default Camera
    const val ACTION_FLASHLIGHT = 2     // Toggle Torch / Flashlight
    const val ACTION_GAMING_SUITE = 3   // Performance Profile + Smart Fan + Triggers
    const val ACTION_COOLING_TURBO = 4  // Force Fan Max Level 5
    const val ACTION_SILENT_MODE = 5    // Mute Ringer / Silent Mode
    const val ACTION_VIBRATE_MODE = 6   // Vibrate Ringer
    const val ACTION_DND_SILENT = 7     // Do Not Disturb (Total Silence)

    fun getSliderAction(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_SLIDER_ACTION, ACTION_DIABLO_MODE)
    }

    fun setSliderAction(context: Context, action: Int) {
        SettingsUtils.putInt(context, KEY_SLIDER_ACTION, action)
    }

    fun onSliderToggled(context: Context, isCompetitiveOn: Boolean) {
        SettingsUtils.putInt(context, KEY_SLIDER_STATE, if (isCompetitiveOn) 1 else 0)
        val action = getSliderAction(context)
        Log.i(TAG, "Slider toggled: isCompetitiveOn=$isCompetitiveOn, action=$action")

        if (isCompetitiveOn) {
            when (action) {
                ACTION_DIABLO_MODE -> {
                    // Engage Extreme Diablo Mode Clocks
                    PowerProfileController.setProfile(context, PowerProfileController.PROFILE_DIABLO)
                    // Engage Full Turbo Fan (Level 5)
                    FanController.setFanEnabled(context, true)
                    FanController.setAutoMode(context, false)
                    FanController.setFanSpeed(context, 5)
                    // Turn on capacitive shoulder triggers
                    TriggerController.setTriggerEnabled(context, true)
                    // Show Silent Ongoing Notification with Live Hardware Stats
                    DiabloNotificationService.start(context)
                }
                ACTION_LAUNCH_CAMERA -> {
                    try {
                        val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(cameraIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch camera", e)
                    }
                }
                ACTION_FLASHLIGHT -> {
                    setTorch(context, true)
                }
                ACTION_GAMING_SUITE -> {
                    // Performance profile + Smart Auto Fan + Triggers
                    PowerProfileController.setProfile(context, PowerProfileController.PROFILE_PERFORMANCE)
                    FanController.setFanEnabled(context, true)
                    FanController.setAutoMode(context, true)
                    TriggerController.setTriggerEnabled(context, true)
                }
                ACTION_COOLING_TURBO -> {
                    FanController.setFanEnabled(context, true)
                    FanController.setAutoMode(context, false)
                    FanController.setFanSpeed(context, 5)
                }
                ACTION_SILENT_MODE -> {
                    try {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set silent mode", e)
                    }
                }
                ACTION_VIBRATE_MODE -> {
                    try {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set vibrate mode", e)
                    }
                }
                ACTION_DND_SILENT -> {
                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to enable DND", e)
                    }
                }
            }
        } else {
            // Slider OFF: Restore Balanced Power Profile & Smart Fan Control
            PowerProfileController.setProfile(context, PowerProfileController.PROFILE_BALANCED)
            FanController.setAutoMode(context, true)
            DiabloNotificationService.stop(context)

            when (action) {
                ACTION_DIABLO_MODE, ACTION_GAMING_SUITE -> {
                    TriggerController.setTriggerEnabled(context, false)
                }
                ACTION_FLASHLIGHT -> {
                    setTorch(context, false)
                }
                ACTION_SILENT_MODE, ACTION_VIBRATE_MODE -> {
                    try {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    } catch (ignored: Exception) {}
                }
                ACTION_DND_SILENT -> {
                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    private fun setTorch(context: Context, enabled: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: "0"
            cm.setTorchMode(cameraId, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch mode: $enabled", e)
        }
    }
}

