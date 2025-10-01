package com.jascanner.presentation.editor.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jascanner.domain.model.*
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTextEditor(
    page: EditablePage,
    onTextUpdated: (String, List<TextFormatting>) -> Unit,
    onFindReplace: (FindReplaceOptions) -> Unit
) {
    var textValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = page.ocrTextLayer.joinToString("\n") {
                    it.editedText ?: it.text
                }
            )
        )
    }

    var showFindReplace by remember { mutableStateOf(false) }
    var showSpellCheck by remember { mutableStateOf(false) }
    var currentFormatting by remember { mutableStateOf(TextFormatting.default()) }
    var textFormattings by remember { mutableStateOf<List<TextFormattingSpan>>(emptyList()) }
    var spellCheckSuggestions by remember { mutableStateOf<List<SpellCheckSuggestion>>(emptyList()) }

    val scope = rememberCoroutineScope()

    // Run spell check
    LaunchedEffect(textValue.text) {
        if (showSpellCheck) {
            spellCheckSuggestions = performSpellCheck(textValue.text)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Formatting toolbar
        TextFormattingToolbar(
            formatting = currentFormatting,
            onFormattingChange = { newFormatting ->
                currentFormatting = newFormatting
                // applyFormatting(textValue.selection, newFormatting)
            },
            onFindReplaceClick = { showFindReplace = true },
            onSpellCheckClick = { showSpellCheck = !showSpellCheck },
            onUndo = { /* Implement undo */ },
            onRedo = { /* Implement redo */ }
        )

        Divider()

        // Find and replace bar
        if (showFindReplace) {
            FindReplaceBar(
                onDismiss = { showFindReplace = false },
                onFind = { query, options ->
                    val results = findInText(textValue.text, query, options)
                    // Highlight results
                },
                onReplace = { query, replacement, options ->
                    val newText = replaceInText(textValue.text, query, replacement, options)
                    textValue = textValue.copy(text = newText)
                    onTextUpdated(newText, textFormattings.map { it.formatting })
                }
            )
        }

        // Text editor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { newValue ->
                    textValue = newValue
                    scope.launch {
                        onTextUpdated(newValue.text, textFormattings.map { it.formatting })
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (textValue.text.isEmpty()) {
                            Text(
                                "Start typing or paste text here...",
                                style = LocalTextStyle.current.copy(
                                    color = Color.Gray
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Spell check underlines
            if (showSpellCheck && spellCheckSuggestions.isNotEmpty()) {
                SpellCheckOverlay(
                    suggestions = spellCheckSuggestions,
                    onSuggestionApplied = { suggestion, replacement ->
                        val newText = textValue.text.replaceRange(
                            suggestion.startIndex,
                            suggestion.endIndex,
                            replacement
                        )
                        textValue = textValue.copy(text = newText)
                    }
                )
            }
        }

        // Status bar
        TextEditorStatusBar(
            wordCount = textValue.text.split("\\s+".toRegex()).size,
            characterCount = textValue.text.length,
            spellIssues = if (showSpellCheck) spellCheckSuggestions.size else 0
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextFormattingToolbar(
    formatting: TextFormatting,
    onFormattingChange: (TextFormatting) -> Unit,
    onFindReplaceClick: () -> Unit,
    onSpellCheckClick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // First row - Font controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Font size
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = "${formatting.fontSize.toInt()}",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .width(80.dp)
                            .menuAnchor(),
                        label = { Text("Size") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(8, 10, 12, 14, 16, 18, 20, 24, 28, 32).forEach { size ->
                            DropdownMenuItem(
                                text = { Text("$size") },
                                onClick = {
                                    onFormattingChange(formatting.copy(fontSize = size.toFloat()))
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                VerticalDivider(modifier = Modifier.height(40.dp))

                // Bold
                IconToggleButton(
                    checked = formatting.isBold,
                    onCheckedChange = {
                        onFormattingChange(formatting.copy(isBold = it))
                    }
                ) {
                    Icon(Icons.Default.FormatBold, "Bold")
                }

                // Italic
                IconToggleButton(
                    checked = formatting.isItalic,
                    onCheckedChange = {
                        onFormattingChange(formatting.copy(isItalic = it))
                    }
                ) {
                    Icon(Icons.Default.FormatItalic, "Italic")
                }

                // Underline
                IconToggleButton(
                    checked = formatting.isUnderline,
                    onCheckedChange = {
                        onFormattingChange(formatting.copy(isUnderline = it))
                    }
                ) {
                    Icon(Icons.Default.FormatUnderlined, "Underline")
                }

                VerticalDivider(modifier = Modifier.height(40.dp))

                // Text color
                ColorPickerButton(
                    color = formatting.textColor,
                    onColorSelected = {
                        onFormattingChange(formatting.copy(textColor = it))
                    }
                )

                // Highlight color
                ColorPickerButton(
                    color = formatting.highlightColor,
                    onColorSelected = {
                        onFormattingChange(formatting.copy(highlightColor = it))
                    },
                    label = "Highlight"
                )
            }

            Spacer(Modifier.height(8.dp))

            // Second row - Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onUndo) {
                    Icon(Icons.Default.Undo, "Undo")
                }

                IconButton(onClick = onRedo) {
                    Icon(Icons.Default.Redo, "Redo")
                }

                VerticalDivider(modifier = Modifier.height(40.dp))

                Button(
                    onClick = onFindReplaceClick,
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.FindReplace, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Find & Replace")
                }

                Button(
                    onClick = onSpellCheckClick,
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Spellcheck, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Spell Check")
                }
            }
        }
    }
}

@Composable
private fun FindReplaceBar(
    onDismiss: () -> Unit,
    onFind: (String, FindOptions) -> Unit,
    onReplace: (String, String, FindOptions) -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Find & Replace", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("Find") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onFind(
                                findText,
                                FindOptions(caseSensitive, wholeWord, useRegex)
                            )
                        }
                    )
                )

                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Replace") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
                    Text("Case sensitive", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wholeWord, onCheckedChange = { wholeWord = it })
                    Text("Whole word", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useRegex, onCheckedChange = { useRegex = it })
                    Text("Regex", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onFind(findText, FindOptions(caseSensitive, wholeWord, useRegex))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Find All")
                }

                Button(
                    onClick = {
                        onReplace(
                            findText,
                            replaceText,
                            FindOptions(caseSensitive, wholeWord, useRegex)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FindReplace, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Replace All")
                }
            }
        }
    }
}

