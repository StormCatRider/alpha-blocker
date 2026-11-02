// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * SafeSearch.kt — forces strict SafeSearch by rewriting search URLs
 *
 * When the address bar shows a search page WITHOUT the engine's strict
 * setting, [rewrite] returns the same URL with that setting added; the blocker
 * service then reopens it in the same browser.
 *
 *   Engine       Parameter added
 *   Google       safe=active
 *   Bing         adlt=strict
 *   DuckDuckGo   kp=1
 *   Brave Search safesearch=strict
 *   Yahoo        vm=r
 *
 * YouTube Restricted Mode can't be forced through the URL. For that, use a
 * family DNS (e.g. CleanBrowsing Family) as Private DNS — see the DNS filter note.
 *
 * This is best-effort: Chrome shows only the search words in the address bar
 * and Firefox shows only the site name, so the full URL often isn't visible.
 * That's why there are two more layers:
 *   * Device Owner: Chrome/Brave get the ForceGoogleSafeSearch policy
 *     (enforcement/PolicyManager.kt, applyBrowserPolicies).
 *   * DNS: a family DNS (Private DNS) forces SafeSearch for EVERY browser/app.
 * The word blocker still checks the visible search words, and DNS filtering
 * is the strongest layer.
 * =============================================================================
 */
package com.alphablocker.core

import android.net.Uri

object SafeSearch {

    /** Returns a strict-mode version of [raw], or null if no change is needed. */
    fun rewrite(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val uri = ContentMatcher.parseUrl(raw) ?: return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path ?: ""
        return when {
            isGoogle(host) && path.startsWith("/search") -> enforce(uri, "safe", "active")
            (host == "bing.com" || host.endsWith(".bing.com")) && path.startsWith("/search") -> enforce(uri, "adlt", "strict")
            (host == "duckduckgo.com" || host.endsWith(".duckduckgo.com")) && uri.getQueryParameter("q") != null -> enforce(uri, "kp", "1")
            host == "search.brave.com" && path.startsWith("/search") -> enforce(uri, "safesearch", "strict")
            host.endsWith("search.yahoo.com") && path.startsWith("/search") -> enforce(uri, "vm", "r")
            else -> null
        }
    }

    /** google.com, www.google.com, google.co.uk, www.google.de … */
    private fun isGoogle(host: String): Boolean =
        host.startsWith("google.") || host.startsWith("www.google.")

    /** Sets [key]=[value], keeping all other parameters. Null if already set. */
    private fun enforce(uri: Uri, key: String, value: String): String? {
        if (uri.getQueryParameter(key) == value) return null
        val builder = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name == key) continue
            for (v in uri.getQueryParameters(name)) builder.appendQueryParameter(name, v)
        }
        builder.appendQueryParameter(key, value)
        return builder.build().toString()
    }
}
