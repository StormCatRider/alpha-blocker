// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Defaults.kt — starter settings used on first launch
 *
 * All of these lists can be extended in the app. Adding is instant; removing
 * goes through the delay (see Changes.kt).
 *
 * The starter word/page lists are intentionally short. DNS filtering (a
 * family DNS set as Android's Private DNS) is the main adult-content layer; these
 * lists catch what DNS misses (search terms, mirror sites).
 * The app hides these lists behind a "Show" button so they aren't on screen
 * by default.
 * =============================================================================
 */
package com.alphablocker.core

import com.alphablocker.data.Feature
import com.alphablocker.data.ProtectedScreen

object Defaults {

    /**
     * Blocked words/phrases. Short (1–3 letter) words only match exactly.
     * Kept to clearly explicit terms on purpose: once protection is on,
     * REMOVING a word that causes false positives waits for the delay, so
     * review this list during setup (while edits are still instant).
     */
    private val KEYWORDS_V1: List<String> = listOf(
        "porn", "xxx", "nsfw", "nudes", "hentai", "onlyfans", "fansly",
        "camgirl", "camgirls", "webcam girls", "sex video", "sex videos", "sex tape", "sex cam",
        "erotica", "milf", "stripchat", "rule34", "r34", "xhamster", "xvideos", "xnxx",
        "redtube", "youporn", "pornhub", "brazzers", "chaturbate", "spankbang",
    )

    /**
     * Added in starter-list version 2: anime / geek-culture explicit terms and
     * a few more general ones. Chosen to avoid common innocent words
     * (e.g. "futa" is left out because it would also match "future", and
     * "hanime" because web addresses like "watchanime" contain it; its site
     * hanime.tv is in the page list instead).
     */
    val KEYWORDS_ADDED_V2: List<String> = listOf(
        // anime / manga / games
        "ecchi", "doujin", "doujinshi", "ahegao", "oppai", "paizuri", "futanari",
        "yaoi", "lewd", "lewds", "r18", "rule 34", "nsfw art", "nsfw cosplay", "nude cosplay",
        "lewd cosplay", "porn game", "porn games", "sex game", "f95zone", "nutaku", "yiff",
        "porn comic", "porn comics", "hentaihaven",
        // general
        "nude pics", "naked girls", "leaked nudes", "onlyfans leak", "camsoda", "bongacams",
    )

    /**
     * Added in starter-list version 3: Spanish terms. Matching ignores
     * accents, so "explicito" also matches "explícito".
     * Words that are innocent in some countries (e.g. "coger") are left out.
     */
    val KEYWORDS_ADDED_V3: List<String> = listOf(
        "desnudas", "chicas desnudas", "mujeres desnudas", "fotos desnudas", "fotos intimas",
        "videos porno", "sexo explicito", "sexo en vivo", "webcam sexo", "follando",
        "putas", "cachondas", "packs filtrados",
    )

    val KEYWORDS: List<String> = KEYWORDS_V1 + KEYWORDS_ADDED_V2 + KEYWORDS_ADDED_V3

    /** Blocked pages: domains (includes subdomains) or domain/path. */
    private val DOMAINS_V1: List<String> = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
        "spankbang.com", "chaturbate.com", "stripchat.com", "onlyfans.com", "fansly.com",
        "rule34.xxx", "e-hentai.org", "nhentai.net", "reddit.com/r/nsfw", "reddit.com/r/gonewild",
    )

    /** Added in starter-list version 2: anime / geek-culture adult sites and a few more. */
    val DOMAINS_ADDED_V2: List<String> = listOf(
        "hanime.tv", "hentaihaven.xxx", "exhentai.org", "hitomi.la", "rule34video.com",
        "rule34.paheal.net", "gelbooru.com", "e621.net", "chan.sankakucomplex.com",
        "f95zone.to", "nutaku.net", "kemono.su", "coomer.su", "simply-hentai.com",
        "camsoda.com", "bongacams.com", "reddit.com/r/hentai", "reddit.com/r/rule34",
    )

    val DOMAINS: List<String> = DOMAINS_V1 + DOMAINS_ADDED_V2

    /**
     * Version of the starter lists. When the app is updated and this number
     * is higher than the saved one, the new starter words/pages are ADDED to
     * your lists (adding is tightening, so it's instant). Words you removed
     * earlier are not re-added by older versions' lists.
     */
    const val LISTS_VERSION = 6

    /**
     * Browsers you chose to allow. Every OTHER browser is blocked when
     * "Allowed browsers only" is on.
     */
    val ALLOWED_BROWSERS: List<String> = listOf(
        "com.android.chrome",              // Chrome
        "org.mozilla.firefox",             // Firefox
        "com.brave.browser",               // Brave
        "com.duckduckgo.mobile.android",   // DuckDuckGo
        "org.mozilla.fennec_fdroid",       // Fennec (F-Droid)
        "org.ironfoxoss.ironfox",          // IronFox (successor of Mull)
    )

    /** Protected screens of the removed VPN support, deleted by list migration 6. */
    val REMOVED_VPN_SCREEN_IDS: Set<String> = setOf("vpndns", "mvblock", "mvdns")

    /** Features switched on after setup. */
    val FEATURES: Set<Feature> = setOf(
        Feature.KEYWORD_BLOCKER,
        Feature.SAFE_SEARCH,
        Feature.BROWSER_ALLOWLIST,
        Feature.PROTECTED_SCREENS,
        Feature.BLOCK_INSTALLS,
        Feature.BLOCK_DEBUGGING,
    )

    /**
     * Settings screens closed automatically because they could switch
     * protection off. Phrases are matched case-insensitively and ALL must be
     * visible. A phrase can list alternatives with "|", used here for
     * English|Spanish. Written for Pixel / Android 16; the Spanish wording is
     * best-effort, so if your phone words a screen differently, add your own
     * entry in the app (Filters tab).
     */
    val PROTECTED_SCREENS: List<ProtectedScreen> = listOf(
        ProtectedScreen("acc", "Alpha Blocker accessibility switch", "com.android.settings", listOf("Use Alpha Blocker|Usar Alpha Blocker")),
        ProtectedScreen("admin", "Alpha Blocker device admin page", "com.android.settings", listOf("Alpha Blocker", "Deactivate|Desactivar")),
        ProtectedScreen("appinfo", "Alpha Blocker app info (force stop)", "com.android.settings", listOf("Alpha Blocker", "Force stop|Forzar detención|Forzar cierre")),
        // Clearing Alpha Blocker's storage would erase its settings (= protection off).
        // Android usually greys this out for an active device admin; this is a backstop.
        ProtectedScreen("appstore", "Alpha Blocker storage (clear data)", "com.android.settings", listOf("Alpha Blocker", "Clear storage|Clear data|Borrar almacenamiento|Borrar datos")),
        ProtectedScreen("uninst", "Uninstall dialog for Alpha Blocker", "com.google.android.packageinstaller", listOf("Alpha Blocker", "uninstall|desinstalar")),
        ProtectedScreen("pdns", "Private DNS settings", "com.android.settings", listOf("Private DNS provider hostname|proveedor de DNS privado")),
        ProtectedScreen("usbdbg", "Developer options (USB debugging)", "com.android.settings", listOf("USB debugging|Depuración por USB|Depuración USB")),
        // Security & privacy › "apps with full access" review, and the full-access
        // notification's dialog. They live in Android's Permission Controller
        // (Google's version on Pixel). Closed only when they show Alpha Blocker
        // together with an off/remove button, so the rest of Security & privacy stays usable.
        ProtectedScreen("fullacc", "Full-access review (turn off Alpha Blocker)", "com.google.android.permissioncontroller", listOf("Alpha Blocker", FULL_ACCESS_OFF)),
        ProtectedScreen("fullacc2", "Full-access review (turn off Alpha Blocker, AOSP)", "com.android.permissioncontroller", listOf("Alpha Blocker", FULL_ACCESS_OFF)),
    )

    /** Buttons that would switch Alpha Blocker off on the full-access screens (English|Spanish). */
    private const val FULL_ACCESS_OFF = "Turn off|Remove access|Uninstall|Desactivar|Quitar acceso|Desinstalar|Detener"

    /** Ids of starter screens replaced by newer versions (an early DNS entry). */
    val RETIRED_SCREEN_IDS: Set<String> = setOf("mvdns")

    /** Spanish names for the starter screens (the saved label is English). */
    private val SCREEN_LABELS_ES: Map<String, String> = mapOf(
        "acc" to "Interruptor de accesibilidad de Alpha Blocker",
        "admin" to "Página de administrador de dispositivo de Alpha Blocker",
        "appinfo" to "Información de la app Alpha Blocker (forzar detención)",
        "appstore" to "Almacenamiento de Alpha Blocker (borrar datos)",
        "uninst" to "Diálogo para desinstalar Alpha Blocker",
        "pdns" to "Ajustes de DNS privado",
        "usbdbg" to "Opciones de desarrollador (depuración USB)",
        "fullacc" to "Revisión de acceso completo (desactivar Alpha Blocker)",
        "fullacc2" to "Revisión de acceso completo (desactivar Alpha Blocker, AOSP)",
    )

    /** Display name of a protected screen in the current language. */
    fun screenLabel(p: ProtectedScreen): String =
        SCREEN_LABELS_ES[p.id]?.let { t(p.label, it) } ?: p.label
}
