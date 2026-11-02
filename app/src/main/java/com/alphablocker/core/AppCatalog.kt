// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * AppCatalog.kt — finds installed apps, browsers and "never block" apps
 *
 * Uses only the package-visibility <queries> declared in the manifest:
 * launcher apps, apps that open https links, and home-screen apps.
 * Nothing about your apps is stored except the rules you create.
 * =============================================================================
 */
package com.alphablocker.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager

/** An installed app shown in the app picker. */
data class AppEntry(val pkg: String, val label: String)

object AppCatalog {

    // Browser list is cached for 60 s because the blocker asks very often.
    @Volatile private var browserCache: Set<String> = emptySet()
    @Volatile private var browserCacheAt = 0L

    /** All apps with a launcher icon, sorted by name, excluding Alpha Blocker. */
    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    /** Every installed app that can open web links (i.e. every browser). */
    fun browsers(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        if (now - browserCacheAt < 60_000L && browserCache.isNotEmpty()) return browserCache
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val found = context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .toSet()
        browserCache = found
        browserCacheAt = now
        return found
    }

    /** Forces the browser list to refresh (call after an app is installed). */
    fun invalidate() { browserCacheAt = 0L; criticalCache = null }

    /**
     * Apps that must NEVER be blocked or suspended, so the phone stays usable
     * and emergency calls always work:
     *   Alpha Blocker itself, the home screen, the phone/dialer app, the
     *   system UI and Settings.
     */
    fun criticalPackages(context: Context): Set<String> {
        // Cached for 60 s: this is asked on every app switch and asks Android
        // for the home screen and dialer apps, which rarely change.
        val now = System.currentTimeMillis()
        criticalCache?.let { (at, set) -> if (now - at < 60_000L) return set }
        val result = computeCritical(context)
        criticalCache = now to result
        return result
    }

    @Volatile private var criticalCache: Pair<Long, Set<String>>? = null

    private fun computeCritical(context: Context): Set<String> {
        val pm = context.packageManager
        val set = mutableSetOf(context.packageName, "com.android.systemui", "com.android.settings", "android")
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, 0).forEach { set += it.activityInfo.packageName }
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { set += it }
        set += "com.google.android.dialer"
        return set
    }

    /** Display name for a package, falling back to the package name. */
    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
