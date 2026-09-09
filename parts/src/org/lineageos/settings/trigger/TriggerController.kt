/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.content.Context
import android.content.Intent
import org.lineageos.settings.utils.FileUtils
import org.lineageos.settings.utils.SettingsUtils

object TriggerController {

    const val KEY_TRIGGER_ENABLE = "trigger_enable"
    const val KEY_FEEDBACK_ENABLE = "trigger_feedback_enable"
    const val KEY_APP_FILTER_MODE = "trigger_app_filter_mode" // 0: All Apps, 1: Selected Apps Only
    const val KEY_SELECTED_APPS = "trigger_selected_apps"

    const val KEY_LEFT_MODE = "trigger_left_mode"
    const val KEY_LEFT_CPS = "trigger_left_cps"
    const val KEY_LEFT_X = "trigger_left_x"
    const val KEY_LEFT_Y = "trigger_left_y"

    const val KEY_RIGHT_MODE = "trigger_right_mode"
    const val KEY_RIGHT_CPS = "trigger_right_cps"
    const val KEY_RIGHT_X = "trigger_right_x"
    const val KEY_RIGHT_Y = "trigger_right_y"

    const val TRIGGER_BUTTON1_ENABLE_NODE = "/proc/nubia_key/sar0/mode_operation"
    const val TRIGGER_BUTTON2_ENABLE_NODE = "/proc/nubia_key/sar1/mode_operation"
    const val TRIGGER_BUTTON1_SYSFS_NODE = "/sys/class/leds/sar0/mode_operation"
    const val TRIGGER_BUTTON2_SYSFS_NODE = "/sys/class/leds/sar1/mode_operation"

    const val TRIGGER_SLEEP_MODE = "2"
    const val TRIGGER_WAKE_MODE = "1"

    const val MODE_NORMAL = 0
    const val MODE_HOLD = 1
    const val MODE_RAPID_FIRE = 2
    const val MODE_DUAL_ACTION = 3

    const val DEFAULT_LEFT_X = 300
    const val DEFAULT_LEFT_Y = 600
    const val DEFAULT_RIGHT_X = 800
    const val DEFAULT_RIGHT_Y = 600
    const val DEFAULT_CPS = 12

    fun isTriggerEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_TRIGGER_ENABLE, 0) == 1
    }

    fun setTriggerEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_TRIGGER_ENABLE, if (enabled) 1 else 0)
        val mode = if (enabled) TRIGGER_WAKE_MODE else TRIGGER_SLEEP_MODE
        FileUtils.writeLine(TRIGGER_BUTTON1_ENABLE_NODE, mode)
        FileUtils.writeLine(TRIGGER_BUTTON2_ENABLE_NODE, mode)
        FileUtils.writeLine(TRIGGER_BUTTON1_SYSFS_NODE, mode)
        FileUtils.writeLine(TRIGGER_BUTTON2_SYSFS_NODE, mode)

        if (enabled) {
            startTriggerService(context)
        } else {
            stopTriggerService(context)
        }
    }

    fun isFeedbackEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_FEEDBACK_ENABLE, 1) == 1
    }

    fun setFeedbackEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_FEEDBACK_ENABLE, if (enabled) 1 else 0)
    }

    fun isAppFilterEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_APP_FILTER_MODE, 0) == 1
    }

    fun setAppFilterEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_APP_FILTER_MODE, if (enabled) 1 else 0)
    }

    fun getSelectedApps(context: Context): Set<String> {
        val raw = SettingsUtils.getString(context, KEY_SELECTED_APPS, "") ?: ""
        return if (raw.isEmpty()) emptySet() else raw.split(",").filter { it.isNotEmpty() }.toSet()
    }

    fun setSelectedApps(context: Context, apps: Set<String>) {
        val raw = apps.joinToString(",")
        SettingsUtils.putString(context, KEY_SELECTED_APPS, raw)
    }

    fun isAppAllowed(context: Context, packageName: String?): Boolean {
        if (!isAppFilterEnabled(context)) return true
        if (packageName.isNullOrEmpty()) return true
        val selected = getSelectedApps(context)
        return selected.isEmpty() || selected.contains(packageName)
    }

    fun getLeftMode(context: Context): Int =
        SettingsUtils.getInt(context, KEY_LEFT_MODE, MODE_NORMAL)

    fun setLeftMode(context: Context, mode: Int) =
        SettingsUtils.putInt(context, KEY_LEFT_MODE, mode)

    fun getLeftCps(context: Context): Int =
        SettingsUtils.getInt(context, KEY_LEFT_CPS, DEFAULT_CPS)

    fun setLeftCps(context: Context, cps: Int) =
        SettingsUtils.putInt(context, KEY_LEFT_CPS, cps)

    fun getLeftCoordinates(context: Context): Pair<Int, Int> {
        val x = SettingsUtils.getInt(context, KEY_LEFT_X, DEFAULT_LEFT_X)
        val y = SettingsUtils.getInt(context, KEY_LEFT_Y, DEFAULT_LEFT_Y)
        return Pair(x, y)
    }

    fun setLeftCoordinates(context: Context, x: Int, y: Int) {
        SettingsUtils.putInt(context, KEY_LEFT_X, x)
        SettingsUtils.putInt(context, KEY_LEFT_Y, y)
    }

    fun getRightMode(context: Context): Int =
        SettingsUtils.getInt(context, KEY_RIGHT_MODE, MODE_NORMAL)

    fun setRightMode(context: Context, mode: Int) =
        SettingsUtils.putInt(context, KEY_RIGHT_MODE, mode)

    fun getRightCps(context: Context): Int =
        SettingsUtils.getInt(context, KEY_RIGHT_CPS, DEFAULT_CPS)

    fun setRightCps(context: Context, cps: Int) =
        SettingsUtils.putInt(context, KEY_RIGHT_CPS, cps)

    fun getRightCoordinates(context: Context): Pair<Int, Int> {
        val x = SettingsUtils.getInt(context, KEY_RIGHT_X, DEFAULT_RIGHT_X)
        val y = SettingsUtils.getInt(context, KEY_RIGHT_Y, DEFAULT_RIGHT_Y)
        return Pair(x, y)
    }

    fun setRightCoordinates(context: Context, x: Int, y: Int) {
        SettingsUtils.putInt(context, KEY_RIGHT_X, x)
        SettingsUtils.putInt(context, KEY_RIGHT_Y, y)
    }

    fun startTriggerService(context: Context) {
        val intent = Intent(context, TriggerService::class.java)
        context.startService(intent)
    }

    fun stopTriggerService(context: Context) {
        val intent = Intent(context, TriggerService::class.java)
        context.stopService(intent)
    }

    fun startOverlayService(context: Context) {
        val intent = Intent(context, TriggerOverlayService::class.java)
        context.startService(intent)
    }

    fun restoreSettings(context: Context) {
        val triggerEnabled = isTriggerEnabled(context)
        setTriggerEnabled(context, triggerEnabled)
    }
}
