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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun Mql5CodeViewer(
    code: String,
    botName: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    isEditable: Boolean = false,
    onCodeChange: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var fontSizeSp by remember { mutableFloatStateOf(12f) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }

    val lines = remember(code) { code.lines() }
    val lineCount = lines.size

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
                }
                count
            }
        }
    }

    val highlightedCode = remember(code, isDarkTheme, searchQuery) {
        Mql5SyntaxHighlighter.highlight(
            code = code,
            isDark = isDarkTheme,
            searchQuery = searchQuery
        )
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$botName.mq5",
                        color = titleColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$lineCount lines",
                        color = mutedColor,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Zoom Out
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
                    // Zoom In
                    IconButton(
                        onClick = { if (fontSizeSp < 22f) fontSizeSp += 1f },
                        modifier = Modifier.testTag("zoom_in_button")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = iconColor)
                    }

                    // Search toggle
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

                    // Copy to clipboard
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("MQL5 Code", code)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "MQL5 code copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_code_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = iconColor)
                    }

                    // Share as .mq5
                    IconButton(
                        onClick = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, code)
                                putExtra(Intent.EXTRA_TITLE, "$botName.mq5")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export $botName.mq5"))
                        },
                        modifier = Modifier.testTag("share_code_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Code", tint = iconColor)
                    }
                }
            }
        }

        // Search Bar row when active
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
                            text = "$matchCount matches",
                            color = if (matchCount > 0) MaterialTheme.colorScheme.primary else mutedColor,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Code and Line Numbers Container
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(verticalScroll)
        ) {
            // Line numbers column
            Column(
                modifier = Modifier
                    .background(gutterBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = "$i",
                        color = mutedColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.45f).sp
                    )
                }
            }

            // Vertical divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(dividerBg)
            )

            // Code text content (horizontal scrollable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (isEditable && onCodeChange != null) {
                    BasicTextField(
                        value = code,
                        onValueChange = onCodeChange,
                        textStyle = TextStyle(
                            color = titleColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.45f).sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("mql5_code_editor_field")
                    )
                } else {
                    Text(
                        text = highlightedCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.45f).sp
                    )
                }
            }
        }
    }
}
