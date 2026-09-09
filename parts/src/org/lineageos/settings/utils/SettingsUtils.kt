/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.utils

import android.content.Context
import android.provider.Settings

private const val SETTINGS_PREFIX = "nubia_parts_"

object SettingsUtils {
    fun putInt(context: Context, key: String, value: Int) {
        Settings.Global.putInt(context.contentResolver, SETTINGS_PREFIX + key, value)
    }

    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        return Settings.Global.getInt(context.contentResolver, SETTINGS_PREFIX + key, defaultValue)
    }

    fun putString(context: Context, key: String, value: String) {
        Settings.Global.putString(context.contentResolver, SETTINGS_PREFIX + key, value)
    }

    fun getString(context: Context, key: String, defaultValue: String = ""): String? {
        val result = Settings.Global.getString(context.contentResolver, SETTINGS_PREFIX + key)
        return result ?: defaultValue
    }
}

fun putInt(context: Context, key: String, value: Int) = SettingsUtils.putInt(context, key, value)
fun getInt(context: Context, key: String, defaultValue: Int): Int = SettingsUtils.getInt(context, key, defaultValue)
fun putString(context: Context, key: String, value: String) = SettingsUtils.putString(context, key, value)
fun getString(context: Context, key: String, defaultValue: String = ""): String? = SettingsUtils.getString(context, key, defaultValue)
