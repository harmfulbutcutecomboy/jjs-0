package com.jjs.studio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.NodeKind
import kotlin.math.*

/**
 * High-performance 3D Cybernetic Viewport rendered on native Compose Canvas.
 * Supports:
 * - Touch-drag 3D orbit rotation (Pitch & Yaw)
 * - 3D Isometric / Perspective coordinate grid
 * - Real-time animated 3D particle simulation based on active skill timeline
 * - 3D Axis indicators (X, Y, Z) and glowing emitter core
 */
@Composable
fun Cyber3DViewport(
    nodes: List<JjsNode>,
    modifier: Modifier = Modifier
) {
    var pitch by remember { mutableFloatStateOf(28f) }
    var yaw by remember { mutableFloatStateOf(-35f) }
    var zoom by remember { mutableFloatStateOf(1.0f) }

    // Particle animation ticker
    val infiniteTransition = rememberInfiniteTransition(label = "3d_viewport")
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "anim_time"
    )

    val coreGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_glow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF040608),
                        Color(0xFF0B0E14)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2B3342),
                        Color(0xFF131720),
                        Color(0xFF384357)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yaw += dragAmount.x * 0.45f
                        pitch = (pitch - dragAmount.y * 0.45f).coerceIn(-80f, 80f)
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseScale = (size.width.coerceAtMost(size.height) * 0.38f) * zoom

            val radYaw = Math.toRadians(yaw.toDouble()).toFloat()
            val radPitch = Math.toRadians(pitch.toDouble()).toFloat()

            val cosYaw = cos(radYaw)
            val sinYaw = sin(radYaw)
            val cosPitch = cos(radPitch)
            val sinPitch = sin(radPitch)

            fun project3D(x: Float, y: Float, z: Float): Offset {
                // Rotate Yaw around Y axis
                val x1 = x * cosYaw - z * sinYaw
                val z1 = x * sinYaw + z * cosYaw
                // Rotate Pitch around X axis
                val y2 = y * cosPitch - z1 * sinPitch
                val z2 = y * sinPitch + z1 * cosPitch

                // Perspective division
                val fov = 3.5f
                val depth = fov / (fov + z2 * 0.7f).coerceAtLeast(0.4f)
                val screenX = cx + x1 * baseScale * depth
                val screenY = cy - y2 * baseScale * depth
                return Offset(screenX, screenY)
            }

            // 1. Draw 3D Ground Grid (-1f to 1f)
            val gridSize = 4
            val gridColor = Color(0xFF17202E)
            val gridAxisColor = Color(0xFF2A364F)

            for (i in -gridSize..gridSize) {
                val f = i / gridSize.toFloat()
                // Parallel to Z
                val p1 = project3D(f, 0f, -1f)
                val p2 = project3D(f, 0f, 1f)
                drawLine(
                    color = if (i == 0) gridAxisColor else gridColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (i == 0) 1.5f else 0.8f
                )
                // Parallel to X
                val p3 = project3D(-1f, 0f, f)
                val p4 = project3D(1f, 0f, f)
                drawLine(
                    color = if (i == 0) gridAxisColor else gridColor,
                    start = p3,
                    end = p4,
                    strokeWidth = if (i == 0) 1.5f else 0.8f
                )
            }

            // 2. Draw 3D Axis Vectors at Origin (0,0,0)
            val o = project3D(0f, 0f, 0f)
            val xAxis = project3D(0.4f, 0f, 0f)
            val yAxis = project3D(0f, 0.4f, 0f)
            val zAxis = project3D(0f, 0f, 0.4f)

            drawLine(Color(0xFFEF4444), o, xAxis, strokeWidth = 2f) // X Red
            drawLine(Color(0xFF22C55E), o, yAxis, strokeWidth = 2f) // Y Green
            drawLine(Color(0xFF3B82F6), o, zAxis, strokeWidth = 2f) // Z Blue

            // 3. Draw Glowing Core Emitter
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.8f * coreGlow),
                        Color(0xFF818CF8).copy(alpha = 0.3f * coreGlow),
                        Color.Transparent
                    ),
                    center = o,
                    radius = 24f * coreGlow
                ),
                radius = 24f * coreGlow,
                center = o
            )
            drawCircle(Color.White, radius = 3.5f, center = o)

            // 4. Render 3D Simulated Particles
            val particleNodes = nodes.filter { it.kind == NodeKind.PARTICLE }
            val count = if (particleNodes.isEmpty()) 16 else (particleNodes.size * 8).coerceIn(12, 48)

            for (idx in 0 until count) {
                val seed = idx * 137.5f
                val nodeIdx = idx % (particleNodes.size.coerceAtLeast(1))
                val node = particleNodes.getOrNull(nodeIdx)
                val speed = ((node?.speed ?: 15.0) / 20.0).toFloat().coerceIn(0.5f, 2.0f)
                val lifetime = ((node?.lifetime ?: 1.2) / 1.5).toFloat()

                // Calculate progress offset per particle
                val particleProgress = (animTime + (idx / count.toFloat())) % 1.0f

                // Spherical angle emission
                val theta = Math.toRadians((seed * 3.14).toDouble()).toFloat()
                val phi = Math.toRadians(((seed * 2.71) % 180.0) - 90.0).toFloat()

                val dist = (particleProgress * speed * lifetime * 0.85f)
                val px = cos(phi) * sin(theta) * dist
                val py = sin(phi) * dist + (particleProgress * 0.25f) // upward drift
                val pz = cos(phi) * cos(theta) * dist

                val screenPt = project3D(px, py, pz)
                val alpha = (1.0f - particleProgress).coerceIn(0f, 1f)
                val radius = (4.5f * (1.0f - particleProgress * 0.6f)).coerceAtLeast(1.5f)

                val pColor = when (idx % 3) {
                    0 -> Color(0xFF38BDF8) // Cyan
                    1 -> Color(0xFFC084FC) // Purple
                    else -> Color(0xFFF43F5E) // Crimson
                }.copy(alpha = alpha)

                drawCircle(
                    color = pColor,
                    radius = radius,
                    center = screenPt
                )

                // Particle tail trail
                val tailDist = (dist - 0.08f).coerceAtLeast(0f)
                val tx = cos(phi) * sin(theta) * tailDist
                val ty = sin(phi) * tailDist + ((particleProgress - 0.05f).coerceAtLeast(0f) * 0.25f)
                val tz = cos(phi) * cos(theta) * tailDist
                val tailPt = project3D(tx, ty, tz)

                drawLine(
                    color = pColor.copy(alpha = alpha * 0.4f),
                    start = tailPt,
                    end = screenPt,
                    strokeWidth = radius * 0.8f
                )
            }
        }

        // Viewport HUD Overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color(0xCC080B10), RoundedCornerShape(8.dp))
                .border(0.5.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF22C55E))
            )
            Text(
                text = "3D VIEWPORT • ORBIT DRAG",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp
            )
        }

        // Reset View Button
        IconButton(
            onClick = {
                pitch = 28f
                yaw = -35f
                zoom = 1.0f
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .background(Color(0xCC080B10), RoundedCornerShape(8.dp))
                .border(0.5.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset Camera",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }

        // Bottom status badge
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "P: ${pitch.roundToInt()}°  Y: ${yaw.roundToInt()}°",
                color = Color(0xFF64748B),
                fontSize = 10.sp
            )
            Text(
                text = "•  ${nodes.count { it.kind == NodeKind.PARTICLE }} EMITTERS",
                color = Color(0xFF38BDF8),
                fontSize = 10.sp
            )
        }
    }
}
