package aktarjabed.com.jaascanner.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import aktarjabed.com.jaascanner.pdf.PdfPageSource

/** Inverts colours so white pages become dark for night reading. */
val NightColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** One rendered PDF page at the given on-screen width, with an optional overlay drawn on top. */
@Composable
fun PageView(
    source: PdfPageSource,
    index: Int,
    width: Dp,
    night: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, source, index, widthPx) {
        value = source.render(index, widthPx)
    }
    Box(
        modifier
            .width(width)
            .aspectRatio(1f / source.heightOverWidth(index))
            .background(Color.White),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                colorFilter = if (night) NightColorFilter else null,
            )
        }
        overlay()
    }
}
