// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * ContentMatcher.kt — decides if a URL or typed text is blocked
 *
 * Pure logic, no Android UI. Input comes from the blocker service:
 *   * the browser's address bar text, and
 *   * text typed into a search/address field.
 * Nothing is stored; the text is checked and forgotten.
 *
 * NORMALIZATION (so simple disguises don't work)
 *   lowercase -> remove accents (á→a, ñ→n) -> common look-alike swaps (0→o, 1→i, 3→e, 4→a, 5→s, 7→t, @→a,
 *   $→s) -> every other non-letter/digit becomes a space.
 *   "P0rn-Site" becomes "porn site".
 *   Entries that contain digits themselves ("r18", "r34", "f95zone") are
 *   matched WITHOUT the swaps, so "r34" doesn't also block "RE4" (a game).
 *
 * WORD MATCHING
 *   * Multi-word entries ("sex video") match the phrase.
 *   * Single words match a whole word, or the START of a word when the entry
 *     is 4+ letters ("porn" matches "porno", "pornography").
 *   * Short words (1–3 letters) only match exactly, to avoid false positives
 *     inside innocent words.
 *   * In URLs, 4+ letter words also match ANYWHERE in the address with
 *     punctuation removed, because site names glue words together.
 *
 * PAGE MATCHING ("example.com" or "example.com/some/path")
 *   * Domain matches itself and all its subdomains (m.example.com).
 *   * With a path, only addresses starting with that path match.
 * =============================================================================
 */
package com.alphablocker.core

import android.net.Uri
import java.net.URLDecoder
import java.text.Normalizer

class ContentMatcher(keywords: List<String>, domains: List<String>) {

    /** One blocked page entry, split into host and optional path prefix. */
    private data class PageRule(val host: String, val pathPrefix: String)

    /** Entries without digits: matched against look-alike-normalized text. */
    private val lookPhrases: List<String> = keywords.filterNot { it.any(Char::isDigit) }
        .map { normalize(it) }.filter { it.isNotBlank() }.distinct()

    /** Entries with digits: matched against text with digits left as-is. */
    private val plainPhrases: List<String> = keywords.filter { it.any(Char::isDigit) }
        .map { normalize(it, lookalikes = false) }.filter { it.isNotBlank() }.distinct()
    private val pages: List<PageRule> = domains.mapNotNull { entry ->
        val clean = cleanDomainEntry(entry)
        if (clean.isBlank()) return@mapNotNull null
        val slash = clean.indexOf('/')
        if (slash < 0) PageRule(clean, "") else PageRule(clean.substring(0, slash), clean.substring(slash))
    }

    /** True if the address [raw] (as shown in a browser's address bar) is blocked. */
    fun isBlockedUrl(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val uri = parseUrl(raw)
        if (uri != null) {
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: ""
            val path = uri.path ?: ""
            if (pages.any { (host == it.host || host.endsWith("." + it.host)) && path.startsWith(it.pathPrefix) }) {
                return true
            }
        }
        // Decode %20 etc. so search queries inside the URL are readable.
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        if (isBlockedText(decoded)) return true
        return compactHit(normalize(decoded).replace(" ", ""), lookPhrases) ||
            compactHit(normalize(decoded, lookalikes = false).replace(" ", ""), plainPhrases)
    }

    /** True if free text (a typed search) contains a blocked word or phrase. */
    fun isBlockedText(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return wordHit(normalize(raw), lookPhrases) || wordHit(normalize(raw, lookalikes = false), plainPhrases)
    }

    /** Word/phrase rules from the file header, on already-normalized [text]. */
    private fun wordHit(text: String, phrases: List<String>): Boolean {
        if (text.isBlank() || phrases.isEmpty()) return false
        val padded = " $text "
        val tokens = text.split(' ')
        return phrases.any { p ->
            if (p.contains(' ')) padded.contains(" $p ")
            else tokens.any { t -> t == p || (p.length >= 4 && t.startsWith(p)) }
        }
    }

    /** URL rule: single 4+ character entries anywhere in the squashed address. */
    private fun compactHit(compact: String, phrases: List<String>): Boolean =
        phrases.any { !it.contains(' ') && it.length >= 4 && compact.contains(it) }

    companion object {
        private val LOOKALIKES = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's')

        /** See file header: lowercase, look-alike swaps, punctuation to spaces. */
        fun normalize(s: String, lookalikes: Boolean = true): String {
            val sb = StringBuilder(s.length)
            for (c in stripAccents(s.lowercase())) {
                val mapped = if (lookalikes) LOOKALIKES[c] ?: c else c
                sb.append(if (mapped.isLetterOrDigit()) mapped else ' ')
            }
            return sb.toString().trim().replace(SPACES, " ")
        }

        /** "explícito" -> "explicito", "español" -> "espanol" (so accents never matter). */
        private fun stripAccents(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        /** Compiled once (normalize runs on every checked text). */
        private val SPACES = Regex(" +")

        /** "HTTPS://www.Example.com/Path/" -> "example.com/path" */
        fun cleanDomainEntry(entry: String): String =
            entry.trim().lowercase()
                .removePrefix("https://").removePrefix("http://")
                .removePrefix("www.")
                .trimEnd('/')

        /** Parses address-bar text (often missing "https://"). Null if it isn't a URL. */
        fun parseUrl(raw: String): Uri? {
            val t = raw.trim()
            if (t.isEmpty() || t.contains(' ') || !t.contains('.')) return null
            val withScheme = if (t.contains("://")) t else "https://$t"
            return runCatching { Uri.parse(withScheme) }.getOrNull()?.takeIf { it.host != null }
        }
    }
}
