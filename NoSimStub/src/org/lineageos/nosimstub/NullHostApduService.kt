/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.nosimstub

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

class NullHostApduService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        return byteArrayOf(0x69.toByte(), 0x86.toByte())
    }

    override fun onDeactivated(reason: Int) {
    }
}
