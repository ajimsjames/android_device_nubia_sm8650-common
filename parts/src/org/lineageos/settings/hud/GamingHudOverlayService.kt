/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.hud

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.lineageos.settings.fan.FanController
import org.lineageos.settings.power.PowerProfileController
import org.lineageos.settings.utils.FileUtils
import java.io.File

class GamingHudOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var hudView: LinearLayout? = null
    private var tvSocTemp: TextView? = null
    private var tvCpuFreq: TextView? = null
    private var tvGpuLoad: TextView? = null
    private var tvFanSpeed: TextView? = null
    private var tvProfile: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            updateStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createHudView()
        isRunning = true
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        hudView?.let { windowManager?.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun createHudView() {
        val wm = windowManager ?: return

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(Color.argb(215, 12, 12, 18))
            setStroke(3, Color.argb(180, 230, 30, 45)) // RedMagic Crimson accent
        }

        hudView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(28, 20, 28, 20)
            elevation = 20f
        }

        val headerTv = TextView(this).apply {
            text = "⚡ REDMAGIC STATS"
            textSize = 11f
            setTextColor(Color.parseColor("#FF4444"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        hudView?.addView(headerTv)

        tvSocTemp = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            text = "🔥 SoC: --°C | Batt: --°C"
        }
        hudView?.addView(tvSocTemp)

        tvCpuFreq = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#CCCCCC"))
            text = "⚡ CPU: X4 --GHz | A720 --GHz"
        }
        hudView?.addView(tvCpuFreq)

        tvGpuLoad = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#00E5FF"))
            text = "🎮 GPU: --% | Level 0"
        }
        hudView?.addView(tvGpuLoad)

        tvFanSpeed = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#76FF03"))
            text = "❄️ Fan: -- RPM (Level --)"
        }
        hudView?.addView(tvFanSpeed)

        tvProfile = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#FFD600"))
            text = "🚀 Profile: Balanced"
        }
        hudView?.addView(tvProfile)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 120
        }

        // Make draggable anywhere on screen
        hudView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        wm.updateViewLayout(hudView, params)
                        return true
                    }
                }
                return false
            }
        })

        wm.addView(hudView, params)
    }

    private fun updateStats() {
        // 1. SoC Temp & Battery Temp
        val socTempRaw = FileUtils.readOneLine("/sys/class/thermal/thermal_zone10/temp")?.toIntOrNull() ?: 0
        val socTemp = socTempRaw / 1000
        val battTempRaw = FileUtils.readOneLine("/sys/class/power_supply/battery/temp")?.toIntOrNull() ?: 0
        val battTemp = battTempRaw / 10.0
        tvSocTemp?.text = "🔥 SoC: ${socTemp}°C | Batt: ${String.format("%.1f", battTemp)}°C"

        // 2. CPU Prime Core (cpu7) & Titanium Core (cpu5)
        val p7Freq = (FileUtils.readOneLine("/sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq")?.toIntOrNull() ?: 0) / 1000
        val p5Freq = (FileUtils.readOneLine("/sys/devices/system/cpu/cpu5/cpufreq/scaling_cur_freq")?.toIntOrNull() ?: 0) / 1000
        tvCpuFreq?.text = "⚡ X4: ${p7Freq}MHz | A720: ${p5Freq}MHz"

        // 3. GPU Busy Percentage
        val gpuBusy = FileUtils.readOneLine("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")?.trim() ?: "0"
        val gpuPwrLevel = FileUtils.readOneLine("/sys/class/kgsl/kgsl-3d0/cur_pwrlevel")?.trim() ?: "0"
        tvGpuLoad?.text = "🎮 GPU: $gpuBusy | Rail Lv: $gpuPwrLevel"

        // 4. Fan RPM & Speed Level
        val fanSpeed = FanController.getCurrentSpeed(this)
        val fanRpm = FanController.getFanRpm()
        val fanEnabled = FanController.isFanEnabled(this)
        tvFanSpeed?.text = if (fanEnabled) "❄️ Fan: $fanRpm RPM (Lv $fanSpeed)" else "❄️ Fan: OFF"

        // 5. Current Performance Profile
        val profileName = when (PowerProfileController.getProfile(this)) {
            PowerProfileController.PROFILE_DIABLO -> "DIABLO MAX"
            PowerProfileController.PROFILE_PERFORMANCE -> "PERFORMANCE"
            PowerProfileController.PROFILE_BATTERY_SAVER -> "BATTERY SAVER"
            else -> "BALANCED"
        }
        tvProfile?.text = "🚀 Profile: $profileName"
    }
}
