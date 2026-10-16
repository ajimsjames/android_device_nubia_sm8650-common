/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.utils

import android.util.Log
import java.io.File

private const val TAG = "FileUtils"

/*
 * Writes the given value into the given file
 * @return true on success, false on failure
 */
fun writeLine(fileName: String, value: String): Boolean =
    runCatching { File(fileName).writeText(value) }
        .onFailure { e -> Log.e(TAG, "Could not write to file $fileName", e) }
        .isSuccess

/*
 * Reads the first line of the given file
 * @return String content on success, null on failure
 */
fun readLine(fileName: String): String? =
    runCatching { File(fileName).readText().trim() }
        .onFailure { e -> Log.e(TAG, "Could not read file $fileName", e) }
        .getOrNull()
