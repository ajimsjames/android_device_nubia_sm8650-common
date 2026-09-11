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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.lineageos.settings.fan.FanController
import org.lineageos.settings.power.PowerProfileController
import org.lineageos.settings.utils.FileUtils
import org.lineageos.settings.utils.SettingsUtils

class GamingHudOverlayService : Service() {

    companion object {
        const val KEY_HUD_SHOW_FPS = "hud_show_fps"
        const val KEY_HUD_SHOW_TEMPS = "hud_show_temps"
        const val KEY_HUD_SHOW_CPU = "hud_show_cpu"
        const val KEY_HUD_SHOW_GPU = "hud_show_gpu"
        const val KEY_HUD_SHOW_FAN = "hud_show_fan"
        const val KEY_HUD_SHOW_POWER = "hud_show_power"
        const val KEY_HUD_SHOW_RAM = "hud_show_ram"
        const val KEY_HUD_SHOW_PROFILE = "hud_show_profile"
    }

    private var windowManager: WindowManager? = null
    private var hudView: LinearLayout? = null

    private var tvFps: TextView? = null
    private var tvSocTemp: TextView? = null
    private var tvCpuFreq: TextView? = null
    private var tvGpuLoad: TextView? = null
    private var tvFanSpeed: TextView? = null
    private var tvPower: TextView? = null
    private var tvRam: TextView? = null
    private var tvProfile: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bgThread: android.os.HandlerThread? = null
    private var bgHandler: Handler? = null
    private var isRunning = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            updateStats()
            bgHandler?.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createHudView()
        isRunning = true
        bgThread = android.os.HandlerThread("GamingHudThread").apply { start() }
        bgHandler = Handler(bgThread!!.looper)
        bgHandler?.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        bgHandler?.removeCallbacks(updateRunnable)
        bgThread?.quitSafely()
        hudView?.let { windowManager?.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun createHudView() {
        val wm = windowManager ?: return

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.argb(130, 8, 8, 12)) // Clean 50% translucent glassmorphism
            setStroke(2, Color.argb(140, 230, 30, 45)) // RedMagic Crimson border
        }

