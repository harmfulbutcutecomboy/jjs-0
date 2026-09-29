package com.jjs.studio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjs.studio.model.VfxTreeNode
import com.jjs.studio.parser.RobloxPlaceParser
import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.JjsSkill
import com.jjs.studio.model.NodeKind
import com.jjs.studio.model.RobloxInstance
import com.jjs.studio.parser.JjsImportExport
import com.jjs.studio.parser.RobloxFileParser
import com.jjs.studio.parser.RobloxXmlParser
import com.jjs.studio.util.ColorUtils
import com.jjs.studio.util.Palette
import com.jjs.studio.util.RgbColor
import com.jjs.studio.util.ZstdHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class AppScreen {
    HOME,
    CONVERT,
    EDITOR,
    EXPLORER,
    CODE_EDITOR
}

data class QueuedRobloxFile(
    val name: String,
    val size: Long,
    val bytes: ByteArray
)

data class JjsUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val skills: List<JjsSkill> = emptyList(),
    val currentSkillIndex: Int = 0,
    val currentBranch: String = "Default",
    val selectedNodeId: String? = null,
    val explorerRoot: RobloxInstance? = null,
    val selectedExplorerInstanceId: String? = null,
    val vfxTreeRoot: VfxTreeNode? = null,
    val browseId: String = "root",
    val vfxNodeMap: Map<String, VfxTreeNode> = emptyMap(),
    val selectedVfxNodeId: String? = null,
    val checkedVfxNodeIds: Set<String> = emptySet(),
    val explorerSearch: String = "",
    val loadedFileName: String? = null,
    val queuedFiles: List<QueuedRobloxFile> = emptyList(),

    // Conversion settings (matches original app.js state)
    val skillName: String = "Custom_Skill",
    val skillKey: Int = 1,
    val duration: Double = 0.0,
    val position: String = "0, 0, 0",
    val packMode: Boolean = false,
    val branchMode: Boolean = false,
    val sectionMark: Boolean = false,
    val runOnServer: Boolean = true,
    val parseCameras: Boolean = true,
    val parseMeshes: Boolean = true,
    val recolorEnabled: Boolean = false,
    val detectedPalette: Palette = Palette(),
    val targetPalette: Palette = Palette(
        main = RgbColor(255, 51, 85),
        accent = RgbColor(247, 215, 248),
        other = RgbColor(255, 255, 255)
    ),

    // Import Code Editor State
    val codeEditorText: String = "",
    val codeEditorStatus: String = "Ready",
    val isDecompiled: Boolean = false,

    // Export cached values
    val liveJson: String = "",
    val liveKluv: String = "",

    // UI Dialogs & Feedback
    val isImportSheetVisible: Boolean = false,
    val isExportSheetVisible: Boolean = false,
    val isAddNodeSheetVisible: Boolean = false,
    val statusMessage: String? = null
)

class JjsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(JjsUiState())
    val uiState: StateFlow<JjsUiState> = _uiState.asStateFlow()

    init {
        // Start with clean initial workspace (no presets)
        val initialSkill = JjsSkill(
            name = "Skill_1",
            key = 1,
            duration = 0.0,
            position = "0, 0, 0"
        ).apply {
            branches["Default"] = mutableListOf(
                JjsNode(
                    kind = NodeKind.PARTICLE,
                    name = "ParticleEmitter_1",
                    texture = "1084991219",
                    color = "255,255,255 255,255,255",
                    rate = 20.0,
                    speedRange = "0.00, 0.00",
                    lifetimeRange = "0.50, 1.00",
                    time = 0.0
                )
            )
        }

        _uiState.update {
            it.copy(
                skills = listOf(initialSkill),
                currentSkillIndex = 0,
                skillName = initialSkill.name,
                skillKey = initialSkill.key,
                duration = initialSkill.duration
            )
        }
        recomputeExports()
    }

    val currentSkill: JjsSkill?
        get() {
            val s = _uiState.value
            return s.skills.getOrNull(s.currentSkillIndex)
        }

    val currentNodes: List<JjsNode>
        get() {
            val skill = currentSkill ?: return emptyList()
            val branch = _uiState.value.currentBranch
            return skill.branches[branch] ?: skill.branches["Default"] ?: emptyList()
        }

    val selectedNode: JjsNode?
        get() {
            val id = _uiState.value.selectedNodeId ?: return null
            return currentNodes.find { it.id == id }
        }

    val selectedExplorerInstance: RobloxInstance?
        get() {
            val id = _uiState.value.selectedExplorerInstanceId ?: return null
            val root = _uiState.value.explorerRoot ?: return null
            return findInstanceById(root, id)
        }

    private fun findInstanceById(instance: RobloxInstance, id: String): RobloxInstance? {
        if (instance.id == id) return instance
        for (child in instance.children) {
            val found = findInstanceById(child, id)
            if (found != null) return found
        }
        return null
    }

    fun setScreen(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun selectSkill(index: Int) {
        if (index in _uiState.value.skills.indices) {
            _uiState.update {
                it.copy(
                    currentSkillIndex = index,
                    currentBranch = "Default",
                    selectedNodeId = null
                )
            }
            updateFromCurrentSkill()
            recomputeExports()
        }
    }

    fun createNewSkill(name: String = "New_Skill") {
        val newSkill = JjsSkill(
            name = name,
            key = (_uiState.value.skills.size + 1).coerceAtMost(9),
            duration = 0.0
        ).apply {
            branches["Default"] = mutableListOf(
                JjsNode(
                    kind = NodeKind.PARTICLE,
                    name = "Emitter_1",
                    texture = "1084991219",
                    time = 0.0
                )
            )
        }
        _uiState.update {
            it.copy(
                skills = it.skills + newSkill,
                currentSkillIndex = it.skills.size,
                currentBranch = "Default"
            )
        }
        updateFromCurrentSkill()
        recomputeExports()
        showStatus("Created skill: $name")
    }

    fun selectBranch(branch: String) {
        _uiState.update { it.copy(currentBranch = branch, selectedNodeId = null) }
    }

    fun addBranch(name: String) {
        val skill = currentSkill ?: return
        val clean = name.trim().ifEmpty { "Branch_${skill.branches.size + 1}" }
        if (!skill.branches.containsKey(clean)) {
            skill.branches[clean] = mutableListOf()
            _uiState.update { it.copy(currentBranch = clean) }
            recomputeExports()
        }
    }

    fun deleteBranch(branch: String) {
        val skill = currentSkill ?: return
        if (branch == "Default") {
            showStatus("Cannot delete Default branch")
            return
        }
        skill.branches.remove(branch)
        _uiState.update { it.copy(currentBranch = "Default") }
        recomputeExports()
    }

    fun selectNode(nodeId: String?) {
        _uiState.update {
            it.copy(selectedNodeId = if (it.selectedNodeId == nodeId) null else nodeId)
        }
    }

    fun selectExplorerInstance(instanceId: String?) {
        _uiState.update {
            it.copy(selectedExplorerInstanceId = if (it.selectedExplorerInstanceId == instanceId) null else instanceId)
        }
    }

    fun updateSkillSettings(
        name: String? = null,
        key: Int? = null,
        duration: Double? = null,
        position: String? = null,
        packMode: Boolean? = null,
        branchMode: Boolean? = null,
        sectionMark: Boolean? = null,
        runOnServer: Boolean? = null,
        parseCameras: Boolean? = null,
        parseMeshes: Boolean? = null,
        recolorEnabled: Boolean? = null
    ) {
        _uiState.update { state ->
            val updated = state.copy(
                skillName = name ?: state.skillName,
                skillKey = key ?: state.skillKey,
                duration = duration ?: state.duration,
                position = position ?: state.position,
                packMode = packMode ?: state.packMode,
                branchMode = branchMode ?: state.branchMode,
                sectionMark = sectionMark ?: state.sectionMark,
                runOnServer = runOnServer ?: state.runOnServer,
                parseCameras = parseCameras ?: state.parseCameras,
                parseMeshes = parseMeshes ?: state.parseMeshes,
                recolorEnabled = recolorEnabled ?: state.recolorEnabled
            )
            currentSkill?.let { s ->
                name?.let { s.name = it }
                key?.let { s.key = it }
                duration?.let { s.duration = it }
                position?.let { s.position = it }
            }
            updated
        }
        recomputeExports()
    }

    fun detectPaletteFromNodes(particles: List<JjsNode>) {
        val colorCounts = mutableMapOf<RgbColor, Int>()
        for (p in particles) {
            val rgbs = parseRgbList(p.color)
            for (c in rgbs) {
                if (c.r + c.g + c.b > 12) {
                    colorCounts[c] = (colorCounts[c] ?: 0) + 1
                }
            }
        }
        val sorted = colorCounts.entries.sortedByDescending { it.value }.map { it.key }
        if (sorted.isNotEmpty()) {
            val main = sorted[0]
            val accent = sorted.getOrNull(1) ?: main
            val other = sorted.getOrNull(2) ?: RgbColor(255, 255, 255)
            val detected = Palette(main = main, accent = accent, other = other)
            _uiState.update { it.copy(detectedPalette = detected) }
        }
    }

    private fun parseRgbList(str: String): List<RgbColor> {
        val out = mutableListOf<RgbColor>()
        val regex = Regex("""(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})""")
        regex.findAll(str).forEach { m ->
            val r = m.groupValues[1].toIntOrNull() ?: 255
            val g = m.groupValues[2].toIntOrNull() ?: 255
            val b = m.groupValues[3].toIntOrNull() ?: 255
            out.add(RgbColor(r, g, b))
        }
        return out
    }

    fun updateTargetPalette(main: RgbColor? = null, accent: RgbColor? = null, other: RgbColor? = null) {
        _uiState.update { state ->
            val curr = state.targetPalette
            state.copy(
                targetPalette = Palette(
                    main = main ?: curr.main,
                    accent = accent ?: curr.accent,
                    other = other ?: curr.other
                )
            )
        }
        recomputeExports()
    }

    fun addNode(kind: NodeKind) {
        val skill = currentSkill ?: return
        val branchList = skill.getBranch(_uiState.value.currentBranch)
        val lastTime = branchList.maxOfOrNull { it.time } ?: 0.0
        val newNode = when (kind) {
            NodeKind.PARTICLE -> JjsNode(kind = kind, name = "Particle", time = lastTime + 0.1)
            NodeKind.VISUAL_MESH -> JjsNode(kind = kind, name = "Mesh", time = lastTime + 0.1)
            NodeKind.VISUAL_CAMERA -> JjsNode(kind = kind, name = "Camera", time = lastTime + 0.1)
            NodeKind.SFX -> JjsNode(kind = kind, name = "Sound", time = lastTime)
            NodeKind.WAIT -> JjsNode(kind = kind, name = "Wait", time = 0.1)
            NodeKind.CONNECT -> JjsNode(kind = kind, name = "Connect", time = lastTime + 0.05)
            NodeKind.TAG -> JjsNode(kind = kind, name = "Tag", time = lastTime)
            NodeKind.BRANCH -> JjsNode(kind = kind, name = "Branch", time = lastTime + 0.1)
        }
        branchList.add(newNode)
        _uiState.update { it.copy(selectedNodeId = newNode.id, isAddNodeSheetVisible = false) }
        recomputeExports()
        showStatus("Added ${kind.displayName} node")
    }

    fun deleteNode(nodeId: String) {
        val skill = currentSkill ?: return
        val branchList = skill.getBranch(_uiState.value.currentBranch)
        branchList.removeAll { it.id == nodeId }
        _uiState.update { it.copy(selectedNodeId = null) }
        recomputeExports()
        showStatus("Node removed")
    }

    fun duplicateNode(node: JjsNode) {
        val skill = currentSkill ?: return
        val branchList = skill.getBranch(_uiState.value.currentBranch)
        val copy = node.copyNode()
        copy.time += 0.05
        val idx = branchList.indexOfFirst { it.id == node.id }
        if (idx != -1) {
            branchList.add(idx + 1, copy)
        } else {
            branchList.add(copy)
        }
        _uiState.update { it.copy(selectedNodeId = copy.id) }
        recomputeExports()
        showStatus("Duplicated ${node.kind.displayName}")
    }

    fun moveNode(fromIndex: Int, toIndex: Int) {
        val skill = currentSkill ?: return
        val branchList = skill.getBranch(_uiState.value.currentBranch)
        if (fromIndex in branchList.indices && toIndex in branchList.indices && fromIndex != toIndex) {
            val item = branchList.removeAt(fromIndex)
            branchList.add(toIndex, item)
            _uiState.update { it.copy(selectedNodeId = item.id) }
            recomputeExports()
        }
    }

    fun updateSelectedNode(transform: (JjsNode) -> Unit) {
        val node = selectedNode ?: return
        transform(node)
        _uiState.update { it.copy() }
        recomputeExports()
    }

    // ==========================================
    // FILE QUEUE (from original app.js)
    // ==========================================
    fun queueFile(name: String, bytes: ByteArray) {
        val qFile = QueuedRobloxFile(name = name, size = bytes.size.toLong(), bytes = bytes)
        _uiState.update { it.copy(queuedFiles = it.queuedFiles + qFile) }
        showStatus("Queued $name (${bytes.size / 1024} KB)")
    }

    fun removeQueuedFile(index: Int) {
        _uiState.update {
            if (index in it.queuedFiles.indices) {
                val updated = it.queuedFiles.toMutableList()
                updated.removeAt(index)
                it.copy(queuedFiles = updated)
            } else it
        }
    }

    fun clearQueue() {
        _uiState.update { it.copy(queuedFiles = emptyList()) }
        showStatus("File queue cleared")
    }

    fun parseQueuedFiles() {
        val queue = _uiState.value.queuedFiles
        if (queue.isEmpty()) return
        val cameras = _uiState.value.parseCameras
        val meshes = _uiState.value.parseMeshes
        viewModelScope.launch {
            try {
                showStatus("Parsing ${queue.size} file(s)...")
                val root = RobloxInstance(name = "Workspace", className = "Workspace")
                for (q in queue) {
                    val parsed = RobloxFileParser.parse(q.bytes, q.name, cameras, meshes)
                    root.children.add(parsed)
                }
                _uiState.update {
                    it.copy(
                        explorerRoot = root,
                        loadedFileName = if (queue.size == 1) queue[0].name else "${queue.size} Files",
                        queuedFiles = emptyList(),
                        currentScreen = AppScreen.EXPLORER
                    )
                }
                val totalParticles = root.findDescendantsByClass("ParticleEmitter").size
                val totalCameras = root.findDescendantsByClass("Camera").size
                val totalMeshes = root.findDescendantsByClass("MeshPart").size + root.findDescendantsByClass("SpecialMesh").size
                showStatus("Loaded ${queue.size} files • $totalParticles Particles • $totalCameras Cameras • $totalMeshes Meshes")
            } catch (e: Exception) {
                showStatus("Parse error: ${e.message}")
            }
        }
    }

    // ==========================================
    // ROBLOX FILE PARSER (.rbxl, .rbxm, .rbxlx, .rbxmx)
    // ==========================================
    fun loadMultipleRobloxFiles(files: List<Pair<String, ByteArray>>) {
        if (files.isEmpty()) return
        val cameras = _uiState.value.parseCameras
        val meshes = _uiState.value.parseMeshes
        viewModelScope.launch {
            try {
                showStatus("Parsing ${files.size} file(s)...")
                val root = VfxTreeNode(id = "root", name = "Workspace", className = "Folder", type = "ParticleFolder")
                for ((fileName, bytes) in files) {
                    val parsed = RobloxPlaceParser.parse(bytes, fileName, cameras, meshes)
                    parsed.parentId = "root"
                    root.children.add(parsed)
                }
                val nodeMap = mutableMapOf<String, VfxTreeNode>()
                indexVfxTree(root, nodeMap)
                val totalParticles = root.collectAllParticles().size
                val totalCameras = root.collectAllCameras().size
                val totalMeshes = root.collectAllMeshes().size

                _uiState.update {
                    it.copy(
                        vfxTreeRoot = root,
                        browseId = "root",
                        vfxNodeMap = nodeMap,
                        selectedVfxNodeId = root.children.firstOrNull()?.id ?: "root",
                        loadedFileName = if (files.size == 1) files[0].first else "${files.size} Files",
                        currentScreen = AppScreen.EXPLORER
                    )
                }
                showStatus("Loaded ${files.size} files • $totalParticles Particles • $totalCameras Cameras • $totalMeshes Meshes")
            } catch (e: Exception) {
                showStatus("Parse error: ${e.message}")
            }
        }
    }

    fun loadRobloxFile(bytes: ByteArray, fileName: String) {
        val cameras = _uiState.value.parseCameras
        val meshes = _uiState.value.parseMeshes
        viewModelScope.launch {
            try {
                showStatus("Parsing $fileName (${bytes.size / 1024} KB)...")
                val parsed = RobloxPlaceParser.parse(bytes, fileName, cameras, meshes)
                val nodeMap = mutableMapOf<String, VfxTreeNode>()
                indexVfxTree(parsed, nodeMap)
                val totalParticles = parsed.collectAllParticles().size
                val totalCameras = parsed.collectAllCameras().size
                val totalMeshes = parsed.collectAllMeshes().size

                _uiState.update {
                    it.copy(
                        vfxTreeRoot = parsed,
                        browseId = "root",
                        vfxNodeMap = nodeMap,
                        selectedVfxNodeId = parsed.children.firstOrNull()?.id ?: "root",
                        loadedFileName = fileName,
                        currentScreen = AppScreen.EXPLORER
                    )
                }
                showStatus("Opened $fileName: $totalParticles Particles • $totalCameras Cameras • $totalMeshes Meshes")
            } catch (e: Exception) {
                showStatus("Error parsing Roblox file: ${e.localizedMessage}")
            }
        }
    }

    fun navigateToVfxNode(id: String) {
        _uiState.update { it.copy(browseId = id) }
    }

    fun navigateUpVfxTree() {
        val currentBrowseId = _uiState.value.browseId
        val parentId = _uiState.value.vfxNodeMap[currentBrowseId]?.parentId ?: "root"
        _uiState.update { it.copy(browseId = parentId) }
    }

    fun selectVfxNode(id: String) {
        val node = _uiState.value.vfxNodeMap[id]
        _uiState.update {
            it.copy(
                selectedVfxNodeId = id,
                selectedExplorerInstanceId = id
            )
        }
        if (node != null) {
            val particles = node.collectAllParticles()
            if (particles.isNotEmpty()) {
                detectPaletteFromNodes(particles)
            }
        }
    }

    fun toggleCheckVfxNode(id: String) {
        _uiState.update {
            val set = it.checkedVfxNodeIds.toMutableSet()
            if (set.contains(id)) set.remove(id) else set.add(id)
            it.copy(checkedVfxNodeIds = set)
        }
    }

    private fun indexVfxTree(node: VfxTreeNode, map: MutableMap<String, VfxTreeNode>) {
        map[node.id] = node
        for (child in node.children) {
            indexVfxTree(child, map)
        }
    }

    fun exportCurrentTargets() {
        val root = _uiState.value.vfxTreeRoot ?: return
        val map = _uiState.value.vfxNodeMap
        val checked = _uiState.value.checkedVfxNodeIds
        val selectedId = _uiState.value.selectedVfxNodeId
        val state = _uiState.value

        val targets = when {
            checked.isNotEmpty() -> checked.mapNotNull { map[it] }
            selectedId != null && map[selectedId] != null -> listOf(map[selectedId]!!)
            else -> listOf(root)
        }

        if (targets.isEmpty()) {
            showStatus("Nothing selected to convert")
            return
        }

        val skillList = mutableListOf<JjsSkill>()

        if (state.packMode && state.branchMode) {
            val skill = JjsSkill(name = state.skillName.ifBlank { "Converted_Pack" }, key = state.skillKey)
            var defaultDone = false
            targets.forEachIndexed { idx, target ->
                val lines = mutableListOf<JjsNode>()
                lines.addAll(target.collectAllParticles())
                lines.addAll(target.collectAllMeshes())
                lines.addAll(target.collectAllCameras())
                lines.addAll(target.collectAllSounds())
                if (lines.isNotEmpty()) {
                    if (!defaultDone) {
                        skill.branches["Default"] = lines
                        defaultDone = true
                    } else {
                        val bName = target.name.ifBlank { "Branch_${idx + 1}" }
                        skill.branches[bName] = lines
                    }
                }
            }
            skillList.add(skill)
        } else {
            targets.forEachIndexed { idx, target ->
                val lines = mutableListOf<JjsNode>()
                lines.addAll(target.collectAllParticles())
                lines.addAll(target.collectAllMeshes())
                lines.addAll(target.collectAllCameras())
                lines.addAll(target.collectAllSounds())
                if (lines.isNotEmpty()) {
                    val sName = if (targets.size == 1) state.skillName.ifBlank { target.name } else target.name
                    val sk = JjsSkill(name = sName, key = state.skillKey + idx)
                    sk.branches["Default"] = lines
                    skillList.add(sk)
                }
            }
        }

        if (skillList.isEmpty()) {
            showStatus("No VFX nodes found in selection")
            return
        }

        _uiState.update {
            it.copy(
                skills = skillList,
                currentSkillIndex = 0,
                currentBranch = "Default",
                currentScreen = AppScreen.HOME
            )
        }
        recomputeExports()
        showStatus("Converted ${skillList.size} skill(s) successfully!")
    }

    fun convertAllExplorerFxToSkill() {
        val root = _uiState.value.explorerRoot ?: run {
            showStatus("No Roblox file loaded")
            return
        }
        val state = _uiState.value
        val skillName = state.loadedFileName?.substringBeforeLast(".") ?: "Roblox_FX_Skill"

        if (state.packMode && state.branchMode) {
            // Branch Mode: each host becomes a branch of 1 skill
            val skill = JjsSkill(name = skillName, key = state.skillKey, duration = 0.0)
            val hosts = root.children.ifEmpty { listOf(root) }
            var defaultDone = false

            hosts.forEachIndexed { idx, host ->
                val branchNodes = RobloxXmlParser.convertInstanceTreeToSkill(
                    root = host,
                    skillName = host.name,
                    parseCameras = state.parseCameras,
                    parseMeshes = state.parseMeshes,
                    sectionMark = state.sectionMark,
                    runOnServer = state.runOnServer
                ).branches["Default"] ?: mutableListOf()

                if (!defaultDone) {
                    skill.branches["Default"] = branchNodes
                    defaultDone = true
                } else {
                    val bName = host.name.replace(" ", "_").ifBlank { "Branch_${idx + 1}" }
                    skill.branches[bName] = branchNodes
                }
            }

            _uiState.update {
                it.copy(
                    skills = listOf(skill) + it.skills,
                    currentSkillIndex = 0,
                    currentBranch = "Default",
                    currentScreen = AppScreen.EDITOR
                )
            }
            updateFromCurrentSkill()
            recomputeExports()
            showStatus("Packed into 1 skill with ${skill.branches.size} branches")
        } else if (state.packMode && !state.branchMode) {
            // Pack Mode: each host becomes a separate skill
            val hosts = root.children.ifEmpty { listOf(root) }
            val newSkills = hosts.mapIndexed { idx, host ->
                RobloxXmlParser.convertInstanceTreeToSkill(
                    root = host,
                    skillName = host.name.replace(" ", "_").ifBlank { "Skill_${state.skillKey + idx}" },
                    parseCameras = state.parseCameras,
                    parseMeshes = state.parseMeshes,
                    sectionMark = state.sectionMark,
                    runOnServer = state.runOnServer
                ).apply { key = state.skillKey + idx }
            }

            _uiState.update {
                it.copy(
                    skills = newSkills + it.skills,
                    currentSkillIndex = 0,
                    currentBranch = "Default",
                    currentScreen = AppScreen.EDITOR
                )
            }
            updateFromCurrentSkill()
            recomputeExports()
            showStatus("Packed ${newSkills.size} separate skills")
        } else {
            // Default single skill
            val converted = RobloxXmlParser.convertInstanceTreeToSkill(
                root = root,
                skillName = skillName,
                parseCameras = state.parseCameras,
                parseMeshes = state.parseMeshes,
                sectionMark = state.sectionMark,
                runOnServer = state.runOnServer
            )

            _uiState.update {
                it.copy(
                    skills = listOf(converted) + it.skills,
                    currentSkillIndex = 0,
                    currentBranch = "Default",
                    currentScreen = AppScreen.EDITOR
                )
            }
            updateFromCurrentSkill()
            recomputeExports()
            showStatus("Converted $skillName into timeline (${converted.branches["Default"]?.size ?: 0} nodes)")
        }
    }

    fun convertSelectedInstanceToSkill() {
        val selected = selectedExplorerInstance ?: run {
            showStatus("Select an instance from the explorer first")
            return
        }
        val state = _uiState.value
        val converted = RobloxXmlParser.convertInstanceTreeToSkill(
            root = selected,
            skillName = selected.name.replace(" ", "_"),
            parseCameras = state.parseCameras,
            parseMeshes = state.parseMeshes,
            sectionMark = state.sectionMark,
            runOnServer = state.runOnServer
        )

        _uiState.update {
            it.copy(
                skills = listOf(converted) + it.skills,
                currentSkillIndex = 0,
                currentBranch = "Default",
                currentScreen = AppScreen.EDITOR
            )
        }
        updateFromCurrentSkill()
        recomputeExports()
        showStatus("Converted ${selected.name} into timeline")
    }

    // ==========================================
    // IMPORT CODE EDITOR LOGIC
    // ==========================================
    fun updateCodeEditorText(text: String) {
        _uiState.update { it.copy(codeEditorText = text) }
    }

    fun setCodeFromCurrentSkill(formatAsKluv: Boolean) {
        val code = if (formatAsKluv) _uiState.value.liveKluv else _uiState.value.liveJson
        _uiState.update {
            it.copy(
                codeEditorText = code,
                codeEditorStatus = if (formatAsKluv) "Loaded KLUv bytecode" else "Loaded JJS JSON"
            )
        }
    }

    fun decodeAndLoadEditorCode() {
        val text = _uiState.value.codeEditorText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(codeEditorStatus = "Error: Input code is empty") }
            return
        }

        viewModelScope.launch {
            try {
                val decoded = JjsImportExport.decodeImport(text)
                if (decoded.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            skills = decoded,
                            currentSkillIndex = 0,
                            currentBranch = "Default",
                            selectedNodeId = null,
                            codeEditorStatus = "Successfully loaded ${decoded.size} skill(s)!",
                            currentScreen = AppScreen.EDITOR
                        )
                    }
                    updateFromCurrentSkill()
                    recomputeExports()
                    showStatus("Decompiled and loaded into timeline!")
                } else if (text.startsWith("<")) {
                    val root = RobloxXmlParser.parseXml(text)
                    val skill = RobloxXmlParser.convertInstanceTreeToSkill(root)
                    _uiState.update {
                        it.copy(
                            skills = listOf(skill) + it.skills,
                            currentSkillIndex = 0,
                            explorerRoot = root,
                            codeEditorStatus = "Parsed XML with ${root.countDescendants} items",
                            currentScreen = AppScreen.EXPLORER
                        )
                    }
                    updateFromCurrentSkill()
                    recomputeExports()
                    showStatus("Parsed Roblox XML hierarchy")
                } else {
                    _uiState.update { it.copy(codeEditorStatus = "Invalid format: Expected JSON or KLUv/...") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(codeEditorStatus = "Parse error: ${e.message}") }
            }
        }
    }

    fun formatJsonInEditor() {
        val text = _uiState.value.codeEditorText.trim()
        try {
            val formatted = if (text.startsWith("[")) {
                JSONArray(text).toString(2)
            } else if (text.startsWith("{")) {
                JSONObject(text).toString(2)
            } else if (ZstdHelper.isKluv(text)) {
                val decompressed = ZstdHelper.decompressKLUv(text)
                if (decompressed.startsWith("[")) JSONArray(decompressed).toString(2)
                else JSONObject(decompressed).toString(2)
            } else {
                text
            }
            _uiState.update {
                it.copy(
                    codeEditorText = formatted,
                    codeEditorStatus = "Formatted JSON"
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(codeEditorStatus = "Format error: ${e.message}") }
        }
    }

    fun loadImportCode(input: String) {
        updateCodeEditorText(input)
        decodeAndLoadEditorCode()
    }

    fun setExplorerSearch(query: String) {
        _uiState.update { it.copy(explorerSearch = query) }
    }

    fun toggleImportSheet(show: Boolean) {
        _uiState.update { it.copy(isImportSheetVisible = show) }
    }

    fun toggleExportSheet(show: Boolean) {
        _uiState.update { it.copy(isExportSheetVisible = show) }
    }

    fun toggleAddNodeSheet(show: Boolean) {
        _uiState.update { it.copy(isAddNodeSheetVisible = show) }
    }

    fun showStatus(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun updateFromCurrentSkill() {
        val skill = currentSkill ?: return
        _uiState.update {
            it.copy(
                skillName = skill.name,
                skillKey = skill.key,
                duration = skill.duration,
                position = skill.position
            )
        }
        detectPaletteFromSkill(skill)
    }

    private fun detectPaletteFromSkill(skill: JjsSkill) {
        val allParticles = skill.branches.values.flatten().filter { it.kind == NodeKind.PARTICLE }
        val allColors = allParticles.flatMap { RgbColor.parseRgbList(it.color) }
        val detected = ColorUtils.detectPaletteFromColors(allColors)
        _uiState.update { it.copy(detectedPalette = detected) }
    }

    fun recomputeExports() {
        val state = _uiState.value
        val skillsToExport = if (state.skills.isNotEmpty()) {
            state.skills
        } else {
            listOf(
                JjsSkill(name = "Skill_1", key = 1, duration = 0.0).apply {
                    branches["Default"] = mutableListOf(
                        JjsNode(kind = NodeKind.PARTICLE, name = "Particle_1", time = 0.0)
                    )
                }
            )
        }

        val json = JjsImportExport.exportToJson(
            skills = skillsToExport,
            recolor = state.recolorEnabled,
            detectedPalette = state.detectedPalette,
            targetPalette = state.targetPalette
        )
        val kluv = JjsImportExport.exportToKluv(
            skills = skillsToExport,
            recolor = state.recolorEnabled,
            detectedPalette = state.detectedPalette,
            targetPalette = state.targetPalette
        )
        _uiState.update { it.copy(liveJson = json, liveKluv = kluv) }
    }
}
