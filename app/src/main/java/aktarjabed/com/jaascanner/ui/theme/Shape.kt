package aktarjabed.com.jaascanner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Softer, more contemporary corner radii than the Material 3 defaults (default medium is
 * 12dp; this is 16dp). Every `Card`, filled/outlined `Button`, `AlertDialog`, bottom sheet,
 * `TextField` and chip across the app reads these from `MaterialTheme.shapes` automatically,
 * so this one file is most of the app's "rounder, softer" visual update.
 */
val JAScannerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
