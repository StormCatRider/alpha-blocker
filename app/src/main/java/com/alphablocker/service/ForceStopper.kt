// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * ForceStopper.kt — really closes a blocked app ("Force stop")
 *
 * WHY: a blocked app that stays alive in the background keeps its place (the
 * feed you were scrolling, the video you were on) and its preview in Recents.
 * Reopening it again and again would let you get a little further each time.
 * Force-stopping ends the app's process and removes it from Recents, so the
 * next open starts fresh, at the app's first screen.
 *
 * HOW: since Android 14, apps can no longer stop other apps directly
 * (killBackgroundProcesses only works on your own app). The one reliable way
 * left is the same one a person uses: Settings › Apps › <app> › Force stop ›
 * OK. The blocker service (an accessibility service, allowed to press buttons)
 * does exactly that in about a second:
 *   1. opens the blocked app's App info page,
 *   2. presses "Force stop" (found by its text, English or Spanish, or by its
 *      button ID),
 *   3. presses "OK" in the confirmation (Android's standard dialog button,
 *      the same in every language),
 *   4. then the block screen appears.
 * If anything doesn't show up within 4 seconds (another phone layout), it gives
 * up quietly and the block screen appears anyway. If "Force stop" is greyed
 * out, the app is already stopped, so there's nothing to do.
 *
 * Your data in that app (logins, settings, downloads) is NOT touched: Force
 * stop only ends the running app. Never used on Alpha Blocker itself or on apps
 * the phone needs (home screen, dialer, Settings, system UI).
 * =============================================================================
 */
package com.alphablocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo

class ForceStopper(private val service: AccessibilityService, private val handler: Handler) {

    /** App being stopped right now, or null. Protected-screen checks pause while set. */
    var target: String? = null
        private set

    private var stage = 0          // 1 = looking for "Force stop", 2 = looking for "OK"
    private var after: (() -> Unit)? = null

    private val poll = object : Runnable {
        override fun run() {
            step()
            if (target != null) handler.postDelayed(this, POLL_MS)
        }
    }
    private val timeout = Runnable { finish() }

    /**
     * Force-stops [pkg], then runs [then] (normally: show the block screen).
     * If a stop is already running, [then] runs right away.
     */
    fun stop(pkg: String, then: () -> Unit) {
        if (target != null) { then(); return }
        target = pkg
        stage = 1
        after = then
        val opened = runCatching {
            service.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                    ),
            )
        }.isSuccess
        if (!opened) { finish(); return }
        handler.postDelayed(poll, POLL_MS)
        handler.postDelayed(timeout, TIMEOUT_MS)
    }

    /** Called on every accessibility event too, so it reacts as soon as the screen appears. */
    fun onEvent() { if (target != null) step() }

    private fun step() {
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != SETTINGS_PKG) return
        when (stage) {
            1 -> {
                val button = findForceStop(root) ?: return
                if (!button.isEnabled) { finish(); return } // already stopped
                if (click(button)) stage = 2
            }
            2 -> {
                // The confirmation's OK is Android's standard positive button.
                val ok = root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull() ?: return
                if (click(ok)) finish()
            }
        }
    }

    private fun findForceStop(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (label in FORCE_STOP_LABELS) {
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.text?.toString()?.trim()?.equals(label, ignoreCase = true) == true }
                ?.let { return it }
        }
        // Fallback: Settings' action-button row (Open / Uninstall / Force stop) on Pixel.
        return root.findAccessibilityNodeInfosByViewId("$SETTINGS_PKG:id/button3").firstOrNull()
    }

    /** Clicks [node] or its nearest clickable parent. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n?.isEnabled == true && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun finish() {
        handler.removeCallbacks(poll)
        handler.removeCallbacks(timeout)
        val then = after
        target = null
        stage = 0
        after = null
        then?.invoke()
    }

    companion object {
        private const val SETTINGS_PKG = "com.android.settings"
        private const val POLL_MS = 120L
        private const val TIMEOUT_MS = 4_000L

        /** "Force stop" as Pixel Settings writes it (English, Spanish variants). */
        private val FORCE_STOP_LABELS = listOf(
            "Force stop", "Forzar detención", "Forzar la detención", "Forzar cierre", "Forzar detencion",
        )
    }
}
