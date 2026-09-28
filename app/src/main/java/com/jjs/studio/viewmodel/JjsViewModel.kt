package com.jjs.studio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.JjsSkill
import com.jjs.studio.model.NodeKind
import com.jjs.studio.model.RobloxInstance
import com.jjs.studio.parser.JjsImportExport
import com.jjs.studio.parser.PresetLibrary
import com.jjs.studio.parser.RobloxXmlParser
import com.jjs.studio.util.ColorUtils
import com.jjs.studio.util.Palette
import com.jjs.studio.util.RgbColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    CONVERT,
    EDITOR,
    EXPLORER
}

data class JjsUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val skills: List<JjsSkill> = emptyList(),
    val currentSkillIndex: Int = 0,
    val currentBranch: String = "Default",
    val selectedNodeId: String? = null,
    val explorerRoot: RobloxInstance? = null,
    val explorerSearch: String = "",

    // Conversion settings
    val skillName: String = "Hollow_Purple",
    val skillKey: Int = 1,
    val duration: Double = 3.5,
    val position: String = "0, 0, 0",
    val packMode: Boolean = false,
    val branchMode: Boolean = false,
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
        // Load default preset skills on start
        val presets = PresetLibrary.getSampleSkills()
        _uiState.update { it.copy(skills = presets, currentSkillIndex = 0) }
        updateFromCurrentSkill()
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

    fun updateSkillSettings(
        name: String? = null,
        key: Int? = null,
        duration: Double? = null,
        position: String? = null,
        packMode: Boolean? = null,
        branchMode: Boolean? = null,
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
                runOnServer = runOnServer ?: state.runOnServer,
                parseCameras = parseCameras ?: state.parseCameras,
                parseMeshes = parseMeshes ?: state.parseMeshes,
                recolorEnabled = recolorEnabled ?: state.recolorEnabled
            )
            // also update current skill
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
            NodeKind.WAIT -> JjsNode(kind = kind, name = "Wait", time = 0.2)
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

    fun updateSelectedNode(transform: (JjsNode) -> Unit) {
        val node = selectedNode ?: return
        transform(node)
        _uiState.update { it.copy() } // Trigger recomposition
        recomputeExports()
    }

    fun loadImportCode(input: String) {
        viewModelScope.launch {
            try {
                val decoded = JjsImportExport.decodeImport(input)
                if (decoded.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            skills = decoded,
                            currentSkillIndex = 0,
                            currentBranch = "Default",
                            selectedNodeId = null,
                            isImportSheetVisible = false
                        )
                    }
                    updateFromCurrentSkill()
                    recomputeExports()
                    showStatus("Successfully loaded ${decoded.size} skill(s)")
                } else {
                    // Try parsing as XML
                    val instance = RobloxXmlParser.parseXml(input)
                    if (instance.countDescendants > 0) {
                        val skill = RobloxXmlParser.convertInstanceTreeToSkill(instance)
                        _uiState.update {
                            it.copy(
                                skills = listOf(skill),
                                currentSkillIndex = 0,
                                explorerRoot = instance,
                                isImportSheetVisible = false
                            )
                        }
                        updateFromCurrentSkill()
                        recomputeExports()
                        showStatus("Imported Roblox place hierarchy (${instance.countDescendants} items)")
                    } else {
                        showStatus("Could not parse input. Check JSON or KLUv format.")
                    }
                }
            } catch (e: Exception) {
                showStatus("Import error: ${e.localizedMessage}")
            }
        }
    }

    fun loadXmlFileContent(xml: String) {
        try {
            val root = RobloxXmlParser.parseXml(xml)
            val skill = RobloxXmlParser.convertInstanceTreeToSkill(root)
            _uiState.update {
                it.copy(
                    skills = listOf(skill) + it.skills,
                    currentSkillIndex = 0,
                    explorerRoot = root
                )
            }
            updateFromCurrentSkill()
            recomputeExports()
            showStatus("Parsed ${root.countDescendants} Roblox instances")
        } catch (e: Exception) {
            showStatus("Failed to parse Roblox file: ${e.message}")
        }
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
        val skillsToExport = if (state.skills.isNotEmpty()) state.skills else listOf(PresetLibrary.createHollowPurplePreset())
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
