// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * LimitCountdown.kt — "time left today" notification for time-limited apps
 *
 * While an app with a daily time limit is in front, a quiet notification
 * shows a live countdown of the minutes left today (limit + any extension,
 * minus what's been used). It disappears when you leave the app, when the
 * screen turns off, or when the limit is reached (the block screen takes over).
 *
 * Optional: Apps tab › "Time-left notification" (on by default). It's a
 * display setting, not protection, so switching it is always instant.
 *
 * Android counts the seconds down by itself (chronometer), so the app only
 * re-posts the notification when the real remaining time drifts by more than
 * a few seconds (checked on every app switch and every 15-second tick).
 * Nothing is stored; the notification is local and never leaves the phone.
 * =============================================================================
 */
package com.alphablocker.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.alphablocker.AlphaApp
import com.alphablocker.Graph
import com.alphablocker.R
import com.alphablocker.core.t
import com.alphablocker.data.AppState
import kotlin.math.abs

object LimitCountdown {

    private const val NOTIFY_ID = 3

    /** What's shown now: package + wall-clock end time, to avoid needless re-posts. */
    private var shownPkg: String? = null
    private var shownEndsAt = 0L

    /**
     * Shows, updates or hides the countdown for [pkg] (the app in front, or
     * null). Call on every app switch and every tick.
     */
    fun update(context: Context, s: AppState, pkg: String?, screenOn: Boolean) {
        val rule = pkg?.let { p -> s.appRules.firstOrNull { it.pkg == p } }
        val limit = rule?.dailyLimitMinutes
        if (!s.showLimitCountdown || !s.protectionActive || !screenOn || rule == null || limit == null || rule.alwaysBlocked) {
            hide(context); return
        }
        val leftMs = limit * 60_000L + Graph.usage.extraMs(rule.pkg) - Graph.usage.usedMs(rule.pkg)
        if (leftMs <= 0) { hide(context); return }

        val endsAt = System.currentTimeMillis() + leftMs
        if (shownPkg == rule.pkg && abs(endsAt - shownEndsAt) < 5_000L) return // still accurate

        val n = NotificationCompat.Builder(context, AlphaApp.CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(t("${rule.label}: time left today", "${rule.label}: tiempo restante hoy"))
            .setContentText(t("Daily limit: $limit min", "Límite diario: $limit min"))
            .setWhen(endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFY_ID, n) }
        shownPkg = rule.pkg
        shownEndsAt = endsAt
    }

    fun hide(context: Context) {
        if (shownPkg == null) return
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID) }
        shownPkg = null
        shownEndsAt = 0L
    }
}
