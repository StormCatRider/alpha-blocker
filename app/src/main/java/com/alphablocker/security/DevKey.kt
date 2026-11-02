// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * DevKey.kt — the developer's PUBLIC key for developer unlock codes
 *
 * Empty = developer unlock codes are switched off in this build (the option
 * is hidden). To switch them on (see tools/dev_unlock.py and README):
 *   1. On YOUR computer:  python tools/dev_unlock.py keygen
 *      It creates alphablocker_dev_private.pem (keep it secret and backed up;
 *      NEVER commit it) and prints a line like  PUBLIC_KEY = "MFkw..."
 *   2. Paste that value below and rebuild.
 * A public key can only CHECK codes, never make them, so it is safe to
 * publish in the source code.
 * =============================================================================
 */
package com.alphablocker.security

object DevKey {
    const val PUBLIC_KEY = ""
}
