/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lineageos.settings.utils.*

class TriggerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateQsState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = getInt(this, TriggerController.KEY_TRIGGER_ENABLE, 0) == 1

        TriggerController.setTriggerEnabled(this, !currentState)

        updateQsState()
    }

    private fun updateQsState() {
        val isTriggerEnabled = getInt(this, TriggerController.KEY_TRIGGER_ENABLE, 0) == 1

        qsTile
            .apply { state = if (isTriggerEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE }
            .updateTile()
    }
}
