// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * TrustedClock.kt — a clock that changing the phone's date can't fool
 *
 * WHY: delays and lock periods are measured in time. If they used the normal
 * clock, you could set the date forward a week and skip a 72-hour delay.
 *
 * HOW:
 *  * While the phone is running, time is measured with
 *    SystemClock.elapsedRealtime(), which counts real time since boot and
 *    CANNOT be changed by the user.
 *  * The trusted time is saved every minute. After a reboot (elapsed time
 *    restarts at 0) we add how long the phone was off, using the wall clock
 *    difference; if the wall clock went backwards, we add nothing.
 *
 * KNOWN LIMIT (documented in README):
 *  * In Device Admin mode, someone could switch the phone off, change the date
 *    forward right after booting, and gain time. In Device Owner mode this is
 *    closed: the app sets DISALLOW_CONFIG_DATE_TIME, and Pixels take the time
 *    from the network.
 *
 * Calendar-based things (schedules, "minutes today") still use the normal
 * local clock, because they are tied to your real day.
 * =============================================================================
 */
package com.alphablocker.core

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

class TrustedClock(private val context: Context) {

    private val prefs = context.getSharedPreferences("trusted_clock", Context.MODE_PRIVATE)
    private val lock = Any()

    // The "anchor" is the last checkpoint: trusted time + raw clocks at that moment.
    private var anchorTrusted: Long
    private var anchorElapsed: Long
    private var anchorWall: Long
    private var anchorBoot: Int

    init {
        if (prefs.contains("trusted")) {
            anchorTrusted = prefs.getLong("trusted", 0L)
            anchorElapsed = prefs.getLong("elapsed", 0L)
            anchorWall = prefs.getLong("wall", 0L)
            anchorBoot = prefs.getInt("boot", -1)
        } else {
            // First ever start: trust the current clock as the starting point.
            anchorTrusted = System.currentTimeMillis()
            anchorElapsed = SystemClock.elapsedRealtime()
            anchorWall = anchorTrusted
            anchorBoot = bootCount()
            persist()
        }
    }

    /** Current trusted time in milliseconds. Safe to call from any thread. */
    fun now(): Long = synchronized(lock) {
        val elapsed = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        val boot = bootCount()
        val sameBoot = (boot == anchorBoot || boot == -1) && elapsed >= anchorElapsed

        val trusted = if (sameBoot) {
            anchorTrusted + (elapsed - anchorElapsed)          // untouchable monotonic time
        } else {
            anchorTrusted + maxOf(0L, wall - anchorWall)      // phone was off: add downtime
        }

        // Re-anchor after a reboot, or once a minute, so a crash loses little.
        if (!sameBoot || elapsed - anchorElapsed > 60_000L) {
            anchorTrusted = trusted
            anchorElapsed = elapsed
            anchorWall = wall
            anchorBoot = boot
            persist()
        }
        trusted
    }

    /**
     * Converts a trusted time to an approximate normal clock time, for display
     * ("unlocks Tue 3:00 PM").
     */
    fun toWall(trusted: Long): Long = System.currentTimeMillis() + (trusted - now())

    /** Converts a normal clock time (e.g. a chosen date) to trusted time. */
    fun fromWall(wall: Long): Long = now() + (wall - System.currentTimeMillis())

    private fun persist() {
        prefs.edit()
            .putLong("trusted", anchorTrusted)
            .putLong("elapsed", anchorElapsed)
            .putLong("wall", anchorWall)
            .putInt("boot", anchorBoot)
            .apply()
    }

    /** Android's reboot counter, or -1 if unavailable. */
    private fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
