// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Priority accent colors used across the app. */
object PriorityColors {
    /** 0 = none, 1 = low, 2 = medium, 3 = high, 4 = urgent. */
    val names = listOf("No priority", "Low", "Medium", "High", "Urgent")
    fun name(priority: Int): String = names.getOrElse(priority) { names[0] }
    /** Sort key: urgent first, no priority last. */
    fun rank(priority: Int): Int = if (priority <= 0) 9 else 5 - priority
    fun of(priority: Int): Color? = when (priority) {
        1 -> Color(0xFF1E88E5)  // low: blue
        2 -> Color(0xFFF9A825)  // medium: amber
        3 -> Color(0xFFFB8C00)  // high: orange
        4 -> Color(0xFFE53935)  // urgent: red
        else -> null
    }
}

/** A selectable accent palette. All are free. Index 0 uses Material You colours on Android 12+. */
data class Palette(val name: String, val seed: Color, val seedDark: Color)

object Palettes {
    val all = listOf(
        Palette("Dynamic / Indigo", Color(0xFF4F5BD5), Color(0xFFB8C0FF)),
        Palette("Forest", Color(0xFF2E7D32), Color(0xFF9BD79E)),
        Palette("Ocean", Color(0xFF00796B), Color(0xFF7FD7CB)),
        Palette("Sunset", Color(0xFFE64A19), Color(0xFFFFB59E)),
        Palette("Rose", Color(0xFFC2185B), Color(0xFFFFB0CB)),
        Palette("Grape", Color(0xFF7B1FA2), Color(0xFFE0B0FF)),
        Palette("Slate", Color(0xFF455A64), Color(0xFFB0BEC5)),
    )

    fun scheme(index: Int, dark: Boolean): ColorScheme {
        val p = all[index.coerceIn(0, all.lastIndex)]
        return if (dark) darkColorScheme(primary = p.seedDark, secondary = p.seedDark.copy(alpha = 0.9f), tertiary = Color(0xFF80CBC4))
        else lightColorScheme(primary = p.seed, secondary = p.seed.copy(alpha = 0.85f), tertiary = Color(0xFF00897B))
    }
}

/** themeMode: 0 system, 1 light, 2 dark. palette 0 = dynamic colour when the OS supports it. */
@Composable
fun TaskHubTheme(themeMode: Int = 0, palette: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = if (palette == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else Palettes.scheme(palette, dark)
    MaterialTheme(colorScheme = scheme, content = content)
}
