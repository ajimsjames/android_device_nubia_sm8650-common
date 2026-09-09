/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.slider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.lineageos.settings.R
import org.lineageos.settings.fan.FanController
import org.lineageos.settings.nubia.NubiaActivity
import org.lineageos.settings.trigger.TriggerController
import org.lineageos.settings.utils.FileUtils

class DiabloNotificationService : Service() {

    private val CHANNEL_ID = "diablo_mode_status"
    private val NOTIFICATION_ID = 9999
    private var notificationManager: NotificationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                updateNotification()
                handler.postDelayed(this, 2000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        isRunning = true
        updateNotification()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val nm = notificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Diablo Mode Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live hardware status when Diablo Mode is engaged via Slider Switch"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val nm = notificationManager ?: return

        // Read live CPU clock
        val cpuFreqRaw = FileUtils.readOneLine("/sys/devices/system/cpu/cpufreq/policy7/scaling_cur_freq")
        val cpuMhz = cpuFreqRaw?.trim()?.toIntOrNull()?.let { it / 1000 } ?: 3398

        // Read live Fan RPM
        val fanRpm = FanController.getFanRpm(this)
        val fanLevel = FileUtils.readOneLine("/sys/kernel/fan/fan_speed_level")?.trim() ?: "5"

        // Read Triggers
        val triggersOn = TriggerController.isTriggerEnabled(this)
        val triggerStatus = if (triggersOn) "ON (L1/R1 Active)" else "OFF"

        // Read GPU
        val gpuPwr = FileUtils.readOneLine("/sys/class/kgsl/kgsl-3d0/max_pwrlevel")?.trim() ?: "0"
        val gpuStatus = if (gpuPwr == "0") "Turbo (1.0 GHz)" else "Level $gpuPwr"

        val contentText = "CPU: ${cpuMhz} MHz | GPU: $gpuStatus | Fan: Lvl $fanLevel ($fanRpm RPM) | Triggers: $triggerStatus"

        val intent = Intent(this, NubiaActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_power_profile)
            .setContentTitle("Diablo Mode Active 🔥")
            .setContentText(contentText)
            .setStyle(Notification.BigTextStyle().bigText(
                "⚡ CPU Clocks: ${cpuMhz} MHz (Max 3.40 GHz Turbo)\n" +
                "🎮 GPU State: $gpuStatus\n" +
                "🌪️ Cooling Fan: Level $fanLevel / 5 ($fanRpm RPM Max)\n" +
                "🎯 Shoulder Triggers: $triggerStatus"
            ))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DiabloNotificationService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DiabloNotificationService::class.java)
            context.stopService(intent)
        }
    }
}
