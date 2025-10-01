package com.jascanner.scanner.pdf

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PDFGenerator @Inject constructor() {
    fun convertLegacyToPDFA(inFile: File, outFile: File): Boolean {
        // Placeholder implementation
        return true
    }
}