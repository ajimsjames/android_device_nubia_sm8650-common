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
        val file = File(fileName)
        if (file.exists()) return true
        // Only if file cannot be read directly due to permission, check with root test
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -e $fileName"))
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    fun readOneLine(fileName: String): String? {
        val file = File(fileName)
        if (file.exists() && file.canRead()) {
            return runCatching { file.readText().trim() }.getOrNull()
        }

        // Try direct read first
        val direct = runCatching { file.readText().trim() }.getOrNull()
        if (!direct.isNullOrEmpty()) return direct

        // If file definitely does not exist on filesystem, don't execute root cat
        if (!file.exists()) {
            // Check once if parent exists or file is accessible via su
            val parent = file.parentFile
            if (parent != null && !parent.exists() && parent.canRead()) {
                return null
            }
        }

        return runRootCommandOutput("cat $fileName 2>/dev/null")?.trim()
    }

    fun writeLine(fileName: String, value: String): Boolean {
        val file = File(fileName)
        val directSuccess = runCatching {
            file.writeText(value)
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
