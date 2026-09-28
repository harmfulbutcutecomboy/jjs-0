package com.jjs.studio.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class NodeKind {
    PARTICLE,
    VISUAL_MESH,
    VISUAL_CAMERA,
    SFX,
    WAIT,
    CONNECT,
    TAG,
    BRANCH;

    val displayName: String
        get() = when (this) {
            PARTICLE -> "Particle"
            VISUAL_MESH -> "Visual Mesh"
            VISUAL_CAMERA -> "Camera"
            SFX -> "Sound FX"
            WAIT -> "Wait"
            CONNECT -> "Connect"
            TAG -> "Tag"
            BRANCH -> "Branch"
        }
}

data class JjsNode(
    val id: String = UUID.randomUUID().toString(),
    val kind: NodeKind,
    var name: String = "",
    var time: Double = 0.0,

    // Particle properties
    var texture: String = "0",
    var color: String = "255, 51, 85",
    var size: String = "1.0, 0.2",
    var speed: Double = 10.0,
    var rate: Double = 15.0,
    var lifetime: Double = 1.0,
    var spreadAngle: String = "0.00, 0.00, 0.00",
    var flipbookMode: String = "OneShot",
    var drag: Double = 0.0,
    var squash: String = "0.00, 0.00",
    var bodyPart: String = "HumanoidRootPart",
    var clientSided: Boolean = false,
    var cancelOnInterrupt: Boolean = false,
    var emitCount: Int = 1,

    // Visual Mesh / Camera properties
    var meshId: String = "0",
    var position: String = "0, 0, 0",
    var visualSize: String = "2, 2, 2",
    var altColor: String = "247, 215, 248",
    var runOnServer: Boolean = true,
    var cframe: String = "0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1",
    var fov: Double = 70.0,

    // SFX properties
    var soundId: String = "0",
    var volume: Double = 1.0,
    var playbackSpeed: Double = 1.0,

    // Connect / Tag / Branch
    var signal: String = "Hit",
    var range: String = "inf",
    var tagLabel: String = "VFX",
    var tagSet: Boolean = true,
    var targetBranch: String = "Default"
) {
    fun copyNode(): JjsNode = this.copy(id = UUID.randomUUID().toString(), name = if (name.isNotBlank()) "$name (Copy)" else "")
}

data class JjsSkill(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Skill",
    var key: Int = 1,
    var duration: Double = 0.0,
    var position: String = "0, 0, 0",
    var branches: MutableMap<String, MutableList<JjsNode>> = mutableMapOf("Default" to mutableListOf())
) {
    fun getBranch(name: String): MutableList<JjsNode> {
        return branches.getOrPut(name) { mutableListOf() }
    }

    val totalNodesCount: Int
        get() = branches.values.sumOf { it.size }
}

data class RobloxInstance(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val className: String,
    val properties: Map<String, String> = emptyMap(),
    val children: MutableList<RobloxInstance> = mutableListOf()
) {
    val isFolder: Boolean
        get() = className in listOf("Folder", "Configuration", "Actor", "Model", "Workspace")

    val countDescendants: Int
        get() = children.size + children.sumOf { it.countDescendants }

    fun findParticles(): List<RobloxInstance> {
        val list = mutableListOf<RobloxInstance>()
        if (className == "ParticleEmitter") list.add(this)
        children.forEach { list.addAll(it.findParticles()) }
        return list
    }
}
