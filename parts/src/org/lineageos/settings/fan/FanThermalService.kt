/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.lineageos.settings.power.PowerProfileController

class FanThermalService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastSpeed = -1

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                evaluateFanSpeed()
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                evaluateFanSpeed()
                handler.postDelayed(this, 2000) // Poll every 2 seconds
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        handler.post(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun evaluateFanSpeed() {
        // If Diablo mode is active, do not override Level 5
        val powerProfile = PowerProfileController.getProfile(this)
        if (powerProfile == PowerProfileController.PROFILE_DIABLO) {
            FanController.setFanSpeedRaw(5)
            return
        }

        if (!FanController.isFanEnabled(this) || !FanController.isAutoMode(this)) {
            return
        }

        val cpuTemp = FanController.getCpuTemp()
        val battTemp = FanController.getBatteryTemp(this)
        // Highest heat metric determines fan cooling
        val effectiveTemp = maxOf(cpuTemp, battTemp + 6f)

        val profile = FanController.getProfile(this)
        val calculatedSpeed = when (profile) {
            FanController.PROFILE_QUIET -> {
                when {
                    effectiveTemp < 45f -> 1
                    effectiveTemp < 50f -> 2
                    effectiveTemp < 56f -> 3
                    effectiveTemp < 62f -> 4
                    else -> 5
                }
            }
            FanController.PROFILE_EXTREME -> {
                when {
                    effectiveTemp < 40f -> 2
                    effectiveTemp < 45f -> 3
                    effectiveTemp < 50f -> 4
                    else -> 5
                }
            }
            else -> { // PROFILE_BALANCED
                when {
                    effectiveTemp < 42f -> 1
                    effectiveTemp < 47f -> 2
                    effectiveTemp < 52f -> 3
                    effectiveTemp < 58f -> 4
                    else -> 5
                }
            }
        }

        if (calculatedSpeed != lastSpeed) {
            lastSpeed = calculatedSpeed
            FanController.setFanSpeedRaw(calculatedSpeed)
        }
    }
}
