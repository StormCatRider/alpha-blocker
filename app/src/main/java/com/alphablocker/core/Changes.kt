// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Changes.kt — every edit to your protection goes through here
 *
 * THE CORE RULE OF ALPHA BLOCKER:
 *   * TIGHTENING changes (more blocking) apply instantly.
 *   * LOOSENING changes (less blocking) wait for your chosen delay, and are
 *     refused entirely while a fixed lock period is active.
 *
 * Each possible edit is a [Change] subclass that answers two questions:
 *   1. isTightening(state)  -> does this make protection stronger?
 *   2. applyTo(state, now)  -> what does the state look like afterwards?
 *
 * [ChangeGate] decides what happens to a submitted change:
 *   protection off          -> apply now
 *   tightening              -> apply now
 *   loosening + lock active -> rejected
 *   loosening               -> queued; applied when the delay has passed
 *
 * The recovery code (single-use) is the only way to skip the delay and lock.
 * =============================================================================
 */
package com.alphablocker.core

import com.alphablocker.data.AppRule
import com.alphablocker.data.AppState
import com.alphablocker.data.Feature
import com.alphablocker.data.PendingChange
import com.alphablocker.data.ProtectedScreen
import com.alphablocker.data.StateJson
import com.alphablocker.data.Store
import com.alphablocker.security.Crypto
import com.alphablocker.security.DevUnlock
import org.json.JSONObject
import java.util.UUID

/** One requested edit. See the file header for the tightening/loosening rule. */
sealed class Change {
    /** Stable name used when saving pending changes to disk. */
    abstract val type: String

    /** Text shown in the "pending changes" list. */
    abstract fun describe(): String

    /** True if this change only makes protection stronger (or keeps it equal). */
    abstract fun isTightening(s: AppState): Boolean

    /** Returns the new state with this change applied. Must not have side effects. */
    abstract fun applyTo(s: AppState, now: Long): AppState

    /** Adds this change's own fields to [o] when saving. */
    protected open fun writeFields(o: JSONObject) {}

    fun toJson(): JSONObject = JSONObject().put("type", type).also { writeFields(it) }

    // ---------------------------------------------------------------- words --
    data class AddKeyword(val word: String) : Change() {
        override val type = "add_keyword"
        override fun describe() = t("Add a blocked word", "Añadir una palabra bloqueada")
        override fun isTightening(s: AppState) = true
        override fun applyTo(s: AppState, now: Long) =
            s.copy(keywords = (s.keywords + word.trim().lowercase()).distinct())
        override fun writeFields(o: JSONObject) { o.put("word", word) }
    }

    data class RemoveKeyword(val word: String) : Change() {
        override val type = "remove_keyword"
        // The word itself is not displayed, so the pending list can't act as a trigger.
        override fun describe() = t("Remove a blocked word", "Quitar una palabra bloqueada")
        override fun isTightening(s: AppState) = word !in s.keywords
        override fun applyTo(s: AppState, now: Long) = s.copy(keywords = s.keywords - word)
        override fun writeFields(o: JSONObject) { o.put("word", word) }
    }

    // ---------------------------------------------------------------- pages --
    data class AddDomain(val entry: String) : Change() {
        override val type = "add_domain"
        override fun describe() = t("Add a blocked page", "Añadir una página bloqueada")
        override fun isTightening(s: AppState) = true
        override fun applyTo(s: AppState, now: Long) =
            s.copy(domains = (s.domains + ContentMatcher.cleanDomainEntry(entry)).distinct())
        override fun writeFields(o: JSONObject) { o.put("entry", entry) }
    }

    data class RemoveDomain(val entry: String) : Change() {
        override val type = "remove_domain"
        override fun describe() = t("Remove a blocked page", "Quitar una página bloqueada")
        override fun isTightening(s: AppState) = entry !in s.domains
        override fun applyTo(s: AppState, now: Long) = s.copy(domains = s.domains - entry)
        override fun writeFields(o: JSONObject) { o.put("entry", entry) }
    }

    // ----------------------------------------------------------------- apps --
    /** Adds a new app rule or replaces the existing rule for the same app. */
    data class SetAppRule(val rule: AppRule) : Change() {
        override val type = "set_app_rule"
        override fun describe() = t("Change rule for ${rule.label}: ${rule.summary()}", "Cambiar regla de ${rule.label}: ${rule.summary()}")
        override fun isTightening(s: AppState): Boolean {
            val old = s.appRules.firstOrNull { it.pkg == rule.pkg } ?: return true // new rule
            return rule.isAtLeastAsStrictAs(old)
        }
        override fun applyTo(s: AppState, now: Long) =
            s.copy(appRules = s.appRules.filterNot { it.pkg == rule.pkg } + rule)
        override fun writeFields(o: JSONObject) { o.put("rule", StateJson.ruleToJson(rule)) }
    }

