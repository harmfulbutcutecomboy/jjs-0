package com.jjs.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.NodeKind
import com.jjs.studio.ui.components.SleekCard
import com.jjs.studio.ui.theme.*
import com.jjs.studio.viewmodel.JjsUiState
import com.jjs.studio.viewmodel.JjsViewModel

@Composable
fun EditorScreen(
    uiState: JjsUiState,
    viewModel: JjsViewModel,
    onShowStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSkill = viewModel.currentSkill
    val currentNodes = viewModel.currentNodes
    val selectedNode = viewModel.selectedNode

    var isAddBranchDialogVisible by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TIMELINE EDITOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedGray,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = currentSkill?.name ?: "No Skill Loaded",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = StarkWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Add Node Button
                    Button(
                        onClick = { viewModel.toggleAddNodeSheet(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StarkWhite,
                            contentColor = BlackBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_node_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Node", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // SKILL SELECTOR PILLS
            if (uiState.skills.size > 1) {
                item {
                    Column {
                        Text("ACTIVE SKILL:", fontSize = 10.sp, color = SubtleGray, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.skills.forEachIndexed { idx, s ->
                                val isSelected = idx == uiState.currentSkillIndex
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectSkill(idx) },
                                    label = { Text(s.name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StarkWhite,
                                        selectedLabelColor = BlackBackground,
                                        containerColor = Color(0xFF101217),
                                        labelColor = MutedGray
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) StarkWhite else BorderSubtle),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // BRANCH SELECTOR TABS
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BRANCHES:", fontSize = 10.sp, color = SubtleGray, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "+ New Branch",
                            fontSize = 11.sp,
                            color = JjsAccentPink,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                newBranchName = "Branch_${(currentSkill?.branches?.size ?: 0) + 1}"
                                isAddBranchDialogVisible = true
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        currentSkill?.branches?.keys?.forEach { bName ->
                            val isSelected = bName == uiState.currentBranch
                            val count = currentSkill.branches[bName]?.size ?: 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF252834) else Color(0xFF0F1015))
                                    .border(
                                        BorderStroke(1.dp, if (isSelected) StarkWhite else BorderSubtle),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectBranch(bName) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = bName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) StarkWhite else MutedGray
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) StarkWhite else Color(0xFF1E2028))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) BlackBackground else MutedGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SELECTED NODE INSPECTOR
            if (selectedNode != null) {
                item {
                    NodeInspectorCard(
                        node = selectedNode,
                        viewModel = viewModel,
                        onClose = { viewModel.selectNode(null) },
                        onDuplicate = { viewModel.duplicateNode(selectedNode) },
                        onDelete = { viewModel.deleteNode(selectedNode.id) }
                    )
                }
            }

            // TIMELINE SECTION HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TIMELINE NODES (${currentNodes.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedGray,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Tap node to inspect",
                        fontSize = 11.sp,
                        color = SubtleGray
                    )
                }
            }

            // TIMELINE NODES LIST
            if (currentNodes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0A0B0E))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(16.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = SubtleGray, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No nodes in this branch yet", color = MutedGray, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.toggleAddNodeSheet(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = StarkWhite, contentColor = BlackBackground),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Add First Node", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(currentNodes) { index, node ->
                    val isSelected = node.id == uiState.selectedNodeId
                    TimelineNodeRow(
                        node = node,
                        index = index,
                        isSelected = isSelected,
                        onClick = { viewModel.selectNode(node.id) }
                    )
                }
            }
        }
    }

    // Add Branch Dialog
    if (isAddBranchDialogVisible) {
        AlertDialog(
            onDismissRequest = { isAddBranchDialogVisible = false },
            containerColor = Color(0xFF14161F),
            title = { Text("Create Branch", color = StarkWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newBranchName,
                    onValueChange = { newBranchName = it },
                    label = { Text("Branch Name", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StarkWhite,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = StarkWhite,
                        unfocusedTextColor = StarkWhite
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addBranch(newBranchName)
                        isAddBranchDialogVisible = false
                        onShowStatus("Created branch '$newBranchName'")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StarkWhite, contentColor = BlackBackground)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddBranchDialogVisible = false }) {
                    Text("Cancel", color = MutedGray)
                }
            }
        )
    }
}

