package com.jjs.studio.parser

import android.util.Xml
import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.JjsSkill
import com.jjs.studio.model.NodeKind
import com.jjs.studio.model.RobloxInstance
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object RobloxXmlParser {

    fun parseXml(
        xmlContent: String,
        parseCameras: Boolean = false,
        parseMeshes: Boolean = false
    ): RobloxInstance {
        val root = RobloxInstance(name = "Workspace", className = "Workspace")
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))

            val stack = ArrayDeque<RobloxInstance>()
            stack.addLast(root)

            var currentProperties: MutableMap<String, String>? = null
            var currentPropName: String? = null
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        if (tagName.equals("Item", ignoreCase = true)) {
                            val className = parser.getAttributeValue(null, "class") ?: "Instance"

                            // Skip cameras if camera parsing is disabled
                            val isCameraClass = className.equals("Camera", ignoreCase = true) ||
                                    className.equals("KeyframeSequence", ignoreCase = true) ||
                                    className.equals("Pose", ignoreCase = true)
                            val isMeshClass = className.equals("MeshPart", ignoreCase = true) ||
                                    className.equals("SpecialMesh", ignoreCase = true) ||
                                    className.equals("FileMesh", ignoreCase = true)

                            val shouldSkip = (isCameraClass && !parseCameras) || (isMeshClass && !parseMeshes)

                            if (!shouldSkip) {
                                val newInstance = RobloxInstance(name = className, className = className)
                                stack.lastOrNull()?.children?.add(newInstance)
                                stack.addLast(newInstance)
                            } else {
                                // Dummy placeholder not attached to parent
                                stack.addLast(RobloxInstance(name = "__SKIP__", className = "__SKIP__"))
                            }
                        } else if (tagName.equals("Properties", ignoreCase = true)) {
                            currentProperties = mutableMapOf()
                        } else if (currentProperties != null) {
                            currentPropName = parser.getAttributeValue(null, "name")
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (!text.isNullOrEmpty() && currentProperties != null && currentPropName != null) {
                            currentProperties[currentPropName] = text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name
                        if (tagName.equals("Item", ignoreCase = true)) {
                            if (stack.size > 1) {
                                stack.removeLast()
                            }
                        } else if (tagName.equals("Properties", ignoreCase = true)) {
                            if (currentProperties != null) {
                                val current = stack.lastOrNull()
                                if (current != null && current.className != "__SKIP__") {
                                    val name = currentProperties["Name"] ?: current.className
                                    val updated = current.copy(name = name, properties = currentProperties.toMap())
                                    if (stack.size >= 2) {
                                        val parent = stack[stack.size - 2]
                                        val idx = parent.children.indexOfFirst { it.id == current.id }
                                        if (idx != -1) {
                                            parent.children[idx] = updated
                                        }
                                    }
                                }
                            }
                            currentProperties = null
                            currentPropName = null
                        } else if (currentPropName != null) {
                            currentPropName = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Clean any skipped items
        fun cleanTree(node: RobloxInstance) {
            node.children.removeAll { it.className == "__SKIP__" }
            node.children.forEach { cleanTree(it) }
        }
        cleanTree(root)

        return root
    }

    fun convertInstanceTreeToSkill(
        root: RobloxInstance,
        skillName: String = "Converted_Roblox_VFX",
        parseCameras: Boolean = true,
        parseMeshes: Boolean = true,
        sectionMark: Boolean = false,
        runOnServer: Boolean = true
    ): JjsSkill {
        val skill = JjsSkill(name = skillName, key = 1, duration = 0.0)
        val defaultNodes = mutableListOf<JjsNode>()

        fun extractParticleNode(instance: RobloxInstance, t: Double): JjsNode {
            val props = instance.properties
            val textureRaw = props["Texture"] ?: props["TextureId"] ?: ""
            val textureId = extractAssetId(textureRaw)

            val rate = props["Rate"]?.toDoubleOrNull() ?: 20.0
            val speed = props["Speed"] ?: "0.00, 0.00"
            val size = props["Size"] ?: "1.0, 0.2"
            val lifetime = props["Lifetime"] ?: "0.50, 1.00"
            val color = props["Color"] ?: "255,255,255 255,255,255"
            val spreadAngle = props["SpreadAngle"] ?: "0.00, 0.00, 0.00"
            val transparency = props["Transparency"] ?: "0.00,0.00"
            val drag = props["Drag"]?.toDoubleOrNull() ?: 0.0
            val zOffset = props["ZOffset"]?.toDoubleOrNull() ?: 0.0
            val brightness = props["Brightness"]?.toDoubleOrNull() ?: 1.0
            val lightEmission = props["LightEmission"]?.toDoubleOrNull() ?: 1.0
            val lightInfluence = props["LightInfluence"]?.toDoubleOrNull() ?: 0.0
            val rotSpeed = props["RotSpeed"] ?: "0.00, 0.00"
            val rotation = props["Rotation"] ?: "0.00, 0.00"
            val squash = props["Squash"] ?: "0.00,0.00"
            val orientation = props["Orientation"] ?: "FacingCamera"
            val emissionDirection = props["EmissionDirection"] ?: "Top"
            val acceleration = props["Acceleration"] ?: "0, 0, 0"

            val flipbookLayout = props["FlipbookLayout"]
            val flipbookSize = when (flipbookLayout) {
                "Grid2x2" -> "2, 2"
                "Grid4x4" -> "4, 4"
                "Grid8x8" -> "8, 8"
                else -> props["FlipbookSize"] ?: "1, 1"
            }
            val flipbookMode = props["FlipbookMode"] ?: "Loop"
            val flipbookFramerate = props["FlipbookFramerate"] ?: "1.00, 1.00"

            val avgLife = lifetime.split(",").mapNotNull { it.trim().toDoubleOrNull() }.average().takeIf { !it.isNaN() && it > 0 } ?: 0.5
            val emitCount = (rate * avgLife.coerceAtMost(0.25)).toInt().coerceIn(1, 15)

            return JjsNode(
                kind = NodeKind.PARTICLE,
                name = instance.name,
                texture = textureId,
                rate = rate,
                speedRange = speed,
                size = size,
                lifetimeRange = lifetime,
                color = color,
                spreadAngle = spreadAngle,
                transparency = transparency,
                drag = drag,
                zOffset = zOffset,
                brightness = brightness,
                lightEmission = lightEmission,
                lightInfluence = lightInfluence,
                rotSpeed = rotSpeed,
                rotation = rotation,
                squash = squash,
                orientationType = orientation,
                emissionDirection = emissionDirection,
                acceleration = acceleration,
                flipbookSize = flipbookSize,
                flipbookMode = flipbookMode,
                flipbookFramerate = flipbookFramerate,
                emitCount = emitCount,
                runOnServer = runOnServer,
                time = t
            )
        }

        fun walk(instance: RobloxInstance, currentTime: Double): Double {
            var t = currentTime
            when (instance.className) {
                "ParticleEmitter" -> {
                    defaultNodes.add(extractParticleNode(instance, t))
                    t += 0.05
                }
                "MeshPart", "SpecialMesh" -> {
                    if (parseMeshes) {
                        val meshId = extractAssetId(instance.properties["MeshId"] ?: "")
                        val textureId = extractAssetId(instance.properties["TextureId"] ?: "")
                        defaultNodes.add(
                            JjsNode(
                                kind = NodeKind.VISUAL_MESH,
                                name = instance.name,
                                meshId = meshId,
                                texture = textureId,
                                runOnServer = runOnServer,
                                time = t
                            )
                        )
                        t += 0.05
                    }
                }
                "Camera" -> {
                    if (parseCameras) {
                        val fov = instance.properties["FieldOfView"]?.toDoubleOrNull() ?: 70.0
                        val pos = instance.properties["Position"] ?: "0, 0, 0"
                        val rot = instance.properties["Orientation"] ?: "0, 0, 0"
                        defaultNodes.add(
                            JjsNode(
                                kind = NodeKind.VISUAL_CAMERA,
                                name = instance.name,
                                fov = fov,
                                position = pos,
                                time = t
                            )
                        )
                        t += 0.1
                    }
                }
                "Sound" -> {
                    val soundId = extractAssetId(instance.properties["SoundId"] ?: "")
                    val vol = instance.properties["Volume"]?.toDoubleOrNull() ?: 1.0
                    val pitch = instance.properties["PlaybackSpeed"]?.toDoubleOrNull() ?: 1.0
                    defaultNodes.add(
                        JjsNode(
                            kind = NodeKind.SFX,
                            name = instance.name,
                            soundId = soundId,
                            volume = vol,
                            playbackSpeed = pitch,
                            time = t
                        )
                    )
                }
            }

            instance.children.forEach { child ->
                t = walk(child, t)
            }
            return t
        }

        walk(root, 0.0)

        // Section marking (like in app.js): Insert CONNECT nodes between distinct textures
        val finalNodes = if (sectionMark) {
            val marked = mutableListOf<JjsNode>()
            var lastTexture: String? = null
            defaultNodes.forEach { node ->
                if (node.kind == NodeKind.PARTICLE && node.texture != lastTexture) {
                    marked.add(
                        JjsNode(
                            kind = NodeKind.CONNECT,
                            signal = "${node.name.ifBlank { "Texture " + node.texture }} [SECTION]",
                            time = node.time
                        )
                    )
                    lastTexture = node.texture
                }
                marked.add(node)
            }
            marked
        } else {
            defaultNodes
        }

        skill.branches["Default"] = finalNodes
        return skill
    }

    private fun extractAssetId(str: String): String {
        val digits = Regex("""\d{7,}""").find(str)?.value
        return digits ?: (if (str.isNotBlank() && str.all { it.isDigit() }) str else "0")
    }
}
