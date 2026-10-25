package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlinx.coroutines.isActive
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Concentric animated soundwave ripples radiating out from behind / under the vinyl record.
 */
@Composable
fun TvVinylSoundwaveRipples(
    isPlaying: Boolean,
    pulseColor: Color,
    modifier: Modifier = Modifier
) {
    if (!isPlaying) {
        Box(
            modifier = modifier
                .size(260.dp)
                .clip(CircleShape)
                .border(1.dp, pulseColor.copy(alpha = 0.15f), CircleShape)
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "vinyl_soundwave_ripples")

    val ripple1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )

    val ripple2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )

    val ripple3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple3"
    )

    Box(
        modifier = modifier.size(310.dp),
        contentAlignment = Alignment.Center
    ) {
        listOf(ripple1, ripple2, ripple3).forEach { progress ->
            val scale = 0.78f + (progress * 0.42f)
            val alpha = ((1f - progress) * 0.45f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .border(
                        width = (2.dp * (1f - progress * 0.4f)),
                        color = pulseColor.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Dynamic Moving Equalizer Wave Visualizer positioned under the vinyl disc on TV.
 * Features undulating sinusoidal audio wave ribbons combined with 24 animated frequency equalizer spectrum bars.
 */
@Composable
fun TvVinylWaveEqualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary
) {
    val transition = rememberInfiniteTransition(label = "tv_wave_equalizer")

    // Traveling wave phases
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    // Dynamic rhythmic beat pulses
    val beatPulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beatPulse"
    )

    val treblePulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(330, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "treblePulse"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Background smooth sine wave ribbon
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height * 0.55f

            if (width <= 0f || height <= 0f) return@Canvas

            // 1. Ambient filled wave curve under bars
            val wavePath = Path()
            val fillPath = Path()

            val steps = 40
            val stepX = width / steps

            wavePath.moveTo(0f, midY)
            fillPath.moveTo(0f, height)
            fillPath.lineTo(0f, midY)

            for (i in 0..steps) {
                val x = i * stepX
                val normX = i.toFloat() / steps
                // Window envelope (sinusoidal bell curve): 0 at sides, 1 in center
                val envelope = sin(normX * PI.toFloat())

                val activeMultiplier = if (isPlaying) 1f else 0.15f
                val yOffset = if (isPlaying) {
                    val w1 = sin((normX * 4f * PI.toFloat()) - phase1) * 14f * beatPulse
                    val w2 = cos((normX * 6f * PI.toFloat()) + phase2) * 8f * treblePulse
                    (w1 + w2) * envelope * activeMultiplier
                } else {
                    sin((normX * 2f * PI.toFloat()) - phase1) * 3f * envelope
                }

                val y = (midY + yOffset).coerceIn(4f, height - 4f)

                if (i == 0) {
                    wavePath.moveTo(x, y)
                } else {
                    wavePath.lineTo(x, y)
                }
                fillPath.lineTo(x, y)
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw translucent wave fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (isPlaying) 0.28f else 0.08f),
                        accentColor.copy(alpha = if (isPlaying) 0.12f else 0.03f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw glowing wave stroke line
            drawPath(
                path = wavePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.3f),
                        primaryColor,
                        accentColor,
                        primaryColor,
                        primaryColor.copy(alpha = 0.3f)
                    )
                ),
                style = Stroke(
                    width = if (isPlaying) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        // Foreground: 24 animated Equalizer Spectrum Wave bars
        Row(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val barCount = 24
            for (i in 0 until barCount) {
                val normPos = i.toFloat() / (barCount - 1)
                val envelope = sin(normPos * PI.toFloat()) // higher in middle, tapered on sides

                val heightFraction = if (isPlaying) {
                    // Combine traveling harmonic waves + local frequency offsets
                    val waveA = sin((normPos * 3.2f * PI.toFloat()) - phase1)
                    val waveB = cos((normPos * 5.4f * PI.toFloat()) + phase2)
                    val rhythm = if (i % 2 == 0) beatPulse else treblePulse
                    val base = 0.20f + (envelope * 0.70f)
                    val dynamic = ((waveA * 0.35f + waveB * 0.25f) * rhythm).toFloat()
                    (base + dynamic).coerceIn(0.12f, 1.0f)
                } else {
                    // Idle breathing state
                    (0.12f + envelope * 0.08f).coerceIn(0.08f, 0.25f)
                }

                // Individual Equalizer Bar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    // Floating peak dot if playing actively
                    if (isPlaying && heightFraction > 0.45f) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.85f))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height((40 * heightFraction).dp)
                            .clip(RoundedCornerShape(topStart = 2.5.dp, topEnd = 2.5.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor,
                                        primaryColor
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Smooth continuous vinyl rotation controller that spins smoothly while playing,
 * and maintains its current angular position when paused (does not jump or snap to 0).
 */
@Composable
fun rememberVinylRotation(
    isPlaying: Boolean,
    rotationDurationMs: Int = 6000
): State<Float> {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = rotation.value % 360f
                rotation.snapTo(current)
                rotation.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(
                        durationMillis = rotationDurationMs,
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    return rotation.asState()
}
