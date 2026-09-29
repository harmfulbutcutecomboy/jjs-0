package com.jjs.studio.model

/**
 * Node in the Roblox VFX Hierarchy, exactly mirroring `app.js` and `parser.js`:
 * - id: unique string id
 * - name: instance name
 * - className: Roblox class name (Folder, Model, Part, MeshPart, ParticleEmitter, etc.)
 * - type: "ParticleFolder" or "ParticleHost"
 * - count: total VFX count (own + children)
 * - particles: list of JjsNode (K_NAME = "PARTICLE")
 * - meshes: list of JjsNode (K_NAME = "VISUAL", EFFECT = "Mesh")
 * - cameras: list of JjsNode (K_NAME = "VISUAL", EFFECT = "Camera")
 * - sounds: list of JjsNode (K_NAME = "SFX")
 * - parentId: parent node id or "root"
 * - children: child tree nodes
 */
data class VfxTreeNode(
    var id: String = "root",
    var name: String = "Workspace",
    var className: String = "Folder",
    var type: String = "ParticleFolder", // ParticleFolder or ParticleHost
    var parentId: String? = null,
    val children: MutableList<VfxTreeNode> = mutableListOf(),
    val particles: MutableList<JjsNode> = mutableListOf(),
    val meshes: MutableList<JjsNode> = mutableListOf(),
    val cameras: MutableList<JjsNode> = mutableListOf(),
    val sounds: MutableList<JjsNode> = mutableListOf(),
    var count: Int = 0
) {
    fun hostCount(): Int {
        if (count > 0) return count
        var c = particles.size + meshes.size + cameras.size + sounds.size
        for (child in children) {
            c += child.hostCount()
        }
        return c
    }

    fun collectAllParticles(): List<JjsNode> {
        val out = mutableListOf<JjsNode>()
        out.addAll(particles)
        for (child in children) {
            out.addAll(child.collectAllParticles())
        }
        return out
    }

    fun collectAllMeshes(): List<JjsNode> {
        val out = mutableListOf<JjsNode>()
        out.addAll(meshes)
        for (child in children) {
            out.addAll(child.collectAllMeshes())
        }
        return out
    }

    fun collectAllCameras(): List<JjsNode> {
        val out = mutableListOf<JjsNode>()
        out.addAll(cameras)
        for (child in children) {
            out.addAll(child.collectAllCameras())
        }
        return out
    }

    fun collectAllSounds(): List<JjsNode> {
        val out = mutableListOf<JjsNode>()
        out.addAll(sounds)
        for (child in children) {
            out.addAll(child.collectAllSounds())
        }
        return out
    }

    fun isFolderLike(): Boolean {
        return type == "ParticleFolder" ||
                className.equals("Folder", ignoreCase = true) ||
                className.equals("Configuration", ignoreCase = true) ||
                className.equals("Model", ignoreCase = true) ||
                children.isNotEmpty()
    }
}
