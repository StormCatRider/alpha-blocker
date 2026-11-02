// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Models.kt — the data that describes your Alpha Blocker setup
 *
 * Everything the app remembers is in [AppState]. It is saved as one JSON file
 * in the app's private storage (see Store.kt). Private storage can only be
 * read by this app (and by root). The app cannot send it anywhere because it
 * has no internet permission.
 *
 * What is stored:
 *   - your rules (blocked words, pages, apps, schedules, limits)
 *   - protection settings (delay, lock date, features)
 *   - HASHES of your PIN and recovery code (never the plain values)
 *   - pending "loosening" changes waiting for their delay to finish
 * What is NOT stored here: browsing history, URLs you visited, text you typed.
 * (Per-app usage minutes for today are kept separately in UsageTracker.kt.)
 * =============================================================================
 */
package com.alphablocker.data

import com.alphablocker.core.Change
import com.alphablocker.core.Defaults
import com.alphablocker.core.I18n
import com.alphablocker.core.t

/** How strongly the app is anchored on the phone. Chosen during setup. */
enum class ProtectionMode {
    /** Not set up yet. */
    NONE,

    /** Device Admin: easy setup, but a determined user can get around it. */
    DEVICE_ADMIN,

    /** Device Owner: set via ADB; blocks uninstall, factory reset, safe mode. */
    DEVICE_OWNER,
}

/** Which kind of Bible verse fits the moment a block happens. */
enum class VerseTheme(private val en: String, private val es: String) {
    /** Shown when adult content / a blocked browser is stopped. */
    PURITY("Purity & temptation", "Pureza y tentación"),

    /** Shown when an app is blocked by a rule, schedule or time limit. */
    DILIGENCE("Diligence & work", "Diligencia y trabajo"),
    ;

    /** Name in the current language (see core/I18n.kt). */
    val label: String get() = t(en, es)
}

/**
 * On/off switches for protection layers.
 * Turning a feature ON is "tightening" (applies instantly).
 * Turning it OFF is "loosening" (waits for the delay; frozen during a lock).
 */
enum class Feature(
    private val enLabel: String,
    private val esLabel: String,
    private val enDesc: String,
    private val esDesc: String,
) {
    KEYWORD_BLOCKER(
        "Word & page blocker", "Bloqueo de palabras y páginas",
        "Checks browser address bars and typed searches against your blocked words and pages.",
        "Revisa la barra de direcciones y las búsquedas escritas contra tus palabras y páginas bloqueadas.",
    ),
    SAFE_SEARCH(
        "Force SafeSearch", "Forzar SafeSearch",
        "Locks SafeSearch on in Chrome (Device Owner) and rewrites searches in other browsers when it can. For every browser and the YouTube app, also set a family DNS filter (Protect tab).",
        "Fija SafeSearch en Chrome (Device Owner) y reescribe búsquedas en otros navegadores cuando puede. Para todos los navegadores y la app de YouTube, configura también un filtro DNS familiar (pestaña Proteger).",
    ),
    BROWSER_ALLOWLIST(
        "Allowed browsers only", "Solo navegadores permitidos",
        "Every browser not on your allowed list is blocked.",
        "Se bloquea todo navegador que no esté en tu lista de permitidos.",
    ),
    PROTECTED_SCREENS(
        "Protected settings screens", "Pantallas de ajustes protegidas",
        "Closes settings screens that could be used to switch protection off.",
        "Cierra pantallas de ajustes que podrían usarse para desactivar la protección.",
    ),
    BLOCK_INSTALLS(
        "Block new installs", "Bloquear nuevas instalaciones",
        "New apps can only be installed during an install window you request (Device Owner).",
        "Solo se pueden instalar apps durante una ventana de instalación que solicites (Device Owner).",
    ),
    BLOCK_DEBUGGING(
        "Block developer options / USB debugging", "Bloquear opciones de desarrollador / depuración USB",
        "Stops ADB from being used to get around protection (Device Owner).",
        "Impide usar ADB para saltarse la protección (Device Owner).",
    ),
    ;

    /** Name in the current language (see core/I18n.kt). */
    val label: String get() = t(enLabel, esLabel)

    /** Description in the current language. */
    val description: String get() = t(enDesc, esDesc)
}

