/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ChargeBypassReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                ChargeBypassController.onPowerConnectionChanged(context, true)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                ChargeBypassController.onPowerConnectionChanged(context, false)
            }
            ChargeBypassController.ACTION_DISABLE_BYPASS -> {
                ChargeBypassController.setBypassEnabled(context, false)
            }
        }
    }
}
