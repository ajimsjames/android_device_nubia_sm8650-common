/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.nosimstub

import android.telecom.Call
import android.telecom.InCallService

class NullInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.disconnect()
    }
}
