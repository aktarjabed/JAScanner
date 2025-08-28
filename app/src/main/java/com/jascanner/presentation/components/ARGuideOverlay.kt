package com.jascanner.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jascanner.ui.theme.FocusIndicatorColor
import com.jascanner.ui.theme.ScannerActiveColor
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ARGuideOverlay(
    isScanning: Boolean = false,
    focusPoint: Offset? = null,
    documentBounds: Rect? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val strokeWidth = with(density) { 2.dp.toPx() }
    val animationProgress by rememberAnimationProgress(isScanning)
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Draw scan area guide
        drawScanAreaGuide(
            color = if (isScanning) ScannerActiveColor else Color.White,
            strokeWidth = strokeWidth
        )
        
        // Draw focus indicator
        focusPoint?.let { point ->
            drawFocusIndicator(
                center = point,
                color = FocusIndicatorColor,
                strokeWidth = strokeWidth,
                animationProgress = animationProgress
            )
        }
        
        // Draw document detection bounds
        documentBounds?.let { bounds ->
            drawDocumentBounds(
                bounds = bounds,
                color = ScannerActiveColor,
                strokeWidth = strokeWidth
            )
        }
        
        // Draw scanning animation
        if (isScanning) {
            drawScanningAnimation(
                progress = animationProgress,
                color = ScannerActiveColor,
                strokeWidth = strokeWidth
            )
        }
    }
}

@Composable
private fun rememberAnimationProgress(isAnimating: Boolean): Float {
    var progress by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            while (isAnimating) {
                progress = (progress + 0.02f) % 1f
                kotlinx.coroutines.delay(16) // ~60 FPS
            }
        } else {
            progress = 0f
        }
    }
    
    return progress
}

private fun DrawScope.drawScanAreaGuide(
    color: Color,
    strokeWidth: Float
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val guideWidth = size.width * 0.8f
    val guideHeight = size.height * 0.6f
    val cornerLength = 40f
    
    val left = centerX - guideWidth / 2
    val top = centerY - guideHeight / 2
    val right = centerX + guideWidth / 2
    val bottom = centerY + guideHeight / 2
    
    // Top-left corner
    drawLine(
        color = color,
        start = Offset(left, top + cornerLength),
        end = Offset(left, top),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(left, top),
        end = Offset(left + cornerLength, top),
        strokeWidth = strokeWidth
    )
    
    // Top-right corner
    drawLine(
        color = color,
        start = Offset(right - cornerLength, top),
        end = Offset(right, top),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(right, top),
        end = Offset(right, top + cornerLength),
        strokeWidth = strokeWidth
    )
    
    // Bottom-left corner
    drawLine(
        color = color,
        start = Offset(left, bottom - cornerLength),
        end = Offset(left, bottom),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(left, bottom),
        end = Offset(left + cornerLength, bottom),
        strokeWidth = strokeWidth
    )
    
    // Bottom-right corner
    drawLine(
        color = color,
        start = Offset(right - cornerLength, bottom),
        end = Offset(right, bottom),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(right, bottom),
        end = Offset(right, bottom - cornerLength),
        strokeWidth = strokeWidth
    )
}

private fun DrawScope.drawFocusIndicator(
    center: Offset,
    color: Color,
    strokeWidth: Float,
    animationProgress: Float
) {
    val radius = 30f + animationProgress * 10f
    val alpha = 1f - animationProgress
    
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth)
    )
    
    // Draw crosshair
    val crosshairLength = 20f
    drawLine(
        color = color,
        start = Offset(center.x - crosshairLength, center.y),
        end = Offset(center.x + crosshairLength, center.y),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - crosshairLength),
        end = Offset(center.x, center.y + crosshairLength),
        strokeWidth = strokeWidth
    )
}

private fun DrawScope.drawDocumentBounds(
    bounds: Rect,
    color: Color,
    strokeWidth: Float
) {
    val path = Path().apply {
        moveTo(bounds.left, bounds.top)
        lineTo(bounds.right, bounds.top)
        lineTo(bounds.right, bounds.bottom)
        lineTo(bounds.left, bounds.bottom)
        close()
    }
    
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth)
    )
    
    // Draw corner indicators
    val cornerSize = 20f
    val corners = listOf(
        Offset(bounds.left, bounds.top),
        Offset(bounds.right, bounds.top),
        Offset(bounds.right, bounds.bottom),
        Offset(bounds.left, bounds.bottom)
    )
    
    corners.forEach { corner ->
        drawCircle(
            color = color,
            radius = cornerSize / 2,
            center = corner,
            style = Stroke(width = strokeWidth)
        )
    }
}

private fun DrawScope.drawScanningAnimation(
    progress: Float,
    color: Color,
    strokeWidth: Float
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val maxRadius = kotlin.math.min(size.width, size.height) / 2
    
    // Ripple effect
    for (i in 0..2) {
        val rippleProgress = (progress + i * 0.33f) % 1f
        val radius = rippleProgress * maxRadius
        val alpha = 1f - rippleProgress
        
        drawCircle(
            color = color.copy(alpha = alpha * 0.3f),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )
    }
    
    // Rotating scanner beam
    val beamLength = maxRadius * 0.8f
    val angle = progress * 2 * kotlin.math.PI
    val beamEnd = Offset(
        centerX + cos(angle).toFloat() * beamLength,
        centerY + sin(angle).toFloat() * beamLength
    )
    
    drawLine(
        color = color.copy(alpha = 0.7f),
        start = Offset(centerX, centerY),
        end = beamEnd,
        strokeWidth = strokeWidth * 2
    )
}