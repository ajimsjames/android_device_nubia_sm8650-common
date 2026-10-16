/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class TriggerService : Service() {

    private val mHandler = Handler(Looper.getMainLooper())
    private var mVibrator: Vibrator? = null

    private var mRunning = AtomicBoolean(false)
    private var mThreadSar0: Thread? = null
    private var mThreadSar1: Thread? = null

    // Rapid fire state
    private var mRapidL1Active = false
    private var mRapidR1Active = false
    private var mRapidL1State = false
    private var mRapidR1State = false

    private val mRapidL1Runnable = object : Runnable {
        override fun run() {
            if (!mRapidL1Active) return
            mRapidL1State = !mRapidL1State
            val (px, py) = getTouchCoords(TriggerController.KEY_TRIGGER_L1_POS_X, TriggerController.KEY_TRIGGER_L1_POS_Y)
            if (mRapidL1State) {
                injectTouchDown(SLOT_L1, TRACKING_ID_L1, px, py)
                vibrateIfEnabled()
            } else {
                injectTouchUp(SLOT_L1)
            }
            val delay = getRapidFireDelay()
            mHandler.postDelayed(this, delay)
        }
    }

    private val mRapidR1Runnable = object : Runnable {
        override fun run() {
            if (!mRapidR1Active) return
            mRapidR1State = !mRapidR1State
            val (px, py) = getTouchCoords(TriggerController.KEY_TRIGGER_R1_POS_X, TriggerController.KEY_TRIGGER_R1_POS_Y)
            if (mRapidR1State) {
                injectTouchDown(SLOT_R1, TRACKING_ID_R1, px, py)
                vibrateIfEnabled()
            } else {
                injectTouchUp(SLOT_R1)
            }
            val delay = getRapidFireDelay()
            mHandler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mVibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!mRunning.get()) {
            startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        if (mRunning.getAndSet(true)) return

        mThreadSar0 = Thread({
            monitorDevice("/dev/input/event7", isLeftTrigger = true)
        }, "TriggerListener-Sar0").apply { start() }

        mThreadSar1 = Thread({
            monitorDevice("/dev/input/event6", isLeftTrigger = false)
        }, "TriggerListener-Sar1").apply { start() }
    }

    private fun stopListening() {
        mRunning.set(false)
        mHandler.removeCallbacks(mRapidL1Runnable)
        mHandler.removeCallbacks(mRapidR1Runnable)
        mRapidL1Active = false
        mRapidR1Active = false

        injectTouchUp(SLOT_L1)
        injectTouchUp(SLOT_R1)

        mThreadSar0?.interrupt()
        mThreadSar1?.interrupt()
        mThreadSar0 = null
        mThreadSar1 = null
    }

    private fun monitorDevice(devicePath: String, isLeftTrigger: Boolean) {
        val file = File(devicePath)
        if (!file.exists()) {
            Log.e(TAG, "Input device not found: $devicePath")
            return
        }

        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(EVENT_SIZE)
                while (mRunning.get()) {
                    val bytesRead = fis.read(buffer)
                    if (bytesRead < EVENT_SIZE) continue

                    val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                    val sec = byteBuffer.long
                    val usec = byteBuffer.long
                    val type = byteBuffer.short.toInt() and 0xFFFF
                    val code = byteBuffer.short.toInt() and 0xFFFF
                    val value = byteBuffer.int

                    if (type == EV_KEY) {
                        handleTriggerEvent(isLeftTrigger, value != 0)
                    }
                }
            }
        } catch (e: Exception) {
            if (mRunning.get()) {
                Log.e(TAG, "Error reading from $devicePath: ${e.message}")
            }
        }
    }

    private fun handleTriggerEvent(isLeftTrigger: Boolean, isDown: Boolean) {
        mHandler.post {
            val rapidEnabled = if (isLeftTrigger) {
                TriggerController.isRapidFireL1Enabled(this)
            } else {
                TriggerController.isRapidFireR1Enabled(this)
            }

            if (isLeftTrigger) {
                if (rapidEnabled) {
                    if (isDown) {
                        if (!mRapidL1Active) {
                            mRapidL1Active = true
                            mRapidL1State = false
                            mHandler.post(mRapidL1Runnable)
                        }
                    } else {
                        mRapidL1Active = false
                        mHandler.removeCallbacks(mRapidL1Runnable)
                        injectTouchUp(SLOT_L1)
                    }
                } else {
                    if (isDown) {
                        val (px, py) = getTouchCoords(TriggerController.KEY_TRIGGER_L1_POS_X, TriggerController.KEY_TRIGGER_L1_POS_Y)
                        injectTouchDown(SLOT_L1, TRACKING_ID_L1, px, py)
                        vibrateIfEnabled()
                    } else {
                        injectTouchUp(SLOT_L1)
                    }
                }
            } else {
                if (rapidEnabled) {
                    if (isDown) {
                        if (!mRapidR1Active) {
                            mRapidR1Active = true
                            mRapidR1State = false
                            mHandler.post(mRapidR1Runnable)
                        }
                    } else {
                        mRapidR1Active = false
                        mHandler.removeCallbacks(mRapidR1Runnable)
                        injectTouchUp(SLOT_R1)
                    }
                } else {
                    if (isDown) {
                        val (px, py) = getTouchCoords(TriggerController.KEY_TRIGGER_R1_POS_X, TriggerController.KEY_TRIGGER_R1_POS_Y)
                        injectTouchDown(SLOT_R1, TRACKING_ID_R1, px, py)
                        vibrateIfEnabled()
                    } else {
                        injectTouchUp(SLOT_R1)
                    }
                }
            }
        }
    }

    private fun getTouchCoords(keyX: String, keyY: String): Pair<Int, Int> {
        val pctX = TriggerController.getTriggerCoord(this, keyX, 20)
        val pctY = TriggerController.getTriggerCoord(this, keyY, 20)

        val rawX = (pctX * RAW_MAX_X / 100).coerceIn(0, RAW_MAX_X)
        val rawY = (pctY * RAW_MAX_Y / 100).coerceIn(0, RAW_MAX_Y)
        return Pair(rawX, rawY)
    }

    private fun getRapidFireDelay(): Long {
        val rate = TriggerController.getRapidFireRate(this)
        val cycleMs = (1000L / rate.coerceAtLeast(1))
        return (cycleMs / 2).coerceAtLeast(15L)
    }

    private fun vibrateIfEnabled() {
        if (!TriggerController.isHapticEnabled(this)) return
        try {
            mVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun injectTouchDown(slot: Int, trackingId: Int, x: Int, y: Int) {
        try {
            FileOutputStream(TOUCH_DEVICE).use { fos ->
                val bb = ByteBuffer.allocate(EVENT_SIZE * 6).order(ByteOrder.LITTLE_ENDIAN)
                writeEvent(bb, EV_ABS, ABS_MT_SLOT, slot)
                writeEvent(bb, EV_ABS, ABS_MT_TRACKING_ID, trackingId)
                writeEvent(bb, EV_ABS, ABS_MT_POSITION_X, x)
                writeEvent(bb, EV_ABS, ABS_MT_POSITION_Y, y)
                writeEvent(bb, EV_KEY, BTN_TOUCH, 1)
                writeEvent(bb, EV_SYN, SYN_REPORT, 0)
                fos.write(bb.array())
                fos.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "injectTouchDown failed: ${e.message}")
        }
    }

    @Synchronized
    private fun injectTouchUp(slot: Int) {
        try {
            FileOutputStream(TOUCH_DEVICE).use { fos ->
                val bb = ByteBuffer.allocate(EVENT_SIZE * 4).order(ByteOrder.LITTLE_ENDIAN)
                writeEvent(bb, EV_ABS, ABS_MT_SLOT, slot)
                writeEvent(bb, EV_ABS, ABS_MT_TRACKING_ID, -1)
                writeEvent(bb, EV_KEY, BTN_TOUCH, 0)
                writeEvent(bb, EV_SYN, SYN_REPORT, 0)
                fos.write(bb.array())
                fos.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "injectTouchUp failed: ${e.message}")
        }
    }

    private fun writeEvent(bb: ByteBuffer, type: Int, code: Int, value: Int) {
        bb.putLong(0L) // sec
        bb.putLong(0L) // usec
        bb.putShort(type.toShort())
        bb.putShort(code.toShort())
        bb.putInt(value)
    }

    companion object {
        private const val TAG = "TriggerService"
        private const val TOUCH_DEVICE = "/dev/input/event8"
        private const val EVENT_SIZE = 24

        // Dedicated MT slots 8 and 9 so player thumb touches (slots 0-7) never get blocked
        private const val SLOT_L1 = 8
        private const val SLOT_R1 = 9
        private const val TRACKING_ID_L1 = 9008
        private const val TRACKING_ID_R1 = 9009

        // Goodix digitizer limits
        private const val RAW_MAX_X = 17856
        private const val RAW_MAX_Y = 39680

        private const val EV_SYN = 0x00
        private const val EV_KEY = 0x01
        private const val EV_ABS = 0x03

        private const val SYN_REPORT = 0x00
        private const val BTN_TOUCH = 0x14a

        private const val ABS_MT_SLOT = 0x2f
        private const val ABS_MT_POSITION_X = 0x35
        private const val ABS_MT_POSITION_Y = 0x36
        private const val ABS_MT_TRACKING_ID = 0x39
    }
}
