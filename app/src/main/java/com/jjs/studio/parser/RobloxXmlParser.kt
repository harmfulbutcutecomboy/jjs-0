package com.jjs.studio.parser

import android.util.Xml
import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.JjsSkill
import com.jjs.studio.model.NodeKind
import com.jjs.studio.model.RobloxInstance
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object RobloxXmlParser {

    fun parseXml(xmlContent: String): RobloxInstance {
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
                            val newInstance = RobloxInstance(name = className, className = className)
                            stack.lastOrNull()?.children?.add(newInstance)
                            stack.addLast(newInstance)
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
                                if (current != null) {
                                    val name = currentProperties["Name"] ?: current.className
                                    val updated = current.copy(name = name, properties = currentProperties.toMap())
                                    // replace in parent
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
        return root
    }

    fun convertInstanceTreeToSkill(
        root: RobloxInstance,
        skillName: String = "Converted_Roblox_VFX",
        parseCameras: Boolean = true,
        parseMeshes: Boolean = true
    ): JjsSkill {
        val skill = JjsSkill(name = skillName, key = 1, duration = 2.0)
        val defaultNodes = mutableListOf<JjsNode>()

        fun walk(instance: RobloxInstance, currentTime: Double): Double {
            var t = currentTime
            when (instance.className) {
                "ParticleEmitter" -> {
                    val textureRaw = instance.properties["Texture"] ?: ""
                    val textureId = extractAssetId(textureRaw)
                    val rate = instance.properties["Rate"]?.toDoubleOrNull() ?: 20.0
                    val speed = instance.properties["Speed"]?.split(" ")?.firstOrNull()?.toDoubleOrNull() ?: 15.0

                    defaultNodes.add(
                        JjsNode(
                            kind = NodeKind.PARTICLE,
                            name = instance.name,
                            texture = textureId,
                            rate = rate,
                            speed = speed,
                            time = t
                        )
                    )
                    t += 0.05
                }
                "MeshPart", "SpecialMesh" -> {
                    if (parseMeshes) {
                        val meshId = extractAssetId(instance.properties["MeshId"] ?: "")
                        defaultNodes.add(
                            JjsNode(
                                kind = NodeKind.VISUAL_MESH,
                                name = instance.name,
                                meshId = meshId,
                                time = t
                            )
                        )
                        t += 0.05
                    }
                }
                "Camera" -> {
                    if (parseCameras) {
                        val fov = instance.properties["FieldOfView"]?.toDoubleOrNull() ?: 70.0
                        defaultNodes.add(
                            JjsNode(
                                kind = NodeKind.VISUAL_CAMERA,
                                name = instance.name,
                                fov = fov,
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
        if (defaultNodes.isEmpty()) {
            defaultNodes.add(
                JjsNode(
                    kind = NodeKind.PARTICLE,
                    name = "Default Particle",
                    time = 0.0
                )
            )
        }

        skill.branches["Default"] = defaultNodes
        return skill
    }

    private fun extractAssetId(str: String): String {
        val digits = Regex("""\d{7,}""").find(str)?.value
        return digits ?: (if (str.isNotBlank()) str.take(15) else "0")
    }
}
