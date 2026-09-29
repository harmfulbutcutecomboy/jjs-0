package com.jjs.studio.parser

import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.NodeKind
import com.jjs.studio.model.VfxTreeNode
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * High-performance, memory-efficient Roblox Place and Model Parser.
 *
 * Implements the exact algorithm from app.js & parser.js:
 * 1. Selective Class Filtering: Ignores Terrain, Guis, Physics, Scripts to prevent OOM on 100MB+ places.
 * 2. True Hierarchy Navigation: Builds hierarchical folders/models/parts containing VFX.
 * 3. Supports drilling down into nested parts, meshes, and attachments.
 */
object RobloxPlaceParser {

    private val RELEVANT_CLASSES = setOf(
        "ParticleEmitter",
        "MeshPart", "SpecialMesh", "FileMesh",
        "Sound", "SoundEffect", "Beam",
        "Camera", "KeyframeSequence", "Pose", "CFrameValue", "StringValue",
        "Model", "Folder", "Configuration", "Actor", "WorldModel",
        "Part", "Attachment", "Tool", "Accessory", "WedgePart", "UnionOperation"
    )

    fun parse(
        bytes: ByteArray,
        fileName: String,
        parseCameras: Boolean = true,
        parseMeshes: Boolean = true,
        onProgress: ((Float, String) -> Unit)? = null
    ): VfxTreeNode {
        if (bytes.isEmpty()) {
            return VfxTreeNode(id = "root", name = fileName, className = "Workspace")
        }

        return try {
            onProgress?.invoke(0.02f, "Detecting format…")
            if (isXml(bytes)) {
                onProgress?.invoke(0.1f, "Parsing XML…")
                parseXmlPlace(ByteArrayInputStream(bytes), fileName, parseCameras, parseMeshes)
            } else if (isBinary(bytes)) {
                onProgress?.invoke(0.05f, "Reading binary chunks…")
                parseBinaryPlace(bytes, fileName, parseCameras, parseMeshes, onProgress)
            } else {
                onProgress?.invoke(0.2f, "Scanning strings…")
                scanPlace(bytes, fileName, parseCameras, parseMeshes)
            }.also { onProgress?.invoke(1f, "Done") }
        } catch (e: OutOfMemoryError) {
            System.gc()
            onProgress?.invoke(0.5f, "Recovering (low memory)…")
            scanPlace(bytes, fileName, parseCameras, parseMeshes)
        } catch (e: Exception) {
            onProgress?.invoke(0.5f, "Fallback scan…")
            scanPlace(bytes, fileName, parseCameras, parseMeshes)
        }
    }