@Composable
private fun SpellCheckOverlay(
    suggestions: List<SpellCheckSuggestion>,
    onSuggestionApplied: (SpellCheckSuggestion, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed(suggestions) { index, suggestion ->
            SpellCheckSuggestionItem(
                suggestion = suggestion,
                onApply = { replacement ->
                    onSuggestionApplied(suggestion, replacement)
                }
            )
        }
    }
}

@Composable
private fun SpellCheckSuggestionItem(
    suggestion: SpellCheckSuggestion,
    onApply: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "\"${suggestion.word}\" - Did you mean:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                suggestion.suggestions.take(3).forEach { replacement ->
                    AssistChip(
                        onClick = { onApply(replacement) },
                        label = { Text(replacement) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPickerButton(
    color: Int,
    onColorSelected: (Int) -> Unit,
    label: String = "Color"
) {
    var showPicker by remember { mutableStateOf(false) }

    IconButton(onClick = { showPicker = true }) {
        Icon(
            Icons.Default.FormatColorFill,
            label,
            tint = Color(color)
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Select $label") },
            text = {
                Column {
                    listOf(
                        android.graphics.Color.BLACK to "Black",
                        android.graphics.Color.RED to "Red",
                        android.graphics.Color.BLUE to "Blue",
                        android.graphics.Color.GREEN to "Green",
                        android.graphics.Color.YELLOW to "Yellow"
                    ).forEach { (c, name) ->
                        TextButton(
                            onClick = {
                                onColorSelected(c)
                                showPicker = false
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(c))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun TextEditorStatusBar(
    wordCount: Int,
    characterCount: Int,
    spellIssues: Int
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Words: $wordCount",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Characters: $characterCount",
                style = MaterialTheme.typography.bodySmall
            )
            if (spellIssues > 0) {
                Text(
                    "Spelling issues: $spellIssues",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Helper functions

private fun findInText(text: String, query: String, options: FindOptions): List<TextRange> {
    if (query.isEmpty()) return emptyList()

    return try {
        val pattern = if (options.useRegex) {
            Regex(query, if (options.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))
        } else {
            val escapedQuery = Regex.escape(query)
            val boundedQuery = if (options.wholeWord) "\\b$escapedQuery\\b" else escapedQuery
            Regex(boundedQuery, if (options.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))
        }

        pattern.findAll(text).map { match ->
            TextRange(match.range.first, match.range.last + 1)
        }.toList()
    } catch (e: Exception) {
        Timber.e(e, "Error finding text")
        emptyList()
    }
}

private fun replaceInText(
    text: String,
    query: String,
    replacement: String,
    options: FindOptions
): String {
    return try {
        val pattern = if (options.useRegex) {
            Regex(query, if (options.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))
        } else {
            val escapedQuery = Regex.escape(query)
            val boundedQuery = if (options.wholeWord) "\\b$escapedQuery\\b" else escapedQuery
            Regex(boundedQuery, if (options.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))
        }

        pattern.replace(text, replacement)
    } catch (e: Exception) {
        Timber.e(e, "Error replacing text")
        text
    }
}

private suspend fun performSpellCheck(text: String): List<SpellCheckSuggestion> {
    // Simple spell check implementation
    // In production, integrate with Android SpellChecker or external API
    return try {
        val words = text.split("\\s+".toRegex())
        val misspelledWords = mutableListOf<SpellCheckSuggestion>()

        var currentIndex = 0
        words.forEach { word ->
            val cleanWord = word.replace(Regex("[^a-zA-Z]"), "")
            if (cleanWord.length > 3 && !isValidWord(cleanWord)) {
                misspelledWords.add(
                    SpellCheckSuggestion(
                        word = cleanWord,
                        startIndex = currentIndex,
                        endIndex = currentIndex + word.length,
                        suggestions = generateSuggestions(cleanWord)
                    )
                )
            }
            currentIndex += word.length + 1
        }

        misspelledWords
    } catch (e: Exception) {
        Timber.e(e, "Spell check failed")
        emptyList()
    }
}

private fun isValidWord(word: String): Boolean {
    // Implement dictionary check
    // For now, return true for all
    return true
}

private fun generateSuggestions(word: String): List<String> {
    // Generate spelling suggestions
    return listOf(word.lowercase(), word.replaceFirstChar { it.uppercase() })
}