package aktarjabed.com.jaascanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { System, Light, Dark }

enum class ScanMode(val label: String) {
    Full("Full: auto-crop, filters and clean-up"),
    BaseWithFilter("Basic with filters"),
    Base("Basic: crop and rotate only"),
}

enum class OcrLanguage(val label: String) {
    Latin("Latin (English and most European languages)"),
    Devanagari("Devanagari (Hindi, Marathi, Nepali)"),
}

enum class SortOrder(val label: String) {
    Newest("Newest first"),
    Name("Name"),
    Size("Largest first"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** False (the default) uses JAScanner's own colours; true matches the phone's wallpaper (Material You). */
    val useDynamicColor: Boolean = false,
    val scanMode: ScanMode = ScanMode.Full,
    val pageLimit: Int = 100,
    val autoOcr: Boolean = true,
    val ocrLanguage: OcrLanguage = OcrLanguage.Latin,
    val appLock: Boolean = false,
    val blockScreenshots: Boolean = false,
    val sortOrder: SortOrder = SortOrder.Newest,
    val gridView: Boolean = true,
    val compressQuality: Int = 65,
    /** Folder that new scans are saved into; null means no folder. */
    val defaultFolderId: Long? = null,
)

/** What the editor remembers between sessions: last-used colours, widths, typeface and recently typed text. */
data class EditorMemory(
    val penColor: Int? = null,
    val highlightColor: Int? = null,
    val penWidth: Float? = null,
    val textScale: Float? = null,
    val textFace: String? = null,
    val recentTexts: List<String> = emptyList(),
)

private const val RECENT_SEPARATOR = '\u001F'

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SCAN_MODE = stringPreferencesKey("scan_mode")
        val PAGE_LIMIT = intPreferencesKey("page_limit")
        val AUTO_OCR = booleanPreferencesKey("auto_ocr")
        val OCR_LANGUAGE = stringPreferencesKey("ocr_language")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        val SORT = stringPreferencesKey("sort_order")
        val GRID = booleanPreferencesKey("grid_view")
        val COMPRESS = intPreferencesKey("compress_quality")
        val DEFAULT_FOLDER = longPreferencesKey("default_folder_id")
        val ED_PEN_COLOR = intPreferencesKey("editor_pen_color")
        val ED_HIGHLIGHT_COLOR = intPreferencesKey("editor_highlight_color")
        val ED_PEN_WIDTH = floatPreferencesKey("editor_pen_width")
        val ED_TEXT_SCALE = floatPreferencesKey("editor_text_scale")
        val ED_TEXT_FACE = stringPreferencesKey("editor_text_face")
        val ED_RECENT = stringPreferencesKey("editor_recent_texts")
    }

    val editorMemory: Flow<EditorMemory> = context.settingsDataStore.data
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyPreferences())
        }
        .map { prefs ->
            EditorMemory(
                penColor = prefs[Keys.ED_PEN_COLOR],
                highlightColor = prefs[Keys.ED_HIGHLIGHT_COLOR],
                penWidth = prefs[Keys.ED_PEN_WIDTH],
                textScale = prefs[Keys.ED_TEXT_SCALE],
                textFace = prefs[Keys.ED_TEXT_FACE],
                recentTexts = prefs[Keys.ED_RECENT]?.split(RECENT_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList(),
            )
        }

    suspend fun saveEditorMemory(memory: EditorMemory) = edit { prefs ->
        memory.penColor?.let { prefs[Keys.ED_PEN_COLOR] = it }
        memory.highlightColor?.let { prefs[Keys.ED_HIGHLIGHT_COLOR] = it }
        memory.penWidth?.let { prefs[Keys.ED_PEN_WIDTH] = it }
        memory.textScale?.let { prefs[Keys.ED_TEXT_SCALE] = it }
        memory.textFace?.let { prefs[Keys.ED_TEXT_FACE] = it }
        prefs[Keys.ED_RECENT] = memory.recentTexts.joinToString(RECENT_SEPARATOR.toString())
    }

    /** Forgets what the editor remembered (colours, widths, typeface, recent text). */
    suspend fun clearEditorMemory() = edit { prefs ->
        prefs.remove(Keys.ED_PEN_COLOR)
        prefs.remove(Keys.ED_HIGHLIGHT_COLOR)
        prefs.remove(Keys.ED_PEN_WIDTH)
        prefs.remove(Keys.ED_TEXT_SCALE)
        prefs.remove(Keys.ED_TEXT_FACE)
        prefs.remove(Keys.ED_RECENT)
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyPreferences())
        }
        .map { prefs ->
            val d = AppSettings()
            AppSettings(
                themeMode = prefs[Keys.THEME].toEnumOr(d.themeMode),
                useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: d.useDynamicColor,
                scanMode = prefs[Keys.SCAN_MODE].toEnumOr(d.scanMode),
                pageLimit = prefs[Keys.PAGE_LIMIT] ?: d.pageLimit,
                autoOcr = prefs[Keys.AUTO_OCR] ?: d.autoOcr,
                ocrLanguage = prefs[Keys.OCR_LANGUAGE].toEnumOr(d.ocrLanguage),
                appLock = prefs[Keys.APP_LOCK] ?: d.appLock,
                blockScreenshots = prefs[Keys.BLOCK_SCREENSHOTS] ?: d.blockScreenshots,
                sortOrder = prefs[Keys.SORT].toEnumOr(d.sortOrder),
                gridView = prefs[Keys.GRID] ?: d.gridView,
                compressQuality = prefs[Keys.COMPRESS] ?: d.compressQuality,
                defaultFolderId = prefs[Keys.DEFAULT_FOLDER],
            )
        }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME] = value.name }

    suspend fun setUseDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = value }

    suspend fun setScanMode(value: ScanMode) = edit { it[Keys.SCAN_MODE] = value.name }

    suspend fun setPageLimit(value: Int) = edit { it[Keys.PAGE_LIMIT] = value }

    suspend fun setAutoOcr(value: Boolean) = edit { it[Keys.AUTO_OCR] = value }

    suspend fun setOcrLanguage(value: OcrLanguage) = edit { it[Keys.OCR_LANGUAGE] = value.name }

    suspend fun setAppLock(value: Boolean) = edit { it[Keys.APP_LOCK] = value }

    suspend fun setBlockScreenshots(value: Boolean) = edit { it[Keys.BLOCK_SCREENSHOTS] = value }

    suspend fun setSortOrder(value: SortOrder) = edit { it[Keys.SORT] = value.name }

    suspend fun setGridView(value: Boolean) = edit { it[Keys.GRID] = value }

    suspend fun setCompressQuality(value: Int) = edit { it[Keys.COMPRESS] = value }

    suspend fun setDefaultFolderId(value: Long?) = edit { prefs ->
        if (value == null) prefs.remove(Keys.DEFAULT_FOLDER) else prefs.set(Keys.DEFAULT_FOLDER, value)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