/**
 * A recurring time window, e.g. Mon–Fri 09:00–17:00.
 *
 * @property daysMask    bit 0 = Monday … bit 6 = Sunday (ISO day numbers 1..7).
 * @property startMinute minutes after midnight, 0..1439.
 * @property endMinute   minutes after midnight, 0..1439. If end < start the
 *                       window runs overnight (e.g. 22:00–06:00).
 *                       If end == start the window covers the whole day.
 */
data class TimeWindow(val daysMask: Int, val startMinute: Int, val endMinute: Int) {

    /**
     * True if [isoDay] (1=Mon..7=Sun) at [minute] falls inside this window.
     * Overnight windows belong to the day they START on, so "Fri 22:00–06:00"
     * also covers Saturday 00:00–06:00.
     */
    fun contains(isoDay: Int, minute: Int): Boolean {
        val today = (daysMask and bit(isoDay)) != 0
        return when {
            startMinute == endMinute -> today
            startMinute < endMinute -> today && minute >= startMinute && minute < endMinute
            else -> {
                val previous = if (isoDay == 1) 7 else isoDay - 1
                (today && minute >= startMinute) ||
                    ((daysMask and bit(previous)) != 0 && minute < endMinute)
            }
        }
    }

    /** Human-readable form, e.g. "Mon Tue Wed 09:00–17:00". */
    fun describe(): String {
        val days = (1..7).filter { (daysMask and bit(it)) != 0 }.joinToString(" ") { dayNames()[it - 1] }
        val range = if (startMinute == endMinute) t("all day", "todo el día") else "${hhmm(startMinute)}–${hhmm(endMinute)}"
        return "$days $range"
    }

