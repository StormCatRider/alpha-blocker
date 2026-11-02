// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * PolicyManager.kt — system-level lockdown (Device Owner mode only)
 *
 * Every call here uses Android's official DevicePolicyManager API, which only
 * works because Alpha Blocker is the Device Owner. Each restriction lists the
 * loophole it closes.
 *
 * Idempotent: [apply] can be called any number of times; it compares the
 * wanted state with the current one and fixes differences. It is called on
 * every sync (about once a minute, and after every settings change).
 *
 * "Ours only": we record which apps WE suspended, which VPN and DNS WE set,
 * and only ever undo those — never settings you made yourself.
 * =============================================================================
 */
package com.alphablocker.enforcement

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import com.alphablocker.core.AppCatalog
import com.alphablocker.data.AppState
import com.alphablocker.data.Feature
import com.alphablocker.data.Store

/**
 * Blocks "app clone" profiles (Android 14+). Android hides this constant from
 * the public SDK, so it can't be referenced as UserManager.DISALLOW_ADD_CLONE_PROFILE;
 * this is its internal value from the Android source code. If a phone doesn't accept it, the
 * call is wrapped in runCatching (see apply) and simply has no effect.
 */
private const val DISALLOW_ADD_CLONE_PROFILE = "no_add_clone_profile"

/** Chromium-based browsers that receive the SafeSearch policy (see applyBrowserPolicies). */
private val CHROMIUM_BROWSERS = listOf("com.android.chrome", "com.brave.browser")

class PolicyManager(private val context: Context, private val store: Store) {

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = AdminReceiver.component(context)
    private val self = context.packageName

    /** True if Alpha Blocker is Device Owner. */
    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(self)

    /** True if Alpha Blocker is an active Device Admin (Device Owners are too). */
    fun isAdminActive(): Boolean = dpm.isAdminActive(admin)

