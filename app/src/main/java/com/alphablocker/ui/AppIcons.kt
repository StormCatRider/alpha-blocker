// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * AppIcons.kt — the app's own line icons (24 dp, 2 dp stroke, round ends)
 *
 * One consistent outline family for the tab bar and section headers, drawn as
 * vectors so they look the same on every phone and take the theme's color.
 * (Before 1.18 the tabs used text symbols like ◉ ⚙, which depend on the
 * phone's font.) Same shapes as the website's icons.
 * No extra library: plain SVG path data turned into ImageVectors.
 * =============================================================================
 */
package com.alphablocker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object AppIcons {

    /** Builds a 24×24 stroke icon from SVG path strings. Icon() tints it. */
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (d in paths) {
                    addPath(
                        pathData = addPathNodes(d),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()

    /** Status: shield with a check. */
    val Status = icon("status", "M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z", "M9 12l2 2 4-4")

    /** Apps: four tiles, one with a plus. */
    val Apps = icon(
        "apps",
        "M4.5 3h4A1.5 1.5 0 0 1 10 4.5v4A1.5 1.5 0 0 1 8.5 10h-4A1.5 1.5 0 0 1 3 8.5v-4A1.5 1.5 0 0 1 4.5 3z",
        "M15.5 3h4A1.5 1.5 0 0 1 21 4.5v4A1.5 1.5 0 0 1 19.5 10h-4A1.5 1.5 0 0 1 14 8.5v-4A1.5 1.5 0 0 1 15.5 3z",
        "M4.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 8.5 21h-4A1.5 1.5 0 0 1 3 19.5v-4A1.5 1.5 0 0 1 4.5 14z",
        "M14 17.5h7", "M17.5 14v7",
    )

    /** Filters: a circle with a slash (blocked). */
    val Filters = icon("filters", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M5.6 5.6l12.8 12.8")

    /** Verses: a book with a cross. */
    val Verses = icon("verses", "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z", "M5 17a3 3 0 0 1 3-3h11", "M11.5 6v5", "M9.5 8h4")

    /** Protect: a padlock. */
    val Protect = icon(
        "protect",
        "M6 11h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z",
        "M8 11V8a4 4 0 0 1 8 0v3",
    )

    /** Small check, for done items. */
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")

    /** Small cross, for problems. */
    val Cross = icon("cross", "M6.5 6.5l11 11", "M17.5 6.5l-11 11")

    /** Small open circle, for items not set yet. */
    val Circle = icon("circle", "M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z")
}
