/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.lineageos.settings.utils.SettingsUtils

object TriggerHapticManager {

    const val KEY_HAPTIC_ENABLE = "trigger_haptic_enable"
    const val KEY_HAPTIC_PROFILE = "trigger_haptic_profile"

    const val HAPTIC_PROFILE_CLICK = 0
    const val HAPTIC_PROFILE_HEAVY = 1
    const val HAPTIC_PROFILE_TICK = 2
    const val HAPTIC_PROFILE_DOUBLE_CLICK = 3

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun isHapticsEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_HAPTIC_ENABLE, 1) == 1
    }

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_HAPTIC_ENABLE, if (enabled) 1 else 0)
    }

    fun getHapticProfile(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_HAPTIC_PROFILE, HAPTIC_PROFILE_CLICK)
    }

    fun setHapticProfile(context: Context, profile: Int) {
        SettingsUtils.putInt(context, KEY_HAPTIC_PROFILE, profile)
    }

    fun performTriggerClick(context: Context) {
        if (!isHapticsEnabled(context)) return
        val vib = vibrator ?: return

        try {
            val profile = getHapticProfile(context)
            val effect = when (profile) {
                HAPTIC_PROFILE_HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HAPTIC_PROFILE_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HAPTIC_PROFILE_DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
            vib.vibrate(effect, attributes)
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                vib.vibrate(20)
            } catch (ignored: Exception) {
            }
        }
    }
}