    data class RemoveAppRule(val pkg: String, val label: String) : Change() {
        override val type = "remove_app_rule"
        override fun describe() = t("Remove rule for $label", "Quitar regla de $label")
        override fun isTightening(s: AppState) = s.appRules.none { it.pkg == pkg }
        override fun applyTo(s: AppState, now: Long) = s.copy(appRules = s.appRules.filterNot { it.pkg == pkg })
        override fun writeFields(o: JSONObject) { o.put("pkg", pkg).put("label", label) }
    }

    // ------------------------------------------------------------- browsers --
    data class AllowBrowser(val pkg: String) : Change() {
        override val type = "allow_browser"
        override fun describe() = t("Allow browser $pkg", "Permitir navegador $pkg")
        override fun isTightening(s: AppState) = pkg in s.allowedBrowsers
        override fun applyTo(s: AppState, now: Long) = s.copy(allowedBrowsers = (s.allowedBrowsers + pkg).distinct())
        override fun writeFields(o: JSONObject) { o.put("pkg", pkg) }
    }

    data class DisallowBrowser(val pkg: String) : Change() {
        override val type = "disallow_browser"
        override fun describe() = t("Block browser $pkg", "Bloquear navegador $pkg")
        override fun isTightening(s: AppState) = true
        override fun applyTo(s: AppState, now: Long) = s.copy(allowedBrowsers = s.allowedBrowsers - pkg)
        override fun writeFields(o: JSONObject) { o.put("pkg", pkg) }
    }

    // ---------------------------------------------------- protected screens --
    data class AddProtectedScreen(val screen: ProtectedScreen) : Change() {
        override val type = "add_screen"
        override fun describe() = t("Protect screen: ${screen.label}", "Proteger pantalla: ${screen.label}")
        override fun isTightening(s: AppState) = true
        override fun applyTo(s: AppState, now: Long) = s.copy(protectedScreens = s.protectedScreens + screen)
        override fun writeFields(o: JSONObject) { o.put("screen", StateJson.screenToJson(screen)) }
    }

    data class RemoveProtectedScreen(val id: String, val label: String) : Change() {
        override val type = "remove_screen"
        override fun describe() = t("Stop protecting screen: $label", "Dejar de proteger pantalla: $label")
        override fun isTightening(s: AppState) = s.protectedScreens.none { it.id == id }
        override fun applyTo(s: AppState, now: Long) = s.copy(protectedScreens = s.protectedScreens.filterNot { it.id == id })
        override fun writeFields(o: JSONObject) { o.put("id", id).put("label", label) }
    }

    // ------------------------------------------------------------- settings --
    data class SetDelayHours(val hours: Int) : Change() {
        override val type = "set_delay"
        override fun describe() = t("Set change delay to $hours hours", "Cambiar el retraso a $hours horas")
        // A longer delay is stricter. Shortening it must itself wait the OLD delay.
        override fun isTightening(s: AppState) = hours >= s.delayHours
        override fun applyTo(s: AppState, now: Long) = s.copy(delayHours = hours.coerceIn(1, MAX_DELAY_HOURS))
        override fun writeFields(o: JSONObject) { o.put("hours", hours) }
    }

    data class SetExtensions(val count: Int, val minutes: Int) : Change() {
        override val type = "set_extensions"
        override fun describe() = t("Time-limit extensions: $count × $minutes min per day", "Extensiones de tiempo: $count × $minutes min por día")
        override fun isTightening(s: AppState) = count <= s.extensionsPerDay && minutes <= s.extensionMinutes
        override fun applyTo(s: AppState, now: Long) =
            s.copy(extensionsPerDay = count.coerceIn(0, 10), extensionMinutes = minutes.coerceIn(1, 60))
        override fun writeFields(o: JSONObject) { o.put("count", count).put("minutes", minutes) }
    }

