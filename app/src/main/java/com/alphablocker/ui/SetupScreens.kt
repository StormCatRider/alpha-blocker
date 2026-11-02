// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * SetupScreens.kt — first-run setup wizard
 *
 * Steps:
 *   0 Welcome + language
 *   1 Lockdown mode (Device Owner via ADB, or Device Admin)
 *   2 PIN
 *   3 Change delay + time-limit extensions
 *   4 Optional fixed lock period
 *   5 DNS filter (Private DNS host) with a note on family DNS services
 *   6 Blocker service (accessibility) + notifications, and a pointer to the
 *     "Before you start" checklist (BeforeYouStart.kt)
 *
 * Setup does NOT switch protection on. Afterwards you can review the starter
 * lists while every edit is still instant, then tap "Start protection" on the
 * Status tab, which creates your recovery code.
 * All texts are English/Spanish via t() (core/I18n.kt).
 * =============================================================================
 */
package com.alphablocker.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alphablocker.Graph
import com.alphablocker.core.t
import com.alphablocker.data.ProtectionMode
import com.alphablocker.enforcement.AdminReceiver
import com.alphablocker.enforcement.Enforcer
import com.alphablocker.security.Crypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LAST_STEP = 6

@Composable
fun SetupWizard() {
    val context = LocalContext.current
    val policy = Graph.enforcer.policy
    val scope = rememberCoroutineScope()

    // rememberSaveable: survives the screen being rebuilt (e.g. after changing language).
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showRestrictedHelp by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(if (policy.isDeviceOwner()) ProtectionMode.DEVICE_OWNER else ProtectionMode.NONE) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var delay by remember { mutableStateOf("72") }
    var extCount by remember { mutableStateOf("2") }
    var extMinutes by remember { mutableStateOf("5") }
    var lockDays by remember { mutableStateOf("0") }
    var dnsHost by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) } // bump to re-check system status
    var saving by remember { mutableStateOf(false) }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (policy.isAdminActive()) mode = ProtectionMode.DEVICE_ADMIN
        refresh++
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    /** Validates the current step. Returns an error message or null. */
    fun validate(): String? = when (step) {
        1 -> if (mode == ProtectionMode.NONE) t("Choose a lockdown mode first.", "Primero elige un modo de bloqueo.") else null
        2 -> when {
            pin1.length < 4 -> t("Use at least 4 digits.", "Usa al menos 4 dígitos.")
            pin1 != pin2 -> t("The two PINs don't match.", "Los dos PIN no coinciden.")
            else -> null
        }
        3 -> when {
            (delay.toIntOrNull() ?: 0) !in 1..720 -> t("Delay must be 1–720 hours.", "El retraso debe ser de 1 a 720 horas.")
            (extCount.toIntOrNull() ?: -1) !in 0..10 -> t("Extensions per day: 0–10.", "Extensiones por día: 0 a 10.")
            (extMinutes.toIntOrNull() ?: 0) !in 1..60 -> t("Extension length: 1–60 minutes.", "Duración de extensión: 1 a 60 minutos.")
            else -> null
        }
        4 -> if ((lockDays.toIntOrNull() ?: -1) !in 0..3650) t("Lock days: 0–3650.", "Días de bloqueo: 0 a 3650.") else null
        else -> null
    }

    fun finish() {
        saving = true
        scope.launch {
            val pinHash = withContext(Dispatchers.Default) { Crypto.hash(pin1) }
            val days = lockDays.toIntOrNull() ?: 0
            Graph.store.update { s ->
                s.copy(
                    setupComplete = true,
                    mode = mode,
                    protectionActive = false, // started from the Status tab after reviewing lists
                    pinHash = pinHash,
                    delayHours = delay.toInt(),
                    extensionsPerDay = extCount.toInt(),
                    extensionMinutes = extMinutes.toInt(),
                    lockUntil = if (days > 0) Graph.clock.now() + days * 86_400_000L else 0L,
                    privateDnsHost = dnsHost.trim().lowercase().ifBlank { null },
                )
            }
            Graph.enforcer.syncAsync()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(t("Set up Alpha Blocker", "Configurar Alpha Blocker") + " · ${step + 1}/${LAST_STEP + 1}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // key(refresh): bumping `refresh` redraws this step, re-checking system status.
            key(refresh) { when (step) {
                0 -> {
                    Text(t("Welcome", "Bienvenido"), style = MaterialTheme.typography.headlineSmall)
                    Text(t(
                        "Alpha Blocker helps you stay pure and productive. It works entirely on this phone and has no internet access, so nothing you do is ever sent anywhere.",
                        "Alpha Blocker te ayuda a mantenerte puro y productivo. Funciona solo en este teléfono y no tiene acceso a internet, así que nada de lo que haces se envía a ningún sitio.",
                    ))
                    Text(t(
                        "The key idea: making protection STRONGER is instant. Making it WEAKER waits for a delay you choose, so a weak moment passes before anything changes.",
                        "La idea clave: hacer la protección MÁS FUERTE es inmediato. Hacerla MÁS DÉBIL espera un retraso que tú eliges, así un momento de debilidad pasa antes de que cambie algo.",
                    ))
                    Section(t("Language", "Idioma")) { LanguagePicker() }
                }

                1 -> {
                    Text(t("Lockdown mode", "Modo de bloqueo"), style = MaterialTheme.typography.headlineSmall)
                    Section(t("Maximum: Device Owner (recommended)", "Máximo: Device Owner (recomendado)")) {
                        Text(t(
                            "Blocks uninstalling, factory reset, safe mode, new users/Private Space and Private DNS changes. Needs a computer once:",
                            "Bloquea la desinstalación, el restablecimiento de fábrica, el modo seguro, nuevos usuarios/Espacio privado y cambios de DNS privado. Necesita una computadora una sola vez:",
                        ))
                        Text(t(
                            "1. Settings › Passwords & accounts: remove every account (Google etc.). You'll add them back after.\n2. Enable Developer options › USB debugging, connect to your computer.\n3. Run:",
                            "1. Ajustes › Contraseñas y cuentas: quita todas las cuentas (Google, etc.). Las vuelves a añadir después.\n2. Activa Opciones de desarrollador › Depuración por USB y conéctalo a tu computadora.\n3. Ejecuta:",
                        ))
                        SelectionContainer { Text(Enforcer.deviceOwnerCommand(context), fontFamily = FontFamily.Monospace) }
                        Text(t("4. Add your accounts back, then tap Check.", "4. Vuelve a añadir tus cuentas y toca Comprobar."))
                        Text(
                            if (policy.isDeviceOwner()) t("✔ Alpha Blocker is Device Owner.", "✔ Alpha Blocker es Device Owner.")
                            else t("Not Device Owner yet.", "Todavía no es Device Owner."),
                        )
                        Button(onClick = {
                            refresh++
                            if (policy.isDeviceOwner()) mode = ProtectionMode.DEVICE_OWNER
                        }) { Text(t("Check", "Comprobar")) }
                    }
                    Section(t("Medium: Device Admin", "Medio: Administrador de dispositivo")) {
                        Text(t(
                            "No computer needed. Weaker: a determined user can deactivate it from Settings (the protected-screens feature makes that harder, not impossible). Factory reset and safe mode stay possible.",
                            "No necesita computadora. Más débil: alguien decidido puede desactivarlo desde Ajustes (las pantallas protegidas lo hacen más difícil, no imposible). El restablecimiento de fábrica y el modo seguro siguen siendo posibles.",
                        ))
                        OutlinedButton(onClick = {
                            adminLauncher.launch(
                                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(context))
                                    .putExtra(
                                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        t("Prevents Alpha Blocker from being removed while protection is on.", "Impide que se quite Alpha Blocker mientras la protección está activa."),
                                    ),
                            )
                        }) { Text(t("Activate Device Admin", "Activar administrador de dispositivo")) }
                        if (policy.isAdminActive() && !policy.isDeviceOwner()) Text(t("✔ Device Admin active.", "✔ Administrador de dispositivo activo."))
                    }
                    Text(t("Selected: ", "Elegido: ") + when (mode) {
                        ProtectionMode.DEVICE_OWNER -> "Device Owner"
                        ProtectionMode.DEVICE_ADMIN -> t("Device Admin", "Administrador de dispositivo")
                        else -> t("none", "ninguno")
                    })
                }

                2 -> {
                    Text(t("Settings PIN", "PIN de ajustes"), style = MaterialTheme.typography.headlineSmall)
                    Text(t("Needed to open Alpha Blocker's settings. It does not bypass the delay.", "Se necesita para abrir los ajustes de Alpha Blocker. No se salta el retraso."))
                    PinField("PIN", pin1) { pin1 = it }
                    PinField(t("Repeat PIN", "Repite el PIN"), pin2) { pin2 = it }
                }

                3 -> {
                    Text(t("Change delay", "Retraso de cambios"), style = MaterialTheme.typography.headlineSmall)
                    Text(t(
                        "How long any loosening change waits before it applies. Shortening the delay later also waits the current delay. 72 hours outlasts most urges.",
                        "Cuánto espera cualquier cambio que afloje la protección antes de aplicarse. Acortar el retraso después también espera el retraso actual. 72 horas superan la mayoría de los impulsos.",
                    ))
                    NumberField(t("Delay (hours)", "Retraso (horas)"), delay) { delay = it }
                    Text(t("Time-limit extensions", "Extensiones de tiempo"), style = MaterialTheme.typography.titleMedium)
                    Text(t("When an app's daily limit runs out, you can use a few short extensions per day.", "Cuando se agota el límite diario de una app, puedes usar algunas extensiones cortas al día."))
                    NumberField(t("Extensions per day", "Extensiones por día"), extCount) { extCount = it }
                    NumberField(t("Minutes per extension", "Minutos por extensión"), extMinutes) { extMinutes = it }
                }

                4 -> {
                    Text(t("Fixed lock period (optional)", "Periodo de bloqueo fijo (opcional)"), style = MaterialTheme.typography.headlineSmall)
                    Text(t(
                        "Starting now, for this many days NOTHING can be loosened once protection is on, even with the delay. Only your paper recovery code can end it early. 0 = no lock.",
                        "Desde ahora y durante estos días, NADA se puede aflojar una vez activa la protección, ni siquiera con el retraso. Solo tu código de recuperación en papel puede terminarlo antes. 0 = sin bloqueo.",
                    ))
                    NumberField(t("Lock days", "Días de bloqueo"), lockDays) { lockDays = it }
                }

                5 -> {
                    Text(t("DNS filter", "Filtro DNS"), style = MaterialTheme.typography.headlineSmall)
                    DnsFilterNote(onPick = { dnsHost = it })
                    OutlinedTextField(
                        dnsHost, { dnsHost = it },
                        label = { Text(t("Private DNS host (Device Owner)", "Host de DNS privado (Device Owner)")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(t("Leave empty to skip. You can set it later on the Protect tab.", "Déjalo vacío para omitirlo. Puedes configurarlo después en la pestaña Proteger."), style = MaterialTheme.typography.bodySmall)
                }

                6 -> {
                    val accOn = Enforcer.isAccessibilityOn(context)
                    Text(t("Blocker service", "Servicio de bloqueo"), style = MaterialTheme.typography.headlineSmall)
                    Text(t(
                        "Alpha Blocker's on-device blocker runs as an accessibility service. It reads app names, browser address bars and typed searches only to check them against your lists. Nothing is stored or sent.",
                        "El bloqueador de Alpha Blocker funciona como servicio de accesibilidad. Lee nombres de apps, barras de direcciones y búsquedas escritas solo para compararlas con tus listas. No se guarda ni se envía nada.",
                    ))
                    Text(t(
                        "Android 13+: the switch is often greyed out (\"Restricted setting\") for apps not from the Play Store. Tap Help for the 3 steps to allow it.",
                        "Android 13+: el interruptor suele estar gris (\"Ajuste restringido\") en apps que no son de Play Store. Toca Ayuda para ver los 3 pasos para permitirlo.",
                    ), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { showRestrictedHelp = true }) { Text(t("Help: switch greyed out?", "Ayuda: ¿interruptor gris?")) }
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                        Text(t("Open accessibility settings", "Abrir ajustes de accesibilidad"))
                    }
                    Text(if (accOn) t("✔ Blocker service is on.", "✔ El servicio de bloqueo está activo.") else t("Blocker service is off.", "El servicio de bloqueo está apagado."))
                    if (Build.VERSION.SDK_INT >= 33) {
                        OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text(t("Allow warning notifications", "Permitir notificaciones de aviso"))
                        }
                    }
                    OutlinedButton(onClick = { refresh++ }) { Text(t("Re-check", "Volver a comprobar")) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        t(
                            "Next: setup leaves protection OFF. Before starting it, open the Protect tab and set the ADB permission, banking pause, app installs and DNS filter. The Status tab shows a checklist. Once protection is on, loosening anything waits your delay.",
                            "Siguiente: la configuración deja la protección APAGADA. Antes de iniciarla, abre la pestaña Proteger y configura el permiso ADB, la pausa para bancos, la instalación de apps y el filtro DNS. La pestaña Estado muestra una lista. Con la protección activa, aflojar cualquier cosa espera el retraso.",
                        ),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (step > 0) OutlinedButton(onClick = { error = null; step-- }) { Text(t("Back", "Atrás")) }
            Spacer(Modifier.weight(1f))
            Button(enabled = !saving, onClick = {
                error = validate()
                if (error == null) {
                    if (step < LAST_STEP) step++ else finish()
                }
            }) { Text(if (step < LAST_STEP) t("Next", "Siguiente") else t("Finish setup", "Terminar configuración")) }
        }
    }

    if (showRestrictedHelp) RestrictedSettingsHelp(onDismiss = { showRestrictedHelp = false; refresh++ })
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(12)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Digits-only text field. */
@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
