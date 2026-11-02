// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Crypto.kt — PIN and recovery-code hashing
 *
 * SECURITY AUDIT NOTES
 *  * Secrets are NEVER stored in plain text. Only a salted hash is saved:
 *      "pbkdf2$<iterations>$<salt base64>$<hash base64>"
 *  * Algorithm: PBKDF2-HMAC-SHA256 (built into Android, no extra library),
 *    120,000 iterations, random 16-byte salt per secret, 32-byte output.
 *    The salt stops precomputed-table attacks; iterations slow down guessing.
 *  * Comparison uses MessageDigest.isEqual (constant time), so timing can't
 *    reveal how much of a guess was right.
 *  * Brute-force limits (growing waits after 5 wrong tries) are in
 *    ChangeGate.checkSecret.
 *
 * RECOVERY CODE
 *  * 20 random characters from SecureRandom, shown as XXXX-XXXX-XXXX-XXXX-XXXX.
 *  * Alphabet skips look-alike characters (0/O, 1/I/L) to avoid copy mistakes.
 *  * 31^20 ≈ 6.7 × 10^29 possibilities: unguessable.
 *  * Shown on screen exactly once; afterwards only its hash exists.
 * =============================================================================
 */
package com.alphablocker.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Crypto {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    /** Hashes [secret] with a fresh random salt. Returns the storable string. */
    fun hash(secret: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val digest = pbkdf2(secret, salt, ITERATIONS)
        return "pbkdf2$$ITERATIONS$${b64(salt)}$${b64(digest)}"
    }

    /** True if [secret] matches a string made by [hash]. */
    fun verify(secret: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = Base64.decode(parts[2], Base64.NO_WRAP)
        val expected = Base64.decode(parts[3], Base64.NO_WRAP)
        val actual = pbkdf2(secret, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    /** New random recovery code, formatted in groups of 4 for writing down. */
    fun newRecoveryCode(): String =
        (1..20).map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .chunked(4).joinToString("-") { it.joinToString("") }

    /**
     * Normalizes typed input so "abcd efgh-…" matches "ABCD-EFGH-…".
     * The HASH is always made from the normalized form.
     */
    fun normalizeRecoveryCode(input: String): String =
        input.uppercase().filter { it in ALPHABET }

    private fun pbkdf2(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword() // wipe the secret from this object's memory
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
