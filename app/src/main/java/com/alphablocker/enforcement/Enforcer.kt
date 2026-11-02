// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Enforcer.kt — the "sync" loop that keeps everything applied
 *
 * sync() does, in order:
 *   1. apply any pending loosening changes whose delay is over (ChangeGate)
 *   2. end a banking pause whose time is up (BankPause.kt)
 *   3. push the current state to the system (PolicyManager, Device Owner)
 *   4. warn with a notification if the blocker service is switched off
 *
 * It also starts and ends banking pauses, because those switch the blocker
 * service off and on and must suspend browsers before the bank app opens.
 *
 * It is called: after every settings change, every minute by the blocker
 * service, every ~15 min by the watchdog alarm, after boot and after updates.
 * All calls run on one background thread, one at a time.
 * =============================================================================
 */
package com.alphablocker.enforcement

import com.alphablocker.core.t
import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import com.alphablocker.service.BlockerService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import com.alphablocker.AlphaApp
import com.alphablocker.R
import com.alphablocker.core.ChangeGate
import com.alphablocker.core.TrustedClock
import com.alphablocker.data.Store
import java.util.concurrent.Executors

class Enforcer(
    private val context: Context,
    private val store: Store,
    private val clock: TrustedClock,
    private val gate: ChangeGate,
    val policy: PolicyManager,
) {
    private val executor = Executors.newSingleThreadExecutor()

    /** Runs [sync] on the background thread. Safe to call from anywhere. */
    fun syncAsync() { executor.execute { runCatching { sync() } } }

    @Synchronized
    fun sync() {
        gate.processDue()
        val now = clock.now()
        // A banking pause whose time is up ends here too (backstop for the timers in BankPause.kt).
        if (store.current.bankPauseUntil != 0L && now >= store.current.bankPauseUntil) endBankPauseLocked(timeUp = true)
        val paused = store.current.bankPauseUntil > now

        // During a banking pause the service counts as OFF even if Android hasn't
        // caught up yet, so browsers and rule apps stay suspended (fail closed).
        var accessibilityOn = !paused && isAccessibilityOn(context)
        // Blocker service switched off while protection is on? Turn it straight back on,
        // if you granted the permission for that (see reEnableAccessibility).
        if (!paused && !accessibilityOn && store.current.protectionActive && runCatching { reEnableAccessibility() }.getOrDefault(false)) {
            accessibilityOn = isAccessibilityOn(context)
        }
        policy.apply(store.current, accessibilityOn, now)
        // The banking pause has its own notification, so no "paused" warning then.
        updateWarning(store.current.protectionActive && !accessibilityOn && !paused)
    }

    // ------------------------------------------------------- banking pause --

    /**
     * Starts a banking pause for [pkg] (see enforcement/BankPause.kt).
     * Run on a background thread. Order matters:
     *   1. save the pause end time,
     *   2. switch the blocker service off,
     *   3. Device Owner: suspend browsers and rule apps NOW (fail closed;
     *      in Device Admin mode PolicyManager does nothing),
     * and only then does the caller open the bank app.
     * @return null if the bank app can be opened, or the reason it can't.
     */
    @Synchronized
    fun startBankPause(pkg: String): String? {
        val s = store.current
        BankPause.problem(context, s, clock.now())?.let { return it }
        if (pkg !in s.bankApps) return t("That app isn't on your bank list.", "Esa app no está en tu lista de bancos.")
        // Service already off: there is nothing to pause, just open the bank.
        if (!isAccessibilityOn(context)) return null
        val now = clock.now()
        store.update { it.copy(bankPauseUntil = now + BankPause.totalMs(s)) }
        if (!setAccessibility(enabled = false)) {
            store.update { it.copy(bankPauseUntil = 0L) }
            return t("Couldn't pause the blocker service.", "No se pudo pausar el servicio de bloqueo.")
        }
        policy.apply(store.current, accessibilityOn = false, now = now)
        updateWarning(false)
        BankPause.onStarted(context)
        return null
    }

    /**
     * Ends the banking pause now. Safe from any thread.
     * @param timeUp true when the pause + 2 minutes ran out (shows the "back on" popup),
     *               false when you tapped "Turn protection back on".
     */
    fun endBankPause(timeUp: Boolean = false) { executor.execute { runCatching { endBankPauseNow(timeUp) } } }

    /** Same, but runs on the calling thread (used after a restart). */
    @Synchronized
    fun endBankPauseNow(timeUp: Boolean = false) {
        endBankPauseLocked(timeUp)
        sync()
    }

    @Synchronized
    private fun endBankPauseLocked(timeUp: Boolean) {
        if (store.current.bankPauseUntil == 0L) return
        store.update { it.copy(bankPauseUntil = 0L) }
        runCatching { setAccessibility(enabled = true) }
        BankPause.onEnded(context)
        if (timeUp) BankPause.showBackOn(context)
    }

    /**
     * AUTO RE-ENABLE (optional, needs one ADB command):
     *   adb shell pm grant com.alphablocker android.permission.WRITE_SECURE_SETTINGS
     * With that permission, the app writes itself back into Android's list of
     * enabled accessibility services. So if the blocker is turned off from
     * anywhere (Accessibility settings, "apps with full access" review, the
     * full-access notification…), it comes back within about a second: the
     * service's shutdown triggers a sync, which runs this.
     * Only happens while protection is ON. Other enabled services are kept.
     * @return true if the setting was written.
     */
    private fun reEnableAccessibility(): Boolean = setAccessibility(enabled = true)

    /**
     * Adds (or removes) Alpha Blocker in Android's list of enabled
     * accessibility services. Other enabled services are always kept.
     * Needs the ADB-granted WRITE_SECURE_SETTINGS (see above).
     * Removing is only used by the banking pause.
     * @return true if the setting was written.
     */
    private fun setAccessibility(enabled: Boolean): Boolean {
        if (!canAutoReEnable(context)) return false
        val me = ComponentName(context, BlockerService::class.java)
        val cr = context.contentResolver
        val entries = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').filter { it.isNotBlank() }
        // Entries may be written in short (".service.X") or long form; compare as components.
        val others = entries.filterNot { ComponentName.unflattenFromString(it) == me }
        val wanted = if (enabled) others + me.flattenToString() else others
        if (wanted != entries) {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, wanted.joinToString(":"))
        }
        // Master switch: on when we're on; off only if no other service is left
        // (some bank apps also check this switch).
        if (enabled) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        else if (others.isEmpty()) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        return true
    }

    /** Shows (or clears) a persistent warning when the blocker service is off. */
    private fun updateWarning(show: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!show) { nm.cancel(NOTIFY_ID); return }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, AlphaApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(t("Alpha Blocker is paused", "Alpha Blocker está en pausa"))
            .setContentText(warningText())
            .setStyle(NotificationCompat.BigTextStyle().bigText(warningText()))
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(NOTIFY_ID, n) } // fails silently if notifications are denied
    }

    companion object {
        private const val NOTIFY_ID = 1
        private fun warningText() = t(
            "Turn the Alpha Blocker accessibility service back on. Browsers stay blocked until you do (Device Owner mode).",
            "Vuelve a activar el servicio de accesibilidad de Alpha Blocker. Los navegadores siguen bloqueados hasta entonces (modo Device Owner).",
        )

        /** True if the ADB-granted permission for auto re-enable is present. */
        fun canAutoReEnable(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /**
         * ADB command that grants the auto re-enable permission. Built from the
         * installed app ID, so it stays correct if applicationId is changed.
         * On a secondary user (not the phone's main user) it adds "--user N":
         * the grant belongs to one user, and without it adb grants it to the
         * main user instead of the user Alpha Blocker runs in.
         */
        fun grantCommand(context: Context): String {
            val user = currentUserId()
            val userArg = if (user != 0) "--user $user " else ""
            return "adb shell pm grant $userArg${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        }

        /** Android user number this app runs in (0 = main user, 10, 11… = other users). */
        fun currentUserId(): Int = android.os.Process.myUid() / 100_000

        /** ADB command that makes Alpha Blocker the Device Owner (uses the installed app ID). */
        fun deviceOwnerCommand(context: Context) =
            "adb shell dpm set-device-owner ${AdminReceiver.component(context).flattenToShortString()}"

        /** True if Alpha Blocker's accessibility service is currently enabled. */
        fun isAccessibilityOn(context: Context): Boolean {
            val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        }
    }
}
