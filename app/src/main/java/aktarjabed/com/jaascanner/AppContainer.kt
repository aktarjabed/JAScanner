package aktarjabed.com.jaascanner

import android.content.Context
import aktarjabed.com.jaascanner.core.Messenger
import aktarjabed.com.jaascanner.data.DocumentRepository
import aktarjabed.com.jaascanner.data.SettingsRepository
import aktarjabed.com.jaascanner.data.SignatureStore
import aktarjabed.com.jaascanner.data.db.AppDatabase
import aktarjabed.com.jaascanner.ocr.OcrEngine
import aktarjabed.com.jaascanner.ocr.OcrIndexer
import aktarjabed.com.jaascanner.ui.components.ThumbnailLoader
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-written dependency container: one instance for the whole process. */
class AppContainer(context: Context) {

    /** Outlives every screen; used for work that must finish even if the user navigates away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val messenger = Messenger()
    val database = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val documents = DocumentRepository(context, database, database.documentDao(), database.folderDao())
    val signatures = SignatureStore(context)
    val ocr = OcrEngine()
    val ocrIndexer = OcrIndexer(documents, ocr, settings, appScope, messenger)
    val thumbnails = ThumbnailLoader()

    init {
        // Files staged for sharing are only needed briefly; start each process with a clean folder.
        appScope.launch(Dispatchers.IO) { File(context.cacheDir, "share").deleteRecursively() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as JAScannerApp).container
