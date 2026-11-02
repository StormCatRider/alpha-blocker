// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * UsageTracker.kt — counts today's minutes per app, for daily time limits
 *
 * PRIVACY: stores ONLY { today's date, package name -> milliseconds used,
 * extensions used today }. It resets every day; no history is kept.
 * File: shared_prefs/usage.xml in app private storage.
 *
 * How it counts: the blocker service tells us when the foreground app changes
 * and "ticks" every 15 seconds. Time is added to the current app only while
 * the screen is on. Each tick adds at most 60 s, so a missed tick can't create
 * a huge jump.
 * =============================================================================
 */
package com.alphablocker.core

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.time.LocalDate

class UsageTracker(context: Context) {

    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    private var day: String = LocalDate.now().toString()
    private val usedMs = mutableMapOf<String, Long>()
    private val extraMs = mutableMapOf<String, Long>()
    private var extensionsUsed = 0

    private var currentPkg: String? = null
    private var lastMark = SystemClock.elapsedRealtime()
    private var lastSave = 0L

    init { load() }

    /** Called when a different app comes to the front. */
    @Synchronized
    fun onForeground(pkg: String?, screenOn: Boolean) {
        accumulate(screenOn)
        currentPkg = pkg
    }

    /** Called every ~15 s by the blocker service. */
    @Synchronized
    fun tick(screenOn: Boolean) {
        accumulate(screenOn)
        if (SystemClock.elapsedRealtime() - lastSave > 60_000L) save()
    }

    /** Milliseconds [pkg] was used today. */
    @Synchronized
    fun usedMs(pkg: String): Long { rollover(); return usedMs[pkg] ?: 0L }

    /** Extra milliseconds granted to [pkg] today by extensions. */
    @Synchronized
    fun extraMs(pkg: String): Long { rollover(); return extraMs[pkg] ?: 0L }

    /** Extensions used today (shared across all apps). */
    @Synchronized
    fun extensionsUsed(): Int { rollover(); return extensionsUsed }

    /**
     * Grants [minutes] more for [pkg] today if fewer than [perDay] extensions
     * were used. Returns true on success.
     */
    @Synchronized
    fun grantExtension(pkg: String, minutes: Int, perDay: Int): Boolean {
        rollover()
        if (extensionsUsed >= perDay) return false
        extensionsUsed++
        extraMs[pkg] = (extraMs[pkg] ?: 0L) + minutes * 60_000L
        save()
        return true
    }

    // ------------------------------------------------------------ internals

    private fun accumulate(screenOn: Boolean) {
        rollover()
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastMark).coerceIn(0L, 60_000L)
        lastMark = now
        val pkg = currentPkg ?: return
        if (screenOn) usedMs[pkg] = (usedMs[pkg] ?: 0L) + delta
    }

    /** Starts a fresh day at midnight. */
    private fun rollover() {
        val today = LocalDate.now().toString()
        if (today != day) {
            day = today
            usedMs.clear(); extraMs.clear(); extensionsUsed = 0
            save()
        }
    }

    private fun load() {
        val raw = prefs.getString("data", null) ?: return
        runCatching {
            val o = JSONObject(raw)
            if (o.getString("day") != day) return
            o.getJSONObject("used").let { u -> u.keys().forEach { usedMs[it] = u.getLong(it) } }
            o.getJSONObject("extra").let { e -> e.keys().forEach { extraMs[it] = e.getLong(it) } }
            extensionsUsed = o.optInt("ext", 0)
        }
    }

    private fun save() {
        lastSave = SystemClock.elapsedRealtime()
        val o = JSONObject()
            .put("day", day)
            .put("used", JSONObject(usedMs as Map<*, *>))
            .put("extra", JSONObject(extraMs as Map<*, *>))
            .put("ext", extensionsUsed)
        prefs.edit().putString("data", o.toString()).apply()
    }
}
