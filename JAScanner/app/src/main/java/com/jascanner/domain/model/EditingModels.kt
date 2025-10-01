package com.jascanner.domain.model

data class EditingPreset(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val isEditable: Boolean = true,
    val adjustments: ImageAdjustments
)