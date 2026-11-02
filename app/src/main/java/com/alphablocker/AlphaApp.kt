// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * AlphaApp.kt — app entry point (runs before any screen or service)
 *
 * Creates shared objects (Graph), the notification channel used for the
 * "blocker paused" warning, and arms the watchdog alarm.
 * =============================================================================
 */
package com.alphablocker

import com.alphablocker.core.t
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.alphablocker.enforcement.WatchdogReceiver

class AlphaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, t("Protection alerts", "Alertas de protección"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = t("Warns you when protection is paused.", "Te avisa cuando la protección está en pausa.")
            },
        )

        // Quiet channel (no sound, no pop-up) for the "time left today" countdown.
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_COUNTDOWN, t("Time-limit countdown", "Cuenta atrás del límite de tiempo"), NotificationManager.IMPORTANCE_LOW).apply {
                description = t("Shows the time left today while a time-limited app is open.", "Muestra el tiempo restante hoy mientras una app con límite está abierta.")
            },
        )

        WatchdogReceiver.schedule(this)
        Graph.enforcer.syncAsync()
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_COUNTDOWN = "countdown"
    }
}
