/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.crosshair

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.lineageos.settings.utils.SettingsUtils

class CrosshairOverlayService : Service() {

    companion object {
        const val KEY_CROSSHAIR_ENABLE = "crosshair_overlay_enable"
        const val KEY_CROSSHAIR_STYLE = "crosshair_style"
        const val KEY_CROSSHAIR_COLOR = "crosshair_color"
        const val KEY_CROSSHAIR_SIZE = "crosshair_size"
        const val KEY_CROSSHAIR_ZOOM = "crosshair_zoom_level"

        const val STYLE_CLASSIC_CROSS = 0
        const val STYLE_DOT = 1
        const val STYLE_CIRCLE_DOT = 2
        const val STYLE_CHEVRON = 3
    }

    private var windowManager: WindowManager? = null
    private var crosshairView: CrosshairView? = null
    private var zoomControlView: LinearLayout? = null

    private var screenWidth = 1116
    private var screenHeight = 2480

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            updateCrosshairViewLayout()
            crosshairView?.updateSettings()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()

        createCrosshairView()
        createZoomControlView()

        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_$KEY_CROSSHAIR_STYLE"),
            false,
            settingsObserver
        )
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_$KEY_CROSSHAIR_COLOR"),
            false,
            settingsObserver
        )
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_$KEY_CROSSHAIR_SIZE"),
            false,
            settingsObserver
        )
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_$KEY_CROSSHAIR_ZOOM"),
            false,
            settingsObserver
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(settingsObserver)
        crosshairView?.let { windowManager?.removeView(it) }
        zoomControlView?.let { windowManager?.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateScreenDimensions() {
        val wm = windowManager ?: return
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
    }

    private fun updateCrosshairViewLayout() {
        val wm = windowManager ?: return
        val view = crosshairView ?: return
        val sizeDp = SettingsUtils.getInt(this, KEY_CROSSHAIR_SIZE, 60)
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val params = (view.layoutParams as? WindowManager.LayoutParams) ?: WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        params.width = sizePx
        params.height = sizePx
        wm.updateViewLayout(view, params)
    }

    private fun createCrosshairView() {
        val wm = windowManager ?: return
        crosshairView = CrosshairView(this)

        val sizeDp = SettingsUtils.getInt(this, KEY_CROSSHAIR_SIZE, 60)
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        wm.addView(crosshairView, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createZoomControlView() {
        val wm = windowManager ?: return

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 30f
            setColor(Color.argb(190, 15, 15, 20))
            setStroke(2, Color.argb(160, 230, 30, 45))
        }

        zoomControlView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(24, 16, 24, 16)
            gravity = Gravity.CENTER
            elevation = 16f
        }

        var currentZoom = SettingsUtils.getInt(this, KEY_CROSSHAIR_ZOOM, 1)

        val tvZoom = TextView(this).apply {
            text = "🔍 Zoom: ${currentZoom}x"
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        zoomControlView?.addView(tvZoom)

        val seekBar = SeekBar(this).apply {
            max = 7 // 0 -> 1x, 7 -> 8x
            progress = (currentZoom - 1).coerceIn(0, 7)
            layoutParams = LinearLayout.LayoutParams(220, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val zoom = progress + 1
                    tvZoom.text = "🔍 Zoom: ${zoom}x"
                    SettingsUtils.putInt(this@CrosshairOverlayService, KEY_CROSSHAIR_ZOOM, zoom)
                    crosshairView?.setZoomLevel(zoom)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        zoomControlView?.addView(seekBar)

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
            x = 50
            y = 350
        }

        zoomControlView?.setOnTouchListener(object : View.OnTouchListener {
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
                        return false // allow seekbar touch
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(event.rawX - initialTouchX) > 15 || Math.abs(event.rawY - initialTouchY) > 15) {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            wm.updateViewLayout(zoomControlView, params)
                            return true
                        }
                    }
                }
                return false
            }
        })

        wm.addView(zoomControlView, params)
    }

    inner class CrosshairView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(160, 255, 255, 255)
        }

        private val lensFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private var zoomLevel = SettingsUtils.getInt(context, KEY_CROSSHAIR_ZOOM, 1)

        fun setZoomLevel(zoom: Int) {
            this.zoomLevel = zoom
            invalidate()
        }

        fun updateSettings() {
            this.zoomLevel = SettingsUtils.getInt(context, KEY_CROSSHAIR_ZOOM, 1)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val color = when (SettingsUtils.getInt(context, KEY_CROSSHAIR_COLOR, 0)) {
                1 -> Color.parseColor("#FF1744") // Crimson Red
                2 -> Color.parseColor("#00E5FF") // Cyan Blue
                3 -> Color.parseColor("#FFD600") // Amber Yellow
                4 -> Color.WHITE
                else -> Color.parseColor("#00E676") // Neon Green (Default)
            }
            paint.color = color

            val cx = width / 2f
            val cy = height / 2f
            val radius = width * 0.42f

            // Magnification Scope Ring & Optical Glass HUD effect
            if (zoomLevel > 1) {
                val glassRadius = radius * (0.6f + (zoomLevel / 14f))
                lensFillPaint.shader = RadialGradient(
                    cx, cy, glassRadius,
                    intArrayOf(Color.argb(40, 0, 229, 255), Color.argb(10, 0, 0, 0), Color.argb(120, 20, 20, 30)),
                    floatArrayOf(0f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, glassRadius, lensFillPaint)
                canvas.drawCircle(cx, cy, glassRadius, lensPaint)

                // Scope cross markers
                canvas.drawLine(cx - glassRadius, cy, cx - glassRadius + 12f, cy, lensPaint)
                canvas.drawLine(cx + glassRadius - 12f, cy, cx + glassRadius, cy, lensPaint)
                canvas.drawLine(cx, cy - glassRadius, cx, cy - glassRadius + 12f, lensPaint)
                canvas.drawLine(cx, cy + glassRadius - 12f, cx, cy + glassRadius, lensPaint)
            }

            val style = SettingsUtils.getInt(context, KEY_CROSSHAIR_STYLE, STYLE_CLASSIC_CROSS)
            when (style) {
                STYLE_DOT -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, 6f * (zoomLevel * 0.4f).coerceAtLeast(1f), paint)
                }
                STYLE_CIRCLE_DOT -> {
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, radius * 0.6f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, 5f, paint)
                }
                STYLE_CHEVRON -> {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(cx - 16, cy + 16, cx, cy, paint)
                    canvas.drawLine(cx, cy, cx + 16, cy + 16, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy - 8, 3.5f, paint)
                }
                else -> {
                    // Classic Crosshair
                    val armLength = radius * 0.75f
                    val gap = 10f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(cx - armLength, cy, cx - gap, cy, paint)
                    canvas.drawLine(cx + gap, cy, cx + armLength, cy, paint)
                    canvas.drawLine(cx, cy - armLength, cx, cy - gap, paint)
                    canvas.drawLine(cx, cy + gap, cx, cy + armLength, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, 3.5f, paint)
                }
            }
        }
    }
}
