package com.jjs.studio.util

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Universal compression helper for JJS Studio.
 * Handles Zstd (with KLUv base64 header standard in JJS) with safe fallback.
 */
object ZstdHelper {

    fun isKluv(text: String): Boolean {
        val s = text.trim()
        return s.startsWith("KLUv") || s.startsWith("KLUv/")
    }

    fun decompressKLUv(base64Str: String): String {
        val clean = base64Str.trim()
        val rawBytes = try {
            Base64.decode(clean, Base64.DEFAULT)
        } catch (e: Exception) {
            java.util.Base64.getDecoder().decode(clean)
        }

        // Try native Zstd first if available
        try {
            val maxOut = 16 * 1024 * 1024
            val decompressed = com.github.luben.zstd.Zstd.decompress(rawBytes, maxOut)
            return String(decompressed, StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            // If zstd native is not linked or fails, try fallback
        }

        // Try direct stream or string
        return String(rawBytes, StandardCharsets.UTF_8)
    }

    fun compressToKLUv(json: String): String {
        val src = json.toByteArray(StandardCharsets.UTF_8)
        try {
            val compressed = com.github.luben.zstd.Zstd.compress(src, 3)
            return try {
                Base64.encodeToString(compressed, Base64.NO_WRAP)
            } catch (e: Exception) {
                java.util.Base64.getEncoder().encodeToString(compressed)
            }
        } catch (e: Throwable) {
            // Fallback encoding if zstd jni native library is unavailable in host
            val b64 = try {
                Base64.encodeToString(src, Base64.NO_WRAP)
            } catch (ex: Exception) {
                java.util.Base64.getEncoder().encodeToString(src)
            }
            return b64
        }
    }
}
