package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldMint,
    onPrimary = OnEmeraldMint,
    primaryContainer = DarkSageContainer,
    onPrimaryContainer = OnDarkSageContainer,
    secondary = WarmGold,
    onSecondary = OnWarmGold,
    secondaryContainer = DarkWheatContainer,
    onSecondaryContainer = OnDarkWheatContainer,
    tertiary = TerracottaContainer,
    onTertiary = OnTerracottaContainer,
    background = DarkForestBg,
    onBackground = SoftCreamText,
    surface = DarkForestSurface,
    onSurface = SoftCreamText,
    surfaceVariant = DarkForestSurfaceVariant,
    onSurfaceVariant = DarkMossText,
    outline = DarkMossText,
    outlineVariant = DarkForestSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = OnForestGreen,
    primaryContainer = SageContainer,
    onPrimaryContainer = OnSageContainer,
    secondary = HarvestAmber,
    onSecondary = OnHarvestAmber,
    secondaryContainer = WheatContainer,
    onSecondaryContainer = OnWheatContainer,
    tertiary = Terracotta,
    onTertiary = OnTerracotta,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = OnTerracottaContainer,
    background = WarmLinen,
    onBackground = CharcoalSoil,
    surface = CreamSurface,
    onSurface = CharcoalSoil,
    surfaceVariant = SoftOatVariant,
    onSurfaceVariant = MutedMoss,
    outline = EarthOutline,
    outlineVariant = EarthOutlineVariant,
    error = ErrorRed,
    errorContainer = ErrorContainerLight
)

val GreenHarvestShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GreenHarvestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GreenHarvestShapes,
        content = content
    )
}
