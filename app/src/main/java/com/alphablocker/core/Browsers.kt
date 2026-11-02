// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Browsers.kt — how to read the address bar of each allowed browser
 *
 * The blocker service asks Android's accessibility API for the on-screen
 * element with a known "view ID" (each browser names its address bar
 * differently). If that fails, it falls back to scanning the top of the
 * screen for text that looks like a web address.
 *
 * MAINTENANCE: when a browser update renames its address bar, detection may
 * stop working for that browser. Fix it by updating the IDs below.
 * You can find the current ID with the "Developer Assistant" or similar
 * accessibility-inspector apps, or `adb shell uiautomator dump`.
 * =============================================================================
 */
package com.alphablocker.core

import android.view.accessibility.AccessibilityNodeInfo

object Browsers {

    /** Address bar view IDs per browser package. Firefox-family browsers share a layout. */
    private val URL_BAR_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.duckduckgo.mobile.android" to listOf(
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
        ),
        "org.mozilla.firefox" to firefoxIds("org.mozilla.firefox"),
        "org.mozilla.fennec_fdroid" to firefoxIds("org.mozilla.fennec_fdroid"),
        "org.ironfoxoss.ironfox" to firefoxIds("org.ironfoxoss.ironfox"),
    )

    private fun firefoxIds(pkg: String) = listOf(
        "$pkg:id/mozac_browser_toolbar_url_view",
        "$pkg:id/mozac_browser_toolbar_edit_url_view",
    )

    /** Looks like a web address: something.tld with optional path. */
    private val URL_LIKE = Regex("""^(https?://)?([a-z0-9-]+\.)+[a-z]{2,}(/\S*)?$""", RegexOption.IGNORE_CASE)

    /**
     * Returns the address-bar text of browser [pkg], or null if not found.
     * [root] is the active window's root node from the accessibility service.
     */
    fun readUrl(root: AccessibilityNodeInfo?, pkg: String): String? {
        if (root == null) return null
        for (id in URL_BAR_IDS[pkg].orEmpty()) {
            val text = root.findAccessibilityNodeInfosByViewId(id)
                .firstNotNullOfOrNull { it.text?.toString()?.takeIf { t -> t.isNotBlank() } }
            if (text != null) return text
        }
        return scanForUrl(root)
    }

    /**
     * Fallback: breadth-first scan of up to 150 on-screen elements for text
     * shaped like a URL. Address bars are near the top of the layout tree, so
     * the limit keeps this fast.
     */
    private fun scanForUrl(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 150) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length < 2000 && URL_LIKE.matches(text)) return text
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }
}
