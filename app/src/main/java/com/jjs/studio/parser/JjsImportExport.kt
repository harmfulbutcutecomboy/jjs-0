package com.jjs.studio.parser

import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.JjsSkill
import com.jjs.studio.model.NodeKind
import com.jjs.studio.util.ColorUtils
import com.jjs.studio.util.Palette
import com.jjs.studio.util.ZstdHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes and decodes the official Jujutsu Shenanigans (JJS) game JSON and KLUv bytecode.
 *
 * Game Format:
 * [
 *   {
 *     "ADD": false,
 *     "NAME": "Skill Name",
 *     "COOLDOWN": 0,
 *     "KEY": 1,
 *     "TOOL TIP": "",
 *     "DATA": "{\"Prop\":[],\"Line\":[...],\"Req\":[],\"Branch\":[]}",
 *     "K_NAME": "SKILL"
 *   }
 * ]
 */
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
        val name = obj.optString("NAME", obj.optString("SkillName", obj.optString("name", "Skill")))
        val key = obj.optInt("KEY", obj.optInt("SkillKey", obj.optInt("key", 1)))
        val add = obj.optBoolean("ADD", false)
        val cooldown = obj.optDouble("COOLDOWN", 0.0)
        val toolTip = obj.optString("TOOL TIP", "")

        val skill = JjsSkill(
            name = name,
            key = key,
            add = add,
            cooldown = cooldown,
            toolTip = toolTip
        )

        // The game stores Line and Branch inside a stringified "DATA" field
        val dataObj: JSONObject = if (obj.has("DATA")) {
            val rawData = obj.get("DATA")
            if (rawData is String) {
                try {
                    JSONObject(rawData)
                } catch (_: Exception) {
                    JSONObject()
                }
            } else if (rawData is JSONObject) {
                rawData
            } else {
                JSONObject()
            }
        } else {
            obj
        }

        // Parse Default branch lines
        val lineArr = dataObj.optJSONArray("Line") ?: dataObj.optJSONArray("line")
        val defaultNodes = mutableListOf<JjsNode>()
        if (lineArr != null) {
            for (i in 0 until lineArr.length()) {
                val nodeObj = lineArr.optJSONObject(i) ?: continue
                defaultNodes.add(parseNodeJson(nodeObj))
            }
        }
        skill.branches["Default"] = defaultNodes

        // Parse additional branches
        val branchField = dataObj.opt("Branch") ?: dataObj.opt("Branches") ?: dataObj.opt("branches")
        if (branchField is JSONObject) {
            val keys = branchField.keys()
            while (keys.hasNext()) {
                val bName = keys.next()
                val bVal = branchField.opt(bName)
                val bNodes = mutableListOf<JjsNode>()
                if (bVal is JSONObject) {
                    val bLine = bVal.optJSONArray("Line")
                    if (bLine != null) {
                        for (i in 0 until bLine.length()) {
                            val nodeObj = bLine.optJSONObject(i) ?: continue
                            bNodes.add(parseNodeJson(nodeObj))
                        }
                    }
                } else if (bVal is JSONArray) {
                    for (i in 0 until bVal.length()) {
                        val nodeObj = bVal.optJSONObject(i) ?: continue
                        bNodes.add(parseNodeJson(nodeObj))
                    }
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

        val rawSpeed = obj.opt("SPEED")?.toString() ?: "0.00, 0.00"
        val rawLifetime = obj.opt("LIFETIME")?.toString() ?: "0.50, 1.00"

        val node = JjsNode(
            kind = kind,
            name = obj.optString("NAME", obj.optString("name", "")),
            time = obj.optDouble("TIME", 0.0),
            texture = obj.opt("TEXTURE")?.toString() ?: "0",
            color = obj.optString("COLOR", "255,255,255 255,255,255"),
            size = obj.opt("SIZE")?.toString() ?: "1.0, 0.2",
            speedRange = rawSpeed,
            rate = obj.optDouble("RATE", 20.0),
            lifetimeRange = rawLifetime,
            spreadAngle = obj.optString("SPREAD ANGLE", "0.00, 0.00, 0.00"),
            flipbookMode = obj.optString("FLIPBOOK MODE", "Loop"),
            flipbookSize = obj.optString("FLIPBOOK SIZE", "1, 1"),
            flipbookFramerate = obj.optString("FLIPBOOK FRAMERATE", "1.00, 1.00"),
            rotSpeed = obj.optString("ROT SPEED", "0.00, 0.00"),
            rotation = obj.optString("ROTATION", "0.00, 0.00"),
            drag = obj.optDouble("DRAG", 0.0),
            squash = obj.optString("SQUASH", "0.00,0.00"),
            transparency = obj.optString("TRANSPARENCY", "0.00,0.00"),
            brightness = obj.optDouble("BRIGHTNESS", 1.0),
            lightEmission = obj.optDouble("LIGHT EMISSION", 1.0),
            lightInfluence = obj.optDouble("LIGHT INFLUENCE", 0.0),
            zOffset = obj.optDouble("ZOFFSET", 0.0),
            emitCount = obj.optInt("EMIT COUNT", 1),
            lockToPart = obj.optBoolean("LOCK TO PART", true),
            orientationType = obj.optString("ORIENTATION TYPE", "FacingCamera"),
            emissionDirection = obj.optString("EMISSION DIRECTION", "Top"),
            acceleration = obj.optString("ACCELERATION", "0, 0, 0"),
            bodyPart = obj.optString("BODY PART", "HumanoidRootPart"),
            clientSided = obj.optBoolean("CLIENT SIDED", false),
            cancelOnInterrupt = obj.optBoolean("CANCEL ON INTERRUPT", false),
            runOnServer = obj.optBoolean("RUN ON SERVER", true),

            // Mesh / Camera
            meshId = obj.opt("AMOUNT")?.toString() ?: obj.opt("MESH")?.toString() ?: "0",
            position = obj.optString("POSITION", "0, 0, 0"),
            visualSize = obj.opt("SIZE")?.toString() ?: "2, 2, 2",
            altColor = obj.optString("ALT COLOR", "247, 215, 248"),
            cframe = obj.optString("CFRAME", "0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1"),
            fov = obj.optDouble("FOV", 70.0),

            // Sound
            soundId = obj.opt("ID")?.toString() ?: obj.opt("SOUND")?.toString() ?: "0",
            volume = obj.optDouble("VOLUME", 1.0),
            playbackSpeed = obj.optDouble("SPEED", obj.optDouble("PITCH", 1.0)),

            // Connect / Tag / Branch
            signal = obj.optString("SIGNAL", "Hit"),
            range = obj.optString("RANGE", "inf"),
            tagLabel = obj.optString("TAG", "VFX"),
            tagSet = obj.optBoolean("SET", true),
            targetBranch = obj.optString("TARGET", "Default")
        )
        return node
    }

    /**
     * Serializes skills to official Jujutsu Shenanigans JSON format.
     */
    fun exportToJson(
        skills: List<JjsSkill>,
        recolor: Boolean = false,
        detectedPalette: Palette? = null,
        targetPalette: Palette? = null
    ): String {
        val rootArray = JSONArray()

        skills.forEach { skill ->
            val skillObj = JSONObject()
            skillObj.put("ADD", skill.add)
            skillObj.put("NAME", skill.name)
            skillObj.put("COOLDOWN", skill.cooldown)
            skillObj.put("KEY", skill.key)
            skillObj.put("TOOL TIP", skill.toolTip)
            skillObj.put("K_NAME", "SKILL")

            // Construct DATA container
            val dataObj = JSONObject()
            dataObj.put("Prop", JSONArray())
            dataObj.put("Req", JSONArray())

            // Default branch lines
            val defaultNodes = skill.branches["Default"] ?: skill.branches.values.firstOrNull() ?: emptyList()
            val linesArr = JSONArray()
            defaultNodes.forEach { node ->
                linesArr.put(nodeToJson(node, recolor, detectedPalette, targetPalette))
            }
            dataObj.put("Line", linesArr)

            // Other branches
            val otherBranches = skill.branches.filterKeys { it != "Default" }
            if (otherBranches.isNotEmpty()) {
                val branchesObj = JSONObject()
                otherBranches.forEach { (bName, bNodes) ->
                    val bData = JSONObject()
                    bData.put("Req", JSONArray())
                    val bLine = JSONArray()
                    bNodes.forEach { node ->
                        bLine.put(nodeToJson(node, recolor, detectedPalette, targetPalette))
                    }
                    bData.put("Line", bLine)
                    branchesObj.put(bName, bData)
                }
                dataObj.put("Branch", branchesObj)
            } else {
                dataObj.put("Branch", JSONArray())
            }

            // CRITICAL: The game requires DATA to be stringified JSON!
            skillObj.put("DATA", dataObj.toString())
            rootArray.put(skillObj)
        }

        return rootArray.toString()
    }

    fun exportToKluv(
        skills: List<JjsSkill>,
        recolor: Boolean = false,
        detectedPalette: Palette? = null,
        targetPalette: Palette? = null
    ): String {
        val json = exportToJson(skills, recolor, detectedPalette, targetPalette)
        val compressed = ZstdHelper.compressToKLUv(json)
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
                obj.put("NAME", node.name.ifBlank { "PARTICLE" })
                obj.put("SIZE", node.size)
                obj.put("EMIT COUNT", node.emitCount)
                obj.put("TEXTURE", node.texture.toLongOrNull() ?: 0L)
                obj.put("LIFETIME", node.lifetimeRange)
                obj.put("ZOFFSET", node.zOffset)
                obj.put("LIGHT INFLUENCE", node.lightInfluence)
                obj.put("FLIPBOOK FRAMERATE", node.flipbookFramerate)
                obj.put("ROT SPEED", node.rotSpeed)
                obj.put("SPREAD ANGLE", node.spreadAngle)
                obj.put("FLIPBOOK MODE", node.flipbookMode)
                obj.put("SPEED", node.speedRange)
                obj.put("LIGHT EMISSION", node.lightEmission)
                obj.put("COLOR", colorVal)
                obj.put("RUN ON SERVER", node.runOnServer)
                obj.put("DRAG", node.drag)
                obj.put("TRANSPARENCY", node.transparency)
                obj.put("BRIGHTNESS", node.brightness)
                obj.put("FLIPBOOK SIZE", node.flipbookSize)
                obj.put("ROTATION", node.rotation)
                obj.put("SQUASH", node.squash)
                obj.put("LOCK TO PART", node.lockToPart)
                obj.put("ORIENTATION TYPE", node.orientationType)
                obj.put("EMISSION DIRECTION", node.emissionDirection)
                obj.put("ACCELERATION", node.acceleration)
                obj.put("BODY PART", node.bodyPart)
                obj.put("RATE", node.rate)
                obj.put("CLIENT SIDED", node.clientSided)
                obj.put("CANCEL ON INTERRUPT", node.cancelOnInterrupt)
            }
            NodeKind.VISUAL_MESH -> {
                obj.put("K_NAME", "VISUAL")
                obj.put("EFFECT", "Mesh")
                obj.put("NAME", node.name.ifBlank { "Mesh" })
                obj.put("AMOUNT", node.meshId.toLongOrNull() ?: 1L)
                obj.put("TEXTURE", node.texture.toLongOrNull() ?: 0L)
                obj.put("SIZE", 1)
                obj.put("ALT SIZE", 1)
                obj.put("OPACITY", 0)
                obj.put("ALT OPACITY", 0)
                obj.put("POSITION", node.position)
                obj.put("ALT POSITION", node.position)
                obj.put("ROTATION", "0, 0, 0")
                obj.put("ALT ROTATION", "0, 0, 0")
                obj.put("COLOR", colorVal)
                obj.put("ALT COLOR", node.altColor)
                obj.put("TIME", 0.3)
                obj.put("BODY PART", "HumanoidRootPart")
                obj.put("RUN ON SERVER", node.runOnServer)
                obj.put("CLIENT SIDED", node.clientSided)
                obj.put("CANCEL ON INTERRUPT", false)
                obj.put("RELATIVE FROM BRANCH", false)
                obj.put("LAST HIT", -1)
            }
            NodeKind.VISUAL_CAMERA -> {
                obj.put("K_NAME", "VISUAL")
                obj.put("EFFECT", "Camera")
                obj.put("NAME", node.name.ifBlank { "Camera" })
                obj.put("POSITION", node.position)
                obj.put("ALT POSITION", node.position)
                obj.put("ROTATION", "0, 0, 0")
                obj.put("ALT ROTATION", "0, 0, 0")
                obj.put("COLOR", "255, 255, 255")
                obj.put("ALT COLOR", "255, 255, 255")
                obj.put("TIME", 0.12)
                obj.put("EASING STYLE", "Quad")
                obj.put("EASING DIRECTION", "Out")
                obj.put("BODY PART", "HumanoidRootPart")
                obj.put("RUN ON SERVER", false)
                obj.put("CLIENT SIDED", false)
                obj.put("CANCEL ON INTERRUPT", false)
                obj.put("RELATIVE FROM BRANCH", false)
                obj.put("LAST HIT", -1)
            }
            NodeKind.SFX -> {
                obj.put("K_NAME", "SFX")
                obj.put("NAME", node.name.ifBlank { "Sound" })
                obj.put("ID", node.soundId.toLongOrNull() ?: 0L)
                obj.put("SPEED", node.playbackSpeed)
                obj.put("VOLUME", node.volume)
                obj.put("START", 0)
                obj.put("END", 500)
                obj.put("FADE IN", 0)
                obj.put("FADE OUT", 0)
                obj.put("CANCEL", false)
                obj.put("GLOBAL", false)
                obj.put("CLIENT SIDED", false)
                obj.put("LAST HIT", -1)
                obj.put("PROJECTILE TAG", "")
            }
            NodeKind.WAIT -> {
                obj.put("K_NAME", "WAIT")
                obj.put("TIME", node.time)
            }
            NodeKind.CONNECT -> {
                obj.put("K_NAME", "CONNECT")
                obj.put("SIGNAL", node.signal)
                obj.put("TIME", 0.1)
                val rangeObj = JSONObject()
                rangeObj.put("m", JSONObject.NULL)
                rangeObj.put("t", "numeric")
                rangeObj.put("v", "inf")
                obj.put("RANGE", rangeObj)
            }
            NodeKind.TAG -> {
                obj.put("K_NAME", "TAG")
                obj.put("TAG", node.tagLabel)
                obj.put("SET", node.tagSet)
                obj.put("ADD/REMOVE", true)
                obj.put("CHECK", false)
                obj.put("TIME", 1.0)
                obj.put("LAST HIT", -1)
            }
            NodeKind.BRANCH -> {
                obj.put("K_NAME", "BRANCH")
                obj.put("TARGET", node.targetBranch)
                obj.put("TIME", node.time)
            }
        }

        return obj
    }
}
