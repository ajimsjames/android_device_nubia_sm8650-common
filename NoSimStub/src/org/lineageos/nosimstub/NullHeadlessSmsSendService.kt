/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.nosimstub

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NullHeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
