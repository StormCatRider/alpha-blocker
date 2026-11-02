// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * BlockerService.kt — the on-device blocker (an Accessibility Service)
 *
 * WHAT IT WATCHES (events from Android, see res/xml/accessibility_service.xml)
 *   * which app is in front               -> app rules, time limits, usage
 *     (from window events, plus a check of the active window every 1.5 s,
 *      because apps reopened from Recents don't always send an event)
 *   * browser address bars + typed text   -> blocked words/pages, SafeSearch
 *   * visible text on protected screens   -> closes settings pages that could
 *                                            switch protection off
 *
 * PRIVACY
 *   * Screen text is checked in memory and immediately discarded.
 *   * Nothing is logged or written to disk except per-app usage minutes.
 *   * The app has no internet permission, so nothing can be sent anywhere.
 *
 * Also shows the optional "time left today" countdown notification for
 * time-limited apps (LimitCountdown.kt).
 *
 * WHAT HAPPENS ON A BLOCK
 *   * Blocked app:          show the block screen on top of it (Exit = home).
 *   * Blocked web content:  press Back (leaves the page), then show the
 *                           block screen with a purity verse.
 *   * Protected screen:     press Back, then show the block screen.
 *
 * A short cool-down stops the same block from firing repeatedly.
 * =============================================================================
 */
package com.alphablocker.service

import com.alphablocker.core.t
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.alphablocker.Graph
import com.alphablocker.core.AppCatalog
import com.alphablocker.core.Browsers
import com.alphablocker.core.ContentMatcher
import com.alphablocker.core.Decision
import com.alphablocker.core.RuleEngine
import com.alphablocker.core.SafeSearch
import com.alphablocker.data.AppState
import com.alphablocker.data.Feature
import com.alphablocker.data.VerseTheme
import com.alphablocker.enforcement.BankPause
import com.alphablocker.ui.BlockActivity
import com.alphablocker.ui.ResumeActivity
import java.time.LocalDateTime

class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var power: PowerManager

    /** App currently in front (null until the first window event). */
    private var currentPkg: String? = null

    /** Packages that are never "the foreground app" (keyboards, system UI). */
    private var ignoredPkgs: Set<String> = emptySet()

    // Throttles / cool-downs (elapsedRealtime ms)
    private var lastBlockAt = 0L
    private var lastScreenScanAt = 0L   // protected-screens text scan
    private var lastUrlScanAt = 0L      // browser address-bar read
    private var lastRewrite: Pair<String, Long>? = null
    private var lastForegroundCheckAt = 0L
    private var tickCount = 0

    /**
     * Backup check every 1.5 s while the screen is on: asks Android which app
     * is really in front. Catches apps reopened from the background (e.g.
     * Reddit or Instagram from Recents) that don't send a window event.
     * Cheap: it only reads the window list, no screen text.
     */
    private val foregroundPoll = object : Runnable {
        override fun run() {
            if (power.isInteractive) {
                lastForegroundCheckAt = 0L // this check always runs
                if (!refreshForeground(hint = null)) recheckCurrentScreen()
            }
            handler.postDelayed(this, FOREGROUND_POLL_MS)
        }
    }

    // Matcher is rebuilt only when the word/page lists change.
    private var matcher: ContentMatcher? = null
    private var matcherKey: Pair<List<String>, List<String>>? = null

    /** Ticks every 15 s: counts usage and re-checks time limits. */
    private val ticker = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** New apps installed -> refresh the browser list and re-sync. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppCatalog.invalidate()
            Graph.enforcer.syncAsync()
        }
    }

    override fun onServiceConnected() {
        power = getSystemService(PowerManager::class.java)
        ignoredPkgs = buildSet {
            add(packageName)
            add("com.android.systemui")
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { add(it.packageName) }
        }
        // NOT_EXPORTED: only the system's own package broadcasts reach this receiver.
        ContextCompat.registerReceiver(this, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.post(ticker)
        handler.post(foregroundPoll)
        instance = this
        Graph.enforcer.syncAsync() // service just came on: lift fail-closed suspensions
        // A banking pause just ran out: say "Protection is back on". Opened from here
        // because Android lets a running accessibility service open screens.
        if (BankPause.consumeBackOnPopup()) {
            handler.postDelayed({ runCatching { startActivity(ResumeActivity.backOnIntent(this)) } }, 500L)
        }
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(foregroundPoll)
        LimitCountdown.hide(this)
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
        Graph.enforcer.syncAsync() // service went off: fail closed
    }

    override fun onInterrupt() {}

    /**
     * Called by AdminReceiver.onDisableRequested when someone taps
     * "Deactivate" on Alpha Blocker's device admin page while protection is on.
     * Leaves the page before the confirmation can be accepted, then shows the
     * block screen. This works no matter how the screen is worded, so it backs
     * up the text-based protected-screens check.
     */
    fun guardAdminDeactivation() {
        handler.post {
            lastBlockAt = 0L // don't let an earlier block's cool-down skip this one
            block(
                pkg = "com.android.settings",
                reason = t(
                    "Alpha Blocker's device admin is protected. Use the time delay or your recovery code to turn protection off.",
                    "El administrador de dispositivo de Alpha Blocker está protegido. Usa el retraso o tu código de recuperación para desactivar la protección.",
                ),
                theme = VerseTheme.PURITY,
                canExtend = false,
                pressBack = true,
            )
        }
    }

    // ------------------------------------------------------------------ events

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val type = event.eventType
        val pkg = event.packageName?.toString()

        // 0. Which app is in front? Window changes, or any event from an app that
        //    isn't the current one, trigger a check of the ACTUAL active window.
        //    (Apps resumed from the background don't always send a "window state
        //    changed" event; see foregroundPoll for the backup check.)
        //    Content events from keyboards / the status bar are skipped here: they
        //    arrive constantly (clock, notifications) and never mean an app switch.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            (pkg != null && pkg != currentPkg && pkg !in ignoredPkgs)
        ) {
            if (refreshForeground(hint = if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) pkg else null)) return
        }

        if (pkg == null || pkg in ignoredPkgs) return
        val s = Graph.store.current
        if (!s.protectionActive) return

        // 1. App rules (always / schedule / limit / browser allowlist). Also re-checked
        //    when an app opens a new screen, not only when it first comes to the front.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg == currentPkg && checkApp(s, pkg)) return

        // 2. Protected settings screens
        if (Feature.PROTECTED_SCREENS in s.features && checkProtectedScreens(s, pkg)) return

        // 3. Browser content
        if (pkg in AppCatalog.browsers(this)) checkBrowser(s, pkg, event)
    }

    // ------------------------------------------------------- foreground app

    /**
     * Package of the app window the user is looking at right now, read from
     * Android's live window list (needs flagRetrieveInteractiveWindows).
     * Falls back to the active window's root, then to [hint].
     */
    private fun foregroundPackage(hint: String?): String? {
        val fromWindows = runCatching {
            windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
                ?.root?.packageName?.toString()
        }.getOrNull()
        return fromWindows ?: rootInActiveWindow?.packageName?.toString() ?: hint
    }

    /**
     * Updates [currentPkg] if a different app is now in front, counts usage,
     * and checks that app's rules immediately.
     * Throttled to once every 250 ms because many events can arrive at once.
     * @return true if the new foreground app was blocked.
     */
    private fun refreshForeground(hint: String?): Boolean {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastForegroundCheckAt < 250L) return false
        lastForegroundCheckAt = nowMs

        val fg = foregroundPackage(hint) ?: return false
        if (fg == currentPkg) return false
        // Our own block screen counts as "in front" (so the blocked app stops being
        // current and stops collecting time); keyboards and the system bar don't.
        if (fg != packageName && fg in ignoredPkgs) return false

        currentPkg = fg
        Graph.usage.onForeground(fg, power.isInteractive)
        val s = Graph.store.current
        val blocked = s.protectionActive && fg != packageName && checkApp(s, fg)
        LimitCountdown.update(this, s, if (blocked) null else fg, power.isInteractive)
        return blocked
    }

    /**
     * Backstop run by the poll: re-checks the screen of the app in front, in case
     * it finished loading without sending another event. Only does work in
     * browsers and in apps that have protected screens.
     */
    private fun recheckCurrentScreen() {
        val pkg = currentPkg ?: return
        if (pkg == packageName) return
        val s = Graph.store.current
        if (!s.protectionActive) return
        if (Feature.PROTECTED_SCREENS in s.features && checkProtectedScreens(s, pkg)) return
        if (pkg in AppCatalog.browsers(this)) checkBrowserUrl(s, pkg)
    }

    /** Returns true if [pkg] was blocked. */
    private fun checkApp(s: AppState, pkg: String): Boolean {
        val usage = Graph.usage
        val decision = RuleEngine.evaluate(
            s = s,
            pkg = pkg,
            isBrowser = pkg in AppCatalog.browsers(this),
            critical = AppCatalog.criticalPackages(this),
            now = LocalDateTime.now(),
            usedMs = usage.usedMs(pkg),
            extraMs = usage.extraMs(pkg),
            extensionsUsed = usage.extensionsUsed(),
        )
        if (decision is Decision.Block) {
            block(pkg, decision.reason, decision.theme, decision.canExtend, pressBack = false)
            return true
        }
        return false
    }

    /**
     * Closes [pkg]'s screen if ALL phrases of any protected-screen entry for
     * that app are visible. Throttled to once every 400 ms; the 1.5 s
     * foreground poll re-checks too, so a screen that finishes drawing during
     * the throttle (and then sends no more events) is still caught.
     */
    private fun checkProtectedScreens(s: AppState, pkg: String): Boolean {
        val entries = s.protectedScreens.filter { it.pkg == pkg }
        if (entries.isEmpty()) return false
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastScreenScanAt < 400L) return false
        lastScreenScanAt = nowMs

        val visible = visibleText(rootInActiveWindow).lowercase()
        // Each phrase may list alternatives separated by "|" (e.g. English|Spanish):
        // the phrase counts as visible if ANY alternative is on screen.
        val hit = entries.firstOrNull { e ->
            e.allOf.isNotEmpty() && e.allOf.all { phrase ->
                phrase.split('|').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.any { visible.contains(it) }
            }
        } ?: return false
        block(pkg, t("This settings screen is protected: ${hit.label}.", "Esta pantalla de ajustes está protegida: ${hit.label}."), VerseTheme.PURITY, false, pressBack = true)
        return true
    }

    /** Word/page blocker and SafeSearch for a browser event. */
    private fun checkBrowser(s: AppState, pkg: String, event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            // Typed text comes with the event itself: no screen walk needed.
            if (Feature.KEYWORD_BLOCKER in s.features && matcherFor(s).isBlockedText(event.text?.joinToString(" "))) {
                block(pkg, t("This content is blocked.", "Este contenido está bloqueado."), VerseTheme.PURITY, false, pressBack = true)
            }
        } else {
            checkBrowserUrl(s, pkg)
        }
    }

    /**
     * Reads the browser's address bar and applies the word/page blocker and
     * SafeSearch. Throttled to once every 300 ms (also run by the 1.5 s poll,
     * so a page that loads during the throttle is still checked).
     */
    private fun checkBrowserUrl(s: AppState, pkg: String) {
        val words = Feature.KEYWORD_BLOCKER in s.features
        val safe = Feature.SAFE_SEARCH in s.features
        if (!words && !safe) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastUrlScanAt < 300L) return
        lastUrlScanAt = nowMs
        val url = Browsers.readUrl(rootInActiveWindow, pkg) ?: return

        if (words && matcherFor(s).isBlockedUrl(url)) {
            block(pkg, t("This content is blocked.", "Este contenido está bloqueado."), VerseTheme.PURITY, false, pressBack = true)
            return
        }

        if (safe) {
            val strict = SafeSearch.rewrite(url) ?: return
            val last = lastRewrite
            if (last != null && last.first == strict && nowMs - last.second < 5_000L) return // avoid loops
            lastRewrite = strict to nowMs
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(strict)).setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    // ------------------------------------------------------------------ ticker

    private fun onTick() {
        val screenOn = power.isInteractive
        Graph.usage.tick(screenOn)
        // Re-check the app in front so a limit or schedule takes effect while it's open.
        val pkg = currentPkg
        val s = Graph.store.current
        val blocked = screenOn && pkg != null && s.protectionActive && checkApp(s, pkg)
        // "Time left today" notification for time-limited apps (if switched on).
        LimitCountdown.update(this, Graph.store.current, if (blocked) null else pkg, screenOn)
        // Full sync once a minute (applies due changes, ends install windows…).
        if (++tickCount % 4 == 0) Graph.enforcer.syncAsync()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Leaves the blocked content and shows the block screen.
     * pressBack = true first navigates back (e.g. off a web page).
     *
     * WHY NO "HOME" FIRST: Android has an "app switch protection": right after
     * the Home button is pressed, it silently refuses activity starts from
     * background apps for a few seconds. Pressing Home and then opening the
     * block screen therefore made the block screen never appear. Instead, the
     * block screen opens directly ON TOP of the blocked app, and its Exit
     * button goes home. If it still didn't appear (checked after 1 s), we fall
     * back to pressing Home so the blocked content is never left on screen.
     */
    private fun block(pkg: String, reason: String, theme: VerseTheme, canExtend: Boolean, pressBack: Boolean) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastBlockAt < 1_500L) {
            // Too soon after the last block (e.g. the app was reopened instantly).
            // Forget the current app so the next event or poll checks it again,
            // instead of letting it through.
            currentPkg = null
            return
        }
        lastBlockAt = nowMs
        if (pressBack) performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            runCatching { BlockActivity.launch(this, reason, theme, pkg, canExtend) }
            handler.postDelayed({
                if (rootInActiveWindow?.packageName?.toString() != packageName) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }, 1_000L)
        }, if (pressBack) 150L else 0L)
    }

    private fun matcherFor(s: AppState): ContentMatcher {
        val key = s.keywords to s.domains
        // Identity check (===): saved lists are never modified in place, so a new
        // list object means an edit. Avoids comparing every word on every event.
        val old = matcherKey
        if (matcher == null || old == null || old.first !== key.first || old.second !== key.second) {
            matcher = ContentMatcher(s.keywords, s.domains)
            matcherKey = key
        }
        return matcher!!
    }

    /** All visible text on screen (up to 400 elements), joined. Memory only. */
    private fun visibleText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val n = queue.removeFirst()
            visited++
            n.text?.let { sb.append(it).append('\n') }
            n.contentDescription?.let { sb.append(it).append('\n') }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return sb.toString()
    }

    companion object {
        private const val TICK_MS = 15_000L
        private const val FOREGROUND_POLL_MS = 1_500L

        /**
         * The running service, or null when accessibility is off. Used only so
         * AdminReceiver can ask it to leave the deactivation screen.
         */
        @Volatile
        var instance: BlockerService? = null
            private set
    }
}
