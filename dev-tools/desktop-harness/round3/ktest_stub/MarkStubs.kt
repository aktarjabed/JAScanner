// Desktop stand-ins for the Android-facing mark types, so pdf/MarkGeometry.kt and its test can be
// compiled and run without Android or PDFBox. They mirror the real declarations in PdfEditor.kt;
// update them when those change.
package aktarjabed.com.jaascanner.pdf

import android.graphics.Bitmap

sealed interface Mark

data class StrokeMark(
    val points: List<NormPoint>,
    val colorArgb: Int,
    val widthFraction: Float,
    val alpha: Float = 1f,
) : Mark

data class FillRectMark(
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val colorArgb: Int,
    val alpha: Float = 1f,
) : Mark

class StampMark(
    val bitmap: Bitmap,
    val centerU: Float,
    val centerV: Float,
    val widthFraction: Float,
    val extraAngleDegrees: Float = 0f,
    val opacity: Float = 1f,
) : Mark

enum class TextFace { Print, Cursive, Casual }

class TextEditMark(
    val word: TextWord,
    val newText: String,
    val textArgb: Int,
    val backgroundArgb: Int,
    val face: TextFace? = null,
    val sourceWord: TextWord = word,
    val layoutChanged: Boolean = false,
) : Mark

class NoteMark(
    val u: Float,
    val v: Float,
    val text: String,
) : Mark
