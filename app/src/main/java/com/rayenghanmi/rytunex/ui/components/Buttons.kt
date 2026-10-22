package com.rayenghanmi.rytunex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.FilledTonalButton as MaterialFilledTonalButton
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Expressive Material buttons: M3E push-pop press feedback baked in. While pressed the
// corners fill toward a pillar and the button shrinks slightly, both on bouncy springs.
// Press state comes from our own pointer observer, so it works on every variant.
//
// The dp radius is read from the passed shape when it is a RoundedCornerShape; shapes
// of other kinds render as-is without the corner morph.
@Composable
private fun restCornerRadius(shape: Shape): Dp? {
    val rounded = shape as? RoundedCornerShape
    val density = LocalDensity.current
    // Absolute corner sizes ignore the shape extents; percent sizes report 0 on a
    // zero probe, which cleanly disables the morph for them.
    val px = rounded?.topStart?.toPx(Size.Zero, density) ?: return null
    return (px / density.density).dp
}

@Composable
private fun pressMorphShape(state: PressMorphState, rest: Shape, enabled: Boolean): Shape {
    if (rest !is RoundedCornerShape) return rest
    val restRadius = restCornerRadius(rest) ?: return rest
    return RoundedCornerShape(
        rememberPressMorphCorner(
            state = state,
            restRadius = restRadius,
            pressedRadius = (restRadius.value + 10f).dp,
            enabled = enabled
        )
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val pressState = rememberPressMorphState()
    MaterialButton(
        onClick = onClick,
        modifier = modifier.pressScale(pressState, enabled = enabled),
        enabled = enabled,
        shape = pressMorphShape(pressState, shape, enabled),
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val pressState = rememberPressMorphState()
    MaterialFilledTonalButton(
        onClick = onClick,
        modifier = modifier.pressScale(pressState, enabled = enabled),
        enabled = enabled,
        shape = pressMorphShape(pressState, shape, enabled),
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val pressState = rememberPressMorphState()
    MaterialOutlinedButton(
        onClick = onClick,
        modifier = modifier.pressScale(pressState, enabled = enabled),
        enabled = enabled,
        shape = pressMorphShape(pressState, shape, enabled),
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val pressState = rememberPressMorphState()
    MaterialTextButton(
        onClick = onClick,
        modifier = modifier.pressScale(pressState, enabled = enabled),
        enabled = enabled,
        shape = pressMorphShape(pressState, shape, enabled),
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}
