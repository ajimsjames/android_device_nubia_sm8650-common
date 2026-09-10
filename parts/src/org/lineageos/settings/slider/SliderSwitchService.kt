/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.slider

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class SliderSwitchService : Service() {

    private val TAG = "SliderSwitchService"
    private var isRunning = false
    private var sliderThread: Thread? = null
    private var lastState = -1

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startSliderReader()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        sliderThread?.interrupt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val KEY_RED = 0x18e   // 398
    private val KEY_GREEN = 0x18f // 399

    private fun startSliderReader() {
        val sliderDevice = findSliderDevice() ?: "/dev/input/event0"
        Log.i(TAG, "Starting SliderSwitchReader on $sliderDevice")
        sliderThread = thread(start = true, name = "SliderSwitchReader") {
            readSliderDevice(sliderDevice)
        }
    }

    private fun findSliderDevice(): String? {
        try {
            val inputDir = File("/sys/class/input")
            val eventDirs = inputDir.listFiles { file -> file.name.startsWith("event") } ?: return null

            for (eventDir in eventDirs) {
                val nameFile = File(eventDir, "device/name")
                if (nameFile.exists()) {
                    val name = nameFile.readText().trim()
                    if (name.contains("gpio-keys_nubia", ignoreCase = true) ||
                        name.contains("slider", ignoreCase = true) ||
                        name.contains("nubia_switch", ignoreCase = true) ||
                        name.contains("hall", ignoreCase = true)) {
                        return "/dev/input/${eventDir.name}"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding slider input device", e)
        }
        return null
    }

    private fun readSliderDevice(devicePath: String) {
        val file = File(devicePath)
        if (!file.exists()) {
            Log.w(TAG, "Slider device $devicePath does not exist")
            return
        }

        val buffer = ByteArray(24)
        val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

        try {
            FileInputStream(file).use { fis ->
                Log.i(TAG, "Listening to slider switch events on $devicePath")
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val bytesRead = fis.read(buffer)
                    if (bytesRead >= 24) {
                        byteBuffer.position(16)
                        val type = byteBuffer.short.toInt()
                        val code = byteBuffer.short.toInt()
                        val value = byteBuffer.int

                        // EV_KEY (1) or EV_SW (5)
                        if (type == 1) {
                            if (code == KEY_RED) {
                                val isCompetitiveOn = value == 1
                                if (lastState != (if (isCompetitiveOn) 1 else 0)) {
                                    lastState = if (isCompetitiveOn) 1 else 0
                                    Log.i(TAG, "Slider KEY_RED event: value=$value -> ON=$isCompetitiveOn")
                                    SliderController.onSliderToggled(this@SliderSwitchService, isCompetitiveOn)
                                }
                            } else if (code == KEY_GREEN) {
                                val isCompetitiveOn = value == 0
                                if (lastState != (if (isCompetitiveOn) 1 else 0)) {
                                    lastState = if (isCompetitiveOn) 1 else 0
                                    Log.i(TAG, "Slider KEY_GREEN event: value=$value -> ON=$isCompetitiveOn")
                                    SliderController.onSliderToggled(this@SliderSwitchService, isCompetitiveOn)
                                }
                            }
                        } else if (type == 5) {
                            // SW_LID / SW_GAME switch
                            val isCompetitiveOn = value > 0
                            if (lastState != value) {
                                lastState = value
                                Log.i(TAG, "Slider EV_SW event: value=$value -> ON=$isCompetitiveOn")
                                SliderController.onSliderToggled(this@SliderSwitchService, isCompetitiveOn)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading slider device $devicePath", e)
        }
    }
}

