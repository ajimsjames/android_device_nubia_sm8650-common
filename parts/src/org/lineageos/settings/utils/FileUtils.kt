/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.utils

import android.util.Log
import java.io.File

private const val TAG = "FileUtils"

object FileUtils {
    fun fileExists(fileName: String): Boolean {
        if (File(fileName).exists()) return true
        return runRootCommand("test -e $fileName") == 0
    }

    fun readOneLine(fileName: String): String? {
        val direct = runCatching { File(fileName).readText().trim() }.getOrNull()
        if (!direct.isNullOrEmpty()) return direct

        return runRootCommandOutput("cat $fileName 2>/dev/null")?.trim()
    }

    fun writeLine(fileName: String, value: String): Boolean {
        val directSuccess = runCatching {
            File(fileName).writeText(value)
            true
        }.getOrDefault(false)

        if (directSuccess) return true

        val code = runRootCommand("echo '$value' > $fileName")
        return code == 0
    }

    fun runRootCommand(cmd: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "Root command failed: $cmd", e)
            -1
        }
    }

    fun runRootCommandOutput(cmd: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.trim().ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "Root command output failed: $cmd", e)
            null
        }
    }
}

fun fileExists(fileName: String): Boolean = FileUtils.fileExists(fileName)
fun readOneLine(fileName: String): String? = FileUtils.readOneLine(fileName)
fun writeLine(fileName: String, value: String): Boolean = FileUtils.writeLine(fileName, value)
