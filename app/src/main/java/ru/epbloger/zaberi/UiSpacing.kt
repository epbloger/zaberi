package ru.epbloger.zaberi

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Shared visual rhythm. System-bar and keyboard insets belong to SafeAppViewport. */
internal object UiSpacing {
    val Page = 16.dp
    val Card = 16.dp
    val Block = 12.dp
    val Related = 8.dp
    val Tight = 4.dp
    val Touch = 48.dp
    val CardRadius = 20.dp
}
internal val LocalActionPagePadding = staticCompositionLocalOf { UiSpacing.Page }
