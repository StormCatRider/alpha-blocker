// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * StateJson.kt — converts AppState to/from JSON text
 *
 * Hand-written with Android's built-in org.json so there are no third-party
 * serialization libraries to audit. Unknown or missing fields fall back to
 * safe defaults, so an older saved file still loads after an app update.
 * =============================================================================
 */
package com.alphablocker.data

import com.alphablocker.core.Change
import com.alphablocker.core.Defaults
import com.alphablocker.core.I18n
import org.json.JSONArray
import org.json.JSONObject

object StateJson {

    fun toJson(s: AppState): JSONObject = JSONObject().apply {
        put("version", 1)
        put("setupComplete", s.setupComplete)
        put("language", s.language)
        put("mode", s.mode.name)
        put("protectionActive", s.protectionActive)
        put("pinHash", s.pinHash ?: JSONObject.NULL)
        put("recoveryHash", s.recoveryHash ?: JSONObject.NULL)
        put("delayHours", s.delayHours)
        put("lockUntil", s.lockUntil)
        put("extensionsPerDay", s.extensionsPerDay)
        put("extensionMinutes", s.extensionMinutes)
        put("listsVersion", s.listsVersion)
        put("keywords", JSONArray(s.keywords))
        put("domains", JSONArray(s.domains))
        put("allowedBrowsers", JSONArray(s.allowedBrowsers))
        put("appRules", JSONArray(s.appRules.map { ruleToJson(it) }))
        put("protectedScreens", JSONArray(s.protectedScreens.map { screenToJson(it) }))
        put("customVerses", JSONArray(s.customVerses.map { verseToJson(it) }))
        put("features", JSONArray(s.features.map { it.name }))
        put("privateDnsHost", s.privateDnsHost ?: JSONObject.NULL)
        put("installWindowUntil", s.installWindowUntil)
        put("showLimitCountdown", s.showLimitCountdown)
        put("bankPauseEnabled", s.bankPauseEnabled)
        put("bankApps", JSONArray(s.bankApps))
        put("bankPauseMinutes", s.bankPauseMinutes)
        put("bankPauseUntil", s.bankPauseUntil)
        put("pending", JSONArray(s.pending.map { p ->
            JSONObject().put("id", p.id).put("change", p.change.toJson())
                .put("requestedAt", p.requestedAt).put("applyAt", p.applyAt)
        }))
        put("suspendedByUs", JSONArray(s.suspendedByUs.toList()))
        put("managedVpn", s.managedVpn ?: JSONObject.NULL)
        put("managedDnsHost", s.managedDnsHost ?: JSONObject.NULL)
        put("devRequest", s.devRequest ?: JSONObject.NULL)
        put("failedSecretAttempts", s.failedSecretAttempts)
        put("secretBackoffUntil", s.secretBackoffUntil)
    }

    /**
     * Reads saved state, then merges in starter words/pages added by newer app
     * versions (adding only, never removing).
     */
    fun fromJson(o: JSONObject): AppState {
        val s = parse(o)
        val saved = o.optInt("listsVersion", 1)
        if (saved >= Defaults.LISTS_VERSION) return s
        var keywords = s.keywords
        var domains = s.domains
        var screens = s.protectedScreens
        if (saved < 2) {
            keywords = (keywords + Defaults.KEYWORDS_ADDED_V2).distinct()
            domains = (domains + Defaults.DOMAINS_ADDED_V2).distinct()
        }
        if (saved < 3) {
            keywords = (keywords + Defaults.KEYWORDS_ADDED_V3).distinct()
            // Starter screens get their new English|Spanish phrases; the Mullvad-only
            // DNS entry is replaced by the "any VPN" one. Your own entries are kept.
            val starterIds = Defaults.PROTECTED_SCREENS.map { it.id }.toSet() + Defaults.RETIRED_SCREEN_IDS
            screens = Defaults.PROTECTED_SCREENS + screens.filterNot { it.id in starterIds }
        }
        if (saved < 4) {
            // Add new starter screens you don't have yet (Security & privacy full-access review).
            val have = screens.map { it.id }.toSet()
            screens = screens + Defaults.PROTECTED_SCREENS.filter { it.id !in have }
        }
        if (saved < 5) {
            // Add the "clear storage" protection if you don't have it yet.
            if (screens.none { it.id == "appstore" }) {
                screens = screens + Defaults.PROTECTED_SCREENS.filter { it.id == "appstore" }
            }
        }
        if (saved < 6) {
            // VPN support was removed: drop the VPN-only protected screens.
            screens = screens.filterNot { it.id in Defaults.REMOVED_VPN_SCREEN_IDS || it.pkg == "@vpn" }
        }
        return s.copy(listsVersion = Defaults.LISTS_VERSION, keywords = keywords, domains = domains, protectedScreens = screens)
    }

