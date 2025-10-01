package com.jascanner.editor

import com.jascanner.domain.model.EditableDocument
import java.util.*

class UndoRedoManager(
    private val maxStackSize: Int = 50
) {
    private val undoStack = Stack<DocumentState>()
    private val redoStack = Stack<DocumentState>()

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun saveState(document: EditableDocument, operationName: String) {
        val state = DocumentState(
            document = document.copy(),
            operationName = operationName,
            timestamp = System.currentTimeMillis()
        )

        undoStack.push(state)
        redoStack.clear() // Clear redo stack on new operation

        // Limit stack size
        if (undoStack.size > maxStackSize) {
            undoStack.removeAt(0)
        }
    }

    fun undo(currentDocument: EditableDocument): UndoRedoResult {
        if (undoStack.isEmpty()) {
            return UndoRedoResult.NoStateAvailable
        }

        // Save current state to redo stack
        redoStack.push(DocumentState(
            document = currentDocument.copy(),
            operationName = "Current State",
            timestamp = System.currentTimeMillis()
        ))

        val previousState = undoStack.pop()
        return UndoRedoResult.Success(
            document = previousState.document,
            operationName = previousState.operationName
        )
    }

    fun redo(): UndoRedoResult {
        if (redoStack.isEmpty()) {
            return UndoRedoResult.NoStateAvailable
        }

        val nextState = redoStack.pop()
        undoStack.push(nextState)

        return UndoRedoResult.Success(
            document = nextState.document,
            operationName = nextState.operationName
        )
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    data class DocumentState(
        val document: EditableDocument,
        val operationName: String,
        val timestamp: Long
    )

    sealed class UndoRedoResult {
        data class Success(
            val document: EditableDocument,
            val operationName: String
        ) : UndoRedoResult()

        object NoStateAvailable : UndoRedoResult()
    }
}