    /** Sets the fixed lock end (trusted time). Later = tightening, earlier/none = loosening. */
    data class SetLock(val untilTrusted: Long) : Change() {
        override val type = "set_lock"
        override fun describe() = if (untilTrusted == 0L) t("Remove fixed lock period", "Quitar periodo de bloqueo fijo") else t("Set fixed lock period", "Fijar periodo de bloqueo")
        override fun isTightening(s: AppState) = untilTrusted >= s.lockUntil
        override fun applyTo(s: AppState, now: Long) = s.copy(lockUntil = untilTrusted)
        override fun writeFields(o: JSONObject) { o.put("until", untilTrusted) }
    }

    data class SetFeature(val feature: Feature, val enabled: Boolean) : Change() {
        override val type = "set_feature"
        override fun describe() = "${if (enabled) t("Turn on", "Activar") else t("Turn off", "Desactivar")}: ${feature.label}"
        override fun isTightening(s: AppState) = enabled || feature !in s.features
        override fun applyTo(s: AppState, now: Long) =
            s.copy(features = if (enabled) s.features + feature else s.features - feature)
        override fun writeFields(o: JSONObject) { o.put("feature", feature.name).put("enabled", enabled) }
    }

    /**
     * Private DNS host to force (Device Owner). Setting one where there was none
     * is tightening; changing or removing it is loosening (waits the delay).
     */
    data class SetPrivateDnsHost(val host: String?) : Change() {
        override val type = "set_dns"
        override fun describe() = t("Private DNS: ", "DNS privado: ") + (host ?: t("not managed", "sin gestionar"))
        override fun isTightening(s: AppState) = host == s.privateDnsHost || (s.privateDnsHost == null && host != null)
        override fun applyTo(s: AppState, now: Long) = s.copy(privateDnsHost = host?.trim()?.lowercase()?.ifBlank { null })
        override fun writeFields(o: JSONObject) { o.put("host", host ?: JSONObject.NULL) }
    }

    // -------------------------------------------------------- banking pause --
    /**
     * Banking pause on/off (see enforcement/BankPause.kt). Turning it ON lets
     * you pause the blocker service, so it is loosening (waits the delay);
     * turning it OFF is tightening (instant).
     */
    data class SetBankPause(val enabled: Boolean) : Change() {
        override val type = "set_bank_pause"
        override fun describe() =
            if (enabled) t("Turn on banking pause", "Activar pausa para bancos") else t("Turn off banking pause", "Desactivar pausa para bancos")
        override fun isTightening(s: AppState) = !enabled || s.bankPauseEnabled
        override fun applyTo(s: AppState, now: Long) = s.copy(bankPauseEnabled = enabled)
        override fun writeFields(o: JSONObject) { o.put("enabled", enabled) }
    }

    /** Adds a bank app the pause may open. Loosening (waits the delay). */
    data class AddBankApp(val pkg: String, val label: String) : Change() {
        override val type = "add_bank_app"
        override fun describe() = t("Add bank app: $label", "Añadir app de banco: $label")
        override fun isTightening(s: AppState) = pkg in s.bankApps
        override fun applyTo(s: AppState, now: Long) = s.copy(bankApps = (s.bankApps + pkg).distinct())
        override fun writeFields(o: JSONObject) { o.put("pkg", pkg).put("label", label) }
    }

    /**
     * Banking pause length, 2–4 minutes (the optional 2 extra minutes stay).
     * Shorter = tightening (instant); longer = loosening (waits the delay).
     */
    data class SetBankPauseMinutes(val minutes: Int) : Change() {
        override val type = "set_bank_pause_minutes"
        override fun describe() = t("Banking pause length: $minutes minutes", "Duración de la pausa para bancos: $minutes minutos")
        override fun isTightening(s: AppState) = minutes <= s.bankPauseMinutes
        override fun applyTo(s: AppState, now: Long) = s.copy(bankPauseMinutes = minutes.coerceIn(2, 4))
        override fun writeFields(o: JSONObject) { o.put("minutes", minutes) }
    }

    /** Removes a bank app. Tightening (instant). */
    data class RemoveBankApp(val pkg: String, val label: String) : Change() {
        override val type = "remove_bank_app"
        override fun describe() = t("Remove bank app: $label", "Quitar app de banco: $label")
        override fun isTightening(s: AppState) = true
        override fun applyTo(s: AppState, now: Long) = s.copy(bankApps = s.bankApps - pkg)
        override fun writeFields(o: JSONObject) { o.put("pkg", pkg).put("label", label) }
    }

