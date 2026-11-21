package aktarjabed.com.jaascanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import aktarjabed.com.jaascanner.data.ThemeMode

/**
 * JAScanner's own branded colours ([JAScannerLightColors] / [JAScannerDarkColors]) by default, so
 * the app looks like itself out of the box rather than a generic Material app; [dynamicColor] (a
 * user setting, "Match my wallpaper") switches to Android's wallpaper-derived Material You
 * palette instead, always available since minSdk is 32. Typography and shapes are JAScanner's own
 * ([JAScannerTypography], [JAScannerShapes]) in both cases.
 */
@Composable
fun JAScannerTheme(mode: ThemeMode, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> JAScannerDarkColors
        else -> JAScannerLightColors
    }
    MaterialTheme(colorScheme = scheme, typography = JAScannerTypography, shapes = JAScannerShapes, content = content)
}
