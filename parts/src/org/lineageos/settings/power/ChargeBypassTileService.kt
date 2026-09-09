/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.power

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lineageos.settings.R

class ChargeBypassTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val newState = !ChargeBypassController.isBypassEnabled(this)
        ChargeBypassController.setBypassEnabled(this, newState)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = ChargeBypassController.isBypassEnabled(this)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.charge_bypass_tile_label)
        tile.subtitle = if (enabled) getString(R.string.charge_bypass_active_subtitle) else getString(R.string.charge_bypass_inactive_subtitle)
        tile.updateTile()
    }
}
