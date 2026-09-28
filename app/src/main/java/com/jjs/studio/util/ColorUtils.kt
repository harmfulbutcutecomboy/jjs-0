package com.jjs.studio.util

import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt

data class RgbColor(val r: Int, val g: Int, val b: Int) {
    fun toHex(): String = String.format("#%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    fun toComposeColor(): Color = Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    fun toRgbString(): String = "$r, $g, $b"

    fun distanceTo(other: RgbColor): Double {
        val dr = (r - other.r).toDouble()
        val dg = (g - other.g).toDouble()
        val db = (b - other.b).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    companion object {
        fun fromHex(hex: String): RgbColor {
            val clean = hex.trim().removePrefix("#")
            return try {
                when (clean.length) {
                    3 -> {
                        val r = clean.substring(0, 1).repeat(2).toInt(16)
                        val g = clean.substring(1, 2).repeat(2).toInt(16)
                        val b = clean.substring(2, 3).repeat(2).toInt(16)
                        RgbColor(r, g, b)
                    }
                    6 -> {
                        val r = clean.substring(0, 2).toInt(16)
                        val g = clean.substring(2, 4).toInt(16)
                        val b = clean.substring(4, 6).toInt(16)
                        RgbColor(r, g, b)
                    }
                    else -> RgbColor(255, 255, 255)
                }
            } catch (e: Exception) {
                RgbColor(255, 255, 255)
            }
        }

        fun parseRgbList(input: String?): List<RgbColor> {
            if (input.isNullOrBlank()) return emptyList()
            val regex = Regex("""(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})""")
            return regex.findAll(input).mapNotNull { match ->
                val r = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val g = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val b = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                RgbColor(r, g, b)
            }.toList()
        }
    }
}

data class Palette(
    val main: RgbColor = RgbColor(255, 51, 85),     // #ff3355
    val accent: RgbColor = RgbColor(247, 215, 248), // #f7d7f8
    val other: RgbColor = RgbColor(255, 255, 255)   // #ffffff
)

object ColorUtils {

    fun detectPaletteFromColors(colors: List<RgbColor>): Palette {
        if (colors.isEmpty()) return Palette()
        // Filter out near-black colors
        val valid = colors.filter { it.r + it.g + it.b > 25 }
        if (valid.isEmpty()) return Palette()

        // Group into frequency
        val freq = valid.groupingBy { "${it.r / 20 * 20},${it.g / 20 * 20},${it.b / 20 * 20}" }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        val dominant = freq.map { entry ->
            val parts = entry.key.split(",").map { it.toInt() }
            RgbColor(parts[0], parts[1], parts[2])
        }

        val main = dominant.getOrElse(0) { RgbColor(255, 51, 85) }
        val accent = dominant.getOrNull(1) ?: RgbColor(247, 215, 248)
        val other = dominant.getOrNull(2) ?: RgbColor(255, 255, 255)

        return Palette(main = main, accent = accent, other = other)
    }

    fun remapColorString(
        originalStr: String,
        detected: Palette,
        replacement: Palette
    ): String {
        val colors = RgbColor.parseRgbList(originalStr)
        if (colors.isEmpty()) return originalStr

        val remapped = colors.map { c ->
            val dMain = c.distanceTo(detected.main)
            val dAccent = c.distanceTo(detected.accent)
            val dOther = c.distanceTo(detected.other)

            when {
                dMain <= dAccent && dMain <= dOther -> replacement.main
                dAccent <= dOther -> replacement.accent
                else -> replacement.other
            }
        }

        return remapped.joinToString(", ") { "${it.r}, ${it.g}, ${it.b}" }
    }
}
