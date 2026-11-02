// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * DevUnlockDialog.kt — "Ask the developer" when the recovery code is lost
 *
 * Shows this phone's REQUEST code (safe to share: it unlocks nothing by
 * itself) and a box for the UNLOCK code the developer sends back. See
 * security/DevUnlock.kt for how codes are made and checked.
 * Reached from the recovery-code dialog (Protect tab, or "Forgot PIN?" on the
 * PIN screen). A correct code = protection off, like the recovery code.
 * =============================================================================
 */
package com.alphablocker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphablocker.Graph
import com.alphablocker.core.SecretResult
import com.alphablocker.core.t
import com.alphablocker.security.DevUnlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DevUnlockDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request = remember { DevUnlock.format(Graph.gate.devRequest()) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(t("Ask the developer", "Pedir al desarrollador")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t(
                    "1. Send this request code to the developer of your Alpha Blocker build (for example by email):",
                    "1. Envía este código de solicitud al desarrollador de tu versión de Alpha Blocker (por ejemplo, por correo):",
                ))
                Spacer(Modifier.height(8.dp))
                // Selectable on purpose: it's not secret (it unlocks nothing by itself).
                SelectionContainer {
                    Text(request, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(t(
                    "2. Type or paste the unlock code you get back. It works only on this phone, only once, and turns protection off like the recovery code.",
                    "2. Escribe o pega el código de desbloqueo que recibas. Solo funciona en este teléfono, una sola vez, y desactiva la protección como el código de recuperación.",
                ))
                OutlinedTextField(
                    code, { code = it },
                    label = { Text(t("Unlock code", "Código de desbloqueo")) },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    t("The developer may ask questions or make you wait before sending it.", "El desarrollador puede hacerte preguntas o pedirte que esperes antes de enviarlo."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(enabled = !busy && code.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.Default) { Graph.gate.useDeveloperCode(context.packageName, code) }
                    busy = false
                    when (r) {
                        SecretResult.Ok -> {
                            Graph.enforcer.syncAsync()
                            toast(t("Protection is off.", "La protección está apagada."))
                            onDismiss()
                            onSuccess()
                        }
                        SecretResult.Wrong -> toast(t("That unlock code doesn't match this phone's request.", "Ese código de desbloqueo no corresponde a la solicitud de este teléfono."))
                        is SecretResult.TooManyAttempts -> toast(
                            t("Too many tries. Try again ${formatTrusted(r.retryAt)}.", "Demasiados intentos. Vuelve a intentarlo el ${formatTrusted(r.retryAt)}."),
                        )
                    }
                }
            }) { Text(t("Unlock", "Desbloquear")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(t("Cancel", "Cancelar")) } },
    )
}
