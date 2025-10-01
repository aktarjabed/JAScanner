package com.jascanner.domain.model

import android.graphics.Bitmap
import android.graphics.PointF

data class SignatureData(
    val id: String,
    val paths: List<List<PointF>>,
    val bitmap: Bitmap,
    val timestamp: Long,
    val signerName: String? = null
)

data class SavedSignature(
    val id: String,
    val paths: List<List<PointF>>,
    val timestamp: Long,
    val signerName: String?
)