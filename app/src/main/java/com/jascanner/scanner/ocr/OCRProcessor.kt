package com.jascanner.scanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class OCRProcessor @Inject constructor(
    private val context: Context
) {
    
    data class OCRResult(
        val text: String,
        val confidence: Float,
        val blocks: List<TextBlock>,
        val processingTimeMs: Long,
        val success: Boolean,
        val error: String? = null
    )

    data class TextBlock(
        val text: String,
        val confidence: Float,
        val boundingBox: Rect,
        val lines: List<TextLine>
    )

    data class TextLine(
        val text: String,
        val confidence: Float,
        val boundingBox: Rect,
        val elements: List<TextElement>
    )

    data class TextElement(
        val text: String,
        val confidence: Float,
        val boundingBox: Rect
    )

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val aiEnhancer = AIEnhancer(context)

    suspend fun processImage(bitmap: Bitmap, enhanceWithAI: Boolean = true): OCRResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Enhance image with AI if requested
            val processedBitmap = if (enhanceWithAI) {
                aiEnhancer.enhanceForOCR(bitmap)
            } else {
                bitmap
            }

            val inputImage = InputImage.fromBitmap(processedBitmap, 0)
            val visionText = recognizeText(inputImage)
            
            val blocks = visionText.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    confidence = block.confidence ?: 0f,
                    boundingBox = block.boundingBox ?: Rect(),
                    lines = block.lines.map { line ->
                        TextLine(
                            text = line.text,
                            confidence = line.confidence ?: 0f,
                            boundingBox = line.boundingBox ?: Rect(),
                            elements = line.elements.map { element ->
                                TextElement(
                                    text = element.text,
                                    confidence = element.confidence ?: 0f,
                                    boundingBox = element.boundingBox ?: Rect()
                                )
                            }
                        )
                    }
                )
            }

            val fullText = visionText.text
            val avgConfidence = blocks.mapNotNull { it.confidence.takeIf { conf -> conf > 0f } }
                .average().toFloat().takeIf { !it.isNaN() } ?: 0f

            val processingTime = System.currentTimeMillis() - startTime

            OCRResult(
                text = fullText,
                confidence = avgConfidence,
                blocks = blocks,
                processingTimeMs = processingTime,
                success = true
            )

        } catch (e: Exception) {
            Timber.e(e, "OCR processing failed")
            OCRResult(
                text = "",
                confidence = 0f,
                blocks = emptyList(),
                processingTimeMs = System.currentTimeMillis() - startTime,
                success = false,
                error = e.message
            )
        }
    }

    private suspend fun recognizeText(inputImage: InputImage): com.google.mlkit.vision.text.Text {
        return suspendCancellableCoroutine { continuation ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText)
                }
                .addOnFailureListener { e ->
                    continuation.cancel(e)
                }
        }
    }

    fun extractSpecificPatterns(text: String): Map<String, List<String>> {
        val patterns = mutableMapOf<String, List<String>>()
        
        // Email addresses
        val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        patterns["emails"] = emailPattern.findAll(text).map { it.value }.toList()
        
        // Phone numbers
        val phonePattern = Regex("\\+?[1-9]\\d{1,14}|\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")
        patterns["phones"] = phonePattern.findAll(text).map { it.value }.toList()
        
        // URLs
        val urlPattern = Regex("https?://[\\w.-]+(?:\\.[\\w\\.-]+)+[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=.]+")
        patterns["urls"] = urlPattern.findAll(text).map { it.value }.toList()
        
        // Dates (basic patterns)
        val datePattern = Regex("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{2,4}[/.-]\\d{1,2}[/.-]\\d{1,2}")
        patterns["dates"] = datePattern.findAll(text).map { it.value }.toList()
        
        // Numbers (currency, amounts)
        val currencyPattern = Regex("\\$\\d+(?:,\\d{3})*(?:\\.\\d{2})?|\\d+(?:,\\d{3})*(?:\\.\\d{2})?\\s*(?:USD|EUR|GBP|CAD)")
        patterns["currency"] = currencyPattern.findAll(text).map { it.value }.toList()
        
        return patterns
    }

    fun analyzeDocumentStructure(blocks: List<TextBlock>): DocumentStructure {
        val lines = blocks.flatMap { it.lines }.sortedBy { it.boundingBox.top }
        
        val headers = lines.filter { line ->
            line.confidence > 0.8f && 
            line.text.length < 100 && 
            line.text.matches(Regex("^[A-Z][A-Za-z\\s]+$"))
        }
        
        val paragraphs = groupIntoParagraphs(lines)
        val tables = detectTables(blocks)
        
        return DocumentStructure(
            headers = headers.map { it.text },
            paragraphs = paragraphs,
            tables = tables,
            totalLines = lines.size,
            averageConfidence = blocks.mapNotNull { it.confidence.takeIf { conf -> conf > 0f } }
                .average().toFloat().takeIf { !it.isNaN() } ?: 0f
        )
    }

    private fun groupIntoParagraphs(lines: List<TextLine>): List<String> {
        val paragraphs = mutableListOf<String>()
        var currentParagraph = StringBuilder()
        var lastBottom = 0
        
        for (line in lines) {
            val gap = line.boundingBox.top - lastBottom
            
            if (gap > 50 && currentParagraph.isNotEmpty()) { // New paragraph threshold
                paragraphs.add(currentParagraph.toString().trim())
                currentParagraph.clear()
            }
            
            if (currentParagraph.isNotEmpty()) {
                currentParagraph.append(" ")
            }
            currentParagraph.append(line.text)
            lastBottom = line.boundingBox.bottom
        }
        
        if (currentParagraph.isNotEmpty()) {
            paragraphs.add(currentParagraph.toString().trim())
        }
        
        return paragraphs
    }

    private fun detectTables(blocks: List<TextBlock>): List<Table> {
        // Simplified table detection based on alignment
        val tables = mutableListOf<Table>()
        
        val alignedBlocks = blocks.groupBy { it.boundingBox.left }
            .filter { it.value.size >= 2 } // At least 2 rows
        
        for ((_, columnBlocks) in alignedBlocks) {
            val sortedBlocks = columnBlocks.sortedBy { it.boundingBox.top }
            val rows = mutableListOf<List<String>>()
            
            for (block in sortedBlocks) {
                val cells = block.lines.map { it.text }
                rows.add(cells)
            }
            
            if (rows.size >= 2) {
                tables.add(Table(rows = rows))
            }
        }
        
        return tables
    }

    data class DocumentStructure(
        val headers: List<String>,
        val paragraphs: List<String>,
        val tables: List<Table>,
        val totalLines: Int,
        val averageConfidence: Float
    )

    data class Table(
        val rows: List<List<String>>
    )

    fun cleanup() {
        textRecognizer.close()
    }
}