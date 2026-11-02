// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * BeforeYouStart.kt — "set these first" checklist before starting protection
 *
 * WHY: while protection is OFF every change is instant. Once it's ON, anything
 * that loosens protection waits the delay (and is refused during a lock).
 * Some settings are easy to forget and painful to fix later, e.g.:
 *   * blocking USB debugging before granting the ADB permission means the
 *     grant can't be done until the block is lifted (delay);
 *   * a bank app that refuses to run needs the banking pause, which can only
 *     be switched on with the delay once protection is on;
 *   * blocking installs means every later install or update needs a window.
 * Shown in the "Start protection" dialog (MainActivity.kt) and on the Status
 * tab while protection is off. Purely informational: nothing here changes
 * settings.
 * =============================================================================
 */
package com.alphablocker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alphablocker.core.t
import com.alphablocker.data.AppState
import com.alphablocker.data.Feature
import com.alphablocker.data.ProtectionMode
import com.alphablocker.enforcement.Enforcer

/** One checklist line: done or not, a title, WHERE to set it, and why it matters. */
private data class Item(val done: Boolean, val title: String, val where: String, val why: String)

@Composable
fun BeforeYouStartChecklist(state: AppState) {
    val context = LocalContext.current
    val owner = state.mode == ProtectionMode.DEVICE_OWNER
    val items = buildList {
        add(Item(
            Enforcer.canAutoReEnable(context),
            t("1. ADB permission (auto re-enable + banking pause)", "1. Permiso ADB (reactivación automática + pausa para bancos)"),
            t(
                "Status tab › Health checks › \"Auto re-enable blocker service\": copy the command shown there and run it from a computer (see the ADB guide).",
                "Pestaña Estado › Comprobaciones › \"Reactivar automáticamente el servicio\": copia el comando que aparece y ejecútalo desde una computadora (ver la guía de ADB).",
            ),
            t(
                "Run the command shown under Health checks while USB debugging is still on. Do this BEFORE blocking USB debugging.",
                "Ejecuta el comando de Comprobaciones mientras la depuración USB sigue activa. Hazlo ANTES de bloquear la depuración USB.",
            ),
        ))
        if (owner) add(Item(
            Feature.BLOCK_DEBUGGING in state.features,
            t("2. Block USB debugging", "2. Bloquear depuración USB"),
            t(
                "Protect tab › DNS filter section › switch \"Block developer options / USB debugging\".",
                "Pestaña Proteger › sección Filtro DNS › interruptor \"Bloquear opciones de desarrollador / depuración USB\".",
            ),
            t(
                "Stops ADB from undoing protection. Turn it on only after step 1: once on, turning it off waits your delay.",
                "Impide usar ADB para deshacer la protección. Actívalo solo después del paso 1: una vez activo, desactivarlo espera el retraso.",
            ),
        ))
        add(Item(
            state.bankPauseEnabled && state.bankApps.isNotEmpty(),
            t("3. Banking pause", "3. Pausa para bancos"),
            t(
                "Protect tab › Banking pause › switch \"Allow banking pause\", then \"Add a bank app\" for each bank.",
                "Pestaña Proteger › Pausa para bancos › interruptor \"Permitir pausa para bancos\" y luego \"Añadir una app de banco\" por cada banco.",
            ),
            t(
                "If a bank app complains about Alpha Blocker, switch this on and add the bank now. Later it waits your delay.",
                "Si una app de banco se queja de Alpha Blocker, actívala y añade el banco ahora. Después espera el retraso.",
            ),
        ))
        if (owner) add(Item(
            Feature.BLOCK_INSTALLS in state.features,
            t("4. App installs", "4. Instalación de apps"),
            t(
                "Protect tab › App installs › switch \"Block new installs\". Install your apps BEFORE turning it on.",
                "Pestaña Proteger › Instalación de apps › interruptor \"Bloquear nuevas instalaciones\". Instala tus apps ANTES de activarlo.",
            ),
            t(
                "Install every app you need first (bank, F-Droid…). With installs blocked, each later install or update needs a 15-minute window that waits your delay.",
                "Instala primero todas las apps que necesites (banco, F-Droid…). Con las instalaciones bloqueadas, cada instalación o actualización posterior necesita una ventana de 15 minutos que espera el retraso.",
            ),
        ))
        add(Item(
            state.privateDnsHost != null,
            t("5. DNS filter", "5. Filtro DNS"),
            t(
                "Protect tab › DNS filter › tap \"Use\" on a service, then \"Save Private DNS\" (Device Owner). Device Admin: Android Settings › Network & internet › Private DNS.",
                "Pestaña Proteger › Filtro DNS › toca \"Usar\" en un servicio y luego \"Guardar DNS privado\" (Device Owner). Administrador de dispositivo: Ajustes de Android › Red e Internet › DNS privado.",
            ),
            t("Setting one later is instant; changing or removing it waits your delay.", "Configurarlo después es inmediato; cambiarlo o quitarlo espera el retraso."),
        ))
        add(Item(
            true,
            t("6. Words, pages, browsers, app rules", "6. Palabras, páginas, navegadores, reglas de apps"),
            t(
                "Filters tab (blocked words, pages, allowed browsers, protected screens) and Apps tab (app rules, time-left notification).",
                "Pestaña Filtros (palabras, páginas, navegadores permitidos, pantallas protegidas) y pestaña Apps (reglas de apps, notificación de tiempo restante).",
            ),
            t(
                "Review the starter lists now: removing an entry later waits your delay. Adding more is always instant.",
                "Revisa ahora las listas iniciales: quitar algo después espera el retraso. Añadir más siempre es inmediato.",
            ),
        ))
    }

    Column {
        Text(
            t(
                "Once protection is on, anything that loosens it waits ${state.delayHours} hours and is refused during a lock. Set these now, while every change is instant:",
                "Con la protección activa, todo lo que la afloja espera ${state.delayHours} horas y se rechaza durante un bloqueo. Configura esto ahora, mientras cada cambio es inmediato:",
            ),
        )
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(
                    if (item.done) "✔" else "○",
                    color = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        t("Where: ", "Dónde: ") + item.where,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(item.why, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            t("○ = not set. Skip anything you don't need.", "○ = sin configurar. Omite lo que no necesites."),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
