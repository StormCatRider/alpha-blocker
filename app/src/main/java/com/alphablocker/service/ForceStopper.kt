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
 *   2. finds "Force stop" (by its text or description, English or Spanish, or
 *      by its button ID) and TAPS its position on screen, like a finger,
 *   3. finds "OK" in the confirmation and taps it the same way,
 *   4. then the block screen appears.
 * WHY TAPS: since Android 14, App info is drawn with Jetpack Compose, which can
 * ignore the "click this element" command even though the button is there.
 * A tap on the screen position always works (needs canPerformGestures in
 * res/xml/accessibility_service.xml). The click command is only a fallback.
 * TIMING (App info is a live page):
 *   * it waits ~0.35 s for the page to finish sliding in before tapping;
 *   * "Force stop" starts GREYED OUT while Android checks if the app runs, then
 *     turns on; only if it stays greyed out for 2 s is the app already stopped;
 *   * if no confirmation appears 1.2 s after a tap, the tap missed: tap again.
 * If it can't finish within 7 seconds (another phone layout), it shows a short
 * message, and the block screen appears anyway.
 *
 * Your data in that app (logins, settings, downloads) is NOT touched: Force
 * stop only ends the running app. Never used on Alpha Blocker itself or on apps
 * the phone needs (home screen, dialer, Settings, system UI).
 * =============================================================================
 */
package com.alphablocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.alphablocker.core.t

class ForceStopper(private val service: AccessibilityService, private val handler: Handler) {

    /** App being stopped right now, or null. Protected-screen checks pause while set. */
    var target: String? = null
        private set

    private var stage = 0          // 1 = looking for "Force stop", 2 = looking for "OK"
    private var after: (() -> Unit)? = null
    private var lastTapAt = 0L     // waits a moment after each tap for the screen to react
    private var stopped = false    // true once OK was tapped (or the app was already stopped)
    private var seenAt = 0L        // when the Force stop button first appeared on screen
    private var disabledSince = 0L // how long it has stayed greyed out

    private val poll = object : Runnable {
        override fun run() {
            step()
            if (target != null) handler.postDelayed(this, POLL_MS)
        }
    }
    private val timeout = Runnable {
        if (!stopped) {
            Toast.makeText(
                service,
                t("Alpha Blocker couldn't press Force stop on this phone.", "Alpha Blocker no pudo pulsar Forzar detención en este teléfono."),
                Toast.LENGTH_SHORT,
            ).show()
        }
        finish()
    }

    /**
     * Force-stops [pkg], then runs [then] (normally: show the block screen).
     * If a stop is already running, [then] runs right away.
     */
    fun stop(pkg: String, then: () -> Unit) {
        if (target != null) { then(); return }
        target = pkg
        stage = 1
        stopped = false
        lastTapAt = 0L
        seenAt = 0L
        disabledSince = 0L
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
        if (SystemClock.elapsedRealtime() - lastTapAt < AFTER_TAP_MS) return
        val roots = settingsRoots()
        if (roots.isEmpty()) return
        when (stage) {
            1 -> {
                val button = roots.firstNotNullOfOrNull { findByLabel(it, FORCE_STOP_LABELS) }
                    ?: roots.firstNotNullOfOrNull { it.findAccessibilityNodeInfosByViewId("$SETTINGS_PKG:id/button3").firstOrNull() }
                    ?: return
                val btn = clickableOf(button)
                val now = SystemClock.elapsedRealtime()
                // Let the page settle before tapping (it's still sliding in at first).
                if (seenAt == 0L) seenAt = now
                if (now - seenAt < SETTLE_MS) return
                // App info shows Force stop GREYED OUT for a moment while it checks
                // whether the app is running, then enables it. So a greyed-out button
                // only means "already stopped" if it STAYS greyed out.
                if (!btn.isEnabled) {
                    if (disabledSince == 0L) disabledSince = now
                    if (now - disabledSince > STILL_DISABLED_MS) { stopped = true; finish() }
                    return
                }
                disabledSince = 0L
                if (press(btn)) { stage = 2; lastTapAt = now }
            }
            2 -> {
                // The confirmation dialog: "OK"/"Aceptar", or Android's standard positive button.
                val ok = roots.firstNotNullOfOrNull { findByLabel(it, OK_LABELS) }
                    ?: roots.firstNotNullOfOrNull { it.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull() }
                if (ok == null) {
                    // No confirmation after the tap? The tap missed (page still moving):
                    // go back and tap Force stop again.
                    if (SystemClock.elapsedRealtime() - lastTapAt > RETRY_TAP_MS) { stage = 1; seenAt = 0L }
                    return
                }
                if (press(clickableOf(ok))) {
                    stopped = true
                    lastTapAt = SystemClock.elapsedRealtime()
                    handler.postDelayed({ finish() }, AFTER_TAP_MS) // let the stop finish
                    stage = 3
                }
            }
        }
    }

    /** Every on-screen window that belongs to Settings (the page and its dialog). */
    private fun settingsRoots(): List<AccessibilityNodeInfo> {
        val fromWindows = runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())
        val all = (listOfNotNull(service.rootInActiveWindow) + fromWindows)
        return all.filter { it.packageName?.toString() == SETTINGS_PKG }
    }

    /**
     * Walks the screen (up to 800 elements) for one whose text or description
     * is exactly one of [labels] (ignoring case). Works for Compose screens too.
     */
    private fun findByLabel(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 800) {
            val n = queue.removeFirst()
            visited++
            val text = n.text?.toString()?.trim()
            val desc = n.contentDescription?.toString()?.trim()
            if (labels.any { it.equals(text, ignoreCase = true) || it.equals(desc, ignoreCase = true) }) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** The element itself if clickable, else its nearest clickable parent (else itself). */
    private fun clickableOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n ?: node
    }

    /** Taps the centre of [node] on screen; falls back to the click command. */
    private fun press(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        if (!r.isEmpty) {
            val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
            val tap = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
                .build()
            if (service.dispatchGesture(tap, null, null)) return true
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun finish() {
        if (target == null) return
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
        private const val POLL_MS = 150L
        private const val AFTER_TAP_MS = 450L
        private const val TIMEOUT_MS = 7_000L
        private const val SETTLE_MS = 350L          // page slide-in before the first tap
        private const val STILL_DISABLED_MS = 2_000L // greyed out this long = really stopped
        private const val RETRY_TAP_MS = 1_200L     // no confirmation this long after a tap = tap again

        /** "Force stop" as Pixel Settings writes it (English, Spanish variants). */
        private val FORCE_STOP_LABELS = listOf(
            "Force stop", "Forzar detención", "Forzar la detención", "Forzar cierre", "Forzar detencion", "Forzar parada",
        )

        /** The confirmation's OK button. */
        private val OK_LABELS = listOf("OK", "Aceptar", "Ok")
    }
}
