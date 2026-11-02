// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * FiltersScreen.kt — blocked words, blocked pages, allowed browsers,
 *                    protected screens, and filter feature switches
 *
 * Word and page lists are hidden until you tap "Show", so explicit terms
 * aren't on screen by default.
 * Adding entries is instant. Removing goes through the delay.
 * =============================================================================
 */
package com.alphablocker.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alphablocker.core.AppCatalog
import com.alphablocker.core.Change
import com.alphablocker.core.Defaults
import com.alphablocker.core.t
import com.alphablocker.data.AppState
import com.alphablocker.data.Feature
import com.alphablocker.data.ProtectedScreen
import java.util.UUID

@Composable
fun FiltersScreen(state: AppState) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {

        Section(t("Filter switches", "Interruptores de filtros")) {
            listOf(Feature.KEYWORD_BLOCKER, Feature.SAFE_SEARCH, Feature.BROWSER_ALLOWLIST, Feature.PROTECTED_SCREENS).forEach { f ->
                SwitchRow(f.label, f in state.features, f.description) {
                    submitWithToast(context, Change.SetFeature(f, it))
                }
            }
        }

        EditableList(
            title = t("Blocked words", "Palabras bloqueadas") + " (${state.keywords.size})",
            hint = t("Word or phrase", "Palabra o frase"),
            help = t(
                "Matched in address bars and typed searches. Words of 4+ letters also match longer words that start with them. Accents don't matter.",
                "Se buscan en barras de direcciones y búsquedas escritas. Las palabras de 4+ letras también coinciden con palabras más largas que empiezan igual. Los acentos no importan.",
            ),
            items = state.keywords.sorted(),
            onAdd = { submitWithToast(context, Change.AddKeyword(it)) },
            onRemove = { submitWithToast(context, Change.RemoveKeyword(it)) },
        )

        EditableList(
            title = t("Blocked pages", "Páginas bloqueadas") + " (${state.domains.size})",
            hint = t("example.com or example.com/path", "ejemplo.com o ejemplo.com/ruta"),
            help = t(
                "A domain also blocks its subdomains. Add a path to block only part of a site.",
                "Un dominio también bloquea sus subdominios. Añade una ruta para bloquear solo parte de un sitio.",
            ),
            items = state.domains.sorted(),
            onAdd = { submitWithToast(context, Change.AddDomain(it)) },
            onRemove = { submitWithToast(context, Change.RemoveDomain(it)) },
        )

        BrowserSection(context, state)

        ProtectedScreensSection(context, state)
    }
}

/** A list hidden behind "Show", with an add field and per-item Remove. */
@Composable
private fun EditableList(
    title: String,
    hint: String,
    help: String,
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    Section(title) {
        Text(help, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, label = { Text(hint) }, singleLine = true, modifier = Modifier.weight(1f))
            Button(enabled = input.isNotBlank(), onClick = { onAdd(input.trim()); input = "" }) { Text(t("Add", "Añadir")) }
        }
        TextButton(onClick = { shown = !shown }) { Text(if (shown) t("Hide list", "Ocultar lista") else t("Show list", "Mostrar lista")) }
        if (shown) items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item, Modifier.weight(1f))
                TextButton(onClick = { onRemove(item) }) { Text(t("Remove", "Quitar")) }
            }
        }
    }
}

@Composable
private fun BrowserSection(context: Context, state: AppState) {
    val installed = remember { AppCatalog.browsers(context) }
    Section(t("Allowed browsers", "Navegadores permitidos")) {
        Text(t(
            "When \"Allowed browsers only\" is on, every browser not switched on here is blocked.",
            "Con \"Solo navegadores permitidos\" activo, se bloquea todo navegador que no esté activado aquí.",
        ), style = MaterialTheme.typography.bodySmall)
        (installed + state.allowedBrowsers).sorted().forEach { pkg ->
            val label = AppCatalog.label(context, pkg)
            val installedNote = if (pkg in installed) "" else t(" (not installed)", " (no instalado)")
            SwitchRow("$label$installedNote", pkg in state.allowedBrowsers, pkg) { allow ->
                submitWithToast(context, if (allow) Change.AllowBrowser(pkg) else Change.DisallowBrowser(pkg))
            }
        }
    }
}

@Composable
private fun ProtectedScreensSection(context: Context, state: AppState) {
    var label by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("com.android.settings") }
    var phrases by remember { mutableStateOf("") }
    Section(t("Protected settings screens", "Pantallas de ajustes protegidas") + " (${state.protectedScreens.size})") {
        Text(t(
            "A screen is closed when ALL its phrases are visible. Use | for alternatives (e.g. Deactivate|Desactivar). Starter entries are for Pixel in English and Spanish; add your own if a screen is worded differently.",
            "Una pantalla se cierra cuando TODAS sus frases están visibles. Usa | para alternativas (p. ej. Deactivate|Desactivar). Las entradas iniciales son para Pixel en inglés y español; añade las tuyas si una pantalla dice otra cosa.",
        ),
            style = MaterialTheme.typography.bodySmall)
        state.protectedScreens.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Defaults.screenLabel(p))
                    Text("${p.pkg}: ${p.allOf.joinToString(" + ") { "\"$it\"" }}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { submitWithToast(context, Change.RemoveProtectedScreen(p.id, p.label)) }) { Text(t("Remove", "Quitar")) }
            }
        }
        Text(t("Add a screen", "Añadir una pantalla"), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(label, { label = it }, label = { Text(t("Name", "Nombre")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pkg, { pkg = it }, label = { Text(t("App package", "Paquete de la app")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phrases, { phrases = it }, label = { Text(t("Phrases, separated by commas", "Frases, separadas por comas")) }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = label.isNotBlank() && pkg.isNotBlank() && phrases.isNotBlank(),
            onClick = {
                val list = phrases.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                submitWithToast(context, Change.AddProtectedScreen(ProtectedScreen(UUID.randomUUID().toString(), label.trim(), pkg.trim(), list)))
                label = ""; phrases = ""
            },
        ) { Text(t("Add screen", "Añadir pantalla")) }
    }
}
