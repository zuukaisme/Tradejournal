package com.example.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.usecase.Mql5SyntaxHighlighter
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceElevated

internal const val LARGE_EDITOR_THRESHOLD_CHARS = 24_000
internal const val LARGE_EDITOR_THRESHOLD_LINES = 350
internal const val CHUNK_EDITOR_PAGE_LINES = 150
internal const val MAX_CLIPBOARD_OR_SHARE_CHARS = 250_000

internal fun isLargeMql5Code(code: String, lineCount: Int): Boolean {
    return code.length > LARGE_EDITOR_THRESHOLD_CHARS || lineCount > LARGE_EDITOR_THRESHOLD_LINES
}

internal fun spliceMql5Lines(
    lines: List<String>,
    startIndex: Int,
    endExclusive: Int,
    replacementChunk: String
): String {
    val safeStart = startIndex.coerceIn(0, lines.size)
    val safeEnd = endExclusive.coerceIn(safeStart, lines.size)
    val prefix = lines.subList(0, safeStart)
    val middle = replacementChunk.replace("\r\n", "\n").replace("\r", "\n").split('\n')
    val suffix = lines.subList(safeEnd, lines.size)
    val total = ArrayList<String>(prefix.size + middle.size + suffix.size)
    total.addAll(prefix)
    total.addAll(middle)
    total.addAll(suffix)
    return total.joinToString("\n")
}

