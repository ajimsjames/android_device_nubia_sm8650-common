/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.lineageos.settings.utils.FileUtils
import org.lineageos.settings.utils.SettingsUtils

object FanController {

    const val KEY_FAN_ENABLE = "fan_enable"
    const val KEY_FAN_SPEED = "fan_speed_level"
    const val KEY_FAN_AUTO_MODE = "fan_auto_mode"
    const val KEY_FAN_PROFILE = "fan_profile"

    const val FAN_ENABLE_NODE = "/sys/kernel/fan/fan_enable"
    const val FAN_SPEED_NODE = "/sys/kernel/fan/fan_speed_level"
    const val FAN_RPM_NODE = "/sys/kernel/fan/fan_speed_count"
    const val BATT_TEMP_NODE = "/sys/class/power_supply/battery/temp"
    const val CPU_TEMP_NODE = "/sys/class/thermal/thermal_zone10/temp"

    const val FAN_MIN_SPEED = 1
    const val FAN_MAX_SPEED = 5
    const val FAN_DEFAULT_SPEED = 3

    const val PROFILE_QUIET = 0
    const val PROFILE_BALANCED = 1
    const val PROFILE_EXTREME = 2

    fun isFanEnabled(context: Context): Boolean {
        val nodeVal = FileUtils.readOneLine(FAN_ENABLE_NODE)?.trim()
        if (nodeVal == "1") return true
        return SettingsUtils.getInt(context, KEY_FAN_ENABLE, 0) == 1
    }

    fun isAutoMode(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_FAN_AUTO_MODE, 0) == 1
    }

    fun getFanSpeed(context: Context): Int {
        val nodeVal = FileUtils.readOneLine(FAN_SPEED_NODE)?.trim()?.toIntOrNull()
        if (nodeVal != null && nodeVal in FAN_MIN_SPEED..FAN_MAX_SPEED) return nodeVal
        return SettingsUtils.getInt(context, KEY_FAN_SPEED, FAN_DEFAULT_SPEED)
    }

    fun setFanEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_FAN_ENABLE, if (enabled) 1 else 0)
        FileUtils.writeLine(FAN_ENABLE_NODE, if (enabled) "1" else "0")

        if (enabled && isAutoMode(context)) {
            startThermalService(context)
        } else if (!enabled) {
            stopThermalService(context)
        }
    }

    fun setAutoMode(context: Context, auto: Boolean) {
        SettingsUtils.putInt(context, KEY_FAN_AUTO_MODE, if (auto) 1 else 0)
        if (auto && isFanEnabled(context)) {
            startThermalService(context)
        } else {
            stopThermalService(context)
            val speed = SettingsUtils.getInt(context, KEY_FAN_SPEED, FAN_DEFAULT_SPEED)
            FileUtils.writeLine(FAN_SPEED_NODE, speed.toString())
        }
    }

    fun setFanSpeed(context: Context, speed: Int) {
        SettingsUtils.putInt(context, KEY_FAN_SPEED, speed)
        FileUtils.writeLine(FAN_SPEED_NODE, speed.toString())
    }

    fun setFanSpeedRaw(speed: Int) {
        FileUtils.writeLine(FAN_SPEED_NODE, speed.toString())
    }

    fun setProfile(context: Context, profile: Int) {
        SettingsUtils.putInt(context, KEY_FAN_PROFILE, profile)
    }

    fun getProfile(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_FAN_PROFILE, PROFILE_BALANCED)
    }

    fun getFanRpm(context: Context? = null): Int {
        if (context != null && isFanEnabled(context)) {
            val level = getFanSpeed(context)
            return when (level) {
                1 -> 4200
                2 -> 7000
                3 -> 10000
                4 -> 15000
                5 -> 20000
                else -> 4200
            }
        }
        return 0
    }

    fun getCpuTemp(): Float {
        val tempStr = FileUtils.readOneLine(CPU_TEMP_NODE)?.trim()
        val tempInt = tempStr?.toIntOrNull() ?: 0
        if (tempInt > 1000) return tempInt / 1000.0f
        if (tempInt > 0) return tempInt.toFloat()
        return 0f
    }

    fun getBatteryTemp(context: Context? = null): Float {
        val tempStr = FileUtils.readOneLine(BATT_TEMP_NODE)?.trim()
        val tempInt = tempStr?.toIntOrNull()
        if (tempInt != null && tempInt > 0) {
            return if (tempInt > 1000) tempInt / 1000.0f else tempInt / 10.0f
        }
        if (context != null) {
            try {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (intent != null) {
                    val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    if (tempTenths > 0) return tempTenths / 10.0f
                }
            } catch (e: Exception) {
            }
        }
        return 0f
    }

    @Volatile
    var isCleaningInProgress: Boolean = false
        private set

    fun startDustCleaningCycle(context: Context, onComplete: (() -> Unit)? = null) {
        if (isCleaningInProgress) return
        isCleaningInProgress = true

        kotlin.concurrent.thread(start = true, name = "FanDustClean") {
            val wasEnabled = isFanEnabled(context)
            val prevSpeed = getFanSpeed(context)
            val prevAuto = isAutoMode(context)

            try {
                setAutoMode(context, false)
                setFanEnabled(context, true)

                // 15-second high-power centrifugal pulse cycle
                for (cycle in 1..5) {
                    // Maximum speed burst
                    setFanSpeedRaw(5)
                    Thread.sleep(1800)

                    // Rapid reverse brake / low speed pulse
                    setFanSpeedRaw(1)
                    Thread.sleep(600)

                    // Spike burst
                    setFanSpeedRaw(5)
                    Thread.sleep(800)
                }
            } catch (ignored: Exception) {
            } finally {
                // Restore previous state
                if (wasEnabled) {
                    setFanSpeed(context, prevSpeed)
                    setAutoMode(context, prevAuto)
                } else {
                    setFanEnabled(context, false)
                    setAutoMode(context, prevAuto)
                }
                isCleaningInProgress = false
                onComplete?.invoke()
            }
        }
    }

    fun startThermalService(context: Context) {
        val intent = Intent(context, FanThermalService::class.java)
        context.startService(intent)
    }

    fun stopThermalService(context: Context) {
        val intent = Intent(context, FanThermalService::class.java)
        context.stopService(intent)
    }

    fun restoreSettings(context: Context) {
        val fanEnabled = isFanEnabled(context)
        val autoMode = isAutoMode(context)
        val fanSpeed = SettingsUtils.getInt(context, KEY_FAN_SPEED, FAN_DEFAULT_SPEED)

        if (fanEnabled) {
            FileUtils.writeLine(FAN_ENABLE_NODE, "1")
            if (autoMode) {
                startThermalService(context)
            } else {
                FileUtils.writeLine(FAN_SPEED_NODE, fanSpeed.toString())
            }
        } else {
            FileUtils.writeLine(FAN_ENABLE_NODE, "0")
            stopThermalService(context)
        }
    }
}
