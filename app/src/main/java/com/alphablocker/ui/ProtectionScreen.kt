// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * ProtectionScreen.kt — language, delay, lock, extensions, installs, DNS filter,
 *                       banking pause, PIN, recovery code, and turning
 *                       protection off
 *
 * "Turn protection off" is a loosening change: it waits the full delay and is
 * refused during a lock. The recovery code is the only immediate way out.
 * Once protection is off, "Release device" gives up Device Owner/Admin so the
 * app can be uninstalled normally.
 * Language is a display setting, not protection, so it changes instantly.
 * =============================================================================
 */
package com.alphablocker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alphablocker.Graph
import com.alphablocker.core.AppCatalog
import com.alphablocker.core.Change
import com.alphablocker.core.SecretResult
import com.alphablocker.core.t
import com.alphablocker.data.AppState
import com.alphablocker.data.Feature
import com.alphablocker.data.ProtectionMode
import com.alphablocker.enforcement.Enforcer
import com.alphablocker.security.Crypto
import com.alphablocker.security.DevUnlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProtectionScreen(state: AppState) {
    val context = LocalContext.current
    val now by rememberTrustedNow()
    var delay by remember(state.delayHours) { mutableStateOf(state.delayHours.toString()) }
    var extCount by remember(state.extensionsPerDay) { mutableStateOf(state.extensionsPerDay.toString()) }
    var extMin by remember(state.extensionMinutes) { mutableStateOf(state.extensionMinutes.toString()) }
    var lockDays by remember { mutableStateOf("") }
    var dns by remember(state.privateDnsHost) { mutableStateOf(state.privateDnsHost ?: "") }
    var confirmDisable by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    var showPinChange by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var pickingBank by remember { mutableStateOf(false) }
    val isOwner = state.mode == ProtectionMode.DEVICE_OWNER

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {

        Section(t("Language", "Idioma")) { LanguagePicker() }

        Section(t("Loosening delay", "Retraso para aflojar")) {
            Text(t(
                "Currently ${state.delayHours} hours. Longer applies now; shorter waits the current delay.",
                "Ahora ${state.delayHours} horas. Más largo se aplica ya; más corto espera el retraso actual.",
            ))
            NumberField(t("Hours (1–720)", "Horas (1–720)"), delay) { delay = it }
            Button(onClick = {
                val h = delay.toIntOrNull()
                if (h == null || h !in 1..Change.MAX_DELAY_HOURS) toast(context, t("Enter 1–720 hours.", "Escribe de 1 a 720 horas."))
                else submitWithToast(context, Change.SetDelayHours(h))
            }) { Text(t("Save delay", "Guardar retraso")) }
        }

        Section(t("Fixed lock period", "Periodo de bloqueo fijo")) {
            if (state.lockUntil > now) Text(t("Locked until ${formatTrusted(state.lockUntil)}.", "Bloqueado hasta el ${formatTrusted(state.lockUntil)}."))
            else Text(t("No lock active.", "Sin bloqueo activo."))
            Text(
                t("Extending the lock is instant. It can't be shortened while active.", "Alargar el bloqueo es inmediato. No se puede acortar mientras está activo."),
                style = MaterialTheme.typography.bodySmall,
            )
            NumberField(t("Lock for days from now", "Bloquear durante días desde ahora"), lockDays) { lockDays = it }
            Button(onClick = {
                val d = lockDays.toIntOrNull()
                if (d == null || d !in 1..3650) toast(context, t("Enter 1–3650 days.", "Escribe de 1 a 3650 días."))
                else { submitWithToast(context, Change.SetLock(now + d * 86_400_000L)); lockDays = "" }
            }) { Text(t("Set lock", "Fijar bloqueo")) }
        }

        Section(t("Time-limit extensions", "Extensiones de tiempo")) {
            NumberField(t("Extensions per day (0–10)", "Extensiones por día (0–10)"), extCount) { extCount = it }
            NumberField(t("Minutes each (1–60)", "Minutos cada una (1–60)"), extMin) { extMin = it }
            Button(onClick = {
                val c = extCount.toIntOrNull(); val m = extMin.toIntOrNull()
                if (c == null || m == null || c !in 0..10 || m !in 1..60) toast(context, t("Check the numbers.", "Revisa los números."))
                else submitWithToast(context, Change.SetExtensions(c, m))
            }) { Text(t("Save extensions", "Guardar extensiones")) }
        }

        Section(t("App installs", "Instalación de apps")) {
            SwitchRow(Feature.BLOCK_INSTALLS.label, Feature.BLOCK_INSTALLS in state.features, Feature.BLOCK_INSTALLS.description) {
                submitWithToast(context, Change.SetFeature(Feature.BLOCK_INSTALLS, it))
            }
            if (state.installWindowUntil > now) {
                Text(t(
                    "Install window open: ${formatDuration(state.installWindowUntil - now)} left.",
                    "Ventana de instalación abierta: quedan ${formatDuration(state.installWindowUntil - now)}.",
                ))
            }
            OutlinedButton(enabled = isOwner, onClick = { submitWithToast(context, Change.OpenInstallWindow(15)) }) {
                Text(t("Request a 15-minute install window", "Pedir una ventana de instalación de 15 minutos"))
            }
            Text(
                t(
                    "Requests wait for the delay, then installs (and app updates from F-Droid) are allowed for 15 minutes.",
                    "Las solicitudes esperan el retraso; luego se permiten instalaciones (y actualizaciones de F-Droid) durante 15 minutos.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section(t("DNS filter", "Filtro DNS")) {
            DnsFilterNote(onPick = { dns = it })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                dns, { dns = it },
                label = { Text(t("Private DNS host (empty = not managed)", "Host de DNS privado (vacío = sin gestionar)")) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(enabled = isOwner, onClick = { submitWithToast(context, Change.SetPrivateDnsHost(dns.ifBlank { null })) }) {
                Text(t("Save Private DNS (Device Owner)", "Guardar DNS privado (Device Owner)"))
            }
            Text(
                t("Setting a host is instant; changing or removing it waits for the delay.", "Configurar un host es inmediato; cambiarlo o quitarlo espera el retraso."),
                style = MaterialTheme.typography.bodySmall,
            )
            SwitchRow(Feature.BLOCK_DEBUGGING.label, Feature.BLOCK_DEBUGGING in state.features, Feature.BLOCK_DEBUGGING.description) {
                submitWithToast(context, Change.SetFeature(Feature.BLOCK_DEBUGGING, it))
            }
        }

        Section(t("Banking pause", "Pausa para bancos")) {
            Text(t(
                "Some bank apps refuse to open while an app installed outside the Play Store has accessibility access. With this on, the Status tab gets an \"Open\" button for each bank app below: it pauses the blocker service and opens the bank. After the pause length you choose below a popup lets you turn protection back on or take 2 more minutes; 2 minutes later protection turns back on by itself. In Device Owner mode browsers and apps with rules stay paused during the pause; in Device Admin mode nothing is blocked for those minutes.",
                "Algunas apps de banco no abren mientras una app instalada fuera de Play Store tiene acceso de accesibilidad. Con esto activado, la pestaña Estado muestra un botón \"Abrir\" para cada app de banco de abajo: pausa el servicio de bloqueo y abre el banco. Al terminar la duración que elijas abajo, una ventana te deja volver a activar la protección o tomar 2 minutos más; 2 minutos después la protección se activa sola. En modo Device Owner los navegadores y las apps con reglas siguen en pausa; en modo Administrador de dispositivo no se bloquea nada durante esos minutos.",
            ))
            SwitchRow(
                t("Allow banking pause", "Permitir pausa para bancos"),
                state.bankPauseEnabled,
                t("Turning it on, or adding a bank app, waits for the delay. Turning it off or removing an app is instant.", "Activarla o añadir una app de banco espera el retraso. Desactivarla o quitar una app es inmediato."),
            ) { submitWithToast(context, Change.SetBankPause(it)) }
            if (state.bankApps.isEmpty()) Text(t("No bank apps yet.", "Aún no hay apps de banco."), style = MaterialTheme.typography.bodySmall)
            state.bankApps.forEach { pkg ->
                val label = AppCatalog.label(context, pkg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    TextButton(onClick = { submitWithToast(context, Change.RemoveBankApp(pkg, label)) }) { Text(t("Remove", "Quitar")) }
                }
            }
            OutlinedButton(onClick = { pickingBank = true }) { Text(t("Add a bank app", "Añadir una app de banco")) }
            Spacer(Modifier.height(8.dp))
            Text(t("Pause length", "Duración de la pausa"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4).forEach { m ->
                    val label = t("$m min", "$m min")
                    if (m == state.bankPauseMinutes) Button(onClick = {}) { Text(label) }
                    else OutlinedButton(onClick = { submitWithToast(context, Change.SetBankPauseMinutes(m)) }) { Text(label) }
                }
            }
            Text(
                t(
                    "Then a popup offers 2 more minutes if you need them. Shorter applies now; longer waits the delay.",
                    "Luego una ventana ofrece 2 minutos más si los necesitas. Más corto se aplica ya; más largo espera el retraso.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!Enforcer.canAutoReEnable(context)) {
                Text(
                    t("Needs the auto re-enable permission. Grant it once from a computer:", "Necesita el permiso de reactivación automática. Concédelo una vez desde una computadora:"),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                )
                SelectionContainer { Text(Enforcer.grantCommand(context), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Section(t("PIN & recovery", "PIN y recuperación")) {
            OutlinedButton(onClick = { showPinChange = true }) { Text(t("Change PIN", "Cambiar PIN")) }
            if (state.protectionActive) {
                OutlinedButton(onClick = { showRecovery = true }) { Text(t("Use recovery code (emergency)", "Usar código de recuperación (emergencia)")) }
            }
        }

        Section(t("Turn protection off / on", "Desactivar / activar protección")) {
            if (state.protectionActive) {
                Text(t(
                    "Turning protection off waits ${state.delayHours} hours and is refused during a lock.",
                    "Desactivar la protección espera ${state.delayHours} horas y se rechaza durante un bloqueo.",
                ))
                Button(onClick = { confirmDisable = true }) { Text(t("Request turning protection off", "Solicitar desactivar la protección")) }
            } else {
                Text(t("Protection is off.", "La protección está apagada."))
                AccentButton(t("Start protection", "Iniciar protección")) { starting = true }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val ok = Graph.enforcer.policy.releaseDevice()
                    toast(
                        context,
                        if (ok) t("Released. You can now uninstall Alpha Blocker normally.", "Liberado. Ya puedes desinstalar Alpha Blocker normalmente.")
                        else t("Couldn't release; turn protection off first.", "No se pudo liberar; primero desactiva la protección."),
                    )
                }) { Text(t("Release device (allow uninstall)", "Liberar dispositivo (permitir desinstalar)")) }
            }
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(t("Turn protection off?", "¿Desactivar la protección?")) },
            text = {
                Text(t(
                    "This will apply after ${state.delayHours} hours. You can cancel it any time before then on the Status tab.",
                    "Se aplicará después de ${state.delayHours} horas. Puedes cancelarlo antes en la pestaña Estado.",
                ))
            },
            confirmButton = {
                Button(onClick = { confirmDisable = false; submitWithToast(context, Change.DisableProtection) }) { Text(t("Request", "Solicitar")) }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text(t("Keep protection", "Mantener protección")) } },
        )
    }
    if (showRecovery) RecoveryDialog(onDismiss = { showRecovery = false })
    if (showPinChange) PinChangeDialog(onDismiss = { showPinChange = false })
    if (starting) StartProtectionDialog(onDismiss = { starting = false })
    if (pickingBank) {
        // Bank apps can't be browsers or apps the phone needs (Settings, home screen, dialer).
        AppPickerDialog(
            exclude = state.bankApps.toSet() + AppCatalog.criticalPackages(context) + AppCatalog.browsers(context),
            onDismiss = { pickingBank = false },
            onPick = { pkg, label -> pickingBank = false; submitWithToast(context, Change.AddBankApp(pkg, label)) },
        )
    }
}

/**
 * Enter the paper recovery code. Correct = protection off immediately.
 * Also opened from the PIN screen ("Forgot PIN?"), which then asks for a new
 * PIN through [onSuccess]. Wrong codes share the PIN's attempt throttling.
 */
@Composable
fun RecoveryDialog(onDismiss: () -> Unit, onSuccess: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var askDeveloper by remember { mutableStateOf(false) }
    if (askDeveloper) {
        DevUnlockDialog(onDismiss = onDismiss, onSuccess = onSuccess)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Emergency recovery code", "Código de recuperación de emergencia")) },
        text = {
            Column {
                Text(t(
                    "A correct code turns protection off right away and ends any lock. It works once.",
                    "Un código correcto desactiva la protección al instante y termina cualquier bloqueo. Funciona una vez.",
                ))
                OutlinedTextField(code, { code = it }, label = { Text("XXXX-XXXX-XXXX-XXXX-XXXX") }, singleLine = true)
                // Only in builds that have a developer key (security/DevKey.kt).
                if (DevUnlock.available) {
                    TextButton(onClick = { askDeveloper = true }) {
                        Text(t("Lost or miswrote the code? Ask the developer", "¿Perdiste o anotaste mal el código? Pídeselo al desarrollador"))
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !busy && code.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.Default) { Graph.gate.useRecoveryCode(code) }
                    busy = false
                    when (r) {
                        SecretResult.Ok -> {
                            Graph.enforcer.syncAsync()
                            toast(context, t("Protection is off.", "La protección está apagada."))
                            onDismiss()
                            onSuccess()
                        }
                        SecretResult.Wrong -> toast(context, t("Wrong code.", "Código incorrecto."))
                        is SecretResult.TooManyAttempts -> toast(
                            context,
                            t("Too many tries. Try again ${formatTrusted(r.retryAt)}.", "Demasiados intentos. Vuelve a intentarlo el ${formatTrusted(r.retryAt)}."),
                        )
                    }
                }
            }) { Text(t("Use code", "Usar código")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Cancelar")) } },
    )
}

/** Change the settings PIN (needs the current PIN). */
@Composable
private fun PinChangeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Change PIN", "Cambiar PIN")) },
        text = {
            Column {
                listOf(
                    Triple(t("Current PIN", "PIN actual"), old) { v: String -> old = v },
                    Triple(t("New PIN", "PIN nuevo"), new1) { v: String -> new1 = v },
                    Triple(t("Repeat new PIN", "Repite el PIN nuevo"), new2) { v: String -> new2 = v },
                ).forEach { (label, value, set) ->
                    OutlinedTextField(
                        value, { set(it.filter(Char::isDigit).take(12)) }, label = { Text(label) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (new1.length < 4 || new1 != new2) {
                    toast(context, t("New PINs must match and have 4+ digits.", "Los PIN nuevos deben coincidir y tener 4+ dígitos."))
                    return@Button
                }
                scope.launch {
                    val r = withContext(Dispatchers.Default) { Graph.gate.checkPin(old) }
                    if (r == SecretResult.Ok) {
                        val hash = withContext(Dispatchers.Default) { Crypto.hash(new1) }
                        Graph.store.update { it.copy(pinHash = hash) }
                        toast(context, t("PIN changed.", "PIN cambiado."))
                        onDismiss()
                    } else {
                        toast(context, t("Current PIN is wrong.", "El PIN actual es incorrecto."))
                    }
                }
            }) { Text(t("Save", "Guardar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Cancelar")) } },
    )
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
