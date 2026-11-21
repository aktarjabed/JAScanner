package aktarjabed.com.jaascanner.pdf

import android.graphics.Bitmap
import android.graphics.Color
import aktarjabed.com.jaascanner.core.PageBox
import aktarjabed.com.jaascanner.core.FontGuessing
import aktarjabed.com.jaascanner.core.PdfCoordinateMapper
import aktarjabed.com.jaascanner.core.WatermarkLayout
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.IdentityHashMap
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Position on the page as the user sees it: u to the right, v downwards, both 0..1. */
data class NormPoint(val u: Float, val v: Float)

sealed interface Mark

/**
 * Freehand pen or highlighter stroke.
 * @param widthFraction stroke width as a fraction of the displayed page width.
 */
data class StrokeMark(
    val points: List<NormPoint>,
    val colorArgb: Int,
    val widthFraction: Float,
    val alpha: Float = 1f,
) : Mark

/** A filled rectangle between two corners given as page-relative positions (box highlight or white-out). */
data class FillRectMark(
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val colorArgb: Int,
    val alpha: Float = 1f,
) : Mark

/** An image (signature, text or watermark) placed on the page. */
class StampMark(
    val bitmap: Bitmap,
    val centerU: Float,
    val centerV: Float,
    val widthFraction: Float,
    val extraAngleDegrees: Float = 0f,
    val opacity: Float = 1f,
) : Mark

/**
 * A word of real page text replaced by new text. On save the original text is edited in place when
 * its font allows; otherwise the old word is covered and the new one drawn in a matched font.
 * Boxes are fractions of the displayed page.
 */
class TextEditMark(
    val word: TextWord,
    val newText: String,
    val textArgb: Int,
    val backgroundArgb: Int,
    /** null = match the original font; otherwise draw in this handwriting-style face. */
    val face: TextFace? = null,
) : Mark

/**
 * A sticky-note comment at ([u], [v]) on the page as displayed. On save it becomes a real PDF note
 * annotation, so other readers show it too.
 */
class NoteMark(
    val u: Float,
    val v: Float,
    val text: String,
) : Mark

/** What a save did with text edits. */
data class EditReport(val editedInPlace: Int = 0, val overlaid: Int = 0, val skipped: Int = 0)

/** Bakes pen strokes, signatures, text and watermarks into a copy of a PDF. */
object PdfEditor {

