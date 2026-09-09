/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout

@SuppressLint("StaticFieldLeak")
object TriggerFeedbackManager {

    private var context: Context? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var leftFeedbackView: View? = null
    private var rightFeedbackView: View? = null

    private const val CIRCLE_SIZE = 80 // px
    private const val HALF_SIZE = CIRCLE_SIZE / 2

    fun init(ctx: Context) {
        context = ctx.applicationContext
        windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun createCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(3, Color.WHITE)
        }
    }

    private fun createFeedbackView(isLeft: Boolean): View {
        val ctx = context ?: return View(null)
        val color = if (isLeft) 0xCCFF3B30.toInt() else 0xCC007AFF.toInt()
        return View(ctx).apply {
            background = createCircleDrawable(color)
            alpha = 0f
        }
    }

    fun showFeedback(isLeft: Boolean) {
        val ctx = context ?: return
        if (!TriggerController.isFeedbackEnabled(ctx)) return

        mainHandler.post {
            val wm = windowManager ?: return@post
            val coords = if (isLeft) {
                TriggerController.getLeftCoordinates(ctx)
            } else {
                TriggerController.getRightCoordinates(ctx)
            }

            var view = if (isLeft) leftFeedbackView else rightFeedbackView

            if (view == null || view.parent == null) {
                view = createFeedbackView(isLeft)
                if (isLeft) leftFeedbackView = view else rightFeedbackView = view

                val params = WindowManager.LayoutParams(
                    CIRCLE_SIZE, CIRCLE_SIZE,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = coords.first - HALF_SIZE
                    y = coords.second - HALF_SIZE
                }

                try {
                    wm.addView(view, params)
                } catch (e: Exception) {
                    return@post
                }
            } else {
                // Update position if user moved crosshairs
                val params = view.layoutParams as WindowManager.LayoutParams
                params.x = coords.first - HALF_SIZE
                params.y = coords.second - HALF_SIZE
                try {
                    wm.updateViewLayout(view, params)
                } catch (e: Exception) {
                    // Ignore
                }
            }

            // Animate touch ripple blink
            view.animate().cancel()
            view.alpha = 0.9f
            view.scaleX = 0.7f
            view.scaleY = 0.7f

            view.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    fun cleanup() {
        mainHandler.post {
            val wm = windowManager ?: return@post
            leftFeedbackView?.let {
                if (it.parent != null) wm.removeView(it)
            }
            rightFeedbackView?.let {
                if (it.parent != null) wm.removeView(it)
            }
            leftFeedbackView = null
            rightFeedbackView = null
        }
    }
}
