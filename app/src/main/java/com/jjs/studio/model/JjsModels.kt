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

    // Particle Emitter Properties (matches game JSON exact spec)
    var texture: String = "0",
    var color: String = "255,255,255 255,255,255",
    var size: String = "1.0, 0.2",
    var speedRange: String = "0.00, 0.00",
    var rate: Double = 20.0,
    var lifetimeRange: String = "0.50, 1.00",
    var spreadAngle: String = "0.00, 0.00, 0.00",
    var flipbookMode: String = "Loop",
    var flipbookSize: String = "1, 1",
    var flipbookFramerate: String = "1.00, 1.00",
    var rotSpeed: String = "0.00, 0.00",
    var rotation: String = "0.00, 0.00",
    var drag: Double = 0.0,
    var squash: String = "0.00,0.00",
    var transparency: String = "0.00,0.00",
    var brightness: Double = 1.0,
    var lightEmission: Double = 1.0,
    var lightInfluence: Double = 0.0,
    var zOffset: Double = 0.0,
    var emitCount: Int = 1,
    var lockToPart: Boolean = true,
    var orientationType: String = "FacingCamera",
    var emissionDirection: String = "Top",
    var acceleration: String = "0, 0, 0",
    var bodyPart: String = "HumanoidRootPart",
    var clientSided: Boolean = false,
    var cancelOnInterrupt: Boolean = false,
    var runOnServer: Boolean = true,

    // Visual Mesh / Camera properties
    var meshId: String = "0",
    var position: String = "0, 0, 0",
    var visualSize: String = "2, 2, 2",
    var altColor: String = "247, 215, 248",
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
    // Backwards-compatible getters & setters
    var speed: Double
        get() = speedRange.split(",").firstOrNull()?.trim()?.toDoubleOrNull() ?: 10.0
        set(value) { speedRange = "$value, $value" }

    var lifetime: Double
        get() = lifetimeRange.split(",").firstOrNull()?.trim()?.toDoubleOrNull() ?: 1.0
        set(value) { lifetimeRange = "$value, $value" }

    fun copyNode(): JjsNode = this.copy(id = UUID.randomUUID().toString(), name = if (name.isNotBlank()) "$name (Copy)" else "")
}

data class JjsSkill(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Skill",
    var key: Int = 1,
    var add: Boolean = false,
    var cooldown: Double = 0.0,
    var toolTip: String = "",
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

    fun findDescendantsByClass(targetClass: String): List<RobloxInstance> {
        val list = mutableListOf<RobloxInstance>()
        if (className.equals(targetClass, ignoreCase = true)) list.add(this)
        children.forEach { list.addAll(it.findDescendantsByClass(targetClass)) }
        return list
    }
}
