package com.rayenghanmi.rytunex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rayenghanmi.rytunex.R
import com.rayenghanmi.rytunex.engine.AdaptiveDeviceInspector
import com.rayenghanmi.rytunex.engine.RyTuneXDeepEngine
import com.rayenghanmi.rytunex.engine.UadDatabaseManager
import com.rayenghanmi.rytunex.ui.components.RyTuneXBrandText
import com.rayenghanmi.rytunex.ui.screens.DashboardScreen
import com.rayenghanmi.rytunex.ui.screens.DebloatScreen
import com.rayenghanmi.rytunex.ui.screens.OptimizationsScreen
import com.rayenghanmi.rytunex.ui.screens.SettingsScreen
import com.rayenghanmi.rytunex.ui.screens.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

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

    var isPreloading by remember { mutableStateOf(true) }

    // Preload EVERY UI screen during startup animation so tab navigation is instantaneous with zero stutter
    val preloadedScreens = remember { mutableStateListOf(*Screen.entries.toTypedArray()) }

    val context = LocalContext.current

    // Reactive watcher: re-scan and probe whenever Shizuku privilege status updates
    LaunchedEffect(isShizukuReady) {
        if (isShizukuReady) {
            launch(Dispatchers.IO) {
                RyTuneXDeepEngine.readCurrentSystemState(forceRefresh = true)
            }
            launch(Dispatchers.IO) {
                UadDatabaseManager.scanInstalledApps(context, forceRefresh = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        val t0 = System.currentTimeMillis()

        // Wait up to 500ms for Shizuku binder if not yet connected, so cold startup query is privileged
        if (!Shizuku.pingBinder()) {
            var waited = 0
            while (!Shizuku.pingBinder() && waited < 500) {
                delay(80)
                waited += 80
            }
        }

        // Asynchronously pre-warm system metrics, display refresh modes, UAD debloat database, and package icons
        val sysJob = async(Dispatchers.IO) {
            RyTuneXDeepEngine.readCurrentSystemState(forceRefresh = true)
        }
        val dispJob = async(Dispatchers.IO) {
            AdaptiveDeviceInspector.queryHardwareDisplayModes(forceRefresh = false)
        }
        val uadJob = async(Dispatchers.IO) {
            UadDatabaseManager.loadDatabase(context)
            try {
                val scanned = UadDatabaseManager.scanInstalledApps(context, forceRefresh = true)
                com.rayenghanmi.rytunex.engine.AppIconManager.preloadIcons(context, scanned.map { it.packageName })
            } catch (_: Throwable) {}
        }
        val iconJob = async(Dispatchers.IO) {
            try {
                val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES or android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
                } else {
                    @Suppress("DEPRECATION")
                    android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES or android.content.pm.PackageManager.GET_DISABLED_COMPONENTS
                }
                val installed = context.packageManager.getInstalledApplications(flags).map { it.packageName }
                com.rayenghanmi.rytunex.engine.AppIconManager.preloadIcons(context, installed)
            } catch (_: Throwable) {}
        }

        // Wait for essential system telemetry and debloat database
        sysJob.await()
        dispJob.await()
        uadJob.await()

        // Fluid cinematic duration: ~950ms total for full organic motion graphics choreography
        val elapsed = System.currentTimeMillis() - t0
        if (elapsed < 950L) {
            delay(950L - elapsed)
        }
        isPreloading = false
    }

    val splashAlpha by animateFloatAsState(
        targetValue = if (isPreloading) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )
    val splashScale by animateFloatAsState(
        targetValue = if (isPreloading) 1f else 1.08f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Retained multi-tab container - seamlessly pre-composed and preserved in memory
            preloadedScreens.forEach { screen ->
                val isCurrent = screen == currentScreen
                val alpha by animateFloatAsState(
                    targetValue = if (isCurrent) 1f else 0f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "screenAlpha_${screen.name}"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha = alpha
                            // Park hidden screens off-screen to avoid touch intercept and draw overhead
                            translationX = if (isCurrent || alpha > 0.01f) 0f else 100000f
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
                            isScreenVisible = isCurrent && !isPreloading
                        )
                        Screen.OPTIMIZATIONS -> OptimizationsScreen(
                            onLogUpdate = { log -> consoleLog += "\n$log" },
                            isScreenVisible = isCurrent && !isPreloading,
                            isShizukuReady = isShizukuReady
                        )
                        Screen.DEBLOAT -> DebloatScreen(
                            onLogUpdate = { log -> consoleLog += "\n$log" },
                            isScreenVisible = isCurrent && !isPreloading,
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

            // Top Status Bar Dark Faded Vignette Dim (passes touches through)
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

            // Bottom Navigation Bar Dark Faded Vignette Dim (passes touches through)
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

            // Modern One UI 9 Floating Navigation Pill with Tactile Spring Animations
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

                        // Tactile Organic Spring Scale: squish on press, pop bounce on release/selection
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

                        // Material 3 Expressive Active Pill Width Expansion
                        val pillWidth by animateDpAsState(
                            targetValue = if (isSelected) 52.dp else 36.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "navPillWidth_${screen.name}"
                        )

                        // Active Icon Pop Bounce Scale
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "navIconScale_${screen.name}"
                        )

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

            // Modern Startup Screen with Logo Motion Graphics (visible only during initial page preloading)
            if (splashAlpha > 0.01f) {
                ModernStartupSplashScreen(
                    alpha = splashAlpha,
                    scale = splashScale
                )
            }
        }
    }
}

/**
 * High-production-value Cyberpunk/Neon Motion Graphics Startup Animation.
 * Runs at locked 120 FPS with continuous organic floating, breathing radial halo,
 * concentric expanding pulse waves, and liquid cyber laser shimmer beam.
 */
@Composable
fun ModernStartupSplashScreen(
    alpha: Float,
    scale: Float
) {
    // 1. Tactile Bouncy Spring Entrance for the Logo
    val logoSpringScale = remember { Animatable(0.78f) }
    LaunchedEffect(Unit) {
        logoSpringScale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // 2. Continuous Organic Float & Pulse Transitions
    val infiniteTransition = rememberInfiniteTransition(label = "startupMotion")

    // Gentle vertical floating motion
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    // Breathing halo pulse
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )

    // Halo luminosity breathing
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    // Outer concentric cyber shockwave ring
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveScale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha"
    )

    // Continuous liquid laser beam sweep
    val laserPhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserPhase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        // Top Status Bar Vignette Dim on Splash
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
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

        // Bottom Navigation Bar Vignette Dim on Splash
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.50f)
                        )
                    )
                )
        )

        // Outer Concentric Cyber Wave Ring
        Box(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    translationY = floatY
                    scaleX = waveScale
                    scaleY = waveScale
                    this.alpha = waveAlpha
                }
                .background(
                    color = Color.Transparent,
                    shape = CircleShape
                )
                .clip(CircleShape)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = waveAlpha)),
                modifier = Modifier.fillMaxSize()
            ) {}
        }

        // Inner Ambient Radial Breathing Glow Halo
        Box(
            modifier = Modifier
                .size(250.dp)
                .graphicsLayer {
                    translationY = floatY
                    scaleX = haloScale
                    scaleY = haloScale
                    this.alpha = haloAlpha
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                translationY = floatY
            }
        ) {
            // Floating, Springing Logo Surface Container
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 8.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primary
                        )
                    )
                ),
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = logoSpringScale.value
                        scaleY = logoSpringScale.value
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "RyTuneX Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Signature Brand Text
            RyTuneXBrandText(
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Cyber Subtitle Badge
            Text(
                text = "ADVANCED TUNING ENGINE",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Continuous Liquid Laser Shimmer Beam (Zero steps, 120 FPS continuous motion)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier
                    .width(130.dp)
                    .height(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary,
                                    Color.Transparent
                                ),
                                startX = laserPhase * 350f,
                                endX = (laserPhase + 1f) * 350f
                            )
                        )
                )
            }
        }
    }
}
