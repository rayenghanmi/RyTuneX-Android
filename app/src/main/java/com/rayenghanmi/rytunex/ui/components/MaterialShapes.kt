package com.rayenghanmi.rytunex.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// Parametric blossom outline from the Material 3 Expressive shape family.
// Ripples between full circle and a gentle {lobes}-petal bloom based on depth.
// The phase is offset so one petal always points straight up for a centered look.
class BloomShape(
    val lobes: Int = 4,
    val depth: Float = 0.3f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = minOf(size.width, size.height) / 2f
        val steps = 96
        val twoPi = 2.0 * PI
        // Shift so a petal peak sits at 90 degrees (straight up)
        val phase = PI - lobes * (PI / 2)
        for (i in 0 until steps) {
            val angle = i * twoPi / steps
            val modulation = 0.5 * (1.0 - cos(lobes * angle + phase))
            val radius = baseRadius * (1.0 - depth * modulation)
            val x = (cx + cos(angle) * radius).toFloat()
            val y = (cy + sin(angle) * radius).toFloat()
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is BloomShape && other.lobes == lobes && other.depth == depth

    override fun hashCode(): Int = lobes * 31 + depth.hashCode()
}

// Material 3 Expressive squircle: a continuous-corner superellipse that reads as a
// rounded square with blendable corners instead of a circle or a creased rounded rect.
// roundness 3 = pill-leaning donut, 4 = the classic M3E squircle, 6+ leans square.
class SquircleShape(
    val roundness: Float = 4f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width / 2f
        val ry = size.height / 2f
        val exp = (2f / roundness).toDouble()
        val steps = 72
        val twoPi = 2.0 * PI
        for (i in 0..steps) {
            val angle = i * twoPi / steps
            val ct = cos(angle)
            val st = sin(angle)
            // Superellipse |x/rx|^n + |y/ry|^n = 1 sampled parametrically
            val x = cx + rx * sign(ct) * abs(ct).pow(exp)
            val y = cy + ry * sign(st) * abs(st).pow(exp)
            if (i == 0) {
                path.moveTo(x.toFloat(), y.toFloat())
            } else {
                path.lineTo(x.toFloat(), y.toFloat())
            }
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is SquircleShape && other.roundness == roundness

    override fun hashCode(): Int = roundness.hashCode()
}

// Small M3E palette used for per-element icon containers: each settings row picks a
// stable shape from its title so the app expresses the shape family without a
// sea of identical circles or squares. Depths stay shallow so glyphs keep dominance.
val M3EShapePalette: List<Shape> = listOf(
    SquircleShape(4f),                     // squircle
    BloomShape(lobes = 4, depth = 0.22f),  // clover
    BloomShape(lobes = 6, depth = 0.17f),  // 6-petal blossom
    BloomShape(lobes = 7, depth = 0.15f),  // 7-petal flower (petal phased up)
    BloomShape(lobes = 12, depth = 0.10f)  // cookie-12 scallop
)

// Deterministic assignment: a given setting always gets the same shape
fun m3eShapeFor(seed: String): Shape =
    M3EShapePalette[abs(seed.hashCode()) % M3EShapePalette.size]
