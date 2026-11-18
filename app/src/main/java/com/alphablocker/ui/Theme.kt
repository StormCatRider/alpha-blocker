// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Theme.kt — color palettes, type scale and shapes
 *
 * Design system (from the ui-ux-pro-max design pass): minimal Swiss style,
 * a calm cool-neutral ground, one brand color for headers and the block
 * screen, one warm accent for the main action. Light AND dark mode follow the
 * phone. Every color used by screens comes from here (Material colorScheme or
 * [LocalBrand]); screens never hard-code hex values.
 *
 * PALETTES (Protect tab › Color theme; a display choice, so it's instant):
 *   Alpha    blue #273DCC + orange #FF792E   (default, the app's brand)
 *   Sanctuary purple #6D28D9 + gold #F2B01E
 *   Forest   green #166534 + amber #F59E0B
 *   Ocean    teal #155E75 + coral #FF8A65
 *
 * Contrast (WCAG, checked for every palette):
 *   * white text on the brand color          >= 7:1
 *   * dark text on the accent (buttons)      >= 6.7:1
 *   * accent button edge on the brand color  >= 3:1 (non-text)
 *   * "secondary" text: a DARK shade of the accent in light mode (>= 5.8:1 on
 *     white) and a light tint in dark mode (>= 9.5:1), so the accent is never
 *     used as low-contrast text.
 * The launcher icon always stays Alpha blue/orange.
 * =============================================================================
 */
package com.alphablocker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphablocker.Graph
import com.alphablocker.core.t

/** Colors outside Material's scheme: the brand band (header, block screen) and the accent button. */
@Immutable
data class BrandColors(
    val brand: Color,        // header bar, block screen background
    val onBrand: Color,      // text on brand
    val onBrandMuted: Color, // secondary text on brand (>= 4.5:1)
    val accent: Color,       // main action button, small highlights
    val onAccent: Color,     // text on accent
    val highlight: Color,    // verse reference / small accent text ON the brand color
)

val LocalBrand = staticCompositionLocalOf {
    BrandColors(Color(0xFF273DCC), Color.White, Color(0xFFDCE1FF), Color(0xFFFF792E), Color(0xFF1A1A1A), Color(0xFFFF9A5C))
}

/** One color theme: brand colors plus full light and dark Material schemes. */
enum class Palette(
    val id: String,
    private val en: String,
    private val es: String,
    val brand: BrandColors,
    val light: ColorScheme,
    val dark: ColorScheme,
) {
    ALPHA(
        "alpha", "Alpha (blue and orange)", "Alpha (azul y naranja)",
        BrandColors(Color(0xFF273DCC), Color.White, Color(0xFFDCE1FF), Color(0xFFFF792E), Color(0xFF1A1A1A), Color(0xFFFFBE94)),
        scheme(false, 0xFF273DCC, 0xFFFFFFFF, 0xFFDDE1FF, 0xFF0B1660, 0xFFA33E00, 0xFFFFDCC9, 0xFF3A1600, 0xFFF7F8FC, 0xFFFFFFFF, 0xFFEEF0F8, 0xFF141A3D, 0xFF4B5275, 0xFFC5CAE0),
        scheme(true, 0xFFB9C3FF, 0xFF0B1660, 0xFF2A3A9E, 0xFFDDE1FF, 0xFFFFB68F, 0xFF6E2A00, 0xFFFFDCC9, 0xFF0F1220, 0xFF171B2E, 0xFF20253C, 0xFFE6E8F5, 0xFFB3B8D3, 0xFF3A4060),
    ),
    SANCTUARY(
        "sanctuary", "Sanctuary (purple and gold)", "Santuario (morado y dorado)",
        BrandColors(Color(0xFF6D28D9), Color.White, Color(0xFFEDE3FF), Color(0xFFF2B01E), Color(0xFF1F1500), Color(0xFFFFD36B)),
        scheme(false, 0xFF6D28D9, 0xFFFFFFFF, 0xFFEDE5FF, 0xFF2E0B6B, 0xFF855300, 0xFFFFE7B3, 0xFF2B1A00, 0xFFFAF7FF, 0xFFFFFFFF, 0xFFF2EDFB, 0xFF1D1530, 0xFF544B6B, 0xFFD3C9E8),
        scheme(true, 0xFFD2BBFF, 0xFF2E0B6B, 0xFF4E1FA0, 0xFFEDE5FF, 0xFFF2C46B, 0xFF5C3B00, 0xFFFFE7B3, 0xFF130F1E, 0xFF1C172B, 0xFF261F38, 0xFFEAE4F5, 0xFFBEB5D3, 0xFF3F3556),
    ),
    FOREST(
        "forest", "Forest (green and amber)", "Bosque (verde y ámbar)",
        BrandColors(Color(0xFF166534), Color.White, Color(0xFFD6F2DF), Color(0xFFF59E0B), Color(0xFF1F1300), Color(0xFFFFC56B)),
        scheme(false, 0xFF166534, 0xFFFFFFFF, 0xFFD3F5DE, 0xFF052E16, 0xFF8F5200, 0xFFFFE2B8, 0xFF2B1700, 0xFFF5FAF6, 0xFFFFFFFF, 0xFFEAF2EC, 0xFF132018, 0xFF4A5A4F, 0xFFC4D6C9),
        scheme(true, 0xFF86E0A5, 0xFF00391A, 0xFF1A5C33, 0xFFD3F5DE, 0xFFFFC46B, 0xFF5A3500, 0xFFFFE2B8, 0xFF0E1611, 0xFF16211A, 0xFF1F2C23, 0xFFE3EEE6, 0xFFB2C4B7, 0xFF33443A),
    ),
    OCEAN(
        "ocean", "Ocean (teal and coral)", "Océano (turquesa y coral)",
        BrandColors(Color(0xFF155E75), Color.White, Color(0xFFD3EEF5), Color(0xFFFF8A65), Color(0xFF2A0E05), Color(0xFFFFC7B5)),
        scheme(false, 0xFF155E75, 0xFFFFFFFF, 0xFFCDEFF7, 0xFF04313D, 0xFFB23E1C, 0xFFFFDBD0, 0xFF3B0E00, 0xFFF4FAFC, 0xFFFFFFFF, 0xFFE8F1F4, 0xFF10232A, 0xFF46585E, 0xFFC1D4DA),
        scheme(true, 0xFF7FD6EA, 0xFF003543, 0xFF125466, 0xFFCDEFF7, 0xFFFFB39E, 0xFF6B1F06, 0xFFFFDBD0, 0xFF0D1619, 0xFF142226, 0xFF1C2D32, 0xFFE1EDF0, 0xFFAFC2C8, 0xFF2F444A),
    ),
    ;

    val label: String get() = t(en, es)

    companion object {
        fun of(id: String?): Palette = entries.firstOrNull { it.id == id } ?: ALPHA
    }
}

/**
 * Builds a Material 3 scheme from a palette's few key colors.
 * card = surfaceContainerLow (cards/sections), raised = surfaceContainer
 * (navigation bar, dialogs), outline = borders and dividers (>= 3:1 for
 * control edges via [outline], softer [outlineVariant] for dividers).
 */
private fun scheme(
    dark: Boolean,
    primary: Long, onPrimary: Long, primaryContainer: Long, onPrimaryContainer: Long,
    secondary: Long, secondaryContainer: Long, onSecondaryContainer: Long,
    background: Long, card: Long, raised: Long, onBackground: Long, onSurfaceVariant: Long, outlineVariant: Long,
): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary), onSecondary = if (dark) Color(0xFF1A1A1A) else Color.White,
        secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(secondary), onTertiary = if (dark) Color(0xFF1A1A1A) else Color.White,
        background = Color(background), onBackground = Color(onBackground),
        surface = Color(background), onSurface = Color(onBackground),
        surfaceVariant = Color(raised), onSurfaceVariant = Color(onSurfaceVariant),
        surfaceContainerLowest = Color(card), surfaceContainerLow = Color(card),
        surfaceContainer = Color(raised), surfaceContainerHigh = Color(raised), surfaceContainerHighest = Color(raised),
        outline = Color(onSurfaceVariant), outlineVariant = Color(outlineVariant),
    )
}

/** Type scale: 16 sp body with 24 sp lines; semibold titles and buttons. */
private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

/** Corners: 8 (chips), 12 (buttons, fields), 16 (cards), 24 (dialogs, sheets). */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Spacing rhythm used across screens (4/8 dp grid). */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Applies the palette chosen in Protect › Color theme (live: changes repaint at once). */
@Composable
fun AlphaTheme(content: @Composable () -> Unit) {
    val state by Graph.store.state.collectAsState()
    val palette = Palette.of(state.palette)
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalBrand provides palette.brand) {
        MaterialTheme(
            colorScheme = if (dark) palette.dark else palette.light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Big, calm text style for the verse on the block screen. */
val VerseStyle = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal)