        hudView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(22, 14, 22, 14)
            elevation = 16f
        }

        val headerTv = TextView(this).apply {
            text = "⚡ REDMAGIC STATS"
            textSize = 10f
            setTextColor(Color.parseColor("#FF5252"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        hudView?.addView(headerTv)

        tvFps = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#00E676"))
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        hudView?.addView(tvFps)

        tvSocTemp = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        hudView?.addView(tvSocTemp)

        tvCpuFreq = TextView(this).apply {
            textSize = 10.5f
            setTextColor(Color.parseColor("#EEEEEE"))
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        hudView?.addView(tvCpuFreq)

        tvGpuLoad = TextView(this).apply {
            textSize = 10.5f
            setTextColor(Color.parseColor("#00E5FF"))
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        hudView?.addView(tvGpuLoad)

        tvFanSpeed = TextView(this).apply {
            textSize = 10.5f
            setTextColor(Color.parseColor("#76FF03"))
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        hudView?.addView(tvFanSpeed)

        tvPower = TextView(this).apply {
            textSize = 10.5f
            setTextColor(Color.parseColor("#FFAB00"))
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        hudView?.addView(tvPower)

        tvRam = TextView(this).apply {
            textSize = 10.5f
            setTextColor(Color.parseColor("#B388FF"))
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        hudView?.addView(tvRam)

        tvProfile = TextView(this).apply {
            textSize = 10.5f
            setTextColor(Color.parseColor("#FFD600"))
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
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
            x = 40
            y = 100
        }

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
        val showFps = SettingsUtils.getInt(this, KEY_HUD_SHOW_FPS, 1) == 1
        val showTemps = SettingsUtils.getInt(this, KEY_HUD_SHOW_TEMPS, 1) == 1
        val showCpu = SettingsUtils.getInt(this, KEY_HUD_SHOW_CPU, 1) == 1
        val showGpu = SettingsUtils.getInt(this, KEY_HUD_SHOW_GPU, 1) == 1
        val showFan = SettingsUtils.getInt(this, KEY_HUD_SHOW_FAN, 1) == 1
        val showPower = SettingsUtils.getInt(this, KEY_HUD_SHOW_POWER, 1) == 1
        val showRam = SettingsUtils.getInt(this, KEY_HUD_SHOW_RAM, 1) == 1
        val showProfile = SettingsUtils.getInt(this, KEY_HUD_SHOW_PROFILE, 1) == 1

        // 1. Real-time Panel FPS
        val fpsText = if (showFps) {
            val fpsRaw = FileUtils.readOneLine("/sys/class/drm/sde-crtc-0/measured_fps")?.trim()
            val fpsVal = if (!fpsRaw.isNullOrBlank()) {
                val parts = fpsRaw.split(":")
                if (parts.size >= 2) parts[1].trim() else fpsRaw
            } else "120.0"
            "🎯 FPS: $fpsVal"
        } else null

        // 2. SoC Temp & Battery Temp
        val tempsText = if (showTemps) {
            val socTempRaw = FileUtils.readOneLine("/sys/class/thermal/thermal_zone10/temp")?.toIntOrNull() ?: 0
            val socTemp = socTempRaw / 1000
            val battTempRaw = FileUtils.readOneLine("/sys/class/power_supply/battery/temp")?.toIntOrNull() ?: 0
            val battTemp = battTempRaw / 10.0
            "🔥 SoC: ${socTemp}°C | Batt: ${String.format("%.1f", battTemp)}°C"
        } else null

        // 3. CPU Prime Core (cpu7) & Titanium Core (cpu5)
        val cpuText = if (showCpu) {
            val p7Freq = (FileUtils.readOneLine("/sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq")?.toIntOrNull() ?: 0) / 1000
            val p5Freq = (FileUtils.readOneLine("/sys/devices/system/cpu/cpu5/cpufreq/scaling_cur_freq")?.toIntOrNull() ?: 0) / 1000
            "⚡ X4: ${p7Freq}MHz | A720: ${p5Freq}MHz"
        } else null

        // 4. GPU Busy Percentage & Clock Frequency
        val gpuText = if (showGpu) {
            val gpuBusy = FileUtils.readOneLine("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")?.trim() ?: "0"
            val gpuClockMhz = FileUtils.readOneLine("/sys/class/kgsl/kgsl-3d0/clock_mhz")?.trim()
                ?: ((FileUtils.readOneLine("/sys/class/kgsl/kgsl-3d0/gpuclk")?.toLongOrNull() ?: 0L) / 1_000_000).toString()
            val gpuPwrLevel = FileUtils.readOneLine("/sys/class/kgsl/kgsl-3d0/min_pwrlevel")?.trim() ?: "0"
            "🎮 GPU: ${gpuClockMhz}MHz ($gpuBusy) | Lv $gpuPwrLevel"
        } else null

        // 5. Fan RPM & Speed Level
        val fanText = if (showFan) {
            val fanSpeed = FanController.getFanSpeed(this)
            val fanRpm = FanController.getFanRpm()
            val fanEnabled = FanController.isFanEnabled(this)
            if (fanEnabled) "❄️ Fan: $fanRpm RPM (Lv $fanSpeed)" else "❄️ Fan: OFF"
        } else null

        // 6. Live Battery Wattage & Power Draw
        val powerText = if (showPower) {
            val voltUv = FileUtils.readOneLine("/sys/class/power_supply/battery/voltage_now")?.toLongOrNull() ?: 0L
            val currUa = FileUtils.readOneLine("/sys/class/power_supply/battery/current_now")?.toLongOrNull() ?: 0L
            val powerWatts = (Math.abs(voltUv * currUa)) / 1_000_000_000_000.0
            val isCharging = currUa > 0
            if (powerWatts < 0.1) {
                "⚡ Power: 0.0W (Bypass Active)"
            } else if (isCharging) {
                "⚡ Power: +${String.format("%.1f", powerWatts)}W (Charging)"
            } else {
                "⚡ Power: -${String.format("%.1f", powerWatts)}W (Discharge)"
            }
        } else null

        // 7. RAM Usage / Free Memory
        val ramText = if (showRam) {
            val (usedGb, totalGb, freePct) = readRamStats()
            "🧠 RAM: ${String.format("%.1f", usedGb)}GB / ${totalGb}GB (${freePct}% Free)"
        } else null

        // 8. Current Performance Profile
        val profileText = if (showProfile) {
            val profileName = when (PowerProfileController.getProfile(this)) {
                PowerProfileController.PROFILE_DIABLO -> "DIABLO MAX"
                PowerProfileController.PROFILE_PERFORMANCE -> "PERFORMANCE"
                PowerProfileController.PROFILE_BATTERY_SAVER -> "BATTERY SAVER"
                else -> "BALANCED"
            }
            "🚀 Profile: $profileName"
        } else null

        mainHandler.post {
            tvFps?.text = fpsText
            tvFps?.visibility = if (fpsText != null) View.VISIBLE else View.GONE

            tvSocTemp?.text = tempsText
            tvSocTemp?.visibility = if (tempsText != null) View.VISIBLE else View.GONE

            tvCpuFreq?.text = cpuText
            tvCpuFreq?.visibility = if (cpuText != null) View.VISIBLE else View.GONE

            tvGpuLoad?.text = gpuText
            tvGpuLoad?.visibility = if (gpuText != null) View.VISIBLE else View.GONE

            tvFanSpeed?.text = fanText
            tvFanSpeed?.visibility = if (fanText != null) View.VISIBLE else View.GONE

            tvPower?.text = powerText
            tvPower?.visibility = if (powerText != null) View.VISIBLE else View.GONE

            tvRam?.text = ramText
            tvRam?.visibility = if (ramText != null) View.VISIBLE else View.GONE

            tvProfile?.text = profileText
            tvProfile?.visibility = if (profileText != null) View.VISIBLE else View.GONE
        }
    }

    private fun readRamStats(): Triple<Double, Int, Int> {
        var totalKb = 16_000_000L
        var availKb = 8_000_000L
        try {
            java.io.File("/proc/meminfo").forEachLine { line ->
                if (line.startsWith("MemTotal:")) {
                    totalKb = line.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: totalKb
                } else if (line.startsWith("MemAvailable:")) {
                    availKb = line.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: availKb
                }
            }
        } catch (ignored: Exception) {}

        val totalGb = Math.round(totalKb / 1024.0 / 1024.0).toInt()
        val usedGb = (totalKb - availKb) / 1024.0 / 1024.0
        val freePct = ((availKb * 100) / totalKb).toInt()
        return Triple(usedGb, totalGb, freePct)
    }
}
