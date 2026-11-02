// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Graph.kt — creates the app's shared objects once and hands them out
 *
 * One instance of each, shared by the UI, the blocker service and receivers:
 *   store    – saved settings              (data/Store.kt)
 *   clock    – tamper-resistant time        (core/TrustedClock.kt)
 *   gate     – tightening/loosening rules   (core/Changes.kt)
 *   usage    – today's minutes per app      (core/UsageTracker.kt)
 *   enforcer – applies settings to the phone (enforcement/Enforcer.kt)
 * =============================================================================
 */
package com.alphablocker

import android.content.Context
import com.alphablocker.core.Change
import com.alphablocker.core.ChangeGate
import com.alphablocker.core.SubmitResult
import com.alphablocker.core.TrustedClock
import com.alphablocker.core.UsageTracker
import com.alphablocker.data.Store
import com.alphablocker.enforcement.Enforcer
import com.alphablocker.enforcement.PolicyManager

object Graph {
    lateinit var store: Store; private set
    lateinit var clock: TrustedClock; private set
    lateinit var gate: ChangeGate; private set
    lateinit var usage: UsageTracker; private set
    lateinit var enforcer: Enforcer; private set

    /** Called once from AlphaApp.onCreate. */
    fun init(context: Context) {
        val app = context.applicationContext
        store = Store(app)
        clock = TrustedClock(app)
        gate = ChangeGate(store, clock)
        usage = UsageTracker(app)
        enforcer = Enforcer(app, store, clock, gate, PolicyManager(app, store))
    }

    /** Submits a change through the gate, then re-syncs the phone. */
    fun submit(change: Change): SubmitResult {
        val result = gate.submit(change)
        enforcer.syncAsync()
        return result
    }
}
