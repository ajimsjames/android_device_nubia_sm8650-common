/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TriggerPositionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Map Triggers"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, TriggerOverlayService::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startService(intent)
    }
}
