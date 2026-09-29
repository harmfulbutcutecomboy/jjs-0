package com.jjs.studio.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.model.VfxTreeNode
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
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            try {
                val loadedFiles = mutableListOf<Pair<String, ByteArray>>()
                for (uri in uris) {
                    var fileName = "RobloxAsset.rbxm"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIdx)
                        }
                    }
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: ByteArray(0)
                    if (bytes.isNotEmpty()) {
                        loadedFiles.add(fileName to bytes)
                    }
                }
                if (loadedFiles.isNotEmpty()) {
                    viewModel.loadMultipleRobloxFiles(loadedFiles)
                }
            } catch (e: Exception) {
                onShowStatus("Failed to open files: ${e.message}")
            }
        }
    }

    val treeRoot = uiState.vfxTreeRoot
    val currentNode = uiState.vfxNodeMap[uiState.browseId] ?: treeRoot

    // Compute breadcrumb path
    val breadcrumbs = remember(currentNode, uiState.vfxNodeMap) {
        val crumbs = mutableListOf<VfxTreeNode>()
        var walk: VfxTreeNode? = currentNode
        while (walk != null && walk.id != "root") {
            crumbs.add(0, walk)
            walk = uiState.vfxNodeMap[walk.parentId]
        }
        crumbs
    }

    // Children of currently browsed node
    val visibleItems = remember(currentNode, searchQuery) {
        if (currentNode == null) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                currentNode.children
            } else {
                currentNode.children.filter {
                    it.name.lowercase().contains(q) || it.className.lowercase().contains(q)
                }
            }
        }
    }

    val selectedNode = uiState.vfxNodeMap[uiState.selectedVfxNodeId] ?: currentNode

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & File Loader
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "ROBLOX FX EXPLORER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedGray,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Hierarchy & VFX Tree",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = StarkWhite
                )
                Spacer(Modifier.height(12.dp))

                // File Open Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xEE0F121A), Color(0xEE090B10))))
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.linearGradient(listOf(Color(0xFF384357), Color(0xFF1E222D), Color(0xFF38BDF8).copy(alpha = 0.5f)))
                            ),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1C2230)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.loadedFileName ?: "Load Roblox Place / Model",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StarkWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Supported: .rbxl • .rbxm • .rbxlx • .rbxmx",
                                    fontSize = 11.sp,
                                    color = MutedGray
                                )
                            }
                        }

                        // Parse options
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF07090E))
                                .border(BorderStroke(1.dp, Color(0xFF1E222D)), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Parse Options:", fontSize = 11.sp, color = MutedGray, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = uiState.parseMeshes,
                                    onClick = { viewModel.updateSkillSettings(parseMeshes = !uiState.parseMeshes) },
                                    label = { Text("Meshes", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(if (uiState.parseMeshes) Icons.Default.Check else Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF1E2538),
                                        selectedLabelColor = StarkWhite,
                                        selectedLeadingIconColor = Color(0xFF38BDF8),
                                        containerColor = Color(0xFF0D0F16),
                                        labelColor = SubtleGray
                                    )
                                )
                                FilterChip(
                                    selected = uiState.parseCameras,
                                    onClick = { viewModel.updateSkillSettings(parseCameras = !uiState.parseCameras) },
                                    label = { Text("Cameras", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(if (uiState.parseCameras) Icons.Default.Check else Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF1E2538),
                                        selectedLabelColor = StarkWhite,
                                        selectedLeadingIconColor = Color(0xFF38BDF8),
                                        containerColor = Color(0xFF0D0F16),
                                        labelColor = SubtleGray
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = { fileLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = StarkWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("explorer_open_file_btn")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("OPEN / MULTI-SELECT FILES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        // HUD & Selection Action Banner (matches HUD from app.js)
        if (treeRoot != null) {
            item {
                SleekCard(modifier = Modifier.fillMaxWidth()) {
                    val pCount = selectedNode?.collectAllParticles()?.size ?: 0
                    val mCount = selectedNode?.collectAllMeshes()?.size ?: 0
                    val cCount = selectedNode?.collectAllCameras()?.size ?: 0
                    val sCount = selectedNode?.collectAllSounds()?.size ?: 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedNode?.name ?: "Workspace",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = StarkWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$pCount particles · $mCount meshes · $cCount cameras · $sCount sounds",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Button(
                            onClick = { viewModel.exportCurrentTargets() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("CONVERT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }

            // Breadcrumbs & Up Button (Matches app.js #explorerPath and '.. up')
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0C12))
                        .border(BorderStroke(1.dp, Color(0xFF1E222D)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentNode?.id != "root" && currentNode?.id != treeRoot.id) {
                        IconButton(
                            onClick = { viewModel.navigateUpVfxTree() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = StarkWhite, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        text = if (breadcrumbs.isEmpty()) "Workspace" else "Workspace / " + breadcrumbs.joinToString(" / ") { it.name },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MutedGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${visibleItems.size} items",
                        fontSize = 11.sp,
                        color = SubtleGray
                    )
                }
            }

            // Search filter
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name or class...", fontSize = 12.sp, color = MutedGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedGray, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF1E222D),
                        focusedContainerColor = Color(0xFF080A10),
                        unfocusedContainerColor = Color(0xFF080A10),
                        focusedTextColor = StarkWhite,
                        unfocusedTextColor = StarkWhite
                    ),
                    singleLine = true
                )
            }
        }

        // Tree Items
        if (treeRoot == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open an .rbxl / .rbxm file above to explore its VFX hierarchy.",
                        fontSize = 13.sp,
                        color = MutedGray
                    )
                }
            }
        } else if (visibleItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No VFX instances under this folder.", fontSize = 12.sp, color = SubtleGray)
                }
            }
        } else {
            items(visibleItems, key = { it.id }) { itemNode ->
                VfxTreeRow(
                    node = itemNode,
                    isSelected = uiState.selectedVfxNodeId == itemNode.id,
                    isChecked = uiState.checkedVfxNodeIds.contains(itemNode.id),
                    onSelect = { viewModel.selectVfxNode(itemNode.id) },
                    onToggleCheck = { viewModel.toggleCheckVfxNode(itemNode.id) },
                    onNavigateInto = { viewModel.navigateToVfxNode(itemNode.id) }
                )
            }
        }
    }
}

