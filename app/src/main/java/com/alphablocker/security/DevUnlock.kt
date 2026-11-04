// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * DevUnlock.kt — developer unlock codes (when the paper recovery code is lost)
 *
 * HOW IT WORKS (challenge–response, like signing a document):
 *   1. The phone shows a REQUEST code: 16 random characters made on that phone
 *      (kept until used, so a request sent by email stays valid).
 *   2. The person sends it to the developer.
 *   3. The developer signs, with their PRIVATE key (offline, never in the app),
 *      "alphablocker-dev-unlock-v1:<app ID>:<request>"; that signature is the UNLOCK
 *      code (written in letters and digits).
 *   4. The phone checks the signature with the PUBLIC key in DevKey.kt.
 *      Correct = same as the recovery code: protection off, lock ended,
 *      pending changes cleared. Then a new request code is made, so each
 *      unlock code works ONCE, on ONE phone.
 *
 * WHY NOT FIXED "MASTER CODES": the app is open source. A fixed code (or its
 * hash) would be in every copy, would unlock every phone, and one leak would
 * break everyone's protection. Here the app only holds a public key, which
 * can check codes but never make them. Only the private key (kept by the
 * developer, never in the app) can make codes.
 *
 * Algorithm: ECDSA on curve P-256 with SHA-256 (built into Android; no
 * libraries). The unlock code is the 64-byte signature (r || s) in base32:
 * 103 characters, case-insensitive; spaces and dashes are ignored, so it can
 * be pasted from an email as is.
 *
 * No network: the app never contacts anyone. Wrong codes share the PIN's
 * attempt throttling (ChangeGate.checkSecret).
 * =============================================================================
 */
package com.alphablocker.security

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object DevUnlock {

    private const val CONTEXT = "alphablocker-dev-unlock-v1"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" // RFC 4648 base32

    /** True if this build has a developer public key (DevKey.kt). Otherwise the option is hidden. */
    val available: Boolean get() = DevKey.PUBLIC_KEY.isNotBlank()

    /** New request code: 10 random bytes = 16 base32 characters. */
    fun newRequest(): String {
        val bytes = ByteArray(10).also { SecureRandom().nextBytes(it) }
        return base32(bytes)
    }

    /** "ABCDEFGHIJKLMNOP" -> "ABCD-EFGH-IJKL-MNOP" (for display). */
    fun format(request: String): String = request.chunked(4).joinToString("-")

    /** Removes spaces/dashes and upper-cases, so pasted codes work. */
    fun normalize(input: String): String = input.uppercase().filter { it in ALPHABET }

    /** Text the developer signs. Must match the developer's signing tool exactly. */
    fun message(appId: String, request: String): ByteArray =
        "$CONTEXT:$appId:${normalize(request)}".toByteArray(Charsets.UTF_8)

    /**
     * True if [unlockCode] is the developer's signature for this phone's
     * [request]. Any malformed input simply returns false.
     */
    fun verify(appId: String, request: String, unlockCode: String): Boolean = runCatching {
        if (!available) return false
        val raw = base32Decode(normalize(unlockCode)) ?: return false
        if (raw.size != 64) return false
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(DevKey.PUBLIC_KEY.trim())))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(message(appId, request))
            verify(rawToDer(raw))
        }
    }.getOrDefault(false)

    // ---- helpers ------------------------------------------------------------

    /** Java expects the signature in DER form: SEQUENCE { INTEGER r, INTEGER s }. */
    private fun rawToDer(raw: ByteArray): ByteArray {
        fun derInt(part: ByteArray): ByteArray {
            val v = BigInteger(1, part).toByteArray() // adds a leading 0 if the top bit is set
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val body = derInt(raw.copyOfRange(0, 32)) + derInt(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun base32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun base32Decode(text: String): ByteArray? {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
