// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * BankPause.kt — "Banking pause": open a bank app with the blocker paused
 *
 * WHY: many bank apps refuse to run while an app that wasn't installed from
 * the Play Store has accessibility access (that's how banking malware works).
 * They don't look at what the app does. So Alpha Blocker can briefly switch
 * its own blocker service off, open the bank, then switch it back on.
 *
 * OPT-IN: off by default. Turn it on and pick your bank apps on the Protect
 * tab. Turning it on or adding a bank app is LOOSENING (waits the delay);
 * turning it off or removing a bank app is instant.
 *
 * TIMELINE (from tapping "Open" on the Status tab). No daily limit.
 * N = your pause length, 2, 3 or 4 minutes (Protect › Banking pause; default 4;
 * shorter is instant, longer waits the delay):
 *   0:00   Blocker service off, bank opens. A notification shows the countdown
 *          and "Turn protection back on".
 *          Device Owner: every browser and app with a rule is suspended FIRST.
 *   N      Popup: "Turn protection back on" or "Give me 2 more minutes".
 *   N + 2  Protection turns itself back on, and a second popup says so.
 * The pause also ends at the next sync after N + 2 and after a restart, in case
 * the timers below were cancelled by Android.
 *
 * REQUIREMENT (checked by [problem]): the ADB-granted WRITE_SECURE_SETTINGS
 * (switches the service off and back on: the same grant as "Auto re-enable").
 * Works in Device Owner and Device Admin mode.
 *
 * What stays protected during the pause:
 *   * Device Owner: uninstall, force stop, clear storage, Private DNS,
 *     developer options, factory reset, and browsers + rule apps (suspended).
 *   * Device Admin: uninstall (device admin) only. Browsers can't be paused
 *     without Device Owner, so NOTHING is blocked for those minutes, which is
 *     why the pause is short and always ends on its own.
 * Neither mode: protected settings screens and the word blocker (they need
 * the blocker service).
 *
 * POPUPS: Android lets a Device Owner open a screen from the background, so
 * both popups appear directly. In Device Admin mode the 4-minute one shows as
 * a heads-up notification (with the same two buttons), and the "back on" one
 * is opened by the blocker service as soon as it restarts (Android allows a
 * running accessibility service to open screens).
 * =============================================================================
 */
package com.alphablocker.enforcement

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.alphablocker.AlphaApp
import com.alphablocker.Graph
import com.alphablocker.R
import com.alphablocker.core.t
import com.alphablocker.data.AppState
import com.alphablocker.ui.ResumeActivity

object BankPause {

    /** Time until the "turn protection back on" popup: your setting, 2–4 minutes. */
    fun promptMs(s: AppState): Long = s.bankPauseMinutes.coerceIn(2, 4) * 60_000L

    /** The "2 more minutes" after the popup; then protection turns on by itself. */
    const val GRACE_MS = 2 * 60_000L

    /** Longest a pause can last. */
    fun totalMs(s: AppState): Long = promptMs(s) + GRACE_MS

    private const val NOTIFY_ID = 2
    internal const val ACTION_PROMPT = "com.alphablocker.BANK_PAUSE_PROMPT"
    internal const val ACTION_RESUME = "com.alphablocker.BANK_PAUSE_RESUME"   // you tapped "turn back on"
    internal const val ACTION_TIME_UP = "com.alphablocker.BANK_PAUSE_TIME_UP" // pause + 2 minutes are over
    internal const val ACTION_MORE = "com.alphablocker.BANK_PAUSE_MORE"       // "2 more minutes"

    private val handler = Handler(Looper.getMainLooper())
    private val promptRunnable = Runnable { appContext?.let { prompt(it) } }
    private val resumeRunnable = Runnable { Graph.enforcer.endBankPause(timeUp = true) }
    @Volatile private var appContext: Context? = null

    /** Set when time ran out; the blocker service shows the "back on" popup when it restarts. */
    @Volatile private var backOnPopupPending = false

    /**
     * Why a banking pause can't start right now, or null if it can.
     * Shown to you as-is.
     */
    fun problem(context: Context, s: AppState, now: Long): String? = when {
        !s.bankPauseEnabled -> t(
            "Banking pause is off. Turn it on in Protect › Banking pause.",
            "La pausa para bancos está apagada. Actívala en Proteger › Pausa para bancos.",
        )
        !Enforcer.canAutoReEnable(context) -> t(
            "Banking pause needs the auto re-enable permission (one ADB command, see the Status tab).",
            "La pausa para bancos necesita el permiso de reactivación automática (un comando ADB, ver la pestaña Estado).",
        )
        s.bankPauseUntil > now -> t("A banking pause is already running.", "Ya hay una pausa para bancos en curso.")
        else -> null
    }

    /** True while a pause is running. */
    fun isActive(s: AppState, now: Long): Boolean = s.bankPauseUntil > now

    /** Called by Enforcer right after the service was switched off. */
    fun onStarted(context: Context) {
        // Timings come from the pause's saved end time, so changing the setting
        // during a pause can't stretch it.
        val totalMs = msLeft()
        val promptMs = (totalMs - GRACE_MS).coerceAtLeast(0L)
        val app = context.applicationContext
        appContext = app
        // Two timers each: an in-app timer (precise, while the app's process lives)
        // and an alarm (survives the process being stopped; Android may delay it
        // by a few minutes). Running twice is harmless.
        handler.removeCallbacks(promptRunnable); handler.removeCallbacks(resumeRunnable)
        handler.postDelayed(promptRunnable, promptMs)
        handler.postDelayed(resumeRunnable, totalMs)
        val am = app.getSystemService(AlarmManager::class.java)
        val start = SystemClock.elapsedRealtime()
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, start + promptMs, broadcast(app, ACTION_PROMPT))
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, start + totalMs, broadcast(app, ACTION_TIME_UP))
        notify(app, promptNow = false)
    }

    /** Called by Enforcer when the pause ends: clears timers, notification and popup. */
    fun onEnded(context: Context) {
        val app = context.applicationContext
        handler.removeCallbacks(promptRunnable); handler.removeCallbacks(resumeRunnable)
        val am = app.getSystemService(AlarmManager::class.java)
        am.cancel(broadcast(app, ACTION_PROMPT))
        am.cancel(broadcast(app, ACTION_TIME_UP))
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID)
        ResumeActivity.closeIfOpen()
    }

    /**
     * Time ran out and protection is back on: show the "Protection is back on"
     * popup. Tried now (works for a Device Owner) and again by the blocker
     * service when it restarts (works in every mode), see [consumeBackOnPopup].
     */
    fun showBackOn(context: Context) {
        backOnPopupPending = true
        runCatching { context.startActivity(ResumeActivity.backOnIntent(context)) }
    }

    /** Called by the blocker service when it starts: true once if the "back on" popup is due. */
    fun consumeBackOnPopup(): Boolean {
        val due = backOnPopupPending
        backOnPopupPending = false
        return due
    }

    /** "Give me 2 more minutes": back to the quiet countdown notification. */
    fun moreTime(context: Context) {
        notify(context.applicationContext, promptNow = false)
        ResumeActivity.closeIfOpen()
    }

    /**
     * The end-of-pause "bubble": a heads-up notification plus the small popup
     * screen (ResumeActivity). Android lets a Device Owner open a screen from
     * the background; if it can't (Device Admin), the heads-up notification
     * with the same two buttons is the popup.
     */
    fun prompt(context: Context) {
        val s = Graph.store.current
        if (!isActive(s, Graph.clock.now())) return
        notify(context, promptNow = true)
        runCatching {
            context.startActivity(Intent(context, ResumeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Milliseconds until protection turns back on by itself (0 if no pause). */
    fun msLeft(): Long = (Graph.store.current.bankPauseUntil - Graph.clock.now()).coerceAtLeast(0L)

    private fun notify(context: Context, promptNow: Boolean) {
        val resume = broadcast(context, ACTION_RESUME)
        val endsAtWallClock = System.currentTimeMillis() + msLeft()
        val owner = runCatching { Graph.enforcer.policy.isDeviceOwner() }.getOrDefault(false)
        val text = when {
            promptNow -> t(
                "Your ${Graph.store.current.bankPauseMinutes} minutes are up. Turn protection back on, or take 2 more minutes; then it turns on by itself.",
                "Se acabaron tus ${Graph.store.current.bankPauseMinutes} minutos. Vuelve a activar la protección o toma 2 minutos más; luego se activa sola.",
            )
            owner -> t(
                "Browsers and apps with rules are paused meanwhile. Protection comes back on by itself.",
                "Mientras tanto, los navegadores y las apps con reglas están en pausa. La protección vuelve sola.",
            )
            else -> t(
                "Nothing is blocked during the pause. Protection comes back on by itself.",
                "Nada está bloqueado durante la pausa. La protección vuelve sola.",
            )
        }
        val n = NotificationCompat.Builder(context, AlphaApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (promptNow) t("Turn protection back on", "Vuelve a activar la protección") else t("Banking pause", "Pausa para bancos"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(!promptNow)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(endsAtWallClock).setUsesChronometer(true).setChronometerCountDown(true)
            .setContentIntent(if (promptNow) openPopup(context) else resume)
            .addAction(0, t("Turn protection back on", "Activar protección"), resume)
            .apply { if (promptNow) addAction(0, t("2 more minutes", "2 minutos más"), broadcast(context, ACTION_MORE)) }
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFY_ID, n) }
    }

    private fun openPopup(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, ResumeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Explicit, immutable broadcast to our own non-exported receiver. */
    private fun broadcast(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, BankPauseReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Receives the two pause alarms and the notification's "Turn protection back
 * on" and "2 more minutes" buttons. Not exported: only this app's own PendingIntents reach it.
 */
class BankPauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BankPause.ACTION_PROMPT -> BankPause.prompt(context.applicationContext)
            BankPause.ACTION_RESUME -> Graph.enforcer.endBankPause()
            BankPause.ACTION_TIME_UP -> Graph.enforcer.endBankPause(timeUp = true)
            BankPause.ACTION_MORE -> BankPause.moreTime(context)
        }
    }
}
