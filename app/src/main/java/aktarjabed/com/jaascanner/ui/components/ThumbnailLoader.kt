package aktarjabed.com.jaascanner.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.pdf.PdfPageRenderer
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Renders and caches first-page previews for the library. */
class ThumbnailLoader {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(2)

    suspend fun load(file: File, version: Long, widthPx: Int): Bitmap? {
        val key = "${file.name}:$version:$widthPx"
        cache.get(key)?.let { return it }
        return gate.withPermit {
            cache.get(key) ?: run {
                val renderer = PdfPageRenderer.open(file) ?: return@withPermit null
                try {
                    renderer.render(0, widthPx)?.also { cache.put(key, it) }
                } finally {
                    withContext(NonCancellable) { renderer.release() }
                }
            }
        }
    }
}

@Composable
fun DocThumbnail(file: File, version: Long, modifier: Modifier = Modifier, widthPx: Int = 360) {
    val loader = LocalContext.current.container.thumbnails
    // widthPx is part of the cache key inside the loader, so it must be part of the effect key
    // too; otherwise a thumbnail first drawn small keeps its low-resolution bitmap when the same
    // composable is reused at a larger size.
    val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath, version, widthPx) {
        value = loader.load(file, version, widthPx)
    }
    Box(modifier.background(Color.White)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
