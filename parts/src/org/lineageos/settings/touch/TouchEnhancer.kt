/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.touch

import android.content.Context
import org.lineageos.settings.utils.FileUtils
import org.lineageos.settings.utils.SettingsUtils

object TouchEnhancer {

    const val KEY_TOUCH_HIGH_RATE = "touch_high_sampling_rate"
    const val KEY_EDGE_DEADZONE = "touch_edge_deadzone"

    const val TOUCH_GAME_NODE = "/sys/devices/platform/goodix_ts.0/rate_boost"
    const val TOUCH_EDGE_NODE = "/sys/devices/platform/goodix_ts.0/edge_mode"

    fun isHighSamplingRateEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_TOUCH_HIGH_RATE, 1) == 1
    }

    fun setHighSamplingRateEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_TOUCH_HIGH_RATE, if (enabled) 1 else 0)
        FileUtils.writeLine(TOUCH_GAME_NODE, if (enabled) "1" else "0")
    }

    fun getEdgeDeadzone(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_EDGE_DEADZONE, 1) // 0: Off, 1: Standard, 2: Wide
    }

    fun setEdgeDeadzone(context: Context, mode: Int) {
        SettingsUtils.putInt(context, KEY_EDGE_DEADZONE, mode)
        FileUtils.writeLine(TOUCH_EDGE_NODE, mode.toString())
    }

    fun restoreSettings(context: Context) {
        setHighSamplingRateEnabled(context, isHighSamplingRateEnabled(context))
        setEdgeDeadzone(context, getEdgeDeadzone(context))
    }
}
