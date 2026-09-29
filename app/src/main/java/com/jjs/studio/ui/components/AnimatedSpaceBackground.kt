package com.jjs.studio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.*
import kotlin.random.Random

/**
 * Ultra-high-fidelity Animated Space Background inspired by space.js & barred.cc.
 *
 * Features:
 * - Multi-layer cosmic depth: Distant pinpoints, mid-field twinkling stars, and foreground bright stars
 * - Diffraction spikes (4-point James Webb / Hubble cross spikes) on luminous stars
 * - Organic drifting nebula gas clouds (deep midnight obsidian, cyan & cosmic violet)
 * - Dynamic meteor / shooting star trajectories with luminous ionization trails
 */
private data class CosmicStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val baseAlpha: Float,
    val pulseSpeed: Float,
    val phase: Float,
    val color: Color,
    val hasSpikes: Boolean = false
)

@Composable
fun AnimatedSpaceBackground(
    modifier: Modifier = Modifier
) {
    // Generate deterministic 3-tier cosmic starfield
    val (distantStars, midStars, brightStars) = remember {
        val rng = Random(4242)

        val distant = List(160) {
            CosmicStar(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = rng.nextFloat() * 0.7f + 0.35f,
                baseAlpha = rng.nextFloat() * 0.4f + 0.15f,
                pulseSpeed = rng.nextFloat() * 1.5f + 0.8f,
                phase = rng.nextFloat() * 6.28f,
                color = when {
                    rng.nextFloat() < 0.7f -> Color(0xFFCBD5E1) // Silver white
                    rng.nextFloat() < 0.88f -> Color(0xFF38BDF8) // Soft cyan
                    else -> Color(0xFFA78BFA) // Lavender
                }
            )
        }

        val mid = List(60) {
            CosmicStar(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = rng.nextFloat() * 1.0f + 1.0f,
                baseAlpha = rng.nextFloat() * 0.45f + 0.35f,
                pulseSpeed = rng.nextFloat() * 2.2f + 1.2f,
                phase = rng.nextFloat() * 6.28f,
                color = when {
                    rng.nextFloat() < 0.55f -> Color(0xFFFFFFFF)
                    rng.nextFloat() < 0.8f -> Color(0xFF7DD3FC) // Sky cyan
                    rng.nextFloat() < 0.92f -> Color(0xFFC084FC) // Radiant purple
                    else -> Color(0xFFFDE68A) // Warm amber
                }
            )
        }

        val bright = List(14) {
            CosmicStar(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = rng.nextFloat() * 1.2f + 2.2f,
                baseAlpha = rng.nextFloat() * 0.3f + 0.65f,
                pulseSpeed = rng.nextFloat() * 1.8f + 1.0f,
                phase = rng.nextFloat() * 6.28f,
                color = when {
                    rng.nextFloat() < 0.6f -> Color(0xFFFFFFFF)
                    rng.nextFloat() < 0.85f -> Color(0xFFBAE6FD)
                    else -> Color(0xFFDDD6FE)
                },
                hasSpikes = true
            )
        }

        Triple(distant, mid, bright)
    }

    // Continuous time ticker
    val infiniteTransition = rememberInfiniteTransition(label = "space_master")
    val cosmicTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 62.8318f, // 20*pi
        animationSpec = infiniteRepeatable(
            animation = tween(90000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cosmic_time"
    )

    // Meteor / Shooting star loop
    val meteorProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meteor"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Deep space void pitch black background
        drawRect(color = Color(0xFF030406))

        // 2. Cosmic Nebula Clouds (Multi-point layered radial washes)
        val nebula1X = w * (0.25f + 0.05f * sin(cosmicTime * 0.15f))
        val nebula1Y = h * (0.28f + 0.04f * cos(cosmicTime * 0.12f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0284C7).copy(alpha = 0.045f), // Cyan nebula core
                    Color(0xFF4F46E5).copy(alpha = 0.025f),
                    Color.Transparent
                ),
                center = Offset(nebula1X, nebula1Y),
                radius = w * 0.85f
            ),
            radius = w * 0.85f,
            center = Offset(nebula1X, nebula1Y)
        )

        val nebula2X = w * (0.75f - 0.04f * cos(cosmicTime * 0.1f))
        val nebula2Y = h * (0.72f - 0.05f * sin(cosmicTime * 0.14f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF9333EA).copy(alpha = 0.038f), // Purple nebula core
                    Color(0xFFDB2777).copy(alpha = 0.018f),
                    Color.Transparent
                ),
                center = Offset(nebula2X, nebula2Y),
                radius = w * 0.75f
            ),
            radius = w * 0.75f,
            center = Offset(nebula2X, nebula2Y)
        )

        // 3. Layer 1: Distant Stars (subtle twinkle)
        for (s in distantStars) {
            val pulse = (sin(cosmicTime * s.pulseSpeed + s.phase) + 1f) * 0.5f
            val alpha = (s.baseAlpha + pulse * 0.25f).coerceIn(0.1f, 0.85f)
            val cy = ((s.y + (cosmicTime * 0.001f * s.pulseSpeed)) % 1.0f) * h
            val cx = s.x * w

            drawCircle(
                color = s.color.copy(alpha = alpha),
                radius = s.radius,
                center = Offset(cx, cy)
            )
        }

        // 4. Layer 2: Mid-field Stars
        for (s in midStars) {
            val pulse = (sin(cosmicTime * s.pulseSpeed + s.phase) + 1f) * 0.5f
            val alpha = (s.baseAlpha + pulse * 0.35f).coerceIn(0.15f, 0.95f)
            val cy = ((s.y + (cosmicTime * 0.0016f * s.pulseSpeed)) % 1.0f) * h
            val cx = s.x * w

            drawCircle(
                color = s.color.copy(alpha = alpha * 0.35f),
                radius = s.radius * 2.2f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = s.color.copy(alpha = alpha),
                radius = s.radius,
                center = Offset(cx, cy)
            )
        }

        // 5. Layer 3: Foreground Bright Stars with 4-Point Diffraction Cross Spikes
        for (s in brightStars) {
            val pulse = (sin(cosmicTime * s.pulseSpeed + s.phase) + 1f) * 0.5f
            val alpha = (s.baseAlpha + pulse * 0.35f).coerceIn(0.4f, 1.0f)
            val cy = ((s.y + (cosmicTime * 0.0022f * s.pulseSpeed)) % 1.0f) * h
            val cx = s.x * w
            val center = Offset(cx, cy)

            // Outer atmospheric soft halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        s.color.copy(alpha = alpha * 0.55f),
                        s.color.copy(alpha = alpha * 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = s.radius * 4.5f
                ),
                radius = s.radius * 4.5f,
                center = center
            )

            // Diffraction Cross Spikes (Telescope Glint)
            val spikeLen = s.radius * (7.5f + pulse * 3.5f)
            val spikeAlpha = alpha * 0.65f

            // Horizontal spike
            drawLine(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = spikeAlpha), Color.Transparent),
                    center = center,
                    radius = spikeLen
                ),
                start = Offset(cx - spikeLen, cy),
                end = Offset(cx + spikeLen, cy),
                strokeWidth = 1.0f
            )

            // Vertical spike
            drawLine(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = spikeAlpha), Color.Transparent),
                    center = center,
                    radius = spikeLen
                ),
                start = Offset(cx, cy - spikeLen),
                end = Offset(cx, cy + spikeLen),
                strokeWidth = 1.0f
            )

            // Solid star core
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = s.radius,
                center = center
            )
        }

        // 6. Shooting Star / Meteor Trajectory
        if (meteorProgress in 0.0f..1.0f) {
            val startX = w * (0.92f - meteorProgress * 0.82f)
            val startY = h * (0.08f + meteorProgress * 0.58f)
            val tailLen = 110f
            val endX = startX - tailLen * 0.866f // cos(30 deg)
            val endY = startY + tailLen * 0.5f // sin(30 deg)

            val headPos = Offset(startX, startY)
            val tailPos = Offset(endX, endY)

            val intensity = sin(meteorProgress * Math.PI.toFloat()).coerceIn(0f, 1f)

            // Luminous Tail with fading ion trail
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = intensity * 0.95f),
                        Color(0xFF38BDF8).copy(alpha = intensity * 0.7f),
                        Color(0xFF818CF8).copy(alpha = intensity * 0.3f),
                        Color.Transparent
                    ),
                    start = headPos,
                    end = tailPos
                ),
                start = headPos,
                end = tailPos,
                strokeWidth = 2.2f
            )

            // Glowing head
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = intensity),
                        Color(0xFF38BDF8).copy(alpha = intensity * 0.6f),
                        Color.Transparent
                    ),
                    center = headPos,
                    radius = 8f
                ),
                radius = 8f,
                center = headPos
            )
            drawCircle(
                color = Color.White.copy(alpha = intensity),
                radius = 2.5f,
                center = headPos
            )
        }
    }
}
