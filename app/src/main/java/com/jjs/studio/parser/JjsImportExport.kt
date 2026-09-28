package com.jjs.studio.parser

import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.JjsSkill
import com.jjs.studio.model.NodeKind
import com.jjs.studio.util.Palette
import com.jjs.studio.util.ColorUtils
import com.jjs.studio.util.ZstdHelper
import org.json.JSONArray
import org.json.JSONObject

object JjsImportExport {

    fun decodeImport(input: String): List<JjsSkill> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        val jsonString = if (ZstdHelper.isKluv(trimmed)) {
            ZstdHelper.decompressKLUv(trimmed)
        } else {
            trimmed
        }

        val skills = mutableListOf<JjsSkill>()
        try {
            if (jsonString.startsWith("[")) {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    skills.add(parseSkillJson(obj))
                }
            } else if (jsonString.startsWith("{")) {
                val obj = JSONObject(jsonString)
                // Might be single skill or object with skills array
                if (obj.has("skills") || obj.has("Skills")) {
                    val arr = obj.optJSONArray("skills") ?: obj.optJSONArray("Skills")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            skills.add(parseSkillJson(item))
                        }
                    }
                } else {
                    skills.add(parseSkillJson(obj))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return skills
    }

    private fun parseSkillJson(obj: JSONObject): JjsSkill {
        val name = obj.optString("SkillName", obj.optString("name", "Converted_Skill"))
        val key = obj.optInt("SkillKey", obj.optInt("key", 1))
        val duration = obj.optDouble("Duration", obj.optDouble("duration", 0.0))
        val position = obj.optString("Position", obj.optString("position", "0, 0, 0"))

        val skill = JjsSkill(
            name = name,
            key = key,
            duration = duration,
            position = position
        )

        // Parse Default branch lines
        val lineArr = obj.optJSONArray("Line") ?: obj.optJSONArray("line")
        val defaultNodes = mutableListOf<JjsNode>()
        if (lineArr != null) {
            for (i in 0 until lineArr.length()) {
                val nodeObj = lineArr.optJSONObject(i) ?: continue
                defaultNodes.add(parseNodeJson(nodeObj))
            }
        }
        skill.branches["Default"] = defaultNodes

        // Parse additional branches
        val branchesObj = obj.optJSONObject("Branches") ?: obj.optJSONObject("branches")
        if (branchesObj != null) {
            val keys = branchesObj.keys()
            while (keys.hasNext()) {
                val bName = keys.next()
                val bArr = branchesObj.optJSONArray(bName) ?: continue
                val bNodes = mutableListOf<JjsNode>()
                for (i in 0 until bArr.length()) {
                    val nodeObj = bArr.optJSONObject(i) ?: continue
                    bNodes.add(parseNodeJson(nodeObj))
                }
                skill.branches[bName] = bNodes
            }
        }

        return skill
    }

    private fun parseNodeJson(obj: JSONObject): JjsNode {
        val kName = obj.optString("K_NAME", obj.optString("kind", "PARTICLE")).uppercase()
        val kind = when (kName) {
            "PARTICLE" -> NodeKind.PARTICLE
            "VISUAL" -> {
                val effect = obj.optString("EFFECT", "Mesh")
                if (effect.equals("Camera", ignoreCase = true)) NodeKind.VISUAL_CAMERA else NodeKind.VISUAL_MESH
            }
            "SFX", "SOUND" -> NodeKind.SFX
            "WAIT" -> NodeKind.WAIT
            "CONNECT" -> NodeKind.CONNECT
            "TAG" -> NodeKind.TAG
            "BRANCH" -> NodeKind.BRANCH
            else -> NodeKind.PARTICLE
        }

        val node = JjsNode(
            kind = kind,
            name = obj.optString("NAME", obj.optString("name", "")),
            time = obj.optDouble("TIME", 0.0),
            texture = obj.optString("TEXTURE", "0"),
            color = obj.optString("COLOR", "255, 51, 85"),
            size = obj.optString("SIZE", "1.0, 0.2"),
            speed = obj.optDouble("SPEED", 10.0),
            rate = obj.optDouble("RATE", 15.0),
            lifetime = obj.optDouble("LIFETIME", 1.0),
            spreadAngle = obj.optString("SPREAD ANGLE", "0.00, 0.00, 0.00"),
            flipbookMode = obj.optString("FLIPBOOK MODE", "OneShot"),
            drag = obj.optDouble("DRAG", 0.0),
            squash = obj.optString("SQUASH", "0.00, 0.00"),
            bodyPart = obj.optString("BODY PART", "HumanoidRootPart"),
            clientSided = obj.optBoolean("CLIENT SIDED", false),
            cancelOnInterrupt = obj.optBoolean("CANCEL ON INTERRUPT", false),
            emitCount = obj.optInt("EMIT COUNT", 1),
            meshId = obj.optString("AMOUNT", obj.optString("MESH", "0")),
            position = obj.optString("POSITION", "0, 0, 0"),
            visualSize = obj.optString("SIZE", "2, 2, 2"),
            altColor = obj.optString("ALT COLOR", "247, 215, 248"),
            runOnServer = obj.optBoolean("RUN ON SERVER", true),
            cframe = obj.optString("CFRAME", "0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1"),
            fov = obj.optDouble("FOV", 70.0),
            soundId = obj.optString("SOUND", "0"),
            volume = obj.optDouble("VOLUME", 1.0),
            playbackSpeed = obj.optDouble("PITCH", 1.0),
            signal = obj.optString("SIGNAL", "Hit"),
            range = obj.optString("RANGE", "inf"),
            tagLabel = obj.optString("TAG", "VFX"),
            tagSet = obj.optBoolean("SET", true),
            targetBranch = obj.optString("TARGET", "Default")
        )
        return node
    }

    fun exportToJson(
        skills: List<JjsSkill>,
        recolor: Boolean = false,
        detectedPalette: Palette? = null,
        targetPalette: Palette? = null
    ): String {
        val rootArray = JSONArray()

        skills.forEach { skill ->
            val sObj = JSONObject()
            sObj.put("SkillName", skill.name)
            sObj.put("SkillKey", skill.key)
            sObj.put("Duration", skill.duration)
            sObj.put("Position", skill.position)

            // Default branch lines
            val defaultNodes = skill.branches["Default"] ?: skill.branches.values.firstOrNull() ?: emptyList()
            val linesArr = JSONArray()
            defaultNodes.forEach { node ->
                linesArr.put(nodeToJson(node, recolor, detectedPalette, targetPalette))
            }
            sObj.put("Line", linesArr)

            // Other branches
            val otherBranches = skill.branches.filterKeys { it != "Default" }
            if (otherBranches.isNotEmpty()) {
                val branchesObj = JSONObject()
                otherBranches.forEach { (bName, bNodes) ->
                    val bArr = JSONArray()
                    bNodes.forEach { node ->
                        bArr.put(nodeToJson(node, recolor, detectedPalette, targetPalette))
                    }
                    branchesObj.put(bName, bArr)
                }
                sObj.put("Branches", branchesObj)
            }

            rootArray.put(sObj)
        }

        return rootArray.toString(2)
    }

    fun exportToKluv(
        skills: List<JjsSkill>,
        recolor: Boolean = false,
        detectedPalette: Palette? = null,
        targetPalette: Palette? = null
    ): String {
        val json = exportToJson(skills, recolor, detectedPalette, targetPalette)
        val compressed = ZstdHelper.compressToKLUv(json)
        // Ensure standard KLUv prefix format
        return if (compressed.startsWith("KLUv")) compressed else "KLUv/$compressed"
    }

    private fun nodeToJson(
        node: JjsNode,
        recolor: Boolean,
        detectedPalette: Palette?,
        targetPalette: Palette?
    ): JSONObject {
        val obj = JSONObject()

        val colorVal = if (recolor && detectedPalette != null && targetPalette != null) {
            ColorUtils.remapColorString(node.color, detectedPalette, targetPalette)
        } else {
            node.color
        }

        when (node.kind) {
            NodeKind.PARTICLE -> {
                obj.put("K_NAME", "PARTICLE")
                obj.put("TIME", node.time)
                obj.put("TEXTURE", node.texture.toLongOrNull() ?: node.texture)
                obj.put("COLOR", colorVal)
                obj.put("SIZE", node.size)
                obj.put("SPEED", node.speed)
                obj.put("RATE", node.rate)
                obj.put("LIFETIME", node.lifetime)
                obj.put("SPREAD ANGLE", node.spreadAngle)
                obj.put("FLIPBOOK MODE", node.flipbookMode)
                obj.put("DRAG", node.drag)
                obj.put("SQUASH", node.squash)
                obj.put("BODY PART", node.bodyPart)
                obj.put("CLIENT SIDED", node.clientSided)
                obj.put("CANCEL ON INTERRUPT", node.cancelOnInterrupt)
                if (node.name.isNotBlank()) obj.put("NAME", node.name)
            }
            NodeKind.VISUAL_MESH -> {
                obj.put("K_NAME", "VISUAL")
                obj.put("EFFECT", "Mesh")
                obj.put("TIME", node.time)
                obj.put("AMOUNT", node.meshId.toLongOrNull() ?: node.meshId)
                obj.put("POSITION", node.position)
                obj.put("SIZE", node.visualSize)
                obj.put("COLOR", colorVal)
                obj.put("ALT COLOR", node.altColor)
                obj.put("RUN ON SERVER", node.runOnServer)
                if (node.name.isNotBlank()) obj.put("NAME", node.name)
            }
            NodeKind.VISUAL_CAMERA -> {
                obj.put("K_NAME", "VISUAL")
                obj.put("EFFECT", "Camera")
                obj.put("TIME", node.time)
                obj.put("CFRAME", node.cframe)
                obj.put("FOV", node.fov)
                if (node.name.isNotBlank()) obj.put("NAME", node.name)
            }
            NodeKind.SFX -> {
                obj.put("K_NAME", "SFX")
                obj.put("TIME", node.time)
                obj.put("SOUND", node.soundId.toLongOrNull() ?: node.soundId)
                obj.put("VOLUME", node.volume)
                obj.put("PITCH", node.playbackSpeed)
                if (node.name.isNotBlank()) obj.put("NAME", node.name)
            }
            NodeKind.WAIT -> {
                obj.put("K_NAME", "WAIT")
                obj.put("TIME", node.time)
                if (node.name.isNotBlank()) obj.put("NAME", node.name)
            }
            NodeKind.CONNECT -> {
                obj.put("K_NAME", "CONNECT")
                obj.put("TIME", node.time)
                obj.put("SIGNAL", node.signal)
                obj.put("RANGE", node.range)
                if (node.name.isNotBlank()) obj.put("NAME", node.name)
            }
            NodeKind.TAG -> {
                obj.put("K_NAME", "TAG")
                obj.put("TIME", node.time)
                obj.put("TAG", node.tagLabel)
                obj.put("SET", node.tagSet)
                if (node.name.isNotBlank()) obj.put("NAME", node.name)
            }
            NodeKind.BRANCH -> {
                obj.put("K_NAME", "BRANCH")
                obj.put("TIME", node.time)
                obj.put("TARGET", node.targetBranch)
                if (node.name.isNotBlank()) obj.put("NAME", node.name)
            }
        }

        return obj
    }
}
