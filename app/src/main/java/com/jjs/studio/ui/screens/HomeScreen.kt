package com.jjs.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.model.JjsSkill
import com.jjs.studio.ui.components.*
import com.jjs.studio.ui.theme.*
import com.jjs.studio.viewmodel.AppScreen
import com.jjs.studio.viewmodel.JjsUiState

@Composable
fun HomeScreen(
    uiState: JjsUiState,
    onNavigate: (AppScreen) -> Unit,
    onSelectSkill: (Int) -> Unit,
    onOpenImport: () -> Unit,
    onOpenExport: () -> Unit,
    onShowStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        // Subtle dot matrix in upper background
        DotMatrixGrid(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            dotSpacing = 32f,
            dotRadius = 1.2f,
            dotColor = Color(0xFF161822)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // HERO SECTION (barred.cc aesthetic)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlowingBadge(
                        icon = Icons.Default.ViewInAr,
                        size = 64.dp,
                        iconSize = 30.dp,
                        contentDescription = "JJS 3D Cube Icon"
                    )

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "Built lean.\nBuilt to last.",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = StarkWhite,
                        textAlign = TextAlign.Center,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "Roblox FX to Jujutsu Shenanigans bytecode converter. Parse ParticleEmitters, Meshes, and SFX directly into KLUv bytecode.",
                        fontSize = 13.sp,
                        color = MutedGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    // Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenImport,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("home_import_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StarkWhite,
                                contentColor = BlackBackground
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Load / Paste", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { onNavigate(AppScreen.EDITOR) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("home_editor_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderMedium),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StarkWhite
                            )
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Editor", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // STATS ROW (Bento cards)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val current = uiState.skills.getOrNull(uiState.currentSkillIndex)
                    StatCard(
                        title = "SKILLS",
                        value = "${uiState.skills.size}",
                        subtitle = "Loaded in memory",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "NODES",
                        value = "${current?.totalNodesCount ?: 0}",
                        subtitle = "Active branch nodes",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "KEY",
                        value = "${current?.key ?: 1}",
                        subtitle = "Trigger bind slot",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // LIVE BYTECODE PIPELINE CARD (barred.cc aesthetic: You type -> We store)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COMPILER PIPELINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedGray,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Auto-synced",
                            fontSize = 11.sp,
                            color = JjsNeonGreen
                        )
                    }

                    // Input block ("You type")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0C0D12))
                            .border(androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "Active Skill Name",
                                fontSize = 11.sp,
                                color = SubtleGray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = uiState.skillName.ifEmpty { "converted_vfx" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StarkWhite
                            )
                        }
                    }

                    // Flow Arrow
                    FlowArrowDivider()

                    // Output block ("We store / KLUv")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0C0D12))
                            .border(androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Generated JJS KLUv Bytecode",
                                    fontSize = 11.sp,
                                    color = SubtleGray,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(uiState.liveKluv))
                                        onShowStatus("Copied KLUv code to clipboard!")
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy KLUv",
                                        tint = StarkWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (uiState.liveKluv.length > 80) uiState.liveKluv.take(80) + "…" else uiState.liveKluv,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFD6C8E8),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // PRESETS LIBRARY HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRESET SKILLS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedGray,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${uiState.skills.size} available",
                        fontSize = 11.sp,
                        color = SubtleGray
                    )
                }
            }

            // PRESET ITEMS
            itemsIndexed(uiState.skills) { index, skill ->
                val isSelected = index == uiState.currentSkillIndex
                PresetSkillCard(
                    skill = skill,
                    isSelected = isSelected,
                    onClick = {
                        onSelectSkill(index)
                        onShowStatus("Selected '${skill.name}'")
                    },
                    onOpenInEditor = {
                        onSelectSkill(index)
                        onNavigate(AppScreen.EDITOR)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0C0D12))
            .border(androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle), shape)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = SubtleGray,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = StarkWhite
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 9.sp,
            color = MutedGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresetSkillCard(
    skill: JjsSkill,
    isSelected: Boolean,
    onClick: () -> Unit,
    onOpenInEditor: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor = if (isSelected) StarkWhite else BorderSubtle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) Color(0xFF14161F) else Color(0xFF0C0D12))
            .border(androidx.compose.foundation.BorderStroke(1.dp, borderColor), shape)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1C24))
                    .border(androidx.compose.foundation.BorderStroke(1.dp, BorderMedium), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (isSelected) JjsAccentPink else StarkWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = StarkWhite
                )
                Text(
                    text = "Key ${skill.key} · ${skill.duration}s · ${skill.branches.size} branch(es) · ${skill.totalNodesCount} nodes",
                    fontSize = 11.sp,
                    color = MutedGray
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2B2E3C))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = StarkWhite)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onOpenInEditor,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = StarkWhite)
                Spacer(Modifier.width(4.dp))
                Text("Edit in Timeline", fontSize = 12.sp, color = StarkWhite, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
