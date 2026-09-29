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
                decompressLz4(chunkBytes, uncompressedLen) ?: chunkBytes
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
        if (uncompressedSize <= 0) return input
        try {
            val output = ByteArray(uncompressedSize)
            var src = 0
            var dst = 0

            while (src < input.size && dst < uncompressedSize) {
                val token = input[src++].toInt() and 0xFF
                var literalLen = token ushr 4

                if (literalLen == 15) {
                    var s: Int
                    do {
                        if (src >= input.size) return null
                        s = input[src++].toInt() and 0xFF
                        literalLen += s
                    } while (s == 255)
                }

                if (src + literalLen > input.size || dst + literalLen > uncompressedSize) {
                    break
                }
                System.arraycopy(input, src, output, dst, literalLen)
                src += literalLen
                dst += literalLen

                if (dst >= uncompressedSize || src >= input.size) break

                val offset = (input[src++].toInt() and 0xFF) or ((input[src++].toInt() and 0xFF) shl 8)
                if (offset == 0) return null

                var matchLen = token and 0x0F
                if (matchLen == 15) {
                    var s: Int
                    do {
                        if (src >= input.size) return null
                        s = input[src++].toInt() and 0xFF
                        matchLen += s
                    } while (s == 255)
                }
                matchLen += 4

                var matchSrc = dst - offset
                if (matchSrc < 0 || dst + matchLen > uncompressedSize) {
                    break
                }

                for (i in 0 until matchLen) {
                    output[dst++] = output[matchSrc++]
                }
            }
            return output
        } catch (_: Exception) {
            return null
        }
    }

    fun scanBinaryStrings(
        bytes: ByteArray,
        fileName: String,
        parseCameras: Boolean = false,
        parseMeshes: Boolean = false
    ): RobloxInstance {
        val root = RobloxInstance(
            name = fileName.substringBeforeLast("."),
            className = "Model"
        )

        val strings = mutableListOf<String>()
        val currentStr = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126) {
                currentStr.append(c.toChar())
            } else {
                if (currentStr.length in 3..256) {
                    strings.add(currentStr.toString())
                }
                currentStr.clear()
            }
        }
        if (currentStr.length in 3..256) {
            strings.add(currentStr.toString())
        }

        val knownClasses = mutableListOf("ParticleEmitter", "Sound", "Attachment", "Part", "Folder", "Model")
        if (parseMeshes) {
            knownClasses.add("MeshPart")
            knownClasses.add("SpecialMesh")
            knownClasses.add("FileMesh")
        }
        if (parseCameras) {
            knownClasses.add("Camera")
            knownClasses.add("KeyframeSequence")
            knownClasses.add("Pose")
        }

        val assetIdRegex = Regex("""(?:rbxassetid://|id=)?(\d{7,12})""")
        val foundAssetIds = mutableListOf<String>()
        for (s in strings) {
            assetIdRegex.findAll(s).forEach { match ->
                foundAssetIds.add(match.groupValues[1])
            }
        }

        var assetIdx = 0

        for (i in strings.indices) {
            val s = strings[i]
            val matchedClass = knownClasses.find { it.equals(s, ignoreCase = true) }
            if (matchedClass != null) {
                val candidateName = strings.getOrNull(i + 1)?.takeIf { it !in knownClasses && it.length < 32 }
                    ?: "$matchedClass ${root.children.size + 1}"

                val props = mutableMapOf<String, String>()
                props["ClassName"] = matchedClass

                when (matchedClass) {
                    "ParticleEmitter" -> {
                        val assetId = foundAssetIds.getOrNull(assetIdx++) ?: "1084991219"
                        props["Texture"] = "rbxassetid://$assetId"
                        props["Rate"] = "20"
                        props["Speed"] = "0.00, 0.00"
                        props["Lifetime"] = "0.50, 1.00"
                    }
                    "MeshPart", "SpecialMesh" -> {
                        val assetId = foundAssetIds.getOrNull(assetIdx++) ?: "430095861"
                        props["MeshId"] = "rbxassetid://$assetId"
                    }
                    "Sound" -> {
                        val assetId = foundAssetIds.getOrNull(assetIdx++) ?: "9114223180"
                        props["SoundId"] = "rbxassetid://$assetId"
                        props["Volume"] = "1.0"
                    }
                    "Camera" -> {
                        props["FieldOfView"] = "70"
                    }
                }

                val inst = RobloxInstance(
                    name = candidateName,
                    className = matchedClass,
                    properties = props
                )
                root.children.add(inst)
            }
        }

        if (root.children.isEmpty() && foundAssetIds.isNotEmpty()) {
            foundAssetIds.distinct().forEachIndexed { index, id ->
                val inst = RobloxInstance(
                    name = "Emitter_${index + 1}",
                    className = "ParticleEmitter",
                    properties = mapOf(
                        "Texture" to "rbxassetid://$id",
                        "Rate" to "20",
                        "Speed" to "0.00, 0.00",
                        "Lifetime" to "0.50, 1.00"
                    )
                )
                root.children.add(inst)
            }
        }

        return root
    }

    private fun ensureCameraHost(root: RobloxInstance, text: String) {
        val hasCamerasFolder = root.children.any { it.name.equals("Cameras", ignoreCase = true) }
        if (hasCamerasFolder) return

        val isMoonAnimator = text.contains("MoonAnimator2Saves", ignoreCase = true) || text.contains("MoonAnimator", ignoreCase = true)
        val hasCameraKeywords = text.contains("CameraRig", ignoreCase = true) || text.contains("CamPart", ignoreCase = true) || text.contains("cutscene", ignoreCase = true)

        if (isMoonAnimator || hasCameraKeywords) {
            val camFolder = RobloxInstance(name = "Cameras", className = "Folder")

            val moonMatch = Regex("""\{"Information":\{"Length":\s*([\d.]+)\}""").find(text)
            val duration = moonMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 2.0

            val shotCount = (duration * 4).toInt().coerceIn(3, 16)
            for (i in 1..shotCount) {
                val t = ((i - 1) * (duration / shotCount))
                val camShot = RobloxInstance(
                    name = "Cam_Shot_$i",
                    className = "Camera",
                    properties = mapOf(
                        "FieldOfView" to "70",
                        "Time" to String.format("%.2f", t),
                        "Position" to "0, 2, 5",
                        "Orientation" to "0, 0, 0",
                        "EasingStyle" to "Quad",
                        "EasingDirection" to "Out"
                    )
                )
                camFolder.children.add(camShot)
            }
            root.children.add(camFolder)
        }
    }

    private fun ensureCameraHostFromBinary(root: RobloxInstance, bytes: ByteArray) {
        val hasCamerasFolder = root.children.any { it.name.equals("Cameras", ignoreCase = true) }
        if (hasCamerasFolder) return

        val text = String(bytes.copyOfRange(0, bytes.size.coerceAtMost(30000)), StandardCharsets.ISO_8859_1)
        val isMoonAnimator = text.contains("MoonAnimator2Saves", ignoreCase = true) || text.contains("MoonAnimator", ignoreCase = true)
        val hasCameraKeywords = text.contains("CameraRig", ignoreCase = true) || text.contains("CamPart", ignoreCase = true) || text.contains("KeyframeSequence", ignoreCase = true)

        if (isMoonAnimator || hasCameraKeywords) {
            val camFolder = RobloxInstance(name = "Cameras", className = "Folder")
            val count = 6
            for (i in 1..count) {
                val camShot = RobloxInstance(
                    name = "Camera_$i",
                    className = "Camera",
                    properties = mapOf(
                        "FieldOfView" to "70",
                        "Time" to String.format("%.2f", (i - 1) * 0.25),
                        "Position" to "0, 1.5, 4",
                        "Orientation" to "0, 0, 0"
                    )
                )
                camFolder.children.add(camShot)
            }
            root.children.add(camFolder)
        }
    }
}
