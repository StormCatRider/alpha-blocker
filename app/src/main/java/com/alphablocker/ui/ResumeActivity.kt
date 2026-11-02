// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * ResumeActivity.kt — the banking-pause popups (enforcement/BankPause.kt)
 *
 * Two versions of one small popup, shown over the bank app:
 *   * after your pause length (2–4 min, Protect tab):
 *                    "Turn protection back on" or "Give me 2 more minutes".
 *                    Closing it any other way also means 2 more minutes;
 *                    protection still turns itself on 2 minutes later.
 *   * 2 minutes later: "Protection is back on" with an OK button
 *                    (opened with [backOnIntent]).
 *
 * Translucent: the bank app stays visible behind it. Not exported, excluded
 * from Recents, own task, so it can't be used to reach anything else.
 * =============================================================================
 */
package com.alphablocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alphablocker.Graph
import com.alphablocker.R
import com.alphablocker.core.t
import com.alphablocker.enforcement.BankPause
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay

class ResumeActivity : ComponentActivity() {

    /** True for the "Protection is back on" version. */
    private var backOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        show(intent)
    }

    /** singleTask: a second popup request reuses this screen with the new version. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent) {
        backOn = intent.getBooleanExtra(EXTRA_BACK_ON, false)
        // The 4-minute question when the pause already ended: nothing to ask.
        if (!backOn && BankPause.msLeft() == 0L) { finish(); return }
        current = WeakReference(this)
        setContent { AlphaTheme { if (backOn) BackOnCard() else QuestionCard() } }
    }

    @Composable
    private fun QuestionCard() {
        var left by remember { mutableLongStateOf(BankPause.msLeft()) }
        LaunchedEffect(Unit) {
            while (left > 0) {
                delay(1_000)
                left = BankPause.msLeft()
            }
            finish() // time ran out; the "back on" popup takes over
        }
        Card(
            title = Graph.store.current.bankPauseMinutes.let { m -> t("Your $m minutes are up", "Se acabaron tus $m minutos") },
            text = t(
                "Turn protection back on now, or take 2 more minutes. It turns on by itself in ${formatSeconds(left)}.",
                "Vuelve a activar la protección ahora o toma 2 minutos más. Se activará sola en ${formatSeconds(left)}.",
            ),
        ) {
            AccentButton(t("Turn protection back on", "Activar protección"), Modifier.fillMaxWidth()) {
                Graph.enforcer.endBankPause()
                finish()
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { BankPause.moreTime(this@ResumeActivity) }, modifier = Modifier.fillMaxWidth()) {
                Text(t("Give me 2 more minutes", "Dame 2 minutos más"))
            }
        }
    }

    @Composable
    private fun BackOnCard() {
        Card(
            title = t("Protection is back on", "La protección volvió a activarse"),
            text = t(
                "The banking pause is over. Alpha Blocker is protecting this phone again.",
                "Terminó la pausa para bancos. Alpha Blocker vuelve a proteger este teléfono.",
            ),
        ) {
            AccentButton(t("OK", "Aceptar"), Modifier.fillMaxWidth()) { finish() }
        }
    }

    /** Dimmed backdrop + centered card with the logo, a title, a line of text and buttons. */
    @Composable
    private fun Card(title: String, text: String, buttons: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp, modifier = Modifier.padding(24.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = "Alpha Blocker logo", modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    buttons()
                }
            }
        }
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_BACK_ON = "backOn"

        /** The open popup, if any. Weak, so it never keeps a closed screen in memory. */
        @Volatile private var current: WeakReference<ResumeActivity>? = null

        /** Intent for the "Protection is back on" popup. */
        fun backOnIntent(context: Context): Intent =
            Intent(context, ResumeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_BACK_ON, true)

        /**
         * Closes the 4-minute question when it's no longer needed (pause ended,
         * or "2 more minutes" tapped in the notification). Never closes the
         * "back on" popup.
         */
        fun closeIfOpen() {
            current?.get()?.let { a -> if (!a.backOn) a.runOnUiThread { a.finish() } }
        }

        /** 95_000 ms -> "1:35" */
        private fun formatSeconds(ms: Long): String {
            val s = (ms + 999) / 1000
            return "%d:%02d".format(s / 60, s % 60)
        }
    }
}
