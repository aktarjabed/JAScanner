package com.jascanner.ai.compression

import java.io.File

interface DocumentCompressor {
    suspend fun compress(file: File): File
}