package com.zenni.exposuremeter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Warm amber accent evoking an exposure needle; used only when dynamic colour is
// unavailable (< Android 12). The app must look correct on every OEM skin
// (brief §7), so a complete static scheme is defined for both light and dark.
private val Amber = Color(0xFFF2B705)
private val AmberDark = Color(0xFF5E4200)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF6B5D3F),
    tertiary = Color(0xFF4C6543),
    background = Color(0xFFFFF8F2),
    surface = Color(0xFFFFF8F2),
    surfaceVariant = Color(0xFFEDE1CF),
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberDark,
    primaryContainer = Color(0xFF876300),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFD7C4A1),
    tertiary = Color(0xFFB2CFA4),
    background = Color(0xFF16130B),
    surface = Color(0xFF16130B),
    surfaceVariant = Color(0xFF4D4639),
)

/** App theme. Uses Material You dynamic colour on Android 12+, else the amber scheme. */
@Composable
fun ExposureMeterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
