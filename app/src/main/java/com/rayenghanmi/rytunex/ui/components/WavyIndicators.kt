package com.rayenghanmi.rytunex.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Determinate wavy progress bar in the Material 3 Expressive style: flat thin track,
// round-capped wave that grows taller while the value is moving and settles flatter
@Composable
fun WavyLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    stroke: Dp = 6.dp,
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 16.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "wavyProgress"
    )

    Canvas(
        modifier.height(18.dp)
    ) {
        val strokePx = stroke.toPx()
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()
        val midY = size.height / 2
        val inset = strokePx / 2
        val trackEnd = size.width - inset

        // Flat track
        drawLine(
            color = trackColor,
            start = Offset(inset, midY),
            end = Offset(trackEnd, midY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )

        // Wavy active stroke; amplitude breathes with spring motion
        if (animatedProgress > 0.003f) {
            val endX = inset + (trackEnd - inset) * animatedProgress
            val twoPi = (2.0 * PI).toFloat()
            val k = twoPi / waveLenPx
            val path = Path()
            var x = inset
            var first = true
            while (x < endX) {
                val y = midY + sin(x * k) * ampPx
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += 2f
            }
            // Tuck the tip back onto the centerline so the round cap sits flush
            path.lineTo(endX, midY)
            drawPath(
                path,
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

// Small circular loading indicator in the Material 3 Expressive style: a wavy ring
// that slowly rotates and breathes on alternating breathing waves
@Composable
fun WavyLoadingIndicator(
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    stroke: Dp = 3.dp,
    amplitude: Dp = 1.75.dp
) {
    val transition = rememberInfiniteTransition(label = "wavyLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing)
        ),
        label = "rotation"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(modifier.size(indicatorSize)) {
        val strokePx = stroke.toPx()
        val ampPx = amplitude.toPx()
        val radius = (size.width - strokePx) / 2 * breathe - ampPx
        if (radius > 0f) {
            val cx = size.width / 2
            val cy = size.height / 2
            val twoPi = (2.0 * PI).toFloat()
            val phase = rotation * twoPi
            val scallop = 4f
            val path = Path()
            var angle = 0f
            var first = true
            while (angle < twoPi) {
                val r = radius + sin(angle * scallop + phase) * ampPx * breathe
                val x = cx + cos(angle) * r
                val y = cy + sin(angle) * r
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                angle += 0.06f
            }
            path.close()
            drawPath(
                path,
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
