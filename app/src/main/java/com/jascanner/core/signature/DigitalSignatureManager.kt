package com.jascanner.core.signature

import java.io.File

interface DigitalSignatureManager {
    suspend fun sign(file: File): File
}