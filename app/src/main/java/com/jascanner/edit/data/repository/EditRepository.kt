package com.jascanner.edit.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jascanner.edit.domain.model.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs

class EditRepository(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun performOCR(bitmap: Bitmap): List<TextBlock> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer.process(inputImage).await()

            result.textBlocks.map { block ->
                TextBlock(
                    id = UUID.randomUUID().toString(),
                    text = block.text,
                    boundingBox = block.boundingBox?.let {
                        android.graphics.RectF(
                            it.left.toFloat(),
                            it.top.toFloat(),
                            it.right.toFloat(),
                            it.bottom.toFloat()
                        )
                    } ?: android.graphics.RectF(),
                    confidence = block.confidence ?: 0f
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun cropBitmap(
        bitmap: Bitmap,
        cropRect: android.graphics.RectF
    ): Bitmap = withContext(Dispatchers.IO) {
        val x = cropRect.left.toInt().coerceIn(0, bitmap.width)
        val y = cropRect.top.toInt().coerceIn(0, bitmap.height)
        val width = (cropRect.width().toInt()).coerceIn(1, bitmap.width - x)
        val height = (cropRect.height().toInt()).coerceIn(1, bitmap.height - y)

        Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    suspend fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap = withContext(Dispatchers.IO) {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun applyPerspectiveCorrection(
        bitmap: Bitmap,
        corners: List<PointF>
    ): Bitmap = withContext(Dispatchers.IO) {
        // Using simple affine transform as OpenCV integration would require native libs
        // For full perspective correction, integrate OpenCV's getPerspectiveTransform + warpPerspective
        bitmap // Placeholder - implement OpenCV integration
    }

    suspend fun exportSearchablePDF(
        document: EditableDocument,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PDDocument()

            document.pages.forEach { page ->
                val bitmap = loadBitmap(page.processedImageUri ?: page.originalImageUri)

                // Create page with bitmap dimensions
                val widthPts = bitmap.width * 72f / 300f // Assuming 300 DPI
                val heightPts = bitmap.height * 72f / 300f
                val pdPage = PDPage(PDRectangle(widthPts, heightPts))
                pdfDocument.addPage(pdPage)

                val contentStream = PDPageContentStream(
                    pdfDocument,
                    pdPage,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                // Draw image as background
                val pdImage = LosslessFactory.createFromImage(pdfDocument, bitmap)
                contentStream.drawImage(pdImage, 0f, 0f, widthPts, heightPts)

                // Add invisible OCR text layer
                page.ocrTextLayer.forEach { textBlock ->
                    val text = if (textBlock.isEdited) {
                        textBlock.editedText ?: textBlock.text
                    } else {
                        textBlock.text
                    }

                    addInvisibleText(
                        contentStream,
                        pdfDocument,
                        text,
                        textBlock.boundingBox,
                        bitmap.width,
                        bitmap.height,
                        widthPts,
                        heightPts
                    )
                }

                // Flatten annotations
                page.annotations.forEach { annotation ->
                    when (annotation) {
                        is Annotation.InkAnnotation -> drawInkAnnotation(contentStream, annotation, widthPts, heightPts, bitmap)
                        is Annotation.TextAnnotation -> drawTextAnnotation(contentStream, pdfDocument, annotation, widthPts, heightPts, bitmap)
                        is Annotation.HighlightAnnotation -> drawHighlight(contentStream, annotation, widthPts, heightPts, bitmap)
                        is Annotation.RedactionAnnotation -> drawRedaction(contentStream, annotation, widthPts, heightPts, bitmap)
                        is Annotation.SignatureAnnotation -> drawSignature(contentStream, pdfDocument, annotation, widthPts, heightPts, bitmap)
                    }
                }

                contentStream.close()
            }

            pdfDocument.save(outputFile)
            pdfDocument.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addInvisibleText(
        contentStream: PDPageContentStream,
        document: PDDocument,
        text: String,
        boundingBox: android.graphics.RectF,
        imageWidth: Int,
        imageHeight: Int,
        pageWidthPts: Float,
        pageHeightPts: Float
    ) {
        try {
            // Scale coordinates from image space to PDF space
            val scaleX = pageWidthPts / imageWidth
            val scaleY = pageHeightPts / imageHeight

            val x = boundingBox.left * scaleX
            val y = pageHeightPts - (boundingBox.bottom * scaleY) // PDF coords are bottom-up
            val fontSize = (boundingBox.height() * scaleY) * 0.8f

            contentStream.beginText()
            contentStream.setNonStrokingColor(255, 255, 255) // White (invisible on white bg)
            contentStream.setRenderingMode(PDPageContentStream.RenderingMode.NEITHER) // Invisible
            contentStream.newLineAtOffset(x, y)
            contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, fontSize.coerceIn(1f, 72f))
            contentStream.showText(text)
            contentStream.endText()
        } catch (e: Exception) {
            // Skip problematic text blocks
        }
    }

    private fun drawInkAnnotation(
        contentStream: PDPageContentStream,
        annotation: Annotation.InkAnnotation,
        pageWidthPts: Float,
        pageHeightPts: Float,
        bitmap: Bitmap
    ) {
        if (annotation.points.isEmpty()) return

        val scaleX = pageWidthPts / bitmap.width
        val scaleY = pageHeightPts / bitmap.height

        contentStream.saveGraphicsState()
        contentStream.setLineWidth(annotation.strokeWidth * scaleX)
        contentStream.setStrokingColor(
            android.graphics.Color.red(annotation.color),
            android.graphics.Color.green(annotation.color),
            android.graphics.Color.blue(annotation.color)
        )

        val firstPoint = annotation.points.first()
        contentStream.moveTo(
            firstPoint.x * scaleX,
            pageHeightPts - (firstPoint.y * scaleY)
        )

        annotation.points.drop(1).forEach { point ->
            contentStream.lineTo(
                point.x * scaleX,
                pageHeightPts - (point.y * scaleY)
            )
        }

        contentStream.stroke()
        contentStream.restoreGraphicsState()
    }

    private fun drawTextAnnotation(
        contentStream: PDPageContentStream,
        document: PDDocument,
        annotation: Annotation.TextAnnotation,
        pageWidthPts: Float,
        pageHeightPts: Float,
        bitmap: Bitmap
    ) {
        val scaleX = pageWidthPts / bitmap.width
        val scaleY = pageHeightPts / bitmap.height

        val x = annotation.boundingBox.left * scaleX
        val y = pageHeightPts - (annotation.boundingBox.bottom * scaleY)

        contentStream.beginText()
        contentStream.setNonStrokingColor(
            android.graphics.Color.red(annotation.color),
            android.graphics.Color.green(annotation.color),
            android.graphics.Color.blue(annotation.color)
        )
        contentStream.newLineAtOffset(x, y)
        contentStream.setFont(
            com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,
            annotation.fontSize * scaleY
        )
        contentStream.showText(annotation.text)
        contentStream.endText()
    }

    private fun drawHighlight(
        contentStream: PDPageContentStream,
        annotation: Annotation.HighlightAnnotation,
        pageWidthPts: Float,
        pageHeightPts: Float,
        bitmap: Bitmap
    ) {
        val scaleX = pageWidthPts / bitmap.width
        val scaleY = pageHeightPts / bitmap.height

        contentStream.saveGraphicsState()
        contentStream.setNonStrokingColor(
            android.graphics.Color.red(annotation.color),
            android.graphics.Color.green(annotation.color),
            android.graphics.Color.blue(annotation.color)
        )

        val rect = annotation.boundingBox
        contentStream.addRect(
            rect.left * scaleX,
            pageHeightPts - (rect.bottom * scaleY),
            rect.width() * scaleX,
            rect.height() * scaleY
        )
        contentStream.fill()
        contentStream.restoreGraphicsState()
    }

    private fun drawRedaction(
        contentStream: PDPageContentStream,
        annotation: Annotation.RedactionAnnotation,
        pageWidthPts: Float,
        pageHeightPts: Float,
        bitmap: Bitmap
    ) {
        val scaleX = pageWidthPts / bitmap.width
        val scaleY = pageHeightPts / bitmap.height

        contentStream.saveGraphicsState()
        contentStream.setNonStrokingColor(0, 0, 0) // Black

        val rect = annotation.boundingBox
        contentStream.addRect(
            rect.left * scaleX,
            pageHeightPts - (rect.bottom * scaleY),
            rect.width() * scaleX,
            rect.height() * scaleY
        )
        contentStream.fill()
        contentStream.restoreGraphicsState()
    }

    private fun drawSignature(
        contentStream: PDPageContentStream,
        document: PDDocument,
        annotation: Annotation.SignatureAnnotation,
        pageWidthPts: Float,
        pageHeightPts: Float,
        bitmap: Bitmap
    ) {
        try {
            val signatureBitmap = loadBitmap(annotation.signatureImageUri)
            val scaleX = pageWidthPts / bitmap.width
            val scaleY = pageHeightPts / bitmap.height

            val pdImage = LosslessFactory.createFromImage(document, signatureBitmap)
            val rect = annotation.boundingBox

            contentStream.drawImage(
                pdImage,
                rect.left * scaleX,
                pageHeightPts - (rect.bottom * scaleY),
                rect.width() * scaleX,
                rect.height() * scaleY
            )
        } catch (e: Exception) {
            // Skip if signature image cannot be loaded
        }
    }

    private fun loadBitmap(uriString: String): Bitmap {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "content" || uri.scheme == "file") {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: throw IllegalArgumentException("Cannot load bitmap from $uriString")
        } else {
            BitmapFactory.decodeFile(uriString)
        }
    }

    suspend fun saveBitmap(bitmap: Bitmap, filename: String): String = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    }
}