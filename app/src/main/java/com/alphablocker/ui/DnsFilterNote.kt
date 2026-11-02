// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * DnsFilterNote.kt — explains DNS filtering and offers family DNS hosts
 *
 * A family DNS service blocks adult sites for EVERY app and browser on the
 * phone, including in-app browsers the word blocker can't read. Android's
 * "Private DNS" setting sends all lookups to it. The app itself still never
 * connects anywhere: the phone talks to the DNS service, not Alpha Blocker.
 *
 * Shown in the setup wizard (DNS step) and on the Protect tab.
 * The hosts listed are public DNS-over-TLS addresses for Android's Private DNS.
 * =============================================================================
 */
package com.alphablocker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.alphablocker.core.t

/** A suggested family DNS host and what it filters. */
private data class DnsOption(val host: String, val name: String, val whatEn: String, val whatEs: String)

private val OPTIONS = listOf(
    DnsOption(
        "family-filter-dns.cleanbrowsing.org", "CleanBrowsing Family",
        "Adult sites, SafeSearch, YouTube Restricted Mode. Also blocks VPN/proxy sites and some mixed sites such as Reddit.",
        "Sitios para adultos, SafeSearch, modo restringido de YouTube. También bloquea sitios de VPN/proxy y algunos sitios mixtos como Reddit.",
    ),
    DnsOption(
        "adult-filter-dns.cleanbrowsing.org", "CleanBrowsing Adult",
        "Adult sites and SafeSearch; leaves mixed sites such as Reddit open.",
        "Sitios para adultos y SafeSearch; deja abiertos sitios mixtos como Reddit.",
    ),
    DnsOption(
        "family.adguard-dns.com", "AdGuard Family",
        "Adult sites, ads and trackers, SafeSearch.",
        "Sitios para adultos, anuncios y rastreadores, SafeSearch.",
    ),
    DnsOption(
        "family.cloudflare-dns.com", "Cloudflare for Families",
        "Adult sites and malware (no SafeSearch).",
        "Sitios para adultos y malware (sin SafeSearch).",
    ),
)

/**
 * The note. [onPick] fills the host field when a "Use" button is tapped;
 * pass null to show the hosts without buttons.
 */
@Composable
fun DnsFilterNote(onPick: ((String) -> Unit)?) {
    Column {
        Text(
            t(
                "Recommended: a family DNS filter blocks adult sites in every app and browser, including links opened inside apps. It works through Android's Private DNS setting; Alpha Blocker itself still never goes online.",
                "Recomendado: un filtro DNS familiar bloquea sitios para adultos en todas las apps y navegadores, incluidos los enlaces abiertos dentro de apps. Funciona con el ajuste DNS privado de Android; Alpha Blocker sigue sin conectarse a internet.",
            ),
        )
        Spacer(Modifier.height(8.dp))
        OPTIONS.forEach { o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(o.name, style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(o.host, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    Text(t(o.whatEn, o.whatEs), style = MaterialTheme.typography.bodySmall)
                }
                if (onPick != null) TextButton(onClick = { onPick(o.host) }) { Text(t("Use", "Usar")) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t("How to turn it on", "Cómo activarlo"), style = MaterialTheme.typography.titleSmall)
        Text(
            t(
                "Device Owner: enter the host below and Alpha Blocker sets it and locks the setting.\nDevice Admin: Settings › Network & internet › Private DNS › Private DNS provider hostname, then enter the host. While protection is on, that screen is closed automatically.",
                "Device Owner: escribe el host abajo y Alpha Blocker lo configura y bloquea el ajuste.\nAdministrador de dispositivo: Ajustes › Redes e Internet › DNS privado › Nombre de host del proveedor de DNS privado, y escribe el host. Con la protección activa, esa pantalla se cierra automáticamente.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            t(
                "Note: a VPN app replaces Private DNS while it's connected. If you use one, set a family DNS inside the VPN app too.",
                "Nota: una app de VPN reemplaza el DNS privado mientras está conectada. Si usas una, configura también un DNS familiar dentro de la app de VPN.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
