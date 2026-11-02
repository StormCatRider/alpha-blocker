// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * AppsScreen.kt — per-app rules: always blocked, schedules, daily limits
 *
 * Every save goes through ChangeGate: stricter edits apply now, looser edits
 * (shorter schedule, longer limit, removing a rule) wait for the delay.
 * Apps the phone needs (home screen, dialer, Settings) can't be picked.
 * =============================================================================
 */
package com.alphablocker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alphablocker.Graph
import com.alphablocker.core.t
import com.alphablocker.core.AppCatalog
import com.alphablocker.core.Change
import com.alphablocker.data.AppRule
import com.alphablocker.data.AppState
import com.alphablocker.data.TimeWindow

@Composable
fun AppsScreen(state: AppState) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppRule?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Section(t("App rules", "Reglas de apps")) {
            Text(t("Block apps always, on a schedule, or after a daily time limit.", "Bloquea apps siempre, con horario o tras un límite diario."))
            Spacer(Modifier.height(8.dp))
            Button(onClick = { picking = true }) { Text(t("Add an app", "Añadir una app")) }
            // Display setting, not protection: saved directly (no delay either way).
            SwitchRow(
                t("Time-left notification", "Notificación de tiempo restante"),
                state.showLimitCountdown,
                t(
                    "While an app with a daily limit is open, a quiet notification counts down the time left today.",
                    "Mientras una app con límite diario está abierta, una notificación silenciosa muestra la cuenta atrás del tiempo restante hoy.",
                ),
            ) { on ->
                Graph.store.update { it.copy(showLimitCountdown = on) }
                if (!on) com.alphablocker.service.LimitCountdown.hide(context)
            }
        }
        state.appRules.sortedBy { it.label.lowercase() }.forEach { rule ->
            Section(rule.label) {
                Text(rule.summary(), style = MaterialTheme.typography.bodyMedium)
                Text(t("Used today: ", "Uso de hoy: ") + "${Graph.usage.usedMs(rule.pkg) / 60_000} min", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = rule }) { Text(t("Edit", "Editar")) }
                    TextButton(onClick = { submitWithToast(context, Change.RemoveAppRule(rule.pkg, rule.label)) }) { Text(t("Remove", "Quitar")) }
                }
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            exclude = state.appRules.map { it.pkg }.toSet() + AppCatalog.criticalPackages(context),
            onDismiss = { picking = false },
            onPick = { pkg, label ->
                picking = false
                editing = AppRule(pkg, label, alwaysBlocked = false, windows = emptyList(), dailyLimitMinutes = 30)
            },
        )
    }
    editing?.let { rule ->
        RuleEditorDialog(
            initial = rule,
            onDismiss = { editing = null },
            onSave = { submitWithToast(context, Change.SetAppRule(it)); editing = null },
        )
    }
}

/** Searchable list of installed apps. Also used to pick bank apps (ProtectionScreen.kt). */
@Composable
fun AppPickerDialog(exclude: Set<String>, onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val context = LocalContext.current
    val apps = remember { AppCatalog.launchableApps(context).filter { it.pkg !in exclude } }
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Choose an app", "Elige una app")) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text(t("Search", "Buscar")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(apps.filter { it.label.contains(query, ignoreCase = true) }, key = { it.pkg }) { app ->
                        Text(
                            app.label,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(app.pkg, app.label) }.padding(vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Cerrar")) } },
    )
}

/** Edits one rule: always-blocked switch, daily limit, and schedule windows. */
@Composable
private fun RuleEditorDialog(initial: AppRule, onDismiss: () -> Unit, onSave: (AppRule) -> Unit) {
    var always by remember { mutableStateOf(initial.alwaysBlocked) }
    var limit by remember { mutableStateOf(initial.dailyLimitMinutes?.toString() ?: "") }
    var windows by remember { mutableStateOf(initial.windows) }

    // New-window form
    var days by remember { mutableIntStateOf(0b0011111) } // Mon–Fri
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("17:00") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(t("Always blocked", "Siempre bloqueada"), always) { always = it }
                Spacer(Modifier.height(8.dp))
                NumberField(t("Daily limit in minutes (empty = none)", "Límite diario en minutos (vacío = ninguno)"), limit) { limit = it }
                Spacer(Modifier.height(12.dp))
                Text(t("Blocked times", "Horarios bloqueados"), style = MaterialTheme.typography.titleSmall)
                if (windows.isEmpty()) Text(t("None", "Ninguno"), style = MaterialTheme.typography.bodySmall)
                windows.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.describe(), Modifier.weight(1f))
                        TextButton(onClick = { windows = windows - w }) { Text(t("Remove", "Quitar")) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(t("Add blocked time", "Añadir horario bloqueado"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..7).forEach { d ->
                        val on = (days and TimeWindow.bit(d)) != 0
                        val label = TimeWindow.dayNames()[d - 1].take(2)
                        if (on) {
                            Button(onClick = { days = days xor TimeWindow.bit(d) }, Modifier.size(40.dp), contentPadding = noPadding()) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { days = days xor TimeWindow.bit(d) }, Modifier.size(40.dp), contentPadding = noPadding()) { Text(label) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it.take(5) }, label = { Text(t("From", "Desde")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it.take(5) }, label = { Text(t("To", "Hasta")) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text(t("24-hour time, e.g. 22:00 to 06:00 runs overnight.", "Formato 24 h; p. ej. 22:00 a 06:00 cruza la noche."), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val s = TimeWindow.parseHhmm(start)
                    val e = TimeWindow.parseHhmm(end)
                    error = when {
                        s == null || e == null -> t("Use HH:MM times.", "Usa horas HH:MM.")
                        days == 0 -> t("Pick at least one day.", "Elige al menos un día.")
                        else -> { windows = (windows + TimeWindow(days, s, e)).distinct(); null }
                    }
                }) { Text(t("Add time", "Añadir horario")) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val minutes = limit.toIntOrNull()?.coerceIn(1, 1440)
                onSave(initial.copy(alwaysBlocked = always, windows = windows, dailyLimitMinutes = minutes))
            }) { Text(t("Save", "Guardar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Cancelar")) } },
    )
}

private fun noPadding() = androidx.compose.foundation.layout.PaddingValues(0.dp)
