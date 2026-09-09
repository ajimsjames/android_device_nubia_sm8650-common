/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.nosimstub

import android.service.quickaccesswallet.GetWalletCardsCallback
import android.service.quickaccesswallet.GetWalletCardsRequest
import android.service.quickaccesswallet.GetWalletCardsResponse
import android.service.quickaccesswallet.QuickAccessWalletService
import android.service.quickaccesswallet.SelectWalletCardRequest

class NullQuickAccessWalletService : QuickAccessWalletService() {
    override fun onWalletCardsRequested(
        request: GetWalletCardsRequest,
        callback: GetWalletCardsCallback
    ) {
        callback.onSuccess(GetWalletCardsResponse(emptyList(), 0))
    }

    override fun onWalletCardSelected(request: SelectWalletCardRequest) {
    }

    override fun onWalletDismissed() {
    }
}
