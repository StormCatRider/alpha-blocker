// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Theme.kt — colors and theme
 *
 * Brand colors: blue #273DCC (main) and orange #FF792E (accent).
 * Contrast rules (WCAG):
 *   * white text on blue #273DCC  ≈ 8:1   -> OK
 *   * DARK text on orange #FF792E ≈ 8:1   -> OK
 *   * white text on orange        ≈ 2.6:1 -> NOT used anywhere
 * In dark mode, plain blue text on a dark background would be hard to read,
 * so text-colored elements use a light tint of the brand blue (#B9C3FF),
 * while large blue surfaces (headers, block screen) keep #273DCC.
 * The theme follows the system light/dark setting.
 * =============================================================================
 */
package com.alphablocker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF273DCC)
val BrandOrange = Color(0xFFFF792E)
val OnOrange = Color(0xFF1A1A1A)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF0B1660),
    secondary = BrandOrange,
    onSecondary = OnOrange,
    secondaryContainer = Color(0xFFFFDCC9),
    onSecondaryContainer = Color(0xFF3A1600),
    tertiary = BrandOrange,
    onTertiary = OnOrange,
    background = Color(0xFFFBFBFF),
    surface = Color(0xFFFBFBFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF0B1660),
    primaryContainer = BrandBlue,
    onPrimaryContainer = Color.White,
    secondary = BrandOrange,
    onSecondary = OnOrange,
    secondaryContainer = Color(0xFF7A3000),
    onSecondaryContainer = Color(0xFFFFDCC9),
    tertiary = BrandOrange,
    onTertiary = OnOrange,
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
)

@Composable
fun AlphaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
