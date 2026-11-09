package com.wyrm.omrajput.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme

/** A card overrides UI roles for its descendants; gameplay assets keep their colours. */
internal val LocalWyrmPalette = staticCompositionLocalOf<WyrmPalette?> { null }

@Composable
internal fun WyrmHeroTheme(slot: Int = 1, enabled: Boolean = true, content: @Composable () -> Unit) {
    val base = LocalWyrmPalette.current ?: Wyrm.currentPalette
    val palette = if (enabled) base.heroView(slot) else base
    CompositionLocalProvider(LocalWyrmPalette provides palette, LocalContentColor provides palette.ink) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
            primary = palette.ink, onPrimary = palette.onInk,
            background = palette.paper, onBackground = palette.ink,
            surface = palette.card, onSurface = palette.ink, onSurfaceVariant = palette.mute,
            secondary = palette.link, onSecondary = palette.onInk, outline = palette.rule,
        ), content = content)
    }
}
