/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.utils

import android.content.Context
import android.provider.Settings

private const val SETTINGS_PREFIX = "nubia_parts_"

/*
 * Stores an integer value in Settings.Global
 * @return puts the value in place
 */
fun putInt(context: Context, key: String, value: Int) {
    Settings.Global.putInt(context.contentResolver, SETTINGS_PREFIX + key, value)
}

/*
 * Retrieves an integer value from Settings.Global
 * @return the value
 */
fun getInt(context: Context, key: String, defaultValue: Int): Int {
    return Settings.Global.getInt(context.contentResolver, SETTINGS_PREFIX + key, defaultValue)
}
