package com.rayenghanmi.rytunex.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Material 3 Expressive motion tokens
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Press state shared between the shape morph and press scale animations.
// Detected with our own pointer observer so it does not depend on how the
// child component wires its interactionSource.
class PressMorphState {
    var isPressed by mutableStateOf(false)
}

@Composable
fun rememberPressMorphState(): PressMorphState = remember { PressMorphState() }

// Observes presses without consuming events; the child click handler stays in charge
fun Modifier.detectPress(state: PressMorphState): Modifier = composed {
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            state.isPressed = true
            waitForUpOrCancellation()
            state.isPressed = false
        }
    }
}

// Shrinks slightly while pressed with a bouncy release, M3E style
fun Modifier.pressScale(
    state: PressMorphState,
    pressedScale: Float = 0.96f,
    enabled: Boolean = true
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (state.isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    detectPress(state).graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// Push-pop corner morph: the corner swells toward a full pill while pressed (M3E press feedback)
@Composable
fun rememberPressMorphCorner(
    state: PressMorphState,
    restRadius: Dp,
    pressedRadius: Dp,
    enabled: Boolean = true
): Dp {
    val corner by animateDpAsState(
        targetValue = if (state.isPressed && enabled) pressedRadius else restRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressMorphCorner"
    )
    return corner
}

// Chip corners fill out into a fuller pill while selected (M3E selection feedback)
@Composable
fun morphingChipCorner(
    selected: Boolean,
    unselectedRadius: Dp = 12.dp,
    selectedRadius: Dp = 22.dp
): Dp {
    val corner by animateDpAsState(
        targetValue = if (selected) selectedRadius else unselectedRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "morphingChipCorner"
    )
    return corner
}
