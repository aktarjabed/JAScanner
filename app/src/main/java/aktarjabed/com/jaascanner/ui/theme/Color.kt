package aktarjabed.com.jaascanner.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * JAScanner's own Material 3 colour scheme, hand-tuned from the launcher icon's blue
 * (#1565C0) and amber (#FFC107) so the app, its icon and its splash screen read as one brand.
 * Used by default; "Match my wallpaper" in Settings switches to Android's dynamic colour
 * instead (see [JAScannerTheme]). Tonal roles follow the usual Material 3 pattern: a colour
 * and its "on" pair are built for contrast, a "container" is a soft tint for large surfaces
 * (chips, cards, filled buttons), and dark-mode tones mirror light-mode roughly in reverse.
 */

// Primary: the launcher-icon blue.
private val PrimaryLight = Color(0xFF1C5EA8)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFD6E3FF)
private val OnPrimaryContainerLight = Color(0xFF001C38)

// Secondary: a quiet blue-grey, for lower-emphasis chips and controls.
private val SecondaryLight = Color(0xFF55606F)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFD9E3F8)
private val OnSecondaryContainerLight = Color(0xFF121C2B)

// Tertiary: the launcher-icon amber, for small accents (highlights, the odd call-out chip).
private val TertiaryLight = Color(0xFF7A5900)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFFFDEA6)
private val OnTertiaryContainerLight = Color(0xFF271900)

private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)

private val BackgroundLight = Color(0xFFFBFCFF)
private val OnBackgroundLight = Color(0xFF1A1C1E)
private val SurfaceLight = Color(0xFFFBFCFF)
private val OnSurfaceLight = Color(0xFF1A1C1E)
private val SurfaceVariantLight = Color(0xFFDFE2EB)
private val OnSurfaceVariantLight = Color(0xFF43474E)
private val OutlineLight = Color(0xFF73777F)
private val OutlineVariantLight = Color(0xFFC3C7CF)
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFF5F6FA)
private val SurfaceContainerLight = Color(0xFFEFF1F5)
private val SurfaceContainerHighLight = Color(0xFFE9EBF0)
private val SurfaceContainerHighestLight = Color(0xFFE3E6EB)
private val InverseSurfaceLight = Color(0xFF2E3133)
private val InverseOnSurfaceLight = Color(0xFFF0F1F4)
private val InversePrimaryLight = Color(0xFFA7C8FF)

val JAScannerLightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    inversePrimary = InversePrimaryLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    scrim = Color.Black,
)

private val PrimaryDark = Color(0xFFA7C8FF)
private val OnPrimaryDark = Color(0xFF003060)
private val PrimaryContainerDark = Color(0xFF00468A)
private val OnPrimaryContainerDark = Color(0xFFD6E3FF)

private val SecondaryDark = Color(0xFFBCC7DB)
private val OnSecondaryDark = Color(0xFF273141)
private val SecondaryContainerDark = Color(0xFF3D4758)
private val OnSecondaryContainerDark = Color(0xFFD9E3F8)

private val TertiaryDark = Color(0xFFF4BD48)
private val OnTertiaryDark = Color(0xFF412D00)
private val TertiaryContainerDark = Color(0xFF5E4200)
private val OnTertiaryContainerDark = Color(0xFFFFDEA6)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

private val BackgroundDark = Color(0xFF121316)
private val OnBackgroundDark = Color(0xFFE3E2E6)
private val SurfaceDark = Color(0xFF121316)
private val OnSurfaceDark = Color(0xFFE3E2E6)
private val SurfaceVariantDark = Color(0xFF43474E)
private val OnSurfaceVariantDark = Color(0xFFC3C7CF)
private val OutlineDark = Color(0xFF8D9199)
private val OutlineVariantDark = Color(0xFF43474E)
private val SurfaceContainerLowestDark = Color(0xFF0C0E11)
private val SurfaceContainerLowDark = Color(0xFF1A1C1E)
private val SurfaceContainerDark = Color(0xFF1E2022)
private val SurfaceContainerHighDark = Color(0xFF282A2D)
private val SurfaceContainerHighestDark = Color(0xFF333538)
private val InverseSurfaceDark = Color(0xFFE3E2E6)
private val InverseOnSurfaceDark = Color(0xFF2E3133)
private val InversePrimaryDark = Color(0xFF1C5EA8)

val JAScannerDarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    inversePrimary = InversePrimaryDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    scrim = Color.Black,
)