    private fun isXml(bytes: ByteArray): Boolean {
        var start = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            start = 3
        }
        while (start < bytes.size && (bytes[start] == ' '.code.toByte() || bytes[start] == '\n'.code.toByte() || bytes[start] == '\r'.code.toByte() || bytes[start] == '\t'.code.toByte())) {
            start++
        }
        if (start >= bytes.size) return false
        val s = String(bytes.copyOfRange(start, (start + 120).coerceAtMost(bytes.size)), StandardCharsets.UTF_8)
        return s.startsWith("<") && (s.contains("<roblox", ignoreCase = true) || s.contains("<?xml", ignoreCase = true) || s.contains("<Item", ignoreCase = true))
    }

    private fun isBinary(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val magic = "<roblox!".toByteArray(StandardCharsets.US_ASCII)
        for (i in magic.indices) {
            if (bytes[i] != magic[i]) return false
        }
        return true
    }

    // ==========================================
    // STREAMING XML PARSER (Zero-OOM)
    // ==========================================
    private fun parseXmlPlace(
        stream: InputStream,
        fileName: String,
        parseCameras: Boolean,
        parseMeshes: Boolean
    ): VfxTreeNode {
        val root = VfxTreeNode(id = "root", name = fileName.substringBeforeLast("."), className = "Workspace", type = "ParticleFolder")
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        val stack = ArrayDeque<VfxTreeNode>()
        stack.addLast(root)

        var currentProps: MutableMap<String, String>? = null
        var currentPropName: String? = null
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag.equals("Item", ignoreCase = true)) {
                        val className = parser.getAttributeValue(null, "class") ?: "Instance"
                        val isCam = className.equals("Camera", ignoreCase = true) || className.equals("KeyframeSequence", ignoreCase = true)
                        val isMesh = className.equals("MeshPart", ignoreCase = true) || className.equals("SpecialMesh", ignoreCase = true)

                        if ((isCam && !parseCameras) || (isMesh && !parseMeshes)) {
                            // Skip this non-requested item
                            skipSubTree(parser)
                        } else if (!isRelevantClass(className)) {
                            // Check if it's a generic container that might hold VFX
                            skipSubTree(parser)
                        } else {
                            val node = VfxTreeNode(
                                id = "node_${System.identityHashCode(parser)}_${stack.size}",
                                name = className,
                                className = className,
                                type = if (isContainerClass(className)) "ParticleFolder" else "ParticleHost",
                                parentId = stack.lastOrNull()?.id
                            )
                            stack.lastOrNull()?.children?.add(node)
                            stack.addLast(node)
                        }
                    } else if (tag.equals("Properties", ignoreCase = true)) {
                        currentProps = mutableMapOf()
                    } else if (currentProps != null) {
                        currentPropName = parser.getAttributeValue(null, "name")
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim()
                    if (!text.isNullOrEmpty() && currentProps != null && currentPropName != null) {
                        currentProps[currentPropName] = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    if (tag.equals("Item", ignoreCase = true)) {
                        if (stack.size > 1) {
                            stack.removeLast()
                        }
                    } else if (tag.equals("Properties", ignoreCase = true)) {
                        if (currentProps != null && stack.isNotEmpty()) {
                            val curr = stack.last()
                            val name = currentProps["Name"]
                            if (!name.isNullOrBlank()) {
                                curr.name = name
                            }
                            // Convert properties into JjsNode if VFX
                            when (curr.className) {
                                "ParticleEmitter" -> {
                                    val particle = createParticleFromProps(curr.name, currentProps)
                                    curr.particles.add(particle)
                                }
                                "MeshPart", "SpecialMesh" -> {
                                    if (parseMeshes) {
                                        val mesh = createMeshFromProps(curr.name, currentProps)
                                        curr.meshes.add(mesh)
                                    }
                                }
                                "Camera" -> {
                                    if (parseCameras) {
                                        val cam = createCameraFromProps(curr.name, currentProps)
                                        curr.cameras.add(cam)
                                    }
                                }
                                "Sound" -> {
                                    val sfx = createSoundFromProps(curr.name, currentProps)
                                    curr.sounds.add(sfx)
                                }
                            }
                        }
                        currentProps = null
                        currentPropName = null
                    }
                }
            }
            eventType = parser.next()
        }

        pruneEmptyContainers(root)
        indexAndRollup(root, 1)
        return root
    }

    private fun skipSubTree(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("Item", ignoreCase = true)) depth++
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("Item", ignoreCase = true)) depth--
                }
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    // ==========================================
    // FAST BINARY PLACE PARSER (Chunk Filtered)
    // ==========================================
    private fun parseBinaryPlace(
        bytes: ByteArray,
        fileName: String,
        parseCameras: Boolean,
        parseMeshes: Boolean,
        onProgress: ((Float, String) -> Unit)? = null
    ): VfxTreeNode {
        val root = VfxTreeNode(id = "root", name = fileName.substringBeforeLast("."), className = "Workspace", type = "ParticleFolder")
        if (bytes.size < 32) return scanPlace(bytes, fileName, parseCameras, parseMeshes)

        data class RawClass(val id: Int, val name: String, val count: Int, val isRelevant: Boolean, val refIds: MutableList<Int> = mutableListOf())
        val classMap = mutableMapOf<Int, RawClass>()
        val instanceMap = mutableMapOf<Int, VfxTreeNode>()
        val parentMap = mutableMapOf<Int, Int>()
        val propsByRef = mutableMapOf<Int, MutableMap<String, String>>()

        val large = bytes.size > 50 * 1024 * 1024
        var chunkIndex = 0

        ChunkCodec.forEachChunk(bytes, onProgress = { done, total, name ->
            chunkIndex++
            val p = (0.08f + 0.75f * (done.toFloat() / total.coerceAtLeast(1))).coerceIn(0.08f, 0.9f)
            onProgress?.invoke(p, "Chunk $name · $chunkIndex")
            if (large) {
                try { Thread.sleep(2) } catch (_: InterruptedException) {}
            }
        }) { chunk ->
            val decomp = chunk.data
            when {
                chunk.name == "INST" -> {
                    try {
                        val cBuf = ByteBuffer.wrap(decomp).order(ByteOrder.LITTLE_ENDIAN)
                        if (cBuf.remaining() >= 8) {
                            val classId = cBuf.int
                            val className = ChunkCodec.readLenString(cBuf) ?: return@forEachChunk true
                            if (cBuf.remaining() > 0) cBuf.get()
                            val instCount = if (cBuf.remaining() >= 4) cBuf.int else 0
                            val isCam = className.equals("Camera", ignoreCase = true) ||
                                className.equals("KeyframeSequence", ignoreCase = true) ||
                                className.equals("Pose", ignoreCase = true) ||
                                className.equals("CFrameValue", ignoreCase = true) ||
                                className.equals("StringValue", ignoreCase = true)
                            val isMesh = className.equals("MeshPart", ignoreCase = true) ||
                                className.equals("SpecialMesh", ignoreCase = true) ||
                                className.equals("FileMesh", ignoreCase = true)
                            val isRelevant = isRelevantClass(className) && (!isCam || parseCameras) && (!isMesh || parseMeshes)
                            val rawClass = RawClass(classId, className, instCount, isRelevant)
                            if (instCount > 0 && cBuf.remaining() >= 4) {
                                var accum = 0
                                val n = instCount.coerceAtMost(cBuf.remaining() / 4)
                                for (i in 0 until n) {
                                    accum += cBuf.int
                                    rawClass.refIds.add(accum)
                                    if (isRelevant) {
                                        instanceMap[accum] = VfxTreeNode(
                                            id = "ref_$accum",
                                            name = className,
                                            className = className,
                                            type = if (isContainerClass(className)) "ParticleFolder" else "ParticleHost"
                                        )
                                    }
                                }
                            }
                            classMap[classId] = rawClass
                        }
                    } catch (_: Exception) {}
                }
                chunk.name == "PROP" -> {
                    try {
                        val pBuf = ByteBuffer.wrap(decomp).order(ByteOrder.LITTLE_ENDIAN)
                        if (pBuf.remaining() < 8) return@forEachChunk true
                        val classId = pBuf.int
                        val propName = ChunkCodec.readLenString(pBuf) ?: return@forEachChunk true
                        val typeByte = if (pBuf.remaining() > 0) pBuf.get().toInt() and 0xFF else 0
                        val rawClass = classMap[classId] ?: return@forEachChunk true
                        if (!rawClass.isRelevant) return@forEachChunk true
                        if (typeByte == 0x01) {
                            val values = ChunkCodec.readStringArray(pBuf, rawClass.refIds.size)
                            values.forEachIndexed { i, v ->
                                if (i < rawClass.refIds.size) {
                                    val ref = rawClass.refIds[i]
                                    if (propName == "Name") {
                                        instanceMap[ref]?.name = v
                                    } else {
                                        propsByRef.getOrPut(ref) { mutableMapOf() }[propName] = v
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                chunk.name == "PRNT" -> {
                    try {
                        val prBuf = ByteBuffer.wrap(decomp).order(ByteOrder.LITTLE_ENDIAN)
                        if (prBuf.remaining() > 0) {
                            prBuf.get()
                            val count = if (prBuf.remaining() >= 4) prBuf.int else 0
                            val childRefs = mutableListOf<Int>()
                            var accum = 0
                            for (i in 0 until count) {
                                if (prBuf.remaining() < 4) break
                                accum += prBuf.int
                                childRefs.add(accum)
                            }
                            var parentAccum = 0
                            for (i in 0 until count) {
                                if (prBuf.remaining() < 4) break
                                parentAccum += prBuf.int
                                if (i < childRefs.size) parentMap[childRefs[i]] = parentAccum
                            }
                        }
                    } catch (_: Exception) {}
                }
                chunk.name.startsWith("END") -> return@forEachChunk false
            }
            true
        }

        // Attach children to parents
        if (instanceMap.isNotEmpty()) {
            val childSet = mutableSetOf<Int>()
            for ((childRef, parentRef) in parentMap) {
                val child = instanceMap[childRef]
                val parent = instanceMap[parentRef]
                if (child != null && parent != null && childRef != parentRef) {
                    parent.children.add(child)
                    child.parentId = parent.id
                    childSet.add(childRef)
                }
            }

            for ((refId, node) in instanceMap) {
                if (!childSet.contains(refId)) {
                    root.children.add(node)
                    node.parentId = root.id
                }
            }

            for ((refId, node) in instanceMap) {
                val props = propsByRef[refId] ?: emptyMap()
                when (node.className) {
                    "ParticleEmitter" -> {
                        node.particles.add(createParticleFromProps(node.name, props))
                    }
                    "MeshPart", "SpecialMesh", "FileMesh" -> {
                        if (parseMeshes) node.meshes.add(createMeshFromProps(node.name, props))
                    }
                    "Camera" -> {
                        if (parseCameras) node.cameras.add(createCameraFromProps(node.name, props))
                    }
                    "Sound" -> {
                        node.sounds.add(createSoundFromProps(node.name, props))
                    }
                }
            }

            onProgress?.invoke(0.95f, "Building hierarchy…")
            pruneEmptyContainers(root)
            indexAndRollup(root, 1)
            if (root.children.isNotEmpty()) {
                return root
            }
        }

        return scanPlace(bytes, fileName, parseCameras, parseMeshes)
    }

    // ==========================================
    // STRING SCANNER FALLBACK (Ultra-Safe)
    // ==========================================
    fun scanPlace(
        bytes: ByteArray,
        fileName: String,
        parseCameras: Boolean,
        parseMeshes: Boolean
    ): VfxTreeNode {
        val root = VfxTreeNode(id = "root", name = fileName.substringBeforeLast("."), className = "Workspace", type = "ParticleFolder")

        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126) {
                sb.append(c.toChar())
            } else {
                if (sb.length in 3..128) {
                    strings.add(sb.toString())
                }
                sb.clear()
            }
        }
        if (sb.length in 3..128) strings.add(sb.toString())

        val idRegex = Regex("""(?:rbxassetid://|id=)?(\d{7,12})""")
        val foundIds = mutableListOf<String>()
        for (s in strings) {
            idRegex.findAll(s).forEach { match ->
                foundIds.add(match.groupValues[1])
            }
        }

        var idIndex = 0
        var emitterCount = 0

        // Look for model/folder groupings in strings
        var currentHost = root
        for (i in strings.indices) {
            val str = strings[i]
            if (str.equals("Model", ignoreCase = true) || str.equals("Folder", ignoreCase = true)) {
                val candidateName = strings.getOrNull(i + 1)?.takeIf { it.length in 2..32 && !isRelevantClass(it) } ?: "VFX_Group_${root.children.size + 1}"
                val container = VfxTreeNode(
                    id = "host_${root.children.size + 1}",
                    name = candidateName,
                    className = str,
                    type = "ParticleFolder",
                    parentId = root.id
                )
                root.children.add(container)
                currentHost = container
            } else if (str.equals("ParticleEmitter", ignoreCase = true)) {
                emitterCount++
                val name = strings.getOrNull(i + 1)?.takeIf { it.length in 2..32 && !isRelevantClass(it) } ?: "Emitter_$emitterCount"
                val tex = foundIds.getOrNull(idIndex++) ?: "1084991219"
                val particle = JjsNode(
                    kind = NodeKind.PARTICLE,
                    name = name,
                    texture = tex,
                    rate = 20.0,
                    size = "1.0, 0.2",
                    speedRange = "0.00, 0.00",
                    lifetimeRange = "0.50, 1.00",
                    emitCount = 1
                )
                val partHost = VfxTreeNode(
                    id = "pe_$emitterCount",
                    name = name,
                    className = "ParticleEmitter",
                    type = "ParticleHost",
                    parentId = currentHost.id
                )
                partHost.particles.add(particle)
                currentHost.children.add(partHost)
            } else if (parseMeshes && (str.equals("MeshPart", ignoreCase = true) || str.equals("SpecialMesh", ignoreCase = true))) {
                val name = strings.getOrNull(i + 1)?.takeIf { it.length in 2..32 && !isRelevantClass(it) } ?: "MeshPart"
                val meshId = foundIds.getOrNull(idIndex++) ?: "430095861"
                val mesh = JjsNode(
                    kind = NodeKind.VISUAL_MESH,
                    name = name,
                    meshId = meshId,
                    texture = "0"
                )
                val meshHost = VfxTreeNode(
                    id = "mesh_${currentHost.children.size + 1}",
                    name = name,
                    className = str,
                    type = "ParticleHost",
                    parentId = currentHost.id
                )
                meshHost.meshes.add(mesh)
                currentHost.children.add(meshHost)
            }
        }

        // If no structured particles found, synthesize from detected asset IDs
        if (emitterCount == 0 && foundIds.isNotEmpty()) {
            val distinctIds = foundIds.distinct().take(32)
            distinctIds.forEachIndexed { idx, id ->
                val pName = "Emitter_${idx + 1}"
                val particle = JjsNode(
                    kind = NodeKind.PARTICLE,
                    name = pName,
                    texture = id,
                    rate = 20.0,
                    size = "1.0, 0.2",
                    emitCount = 1
                )
                val host = VfxTreeNode(
                    id = "node_$idx",
                    name = pName,
                    className = "ParticleEmitter",
                    type = "ParticleHost",
                    parentId = root.id
                )
                host.particles.add(particle)
                root.children.add(host)
            }
        }

        pruneEmptyContainers(root)
        indexAndRollup(root, 1)
        return root
    }

    private fun isRelevantClass(className: String): Boolean {
        return RELEVANT_CLASSES.contains(className)
    }

    private fun isContainerClass(className: String): Boolean {
        return className.equals("Folder", ignoreCase = true) ||
                className.equals("Model", ignoreCase = true) ||
                className.equals("Configuration", ignoreCase = true) ||
                className.equals("Actor", ignoreCase = true) ||
                className.equals("Part", ignoreCase = true) ||
                className.equals("Tool", ignoreCase = true) ||
                className.equals("Accessory", ignoreCase = true)
    }

    private fun pruneEmptyContainers(node: VfxTreeNode): Boolean {
        val iterator = node.children.iterator()
        while (iterator.hasNext()) {
            val child = iterator.next()
            val hasVfx = pruneEmptyContainers(child)
            if (!hasVfx && child.particles.isEmpty() && child.meshes.isEmpty() && child.cameras.isEmpty() && child.sounds.isEmpty()) {
                iterator.remove()
            }
        }
        return node.children.isNotEmpty() || node.particles.isNotEmpty() || node.meshes.isNotEmpty() || node.cameras.isNotEmpty() || node.sounds.isNotEmpty()
    }

    private fun indexAndRollup(node: VfxTreeNode, counter: Int): Int {
        var nextId = counter
        if (node.id.startsWith("node_") || node.id == "root") {
            node.id = if (node.id == "root") "root" else "n${nextId++}"
        }
        var total = node.particles.size + node.meshes.size + node.cameras.size + node.sounds.size
        for (child in node.children) {
            child.parentId = node.id
            nextId = indexAndRollup(child, nextId)
            total += child.count
        }
        node.count = total
        return nextId
    }

    private fun createParticleFromProps(name: String, props: Map<String, String>): JjsNode {
        val tex = extractAssetId(props["Texture"] ?: props["TextureId"] ?: "")
        val rate = props["Rate"]?.toDoubleOrNull() ?: 20.0
        val speed = props["Speed"] ?: "0.00, 0.00"
        val size = props["Size"] ?: "1.0, 0.2"
        val lifetime = props["Lifetime"] ?: "0.50, 1.00"
        val color = props["Color"] ?: "255,255,255 255,255,255"
        val emitCount = (rate * 0.2).toInt().coerceIn(1, 15)

        return JjsNode(
            kind = NodeKind.PARTICLE,
            name = name,
            texture = tex,
            rate = rate,
            speedRange = speed,
            size = size,
            lifetimeRange = lifetime,
            color = color,
            emitCount = emitCount
        )
    }

    private fun createMeshFromProps(name: String, props: Map<String, String>): JjsNode {
        val meshId = extractAssetId(props["MeshId"] ?: "")
        val tex = extractAssetId(props["TextureId"] ?: "")
        return JjsNode(
            kind = NodeKind.VISUAL_MESH,
            name = name,
            meshId = meshId,
            texture = tex
        )
    }

    private fun createCameraFromProps(name: String, props: Map<String, String>): JjsNode {
        val fov = props["FieldOfView"]?.toDoubleOrNull() ?: 70.0
        return JjsNode(
            kind = NodeKind.VISUAL_CAMERA,
            name = name,
            fov = fov
        )
    }

    private fun createSoundFromProps(name: String, props: Map<String, String>): JjsNode {
        val soundId = extractAssetId(props["SoundId"] ?: "")
        return JjsNode(
            kind = NodeKind.SFX,
            name = name,
            soundId = soundId
        )
    }

    private fun extractAssetId(str: String): String {
        val digits = Regex("""\d{7,}""").find(str)?.value
        return digits ?: (if (str.isNotBlank() && str.all { it.isDigit() }) str else "0")
    }

    private fun decompressLz4(input: ByteArray, uncompressedSize: Int): ByteArray? {
        return ChunkCodec.decompress(input, uncompressedSize)
    }
}

