/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.utils

import android.util.Log
import java.io.File

private const val TAG = "FileUtils"

object FileUtils {
    fun fileExists(fileName: String): Boolean = File(fileName).exists()

    fun readOneLine(fileName: String): String? =
        runCatching { File(fileName).readText().trim() }
            .onFailure { e -> Log.e(TAG, "Could not read from file $fileName", e) }
            .getOrNull()

    fun writeLine(fileName: String, value: String): Boolean =
        runCatching { File(fileName).writeText(value) }
            .onFailure { e -> Log.e(TAG, "Could not write to file $fileName", e) }
            .isSuccess
}

fun fileExists(fileName: String): Boolean = FileUtils.fileExists(fileName)
fun readOneLine(fileName: String): String? = FileUtils.readOneLine(fileName)
fun writeLine(fileName: String, value: String): Boolean = FileUtils.writeLine(fileName, value)
