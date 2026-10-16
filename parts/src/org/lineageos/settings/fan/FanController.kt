/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.content.Context
import org.lineageos.settings.utils.*

object FanController {

    const val KEY_FAN_ENABLE = "fan_enable"
    const val KEY_SMART_FAN_ENABLE = "smart_fan_enable"
    const val KEY_FAN_SPEED = "fan_speed_level"
    const val KEY_SMART_FAN_TEMP_SPEED_1 = "smart_fan_temp_speed_1"
    const val KEY_SMART_FAN_TEMP_SPEED_2 = "smart_fan_temp_speed_2"
    const val KEY_SMART_FAN_TEMP_SPEED_3 = "smart_fan_temp_speed_3"
    const val KEY_SMART_FAN_TEMP_SPEED_4 = "smart_fan_temp_speed_4"
    const val KEY_SMART_FAN_TEMP_SPEED_5 = "smart_fan_temp_speed_5"

    const val DEFAULT_TEMP_SPEED_1 = 36
    const val DEFAULT_TEMP_SPEED_2 = 40
    const val DEFAULT_TEMP_SPEED_3 = 44
    const val DEFAULT_TEMP_SPEED_4 = 48
    const val DEFAULT_TEMP_SPEED_5 = 52


    const val FAN_ENABLE_NODE = "/sys/kernel/fan/fan_enable"
    const val FAN_SPEED_NODE = "/sys/kernel/fan/fan_speed_level"
    const val FAN_RPM_NODE = "/sys/kernel/fan/fan_speed_count"

    const val CPU_TEMP_NODE = "/sys/class/thermal/thermal_zone10/temp"
    const val GPU_TEMP_NODE = "/sys/class/thermal/thermal_zone41/temp"
    const val BATT_TEMP_NODE = "/sys/class/thermal/thermal_zone74/temp"

    const val FAN_MIN_SPEED = 1
    const val FAN_MAX_SPEED = 5
    const val FAN_DEFAULT_SPEED = 3

    fun getCpuTemp(): Int {
        val raw = readLine(CPU_TEMP_NODE)?.toIntOrNull() ?: 0
        return if (raw > 1000) raw / 1000 else raw
    }

    fun getGpuTemp(): Int {
        val raw = readLine(GPU_TEMP_NODE)?.toIntOrNull() ?: 0
        return if (raw > 1000) raw / 1000 else raw
    }

    fun getBatteryTemp(): Float {
        val raw = readLine(BATT_TEMP_NODE)?.toIntOrNull() ?: 0
        return if (raw > 1000) raw / 1000.0f else (if (raw > 100) raw / 10.0f else raw.toFloat())
    }

    fun getFanRpm(): Int {
        return readLine(FAN_RPM_NODE)?.toIntOrNull() ?: 0
    }

    fun writeRawFanEnabled(enabled: Boolean) {
        writeLine(FAN_ENABLE_NODE, if (enabled) "1" else "0")
    }

    fun writeRawFanSpeed(speed: Int) {
        writeLine(FAN_SPEED_NODE, speed.toString())
    }

    /*
     * Enable or disable the fan
     * @return puts the value in place
     */
    fun setFanEnabled(context: Context, enabled: Boolean) {
        putInt(context, KEY_FAN_ENABLE, if (enabled) 1 else 0)
        writeRawFanEnabled(enabled)
    }

    /*
     * Set the fan speed level
     * @return puts the value in place
     */
    fun setFanSpeed(context: Context, speed: Int) {
        putInt(context, KEY_FAN_SPEED, speed)
        writeRawFanSpeed(speed)
    }

    /*
     * Enable or disable smart auto fan control
     */
    fun setSmartFanEnabled(context: Context, enabled: Boolean) {
        putInt(context, KEY_SMART_FAN_ENABLE, if (enabled) 1 else 0)
        if (enabled) {
            SmartFanService.startService(context)
        } else {
            SmartFanService.stopService(context)
            restoreManualSettings(context)
        }
    }

    /*
     * Apply both fan state and speed settings
     * @return puts the value in place
     */
    fun applySettings(context: Context, enabled: Boolean, speed: Int) {
        setFanEnabled(context, enabled)
        setFanSpeed(context, speed)
    }

    fun restoreManualSettings(context: Context) {
        val fanEnabled = getInt(context, KEY_FAN_ENABLE, 0) == 1
        val fanSpeed = getInt(context, KEY_FAN_SPEED, FAN_DEFAULT_SPEED)
        applySettings(context, fanEnabled, fanSpeed)
    }

    /*
     * Restore settings on boot / resume
     * @return puts the value in place
     */
    fun restoreSettings(context: Context) {
        val smartFanEnabled = getInt(context, KEY_SMART_FAN_ENABLE, 1) == 1
        if (smartFanEnabled) {
            SmartFanService.startService(context)
        } else {
            restoreManualSettings(context)
        }
    }
}
