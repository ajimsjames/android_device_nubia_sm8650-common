/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.content.Context
import org.lineageos.settings.utils.*

object TriggerController {

    const val KEY_TRIGGER_ENABLE = "trigger_enable"

    const val KEY_TRIGGER_L1_POS_X = "trigger_l1_pos_x"
    const val KEY_TRIGGER_L1_POS_Y = "trigger_l1_pos_y"
    const val KEY_TRIGGER_R1_POS_X = "trigger_r1_pos_x"
    const val KEY_TRIGGER_R1_POS_Y = "trigger_r1_pos_y"

    const val KEY_TRIGGER_RAPID_L1 = "trigger_rapid_l1"
    const val KEY_TRIGGER_RAPID_R1 = "trigger_rapid_r1"
    const val KEY_TRIGGER_RAPID_RATE = "trigger_rapid_rate"
    const val KEY_TRIGGER_HAPTIC = "trigger_haptic"

    const val DEFAULT_L1_POS_X = 20
    const val DEFAULT_L1_POS_Y = 20
    const val DEFAULT_R1_POS_X = 80
    const val DEFAULT_R1_POS_Y = 20
    const val DEFAULT_RAPID_RATE = 10 // 10 taps / second

    const val TRIGGER_BUTTON1_ENABLE_NODE = "/proc/nubia_key/sar0/mode_operation"
    const val TRIGGER_BUTTON2_ENABLE_NODE = "/proc/nubia_key/sar1/mode_operation"

    const val TRIGGER_SLEEP_MODE = "2"
    const val TRIGGER_WAKE_MODE = "1"

    /*
     * Enable or disable the trigger buttons
     * @return puts the value in place
     */
    fun setTriggerEnabled(context: Context, enabled: Boolean) {
        putInt(context, KEY_TRIGGER_ENABLE, if (enabled) 1 else 0)
        writeLine(
            TRIGGER_BUTTON1_ENABLE_NODE,
            if (enabled) TRIGGER_WAKE_MODE else TRIGGER_SLEEP_MODE,
        )
        writeLine(
            TRIGGER_BUTTON2_ENABLE_NODE,
            if (enabled) TRIGGER_WAKE_MODE else TRIGGER_SLEEP_MODE,
        )

        val serviceIntent = android.content.Intent(context, TriggerService::class.java)
        if (enabled) {
            context.startService(serviceIntent)
        } else {
            context.stopService(serviceIntent)
        }
    }

    fun isTriggerEnabled(context: Context): Boolean {
        return getInt(context, KEY_TRIGGER_ENABLE, 0) == 1
    }

    fun isRapidFireL1Enabled(context: Context): Boolean {
        return getInt(context, KEY_TRIGGER_RAPID_L1, 0) == 1
    }

    fun setRapidFireL1Enabled(context: Context, enabled: Boolean) {
        putInt(context, KEY_TRIGGER_RAPID_L1, if (enabled) 1 else 0)
    }

    fun isRapidFireR1Enabled(context: Context): Boolean {
        return getInt(context, KEY_TRIGGER_RAPID_R1, 0) == 1
    }

    fun setRapidFireR1Enabled(context: Context, enabled: Boolean) {
        putInt(context, KEY_TRIGGER_RAPID_R1, if (enabled) 1 else 0)
    }

    fun getRapidFireRate(context: Context): Int {
        return getInt(context, KEY_TRIGGER_RAPID_RATE, DEFAULT_RAPID_RATE)
    }

    fun setRapidFireRate(context: Context, rate: Int) {
        putInt(context, KEY_TRIGGER_RAPID_RATE, rate)
    }

    fun isHapticEnabled(context: Context): Boolean {
        return getInt(context, KEY_TRIGGER_HAPTIC, 1) == 1
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        putInt(context, KEY_TRIGGER_HAPTIC, if (enabled) 1 else 0)
    }

    fun getTriggerCoord(context: Context, key: String, defaultVal: Int): Int {
        return getInt(context, key, defaultVal)
    }

    fun setTriggerCoord(context: Context, key: String, value: Int) {
        putInt(context, key, value)
    }

    /*
     * Apply trigger buttons state settings
     * @return puts the value in place
     */
    fun applySettings(context: Context, enabled: Boolean) {
        setTriggerEnabled(context, enabled)
    }

    /*
     * Restore settings on boot / resume
     * @return puts the value in place
     */
    fun restoreSettings(context: Context) {
        val triggerEnabled = isTriggerEnabled(context)
        applySettings(context, triggerEnabled)
    }
}