    /** Every restriction this app may manage. Others are never touched. */
    private val managedRestrictions: List<String> = buildList {
        add(UserManager.DISALLOW_FACTORY_RESET)              // no "erase all data" to escape
        add(UserManager.DISALLOW_SAFE_BOOT)                  // safe mode would skip the blocker
        add(UserManager.DISALLOW_ADD_USER)                   // a new user profile has no blocker
        add(UserManager.DISALLOW_ADD_MANAGED_PROFILE)        // same for work profiles
        add(UserManager.DISALLOW_CONFIG_DATE_TIME)           // date changes could fool timers
        add(UserManager.DISALLOW_INSTALL_APPS)               // install window control
        add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)    // blocks sideloading / F-Droid installs
        add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
        add(UserManager.DISALLOW_DEBUGGING_FEATURES)         // no ADB to strip protection
        add(UserManager.DISALLOW_CONFIG_VPN)                 // never wanted now; kept so older versions' setting is cleared
        add(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)         // no changing Private DNS
        if (Build.VERSION.SDK_INT >= 34) add(DISALLOW_ADD_CLONE_PROFILE)               // app clones
        if (Build.VERSION.SDK_INT >= 35) add(UserManager.DISALLOW_ADD_PRIVATE_PROFILE) // Private Space
    }

    /** Which restrictions should be ON for state [s] at trusted time [now]. */
    private fun wantedRestrictions(s: AppState, now: Long): Set<String> {
        if (!s.protectionActive) return emptySet()
        val f = s.features
        return buildSet {
            add(UserManager.DISALLOW_FACTORY_RESET)
            add(UserManager.DISALLOW_SAFE_BOOT)
            add(UserManager.DISALLOW_ADD_USER)
            add(UserManager.DISALLOW_ADD_MANAGED_PROFILE)
            add(UserManager.DISALLOW_CONFIG_DATE_TIME)
            if (Build.VERSION.SDK_INT >= 34) add(DISALLOW_ADD_CLONE_PROFILE)
            if (Build.VERSION.SDK_INT >= 35) add(UserManager.DISALLOW_ADD_PRIVATE_PROFILE)
            if (Feature.BLOCK_INSTALLS in f && now >= s.installWindowUntil) {
                add(UserManager.DISALLOW_INSTALL_APPS)
                add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }
            if (Feature.BLOCK_DEBUGGING in f) add(UserManager.DISALLOW_DEBUGGING_FEATURES)
            if (s.privateDnsHost != null) add(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        }
    }

    /**
     * Brings the phone in line with [s].
     * @param accessibilityOn if the blocker service is OFF while protection is
     *        on, we "fail closed": browsers and rule apps are suspended until
     *        the service is switched back on.
     */
    fun apply(s: AppState, accessibilityOn: Boolean, now: Long) {
        if (!isDeviceOwner()) return

        // ---- 1. User restrictions -------------------------------------------
        // Only change restrictions that differ from what's already set (this runs
        // every minute; unchanged restrictions cost no system calls).
        val wanted = wantedRestrictions(s, now)
        val current = runCatching { dpm.getUserRestrictions(admin) }.getOrNull()
        for (r in managedRestrictions) {
            val isOn = current?.getBoolean(r, false)
            val wantOn = r in wanted
            if (isOn == wantOn) continue
            runCatching {
                if (wantOn) dpm.addUserRestriction(admin, r) else dpm.clearUserRestriction(admin, r)
            }
        }

        val active = s.protectionActive

        // ---- 2. Block uninstalling Alpha Blocker -----------------------------
        runCatching { dpm.setUninstallBlocked(admin, self, active) }

        // ---- 3. No "Force stop" / "Clear storage" for Alpha Blocker (Android 11+)
        if (Build.VERSION.SDK_INT >= 30) {
            val protectedPkgs = if (active) listOf(self) else emptyList()
            runCatching { dpm.setUserControlDisabledPackages(admin, protectedPkgs) }
        }

        // ---- 4. Undo the always-on VPN an older version may have set --------
        clearLegacyVpn(s)

        // ---- 5. Private DNS: the family DNS filter, set and locked -----------
        applyPrivateDns(s, if (active) s.privateDnsHost else null)

        // ---- 6. Suspend blocked apps ----------------------------------------
        applySuspensions(s, accessibilityOn)

        // ---- 7. Force SafeSearch in Chromium browsers (managed policy) ------
        applyBrowserPolicies(active && Feature.SAFE_SEARCH in s.features)
    }

    /**
     * Uses Chrome's official enterprise policies (Android "managed app
     * configuration", which only a Device Owner can set):
     *   ForceGoogleSafeSearch = true  -> Google always uses SafeSearch
     *   ForceYouTubeRestrict  = 1     -> YouTube (in the browser) uses
     *                                    Restricted Mode, moderate (2 = strict)
     * Brave is built on Chromium and is sent the same settings; it may or may
     * not honor them. Firefox-based browsers and DuckDuckGo don't support this
     * on Android, so for them rely on the DNS filter (see README).
     * The browser picks the change up the next time it starts.
     * When protection/SafeSearch is off, the settings are cleared again.
     */
    private fun applyBrowserPolicies(enforce: Boolean) {
        val wanted = Bundle().apply {
            if (enforce) {
                putBoolean("ForceGoogleSafeSearch", true)
                putInt("ForceYouTubeRestrict", 1)
            }
        }
        for (pkg in CHROMIUM_BROWSERS) {
            runCatching {
                val current = dpm.getApplicationRestrictions(admin, pkg)
                val same = current.getBoolean("ForceGoogleSafeSearch", false) == wanted.getBoolean("ForceGoogleSafeSearch", false) &&
                    current.getInt("ForceYouTubeRestrict", 0) == wanted.getInt("ForceYouTubeRestrict", 0)
                if (!same) dpm.setApplicationRestrictions(admin, pkg, wanted)
            }
        }
    }

    /**
     * VPN support was removed. If an older version locked a VPN app as
     * always-on (and blocked its uninstall), undo exactly that, once.
     * VPN settings you made yourself are never touched.
     */
    private fun clearLegacyVpn(s: AppState) {
        val legacy = s.managedVpn ?: return
        val current = runCatching { dpm.getAlwaysOnVpnPackage(admin) }.getOrNull()
        if (current == legacy) runCatching { dpm.setAlwaysOnVpnPackage(admin, null, false) }
        runCatching { dpm.setUninstallBlocked(admin, legacy, false) }
        store.update { it.copy(managedVpn = null) }
    }

    private fun applyPrivateDns(s: AppState, wantedHost: String?) {
        if (wantedHost != null) {
            val current = runCatching { dpm.getGlobalPrivateDnsHost(admin) }.getOrNull()
            if (current != wantedHost) {
                // May take a moment: Android checks the host answers DNS-over-TLS.
                runCatching { dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, wantedHost) }
            }
            if (s.managedDnsHost != wantedHost) store.update { it.copy(managedDnsHost = wantedHost) }
        } else if (s.managedDnsHost != null) {
            val current = runCatching { dpm.getGlobalPrivateDnsHost(admin) }.getOrNull()
            if (current == s.managedDnsHost) runCatching { dpm.setGlobalPrivateDnsModeOpportunistic(admin) }
            store.update { it.copy(managedDnsHost = null) }
        }
    }

    /**
     * Suspended apps can't be opened at all (Android shows "app paused").
     * Suspended: non-allowed browsers and "always blocked" apps.
     * Fail-closed: if the blocker service is off, allowed browsers and all
     * rule apps are suspended too, since nothing else can watch them.
     * Schedules and limits are normally handled live by the blocker service,
     * so the block screen can show a verse and offer extensions.
     */
    private fun applySuspensions(s: AppState, accessibilityOn: Boolean) {
        val target = mutableSetOf<String>()
        if (s.protectionActive) {
            val browsers = AppCatalog.browsers(context)
            if (Feature.BROWSER_ALLOWLIST in s.features) target += browsers - s.allowedBrowsers.toSet()
            target += s.appRules.filter { it.alwaysBlocked }.map { it.pkg }
            if (!accessibilityOn) {
                target += browsers
                target += s.appRules.map { it.pkg }
            }
            target -= AppCatalog.criticalPackages(context)
        }

        val toSuspend = target - s.suspendedByUs
        val toRelease = s.suspendedByUs - target
        var ours = s.suspendedByUs

        if (toSuspend.isNotEmpty()) {
            val failed = runCatching { dpm.setPackagesSuspended(admin, toSuspend.toTypedArray(), true) }
                .getOrDefault(toSuspend.toTypedArray()).toSet()
            ours = ours + (toSuspend - failed)
        }
        if (toRelease.isNotEmpty()) {
            val failed = runCatching { dpm.setPackagesSuspended(admin, toRelease.toTypedArray(), false) }
                .getOrDefault(emptyArray()).toSet()
            // Uninstalled apps "fail" too; drop them from our list either way.
            ours = ours - toRelease + (failed intersect installedPackages(failed))
        }
        if (ours != s.suspendedByUs) store.update { it.copy(suspendedByUs = ours) }
    }

    private fun installedPackages(pkgs: Set<String>): Set<String> = pkgs.filter {
        runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
    }.toSet()

    /**
     * Fully releases the phone (only allowed when protection is off):
     * clears our restrictions and suspensions, then gives up Device Owner /
     * Device Admin so Alpha Blocker can be uninstalled normally.
     */
    @Suppress("DEPRECATION")
    fun releaseDevice(): Boolean {
        val s = store.current
        if (s.protectionActive) return false
        apply(s, accessibilityOn = true, now = 0L)
        return runCatching {
            if (isDeviceOwner()) dpm.clearDeviceOwnerApp(self) else dpm.removeActiveAdmin(admin)
        }.isSuccess
    }
}
