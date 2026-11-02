// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * AdminReceiver.kt — Android's handle for Device Owner / Device Admin
 *
 * Device Owner is set from a computer with:
 *   adb shell dpm set-device-owner com.alphablocker/.enforcement.AdminReceiver
 *
 * Device Admin is set from the on-screen prompt in the setup wizard.
 *
 * DEACTIVATION GUARD (Device Admin mode)
 * Android always lets a user deactivate a plain Device Admin. Two layers make
 * that hard while protection is on (same idea apps like BlockerHero use):
 *   1. Android calls [onDisableRequested] the moment "Deactivate" is tapped,
 *      BEFORE the user confirms. We ask the blocker service to leave the page
 *      and show the block screen, so the confirmation never gets accepted.
 *   2. The "Protected settings screens" feature closes the admin page when
 *      its text is seen.
 * Both need the accessibility service to be on, which is itself a protected
 * screen. Limit: safe mode turns third-party apps off, so in Device Admin mode
 * deactivation is still possible from safe mode. Device Owner blocks safe mode.
 * =============================================================================
 */
package com.alphablocker.enforcement

import com.alphablocker.core.t
import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.alphablocker.Graph
import com.alphablocker.data.Feature
import com.alphablocker.service.BlockerService

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Graph.enforcer.syncAsync()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val s = Graph.store.current
        if (s.protectionActive && Feature.PROTECTED_SCREENS in s.features) {
            BlockerService.instance?.guardAdminDeactivation()
        }
        return t(
            "Alpha Blocker protects you from temptation. Turning this off removes that protection. " +
                "If you really need to, use the time delay or your recovery code instead.",
            "Alpha Blocker te protege de la tentación. Desactivar esto quita esa protección. " +
                "Si de verdad lo necesitas, usa el retraso o tu código de recuperación.",
        )
    }

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)
    }
}
