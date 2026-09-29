package com.jjs.studio.parser

import com.github.luben.zstd.Zstd
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Shared Roblox binary chunk codec.
 * Modern places use Zstd; older ones use raw LZ4 blocks.
 * Progress-aware helpers for large files.
 */
object ChunkCodec {

    data class Chunk(
        val name: String,
        val data: ByteArray,
        val compressedLen: Int,
        val uncompressedLen: Int
    )

    fun isZstd(input: ByteArray): Boolean {
        return input.size >= 4 &&
            (input[0].toInt() and 0xFF) == 0x28 &&
            (input[1].toInt() and 0xFF) == 0xB5 &&
            (input[2].toInt() and 0xFF) == 0x2F &&
            (input[3].toInt() and 0xFF) == 0xFD
    }

    fun decompress(input: ByteArray, uncompressedSize: Int): ByteArray {
        if (uncompressedSize <= 0) return input
        if (input.isEmpty()) return ByteArray(0)
        if (input.size == uncompressedSize) return input

        // Prefer Zstd when magic matches or LZ4 fails
        if (isZstd(input)) {
            try {
                val out = Zstd.decompress(input, uncompressedSize)
                if (out != null && out.isNotEmpty()) return out
            } catch (_: Throwable) { }
        }

        decompressLz4(input, uncompressedSize)?.let { return it }

        // Last resort: try Zstd anyway (some payloads omit a clear magic path)
        try {
            val out = Zstd.decompress(input, uncompressedSize.coerceAtLeast(input.size * 4))
            if (out != null && out.isNotEmpty()) {
                return if (out.size > uncompressedSize) out.copyOf(uncompressedSize) else out
            }
        } catch (_: Throwable) { }

        return input
    }

    fun decompressLz4(input: ByteArray, uncompressedSize: Int): ByteArray? {
        if (uncompressedSize <= 0) return input
        return try {
            val output = ByteArray(uncompressedSize)
            var src = 0
            var dst = 0
            while (src < input.size && dst < uncompressedSize) {
                val token = input[src++].toInt() and 0xFF
                var literalLen = token ushr 4
                if (literalLen == 15) {
                    while (true) {
                        if (src >= input.size) return null
                        val s = input[src++].toInt() and 0xFF
                        literalLen += s
                        if (s != 255) break
                    }
                }
                if (src + literalLen > input.size || dst + literalLen > uncompressedSize) break
                System.arraycopy(input, src, output, dst, literalLen)
                src += literalLen
                dst += literalLen
                if (dst >= uncompressedSize || src >= input.size) break
                if (src + 2 > input.size) break
                val offset = (input[src].toInt() and 0xFF) or ((input[src + 1].toInt() and 0xFF) shl 8)
                src += 2
                if (offset == 0) return null
                var matchLen = (token and 0x0F) + 4
                if ((token and 0x0F) == 15) {
                    while (true) {
                        if (src >= input.size) return null
                        val s = input[src++].toInt() and 0xFF
                        matchLen += s
                        if (s != 255) break
                    }
                }
                var matchSrc = dst - offset
                if (matchSrc < 0 || dst + matchLen > uncompressedSize) break
                var i = 0
                while (i < matchLen) {
                    output[dst++] = output[matchSrc++]
                    i++
                }
            }
            if (dst < uncompressedSize / 2) return null
            output
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Iterate binary place/model chunks one at a time.
     * Yields after each chunk so callers can update progress UI.
     */
    fun forEachChunk(
        bytes: ByteArray,
        onProgress: ((done: Int, total: Int, name: String) -> Unit)? = null,
        handler: (Chunk) -> Boolean // return false to stop
    ) {
        if (bytes.size < 32) return
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(14)
        buffer.short // version
        buffer.int // classCount
        buffer.int // instanceCount
        buffer.long // reserved

        val totalApprox = (bytes.size - 32).coerceAtLeast(1)
        while (buffer.remaining() >= 16) {
            val nameBytes = ByteArray(4)
            buffer.get(nameBytes)
            val name = String(nameBytes, StandardCharsets.US_ASCII)
            val compressedLen = buffer.int
            val uncompressedLen = buffer.int
            buffer.int // reserved
            if (compressedLen < 0 || buffer.remaining() < compressedLen) break
            val payload = ByteArray(compressedLen)
            if (compressedLen > 0) buffer.get(payload)
            val data = if (compressedLen == 0) {
                ByteArray(0)
            } else {
                decompress(payload, uncompressedLen)
            }
            onProgress?.invoke(buffer.position(), totalApprox + 32, name)
            val cont = handler(Chunk(name, data, compressedLen, uncompressedLen))
            if (!cont || name.startsWith("END")) break
        }
    }

    fun readLenString(buf: ByteBuffer): String? {
        if (buf.remaining() < 4) return null
        val len = buf.int
        if (len < 0 || len > buf.remaining() || len > 1_000_000) return null
        val arr = ByteArray(len)
        buf.get(arr)
        return try {
            String(arr, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            String(arr, StandardCharsets.ISO_8859_1)
        }
    }

    /** Roblox interleaved string arrays (type 0x01) without shared-string table. */
    fun readStringArray(buf: ByteBuffer, count: Int): List<String> {
        val out = ArrayList<String>(count.coerceAtMost(4096))
        for (i in 0 until count) {
            val s = readLenString(buf) ?: break
            out.add(s)
        }
        return out
    }
}
