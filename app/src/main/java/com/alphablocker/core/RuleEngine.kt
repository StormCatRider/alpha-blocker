// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * RuleEngine.kt — decides whether an app may be used right now
 *
 * Pure decision logic. Checked in this order (first match wins):
 *   1. Protection off, or app is "critical" (home, dialer, settings…) -> allow
 *   2. Browser not on the allowed list (feature on)                    -> block
 *   3. Rule: always blocked                                            -> block
 *   4. Rule: inside a scheduled block window                           -> block
 *   5. Rule: daily limit (+ extensions) used up                        -> block
 *   6. Otherwise                                                       -> allow
 * =============================================================================
 */
package com.alphablocker.core

import com.alphablocker.data.AppState
import com.alphablocker.data.Feature
import com.alphablocker.data.VerseTheme
import java.time.LocalDateTime

sealed class Decision {
    data object Allow : Decision()

    /**
     * @property reason    short text shown on the block screen.
     * @property theme     which kind of verse to show.
     * @property canExtend true if an extension button should be offered.
     */
    data class Block(val reason: String, val theme: VerseTheme, val canExtend: Boolean) : Decision()
}

object RuleEngine {

    fun evaluate(
        s: AppState,
        pkg: String,
        isBrowser: Boolean,
        critical: Set<String>,
        now: LocalDateTime,
        usedMs: Long,
        extraMs: Long,
        extensionsUsed: Int,
    ): Decision {
        if (!s.protectionActive || pkg in critical) return Decision.Allow

        if (isBrowser && Feature.BROWSER_ALLOWLIST in s.features && pkg !in s.allowedBrowsers) {
            return Decision.Block(t("This browser isn't on your allowed list.", "Este navegador no está en tu lista de permitidos."), VerseTheme.PURITY, false)
        }

        val rule = s.appRules.firstOrNull { it.pkg == pkg } ?: return Decision.Allow

        if (rule.alwaysBlocked) {
            return Decision.Block(t("${rule.label} is blocked.", "${rule.label} está bloqueada."), VerseTheme.DILIGENCE, false)
        }

        val isoDay = now.dayOfWeek.value            // 1 = Monday
        val minute = now.hour * 60 + now.minute
        rule.windows.firstOrNull { it.contains(isoDay, minute) }?.let {
            return Decision.Block(t("${rule.label} is blocked now (${it.describe()}).", "${rule.label} está bloqueada ahora (${it.describe()})."), VerseTheme.DILIGENCE, false)
        }

        rule.dailyLimitMinutes?.let { limit ->
            if (usedMs >= limit * 60_000L + extraMs) {
                val canExtend = extensionsUsed < s.extensionsPerDay
                return Decision.Block(t("Today's $limit-minute limit for ${rule.label} is used up.", "Se agotó el límite de hoy de $limit minutos para ${rule.label}."), VerseTheme.DILIGENCE, canExtend)
            }
        }
        return Decision.Allow
    }
}
