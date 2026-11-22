package aktarjabed.com.jaascanner.core

import java.text.Normalizer
import java.util.Locale

/**
 * Normalises text stored for library search.
 *
 * SQLite's `LIKE` only folds ASCII A-Z, so searching for "MÜLLER" would miss a document whose
 * OCR text says "Müller" (and NFKC compatibility characters such as the "ﬁ" ligature would not
 * match "fi" at all). Both the stored text and the query go through the same normalisation:
 * compatibility-normalised (NFKC) and lower-cased with a locale-independent rule.
 */
object TextSearch {

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}
