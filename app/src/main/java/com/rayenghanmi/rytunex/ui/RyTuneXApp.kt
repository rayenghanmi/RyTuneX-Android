package com.rayenghanmi.rytunex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rayenghanmi.rytunex.engine.AdaptiveDeviceInspector
import com.rayenghanmi.rytunex.engine.AppIconManager
import com.rayenghanmi.rytunex.engine.RyTuneXDeepEngine
import com.rayenghanmi.rytunex.engine.UadDatabaseManager
import com.rayenghanmi.rytunex.ui.components.EmphasizedDecelerateEasing
import com.rayenghanmi.rytunex.ui.components.EmphasizedEasing
import com.rayenghanmi.rytunex.ui.screens.DashboardScreen
import com.rayenghanmi.rytunex.ui.screens.DebloatScreen
import com.rayenghanmi.rytunex.ui.screens.OptimizationsScreen
import com.rayenghanmi.rytunex.ui.screens.SettingsScreen
import com.rayenghanmi.rytunex.ui.screens.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Screen(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard),
    OPTIMIZATIONS("Optimize", Icons.Rounded.Tune),
    DEBLOAT("Debloat", Icons.Rounded.CleaningServices),
    TERMINAL("Terminal", Icons.Rounded.Terminal),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

@Composable
fun RyTuneXApp(
    isShizukuReady: Boolean,
    onRequestShizuku: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var consoleLog by remember {
        mutableStateOf("=== RyTuneX Engine Initialized ===\n")
    }

    // Pre-compose all tabs and keep them alive for instant switching
    val preloadedScreens = remember { mutableStateListOf(*Screen.entries.toTypedArray()) }

    val context = LocalContext.current

    // Pre-warm system state, display modes, UAD scan and icons off the main thread
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            RyTuneXDeepEngine.readCurrentSystemState(forceRefresh = false)
        }
        launch(Dispatchers.IO) {
            AdaptiveDeviceInspector.queryHardwareDisplayModes(forceRefresh = false)
        }
        launch(Dispatchers.IO) {
            try {
                // Uses the cached/bundled database; GitHub refresh is on-demand only
                UadDatabaseManager.scanInstalledApps(context, forceRefresh = false)
            } catch (_: Throwable) {}
        }
        launch(Dispatchers.IO) {
            try {
                AppIconManager.preloadAllIcons(context)
            } catch (_: Throwable) {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Direction of travel between screens: slides incoming content from the right/left
            val lastScreenOrdinal = remember { mutableIntStateOf(currentScreen.ordinal) }
            val navDirection = remember(currentScreen) {
                val dir = if (currentScreen.ordinal >= lastScreenOrdinal.intValue) 1f else -1f
                lastScreenOrdinal.intValue = currentScreen.ordinal
                dir
            }
            preloadedScreens.forEach { screen ->
                val isCurrent = screen == currentScreen
                val alpha by animateFloatAsState(
                    targetValue = if (isCurrent) 1f else 0f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "screenAlpha_${screen.name}"
                )
                // Emphasized glide for the incoming screen, subtle push-out for the outgoing one
                val slideIn by animateFloatAsState(
                    targetValue = if (isCurrent) 1f else 0f,
                    animationSpec = tween(durationMillis = 380, easing = EmphasizedDecelerateEasing),
                    label = "screenSlide_${screen.name}"
                )
                val screenScale by animateFloatAsState(
                    targetValue = if (isCurrent) 1f else 0.96f,
                    animationSpec = tween(durationMillis = 380, easing = EmphasizedEasing),
                    label = "screenScale_${screen.name}"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = screenScale
                            scaleY = screenScale
                            val slide = if (isCurrent) {
                                (1f - slideIn) * 28f * navDirection
                            } else {
                                (1f - slideIn) * -14f * navDirection
                            }
                            // Park hidden screens off-screen to avoid touch intercept and draw overhead
                            translationX = if (isCurrent || alpha > 0.01f) slide else 100000f
                        }
                        .then(if (isCurrent) Modifier.zIndex(1f) else Modifier.zIndex(0f))
                ) {
                    when (screen) {
                        Screen.DASHBOARD -> DashboardScreen(
                            isShizukuReady = isShizukuReady,
                            onRequestAuth = onRequestShizuku,
                            onNavigateToDebloat = { currentScreen = Screen.DEBLOAT },
                            onNavigateToOptimizations = { currentScreen = Screen.OPTIMIZATIONS },
                            onLogUpdate = { log -> consoleLog += "\n$log" },
                            isScreenVisible = isCurrent
                        )
                        Screen.OPTIMIZATIONS -> OptimizationsScreen(
                            onLogUpdate = { log -> consoleLog += "\n$log" },
                            isScreenVisible = isCurrent,
                            isShizukuReady = isShizukuReady
                        )
                        Screen.DEBLOAT -> DebloatScreen(
                            onLogUpdate = { log -> consoleLog += "\n$log" },
                            isScreenVisible = isCurrent,
                            isShizukuReady = isShizukuReady
                        )
                        Screen.TERMINAL -> TerminalScreen(
                            consoleLog = consoleLog,
                            onClearLogs = { consoleLog = "=== RyTuneX Terminal Buffer Reset ===\n" },
                            onAppendLog = { log -> consoleLog += "\n$log" }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            isShizukuReady = isShizukuReady,
                            onRequestAuth = onRequestShizuku,
                            onClearLogs = { consoleLog = "=== RyTuneX Terminal Buffer Reset ===\n" },
                            onLogUpdate = { log -> consoleLog += "\n$log" }
                        )
                    }
                }
            }

            // Status bar scrim (passes touches through)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(8f)
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Nav bar scrim (passes touches through)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(5f)
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            // Floating navigation pill
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .height(66.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Screen.entries.forEach { screen ->
                        val isSelected = currentScreen == screen
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        // Squish on press, bounce on selection
                        val targetScale = when {
                            isPressed -> 0.88f
                            isSelected -> 1.05f
                            else -> 1.0f
                        }
                        val itemScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "navScale_${screen.name}"
                        )

                        val pillWidth by animateDpAsState(
                            targetValue = if (isSelected) 52.dp else 36.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "navPillWidth_${screen.name}"
                        )

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "navIconScale_${screen.name}"
                        )

                        // Selection hop: the icon jogs upward on the bouncy spring and settles
                        val iconJog = remember { Animatable(0f) }
                        LaunchedEffect(isSelected) {
                            if (isSelected) {
                                iconJog.animateTo(
                                    -1f,
                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                                )
                                iconJog.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
                            } else {
                                iconJog.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }

                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(180),
                            label = "navIconTint_${screen.name}"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            animationSpec = tween(180),
                            label = "navTextColor_${screen.name}"
                        )
                        val pillColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            animationSpec = tween(180),
                            label = "navPillColor_${screen.name}"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        if (!preloadedScreens.contains(screen)) {
                                            preloadedScreens.add(screen)
                                        }
                                        currentScreen = screen
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = pillColor,
                                    modifier = Modifier
                                        .height(30.dp)
                                        .width(pillWidth)
                                        .clip(CircleShape)
                                        .indication(interactionSource, ripple(bounded = true))
                                        .graphicsLayer {
                                            scaleX = itemScale
                                            scaleY = itemScale
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.label,
                                            tint = iconTint,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                    translationY = iconJog.value * 3.dp.toPx()
                                                }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = screen.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        letterSpacing = (-0.2).sp
                                    ),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = textColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