@Composable
fun Mql5CodeViewer(
    code: String,
    botName: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    isEditable: Boolean = false,
    onCodeChange: ((String) -> Unit)? = null,
    sourceKey: Any? = null
) {
    val context = LocalContext.current
    var fontSizeSp by remember { mutableFloatStateOf(12f) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }

    val lines = remember(code) { code.lines() }
    val lineCount = lines.size
    val isLargeFile = isLargeMql5Code(code, lineCount)

    var allowLargeEdit by remember(isLargeFile, sourceKey) { mutableStateOf(false) }
    var chunkStartLine by remember(isLargeFile, sourceKey) { mutableIntStateOf(0) }

    LaunchedEffect(lineCount) {
        if (chunkStartLine >= lineCount) {
            chunkStartLine = ((lineCount - 1).coerceAtLeast(0) / CHUNK_EDITOR_PAGE_LINES) * CHUNK_EDITOR_PAGE_LINES
        }
    }

    val effectiveEditable = isEditable && onCodeChange != null && (!isLargeFile || allowLargeEdit)

    val matchCount by remember(code, searchQuery) {
        derivedStateOf {
            if (searchQuery.length < 2) 0
            else {
                var count = 0
                var idx = 0
                while (idx < code.length) {
                    val found = code.indexOf(searchQuery, idx, ignoreCase = true)
                    if (found == -1) break
                    count++
                    idx = found + searchQuery.length
                    if (count >= 999) break
                }
                count
            }
        }
    }

    val listState = rememberLazyListState()
    val editorVerticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val gutterWidthDp = remember(lineCount) {
        when {
            lineCount >= 10_000 -> 54.dp
            lineCount >= 1_000 -> 46.dp
            else -> 38.dp
        }
    }

    val editorBg = if (isDarkTheme) DarkBackground else Color(0xFFF8FAFC)
    val toolbarBg = if (isDarkTheme) DarkSurfaceElevated else Color(0xFFE7EDF4)
    val gutterBg = if (isDarkTheme) Color(0xFF080E18) else Color(0xFFEEF2F6)
    val dividerBg = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val titleColor = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val mutedColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val iconColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF334155)
    val searchBg = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val searchFieldBg = if (isDarkTheme) Color(0xFF1E293B) else Color.White

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(editorBg, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        // Toolbar with zoom, search, copy, share
        Surface(
            color = toolbarBg,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$botName.mq5",
                        color = titleColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$lineCount lines",
                        color = mutedColor,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (fontSizeSp > 8f) fontSizeSp -= 1f },
                        modifier = Modifier.testTag("zoom_out_button")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = iconColor)
                    }
                    Text(
                        text = "${fontSizeSp.toInt()}sp",
                        color = mutedColor,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = { if (fontSizeSp < 22f) fontSizeSp += 1f },
                        modifier = Modifier.testTag("zoom_in_button")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = iconColor)
                    }

                    IconButton(
                        onClick = { isSearchOpen = !isSearchOpen },
                        modifier = Modifier.testTag("search_code_button")
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search In Code",
                            tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else iconColor
                        )
                    }

                    IconButton(
                        onClick = {
                            if (code.length > MAX_CLIPBOARD_OR_SHARE_CHARS) {
                                Toast.makeText(
                                    context,
                                    "Bot exceeds Android clipboard limit (>250 KB). Use a smaller section.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                runCatching {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("MQL5 Code", code)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "MQL5 code copied to clipboard", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Unable to copy full bot to clipboard.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("copy_code_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = iconColor)
                    }

                    IconButton(
                        onClick = {
                            if (code.length > MAX_CLIPBOARD_OR_SHARE_CHARS) {
                                Toast.makeText(
                                    context,
                                    "Bot exceeds Android share intent limit (>250 KB).",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                runCatching {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, code)
                                        putExtra(Intent.EXTRA_TITLE, "$botName.mq5")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Export $botName.mq5"))
                                }.onFailure {
                                    Toast.makeText(context, "Unable to share bot source.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("share_code_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Code", tint = iconColor)
                    }
                }
            }
        }

        if (isEditable && isLargeFile && !allowLargeEdit) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Large file ($lineCount lines • ${code.length / 1024} KB): smooth preview active.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val visible = listState.firstVisibleItemIndex.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
                            chunkStartLine = (visible / CHUNK_EDITOR_PAGE_LINES) * CHUNK_EDITOR_PAGE_LINES
                            allowLargeEdit = true
                        }
                    ) {
                        Text("Edit Section", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (isEditable && isLargeFile && allowLargeEdit) {
            val chunkEnd = (chunkStartLine + CHUNK_EDITOR_PAGE_LINES).coerceAtMost(lineCount)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lines ${chunkStartLine + 1}–$chunkEnd / $lineCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                chunkStartLine = (chunkStartLine - CHUNK_EDITOR_PAGE_LINES).coerceAtLeast(0)
                            },
                            enabled = chunkStartLine > 0
                        ) {
                            Text("Prev", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = {
                                val nextStart = chunkStartLine + CHUNK_EDITOR_PAGE_LINES
                                if (nextStart < lineCount) chunkStartLine = nextStart
                            },
                            enabled = chunkEnd < lineCount
                        ) {
                            Text("Next", fontSize = 11.sp)
                        }
                        TextButton(onClick = { allowLargeEdit = false }) {
                            Text("Preview", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (isSearchOpen) {
            Surface(
                color = searchBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search keyword, function, variable...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("code_search_input"),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, color = titleColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = dividerBg,
                            focusedContainerColor = searchFieldBg,
                            unfocusedContainerColor = searchFieldBg
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = mutedColor)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (searchQuery.length >= 2) {
                        Text(
                            text = if (matchCount >= 999) "999+ matches" else "$matchCount matches",
                            color = if (matchCount > 0) MaterialTheme.colorScheme.primary else mutedColor,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (effectiveEditable) {
            val editStartIndex = if (isLargeFile) chunkStartLine.coerceIn(0, (lineCount - 1).coerceAtLeast(0)) else 0
            val editEndExclusive = if (isLargeFile) (editStartIndex + CHUNK_EDITOR_PAGE_LINES).coerceAtMost(lineCount) else lineCount
            val editableText = remember(code, isLargeFile, editStartIndex, editEndExclusive) {
                if (!isLargeFile) {
                    code
                } else if (lines.isEmpty()) {
                    ""
                } else {
                    lines.subList(editStartIndex, editEndExclusive).joinToString("\n")
                }
            }
            val editableLineCount = remember(editableText) { editableText.count { it == '\n' } + 1 }
            val gutterNumbersText = remember(editStartIndex, editableLineCount) {
                val sb = StringBuilder(editableLineCount * 5)
                for (offset in 0 until editableLineCount) {
                    if (offset > 0) sb.append('\n')
                    sb.append(editStartIndex + offset + 1)
                }
                sb.toString()
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(editorVerticalScroll)
            ) {
                Box(
                    modifier = Modifier
                        .width(gutterWidthDp)
                        .background(gutterBg)
                        .drawBehind {
                            drawLine(
                                color = dividerBg,
                                start = Offset(size.width - 0.5f, 0f),
                                end = Offset(size.width - 0.5f, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        text = gutterNumbersText,
                        color = mutedColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.45f).sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = editableText,
                        onValueChange = { updated ->
                            if (!isLargeFile) {
                                onCodeChange!!(updated)
                            } else {
                                onCodeChange!!(spliceMql5Lines(lines, editStartIndex, editEndExclusive, updated))
                            }
                        },
                        textStyle = TextStyle(
                            color = titleColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.45f).sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minWidth = 240.dp, minHeight = 220.dp)
                            .testTag("mql5_code_editor_field")
                    )
                }
            }
        } else {
            // Virtualized LazyColumn keeps 4,000+ line / 5MB+ bots smooth at 60fps
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(count = lineCount, key = { it }) { idx ->
                    val lineText = lines[idx]
                    val highlightedLine = remember(lineText, isDarkTheme, searchQuery) {
                        val trimmed = if (lineText.length <= 2_000) lineText else lineText.take(2_000)
                        Mql5SyntaxHighlighter.highlight(
                            code = trimmed,
                            isDark = isDarkTheme,
                            searchQuery = searchQuery
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(gutterWidthDp)
                                .fillMaxHeight()
                                .background(gutterBg)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "${idx + 1}",
                                color = mutedColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.45f).sp,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(dividerBg)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScroll)
                                .padding(horizontal = 10.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = highlightedLine,
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.45f).sp,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
