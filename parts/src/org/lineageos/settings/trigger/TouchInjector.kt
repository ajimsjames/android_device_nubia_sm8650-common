/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.content.Context
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TouchInjector {
    private const val TAG = "TouchInjector"

    private const val EV_SYN = 0
    private const val EV_KEY = 1
    private const val EV_ABS = 3

    private const val SYN_REPORT = 0
    private const val BTN_TOUCH = 330

    private const val ABS_MT_SLOT = 0x2f
    private const val ABS_MT_TOUCH_MAJOR = 0x30
    private const val ABS_MT_POSITION_X = 0x35
    private const val ABS_MT_POSITION_Y = 0x36
    private const val ABS_MT_TRACKING_ID = 0x39

    // Touch sensor max limits (goodix_ts)
    private const val TOUCH_MAX_X = 17856f
    private const val TOUCH_MAX_Y = 39680f
    private const val SCREEN_WIDTH = 1116f
    private const val SCREEN_HEIGHT = 2480f

    // Dedicated slots for triggers so they never collide with physical fingers (slots 0..7)
    private const val SLOT_LEFT = 8
    private const val SLOT_RIGHT = 9
    private const val TRACK_LEFT = 1008
    private const val TRACK_RIGHT = 1009

    private var touchDevicePath: String? = null
    private var touchOutputStream: FileOutputStream? = null

    private var injectInputEventMethod: Method? = null
    private var inputManagerInstance: Any? = null
    private var appContext: Context? = null

    private var downTimeLeft: Long = 0
    private var downTimeRight: Long = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        findTouchDevice()
        openTouchStream()
        initInputManagerReflection()
    }

    private fun findTouchDevice() {
        try {
            val inputDir = File("/sys/class/input")
            val eventDirs = inputDir.listFiles { file -> file.name.startsWith("event") }
            if (eventDirs != null) {
                for (eventDir in eventDirs) {
                    val nameFile = File(eventDir, "device/name")
                    if (nameFile.exists()) {
                        val name = nameFile.readText().trim()
                        if (name.contains("goodix_ts", ignoreCase = true) || name.contains("touch", ignoreCase = true)) {
                            touchDevicePath = "/dev/input/${eventDir.name}"
                            Log.i(TAG, "Found touch device: $touchDevicePath ($name)")
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding touch device", e)
        }
        touchDevicePath = "/dev/input/event8"
    }

    private fun openTouchStream() {
        val path = touchDevicePath ?: "/dev/input/event8"
        try {
            val file = File(path)
            if (file.exists() && file.canWrite()) {
                touchOutputStream = FileOutputStream(file)
                Log.i(TAG, "Opened direct touch output stream to $path")
            } else {
                Log.w(TAG, "Touch device $path cannot be written directly (will use InputManager fallback)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open touch stream for $path: ${e.message}")
        }
    }

    private fun initInputManagerReflection() {
        try {
            val inputManagerClass = Class.forName("android.hardware.input.InputManager")
            val getInstanceMethod = inputManagerClass.getMethod("getInstance")
            inputManagerInstance = getInstanceMethod.invoke(null)
            injectInputEventMethod = inputManagerClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
        } catch (e: Exception) {
            try {
                val inputManagerGlobalClass = Class.forName("android.hardware.input.InputManagerGlobal")
                val getInstanceMethod = inputManagerGlobalClass.getMethod("getInstance")
                inputManagerInstance = getInstanceMethod.invoke(null)
                injectInputEventMethod = inputManagerGlobalClass.getMethod(
                    "injectInputEvent",
                    InputEvent::class.java,
                    Int::class.javaPrimitiveType
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to initialize InputManager via reflection", e2)
            }
        }
    }

    private fun writeInputEvent(fos: FileOutputStream, type: Int, code: Int, value: Int) {
        val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        val now = SystemClock.uptimeMillis()
        val sec = now / 1000
        val usec = (now % 1000) * 1000

        buffer.putLong(sec)
        buffer.putLong(usec)
        buffer.putShort(type.toShort())
        buffer.putShort(code.toShort())
        buffer.putInt(value)

        fos.write(buffer.array())
    }

    private fun mapDisplayToRawCoords(x: Float, y: Float): Pair<Int, Int> {
        var rotation = Surface.ROTATION_0
        var displayWidth = SCREEN_WIDTH
        var displayHeight = SCREEN_HEIGHT

        try {
            val context = appContext
            if (context != null) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val display = wm?.defaultDisplay
                if (display != null) {
                    rotation = display.rotation
                    val dm = DisplayMetrics()
                    display.getRealMetrics(dm)
                    displayWidth = dm.widthPixels.toFloat()
                    displayHeight = dm.heightPixels.toFloat()
                }
            }
        } catch (e: Exception) {
            // Default
        }

        var rawX: Float
        var rawY: Float

        when (rotation) {
            Surface.ROTATION_90 -> {
                // Landscape (left = top of screen)
                rawX = (y / displayHeight) * TOUCH_MAX_X
                rawY = ((displayWidth - x) / displayWidth) * TOUCH_MAX_Y
            }
            Surface.ROTATION_270 -> {
                // Landscape reversed
                rawX = ((displayHeight - y) / displayHeight) * TOUCH_MAX_X
                rawY = (x / displayWidth) * TOUCH_MAX_Y
            }
            Surface.ROTATION_180 -> {
                // Portrait inverted
                rawX = ((displayWidth - x) / displayWidth) * TOUCH_MAX_X
                rawY = ((displayHeight - y) / displayHeight) * TOUCH_MAX_Y
            }
            else -> {
                // Portrait normal
                rawX = (x / displayWidth) * TOUCH_MAX_X
                rawY = (y / displayHeight) * TOUCH_MAX_Y
            }
        }

        return Pair(rawX.toInt().coerceIn(0, TOUCH_MAX_X.toInt()), rawY.toInt().coerceIn(0, TOUCH_MAX_Y.toInt()))
    }

    fun injectDown(x: Float, y: Float, isLeft: Boolean) {
        val slot = if (isLeft) SLOT_LEFT else SLOT_RIGHT
        val trackId = if (isLeft) TRACK_LEFT else TRACK_RIGHT
        val (rawX, rawY) = mapDisplayToRawCoords(x, y)

        // Try direct hardware touch injection first
        val fos = touchOutputStream
        if (fos != null) {
            try {
                synchronized(this) {
                    writeInputEvent(fos, EV_ABS, ABS_MT_SLOT, slot)
                    writeInputEvent(fos, EV_ABS, ABS_MT_TRACKING_ID, trackId)
                    writeInputEvent(fos, EV_ABS, ABS_MT_POSITION_X, rawX)
                    writeInputEvent(fos, EV_ABS, ABS_MT_POSITION_Y, rawY)
                    writeInputEvent(fos, EV_ABS, ABS_MT_TOUCH_MAJOR, 120)
                    writeInputEvent(fos, EV_KEY, BTN_TOUCH, 1)
                    writeInputEvent(fos, EV_SYN, SYN_REPORT, 0)
                    fos.flush()
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Direct kernel write failed, falling back to InputManager", e)
                touchOutputStream = null
            }
        }

        // Fallback to InputManager with proper touchscreen source
        val now = SystemClock.uptimeMillis()
        if (isLeft) downTimeLeft = now else downTimeRight = now
        val downTime = if (isLeft) downTimeLeft else downTimeRight

        val event = MotionEvent.obtain(
            downTime,
            now,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }

        try {
            injectInputEventMethod?.invoke(inputManagerInstance, event, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting touch event via InputManager", e)
        } finally {
            event.recycle()
        }
    }

    fun injectUp(x: Float, y: Float, isLeft: Boolean) {
        val slot = if (isLeft) SLOT_LEFT else SLOT_RIGHT

        val fos = touchOutputStream
        if (fos != null) {
            try {
                synchronized(this) {
                    writeInputEvent(fos, EV_ABS, ABS_MT_SLOT, slot)
                    writeInputEvent(fos, EV_ABS, ABS_MT_TRACKING_ID, -1)
                    writeInputEvent(fos, EV_KEY, BTN_TOUCH, 0)
                    writeInputEvent(fos, EV_SYN, SYN_REPORT, 0)
                    fos.flush()
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Direct kernel write failed on UP", e)
                touchOutputStream = null
            }
        }

        val now = SystemClock.uptimeMillis()
        val downTime = if (isLeft) downTimeLeft else downTimeRight

        val event = MotionEvent.obtain(
            downTime,
            now,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }

        try {
            injectInputEventMethod?.invoke(inputManagerInstance, event, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting touch UP event", e)
        } finally {
            event.recycle()
        }
    }

    fun injectTap(x: Float, y: Float, isLeft: Boolean, holdMs: Long = 25) {
        injectDown(x, y, isLeft)
        if (holdMs > 0) {
            SystemClock.sleep(holdMs)
        }
        injectUp(x, y, isLeft)
    }
}
