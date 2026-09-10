package com.example.nflunkyball.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = OnGreenPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = OnGreenPrimaryContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmberSecondaryDark,
    secondaryContainer = AmberSecondaryContainerDark,
    onSecondaryContainer = OnAmberSecondaryContainerDark,
    tertiary = BrownTertiaryDark,
    onTertiary = OnBrownTertiaryDark,
    tertiaryContainer = BrownTertiaryContainerDark,
    onTertiaryContainer = OnBrownTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = CreamBackgroundDark,
    onBackground = OnCreamBackgroundDark,
    surface = CreamBackgroundDark,
    onSurface = OnCreamBackgroundDark,
    surfaceVariant = CreamSurfaceVariantDark,
    onSurfaceVariant = OnCreamSurfaceVariantDark,
    outline = CreamOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = OnGreenPrimaryLight,
    primaryContainer = GreenPrimaryContainerLight,
    onPrimaryContainer = OnGreenPrimaryContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = OnAmberSecondaryLight,
    secondaryContainer = AmberSecondaryContainerLight,
    onSecondaryContainer = OnAmberSecondaryContainerLight,
    tertiary = BrownTertiaryLight,
    onTertiary = OnBrownTertiaryLight,
    tertiaryContainer = BrownTertiaryContainerLight,
    onTertiaryContainer = OnBrownTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = CreamBackgroundLight,
    onBackground = OnCreamBackgroundLight,
    surface = CreamBackgroundLight,
    onSurface = OnCreamBackgroundLight,
    surfaceVariant = CreamSurfaceVariantLight,
    onSurfaceVariant = OnCreamSurfaceVariantLight,
    outline = CreamOutlineLight
)

@Composable
fun NFLunkyBallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color derives the whole palette from the device wallpaper on Android 12+, which
    // can produce low-contrast/washed-out text depending on the wallpaper -- off by default so
    // this data-dense app gets a consistent, tested-legible palette regardless of device. Also,
    // now that the app has its own designed identity (see Color.kt), a wallpaper-derived palette
    // would just override it.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
