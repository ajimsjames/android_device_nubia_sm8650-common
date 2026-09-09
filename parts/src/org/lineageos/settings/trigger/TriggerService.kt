/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class TriggerService : Service() {

    private val TAG = "TriggerService"
    private var isRunning = false

    private var leftThread: Thread? = null
    private var rightThread: Thread? = null

    private var leftRapidThread: Thread? = null
    private var rightRapidThread: Thread? = null
    @Volatile private var isLeftPressed = false
    @Volatile private var isRightPressed = false

    private var activityManager: ActivityManager? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.i(TAG, "TriggerService started")
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        TriggerFeedbackManager.init(this)
        TriggerHapticManager.init(this)
        TouchInjector.init(this)
        startTriggerReaders()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isLeftPressed = false
        isRightPressed = false
        leftThread?.interrupt()
        rightThread?.interrupt()
        leftRapidThread?.interrupt()
        rightRapidThread?.interrupt()
        TriggerFeedbackManager.cleanup()
        Log.i(TAG, "TriggerService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTriggerReaders() {
        val sar0Event = findInputEventDevice("sar0") ?: "/dev/input/event7"
        val sar1Event = findInputEventDevice("sar1") ?: "/dev/input/event6"

        Log.i(TAG, "Binding Left Trigger (SAR0) to $sar0Event")
        Log.i(TAG, "Binding Right Trigger (SAR1) to $sar1Event")

        leftThread = thread(start = true, name = "TriggerReader-Left") {
            readInputDevice(sar0Event, isLeft = true)
        }

        rightThread = thread(start = true, name = "TriggerReader-Right") {
            readInputDevice(sar1Event, isLeft = false)
        }
    }

    private fun findInputEventDevice(targetSubstring: String): String? {
        try {
            val inputDir = File("/sys/class/input")
            val eventDirs = inputDir.listFiles { file -> file.name.startsWith("event") }
            if (eventDirs != null) {
                for (eventDir in eventDirs) {
                    val nameFile = File(eventDir, "device/name")
                    if (nameFile.exists()) {
                        val name = nameFile.readText().trim()
                        if (name.contains(targetSubstring, ignoreCase = true)) {
                            return "/dev/input/${eventDir.name}"
                        }
                    }
                }
            }

            for (i in 0..15) {
                val nameFile = File("/sys/class/input/input$i/name")
                if (nameFile.exists()) {
                    val name = nameFile.readText().trim()
                    if (name.contains(targetSubstring, ignoreCase = true)) {
                        val eventFile = File("/sys/class/input/input$i")
                        val eventSub = eventFile.listFiles { f -> f.name.startsWith("event") }
                        if (!eventSub.isNullOrEmpty()) {
                            return "/dev/input/${eventSub[0].name}"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering input devices for $targetSubstring", e)
        }
        return null
    }

    private fun getTopPackageName(): String? {
        try {
            val am = activityManager ?: return null
            val tasks = am.getRunningTasks(1)
            if (!tasks.isNullOrEmpty()) {
                return tasks[0].topActivity?.packageName
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun readInputDevice(devicePath: String, isLeft: Boolean) {
        val file = File(devicePath)
        if (!file.exists()) {
            Log.w(TAG, "Input device $devicePath does not exist")
            return
        }

        val buffer = ByteArray(24) // struct input_event
        val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

        try {
            FileInputStream(file).use { fis ->
                Log.i(TAG, "Listening to ${if (isLeft) "Left (L1)" else "Right (R1)"} on $devicePath")
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val bytesRead = fis.read(buffer)
                    if (bytesRead >= 24) {
                        byteBuffer.position(16)
                        val type = byteBuffer.short.toInt()
                        val code = byteBuffer.short.toInt()
                        val value = byteBuffer.int

                        // EV_KEY (1) or EV_ABS (3)
                        if (type == 1 || type == 3) {
                            val isPressed = value > 0

                            val topPackage = getTopPackageName()
                            val isAllowed = TriggerController.isAppAllowed(this@TriggerService, topPackage)
                            if (isAllowed) {
                                handleTriggerState(isLeft, isPressed)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading $devicePath", e)
        }
    }

    private fun handleTriggerState(isLeft: Boolean, pressed: Boolean) {
        if (isLeft) {
            if (isLeftPressed == pressed) return
            isLeftPressed = pressed
        } else {
            if (isRightPressed == pressed) return
            isRightPressed = pressed
        }

        val mode = if (isLeft) TriggerController.getLeftMode(this) else TriggerController.getRightMode(this)
        val coords = if (isLeft) TriggerController.getLeftCoordinates(this) else TriggerController.getRightCoordinates(this)
        val x = coords.first.toFloat()
        val y = coords.second.toFloat()
        val cps = if (isLeft) TriggerController.getLeftCps(this) else TriggerController.getRightCps(this)

        Log.i(TAG, "Trigger ${if (isLeft) "L1" else "R1"} -> pressed: $pressed at ($x, $y) mode: $mode")

        if (pressed) {
            TriggerFeedbackManager.showFeedback(isLeft)
            TriggerHapticManager.performTriggerClick(this)
        }

        when (mode) {
            TriggerController.MODE_NORMAL, TriggerController.MODE_HOLD -> {
                if (pressed) {
                    TouchInjector.injectDown(x, y, isLeft)
                } else {
                    TouchInjector.injectUp(x, y, isLeft)
                }
            }
            TriggerController.MODE_RAPID_FIRE -> {
                if (pressed) {
                    startRapidFire(isLeft, x, y, cps)
                } else {
                    stopRapidFire(isLeft)
                }
            }
            TriggerController.MODE_DUAL_ACTION -> {
                TouchInjector.injectTap(x, y, isLeft)
            }
        }
    }

    private fun startRapidFire(isLeft: Boolean, x: Float, y: Float, cps: Int) {
        val safeCps = cps.coerceIn(2, 40)
        val intervalMs = (1000L / safeCps).coerceAtLeast(15L)
        val halfInterval = (intervalMs / 2).coerceAtLeast(8L)

        val rapidThread = thread(start = true, name = if (isLeft) "Rapid-Left" else "Rapid-Right") {
            while (isRunning && (if (isLeft) isLeftPressed else isRightPressed)) {
                TriggerFeedbackManager.showFeedback(isLeft)
                TriggerHapticManager.performTriggerClick(this@TriggerService)
                TouchInjector.injectDown(x, y, isLeft)
                try {
                    Thread.sleep(halfInterval)
                } catch (e: InterruptedException) {
                    break
                }
                TouchInjector.injectUp(x, y, isLeft)
                try {
                    Thread.sleep(halfInterval)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        if (isLeft) {
            leftRapidThread?.interrupt()
            leftRapidThread = rapidThread
        } else {
            rightRapidThread?.interrupt()
            rightRapidThread = rapidThread
        }
    }

    private fun stopRapidFire(isLeft: Boolean) {
        if (isLeft) {
            leftRapidThread?.interrupt()
            leftRapidThread = null
        } else {
            rightRapidThread?.interrupt()
            rightRapidThread = null
        }
    }
}