    companion object {
        private val DAY_NAMES_EN = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val DAY_NAMES_ES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

        /** Short day names (Monday first) in the current language. */
        fun dayNames(): List<String> = if (I18n.spanish) DAY_NAMES_ES else DAY_NAMES_EN
        const val ALL_DAYS = 0b1111111

        /** Bit for an ISO day number (1=Monday). */
        fun bit(isoDay: Int): Int = 1 shl (isoDay - 1)

        /** 75 -> "01:15" */
        fun hhmm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

        /** "01:15" -> 75, or null if the text isn't a valid time. */
        fun parseHhmm(text: String): Int? {
            val parts = text.trim().split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
    }
}

/**
 * Blocking rule for one app. A rule can combine all three options.
 *
 * @property alwaysBlocked      app is blocked at all times.
 * @property windows            app is blocked during these time windows.
 * @property dailyLimitMinutes  app is allowed this many minutes per day,
 *                              then blocked (null = no limit).
 */
data class AppRule(
    val pkg: String,
    val label: String,
    val alwaysBlocked: Boolean,
    val windows: List<TimeWindow>,
    val dailyLimitMinutes: Int?,
) {
    /**
     * True if this rule blocks AT LEAST as much as [old].
     * Used to decide if an edit is tightening (instant) or loosening (delayed).
     * Conservative: any doubt counts as loosening.
     */
    fun isAtLeastAsStrictAs(old: AppRule): Boolean {
        if (alwaysBlocked) return true               // nothing is stricter
        if (old.alwaysBlocked) return false          // removing "always" loosens
        val keepsAllWindows = old.windows.all { it in windows }
        val limitOk = when {
            old.dailyLimitMinutes == null -> true                // old had no limit
            dailyLimitMinutes == null -> false                   // limit removed
            else -> dailyLimitMinutes <= old.dailyLimitMinutes   // same or shorter
        }
        return keepsAllWindows && limitOk
    }

    /** Short summary for the UI. */
    fun summary(): String = buildList {
        if (alwaysBlocked) add(t("Always blocked", "Siempre bloqueada"))
        dailyLimitMinutes?.let { add(t("$it min/day", "$it min/día")) }
        windows.forEach { add(t("Blocked ${it.describe()}", "Bloqueada ${it.describe()}")) }
        if (isEmpty()) add(t("No restriction", "Sin restricción"))
    }.joinToString(" · ")
}

/** A Bible verse shown on the block screen. */
data class Verse(
    val id: String,
    val reference: String,
    val text: String,
    val theme: VerseTheme,
)

/**
 * A settings screen that should be closed when it appears, because it could
 * be used to switch protection off (e.g. the accessibility toggle for this app).
 *
 * @property pkg   app the screen belongs to, e.g. "com.android.settings".
 * @property allOf the screen is closed when ALL of these phrases are visible
 *                 (case-insensitive). Requiring several phrases avoids
 *                 closing unrelated screens.
 */
data class ProtectedScreen(
    val id: String,
    val label: String,
    val pkg: String,
    val allOf: List<String>,
)

/**
 * A loosening change waiting for its delay to pass.
 *
 * @property applyAt time (trusted clock, see TrustedClock.kt) when it applies.
 */
data class PendingChange(
    val id: String,
    val change: Change,
    val requestedAt: Long,
    val applyAt: Long,
)

/**
 * Everything Alpha Blocker remembers. Immutable: every edit makes a copy,
 * which keeps updates simple to reason about (see Store.update).
 *
 * Times ending in "Until"/"At" are on the TRUSTED clock (TrustedClock.kt),
 * which can't be fooled by changing the phone's date.
 */
data class AppState(
    // ---- setup ----
    val setupComplete: Boolean = false,
    /** UI language: "system", "en" or "es" (see core/I18n.kt). Not a protection setting. */
    val language: String = I18n.SYSTEM,
    val mode: ProtectionMode = ProtectionMode.NONE,
    val protectionActive: Boolean = false,

    // ---- secrets (salted PBKDF2 hashes only; see security/Crypto.kt) ----
    val pinHash: String? = null,
    val recoveryHash: String? = null,

    // ---- how hard it is to loosen things ----
    val delayHours: Int = 72,
    val lockUntil: Long = 0L,

    // ---- time-limit extensions (shared across all apps, per day) ----
    val extensionsPerDay: Int = 2,
    val extensionMinutes: Int = 5,

    // ---- rules ----
    /** Which starter-list version has been merged in (see Defaults.LISTS_VERSION). */
    val listsVersion: Int = Defaults.LISTS_VERSION,
    val keywords: List<String> = Defaults.KEYWORDS,
    val domains: List<String> = Defaults.DOMAINS,
    val allowedBrowsers: List<String> = Defaults.ALLOWED_BROWSERS,
    val appRules: List<AppRule> = emptyList(),
    val protectedScreens: List<ProtectedScreen> = Defaults.PROTECTED_SCREENS,
    val customVerses: List<Verse> = emptyList(),
    val features: Set<Feature> = Defaults.FEATURES,

    // ---- DNS filter (Private DNS host, set and locked in Device Owner mode) ----
    val privateDnsHost: String? = null,

    // ---- display ----
    /** "Time left today" notification for time-limited apps (service/LimitCountdown.kt). Not protection: instant. */
    val showLimitCountdown: Boolean = true,

    // ---- banking pause (opt-in; see enforcement/BankPause.kt) ----
    /** Lets the Status tab open a chosen bank app with the blocker service paused. */
    val bankPauseEnabled: Boolean = false,
    /** Package names of the bank apps you picked. */
    val bankApps: List<String> = emptyList(),
    /** Pause length before the "turn back on" popup: 2, 3 or 4 minutes (+2 extra on request). */
    val bankPauseMinutes: Int = 4,

    // ---- install window (Device Owner, BLOCK_INSTALLS) ----
    val installWindowUntil: Long = 0L,

    // ---- queue of delayed loosening changes ----
    val pending: List<PendingChange> = emptyList(),

    // ---- bookkeeping written by the enforcer (not user settings) ----
    /** Apps WE suspended, so we only ever un-suspend our own. */
    val suspendedByUs: Set<String> = emptySet(),
    /**
     * Always-on VPN set by an OLDER version of Alpha Blocker (VPN support was
     * removed). Kept only so the next sync can undo that setting, then cleared.
     */
    val managedVpn: String? = null,
    /** Private DNS host WE set, so we only undo our own setting. */
    val managedDnsHost: String? = null,
    /**
     * Trusted time when the running banking pause ends for sure (0 = no pause).
     * The "turn protection back on" popup appears 2 minutes before this.
     */
    val bankPauseUntil: Long = 0L,

    // ---- developer unlock (security/DevUnlock.kt) ----
    /** This phone's current request code; replaced after each successful unlock. */
    val devRequest: String? = null,

    // ---- brute-force protection for PIN and recovery code ----
    val failedSecretAttempts: Int = 0,
    val secretBackoffUntil: Long = 0L,
)
