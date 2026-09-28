package com.jjs.studio.ui.dialogs

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.model.NodeKind
import com.jjs.studio.ui.components.MonospaceCodeCard
import com.jjs.studio.ui.theme.*
import com.jjs.studio.viewmodel.JjsUiState
import com.jjs.studio.viewmodel.JjsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportModalSheet(
    viewModel: JjsViewModel,
    onDismiss: () -> Unit
) {
    var importText by remember { mutableStateOf("") }
    val clipboard: ClipboardManager = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1117),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF333644)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Load Import Code",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = StarkWhite
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val clip = clipboard.getText()?.text ?: ""
                        if (clip.isNotBlank()) importText = clip
                    }
                ) {
                    Text("Paste Clipboard", color = JjsAccentPink, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Paste a JJS Zstandard bytecode (starting with KLUv/...) or raw JSON skill array.",
                fontSize = 12.sp,
                color = MutedGray
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                placeholder = { Text("Paste JSON array or KLUv/…", fontSize = 12.sp, color = SubtleGray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("import_text_field"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StarkWhite,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = StarkWhite,
                    unfocusedTextColor = StarkWhite,
                    focusedContainerColor = Color(0xFF08090C),
                    unfocusedContainerColor = Color(0xFF08090C)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { importText = "" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedGray)
                ) {
                    Text("Clear")
                }

                Button(
                    onClick = {
                        if (importText.isNotBlank()) {
                            viewModel.loadImportCode(importText)
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("submit_import_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StarkWhite,
                        contentColor = BlackBackground
                    )
                ) {
                    Text("Parse & Load", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportModalSheet(
    uiState: JjsUiState,
    onDismiss: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1117),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF333644)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Export JJS Skill",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = StarkWhite
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, uiState.liveKluv)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share JJS KLUv Code")
                        context.startActivity(shareIntent)
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = StarkWhite)
                }
            }

            if (copiedNotice != null) {
                Text(copiedNotice!!, fontSize = 12.sp, color = JjsNeonGreen)
            } else {
                Text("Copy bytecode to paste into Jujutsu Shenanigans custom moves.", fontSize = 12.sp, color = MutedGray)
            }

            Spacer(Modifier.height(16.dp))

            MonospaceCodeCard(
                title = "Zstandard KLUv Bytecode",
                code = uiState.liveKluv,
                onCopy = {
                    clipboard.setText(AnnotatedString(uiState.liveKluv))
                    copiedNotice = "Copied KLUv bytecode!"
                },
                maxLines = 4
            )

            Spacer(Modifier.height(12.dp))

            MonospaceCodeCard(
                title = "JSON Skill Array",
                code = uiState.liveJson,
                onCopy = {
                    clipboard.setText(AnnotatedString(uiState.liveJson))
                    copiedNotice = "Copied JSON array!"
                },
                maxLines = 4
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(uiState.liveKluv))
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("export_copy_dismiss_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StarkWhite,
                    contentColor = BlackBackground
                )
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy KLUv & Close", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNodeModalSheet(
    onSelectKind: (NodeKind) -> Unit,
    onDismiss: () -> Unit
) {
    val kinds = listOf(
        NodeKind.PARTICLE to ("Particle" to "Texture, emitter rate, speed, color and spread"),
        NodeKind.VISUAL_MESH to ("Mesh Part" to "3D mesh model with position offset and size"),
        NodeKind.VISUAL_CAMERA to ("Camera" to "Field of View (FOV) and cinematic angle"),
        NodeKind.SFX to ("Sound FX" to "Roblox sound asset ID, volume, and pitch"),
        NodeKind.WAIT to ("Wait Delay" to "Time pause before executing next node"),
        NodeKind.CONNECT to ("Connect" to "Event trigger listener (Hit, Land, KeyUp)"),
        NodeKind.TAG to ("Tag" to "Status effect label (Stun, HyperArmor)"),
        NodeKind.BRANCH to ("Branch Jump" to "Jump execution to another named branch")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1117),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF333644)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Add Timeline Node",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = StarkWhite
            )
            Text(
                text = "Select an element to sequence into the active skill branch.",
                fontSize = 12.sp,
                color = MutedGray
            )

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(kinds) { (kind, desc) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF14161F))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(14.dp))
                            .clickable { onSelectKind(kind) }
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = desc.first,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = StarkWhite
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = desc.second,
                                fontSize = 10.sp,
                                color = MutedGray,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
