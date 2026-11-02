// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * MainActivity.kt — the settings app
 *
 * Flow:
 *   setup not finished   -> SetupWizard (SetupScreens.kt)
 *   PIN set + locked     -> PinScreen
 *   otherwise            -> five tabs: Status, Apps, Filters, Verses, Protection
 *
 * The PIN screen re-appears every time the app leaves the screen (onStop).
 * Forgot the PIN? While protection is on, the PIN screen offers the paper
 * recovery code: it turns protection off (as on the Protect tab) and then you
 * set a new PIN. Without the code, the PIN can't be bypassed.
 * The PIN only guards the settings UI; the real protection against loosening
 * is the delay/lock in ChangeGate, which applies even with the right PIN.
 * =============================================================================
 */
package com.alphablocker.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.res.painterResource
import com.alphablocker.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphablocker.Graph
import com.alphablocker.core.SecretResult
import com.alphablocker.core.t
import com.alphablocker.security.Crypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** False = show the PIN screen. Reset every time the app is left. */
    private val unlocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE: no screenshots or screen recordings of the settings, and
        // the Recents preview is blank. Protects the recovery code, PIN entry and
        // your block lists from being captured by other apps or cloud photo backup.
        // Debug builds (run from Android Studio) skip it so you can take store
        // screenshots; every release build has it.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        setContent {
            AlphaTheme {
                Root(unlocked = unlocked.value, onUnlock = { unlocked.value = true })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Graph.enforcer.syncAsync()
    }

    override fun onStop() {
        super.onStop()
        unlocked.value = false
    }
}

@Composable
private fun Root(unlocked: Boolean, onUnlock: () -> Unit) {
    val state by Graph.store.state.collectAsState()
    // Surface (not a plain Box) sets the default text color to the theme's
    // "onBackground": black in light mode, white in dark mode. Without it,
    // setup and PIN text stayed black on the dark background.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !state.setupComplete -> SetupWizard()
            state.pinHash != null && !unlocked -> PinScreen(onUnlock)
            else -> HomeScaffold()
        }
    }
}

/** Blue title bar with the Alpha Blocker logo, used at the top of every screen. */
@Composable
fun Header(title: String) {
    Row(
        Modifier.fillMaxWidth().background(BrandBlue).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = "Alpha Blocker logo", modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

// --------------------------------------------------------------------- PIN

@Composable
private fun PinScreen(onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf(false) }
    val state by Graph.store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    /** Checks the PIN (also triggered by the keyboard's ✓ / Done key). */
    fun submit() {
        if (busy || pin.isEmpty()) return
        busy = true
        scope.launch {
            // Hash checking is slow on purpose; keep it off the UI thread.
            val r = withContext(Dispatchers.Default) { Graph.gate.checkPin(pin) }
            busy = false
            when (r) {
                SecretResult.Ok -> onUnlock()
                SecretResult.Wrong -> { message = t("Wrong PIN.", "PIN incorrecto."); pin = "" }
                is SecretResult.TooManyAttempts -> message = t("Too many tries. Try again ${formatTrusted(r.retryAt)}.", "Demasiados intentos. Vuelve a intentarlo el ${formatTrusted(r.retryAt)}.")
            }
        }
    }

    // Focus the PIN box and open the number keyboard as soon as the screen appears.
    LaunchedEffect(Unit) {
        delay(150) // let the screen finish laying out first
        focus.requestFocus()
        keyboard?.show()
    }

    Column(Modifier.fillMaxSize()) {
        Header("Alpha Blocker")
        // imePadding: keeps the PIN centered in the space ABOVE the keyboard.
        Column(
            Modifier.fillMaxSize().imePadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(t("Enter your PIN to change settings.", "Escribe tu PIN para cambiar los ajustes."), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                label = { Text("PIN") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 24.sp, letterSpacing = 6.sp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.width(220.dp).focusRequester(focus),
            )
            Spacer(Modifier.height(16.dp))
            Button(enabled = !busy && pin.isNotEmpty(), onClick = { submit() }, modifier = Modifier.width(220.dp)) {
                Text(if (busy) t("Checking…", "Comprobando…") else t("Unlock", "Desbloquear"))
            }
            message?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            // Only while protection is on: that's when a recovery code exists.
            if (state.recoveryHash != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { recovering = true }) { Text(t("Forgot PIN? Use recovery code", "¿Olvidaste el PIN? Usa el código de recuperación")) }
            }
        }
    }

    if (recovering) RecoveryDialog(onDismiss = { recovering = false }, onSuccess = { newPin = true })
    if (newPin) NewPinDialog(onDone = { newPin = false; onUnlock() })
}

/**
 * After the recovery code was used from the PIN screen: choose a new PIN.
 * Can't be skipped (the old PIN is unknown, and settings need one).
 */
@Composable
private fun NewPinDialog(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {}, // must choose a PIN
        title = { Text(t("Choose a new PIN", "Elige un PIN nuevo")) },
        text = {
            Column {
                Text(t("Protection is off. Set a new PIN for the settings.", "La protección está apagada. Elige un PIN nuevo para los ajustes."))
                listOf(
                    Triple(t("New PIN", "PIN nuevo"), p1) { v: String -> p1 = v },
                    Triple(t("Repeat new PIN", "Repite el PIN nuevo"), p2) { v: String -> p2 = v },
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
                if (p1.length < 4 || p1 != p2) {
                    android.widget.Toast.makeText(context, t("PINs must match and have 4+ digits.", "Los PIN deben coincidir y tener 4+ dígitos."), android.widget.Toast.LENGTH_LONG).show()
                    return@Button
                }
                scope.launch {
                    val hash = withContext(Dispatchers.Default) { Crypto.hash(p1) }
                    Graph.store.update { it.copy(pinHash = hash) }
                    onDone()
                }
            }) { Text(t("Save", "Guardar")) }
        },
    )
}