@Composable
private fun VfxTreeRow(
    node: VfxTreeNode,
    isSelected: Boolean,
    isChecked: Boolean,
    onSelect: () -> Unit,
    onToggleCheck: () -> Unit,
    onNavigateInto: () -> Unit
) {
    val isFolder = node.isFolderLike()
    val bg = if (isSelected) Color(0xFF171B26) else Color(0xFF090B10)
    val border = if (isSelected) Color(0xFF38BDF8) else Color(0xFF161922)

    val dotColor = when {
        isFolder -> Color(0xFFFFFFFF)
        node.className.contains("Mesh", ignoreCase = true) -> Color(0xFF38BDF8)
        node.className.contains("Camera", ignoreCase = true) -> Color(0xFFA855F7)
        node.className.contains("Sound", ignoreCase = true) -> Color(0xFF22C55E)
        else -> Color(0xFFF43F5E) // Particle
    }

    val bits = mutableListOf<String>()
    if (node.particles.isNotEmpty()) bits.add("${node.particles.size}pe")
    if (node.meshes.isNotEmpty()) bits.add("${node.meshes.size}mesh")
    if (node.cameras.isNotEmpty()) bits.add("${node.cameras.size}cam")
    if (node.sounds.isNotEmpty()) bits.add("${node.sounds.size}sfx")
    val metaText = if (bits.isNotEmpty()) bits.joinToString(" · ") else if (node.count > 0) "${node.count}" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(10.dp))
            .clickable {
                onSelect()
                if (isFolder) {
                    onNavigateInto()
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox for multi-selection (matches app.js checkbox)
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggleCheck() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF38BDF8),
                uncheckedColor = Color(0xFF384357),
                checkmarkColor = Color.Black
            ),
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Color Dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Spacer(Modifier.width(10.dp))

        // Name
        Text(
            text = node.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = StarkWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Meta tag
        if (metaText.isNotEmpty()) {
            Text(
                text = metaText,
                fontSize = 11.sp,
                color = MutedGray,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF12151E))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (isFolder) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = "Enter", tint = MutedGray, modifier = Modifier.size(16.dp))
        }
    }
}
