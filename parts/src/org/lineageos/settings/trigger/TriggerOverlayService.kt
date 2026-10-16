/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class TriggerOverlayService : Service() {

    private val TAG = "TriggerOverlayService"

    private var mWindowManager: WindowManager? = null
    private var mL1View: View? = null
    private var mR1View: View? = null
    private var mCloseView: View? = null

    private var mL1Params: WindowManager.LayoutParams? = null
    private var mR1Params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayViews()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayViews() {
        val wm = mWindowManager ?: return
        val display = wm.defaultDisplay
        val size = android.graphics.Point()
        display.getRealSize(size)
        val screenWidth = size.x
        val screenHeight = size.y

        val l1XPercent = TriggerController.getTriggerCoord(this, TriggerController.KEY_TRIGGER_L1_POS_X, TriggerController.DEFAULT_L1_POS_X)
        val l1YPercent = TriggerController.getTriggerCoord(this, TriggerController.KEY_TRIGGER_L1_POS_Y, TriggerController.DEFAULT_L1_POS_Y)
        val r1XPercent = TriggerController.getTriggerCoord(this, TriggerController.KEY_TRIGGER_R1_POS_X, TriggerController.DEFAULT_R1_POS_X)
        val r1YPercent = TriggerController.getTriggerCoord(this, TriggerController.KEY_TRIGGER_R1_POS_Y, TriggerController.DEFAULT_R1_POS_Y)

        val l1View = createButtonView("L1", Color.argb(220, 0, 140, 255), Color.argb(240, 0, 80, 220))
        val r1View = createButtonView("R1", Color.argb(220, 255, 60, 60), Color.argb(240, 200, 30, 30))

        val l1P = createLayoutParams(
            (l1XPercent * screenWidth) / 100,
            (l1YPercent * screenHeight) / 100
        )
        val r1P = createLayoutParams(
            (r1XPercent * screenWidth) / 100,
            (r1YPercent * screenHeight) / 100
        )

        setupDrag(l1View, l1P, TriggerController.KEY_TRIGGER_L1_POS_X, TriggerController.KEY_TRIGGER_L1_POS_Y)
        setupDrag(r1View, r1P, TriggerController.KEY_TRIGGER_R1_POS_X, TriggerController.KEY_TRIGGER_R1_POS_Y)

        mL1View = l1View
        mR1View = r1View
        mL1Params = l1P
        mR1Params = r1P

        try {
            wm.addView(mL1View, l1P)
            wm.addView(mR1View, r1P)
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding trigger views", e)
        }

        // Close pill button
        val closeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 60
        }

        val closeBtn = TextView(this).apply {
            text = "✕ Save & Close Triggers"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            val bg = GradientDrawable().apply {
                setColor(Color.argb(230, 15, 15, 15))
                cornerRadius = 40f
                setStroke(3, Color.argb(200, 255, 255, 255))
            }
            background = bg
            setPadding(45, 22, 45, 22)
            elevation = 20f
            setOnClickListener {
                stopSelf()
            }
        }
        mCloseView = closeBtn
        try {
            wm.addView(mCloseView, closeParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding close view", e)
        }
    }

    private fun createButtonView(label: String, circleColor: Int, innerColor: Int): View {
        val sizePx = (resources.displayMetrics.density * 76).toInt()
        val frame = FrameLayout(this)
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(circleColor, innerColor)
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(5, Color.WHITE)
        }
        frame.background = bg
        frame.elevation = 25f

        val tv = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        frame.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return frame
    }

    private fun createLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        val sizePx = (resources.displayMetrics.density * 76).toInt()
        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x - sizePx / 2
            this.y = y - sizePx / 2
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(
        view: View,
        params: WindowManager.LayoutParams,
        prefKeyX: String,
        prefKeyY: String
    ) {
        val wm = mWindowManager ?: return

        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val display = wm.defaultDisplay
                val size = android.graphics.Point()
                display.getRealSize(size)
                val screenWidth = size.x
                val screenHeight = size.y

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
                        wm.updateViewLayout(view, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val centerX = params.x + view.width / 2
                        val centerY = params.y + view.height / 2
                        val percentX = ((centerX.toFloat() / screenWidth) * 100).toInt().coerceIn(0, 100)
                        val percentY = ((centerY.toFloat() / screenHeight) * 100).toInt().coerceIn(0, 100)
                        val wm = mWindowManager
                        val display = wm?.defaultDisplay
                        val rotation = display?.rotation ?: 0
                        TriggerController.setTriggerCoord(this@TriggerOverlayService, prefKeyX, percentX)
                        TriggerController.setTriggerCoord(this@TriggerOverlayService, prefKeyY, percentY)
                        android.provider.Settings.Global.putInt(
                            this@TriggerOverlayService.contentResolver,
                            "nubia_parts_trigger_rotation",
                            rotation
                        )
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mWindowManager?.let { wm ->
            mL1View?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            mR1View?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            mCloseView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        }
    }
}
