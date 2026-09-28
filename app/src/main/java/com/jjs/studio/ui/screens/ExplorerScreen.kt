package com.jjs.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.model.NodeKind
import com.jjs.studio.model.RobloxInstance
import com.jjs.studio.ui.components.SleekCard
import com.jjs.studio.ui.theme.*
import com.jjs.studio.viewmodel.JjsUiState
import com.jjs.studio.viewmodel.JjsViewModel

@Composable
fun ExplorerScreen(
    uiState: JjsUiState,
    viewModel: JjsViewModel,
    onShowStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedInstance by remember { mutableStateOf<RobloxInstance?>(null) }

    // If no explorer root loaded, construct a representative Roblox place tree
    val root = remember(uiState.explorerRoot) {
        uiState.explorerRoot ?: createSampleRobloxTree()
    }

    val flattenedList = remember(root, searchQuery) {
        flattenTree(root).filter {
            if (searchQuery.isBlank()) true
            else it.name.contains(searchQuery, ignoreCase = true) || it.className.contains(searchQuery, ignoreCase = true)
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ROBLOX EXPLORER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedGray,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Instance Hierarchy",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = StarkWhite
                    )
                    Spacer(Modifier.height(8.dp))

                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search instances (e.g. ParticleEmitter, Sound)", fontSize = 12.sp, color = SubtleGray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MutedGray, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MutedGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explorer_search_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StarkWhite,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = StarkWhite,
                            unfocusedTextColor = StarkWhite,
                            focusedContainerColor = Color(0xFF0C0D12),
                            unfocusedContainerColor = Color(0xFF0C0D12)
                        ),
                        singleLine = true
                    )
                }
            }

            // Instance Inspector Sheet (if selected)
            if (selectedInstance != null) {
                item {
                    val inst = selectedInstance!!
                    SleekCard(testTag = "instance_inspector_card") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = inst.className.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JjsAccentPink,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = inst.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StarkWhite
                                )
                            }
                            IconButton(onClick = { selectedInstance = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedGray)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        if (inst.properties.isNotEmpty()) {
                            Text("PROPERTIES", fontSize = 10.sp, color = SubtleGray, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            inst.properties.entries.take(6).forEach { (k, v) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(k, fontSize = 11.sp, color = MutedGray)
                                    Text(
                                        v,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = StarkWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Convert to Node Action
                        Button(
                            onClick = {
                                when (inst.className) {
                                    "ParticleEmitter" -> viewModel.addNode(NodeKind.PARTICLE)
                                    "MeshPart", "SpecialMesh" -> viewModel.addNode(NodeKind.VISUAL_MESH)
                                    "Camera" -> viewModel.addNode(NodeKind.VISUAL_CAMERA)
                                    "Sound" -> viewModel.addNode(NodeKind.SFX)
                                    else -> viewModel.addNode(NodeKind.PARTICLE)
                                }
                                onShowStatus("Added '${inst.name}' to timeline")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StarkWhite, contentColor = BlackBackground),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import to Current Skill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Results count
            item {
                Text(
                    text = "DISCOVERED INSTANCES (${flattenedList.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedGray,
                    letterSpacing = 1.sp
                )
            }

            // INSTANCE ROWS
            items(flattenedList) { item ->
                val isSelected = selectedInstance?.id == item.id
                InstanceRow(
                    instance = item,
                    isSelected = isSelected,
                    onClick = { selectedInstance = if (isSelected) null else item }
                )
            }
        }
    }
}

@Composable
private fun InstanceRow(
    instance: RobloxInstance,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val dotColor = when (instance.className) {
        "ParticleEmitter" -> Color(0xFFF7D7F8)
        "MeshPart", "SpecialMesh" -> Color(0xFFFF6BB5)
        "Camera" -> Color(0xFFC45EC8)
        "Sound" -> Color(0xFF7DFFB3)
        "Beam" -> Color(0xFF6EA8FF)
        else -> Color(0xFFFFFFFF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) Color(0xFF1B1E28) else Color(0xFF0C0D12))
            .border(BorderStroke(1.dp, if (isSelected) StarkWhite else BorderSubtle), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Class Indicator Dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = instance.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = StarkWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = instance.className,
                fontSize = 10.sp,
                color = MutedGray
            )
        }

        if (instance.children.isNotEmpty()) {
            Text(
                text = "${instance.children.size} items",
                fontSize = 10.sp,
                color = SubtleGray
            )
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SubtleGray,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun flattenTree(root: RobloxInstance): List<RobloxInstance> {
    val list = mutableListOf<RobloxInstance>()
    fun walk(inst: RobloxInstance) {
        list.add(inst)
        inst.children.forEach { walk(it) }
    }
    walk(root)
    return list
}

private fun createSampleRobloxTree(): RobloxInstance {
    val workspace = RobloxInstance(name = "Workspace", className = "Workspace")

    val purpleModel = RobloxInstance(name = "HollowPurpleRig", className = "Model")
    purpleModel.children.add(
        RobloxInstance(
            name = "BlueCompressionParticle",
            className = "ParticleEmitter",
            properties = mapOf("Texture" to "rbxassetid://5859188448", "Rate" to "45", "Speed" to "25", "Color" to "50, 120, 255")
        )
    )
    purpleModel.children.add(
        RobloxInstance(
            name = "RedReversalParticle",
            className = "ParticleEmitter",
            properties = mapOf("Texture" to "rbxassetid://5859188448", "Rate" to "40", "Speed" to "22", "Color" to "255, 40, 70")
        )
    )
    purpleModel.children.add(
        RobloxInstance(
            name = "PurpleCoreMesh",
            className = "MeshPart",
            properties = mapOf("MeshId" to "rbxassetid://6023773194", "Size" to "6, 6, 6")
        )
    )
    purpleModel.children.add(
        RobloxInstance(
            name = "SingularityBlastSound",
            className = "Sound",
            properties = mapOf("SoundId" to "rbxassetid://9114387890", "Volume" to "1.2")
        )
    )
    purpleModel.children.add(
        RobloxInstance(
            name = "ShakeCamera",
            className = "Camera",
            properties = mapOf("FieldOfView" to "60")
        )
    )

    val shrineFolder = RobloxInstance(name = "MalevolentShrineFX", className = "Folder")
    shrineFolder.children.add(
        RobloxInstance(
            name = "DismantleCrescent",
            className = "SpecialMesh",
            properties = mapOf("MeshId" to "rbxassetid://4815162342", "Size" to "8, 0.2, 3")
        )
    )
    shrineFolder.children.add(
        RobloxInstance(
            name = "BloodBurstEmitter",
            className = "ParticleEmitter",
            properties = mapOf("Texture" to "rbxassetid://5859188448", "Rate" to "60", "Speed" to "30")
        )
    )
    shrineFolder.children.add(
        RobloxInstance(
            name = "TearSlashAudio",
            className = "Sound",
            properties = mapOf("SoundId" to "rbxassetid://9114384455", "Volume" to "1.1")
        )
    )

    workspace.children.add(purpleModel)
    workspace.children.add(shrineFolder)
    return workspace
}
