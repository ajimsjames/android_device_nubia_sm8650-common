/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.io.File

class SmartFanService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            updateFanState()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            handler.post(monitorRunnable)
            Log.d(TAG, "SmartFanService started")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        Log.d(TAG, "SmartFanService stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateFanState() {
        val temp = getHighestTemp()
        if (temp <= 0) return
        val context = applicationContext
        val t1 = org.lineageos.settings.utils.getInt(context, FanController.KEY_SMART_FAN_TEMP_SPEED_1, FanController.DEFAULT_TEMP_SPEED_1) * 1000
        val t2 = org.lineageos.settings.utils.getInt(context, FanController.KEY_SMART_FAN_TEMP_SPEED_2, FanController.DEFAULT_TEMP_SPEED_2) * 1000
        val t3 = org.lineageos.settings.utils.getInt(context, FanController.KEY_SMART_FAN_TEMP_SPEED_3, FanController.DEFAULT_TEMP_SPEED_3) * 1000
        val t4 = org.lineageos.settings.utils.getInt(context, FanController.KEY_SMART_FAN_TEMP_SPEED_4, FanController.DEFAULT_TEMP_SPEED_4) * 1000
        val t5 = org.lineageos.settings.utils.getInt(context, FanController.KEY_SMART_FAN_TEMP_SPEED_5, FanController.DEFAULT_TEMP_SPEED_5) * 1000

        val targetSpeed = when {
            temp >= t5 -> 5
            temp >= t4 -> 4
            temp >= t3 -> 3
            temp >= t2 -> 2
            temp >= t1 -> 1
            else -> 0
        }

        if (targetSpeed == 0) {
            FanController.writeRawFanEnabled(false)
        } else {
            FanController.writeRawFanSpeed(targetSpeed)
            FanController.writeRawFanEnabled(true)
        }
    }

    private fun getHighestTemp(): Int {
        var maxTemp = 0
        for (path in THERMAL_PATHS) {
            runCatching {
                val file = File(path)
                if (file.exists()) {
                    val value = file.readText().trim().toIntOrNull() ?: 0
                    if (value > maxTemp) {
                        maxTemp = value
                    }
                }
            }
        }
        return maxTemp
    }

    companion object {
        private const val TAG = "SmartFanService"
        private const val POLL_INTERVAL_MS = 5000L

        private val THERMAL_PATHS = arrayOf(
            "/sys/class/thermal/thermal_zone55/temp", // skin-msm-therm
            "/sys/class/thermal/thermal_zone10/temp", // cpuss-0
            "/sys/class/thermal/thermal_zone14/temp", // cpu-2-0-0
            "/sys/class/thermal/thermal_zone74/temp"  // battery
        )

        fun startService(context: Context) {
            val intent = Intent(context, SmartFanService::class.java)
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SmartFanService::class.java)
            context.stopService(intent)
        }
    }
}