    private fun parse(o: JSONObject): AppState = AppState(
        setupComplete = o.optBoolean("setupComplete", false),
        language = o.optString("language", I18n.SYSTEM),
        mode = runCatching { ProtectionMode.valueOf(o.getString("mode")) }.getOrDefault(ProtectionMode.NONE),
        protectionActive = o.optBoolean("protectionActive", false),
        pinHash = o.optStringOrNull("pinHash"),
        recoveryHash = o.optStringOrNull("recoveryHash"),
        delayHours = o.optInt("delayHours", 72),
        lockUntil = o.optLong("lockUntil", 0L),
        extensionsPerDay = o.optInt("extensionsPerDay", 2),
        extensionMinutes = o.optInt("extensionMinutes", 5),
        keywords = o.optJSONArray("keywords")?.strings() ?: Defaults.KEYWORDS,
        domains = o.optJSONArray("domains")?.strings() ?: Defaults.DOMAINS,
        allowedBrowsers = o.optJSONArray("allowedBrowsers")?.strings() ?: Defaults.ALLOWED_BROWSERS,
        appRules = o.optJSONArray("appRules")?.objects()?.map { ruleFromJson(it) } ?: emptyList(),
        protectedScreens = o.optJSONArray("protectedScreens")?.objects()?.map { screenFromJson(it) }
            ?: Defaults.PROTECTED_SCREENS,
        customVerses = o.optJSONArray("customVerses")?.objects()?.mapNotNull { verseFromJson(it) } ?: emptyList(),
        features = o.optJSONArray("features")?.strings()
            ?.mapNotNull { name -> runCatching { Feature.valueOf(name) }.getOrNull() }?.toSet()
            ?: Defaults.FEATURES,
        privateDnsHost = o.optStringOrNull("privateDnsHost"),
        installWindowUntil = o.optLong("installWindowUntil", 0L),
        showLimitCountdown = o.optBoolean("showLimitCountdown", true),
        bankPauseEnabled = o.optBoolean("bankPauseEnabled", false),
        bankApps = o.optJSONArray("bankApps")?.strings() ?: emptyList(),
        bankPauseMinutes = o.optInt("bankPauseMinutes", 4).coerceIn(2, 4),
        bankPauseUntil = o.optLong("bankPauseUntil", 0L),
        pending = o.optJSONArray("pending")?.objects()?.mapNotNull { p ->
            val change = Change.fromJson(p.getJSONObject("change")) ?: return@mapNotNull null
            PendingChange(p.getString("id"), change, p.getLong("requestedAt"), p.getLong("applyAt"))
        } ?: emptyList(),
        suspendedByUs = o.optJSONArray("suspendedByUs")?.strings()?.toSet() ?: emptySet(),
        managedVpn = o.optStringOrNull("managedVpn"),
        managedDnsHost = o.optStringOrNull("managedDnsHost"),
        devRequest = o.optStringOrNull("devRequest"),
        failedSecretAttempts = o.optInt("failedSecretAttempts", 0),
        secretBackoffUntil = o.optLong("secretBackoffUntil", 0L),
    )

    // ---- pieces (also used by Change serialization) ----

    fun ruleToJson(r: AppRule): JSONObject = JSONObject()
        .put("pkg", r.pkg).put("label", r.label).put("always", r.alwaysBlocked)
        .put("limit", r.dailyLimitMinutes ?: JSONObject.NULL)
        .put("windows", JSONArray(r.windows.map {
            JSONObject().put("days", it.daysMask).put("start", it.startMinute).put("end", it.endMinute)
        }))

    fun ruleFromJson(o: JSONObject) = AppRule(
        pkg = o.getString("pkg"),
        label = o.optString("label", o.getString("pkg")),
        alwaysBlocked = o.optBoolean("always", false),
        windows = o.optJSONArray("windows")?.objects()?.map {
            TimeWindow(it.getInt("days"), it.getInt("start"), it.getInt("end"))
        } ?: emptyList(),
        dailyLimitMinutes = if (o.isNull("limit")) null else o.optInt("limit"),
    )

    fun screenToJson(p: ProtectedScreen): JSONObject = JSONObject()
        .put("id", p.id).put("label", p.label).put("pkg", p.pkg).put("allOf", JSONArray(p.allOf))

    fun screenFromJson(o: JSONObject) = ProtectedScreen(
        id = o.getString("id"),
        label = o.optString("label"),
        pkg = o.getString("pkg"),
        allOf = o.optJSONArray("allOf")?.strings() ?: emptyList(),
    )

    private fun verseToJson(v: Verse): JSONObject = JSONObject()
        .put("id", v.id).put("ref", v.reference).put("text", v.text).put("theme", v.theme.name)

    private fun verseFromJson(o: JSONObject): Verse? = runCatching {
        Verse(o.getString("id"), o.getString("ref"), o.getString("text"), VerseTheme.valueOf(o.getString("theme")))
    }.getOrNull()

    // ---- small org.json helpers ----

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