    /** Opens a window during which new apps may be installed. */
    data class OpenInstallWindow(val minutes: Int) : Change() {
        override val type = "install_window"
        override fun describe() = t("Open a $minutes-minute install window", "Abrir una ventana de instalación de $minutes minutos")
        override fun isTightening(s: AppState) = Feature.BLOCK_INSTALLS !in s.features
        override fun applyTo(s: AppState, now: Long) = s.copy(installWindowUntil = now + minutes * 60_000L)
        override fun writeFields(o: JSONObject) { o.put("minutes", minutes) }
    }

    /** Turns all protection off (the Device Owner / admin can then be released). */
    data object DisableProtection : Change() {
        override val type = "disable_protection"
        override fun describe() = t("Turn protection OFF", "DESACTIVAR la protección")
        override fun isTightening(s: AppState) = !s.protectionActive
        override fun applyTo(s: AppState, now: Long) = s.copy(protectionActive = false, pending = emptyList())
    }

    companion object {
        const val MAX_DELAY_HOURS = 24 * 30

        /** Rebuilds a saved change. Unknown types return null and are dropped. */
        fun fromJson(o: JSONObject): Change? = when (o.optString("type")) {
            "add_keyword" -> AddKeyword(o.getString("word"))
            "remove_keyword" -> RemoveKeyword(o.getString("word"))
            "add_domain" -> AddDomain(o.getString("entry"))
            "remove_domain" -> RemoveDomain(o.getString("entry"))
            "set_app_rule" -> SetAppRule(StateJson.ruleFromJson(o.getJSONObject("rule")))
            "remove_app_rule" -> RemoveAppRule(o.getString("pkg"), o.optString("label", o.getString("pkg")))
            "allow_browser" -> AllowBrowser(o.getString("pkg"))
            "disallow_browser" -> DisallowBrowser(o.getString("pkg"))
            "add_screen" -> AddProtectedScreen(StateJson.screenFromJson(o.getJSONObject("screen")))
            "remove_screen" -> RemoveProtectedScreen(o.getString("id"), o.optString("label"))
            "set_delay" -> SetDelayHours(o.getInt("hours"))
            "set_extensions" -> SetExtensions(o.getInt("count"), o.getInt("minutes"))
            "set_lock" -> SetLock(o.getLong("until"))
            "set_feature" -> runCatching { SetFeature(Feature.valueOf(o.getString("feature")), o.getBoolean("enabled")) }.getOrNull()
            // "set_vpn" (removed VPN support) falls through to else and is dropped.
            "set_dns" -> SetPrivateDnsHost(if (o.isNull("host")) null else o.getString("host"))
            "install_window" -> OpenInstallWindow(o.getInt("minutes"))
            "set_bank_pause" -> SetBankPause(o.getBoolean("enabled"))
            "set_bank_pause_minutes" -> SetBankPauseMinutes(o.getInt("minutes"))
            "add_bank_app" -> AddBankApp(o.getString("pkg"), o.optString("label", o.getString("pkg")))
            "remove_bank_app" -> RemoveBankApp(o.getString("pkg"), o.optString("label", o.getString("pkg")))
            "disable_protection" -> DisableProtection
            else -> null
        }
    }
}

/** Outcome of [ChangeGate.submit]. */
sealed class SubmitResult {
    data object Applied : SubmitResult()
    data class Queued(val applyAt: Long) : SubmitResult()
    data class Rejected(val reason: String) : SubmitResult()
}

/** Outcome of checking a PIN or recovery code. */
sealed class SecretResult {
    data object Ok : SecretResult()
    data object Wrong : SecretResult()
    data class TooManyAttempts(val retryAt: Long) : SecretResult()
}

/**
 * Enforces the tightening/loosening rule. All edits to rules must go through
 * [submit]; the UI never edits rules directly.
 */
class ChangeGate(private val store: Store, private val clock: TrustedClock) {

    /** True while a fixed lock period is running. */
    fun lockActive(s: AppState = store.current, now: Long = clock.now()): Boolean =
        s.protectionActive && s.lockUntil > now

    /** Submit an edit. See file header for the decision table. */
    fun submit(change: Change): SubmitResult {
        val now = clock.now()
        var result: SubmitResult = SubmitResult.Applied
        store.update { s ->
            when {
                !s.protectionActive || change.isTightening(s) -> {
                    result = SubmitResult.Applied
                    change.applyTo(s, now)
                }
                lockActive(s, now) -> {
                    result = SubmitResult.Rejected(t("Locked: nothing can be loosened until the lock period ends.", "Bloqueado: no se puede aflojar nada hasta que termine el periodo de bloqueo."))
                    s
                }
                else -> {
                    val applyAt = now + s.delayHours * HOUR_MS
                    result = SubmitResult.Queued(applyAt)
                    s.copy(pending = s.pending + PendingChange(UUID.randomUUID().toString(), change, now, applyAt))
                }
            }
        }
        return result
    }

