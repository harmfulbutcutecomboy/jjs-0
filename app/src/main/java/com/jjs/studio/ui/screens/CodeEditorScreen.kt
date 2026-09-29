package com.jjs.studio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.ui.components.SleekCard
import com.jjs.studio.ui.theme.*
import com.jjs.studio.util.ZstdHelper
import com.jjs.studio.viewmodel.JjsUiState
import com.jjs.studio.viewmodel.JjsViewModel

@Composable
fun CodeEditorScreen(
    uiState: JjsUiState,
    viewModel: JjsViewModel,
    onShowStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val codeText = uiState.codeEditorText
    val isKluv = remember(codeText) { ZstdHelper.isKluv(codeText.trim()) }
    val isJson = remember(codeText) {
        val t = codeText.trim()
        t.startsWith("{") || t.startsWith("[")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BYTECODE & JSON",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedGray,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Import Code Editor",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = StarkWhite
                )
            }

            // Quick Paste button
            Button(
                onClick = {
                    val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!clip.isNullOrBlank()) {
                        viewModel.updateCodeEditorText(clip)
                        onShowStatus("Pasted from clipboard")
                    } else {
                        onShowStatus("Clipboard is empty")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161922)),
                border = BorderStroke(1.dp, Color(0xFF2C3140)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("code_editor_paste_btn")
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = StarkWhite, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste", fontSize = 12.sp, color = StarkWhite)
            }
        }

        // Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B0D12))
                .border(BorderStroke(1.dp, Color(0xFF1E222D)), RoundedCornerShape(14.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Load from Studio (JSON)
            FilledTonalButton(
                onClick = { viewModel.setCodeFromCurrentSkill(formatAsKluv = false) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF151820)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Pull JSON", fontSize = 11.sp, color = StarkWhite, maxLines = 1)
            }

            // Load from Studio (KLUv)
            FilledTonalButton(
                onClick = { viewModel.setCodeFromCurrentSkill(formatAsKluv = true) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF151820)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Pull KLUv", fontSize = 11.sp, color = StarkWhite, maxLines = 1)
            }

            // Format JSON
            FilledTonalButton(
                onClick = { viewModel.formatJsonInEditor() },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF151820)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Format", fontSize = 11.sp, color = StarkWhite, maxLines = 1)
            }

            // Clear
            IconButton(
                onClick = { viewModel.updateCodeEditorText("") },
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1D27))
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MutedGray, modifier = Modifier.size(16.dp))
            }
        }

        // Code Editor Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 460.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF06070A), Color(0xFF090B10))
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFF2C3242), Color(0xFF151821), Color(0xFF384357))
                        )
                    ),
                    RoundedCornerShape(18.dp)
                )
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar inside editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isKluv -> Color(0xFF38BDF8) // Cyan for KLUv
                                    isJson -> Color(0xFF22C55E) // Green for JSON
                                    codeText.isNotBlank() -> Color(0xFFF59E0B) // Amber
                                    else -> Color(0xFF64748B)
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isKluv -> "KLUv BYTECODE (${codeText.length} chars)"
                            isJson -> "JJS JSON SPECIFICATION"
                            codeText.isNotBlank() -> "RAW TEXT"
                            else -> "EMPTY BUFFER"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.weight(1f))

                    // Copy button
                    if (codeText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("JJS Code", codeText))
                                onShowStatus("Copied editor text to clipboard")
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = StarkWhite, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF161922), thickness = 0.8.dp)
                Spacer(Modifier.height(8.dp))

                // Scrollable Text Field
                BasicTextField(
                    value = codeText,
                    onValueChange = { viewModel.updateCodeEditorText(it) },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("code_editor_input"),
                    decorationBox = { innerTextField ->
                        if (codeText.isEmpty()) {
                            Text(
                                text = "// Paste JJS JSON or KLUv/... bytecode here to inspect and decompile\n// e.g. [{\"SkillName\":\"MySkill\",\"Duration\":2.0,\"Line\":[...]}]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF475569),
                                lineHeight = 18.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        // Status Card
        if (uiState.codeEditorStatus.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F121A))
                    .border(BorderStroke(1.dp, Color(0xFF222838)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = if (uiState.codeEditorStatus.startsWith("Error")) Color(0xFFEF4444) else Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = uiState.codeEditorStatus,
                    fontSize = 12.sp,
                    color = StarkWhite,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Primary Action: Decompile & Load to Studio
        Button(
            onClick = { viewModel.decodeAndLoadEditorCode() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("code_editor_decompile_button")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "DECOMPILE & LOAD TO STUDIO",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.Black,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
