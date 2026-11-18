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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.Icon
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

/**
 * A titled card that groups related settings: a light card on the calm
 * background with a thin border (visible in light and dark mode), 16 dp
 * corners and padding, and 8 dp between its rows.
 */
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs + 2.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/**
 * Label + description + switch on one row. The WHOLE row is the touch target
 * (at least 56 dp tall) and reads as one switch to screen readers.
 */
@Composable
fun SwitchRow(label: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Space.md))
        Switch(checked = checked, onCheckedChange = null) // the row handles the tap
    }
}

/**
 * The main action button in the palette's accent color, with dark text for
 * contrast; 48 dp tall (Android's minimum touch size), 12 dp corners.
 */
@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val brand = LocalBrand.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = brand.accent,
            contentColor = brand.onAccent,
            // Disabled (e.g. during the block-screen countdown): faded accent, text still readable.
            disabledContainerColor = brand.accent.copy(alpha = 0.55f),
            disabledContentColor = brand.onAccent.copy(alpha = 0.75f),
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Two buttons side by side (secondary outlined, primary filled), both 48 dp tall. */
@Composable
fun ButtonRow(primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        OutlinedButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text(secondary) }
        Button(onClick = onPrimary, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text(primary) }
    }
}

/** Result of a check, shown by shape AND color (never color alone). */
enum class CheckState { OK, PROBLEM, NOT_SET }

/**
 * One status line: an icon (check / cross / open circle) plus text. Used for
 * health checks, setup steps and the "Before you start" checklist.
 */
@Composable
fun StatusLine(state: CheckState, label: String, modifier: Modifier = Modifier, detail: String? = null) {
    val (icon, tint) = when (state) {
        CheckState.OK -> AppIcons.Check to MaterialTheme.colorScheme.primary
        CheckState.PROBLEM -> AppIcons.Cross to MaterialTheme.colorScheme.error
        CheckState.NOT_SET -> AppIcons.Circle to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(22.dp))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (state == CheckState.PROBLEM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = code == current, role = Role.RadioButton) {
                        if (code != current) {
                            Graph.store.update { it.copy(language = code) }
                            activity?.recreate()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = code == current, onClick = null)
                Spacer(Modifier.width(Space.md))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * Color theme choice (ui/Theme.kt). Each row shows the palette's brand and
 * accent colors as two swatches. A display setting, so it applies at once,
 * and the whole app repaints without restarting.
 */
@Composable
fun PalettePicker() {
    val state by Graph.store.state.collectAsState()
    val current = Palette.of(state.palette)
    Column(Modifier.selectableGroup()) {
        Palette.entries.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = p == current, role = Role.RadioButton) {
                        Graph.store.update { it.copy(palette = p.id) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = p == current, onClick = null)
                Spacer(Modifier.width(Space.md))
                // Two swatches: brand (header, block screen) and accent (main button).
                Box(Modifier.size(28.dp).clip(CircleShape).background(p.brand.brand).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                Spacer(Modifier.width(Space.xs))
                Box(Modifier.size(28.dp).clip(CircleShape).background(p.brand.accent).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                Spacer(Modifier.width(Space.md))
                Text(p.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