    /**
     * Applies every pending change whose delay has passed. Called often by
     * the enforcer. Changes still waiting stay queued.
     * A change that has become "tightening" in the meantime simply applies.
     * @return true if anything was applied.
     */
    fun processDue(): Boolean {
        val now = clock.now()
        var applied = false
        store.update { s ->
            val due = s.pending.filter { it.applyAt <= now }
            if (due.isEmpty() || (lockActive(s, now) && due.none { it.change.isTightening(s) })) return@update s
            var next = s.copy(pending = s.pending - due.toSet())
            for (p in due.sortedBy { it.applyAt }) {
                // A lock set AFTER this change was requested still freezes it.
                if (lockActive(next, now) && !p.change.isTightening(next)) {
                    next = next.copy(pending = next.pending + p)
                    continue
                }
                next = p.change.applyTo(next, now)
                applied = true
            }
            next
        }
        return applied
    }

    /** Cancelling a pending loosening change is itself tightening, so it's instant. */
    fun cancel(id: String) {
        store.update { s -> s.copy(pending = s.pending.filterNot { it.id == id }) }
    }

    // ------------------------------------------------------ PIN / recovery --

    /**
     * Checks the settings PIN. After 5 wrong tries, waits grow
     * (1 min, 2, 4, … up to 1 hour) to stop guessing.
     */
    fun checkPin(pin: String): SecretResult = checkSecret { s -> s.pinHash?.let { Crypto.verify(pin, it) } ?: true }

    /**
     * Emergency recovery code. If correct, it IMMEDIATELY:
     *   * ends the fixed lock period,
     *   * clears all pending changes,
     *   * turns protection off,
     *   * and is used up (a new code is made when you turn protection back on).
     */
    fun useRecoveryCode(code: String): SecretResult {
        val normalized = Crypto.normalizeRecoveryCode(code)
        val r = checkSecret { s -> s.recoveryHash?.let { Crypto.verify(normalized, it) } ?: false }
        if (r == SecretResult.Ok) {
            store.update { it.copy(protectionActive = false, lockUntil = 0L, pending = emptyList(), recoveryHash = null) }
        }
        return r
    }

    /**
     * This phone's developer-unlock request code (security/DevUnlock.kt).
     * Made once and kept until used, so a request sent by email stays valid.
     */
    fun devRequest(): String {
        store.current.devRequest?.let { return it }
        val fresh = DevUnlock.newRequest()
        store.update { if (it.devRequest == null) it.copy(devRequest = fresh) else it }
        return store.current.devRequest ?: fresh
    }

    /**
     * Developer unlock code. If it is the developer's signature for this
     * phone's request, it does exactly what the recovery code does (protection
     * off, lock ended, pending cleared) and a NEW request code is made, so the
     * same unlock code never works twice. Wrong codes are throttled like PINs.
     */
    fun useDeveloperCode(appId: String, code: String): SecretResult {
        val request = devRequest()
        val r = checkSecret { DevUnlock.verify(appId, request, code) }
        if (r == SecretResult.Ok) {
            store.update {
                it.copy(protectionActive = false, lockUntil = 0L, pending = emptyList(), recoveryHash = null, devRequest = DevUnlock.newRequest())
            }
        }
        return r
    }

    /** Shared brute-force throttle for the PIN and the recovery code. */
    private fun checkSecret(verify: (AppState) -> Boolean): SecretResult {
        val now = clock.now()
        val s = store.current
        if (now < s.secretBackoffUntil) return SecretResult.TooManyAttempts(s.secretBackoffUntil)
        return if (verify(s)) {
            store.update { it.copy(failedSecretAttempts = 0, secretBackoffUntil = 0L) }
            SecretResult.Ok
        } else {
            store.update {
                val fails = it.failedSecretAttempts + 1
                val wait = if (fails < 5) 0L else (60_000L shl (fails - 5).coerceAtMost(6)).coerceAtMost(HOUR_MS)
                it.copy(failedSecretAttempts = fails, secretBackoffUntil = if (wait > 0) now + wait else 0L)
            }
            SecretResult.Wrong
        }
    }

    companion object {
        const val HOUR_MS = 3_600_000L
    }
}
