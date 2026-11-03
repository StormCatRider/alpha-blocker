// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * StatusScreen.kt — overview: is protection on, health checks, pending changes
 *
 * Pending loosening changes are listed with a countdown and a Cancel button.
 * Cancelling is always instant (it keeps protection stronger).
 * At the bottom: an optional tip link (opens in the browser; the app stays
 * offline and free).
 * If the banking pause is on, each bank app gets an "Open" button here
 * (see enforcement/BankPause.kt).
 * =============================================================================
 */
package com.alphablocker.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alphablocker.Graph
import com.alphablocker.core.AppCatalog
import com.alphablocker.core.t
import com.alphablocker.data.AppState
import com.alphablocker.data.ProtectionMode
import com.alphablocker.enforcement.BankPause
import com.alphablocker.enforcement.Enforcer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StatusScreen(state: AppState) {
    val context = LocalContext.current
    val now by rememberTrustedNow()
    val policy = Graph.enforcer.policy
    var starting by remember { mutableStateOf(false) }
    var showRestrictedHelp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {

        if (BankPause.isActive(state, now)) {
            Section(t("Banking pause", "Pausa para bancos")) {
                Text(
                    t(
                        "Blocker paused. Protection turns back on by itself in ${formatDuration(state.bankPauseUntil - now)}.",
                        "Bloqueador en pausa. La protección se activa sola en ${formatDuration(state.bankPauseUntil - now)}.",
                    ),
                    color = MaterialTheme.colorScheme.secondary,
                )
                AccentButton(t("Turn protection back on", "Activar protección")) { Graph.enforcer.endBankPause() }
            }
        } else if (state.bankPauseEnabled && state.bankApps.isNotEmpty()) {
            Section(t("Banking", "Bancos")) {
                val problem = BankPause.problem(context, state, now)
                if (problem != null) Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (state.mode == ProtectionMode.DEVICE_OWNER) t(
                        "Opens the bank with the blocker paused for ${state.bankPauseMinutes} minutes (2 more if you need them). Browsers and rule apps stay paused meanwhile.",
                        "Abre el banco con el bloqueador en pausa ${state.bankPauseMinutes} minutos (2 más si los necesitas). Mientras tanto, los navegadores y las apps con reglas siguen en pausa.",
                    ) else t(
                        "Opens the bank with the blocker paused for ${state.bankPauseMinutes} minutes (2 more if you need them). Nothing is blocked meanwhile.",
                        "Abre el banco con el bloqueador en pausa ${state.bankPauseMinutes} minutos (2 más si los necesitas). Mientras tanto no se bloquea nada.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                state.bankApps.forEach { pkg ->
                    val label = AppCatalog.label(context, pkg)
                    OutlinedButton(enabled = problem == null, onClick = {
                        scope.launch {
                            // Pausing suspends browsers first, so it runs off the main thread.
                            val error = withContext(Dispatchers.Default) { runCatching { Graph.enforcer.startBankPause(pkg) }.getOrElse { it.message } }
                            if (error != null) {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            } else {
                                // Give Android a moment to finish switching the service off,
                                // so the bank's check at startup sees it off.
                                delay(700)
                                context.packageManager.getLaunchIntentForPackage(pkg)
                                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ?.let { context.startActivity(it) }
                            }
                        }
                    }) { Text(t("Open $label", "Abrir $label")) }
                }
            }
        }

        Section(t("Protection", "Protección")) {
            if (state.protectionActive) {
                Text(t("● Protection is ON", "● La protección está ACTIVA"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            } else {
                Text(t("○ Protection is OFF", "○ La protección está APAGADA"), style = MaterialTheme.typography.titleLarge)
                Text(t(
                    "Review your lists on the Filters and Apps tabs while edits are still instant, then start.",
                    "Revisa tus listas en las pestañas Filtros y Apps mientras los cambios aún son inmediatos, y luego inicia.",
                ))
                AccentButton(t("Start protection", "Iniciar protección")) { starting = true }
            }
            Text(t("Mode: ", "Modo: ") + when (state.mode) {
                ProtectionMode.DEVICE_OWNER -> t("Device Owner (maximum)", "Device Owner (máximo)")
                ProtectionMode.DEVICE_ADMIN -> t("Device Admin (medium)", "Administrador de dispositivo (medio)")
                ProtectionMode.NONE -> t("none", "ninguno")
            })
            Text(t("Loosening delay: ${state.delayHours} hours", "Retraso para aflojar: ${state.delayHours} horas"))
            if (state.protectionActive && state.lockUntil > now) {
                Text(
                    t(
                        "Locked until ${formatTrusted(state.lockUntil)} (${formatDuration(state.lockUntil - now)} left)",
                        "Bloqueado hasta el ${formatTrusted(state.lockUntil)} (faltan ${formatDuration(state.lockUntil - now)})",
                    ),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (state.installWindowUntil > now) {
                Text(t(
                    "Install window open for ${formatDuration(state.installWindowUntil - now)}",
                    "Ventana de instalación abierta por ${formatDuration(state.installWindowUntil - now)}",
                ))
            }
        }

        if (!state.protectionActive) {
            Section(t("Before you start", "Antes de empezar")) { BeforeYouStartChecklist(state) }
        }

        Section(t("Health checks", "Comprobaciones")) {
            val accOn = Enforcer.isAccessibilityOn(context)
            Check(t("Blocker service (accessibility)", "Servicio de bloqueo (accesibilidad)"), accOn)
            if (!accOn) {
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text(t("Turn it on", "Activarlo"))
                }
                TextButton(onClick = { showRestrictedHelp = true }) { Text(t("Help: switch greyed out?", "Ayuda: ¿interruptor gris?")) }
            }
            val autoOn = Enforcer.canAutoReEnable(context)
            Check(t("Auto re-enable blocker service", "Reactivar automáticamente el servicio"), autoOn)
            if (!autoOn) {
                Text(
                    t(
                        "Optional: if the blocker is switched off (e.g. from Security & privacy › apps with full access), it can turn itself back on. Grant it once from a computer:",
                        "Opcional: si se apaga el bloqueador (p. ej. desde Seguridad y privacidad › apps con acceso completo), puede volver a encenderse solo. Concédelo una vez desde una computadora:",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectionContainer { Text(Enforcer.grantCommand(context), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
            when (state.mode) {
                ProtectionMode.DEVICE_OWNER -> Check("Device Owner", policy.isDeviceOwner())
                ProtectionMode.DEVICE_ADMIN -> Check(t("Device Admin", "Administrador de dispositivo"), policy.isAdminActive())
                ProtectionMode.NONE -> {}
            }
            if (state.privateDnsHost != null && state.mode == ProtectionMode.DEVICE_OWNER) {
                Check(t("DNS filter (${state.privateDnsHost})", "Filtro DNS (${state.privateDnsHost})"), state.managedDnsHost == state.privateDnsHost)
            } else if (state.privateDnsHost == null) {
                Text(
                    t("No DNS filter set. Recommended: see Protect › DNS filter.", "Sin filtro DNS. Recomendado: ver Proteger › Filtro DNS."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.mode == ProtectionMode.DEVICE_ADMIN) {
                Text(
                    t(
                        "Device Admin mode can't block factory reset, safe mode, new installs, or Private DNS changes. Device Owner closes those gaps.",
                        "El modo Administrador de dispositivo no puede bloquear el restablecimiento de fábrica, el modo seguro, nuevas instalaciones ni cambios de DNS privado. Device Owner cierra esos huecos.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Section(t("Pending changes", "Cambios pendientes") + " (${state.pending.size})") {
            if (state.pending.isEmpty()) Text(t("Nothing waiting.", "Nada en espera."))
            state.pending.sortedBy { it.applyAt }.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.change.describe())
                        Text(
                            t(
                                "Applies in ${formatDuration(p.applyAt - now)} (${formatTrusted(p.applyAt)})",
                                "Se aplica en ${formatDuration(p.applyAt - now)} (${formatTrusted(p.applyAt)})",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { Graph.gate.cancel(p.id) }) { Text(t("Cancel", "Cancelar")) }
                }
                HorizontalDivider()
            }
        }

        // Optional tip link. Opens in your browser; Alpha Blocker itself still has
        // no internet permission and sends nothing. The app is free either way.
        Section(t("Support Alpha Blocker", "Apoya Alpha Blocker")) {
            Text(t(
                "Alpha Blocker is free and always will be. If it helps you and you'd like to support its development, you can leave a tip.",
                "Alpha Blocker es gratis y siempre lo será. Si te ayuda y quieres apoyar su desarrollo, puedes dejar una propina.",
            ))
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure {
                    Toast.makeText(context, t("No browser found to open the link.", "No se encontró un navegador para abrir el enlace."), Toast.LENGTH_LONG).show()
                }
            }) { Text(t("Donate on Ko-fi", "Donar en Ko-fi")) }
            Text(DONATE_URL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }

    if (starting) StartProtectionDialog(onDismiss = { starting = false })
    if (showRestrictedHelp) RestrictedSettingsHelp(onDismiss = { showRestrictedHelp = false })
}

/** The developer's tip page (Status tab › Support Alpha Blocker). */
private const val DONATE_URL = "https://ko-fi.com/catstormrider"

@Composable
private fun Check(label: String, ok: Boolean) {
    Text((if (ok) "✔ " else "✘ ") + label, color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
}
