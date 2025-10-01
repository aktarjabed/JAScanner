package com.jascanner.utils

import android.content.Context
import timber.log.Timber

object AssetValidator {

    private val requiredAssets = listOf(
        "fonts/arial.ttf"
    )

    private val optionalAssets = listOf(
        "tessdata/eng.traineddata",
        "models/font_detection.tflite"
    )

    fun validateAssets(context: Context): AssetValidationResult {
        val missingRequired = mutableListOf<String>()
        val missingOptional = mutableListOf<String>()

        requiredAssets.forEach { asset ->
            if (!assetExists(context, asset)) {
                missingRequired.add(asset)
                Timber.e("REQUIRED asset missing: $asset")
            }
        }

        optionalAssets.forEach { asset ->
            if (!assetExists(context, asset)) {
                missingOptional.add(asset)
                Timber.w("Optional asset missing: $asset")
            }
        }

        return AssetValidationResult(
            isValid = missingRequired.isEmpty(),
            missingRequired = missingRequired,
            missingOptional = missingOptional
        )
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return try {
            context.assets.open(path).use { true }
        } catch (e: Exception) {
            false
        }
    }

    data class AssetValidationResult(
        val isValid: Boolean,
        val missingRequired: List<String>,
        val missingOptional: List<String>
    )
}