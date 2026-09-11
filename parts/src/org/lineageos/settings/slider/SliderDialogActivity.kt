/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.slider

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import org.lineageos.settings.R

class SliderDialogActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val items = arrayOf(
            "🚀 Diablo Mode + Triggers",
            "🎮 Gaming Suite (Performance + Smart Fan)",
            "📷 Open Camera",
            "🔦 Toggle Flashlight",
            "❄️ Max Cooling Turbo (Fan Lv5)",
            "🔕 Mute / Silent Mode",
            "📳 Vibrate Mode",
            "🛑 Do Not Disturb",
            "⚙️ Nubia Settings & Parts"
        )

        val actionValues = intArrayOf(
            SliderController.ACTION_DIABLO_MODE,
            SliderController.ACTION_GAMING_SUITE,
            SliderController.ACTION_LAUNCH_CAMERA,
            SliderController.ACTION_FLASHLIGHT,
            SliderController.ACTION_COOLING_TURBO,
            SliderController.ACTION_SILENT_MODE,
            SliderController.ACTION_VIBRATE_MODE,
            SliderController.ACTION_DND_SILENT,
            99 // Nubia Settings
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("🎮 Game Switch Action")
            .setItems(items) { _, which ->
                val chosenAction = actionValues[which]
                if (chosenAction == 99) {
                    openNubiaSettings()
                } else {
                    SliderController.executeAction(this, chosenAction, true)
                }
                finish()
            }
            .setPositiveButton("⚙️ Settings") { _, _ ->
                openNubiaSettings()
                finish()
            }
            .setNegativeButton("Close") { _, _ ->
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .create()

        dialog.show()
    }

    private fun openNubiaSettings() {
        try {
            val intent = Intent(this, org.lineageos.settings.nubia.NubiaActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SliderDialogActivity", "Failed to launch Nubia Settings", e)
        }
    }
}
