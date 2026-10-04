package com.farmtourism.assistant.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = FarmGreenPrimaryLight,
    onPrimary = FarmGreenOnPrimaryLight,
    primaryContainer = FarmGreenPrimaryContainerLight,
    onPrimaryContainer = FarmGreenOnPrimaryContainerLight,
    secondary = HarvestAmberSecondaryLight,
    onSecondary = HarvestAmberOnSecondaryLight,
    secondaryContainer = HarvestAmberSecondaryContainerLight,
    onSecondaryContainer = HarvestAmberOnSecondaryContainerLight,
    tertiary = RiverBlueTertiaryLight,
    onTertiary = RiverBlueOnTertiaryLight,
    tertiaryContainer = RiverBlueTertiaryContainerLight,
    onTertiaryContainer = RiverBlueOnTertiaryContainerLight,
    background = FarmBackgroundLight,
    onBackground = FarmOnBackgroundLight,
    surface = FarmSurfaceLight,
    onSurface = FarmOnSurfaceLight,
    surfaceVariant = FarmSurfaceVariantLight,
    onSurfaceVariant = FarmOnSurfaceVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = FarmGreenPrimaryDark,
    onPrimary = FarmGreenOnPrimaryDark,
    primaryContainer = FarmGreenPrimaryContainerDark,
    onPrimaryContainer = FarmGreenOnPrimaryContainerDark,
    secondary = HarvestAmberSecondaryDark,
    onSecondary = HarvestAmberOnSecondaryDark,
    secondaryContainer = HarvestAmberSecondaryContainerDark,
    onSecondaryContainer = HarvestAmberOnSecondaryContainerDark,
    tertiary = RiverBlueTertiaryDark,
    onTertiary = RiverBlueOnTertiaryDark,
    tertiaryContainer = RiverBlueTertiaryContainerDark,
    onTertiaryContainer = RiverBlueOnTertiaryContainerDark,
    background = FarmBackgroundDark,
    onBackground = FarmOnBackgroundDark,
    surface = FarmSurfaceDark,
    onSurface = FarmOnSurfaceDark,
    surfaceVariant = FarmSurfaceVariantDark,
    onSurfaceVariant = FarmOnSurfaceVariantDark
)

@Composable
fun FarmTourismTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        content = content
    )
}
