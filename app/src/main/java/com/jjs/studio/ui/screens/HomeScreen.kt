package com.jjs.studio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
    onCreateNewSkill: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenExport: () -> Unit,
    onShowStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val currentSkill = uiState.skills.getOrNull(uiState.currentSkillIndex)
    val activeNodes = currentSkill?.branches?.get(uiState.currentBranch)
        ?: currentSkill?.branches?.get("Default")
        ?: emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Subtle cybernetic dot matrix
        DotMatrixGrid(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            dotSpacing = 32f,
            dotRadius = 1.2f,
            dotColor = Color(0xFF141722)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // HERO TITLE & 3D VIEWPORT
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ViewInAr, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "JJS STUDIO • 3D ENGINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Built lean.\nBuilt to last.",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = StarkWhite,
                        textAlign = TextAlign.Center,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Roblox FX parser & KLUv bytecode compiler. Parse ParticleEmitters, Meshes, and SFX directly into Jujutsu Shenanigans.",
                        fontSize = 12.sp,
                        color = MutedGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    // 3D Interactive Viewport Component
                    Cyber3DViewport(
                        nodes = activeNodes,
                        modifier = Modifier.testTag("cyber_3d_viewport")
                    )
                }
            }

            // QUICK ACCESS CARDS (2x2 Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Roblox Explorer Opener Card
                        QuickToolCard(
                            title = "Roblox Opener",
                            subtitle = ".rbxl / .rbxm / XML",
                            icon = Icons.Default.FolderOpen,
                            accentColor = Color(0xFF38BDF8),
                            onClick = { onNavigate(AppScreen.EXPLORER) },
                            modifier = Modifier.weight(1f),
                            testTag = "home_card_explorer"
                        )

                        // Import Code Editor Card
                        QuickToolCard(
                            title = "Code Editor",
                            subtitle = "JSON & KLUv Bytecode",
                            icon = Icons.Default.Code,
                            accentColor = Color(0xFFC084FC),
                            onClick = { onNavigate(AppScreen.CODE_EDITOR) },
                            modifier = Modifier.weight(1f),
                            testTag = "home_card_code_editor"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Timeline Sequencer Card
                        QuickToolCard(
                            title = "Timeline",
                            subtitle = "${activeNodes.size} Sequence Nodes",
                            icon = Icons.Default.Timeline,
                            accentColor = Color(0xFF22C55E),
                            onClick = { onNavigate(AppScreen.EDITOR) },
                            modifier = Modifier.weight(1f),
                            testTag = "home_card_timeline"
                        )

                        // Compiler Card
                        QuickToolCard(
                            title = "Converter",
                            subtitle = "Recolor & Flags",
                            icon = Icons.Default.SwapHoriz,
                            accentColor = Color(0xFFF43F5E),
                            onClick = { onNavigate(AppScreen.CONVERT) },
                            modifier = Modifier.weight(1f),
                            testTag = "home_card_convert"
                        )
                    }
                }
            }

            // COMPILER OUTPUT PREVIEW CARD
            item {
                SleekCard(
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "home_pipeline_card"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE SKILL COMPILER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = currentSkill?.name ?: "Custom_Skill",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = StarkWhite
                            )
                            Text(
                                text = "Slot [${currentSkill?.key ?: 1}] • ${currentSkill?.duration ?: 2.0}s duration",
                                fontSize = 11.sp,
                                color = MutedGray
                            )
                        }

                        IconButton(
                            onClick = {
                                if (uiState.liveKluv.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(uiState.liveKluv))
                                    onShowStatus("Copied KLUv bytecode")
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2330))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy KLUv", tint = StarkWhite, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Code Output Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF040608))
                            .border(BorderStroke(1.dp, Color(0xFF181C26)), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (uiState.liveKluv.isNotBlank()) uiState.liveKluv else "// No bytecode compiled",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF818CF8),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // SKILLS IN WORKSPACE
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "WORKSPACE SKILLS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedGray,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${uiState.skills.size} Active Skill(s)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = StarkWhite
                        )
                    }

                    Button(
                        onClick = onCreateNewSkill,
                        colors = ButtonDefaults.buttonColors(containerColor = StarkWhite),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("home_create_skill_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Skill", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }

            // Skills List
            itemsIndexed(uiState.skills) { index, skill ->
                val isSelected = index == uiState.currentSkillIndex
                val border = if (isSelected) Color(0xFF38BDF8) else Color(0xFF181C26)
                val bg = if (isSelected) Color(0xFF11141E) else Color(0xFF090B10)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .border(BorderStroke(1.dp, border), RoundedCornerShape(14.dp))
                        .clickable { onSelectSkill(index) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF181D29)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${skill.key}",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = if (isSelected) Color(0xFF38BDF8) else StarkWhite
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skill.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StarkWhite
                        )
                        Text(
                            text = "${skill.branches.size} branch(es) • ${skill.branches.values.sumOf { it.size }} nodes",
                            fontSize = 11.sp,
                            color = MutedGray
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F1218), Color(0xFF07090D))
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color(0xFF262C3A), Color(0xFF131720), accentColor.copy(alpha = 0.4f))
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161B24)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = StarkWhite
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MutedGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
