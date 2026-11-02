// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * RestrictedSettingsHelp.kt — help for Android's "Restricted setting" block
 *
 * Android 13+ blocks the accessibility switch for apps installed outside the
 * Play Store (sideloaded APKs, F-Droid). The way out is hidden: the
 * "Allow restricted settings" menu item only appears in the app's info page
 * AFTER you try the switch once and get the "Restricted setting" message.
 * This dialog explains that in three steps, with shortcuts to both screens.
 * Opened from the setup wizard (Blocker service step) and the Status tab.
 * =============================================================================
 */
package com.alphablocker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alphablocker.core.t

@Composable
fun RestrictedSettingsHelp(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Switch greyed out?", "¿El interruptor está gris?")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t(
                    "Android blocks the accessibility switch for apps not installed from the Play Store (\"Restricted setting\"). The option to allow it only appears after one blocked try:",
                    "Android bloquea el interruptor de accesibilidad de las apps no instaladas desde Play Store (\"Ajuste restringido\"). La opción para permitirlo solo aparece después de un intento bloqueado:",
                ))
                Step(
                    t("1. Try once", "1. Inténtalo una vez"),
                    t(
                        "Open accessibility settings › Alpha Blocker, tap the switch and tap OK on the \"Restricted setting\" message.",
                        "Abre los ajustes de accesibilidad › Alpha Blocker, toca el interruptor y toca Aceptar en el mensaje \"Ajuste restringido\".",
                    ),
                )
                OutlinedButton(onClick = { open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("Open accessibility settings", "Abrir ajustes de accesibilidad"))
                }
                Step(
                    t("2. Allow restricted settings", "2. Permitir ajustes restringidos"),
                    t(
                        "Open Alpha Blocker's app info, tap ⋮ (top right) › Allow restricted settings, and confirm with your phone's PIN.",
                        "Abre la información de la app Alpha Blocker, toca ⋮ (arriba a la derecha) › Permitir ajustes restringidos y confirma con el PIN del teléfono.",
                    ),
                )
                OutlinedButton(
                    onClick = {
                        open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t("Open Alpha Blocker app info", "Abrir información de Alpha Blocker")) }
                Step(
                    t("3. Turn it on", "3. Actívalo"),
                    t(
                        "Back in accessibility settings › Alpha Blocker, turn on Use Alpha Blocker and accept.",
                        "De vuelta en ajustes de accesibilidad › Alpha Blocker, activa Usar Alpha Blocker y acepta.",
                    ),
                )
                OutlinedButton(onClick = { open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("Open accessibility settings", "Abrir ajustes de accesibilidad"))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    t(
                        "Don't see ⋮ › Allow restricted settings? Do step 1 again first; it only appears after the blocked try. Menu names can differ slightly on non-Pixel phones.",
                        "¿No ves ⋮ › Permitir ajustes restringidos? Repite primero el paso 1; solo aparece después del intento bloqueado. Los nombres pueden variar un poco en teléfonos que no son Pixel.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Done", "Listo")) } },
    )
}

@Composable
private fun Step(title: String, text: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, fontWeight = FontWeight.Bold)
    Text(text)
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