@Composable
private fun TimelineNodeRow(
    node: JjsNode,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (isSelected) JjsAccentPink else BorderSubtle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) Color(0xFF1C1E28) else Color(0xFF0C0D12))
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timestamp Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161820))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${String.format("%.2f", node.time)}s",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MutedGray
            )
        }

        Spacer(Modifier.width(10.dp))

        // Kind Badge
        NodeKindBadge(kind = node.kind)

        Spacer(Modifier.width(10.dp))

        // Node Title & Summary
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (node.name.isNotBlank()) node.name else node.kind.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = StarkWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = getNodeSummary(node),
                fontSize = 10.sp,
                color = MutedGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Arrow / Selection Indicator
        Icon(
            imageVector = if (isSelected) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (isSelected) StarkWhite else SubtleGray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun NodeKindBadge(kind: NodeKind) {
    val (bgColor, textColor) = when (kind) {
        NodeKind.PARTICLE -> Color(0xFF2E1C2C) to Color(0xFFF7D7F8)
        NodeKind.VISUAL_MESH -> Color(0xFF1F1C32) to Color(0xFFC7A8FF)
        NodeKind.VISUAL_CAMERA -> Color(0xFF14262E) to Color(0xFF7DE5FF)
        NodeKind.SFX -> Color(0xFF162B1D) to Color(0xFF7DFFB3)
        NodeKind.WAIT -> Color(0xFF282516) to Color(0xFFE0C45A)
        NodeKind.CONNECT -> Color(0xFF2E1A1A) to Color(0xFFFF8585)
        NodeKind.TAG -> Color(0xFF26182C) to Color(0xFFE59AE8)
        NodeKind.BRANCH -> Color(0xFF1B242C) to Color(0xFF9ABAE8)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = kind.name,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = textColor,
            letterSpacing = 0.5.sp
        )
    }
}

private fun getNodeSummary(node: JjsNode): String {
    return when (node.kind) {
        NodeKind.PARTICLE -> "Tex: ${node.texture} · Rate: ${node.rate} · Spd: ${node.speed}"
        NodeKind.VISUAL_MESH -> "Mesh: ${node.meshId} · Size: ${node.visualSize}"
        NodeKind.VISUAL_CAMERA -> "FOV: ${node.fov}° · Shake"
        NodeKind.SFX -> "SoundId: ${node.soundId} · Vol: ${node.volume}"
        NodeKind.WAIT -> "Pause for ${node.time}s"
        NodeKind.CONNECT -> "Signal: '${node.signal}' · Range: ${node.range}"
        NodeKind.TAG -> "Label: '${node.tagLabel}'"
        NodeKind.BRANCH -> "Jump to: '${node.targetBranch}'"
    }
}

