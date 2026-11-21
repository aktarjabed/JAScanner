package aktarjabed.com.jaascanner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps the user's drawn signatures as transparent PNG files inside app storage. */
class SignatureStore(context: Context) {

    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }

    suspend fun list(): List<File> = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.extension.equals("png", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    suspend fun save(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        // A timestamp alone can collide if two signatures are saved within the same millisecond;
        // a short random suffix makes the name unique regardless of timing.
        val file = File(dir, "sig_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }

    suspend fun load(file: File): Bitmap? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(file.absolutePath)
    }

    suspend fun delete(file: File) = withContext(Dispatchers.IO) { file.delete() }
}