// -------------------------------------------------------------------- tabs

private enum class Tab(private val en: String, private val es: String, val icon: String) {
    STATUS("Status", "Estado", "◉"), APPS("Apps", "Apps", "▦"), FILTERS("Filters", "Filtros", "⛨"),
    VERSES("Verses", "Versículos", "✝"), PROTECTION("Protect", "Proteger", "⚙");

    val label: String get() = t(en, es)
}

@Composable
private fun HomeScaffold() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val state by Graph.store.state.collectAsState()
    Scaffold(
        topBar = { Header("Alpha Blocker · ${Tab.entries[tab].label}") },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(t.icon, fontSize = 18.sp) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.STATUS -> StatusScreen(state)
                Tab.APPS -> AppsScreen(state)
                Tab.FILTERS -> FiltersScreen(state)
                Tab.VERSES -> VersesScreen(state)
                Tab.PROTECTION -> ProtectionScreen(state)
            }
        }
    }
}

// ------------------------------------------------- start protection dialog

/**
 * Turns protection on, in two pages:
 *   1. "Before you start": settings to set first, while changes are instant
 *      (BeforeYouStart.kt).
 *   2. A NEW single-use recovery code is generated, shown once, and only its
 *      hash is saved. You must confirm you wrote it down.
 */
@Composable
fun StartProtectionDialog(onDismiss: () -> Unit) {
    val code = remember { Crypto.newRecoveryCode() }
    var checklistSeen by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state by Graph.store.state.collectAsState()

    if (!checklistSeen) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(t("Before you start", "Antes de empezar")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { BeforeYouStartChecklist(state) } },
            confirmButton = { Button(onClick = { checklistSeen = true }) { Text(t("All set, continue", "Listo, continuar")) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(t("Go back and set things", "Volver y configurar")) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(t("Your recovery code", "Tu código de recuperación")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("Write this code on paper. It is shown ONLY ONCE.", "Escribe este código en papel. Se muestra SOLO UNA VEZ."))
                Spacer(Modifier.height(12.dp))
                // Deliberately NOT selectable: copying it would put it on the clipboard,
                // where keyboards' clipboard history can keep it. Write it on paper.
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    t(
                        "In an emergency it turns protection off immediately, even during a lock period. " +
                            "It works once. Store it somewhere inconvenient: a sealed envelope, or with a trusted friend.",
                        "En una emergencia desactiva la protección al instante, incluso durante un periodo de bloqueo. " +
                            "Funciona una vez. Guárdalo en un lugar incómodo: un sobre cerrado o con un amigo de confianza.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text(t("I wrote it down and put it away.", "Lo anoté y lo guardé."))
                }
            }
        },
        confirmButton = {
            Button(enabled = confirmed && !busy, onClick = {
                busy = true
                scope.launch {
                    val hash = withContext(Dispatchers.Default) { Crypto.hash(Crypto.normalizeRecoveryCode(code)) }
                    Graph.store.update { it.copy(protectionActive = true, recoveryHash = hash) }
                    Graph.enforcer.syncAsync()
                    onDismiss()
                }
            }) { Text(t("Start protection", "Iniciar protección")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(t("Not yet", "Todavía no")) } },
    )
}
