// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * I18n.kt — English / Spanish text
 *
 * Every visible text in the app is written as t("English", "Español"), right
 * where it's used, so an auditor can read both versions side by side.
 *
 * Which language is shown:
 *   * Language setting in the app (Protect tab): "en" or "es" forces it.
 *   * "system" (default): Spanish if the phone's language is Spanish
 *     (including Settings › Apps › Alpha Blocker › Language on Android 13+),
 *     otherwise English.
 * =============================================================================
 */
package com.alphablocker.core

import com.alphablocker.Graph
import java.util.Locale

object I18n {
    const val SYSTEM = "system"
    const val EN = "en"
    const val ES = "es"

    /** True when Spanish text should be shown. */
    val spanish: Boolean
        get() = when (runCatching { Graph.store.current.language }.getOrDefault(SYSTEM)) {
            ES -> true
            EN -> false
            else -> Locale.getDefault().language == "es"
        }
}

/** Returns [en] or [es] depending on the current language (see I18n). */
fun t(en: String, es: String): String = if (I18n.spanish) es else en
