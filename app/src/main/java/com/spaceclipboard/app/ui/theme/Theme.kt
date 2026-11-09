package com.spaceclipboard.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SpaceColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = DeepSpace,
    primaryContainer = BlueDeep,
    onPrimaryContainer = TextPrimary,
    secondary = Blue,
    onSecondary = TextPrimary,
    secondaryContainer = PanelRaised,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanSoft,
    onTertiary = DeepSpace,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = Panel,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelRaised,
    surfaceContainerHighest = PanelFocused,
    error = Error,
    onError = DeepSpace,
    errorContainer = Error.copy(alpha = 0.16f),
    onErrorContainer = Error,
    outline = OutlineStrong,
    outlineVariant = Outline,
    scrim = DeepSpace,
)

@Composable
fun SpaceClipboardTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = SpaceColorScheme,
        typography = SpaceTypography,
        shapes = SpaceShapes,
        content = content,
    )
}
