// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * DevKey.kt — the developer's PUBLIC key for developer unlock codes
 *
 * Empty = developer unlock codes are switched off in this build (the option
 * is hidden). The developer pastes their public key here before building.
 * A public key can only CHECK codes, never make them, so it is safe to
 * publish in the source code. The matching private key never leaves the
 * developer's computer.
 * =============================================================================
 */
package com.alphablocker.security

object DevKey {
    const val PUBLIC_KEY = ""
}
