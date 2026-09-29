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
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) } // null=All, or "ParticleEmitter", "MeshPart", "Sound"

    // File opener launcher for multiple .rbxl, .rbxm, .rbxlx, .rbxmx files
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
                } else {
                    onShowStatus("No valid files selected")
                }
            } catch (e: Exception) {
                onShowStatus("Failed to open files: ${e.message}")
            }
        }
    }

    val root = uiState.explorerRoot

    val flattenedList = remember(root, searchQuery, selectedFilter) {
        if (root == null) emptyList()
        else {
            flattenTree(root).filter { inst ->
                val matchesFilter = selectedFilter == null || inst.className.equals(selectedFilter, ignoreCase = true)
                val matchesSearch = if (searchQuery.isBlank()) true
                else inst.name.contains(searchQuery, ignoreCase = true) || inst.className.contains(searchQuery, ignoreCase = true)
                matchesFilter && matchesSearch
            }
        }
    }

    val selectedInstance = viewModel.selectedExplorerInstance

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
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
                    text = "Asset & FX Hierarchy",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = StarkWhite
                )
                Spacer(Modifier.height(12.dp))

                // Shiny Open File Action Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xEE0F121A), Color(0xEE090B10))
                            )
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(Color(0xFF384357), Color(0xFF1E222D), Color(0xFF38BDF8).copy(alpha = 0.5f))
                                )
                            ),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C2230)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = if (uiState.loadedFileName != null) uiState.loadedFileName!! else "Load Roblox Place / Model",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StarkWhite
                                )
                                Text(
                                    text = "Supported: .rbxl • .rbxm • .rbxlx • .rbxmx",
                                    fontSize = 11.sp,
                                    color = MutedGray
                                )
                            }
                        }

                        // Camera & Mesh Parsing Toggles (matching gate in original app)
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
                            Text(
                                text = "Parse Options:",
                                fontSize = 11.sp,
                                color = MutedGray,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = uiState.parseMeshes,
                                    onClick = { viewModel.updateSkillSettings(parseMeshes = !uiState.parseMeshes) },
                                    label = { Text("Meshes", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            if (uiState.parseMeshes) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF1E2538),
                                        selectedLabelColor = StarkWhite,
                                        selectedLeadingIconColor = Color(0xFF38BDF8),
                                        containerColor = Color(0xFF0D0F16),
                                        labelColor = SubtleGray
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (uiState.parseMeshes) Color(0xFF38BDF8) else Color(0xFF1E222D),
                                        enabled = true,
                                        selected = uiState.parseMeshes
                                    )
                                )
                                FilterChip(
                                    selected = uiState.parseCameras,
                                    onClick = { viewModel.updateSkillSettings(parseCameras = !uiState.parseCameras) },
                                    label = { Text("Cameras", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            if (uiState.parseCameras) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF1E2538),
                                        selectedLabelColor = StarkWhite,
                                        selectedLeadingIconColor = Color(0xFF38BDF8),
                                        containerColor = Color(0xFF0D0F16),
                                        labelColor = SubtleGray
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (uiState.parseCameras) Color(0xFF38BDF8) else Color(0xFF1E222D),
                                        enabled = true,
                                        selected = uiState.parseCameras
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { fileLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = StarkWhite),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("explorer_open_file_btn")
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("OPEN / MULTI-SELECT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }

                            if (root != null) {
                                Button(
                                    onClick = { viewModel.convertAllExplorerFxToSkill() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2330)),
                                    border = BorderStroke(1.dp, Color(0xFF384357)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("explorer_convert_all_fx_btn")
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("CONVERT ALL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StarkWhite)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (root == null) {
            // Empty State
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F121A))
                            .border(1.dp, Color(0xFF222838), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
                    }
                    Text(
                        text = "No Roblox Asset Loaded",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = StarkWhite
                    )
                    Text(
                        text = "Tap 'Open File' above to browse and parse any Roblox .rbxl place or .rbxm model to extract ParticleEmitters, Meshes, and Sounds.",
                        fontSize = 12.sp,
                        color = MutedGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Search & Filter Toolbar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter instances...", fontSize = 12.sp, color = SubtleGray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedGray, modifier = Modifier.size(18.dp)) },
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
                            focusedContainerColor = CardBackground,
                            unfocusedContainerColor = CardBackground,
                            focusedBorderColor = Color(0xFF384357),
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = StarkWhite,
                            unfocusedTextColor = StarkWhite
                        ),
                        singleLine = true
                    )

                    // Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(null to "All", "ParticleEmitter" to "Particles", "MeshPart" to "Meshes", "Camera" to "Cameras", "Sound" to "SFX").forEach { (filterVal, label) ->
                            val isSelected = selectedFilter == filterVal
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = if (isSelected) null else filterVal },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF222838),
                                    selectedLabelColor = StarkWhite,
                                    containerColor = Color(0xFF0F1117),
                                    labelColor = MutedGray
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E222D),
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                        }
                    }
                }
            }

            // Instance Tree List
            items(flattenedList, key = { it.id }) { inst ->
                val isSelected = selectedInstance?.id == inst.id
                ExplorerRowItem(
                    instance = inst,
                    isSelected = isSelected,
                    onClick = { viewModel.selectExplorerInstance(inst.id) }
                )
            }

            // Property Inspector if selected
            if (selectedInstance != null) {
                item {
                    SleekCard(
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "explorer_inspector_card"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SELECTED INSTANCE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = selectedInstance.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StarkWhite
                                )
                                Text(
                                    text = "Class: ${selectedInstance.className} • ${selectedInstance.children.size} children",
                                    fontSize = 11.sp,
                                    color = MutedGray
                                )
                            }

                            Button(
                                onClick = { viewModel.convertSelectedInstanceToSkill() },
                                colors = ButtonDefaults.buttonColors(containerColor = StarkWhite),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Convert Branch", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF1E222D), thickness = 0.8.dp)
                        Spacer(Modifier.height(10.dp))

                        // Properties List
                        if (selectedInstance.properties.isEmpty()) {
                            Text("No additional properties stored", fontSize = 11.sp, color = SubtleGray)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                selectedInstance.properties.forEach { (k, v) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(k, fontSize = 11.sp, color = MutedGray, fontFamily = FontFamily.Monospace)
                                        Text(v, fontSize = 11.sp, color = StarkWhite, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerRowItem(
    instance: RobloxInstance,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFF171B26) else Color(0xFF0A0C11)
    val border = if (isSelected) Color(0xFF38BDF8) else Color(0xFF161922)

    val icon = when (instance.className) {
        "ParticleEmitter" -> Icons.Default.Grain
        "MeshPart", "SpecialMesh" -> Icons.Default.Category
        "Camera" -> Icons.Default.Videocam
        "Sound" -> Icons.AutoMirrored.Filled.VolumeUp
        "Attachment" -> Icons.Default.Adjust
        "Model" -> Icons.Default.FolderSpecial
        "Folder" -> Icons.Default.Folder
        else -> Icons.Default.DataObject
    }

    val iconTint = when (instance.className) {
        "ParticleEmitter" -> Color(0xFFF43F5E)
        "MeshPart", "SpecialMesh" -> Color(0xFF38BDF8)
        "Camera" -> Color(0xFFA855F7)
        "Sound" -> Color(0xFF22C55E)
        else -> Color(0xFF94A3B8)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
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
                color = MutedGray,
                fontFamily = FontFamily.Monospace
            )
        }
        if (instance.children.isNotEmpty()) {
            Text(
                text = "${instance.children.size}",
                fontSize = 11.sp,
                color = SubtleGray,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141720))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun flattenTree(root: RobloxInstance): List<RobloxInstance> {
    val list = mutableListOf<RobloxInstance>()
    fun traverse(inst: RobloxInstance) {
        list.add(inst)
        inst.children.forEach { traverse(it) }
    }
    traverse(root)
    return list
}