@Composable
private fun NodeInspectorCard(
    node: JjsNode,
    viewModel: JjsViewModel,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF12141C))
            .border(BorderStroke(1.5.dp, StarkWhite), shape)
            .padding(16.dp)
    ) {
        // Inspector Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "INSPECTING ${node.kind.name}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = JjsAccentPink,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (node.name.isNotBlank()) node.name else node.kind.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = StarkWhite
                )
            }

            IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = StarkWhite, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = JjsDangerRed, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedGray, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Common Fields: Name & Time Offset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = node.name,
                onValueChange = { newName -> viewModel.updateSelectedNode { it.name = newName } },
                label = { Text("Node Name", fontSize = 11.sp) },
                modifier = Modifier.weight(1.4f),
                shape = RoundedCornerShape(10.dp),
                colors = inspectorTextFieldColors(),
                singleLine = true
            )

            OutlinedTextField(
                value = node.time.toString(),
                onValueChange = { it.toDoubleOrNull()?.let { t -> viewModel.updateSelectedNode { n -> n.time = t } } },
                label = { Text("Time (s)", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = inspectorTextFieldColors(),
                singleLine = true
            )
        }

        Spacer(Modifier.height(10.dp))

        // Kind Specific Fields
        when (node.kind) {
            NodeKind.PARTICLE -> {
                OutlinedTextField(
                    value = node.texture,
                    onValueChange = { tex -> viewModel.updateSelectedNode { it.texture = tex } },
                    label = { Text("Texture Asset ID", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = node.rate.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { r -> viewModel.updateSelectedNode { n -> n.rate = r } } },
                        label = { Text("Rate", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = node.speed.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { s -> viewModel.updateSelectedNode { n -> n.speed = s } } },
                        label = { Text("Speed", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = node.color,
                    onValueChange = { c -> viewModel.updateSelectedNode { it.color = c } },
                    label = { Text("RGB Colors", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = node.size,
                        onValueChange = { s -> viewModel.updateSelectedNode { it.size = s } },
                        label = { Text("Size Sequence", fontSize = 11.sp) },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = node.emitCount.toString(),
                        onValueChange = { it.toIntOrNull()?.let { ec -> viewModel.updateSelectedNode { n -> n.emitCount = ec } } },
                        label = { Text("Emit Count", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = node.lifetimeRange,
                        onValueChange = { lr -> viewModel.updateSelectedNode { it.lifetimeRange = lr } },
                        label = { Text("Lifetime Range", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = node.zOffset.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { z -> viewModel.updateSelectedNode { n -> n.zOffset = z } } },
                        label = { Text("Z-Offset", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = node.spreadAngle,
                        onValueChange = { sa -> viewModel.updateSelectedNode { it.spreadAngle = sa } },
                        label = { Text("Spread Angle", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = node.flipbookSize,
                        onValueChange = { fs -> viewModel.updateSelectedNode { it.flipbookSize = fs } },
                        label = { Text("Flipbook Size", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = node.lightEmission.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { le -> viewModel.updateSelectedNode { n -> n.lightEmission = le } } },
                        label = { Text("Light Emission", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = node.brightness.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { b -> viewModel.updateSelectedNode { n -> n.brightness = b } } },
                        label = { Text("Brightness", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                }
            }
            NodeKind.VISUAL_MESH -> {
                OutlinedTextField(
                    value = node.meshId,
                    onValueChange = { m -> viewModel.updateSelectedNode { it.meshId = m } },
                    label = { Text("Mesh Asset ID", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = node.visualSize,
                    onValueChange = { s -> viewModel.updateSelectedNode { it.visualSize = s } },
                    label = { Text("Mesh Size (x, y, z)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
            }
            NodeKind.VISUAL_CAMERA -> {
                OutlinedTextField(
                    value = node.fov.toString(),
                    onValueChange = { it.toDoubleOrNull()?.let { f -> viewModel.updateSelectedNode { n -> n.fov = f } } },
                    label = { Text("Field of View (FOV)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
            }
            NodeKind.SFX -> {
                OutlinedTextField(
                    value = node.soundId,
                    onValueChange = { s -> viewModel.updateSelectedNode { it.soundId = s } },
                    label = { Text("Sound Asset ID", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = node.volume.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> viewModel.updateSelectedNode { n -> n.volume = v } } },
                        label = { Text("Volume", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = node.playbackSpeed.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { p -> viewModel.updateSelectedNode { n -> n.playbackSpeed = p } } },
                        label = { Text("Pitch / Speed", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = inspectorTextFieldColors(),
                        singleLine = true
                    )
                }
            }
            NodeKind.CONNECT -> {
                OutlinedTextField(
                    value = node.signal,
                    onValueChange = { s -> viewModel.updateSelectedNode { it.signal = s } },
                    label = { Text("Signal (e.g. Hit, Land, Dash)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
            }
            NodeKind.TAG -> {
                OutlinedTextField(
                    value = node.tagLabel,
                    onValueChange = { t -> viewModel.updateSelectedNode { it.tagLabel = t } },
                    label = { Text("Tag Name", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
            }
            NodeKind.BRANCH -> {
                OutlinedTextField(
                    value = node.targetBranch,
                    onValueChange = { b -> viewModel.updateSelectedNode { it.targetBranch = b } },
                    label = { Text("Target Branch Name", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = inspectorTextFieldColors(),
                    singleLine = true
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun inspectorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = StarkWhite,
    unfocusedBorderColor = Color(0xFF262935),
    focusedTextColor = StarkWhite,
    unfocusedTextColor = StarkWhite,
    focusedLabelColor = StarkWhite,
    unfocusedLabelColor = MutedGray,
    cursorColor = StarkWhite,
    focusedContainerColor = Color(0xFF090A0E),
    unfocusedContainerColor = Color(0xFF090A0E)
)
