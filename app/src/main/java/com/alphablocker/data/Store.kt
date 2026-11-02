// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Store.kt — loads and saves AppState
 *
 * Where: <app private storage>/files/state.json
 *   Only this app can read it. It is excluded from cloud backup
 *   (allowBackup=false in AndroidManifest.xml).
 *
 * How: AtomicFile writes a new copy and swaps it in, so a crash or power loss
 *      mid-save can't leave a half-written (corrupted) settings file.
 *
 * Every change goes through [update], which is synchronized, so the blocker
 * service and the UI can never overwrite each other's edits.
 * =============================================================================
 */
package com.alphablocker.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

class Store(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "state.json"))
    private val _state = MutableStateFlow(load())

    /** Observable state: the UI redraws automatically when it changes. */
    val state: StateFlow<AppState> = _state

    /** Current state snapshot. */
    val current: AppState get() = _state.value

    /**
     * Applies [transform] to the current state, saves the result, then
     * publishes it. Synchronized: one update at a time.
     */
    @Synchronized
    fun update(transform: (AppState) -> AppState): AppState {
        val next = transform(_state.value)
        if (next != _state.value) {
            save(next)
            _state.value = next
        }
        return next
    }

    /** Reads state.json. A missing or unreadable file gives fresh defaults. */
    private fun load(): AppState = runCatching {
        val text = String(file.readFully(), Charsets.UTF_8)
        StateJson.fromJson(JSONObject(text))
    }.getOrElse { AppState() }

    private fun save(s: AppState) {
        val out = file.startWrite()
        try {
            out.write(StateJson.toJson(s).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
