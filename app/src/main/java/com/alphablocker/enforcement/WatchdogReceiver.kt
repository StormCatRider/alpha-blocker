// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * WatchdogReceiver.kt — re-applies protection when nothing else is running
 *
 * Triggered by:
 *   * BOOT_COMPLETED        phone restarted
 *   * MY_PACKAGE_REPLACED   Alpha Blocker was updated
 *   * a repeating alarm     roughly every 15 minutes (Android may batch it)
 * Each trigger runs Enforcer.sync() and re-arms the alarm. After a restart it
 * also ends any banking pause (BankPause.kt).
 * =============================================================================
 */
package com.alphablocker.enforcement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.alphablocker.Graph

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync() // keep the process alive until sync finishes
        Thread {
            try {
                // A banking pause never survives a restart (its timers don't either).
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) runCatching { Graph.enforcer.endBankPauseNow() }
                runCatching { Graph.enforcer.sync() }
                schedule(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val ACTION_TICK = "com.alphablocker.WATCHDOG_TICK"

        /** Arms (or re-arms) the repeating watchdog alarm. Safe to call repeatedly. */
        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WatchdogReceiver::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pi,
            )
        }
    }
}
