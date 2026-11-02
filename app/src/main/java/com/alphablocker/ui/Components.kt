// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Components.kt — small reusable UI pieces and formatting helpers
 * =============================================================================
 */
package com.alphablocker.ui

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alphablocker.Graph
import com.alphablocker.core.Change
import com.alphablocker.core.I18n
import com.alphablocker.core.SubmitResult
import com.alphablocker.core.t
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** A titled card that groups related settings. */
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Label + description + switch on one row. */
@Composable
fun SwitchRow(label: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Orange accent button (dark text for contrast). */
@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandOrange,
            contentColor = OnOrange,
            // Disabled (e.g. during the block-screen countdown): faded orange, text still readable.
            disabledContainerColor = BrandOrange.copy(alpha = 0.55f),
            disabledContentColor = OnOrange.copy(alpha = 0.75f),
        ),
    ) { Text(text) }
}

/** Two buttons side by side. */
@Composable
fun ButtonRow(primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSecondary) { Text(secondary) }
        Button(onClick = onPrimary) { Text(primary) }
    }
}

/**
 * Submits a change and tells the user what happened:
 * applied now, queued until a time, or refused because of the lock.
 */
fun submitWithToast(context: Context, change: Change) {
    val msg = when (val r = Graph.submit(change)) {
        SubmitResult.Applied -> t("Applied.", "Aplicado.")
        is SubmitResult.Queued -> t(
            "This loosens protection, so it will apply ${formatTrusted(r.applyAt)}. You can cancel it on the Status tab.",
            "Esto afloja la protección, así que se aplicará el ${formatTrusted(r.applyAt)}. Puedes cancelarlo en la pestaña Estado.",
        )
        is SubmitResult.Rejected -> r.reason
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

/** Trusted time -> "Oct 29, 2026, 3:00 PM" on the normal clock. */
fun formatTrusted(trusted: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, uiLocale()).format(Date(Graph.clock.toWall(trusted)))

/** Locale matching the app's chosen language, for dates. */
private fun uiLocale(): Locale = if (I18n.spanish) Locale.forLanguageTag("es") else Locale.getDefault().takeIf { it.language != "es" } ?: Locale.ENGLISH

/** 93_784_000 -> "1d 2h 3m" */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return t("now", "ahora")
    val totalMin = ms / 60_000
    val d = totalMin / 1440
    val h = (totalMin % 1440) / 60
    val m = totalMin % 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (h > 0 || d > 0) append("${h}h ")
        append("${m}m")
    }
}

/** Trusted "now" that refreshes every 30 s, for countdowns. */
@Composable
fun rememberTrustedNow(): State<Long> = produceState(initialValue = Graph.clock.now()) {
    while (true) {
        delay(30_000)
        value = Graph.clock.now()
    }
}

/**
 * Language choice: phone setting, English or Spanish. Saved in the app's
 * state (not a protection setting, so it changes instantly), then the screen
 * is rebuilt so every text switches language.
 */
@Composable
fun LanguagePicker() {
    val activity = LocalContext.current as? Activity
    val current = Graph.store.current.language
    val options = listOf(
        I18n.SYSTEM to t("Phone language", "Idioma del teléfono"),
        I18n.EN to "English",
        I18n.ES to "Español",
    )
    Column {
        options.forEach { (code, label) ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (code != current) {
                        Graph.store.update { it.copy(language = code) }
                        activity?.recreate()
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = code == current, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(label, Modifier.padding(vertical = 10.dp))
            }
        }
    }
}