    suspend fun apply(source: File, target: File, marksByPage: Map<Int, List<Mark>>): EditReport {
        return withContext(Dispatchers.IO) {
            try {
                PDDocument.load(source).use { document ->
                    val images = IdentityHashMap<Bitmap, PDImageXObject>()
                    val fonts = HashMap<String, PDFont>()
                    var inPlace = 0
                    var overlaid = 0
                    var skipped = 0
                    for ((pageIndex, allMarks) in marksByPage) {
                        if (allMarks.isEmpty() || pageIndex !in 0 until document.numberOfPages) continue
                        // Text edits first: those the original font can draw change the page's own text.
                        val marks = allMarks.filter { mark ->
                            if (mark !is TextEditMark || mark.face != null) return@filter true
                            val done = runCatching {
                                PdfTextReplace.replaceInPlace(document, mark.word, mark.newText) == PdfTextReplace.Outcome.Done
                            }.getOrDefault(false)
                            if (done) inPlace++
                            !done
                        }
                        if (marks.isEmpty()) continue
                        val page = document.getPage(pageIndex)
                        val box = boxOf(page)
                        val rotation = page.rotation
                        val drawable = marks.filter { it !is NoteMark }
                        if (drawable.isNotEmpty()) {
                            PDPageContentStream(
                                document,
                                page,
                                PDPageContentStream.AppendMode.APPEND,
                                true,
                                true,
                            ).use { content ->
                                for (mark in drawable) {
                                    when (mark) {
                                        is StrokeMark -> drawStroke(content, box, rotation, mark)
                                        is StampMark -> drawStamp(document, content, box, rotation, mark, images)
                                        is FillRectMark -> drawFill(content, box, rotation, mark)
                                        is TextEditMark -> {
                                            val drawn = runCatching {
                                                drawTextEdit(document, content, box, rotation, mark, fonts, images)
                                            }.getOrDefault(false)
                                            if (drawn) overlaid++ else skipped++
                                        }
                                        is NoteMark -> Unit
                                    }
                                }
                            }
                        }
                        // Notes are annotations, not page content, so they are added after the drawing.
                        for (mark in marks) {
                            if (mark is NoteMark && mark.text.isNotBlank()) {
                                PdfNotes.addNote(document, pageIndex, mark.u, mark.v, mark.text)
                            }
                        }
                    }
                    document.save(target)
                    EditReport(inPlace, overlaid, skipped)
                }
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }

    /** Diagonal (or any angle) watermark repeated on every page. */
    suspend fun watermark(
        source: File,
        target: File,
        stamp: Bitmap,
        opacity: Float,
        widthFraction: Float = 0.6f,
        angleDegrees: Float = 45f,
        tiled: Boolean = false,
    ) {
        val pageCount = withContext(Dispatchers.IO) {
            PDDocument.load(source).use { it.numberOfPages }
        }
        val spots = if (tiled) WatermarkLayout.tiled(columns = 3, rows = 4) else WatermarkLayout.centre()
        val marks = spots.map { spot ->
            StampMark(
                bitmap = stamp,
                centerU = spot.centerU,
                centerV = spot.centerV,
                widthFraction = widthFraction,
                extraAngleDegrees = angleDegrees,
                opacity = opacity,
            )
        }
        apply(source, target, (0 until pageCount).associateWith { marks })
    }

    private fun boxOf(page: PDPage): PageBox {
        val crop = page.cropBox
        return PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height)
    }

    private fun alphaState(alpha: Float): PDExtendedGraphicsState {
        val state = PDExtendedGraphicsState()
        state.setStrokingAlphaConstant(alpha)
        state.setNonStrokingAlphaConstant(alpha)
        return state
    }

    private fun drawFill(content: PDPageContentStream, box: PageBox, rotation: Int, mark: FillRectMark) {
        val corners = listOf(
            PdfCoordinateMapper.point(box, rotation, mark.u0, mark.v0),
            PdfCoordinateMapper.point(box, rotation, mark.u1, mark.v0),
            PdfCoordinateMapper.point(box, rotation, mark.u1, mark.v1),
            PdfCoordinateMapper.point(box, rotation, mark.u0, mark.v1),
        )
        content.saveGraphicsState()
        if (mark.alpha < 1f) content.setGraphicsStateParameters(alphaState(mark.alpha))
        content.setNonStrokingColor(Color.red(mark.colorArgb), Color.green(mark.colorArgb), Color.blue(mark.colorArgb))
        content.moveTo(corners[0].x, corners[0].y)
        for (i in 1 until corners.size) content.lineTo(corners[i].x, corners[i].y)
        content.closePath()
        content.fill()
        content.restoreGraphicsState()
    }

    private fun drawStroke(content: PDPageContentStream, box: PageBox, rotation: Int, mark: StrokeMark) {
        if (mark.points.isEmpty()) return
        content.saveGraphicsState()
        if (mark.alpha < 1f) content.setGraphicsStateParameters(alphaState(mark.alpha))
        content.setStrokingColor(Color.red(mark.colorArgb), Color.green(mark.colorArgb), Color.blue(mark.colorArgb))
        content.setLineWidth(max(0.5f, PdfCoordinateMapper.displayedWidth(box, rotation) * mark.widthFraction))
        content.setLineCapStyle(1)
        content.setLineJoinStyle(1)
        val points = mark.points.map { PdfCoordinateMapper.point(box, rotation, it.u, it.v) }
        content.moveTo(points[0].x, points[0].y)
        if (points.size == 1) {
            // A tap: a zero-length line with round caps draws a dot.
            content.lineTo(points[0].x + 0.01f, points[0].y)
        } else {
            for (i in 1 until points.size) content.lineTo(points[i].x, points[i].y)
        }
        content.stroke()
        content.restoreGraphicsState()
    }

    private fun drawStamp(
        document: PDDocument,
        content: PDPageContentStream,
        box: PageBox,
        rotation: Int,
        mark: StampMark,
        images: IdentityHashMap<Bitmap, PDImageXObject>,
    ) {
        val bitmap = mark.bitmap
        if (bitmap.isRecycled || bitmap.height == 0) return
        val image = images.getOrPut(bitmap) { LosslessFactory.createFromImage(document, bitmap) }
        val placement = PdfCoordinateMapper.stamp(
            box = box,
            rotation = rotation,
            centerU = mark.centerU,
            centerV = mark.centerV,
            widthFraction = mark.widthFraction,
            aspect = bitmap.width.toFloat() / bitmap.height.toFloat(),
            extraAngleDegrees = mark.extraAngleDegrees,
        )
        content.saveGraphicsState()
        if (mark.opacity < 1f) content.setGraphicsStateParameters(alphaState(mark.opacity))
        content.transform(
            Matrix.getRotateInstance(
                Math.toRadians(placement.angleDegrees.toDouble()),
                placement.centerX,
                placement.centerY,
            ),
        )
        content.drawImage(
            image,
            -placement.width / 2f,
            -placement.height / 2f,
            placement.width,
            placement.height,
        )
        content.restoreGraphicsState()
    }

    private const val COVER_SIDE_PAD = 0.12f
    private const val HANDWRITING_SCALE = 1.15f

    /**
     * Covers the old word and draws the new one. Returns false when no available font can draw it.
     * The cover is a plain colour, so on a photo or gradient background it will show.
     */
    private fun drawTextEdit(
        document: PDDocument,
        content: PDPageContentStream,
        box: PageBox,
        rotation: Int,
        mark: TextEditMark,
        fonts: HashMap<String, PDFont>,
        images: IdentityHashMap<Bitmap, PDImageXObject>,
    ): Boolean {
        val word = mark.word
        val displayedWidth = PdfCoordinateMapper.displayedWidth(box, rotation)
        val displayedHeight = if (PdfCoordinateMapper.normalizeRotation(rotation) % 180 == 0) box.height else box.width
        val sizePt = word.fontSizeFrac * displayedWidth
        val padU = sizePt * COVER_SIDE_PAD / displayedWidth
        val oldWidthPt = (word.u1 - word.u0) * displayedWidth

        val face = mark.face
        val bitmap: Bitmap?
        val pdfFont: PDFont?
        if (face != null) {
            bitmap = TextBitmaps.render(mark.newText, mark.textArgb, face = face)
            pdfFont = null
        } else {
            bitmap = null
            pdfFont = SystemFonts.pick(document, FontGuessing.guess(word.fontName), mark.newText, fonts) ?: return false
        }

        drawFill(
            content,
            box,
            rotation,
            FillRectMark(word.u0 - padU, word.v0, word.u1 + padU, word.v1, mark.backgroundArgb),
        )

        if (bitmap != null) {
            val heightPt = sizePt * HANDWRITING_SCALE * bitmap.height / TEXT_BITMAP_EM
            val widthPt = heightPt * bitmap.width / bitmap.height
            val stamp = StampMark(
                bitmap = bitmap,
                centerU = word.u0 + widthPt / 2f / displayedWidth,
                centerV = word.baselineV - sizePt * 0.3f / displayedHeight,
                widthFraction = widthPt / displayedWidth,
            )
            drawStamp(document, content, box, rotation, stamp, images)
            return true
        }

        val font = pdfFont ?: return false
        val naturalPt = font.getStringWidth(mark.newText) / 1000f * sizePt
        val sameLength = mark.newText.length == word.text.length
        val stretch = if (sameLength && naturalPt > 0f) (oldWidthPt / naturalPt).coerceIn(0.85f, 1.15f) else 1f
        val origin = PdfCoordinateMapper.point(box, rotation, word.u0, word.baselineV)
        val along = PdfCoordinateMapper.point(box, rotation, word.u0 + 0.1f, word.baselineV)
        val angle = Math.atan2((along.y - origin.y).toDouble(), (along.x - origin.x).toDouble())
        content.saveGraphicsState()
        content.transform(Matrix.getRotateInstance(angle, origin.x, origin.y))
        content.setNonStrokingColor(Color.red(mark.textArgb), Color.green(mark.textArgb), Color.blue(mark.textArgb))
        content.beginText()
        content.setFont(font, sizePt)
        content.setTextMatrix(Matrix(stretch, 0f, 0f, 1f, 0f, 0f))
        content.showText(mark.newText)
        content.endText()
        content.restoreGraphicsState()
        return true
    }

    /** Text size used when rendering bitmaps in [TextBitmaps] (pixels per em). */
    private const val TEXT_BITMAP_EM = 96f
}
