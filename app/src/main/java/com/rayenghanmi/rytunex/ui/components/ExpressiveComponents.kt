package com.rayenghanmi.rytunex.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import com.rayenghanmi.rytunex.theme.AccentColor
import com.rayenghanmi.rytunex.theme.AppThemeMode
import com.rayenghanmi.rytunex.theme.RyTuneXLogoColor
import com.rayenghanmi.rytunex.theme.accentSeed

// Card hierarchy position for grouped list items with morphing corners.
enum class SettingsCardPosition {
    Single,
    GroupedSingle,
    Top,
    Middle,
    Bottom,
}

// Spring-animated squircle that follows the grouped card position.
@Composable
fun expressiveClickableCardShape(
    interactionSource: MutableInteractionSource,
    position: SettingsCardPosition = SettingsCardPosition.Single,
): RoundedCornerShape {
    val pressed by interactionSource.collectIsPressedAsState()
    val topRadius by animateDpAsState(
        targetValue = when {
            pressed -> 28.dp
            position == SettingsCardPosition.Single -> 16.dp
            position in setOf(SettingsCardPosition.GroupedSingle, SettingsCardPosition.Top) -> 24.dp
            else -> 6.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "clickable-card-top-corner",
    )
    val bottomRadius by animateDpAsState(
        targetValue = when {
            pressed -> 28.dp
            position == SettingsCardPosition.Single -> 16.dp
            position in setOf(SettingsCardPosition.GroupedSingle, SettingsCardPosition.Bottom) -> 24.dp
            else -> 6.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "clickable-card-bottom-corner",
    )
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
}

// Clickable settings card with spring press feedback.
@Composable
fun MaterialSettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = expressiveClickableCardShape(interactionSource, position)

    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title.contains("RyTuneX")) {
                    Text(
                        text = buildRyTuneXAnnotatedString(title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (description != null) {
                    Text(
                        text = if (description.contains("RyTuneX")) buildRyTuneXAnnotatedString(description) else AnnotatedString(description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bottomContent != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    bottomContent()
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

// Toggle switch card with spring press feedback.
@Composable
fun MaterialSettingsSwitchCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = expressiveClickableCardShape(interactionSource, position)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title.contains("RyTuneX")) {
                    Text(
                        text = buildRyTuneXAnnotatedString(title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (description != null) {
                    Text(
                        text = if (description.contains("RyTuneX")) buildRyTuneXAnnotatedString(description) else AnnotatedString(description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    }
}

// Segmented selector with sticky buttons and a sliding spring indicator.
@Composable
fun ThemeModeSelector(
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = AppThemeMode.entries
    val targetIndex = modes.indexOf(themeMode).coerceAtLeast(0)

    val previousIndex = remember { mutableIntStateOf(targetIndex) }
    val direction = remember(targetIndex) {
        val dir = targetIndex - previousIndex.intValue
        previousIndex.intValue = targetIndex
        dir
    }
    val movingRight = direction > 0
    val movingLeft = direction < 0

    // Leading edge moves faster than the trailing edge so the pill stretches
    val indicatorStart by animateFloatAsState(
        targetValue = targetIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = if (movingLeft) Spring.StiffnessMedium else Spring.StiffnessMediumLow
        ),
        label = "fluidThemeIndicatorStart"
    )

    val indicatorEnd by animateFloatAsState(
        targetValue = (targetIndex + 1).toFloat(),
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = if (movingRight) Spring.StiffnessMedium else Spring.StiffnessMediumLow
        ),
        label = "fluidThemeIndicatorEnd"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                CircleShape
            )
    ) {
        val trackPadding = 4.dp
        val availableWidth = maxWidth - (trackPadding * 2)
        val slotWidth = availableWidth / modes.size

        // Enforce safe bounds during spring overshoot
        val actualStart = kotlin.math.min(indicatorStart, indicatorEnd - 0.65f)
        val actualEnd = kotlin.math.max(indicatorEnd, actualStart + 0.65f)
        val pillLeft = trackPadding + (slotWidth * actualStart)
        val pillWidth = slotWidth * (actualEnd - actualStart)

        // Squish height when stretched to preserve volume
        val stretchExtra = (actualEnd - actualStart - 1f).coerceAtLeast(0f)
        val pillHeight = (40.dp - (4.dp * stretchExtra)).coerceIn(35.dp, 40.dp)

        // Hairline dividers between unselected buttons fade under the pill
        val divider1Alpha = if (actualStart <= 1.05f && actualEnd >= 0.95f) {
            0f
        } else {
            val dist = kotlin.math.min(kotlin.math.abs(actualStart - 1f), kotlin.math.abs(actualEnd - 1f))
            (dist / 0.35f).coerceIn(0f, 1f)
        }
        if (divider1Alpha > 0.02f) {
            Box(
                modifier = Modifier
                    .offset(x = trackPadding + slotWidth - 0.5.dp)
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .height(18.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f * divider1Alpha),
                        shape = CircleShape
                    )
            )
        }

        val divider2Alpha = if (actualStart <= 2.05f && actualEnd >= 1.95f) {
            0f
        } else {
            val dist = kotlin.math.min(kotlin.math.abs(actualStart - 2f), kotlin.math.abs(actualEnd - 2f))
            (dist / 0.35f).coerceIn(0f, 1f)
        }
        if (divider2Alpha > 0.02f) {
            Box(
                modifier = Modifier
                    .offset(x = trackPadding + (slotWidth * 2) - 0.5.dp)
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .height(18.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f * divider2Alpha),
                        shape = CircleShape
                    )
            )
        }

        // Sliding indicator pill
        Box(
            modifier = Modifier
                .offset(x = pillLeft)
                .align(Alignment.CenterStart)
                .width(pillWidth)
                .height(pillHeight)
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                )
        )

        // Sticky buttons (no gaps)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(trackPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { mode ->
                val isSelected = themeMode == mode
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.14f else if (isPressed) 0.92f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "themeIconScale_${mode.name}"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(200),
                    label = "themeContentColor_${mode.name}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            onClick = { onThemeModeChanged(mode) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                AppThemeMode.System -> Icons.Rounded.BrightnessAuto
                                AppThemeMode.Light -> Icons.Rounded.LightMode
                                AppThemeMode.Dark -> Icons.Rounded.DarkMode
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            tint = contentColor
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Accent color swatch carousel.
@Composable
fun AccentColorSelector(
    selectedColor: AccentColor,
    onColorSelected: (AccentColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AccentColor.entries
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { accent ->
            val isSelected = selectedColor == accent
            val colorPreview = accentSeed(context, accent)
            val borderStroke = if (isSelected) {
                BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }

            Surface(
                onClick = { onColorSelected(accent) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                border = borderStroke,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colorPreview),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = accent.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Grouped container card matching Pixel Settings surfaces.
@Composable
fun GoogleGroupedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

// Backward-compatible ExpressiveCard alias.
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = Color.Transparent,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

// Section header.
@Composable
fun GoogleSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = if (title.contains("RyTuneX")) buildRyTuneXAnnotatedString(title) else AnnotatedString(title),
        style = MaterialTheme.typography.titleSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// Settings item row with leading icon surface, title, subtitle and trailing content.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleSettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    trailingContent: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title.contains("RyTuneX")) {
                    Text(
                        text = buildRyTuneXAnnotatedString(title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingContent()
                }
            }

            if (subtitle != null) {
                Text(
                    text = if (subtitle.contains("RyTuneX")) buildRyTuneXAnnotatedString(subtitle) else AnnotatedString(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bottomContent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    bottomContent()
                }
            }
        }
    }
}

// Telemetry metric tile.
@Composable
fun HardwareMetricTile(
    title: String,
    primaryValue: String,
    subtitle: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    progress: Float? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.defaultMinSize(minHeight = 126.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = primaryValue,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = accentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = subtitle,
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Pulsing status pill badge.
@Composable
fun PulsingStatusBadge(
    isActive: Boolean,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.error
    val currentColor = if (isActive) activeColor else inactiveColor
    val currentContainerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val currentOnContainerColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        color = currentContainerColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer { this.alpha = if (isActive) alpha else 1f }
                    .clip(CircleShape)
                    .background(currentColor)
            )
            Text(
                text = if (isActive) activeText else inactiveText,
                color = currentOnContainerColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// Floating pill header that slides in once the page is scrolled.
@Composable
fun FloatingTopPillHeader(
    visible: Boolean,
    title: String? = null,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
            ),
            exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .wrapContentWidth()
                    .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(
                            start = if (leadingContent != null) 10.dp else 18.dp,
                            end = if (trailingContent != null) 10.dp else 18.dp,
                            top = 4.dp,
                            bottom = 4.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    leadingContent?.invoke()

                    if (titleContent != null) {
                        titleContent()
                    } else if (title != null) {
                        Text(
                            text = if (title.contains("RyTuneX")) buildRyTuneXAnnotatedString(title) else AnnotatedString(title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    trailingContent?.invoke()
                }
            }
        }
    }
}

// Renders the trailing "X" of "RyTuneX" in the logo pink (#FE6093).
@Composable
fun buildRyTuneXAnnotatedString(
    text: String,
    xColor: Color = RyTuneXLogoColor
): AnnotatedString {
    return remember(text, xColor) {
        buildAnnotatedString {
            if (text.contains("RyTuneX")) {
                val parts = text.split("RyTuneX")
                parts.forEachIndexed { index, part ->
                    append(part)
                    if (index < parts.size - 1) {
                        append("RyTune")
                        withStyle(SpanStyle(color = xColor)) {
                            append("X")
                        }
                    }
                }
            } else {
                append(text)
            }
        }
    }
}

// Brand title with "RyTune" in surface color and "X" in logo pink.
@Composable
fun RyTuneXBrandText(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight = FontWeight.Bold,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    xColor: Color = RyTuneXLogoColor
) {
    Text(
        text = buildAnnotatedString {
            append("RyTune")
            withStyle(SpanStyle(color = xColor)) {
                append("X")
            }
        },
        style = style,
        fontWeight = fontWeight,
        color = textColor,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

// Alert dialog with comfortable edge margins instead of touching the borders.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(28.dp),
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(min = 280.dp, max = 560.dp)
            .fillMaxWidth(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}


