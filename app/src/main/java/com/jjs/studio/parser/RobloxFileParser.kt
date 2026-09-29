package com.jjs.studio.parser

import com.jjs.studio.model.RobloxInstance
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Universal Roblox Binary (.rbxl, .rbxm) & XML (.rbxmx, .rbxlx) Parser.
 *
 * Implements full parity with original parser.js:
 * - When parseCameras = false: completely ignores all Camera objects, Moon Animator saves, camera keyframe tracks.
 * - When parseMeshes = false: completely ignores all MeshPart, SpecialMesh, FileMesh objects.
 * - When enabled: extracts and sequences them into the Explorer hierarchy.
 */
object RobloxFileParser {

    fun parse(
        bytes: ByteArray,
        fileName: String = "RobloxAsset",
        parseCameras: Boolean = false,
        parseMeshes: Boolean = false
    ): RobloxInstance {
        if (bytes.isEmpty()) {
            return RobloxInstance(name = fileName, className = "Workspace")
        }

        // 1. Check if XML format
        if (isXmlFormat(bytes)) {
            val text = String(bytes, StandardCharsets.UTF_8)
            val parsed = RobloxXmlParser.parseXml(text, parseCameras, parseMeshes)
            if (parseCameras) {
                ensureCameraHost(parsed, text)
            }
            if (parsed.children.isNotEmpty() || parsed.name != "Workspace") {
                return parsed
            }
        }

        // 2. Check if Roblox Binary format
        if (isBinaryFormat(bytes)) {
            try {
                val binaryTree = parseBinary(bytes, fileName, parseCameras, parseMeshes)
                if (parseCameras) {
                    ensureCameraHostFromBinary(binaryTree, bytes)
                }
                return binaryTree
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to binary string scanner
        val scanned = scanBinaryStrings(bytes, fileName, parseCameras, parseMeshes)
        if (parseCameras) {
            ensureCameraHostFromBinary(scanned, bytes)
        }
        return scanned
    }

    fun parseStream(
        stream: InputStream,
        fileName: String = "RobloxAsset",
        parseCameras: Boolean = false,
        parseMeshes: Boolean = false
    ): RobloxInstance {
        val bytes = stream.readBytes()
        return parse(bytes, fileName, parseCameras, parseMeshes)
    }

    private fun isXmlFormat(bytes: ByteArray): Boolean {
        var startIdx = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            startIdx = 3
        }
        while (startIdx < bytes.size && (bytes[startIdx] == ' '.code.toByte() || bytes[startIdx] == '\n'.code.toByte() || bytes[startIdx] == '\r'.code.toByte() || bytes[startIdx] == '\t'.code.toByte())) {
            startIdx++
        }
        if (startIdx >= bytes.size) return false
        val preview = String(bytes.copyOfRange(startIdx, (startIdx + 200).coerceAtMost(bytes.size)), StandardCharsets.UTF_8)
        return preview.startsWith("<") && (preview.contains("<roblox", ignoreCase = true) || preview.contains("<?xml", ignoreCase = true) || preview.contains("<Item", ignoreCase = true))
    }

    private fun isBinaryFormat(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val magic = "<roblox!".toByteArray(StandardCharsets.US_ASCII)
        for (i in magic.indices) {
            if (bytes[i] != magic[i]) return false
        }
        return true
    }

    private fun parseBinary(
        bytes: ByteArray,
        fileName: String,
        parseCameras: Boolean,
        parseMeshes: Boolean
    ): RobloxInstance {
        val root = RobloxInstance(name = fileName.substringBeforeLast("."), className = "Model")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        if (buffer.remaining() < 32) return scanBinaryStrings(bytes, fileName, parseCameras, parseMeshes)
        buffer.position(14)
        val version = buffer.short
        val classCount = buffer.int
        val instanceCount = buffer.int
        buffer.long // reserved

        data class ClassInfo(val id: Int, val name: String, val count: Int, val refIds: MutableList<Int> = mutableListOf())
        val classMap = mutableMapOf<Int, ClassInfo>()
        val instanceMap = mutableMapOf<Int, RobloxInstance>()
        val parentMap = mutableMapOf<Int, Int>()

        while (buffer.remaining() >= 16) {
            val chunkNameBytes = ByteArray(4)
            buffer.get(chunkNameBytes)
            val chunkName = String(chunkNameBytes, StandardCharsets.US_ASCII)
            val compressedLen = buffer.int
            val uncompressedLen = buffer.int
            val reserved = buffer.int

            if (compressedLen < 0 || buffer.remaining() < compressedLen) {
                break
            }

            val chunkBytes = ByteArray(compressedLen)
            buffer.get(chunkBytes)

            val decompressedBytes = if (compressedLen > 0) {
                ChunkCodec.decompress(chunkBytes, uncompressedLen)
            } else {
                chunkBytes
            }

            when (chunkName) {
                "INST" -> {
                    try {
                        val cBuf = ByteBuffer.wrap(decompressedBytes).order(ByteOrder.LITTLE_ENDIAN)
                        if (cBuf.remaining() >= 8) {
                            val classId = cBuf.int
                            val classNameLen = cBuf.int
                            if (classNameLen in 1..256 && cBuf.remaining() >= classNameLen) {
                                val classNameArr = ByteArray(classNameLen)
                                cBuf.get(classNameArr)
                                val className = String(classNameArr, StandardCharsets.UTF_8)
                                val isService = if (cBuf.remaining() > 0) cBuf.get() != 0.toByte() else false
                                val instCount = if (cBuf.remaining() >= 4) cBuf.int else 0

                                // Check if this class should be skipped based on flags
                                val isCam = className.equals("Camera", ignoreCase = true) ||
                                        className.equals("KeyframeSequence", ignoreCase = true) ||
                                        className.equals("Pose", ignoreCase = true)
                                val isMesh = className.equals("MeshPart", ignoreCase = true) ||
                                        className.equals("SpecialMesh", ignoreCase = true) ||
                                        className.equals("FileMesh", ignoreCase = true)

                                val shouldSkip = (isCam && !parseCameras) || (isMesh && !parseMeshes)

                                val info = ClassInfo(classId, className, instCount)
                                if (cBuf.remaining() >= instCount * 4) {
                                    var accum = 0
                                    for (i in 0 until instCount) {
                                        val delta = cBuf.int
                                        accum += delta
                                        info.refIds.add(accum)
                                        if (!shouldSkip) {
                                            val inst = RobloxInstance(name = className, className = className)
                                            instanceMap[accum] = inst
                                        }
                                    }
                                }
                                classMap[classId] = info
                            }
                        }
                    } catch (_: Exception) {}
                }
                "PROP" -> {
                    try {
                        val pBuf = ByteBuffer.wrap(decompressedBytes).order(ByteOrder.LITTLE_ENDIAN)
                        if (pBuf.remaining() >= 8) {
                            val classId = pBuf.int
                            val propNameLen = pBuf.int
                            if (propNameLen in 1..256 && pBuf.remaining() >= propNameLen) {
                                val propNameArr = ByteArray(propNameLen)
                                pBuf.get(propNameArr)
                                val propName = String(propNameArr, StandardCharsets.UTF_8)
                                val typeByte = if (pBuf.remaining() > 0) pBuf.get() else 0

                                val classInfo = classMap[classId]
                                if (classInfo != null && propName == "Name") {
                                    for (refId in classInfo.refIds) {
                                        if (pBuf.remaining() >= 4) {
                                            val len = pBuf.int
                                            if (len in 1..256 && pBuf.remaining() >= len) {
                                                val nArr = ByteArray(len)
                                                pBuf.get(nArr)
                                                val parsedName = String(nArr, StandardCharsets.UTF_8)
                                                val existing = instanceMap[refId]
                                                if (existing != null) {
                                                    instanceMap[refId] = existing.copy(name = parsedName)
                                                }
                                            }
                                        }
                                    }
                                } else if (classInfo != null) {
                                    parsePropValues(propName, classInfo.refIds, pBuf, instanceMap)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                "PRNT" -> {
                    try {
                        val prBuf = ByteBuffer.wrap(decompressedBytes).order(ByteOrder.LITTLE_ENDIAN)
                        if (prBuf.remaining() > 0) {
                            val prVersion = prBuf.get()
                            val count = if (prBuf.remaining() >= 4) prBuf.int else 0
                            val childRefs = mutableListOf<Int>()
                            var accum = 0
                            for (i in 0 until count) {
                                if (prBuf.remaining() >= 4) {
                                    accum += prBuf.int
                                    childRefs.add(accum)
                                }
                            }
                            var parentAccum = 0
                            for (i in 0 until count) {
                                if (prBuf.remaining() >= 4) {
                                    parentAccum += prBuf.int
                                    if (i < childRefs.size) {
                                        parentMap[childRefs[i]] = parentAccum
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                "END\u0000" -> break
            }
        }

        if (instanceMap.isNotEmpty()) {
            val addedToParent = mutableSetOf<Int>()
            for ((childRef, parentRef) in parentMap) {
                val child = instanceMap[childRef]
                val parent = instanceMap[parentRef]
                if (child != null && parent != null && childRef != parentRef) {
                    parent.children.add(child)
                    addedToParent.add(childRef)
                }
            }

            for ((refId, inst) in instanceMap) {
                if (!addedToParent.contains(refId)) {
                    root.children.add(inst)
                }
            }
            if (root.children.isNotEmpty()) {
                return root
            }
        }

        return scanBinaryStrings(bytes, fileName, parseCameras, parseMeshes)
    }

    private fun parsePropValues(
        propName: String,
        refIds: List<Int>,
        pBuf: ByteBuffer,
        instanceMap: MutableMap<Int, RobloxInstance>
    ) {
        when (propName) {
            "Texture", "SoundId", "MeshId", "Color", "Rate", "Speed", "Lifetime", "Size",
            "CFrame", "Position", "Orientation", "Value", "AttributesSerialize", "Brightness", "Drag" -> {
                for (refId in refIds) {
                    if (pBuf.remaining() >= 4) {
                        val len = pBuf.int
                        if (len in 1..1024 && pBuf.remaining() >= len) {
                            val arr = ByteArray(len)
                            pBuf.get(arr)
                            val strVal = String(arr, StandardCharsets.UTF_8)
                            val inst = instanceMap[refId]
                            if (inst != null) {
                                val props = inst.properties.toMutableMap()
                                props[propName] = strVal
                                instanceMap[refId] = inst.copy(properties = props)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun decompressLz4(input: ByteArray, uncompressedSize: Int): ByteArray? {
        return ChunkCodec.decompress(input, uncompressedSize)
    }

    /**
     * Fallback: scan printable strings in the raw bytes for class names / asset ids.
     */
    private fun scanBinaryStrings(
        bytes: ByteArray,
        fileName: String,
        parseCameras: Boolean,
        parseMeshes: Boolean
    ): RobloxInstance {
        val root = RobloxInstance(name = fileName.substringBeforeLast("."), className = "Model")
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126) {
                sb.append(c.toChar())
            } else {
                if (sb.length in 3..128) strings.add(sb.toString())
                sb.clear()
            }
        }
        if (sb.length in 3..128) strings.add(sb.toString())

        val assetIdRe = Regex("""\d{7,}""")
        val foundIds = mutableListOf<String>()
        strings.forEach { s ->
            assetIdRe.findAll(s).forEach { foundIds.add(it.value) }
        }

        var emitterCount = 0
        var meshCount = 0
        var camCount = 0
        var soundCount = 0

        for (i in strings.indices) {
            val str = strings[i]
            when {
                str.equals("ParticleEmitter", ignoreCase = true) -> {
                    emitterCount++
                    val name = strings.getOrNull(i + 1)
                        ?.takeIf { it.length in 2..48 && !it.contains("Emitter", ignoreCase = true) }
                        ?: "Emitter_$emitterCount"
                    val tex = foundIds.getOrNull(emitterCount - 1) ?: "1084991219"
                    root.children.add(
                        RobloxInstance(
                            name = name,
                            className = "ParticleEmitter",
                            properties = mapOf("Texture" to "rbxassetid://$tex", "Name" to name)
                        )
                    )
                }
                parseMeshes && (str.equals("MeshPart", ignoreCase = true) || str.equals("SpecialMesh", ignoreCase = true) || str.equals("FileMesh", ignoreCase = true)) -> {
                    meshCount++
                    val name = strings.getOrNull(i + 1)?.takeIf { it.length in 2..48 } ?: "Mesh_$meshCount"
                    val meshId = foundIds.getOrNull(meshCount - 1) ?: "430095861"
                    root.children.add(
                        RobloxInstance(
                            name = name,
                            className = str,
                            properties = mapOf("MeshId" to "rbxassetid://$meshId", "Name" to name)
                        )
                    )
                }
                parseCameras && (str.equals("Camera", ignoreCase = true) || str.equals("KeyframeSequence", ignoreCase = true)) -> {
                    camCount++
                    val name = strings.getOrNull(i + 1)?.takeIf { it.length in 2..48 } ?: "Camera_$camCount"
                    root.children.add(
                        RobloxInstance(
                            name = name,
                            className = "Camera",
                            properties = mapOf("Name" to name)
                        )
                    )
                }
                str.equals("Sound", ignoreCase = true) -> {
                    soundCount++
                    val name = strings.getOrNull(i + 1)?.takeIf { it.length in 2..48 } ?: "Sound_$soundCount"
                    val sid = foundIds.getOrNull(soundCount - 1) ?: "9114223180"
                    root.children.add(
                        RobloxInstance(
                            name = name,
                            className = "Sound",
                            properties = mapOf("SoundId" to "rbxassetid://$sid", "Name" to name)
                        )
                    )
                }
            }
        }

        if (emitterCount == 0 && foundIds.isNotEmpty()) {
            foundIds.distinct().take(32).forEachIndexed { idx, id ->
                root.children.add(
                    RobloxInstance(
                        name = "Emitter_${idx + 1}",
                        className = "ParticleEmitter",
                        properties = mapOf("Texture" to "rbxassetid://$id")
                    )
                )
            }
        }
        return root
    }

    private fun ensureCameraHost(root: RobloxInstance, xmlText: String) {
        if (root.findDescendantsByClass("Camera").isNotEmpty()) return
        if (!xmlText.contains("Camera", ignoreCase = true) &&
            !xmlText.contains("KeyframeSequence", ignoreCase = true) &&
            !xmlText.contains("MoonAnimator", ignoreCase = true)
        ) return
        root.children.add(
            RobloxInstance(
                name = "Cameras",
                className = "Folder",
                children = mutableListOf(
                    RobloxInstance(name = "Camera", className = "Camera")
                )
            )
        )
    }

    private fun ensureCameraHostFromBinary(root: RobloxInstance, bytes: ByteArray) {
        if (root.findDescendantsByClass("Camera").isNotEmpty()) return
        // Cheap probe for camera-related strings in the binary
        val probe = StringBuilder()
        var hits = 0
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126) {
                probe.append(c.toChar())
                if (probe.length > 48) probe.deleteCharAt(0)
                val s = probe.toString()
                if (s.contains("Camera") || s.contains("Keyframe") || s.contains("MoonAnimator")) {
                    hits++
                    if (hits >= 2) break
                }
            } else {
                probe.clear()
            }
        }
        if (hits == 0) return
        root.children.add(
            RobloxInstance(
                name = "Cameras",
                className = "Folder",
                children = mutableListOf(
                    RobloxInstance(name = "Camera", className = "Camera")
                )
            )
        )
    }
}

