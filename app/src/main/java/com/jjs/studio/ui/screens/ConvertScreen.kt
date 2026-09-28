package com.jjs.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.ui.components.*
import com.jjs.studio.ui.theme.*
import com.jjs.studio.util.RgbColor
import com.jjs.studio.viewmodel.JjsUiState
import com.jjs.studio.viewmodel.JjsViewModel

@Composable
fun ConvertScreen(
    uiState: JjsUiState,
    viewModel: JjsViewModel,
    onShowStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header / Intro
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "CONVERT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedGray,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "One line. Done.",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = StarkWhite,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tune compiler flags, bind keys, remap colors, and export KLUv bytecode.",
                        fontSize = 12.sp,
                        color = MutedGray
                    )
                }
            }

            // PRIMARY PARAMETERS CARD
            item {
                SleekCard(testTag = "convert_params_card") {
                    Text(
                        text = "SKILL IDENTITY & TIMING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubtleGray,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(14.dp))

                    // Skill Name
                    OutlinedTextField(
                        value = uiState.skillName,
                        onValueChange = { viewModel.updateSkillSettings(name = it) },
                        label = { Text("Skill Name", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("convert_skill_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = sleekTextFieldColors(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(12.dp))

                    // Key & Duration row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.skillKey.toString(),
                            onValueChange = { it.toIntOrNull()?.let { k -> viewModel.updateSkillSettings(key = k) } },
                            label = { Text("Key Slot", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = sleekTextFieldColors(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = uiState.duration.toString(),
                            onValueChange = { it.toDoubleOrNull()?.let { d -> viewModel.updateSkillSettings(duration = d) } },
                            label = { Text("Duration (s)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = sleekTextFieldColors(),
                            singleLine = true
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Position Offset
                    OutlinedTextField(
                        value = uiState.position,
                        onValueChange = { viewModel.updateSkillSettings(position = it) },
                        label = { Text("Position Offset (x, y, z)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = sleekTextFieldColors(),
                        singleLine = true
                    )
                }
            }

            // COMPILER TOGGLES CARD
            item {
                SleekCard(testTag = "convert_toggles_card") {
                    Text(
                        text = "PIPELINE COMPILER FLAGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubtleGray,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    SleekToggleRow(
                        title = "Pack Mode",
                        subtitle = "One skill per host model container",
                        checked = uiState.packMode,
                        onCheckedChange = { viewModel.updateSkillSettings(packMode = it) }
                    )

                    HorizontalDivider(color = Color(0xFF181A22), modifier = Modifier.padding(vertical = 10.dp))

                    SleekToggleRow(
                        title = "Branch Mode",
                        subtitle = "Hosts become named branches of one skill",
                        checked = uiState.branchMode,
                        onCheckedChange = { viewModel.updateSkillSettings(branchMode = it) }
                    )

                    HorizontalDivider(color = Color(0xFF181A22), modifier = Modifier.padding(vertical = 10.dp))

                    SleekToggleRow(
                        title = "Parse Meshes",
                        subtitle = "Convert MeshPart / SpecialMesh into VISUAL nodes",
                        checked = uiState.parseMeshes,
                        onCheckedChange = { viewModel.updateSkillSettings(parseMeshes = it) }
                    )

                    HorizontalDivider(color = Color(0xFF181A22), modifier = Modifier.padding(vertical = 10.dp))

                    SleekToggleRow(
                        title = "Parse Cameras",
                        subtitle = "Sequence camera angles and FOV shakes",
                        checked = uiState.parseCameras,
                        onCheckedChange = { viewModel.updateSkillSettings(parseCameras = it) }
                    )

                    HorizontalDivider(color = Color(0xFF181A22), modifier = Modifier.padding(vertical = 10.dp))

                    SleekToggleRow(
                        title = "Run On Server",
                        subtitle = "Execute logic server-side in JJS runtime",
                        checked = uiState.runOnServer,
                        onCheckedChange = { viewModel.updateSkillSettings(runOnServer = it) }
                    )
                }
            }

            // COLOR PALETTE RECOLORING ENGINE
            item {
                SleekCard(testTag = "convert_recolor_card") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RECOLOR ENGINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SubtleGray,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Remap particle hues from detected palette",
                                fontSize = 11.sp,
                                color = MutedGray
                            )
                        }
                        Switch(
                            checked = uiState.recolorEnabled,
                            onCheckedChange = { viewModel.updateSkillSettings(recolorEnabled = it) },
                            colors = sleekSwitchColors()
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Detected Colors Row
                    Text("Detected in Particles:", fontSize = 11.sp, color = SubtleGray)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PaletteChip("Main", uiState.detectedPalette.main)
                        PaletteChip("Accent", uiState.detectedPalette.accent)
                        PaletteChip("Other", uiState.detectedPalette.other)
                    }

                    if (uiState.recolorEnabled) {
                        Spacer(Modifier.height(16.dp))
                        Text("Target Replacement Palette:", fontSize = 11.sp, color = SubtleGray)
                        Spacer(Modifier.height(8.dp))

                        // Target Color Pickers / Preset Palettes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PalettePickerChip(
                                label = "Crimson",
                                color = RgbColor(255, 30, 60),
                                isSelected = uiState.targetPalette.main.r > 200 && uiState.targetPalette.main.g < 50,
                                onClick = {
                                    viewModel.updateTargetPalette(
                                        main = RgbColor(255, 30, 60),
                                        accent = RgbColor(255, 120, 160)
                                    )
                                }
                            )
                            PalettePickerChip(
                                label = "Electric Blue",
                                color = RgbColor(40, 140, 255),
                                isSelected = uiState.targetPalette.main.b > 200,
                                onClick = {
                                    viewModel.updateTargetPalette(
                                        main = RgbColor(40, 140, 255),
                                        accent = RgbColor(180, 220, 255)
                                    )
                                }
                            )
                            PalettePickerChip(
                                label = "Emerald",
                                color = RgbColor(30, 220, 120),
                                isSelected = uiState.targetPalette.main.g > 200,
                                onClick = {
                                    viewModel.updateTargetPalette(
                                        main = RgbColor(30, 220, 120),
                                        accent = RgbColor(180, 255, 210)
                                    )
                                }
                            )
                            PalettePickerChip(
                                label = "Void / Gold",
                                color = RgbColor(255, 200, 40),
                                isSelected = uiState.targetPalette.main.r > 200 && uiState.targetPalette.main.g > 180,
                                onClick = {
                                    viewModel.updateTargetPalette(
                                        main = RgbColor(255, 200, 40),
                                        accent = RgbColor(255, 240, 180)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // OUTPUT CARDS
            item {
                MonospaceCodeCard(
                    title = "Zstandard KLUv Bytecode",
                    subtitle = "Ready for Jujutsu Shenanigans in-game import",
                    code = uiState.liveKluv,
                    onCopy = {
                        clipboard.setText(AnnotatedString(uiState.liveKluv))
                        onShowStatus("Copied Zstandard KLUv bytecode!")
                    },
                    maxLines = 6,
                    testTag = "kluv_output_card"
                )
            }

            item {
                MonospaceCodeCard(
                    title = "Formatted JSON Skill Structure",
                    subtitle = "Uncompressed human-readable JJS line array",
                    code = uiState.liveJson,
                    onCopy = {
                        clipboard.setText(AnnotatedString(uiState.liveJson))
                        onShowStatus("Copied JSON structure to clipboard!")
                    },
                    maxLines = 8,
                    testTag = "json_output_card"
                )
            }
        }
    }
}

@Composable
private fun SleekToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = StarkWhite
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MutedGray
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = sleekSwitchColors()
        )
    }
}

@Composable
private fun PaletteChip(label: String, color: RgbColor) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color.toComposeColor())
                .border(BorderStroke(1.dp, Color(0xFF444444)), CircleShape)
        )
        Text(text = label, fontSize = 11.sp, color = MutedGray)
    }
}

@Composable
private fun PalettePickerChip(
    label: String,
    color: RgbColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF242732) else Color(0xFF0F1015))
            .border(
                BorderStroke(1.dp, if (isSelected) StarkWhite else BorderSubtle),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color.toComposeColor())
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) StarkWhite else MutedGray
        )
    }
}

@Composable
private fun sleekTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = StarkWhite,
    unfocusedBorderColor = BorderSubtle,
    focusedTextColor = StarkWhite,
    unfocusedTextColor = StarkWhite,
    focusedLabelColor = StarkWhite,
    unfocusedLabelColor = MutedGray,
    cursorColor = StarkWhite,
    focusedContainerColor = Color(0xFF08090C),
    unfocusedContainerColor = Color(0xFF08090C)
)

@Composable
private fun sleekSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = BlackBackground,
    checkedTrackColor = StarkWhite,
    uncheckedThumbColor = MutedGray,
    uncheckedTrackColor = Color(0xFF1A1C24),
    uncheckedBorderColor = BorderSubtle
)
