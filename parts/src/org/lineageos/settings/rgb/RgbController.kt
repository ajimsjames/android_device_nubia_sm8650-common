/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.rgb

import android.content.Context
import org.lineageos.settings.utils.FileUtils
import org.lineageos.settings.utils.SettingsUtils

object RgbController {

    const val KEY_RGB_ENABLE = "rgb_lighting_enable"
    const val KEY_RGB_MODE = "rgb_lighting_mode"
    const val KEY_RGB_COLOR = "rgb_lighting_color"
    const val KEY_RGB_BRIGHTNESS = "rgb_lighting_brightness"

    const val LED_ENABLE_NODE = "/sys/kernel/fan/led_enable"
    const val LED_BRIGHTNESS_NODE = "/sys/kernel/fan/led_brightness"
    const val LED_ID_NODE = "/sys/kernel/fan/led_id"
    const val LED_FADE_NODE = "/sys/kernel/fan/led_fade_ms"
    const val LED_ON_NODE = "/sys/kernel/fan/led_on_ms"
    const val LED_OFF_NODE = "/sys/kernel/fan/led_off_ms"

    const val MODE_STATIC = 0
    const val MODE_BREATHING = 1
    const val MODE_RAINBOW = 2
    const val MODE_GAME_COMBAT = 3

    const val COLOR_RED = 0
    const val COLOR_CYAN = 1
    const val COLOR_GREEN = 2
    const val COLOR_VIOLET = 3
    const val COLOR_YELLOW = 4
    const val COLOR_WHITE = 5

    fun isRgbEnabled(context: Context): Boolean {
        return SettingsUtils.getInt(context, KEY_RGB_ENABLE, 1) == 1
    }

    fun setRgbEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_RGB_ENABLE, if (enabled) 1 else 0)
        FileUtils.writeLine(LED_ENABLE_NODE, if (enabled) "1" else "0")
        if (enabled) {
            applyCurrentEffect(context)
        }
    }

    fun getMode(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_RGB_MODE, MODE_BREATHING)
    }

    fun setMode(context: Context, mode: Int) {
        SettingsUtils.putInt(context, KEY_RGB_MODE, mode)
        if (isRgbEnabled(context)) {
            applyCurrentEffect(context)
        }
    }

    fun getColor(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_RGB_COLOR, COLOR_RED)
    }

    fun setColor(context: Context, color: Int) {
        SettingsUtils.putInt(context, KEY_RGB_COLOR, color)
        if (isRgbEnabled(context)) {
            applyCurrentEffect(context)
        }
    }

    fun getBrightness(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_RGB_BRIGHTNESS, 200)
    }

    fun setBrightness(context: Context, brightness: Int) {
        SettingsUtils.putInt(context, KEY_RGB_BRIGHTNESS, brightness)
        if (isRgbEnabled(context)) {
            FileUtils.writeLine(LED_BRIGHTNESS_NODE, brightness.toString())
        }
    }

    fun applyCurrentEffect(context: Context) {
        if (!isRgbEnabled(context)) return

        val mode = getMode(context)
        val color = getColor(context)
        val brightness = getBrightness(context)

        FileUtils.writeLine(LED_ENABLE_NODE, "1")
        FileUtils.writeLine(LED_BRIGHTNESS_NODE, brightness.toString())
        FileUtils.writeLine(LED_ID_NODE, color.toString())

        when (mode) {
            MODE_STATIC -> {
                FileUtils.writeLine(LED_FADE_NODE, "0")
                FileUtils.writeLine(LED_ON_NODE, "1000")
                FileUtils.writeLine(LED_OFF_NODE, "0")
            }
            MODE_BREATHING -> {
                FileUtils.writeLine(LED_FADE_NODE, "800")
                FileUtils.writeLine(LED_ON_NODE, "600")
                FileUtils.writeLine(LED_OFF_NODE, "400")
            }
            MODE_RAINBOW -> {
                FileUtils.writeLine(LED_FADE_NODE, "400")
                FileUtils.writeLine(LED_ON_NODE, "300")
                FileUtils.writeLine(LED_OFF_NODE, "100")
            }
            MODE_GAME_COMBAT -> {
                FileUtils.writeLine(LED_FADE_NODE, "100")
                FileUtils.writeLine(LED_ON_NODE, "150")
                FileUtils.writeLine(LED_OFF_NODE, "150")
            }
        }
    }

    fun restoreSettings(context: Context) {
        val enabled = isRgbEnabled(context)
        setRgbEnabled(context, enabled)
    }
}
