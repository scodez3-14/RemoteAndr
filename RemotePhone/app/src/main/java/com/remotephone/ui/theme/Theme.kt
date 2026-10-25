package com.remotephone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand colors
val Blue40 = Color(0xFF1565C0)
val Blue80 = Color(0xFF90CAF9)
val BlueGrey40 = Color(0xFF546E7A)
val BlueGrey80 = Color(0xFFB0BEC5)
val Teal40 = Color(0xFF00897B)
val Teal80 = Color(0xFF80CBC4)
val Red40 = Color(0xFFE53935)
val Red80 = Color(0xFFEF9A9A)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004880),
    onPrimaryContainer = Blue80,
    secondary = Teal80,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Teal80,
    tertiary = BlueGrey80,
    error = Red80,
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2F0E8),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = BlueGrey40,
    error = Red40,
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
)

@Composable
fun RemotePhoneTheme(
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
        typography = Typography(),
        content = content
    )
}
