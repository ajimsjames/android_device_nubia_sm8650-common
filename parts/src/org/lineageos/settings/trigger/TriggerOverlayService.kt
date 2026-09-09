/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.lineageos.settings.R

class TriggerOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var leftView: View? = null
    private var rightView: View? = null
    private var controlView: View? = null

    private var screenWidth = 1116
    private var screenHeight = 2480

    private var leftX = 300
    private var leftY = 600
    private var rightX = 800
    private var rightY = 600

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        updateScreenDimensions()

        val coordsL = TriggerController.getLeftCoordinates(this)
        val coordsR = TriggerController.getRightCoordinates(this)

        leftX = coordsL.first.coerceIn(75, screenWidth - 75)
        leftY = coordsL.second.coerceIn(75, screenHeight - 75)

        rightX = coordsR.first.coerceIn(75, screenWidth - 75)
        rightY = coordsR.second.coerceIn(75, screenHeight - 75)

        // If coordinates were out-of-bounds or overlapping, place them visibly
        if (rightX >= screenWidth - 80 || rightX <= 80 || (Math.abs(rightX - leftX) < 50 && Math.abs(rightY - leftY) < 50)) {
            rightX = (screenWidth * 0.75f).toInt()
            rightY = leftY
        }
        if (leftX <= 80 || leftX >= screenWidth - 80) {
            leftX = (screenWidth * 0.25f).toInt()
        }

        createControlBar()
        createLeftBadge()
        createRightBadge()

        Toast.makeText(this, "Drag L1 (Red) and R1 (Blue) to desired target buttons, then tap Save.", Toast.LENGTH_LONG).show()
    }

    private fun updateScreenDimensions() {
        val wm = windowManager ?: return
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
    }

    override fun onDestroy() {
        super.onDestroy()
        removeViews()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createBadgeDrawable(bgColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
            setStroke(5, Color.WHITE)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createLeftBadge() {
        val wm = windowManager ?: return

        val params = WindowManager.LayoutParams(
            150, 150,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (leftX - 75).coerceIn(0, screenWidth - 150)
            y = (leftY - 75).coerceIn(0, screenHeight - 150)
        }

        val frame = FrameLayout(this).apply {
            background = createBadgeDrawable(0xD9E53935.toInt()) // Crimson Red
            val tv = TextView(this@TriggerOverlayService).apply {
                text = "L1"
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        frame.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

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
                        params.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceIn(0, screenWidth - 150)
                        params.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceIn(0, screenHeight - 150)
                        leftX = params.x + 75
                        leftY = params.y + 75
                        wm.updateViewLayout(frame, params)
                        return true
                    }
                }
                return false
            }
        })

        leftView = frame
        wm.addView(frame, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createRightBadge() {
        val wm = windowManager ?: return

        val params = WindowManager.LayoutParams(
            150, 150,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (rightX - 75).coerceIn(0, screenWidth - 150)
            y = (rightY - 75).coerceIn(0, screenHeight - 150)
        }

        val frame = FrameLayout(this).apply {
            background = createBadgeDrawable(0xD91E88E5.toInt()) // Vivid Blue
            val tv = TextView(this@TriggerOverlayService).apply {
                text = "R1"
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        frame.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

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
                        params.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceIn(0, screenWidth - 150)
                        params.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceIn(0, screenHeight - 150)
                        rightX = params.x + 75
                        rightY = params.y + 75
                        wm.updateViewLayout(frame, params)
                        return true
                    }
                }
                return false
            }
        })

        rightView = frame
        wm.addView(frame, params)
    }

    private fun createControlBar() {
        val wm = windowManager ?: return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(30, 20, 30, 20)
            background = GradientDrawable().apply {
                setColor(0xE6212121.toInt())
                cornerRadius = 30f
                setStroke(2, 0xFF424242.toInt())
            }

            val title = TextView(this@TriggerOverlayService).apply {
                text = "Trigger Positioning"
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(0, 0, 25, 0)
                gravity = Gravity.CENTER_VERTICAL
            }
            addView(title)

            val btnSave = Button(this@TriggerOverlayService).apply {
                text = "Save & Apply"
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFF4CAF50.toInt())
                setOnClickListener {
                    saveCoordinates()
                    stopSelf()
                }
            }
            addView(btnSave)

            val btnCancel = Button(this@TriggerOverlayService).apply {
                text = "Cancel"
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFF757575.toInt())
                setOnClickListener {
                    stopSelf()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 15
            }
            addView(btnCancel, lp)
        }

        controlView = layout
        wm.addView(layout, params)
    }

    private fun saveCoordinates() {
        TriggerController.setLeftCoordinates(this, leftX, leftY)
        TriggerController.setRightCoordinates(this, rightX, rightY)
        Toast.makeText(this, "Coordinates Saved: L1($leftX, $leftY) R1($rightX, $rightY)", Toast.LENGTH_SHORT).show()
    }

    private fun removeViews() {
        val wm = windowManager ?: return
        leftView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        rightView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        controlView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        leftView = null
        rightView = null
        controlView = null
    }
}
