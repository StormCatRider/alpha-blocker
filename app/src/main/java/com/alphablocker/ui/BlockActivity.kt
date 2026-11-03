// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * BlockActivity.kt — the full-screen "blocked" page
 *
 * Shows a Bible verse (purity or diligence, depending on what was blocked),
 * the reason, a "Go to home screen" button and, for time limits, an extension
 * button if any extensions are left today.
 *
 * A 3-second countdown runs first: the Exit button (and the extension
 * button) only unlock after it, giving you a moment to read the verse.
 * Back button = nothing during the countdown, then go home
 * (it never returns to the blocked app).
 * =============================================================================
 */
package com.alphablocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.res.painterResource
import com.alphablocker.R
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphablocker.Graph
import com.alphablocker.core.AppCatalog
import com.alphablocker.core.Verses
import com.alphablocker.core.t
import com.alphablocker.data.VerseTheme

class BlockActivity : ComponentActivity() {

    /** Seconds until "Exit" unlocks. Restarts at 3 for every new block. */
    private val secondsLeft = mutableIntStateOf(COUNTDOWN_SECONDS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val reason = intent.getStringExtra(EXTRA_REASON) ?: t("Blocked.", "Bloqueado.")
        val theme = runCatching { VerseTheme.valueOf(intent.getStringExtra(EXTRA_THEME)!!) }.getOrDefault(VerseTheme.PURITY)
        val pkg = intent.getStringExtra(EXTRA_PKG)
        val canExtend = intent.getBooleanExtra(EXTRA_CAN_EXTEND, false)

        // Back does nothing during the countdown, then acts like "Exit".
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (secondsLeft.intValue == 0) goHome()
            }
        })

        setContent {
            AlphaTheme {
                val state = Graph.store.current
                // A random verse each time this screen opens (built-in + your own, matching the block type).
                val verse = remember { Verses.random(theme, state.customVerses) }
                val extensionsLeft = (state.extensionsPerDay - Graph.usage.extensionsUsed()).coerceAtLeast(0)
                val seconds = secondsLeft.intValue

                // 3-second countdown: a short pause to read the verse before you can leave.
                LaunchedEffect(Unit) {
                    while (secondsLeft.intValue > 0) {
                        delay(1_000)
                        secondsLeft.intValue -= 1
                    }
                }

                Box(Modifier.fillMaxSize().background(BrandBlue).systemBarsPadding()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(painterResource(R.drawable.ic_logo), contentDescription = "Alpha Blocker logo", modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        // Orange accent bar
                        Box(Modifier.width(64.dp).height(6.dp).background(BrandOrange, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "“${verse.text}”",
                            color = Color.White,
                            fontSize = 22.sp,
                            lineHeight = 30.sp,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            // Built-in verses show their translation (Douay-Rheims / Torres Amat); your own show just the reference.
                            Verses.translationOf(verse)?.let { "— ${verse.reference} ($it)" } ?: "— ${verse.reference}",
                            color = BrandOrange,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(32.dp))
                        Text(reason, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(32.dp))
                        // Disabled and showing the countdown until 3 seconds have passed.
                        AccentButton(
                            if (seconds > 0) t("Exit in $seconds…", "Salir en $seconds…") else t("Exit", "Salir"),
                            Modifier.fillMaxWidth(),
                            enabled = seconds == 0,
                        ) { goHome() }

                        if (seconds == 0 && canExtend && pkg != null && extensionsLeft > 0) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { extend(pkg) }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    t(
                                        "Use an extension: ${state.extensionMinutes} min ($extensionsLeft left today)",
                                        "Usar una extensión: ${state.extensionMinutes} min (quedan $extensionsLeft hoy)",
                                    ),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Grants an extension and reopens the app. */
    private fun extend(pkg: String) {
        val s = Graph.store.current
        if (Graph.usage.grantExtension(pkg, s.extensionMinutes, s.extensionsPerDay)) {
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        }
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Close the blocked app once more now that it's fully in the background,
        // so opening it again starts fresh (see BlockerService.closeApp).
        val pkg = intent.getStringExtra(EXTRA_PKG)
        if (pkg != null && pkg != packageName && pkg !in AppCatalog.criticalPackages(this)) {
            val am = getSystemService(android.app.ActivityManager::class.java)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { am?.killBackgroundProcesses(pkg) } }, 500L)
        }
        finish()
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_THEME = "theme"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_CAN_EXTEND = "canExtend"
        private const val COUNTDOWN_SECONDS = 3

        fun launch(context: Context, reason: String, theme: VerseTheme, pkg: String?, canExtend: Boolean) {
            context.startActivity(
                Intent(context, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_REASON, reason)
                    .putExtra(EXTRA_THEME, theme.name)
                    .putExtra(EXTRA_PKG, pkg)
                    .putExtra(EXTRA_CAN_EXTEND, canExtend),
            )
        }
    }
}
