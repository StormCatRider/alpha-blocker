// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * VersesScreen.kt — built-in verses and your own verses
 *
 * Adding or removing YOUR verses is always instant: verses don't affect what
 * gets blocked, only what you see on the block screen.
 * =============================================================================
 */
package com.alphablocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.alphablocker.Graph
import com.alphablocker.core.Verses
import com.alphablocker.core.t
import com.alphablocker.data.AppState
import com.alphablocker.data.Verse
import com.alphablocker.data.VerseTheme
import java.util.UUID

@Composable
fun VersesScreen(state: AppState) {
    var reference by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(VerseTheme.PURITY) }
    var showBuiltIn by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {

        Section(t("Add your own verse", "Añade tu propio versículo")) {
            OutlinedTextField(reference, { reference = it }, label = { Text(t("Reference, e.g. Psalm 50:12", "Referencia, p. ej. Salmo 50:12")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(text, { text = it }, label = { Text(t("Verse text", "Texto del versículo")) }, modifier = Modifier.fillMaxWidth())
            Text(t("Show it when:", "Mostrarlo cuando:"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VerseTheme.entries.forEach { t ->
                    if (t == theme) Button(onClick = {}) { Text(t.label) }
                    else OutlinedButton(onClick = { theme = t }) { Text(t.label) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(enabled = reference.isNotBlank() && text.isNotBlank(), onClick = {
                val v = Verse(UUID.randomUUID().toString(), reference.trim(), text.trim(), theme)
                Graph.store.update { it.copy(customVerses = it.customVerses + v) }
                reference = ""; text = ""
            }) { Text(t("Add verse", "Añadir versículo")) }
        }

        Section(t("Your verses", "Tus versículos") + " (${state.customVerses.size})") {
            if (state.customVerses.isEmpty()) Text(t("None yet.", "Ninguno todavía."))
            state.customVerses.forEach { v ->
                VerseItem(v)
                TextButton(onClick = { Graph.store.update { s -> s.copy(customVerses = s.customVerses.filterNot { it.id == v.id }) } }) {
                    Text(t("Remove", "Quitar"))
                }
            }
        }

        Section(t("Built-in verses", "Versículos incluidos") + " (${Verses.builtIn().size}, " + t("Douay-Rheims", "Torres Amat") + ")") {
            TextButton(onClick = { showBuiltIn = !showBuiltIn }) { Text(if (showBuiltIn) t("Hide", "Ocultar") else t("Show all", "Mostrar todos")) }
            if (showBuiltIn) Verses.builtIn().forEach { VerseItem(it) }
        }
    }
}

@Composable
private fun VerseItem(v: Verse) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("“${v.text}”", fontStyle = FontStyle.Italic)
        Text("— ${v.reference} · ${v.theme.label}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
    }
}
