package com.shadowcore.app.ui.screens.container

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val NeonCyan = Color(0xFF00E5FF)
private val NeonGreen = Color(0xFF00E676)
private val NeonPurple = Color(0xFFBB86FC)
private val DeepSurface = Color(0xFF0D1117)
private val CardSurface = Color(0xFF161B22)
private val DimText = Color(0xFF8B949E)
private val StatusBarBg = Color(0xFF010409)

/**
 * Full-screen container display showing the virtual Android environment.
 * Acts as the main interaction surface for the running virtual environment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerScreen(
    onBack: () -> Unit,
    viewModel: ContainerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // APK file picker launcher
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.installApkFromUri(it, context)
        }
    }

    // Snackbar notifications
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DeepSurface,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DeepSurface),
        ) {
            // Virtual Status Bar
            VirtualStatusBar(
                androidVersion = state.androidVersion,
                isRunning = state.isRunning,
                onBack = onBack,
            )

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.isBooting -> {
                        BootingOverlay(
                            bootPhase = state.bootPhase,
                            bootProgress = state.bootProgress,
                        )
                    }
                    state.isRunning -> {
                        VirtualLauncher(
                            installedApps = state.installedApps,
                            onAppClick = { viewModel.launchApp(it) },
                            onAppLongClick = { viewModel.selectApp(it) },
                            onInstallApp = {
                                apkPickerLauncher.launch("*/*")
                            },
                        )
                    }
                    else -> {
                        StoppedOverlay(onStart = { viewModel.startContainer() })
                    }
                }

                // Installation Progress Modal
                if (state.isInstalling) {
                    InstallingModal(taskName = state.installingAppName ?: "Installing APK...")
                }

                // App Detail & Action Dialog
                state.selectedAppForDetail?.let { app ->
                    AppOptionsDialog(
                        app = app,
                        onDismiss = { viewModel.selectApp(null) },
                        onLaunch = {
                            viewModel.selectApp(null)
                            viewModel.launchApp(app.packageName)
                        },
                        onUninstall = {
                            viewModel.uninstallApp(app.packageName)
                        },
                    )
                }
            }

            // Virtual Navigation Bar
            VirtualNavBar(
                onBack = { viewModel.onVirtualBack() },
                onHome = { viewModel.onVirtualHome() },
                onRecents = { viewModel.onVirtualRecents() },
                isRunning = state.isRunning,
            )
        }
    }
}

@Composable
private fun VirtualStatusBar(
    androidVersion: String,
    isRunning: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StatusBarBg)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Exit Container",
                tint = DimText,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Running indicator
        if (isRunning) {
            val pulseAnim = rememberInfiniteTransition(label = "statusPulse")
            val alpha by pulseAnim.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "statusAlpha",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(NeonGreen.copy(alpha = alpha)),
            )
            Spacer(Modifier.width(6.dp))
        }

        Text(
            text = "ShadowCore • $androidVersion",
            color = DimText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.weight(1f))

        // Status icons
        Icon(Icons.Rounded.Wifi, null, tint = DimText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.BatteryFull, null, tint = DimText, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun VirtualLauncher(
    installedApps: List<VirtualApp>,
    onAppClick: (String) -> Unit,
    onAppLongClick: (VirtualApp) -> Unit,
    onInstallApp: () -> Unit,
) {
    val systemApps = installedApps.filter { it.isSystemApp }
    val userApps = installedApps.filter { !it.isSystemApp }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Welcome header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Virtual Environment",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${installedApps.size} apps available • Long press app for options",
                    color = DimText,
                    fontSize = 12.sp,
                )
            }

            FilledTonalButton(
                onClick = onInstallApp,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = NeonCyan.copy(alpha = 0.15f),
                    contentColor = NeonCyan,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Install APK", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // App grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            // Tools Section Header
            item(span = { GridItemSpan(4) }) {
                SectionHeader("Core Tools")
            }

            // Quick Tool Items
            item {
                AppGridItem(
                    label = "Install APK",
                    icon = Icons.Rounded.Add,
                    iconColor = NeonCyan,
                    onClick = onInstallApp,
                    isSpecial = true,
                )
            }

            item {
                AppGridItem(
                    label = "Settings",
                    icon = Icons.Rounded.Settings,
                    iconColor = Color(0xFF64B5F6),
                    onClick = { onAppClick("com.android.settings") },
                )
            }

            item {
                AppGridItem(
                    label = "Files",
                    icon = Icons.Rounded.Folder,
                    iconColor = Color(0xFFFFB74D),
                    onClick = { onAppClick("com.android.documentsui") },
                )
            }

            item {
                AppGridItem(
                    label = "Terminal",
                    icon = Icons.Rounded.Code,
                    iconColor = NeonGreen,
                    onClick = { onAppClick("com.android.terminal") },
                )
            }

            // User Installed Apps
            if (userApps.isNotEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    SectionHeader("Installed Apps (${userApps.size})")
                }

                items(userApps, key = { it.packageName }) { app ->
                    AppGridItem(
                        label = app.appName,
                        icon = Icons.Rounded.Android,
                        iconColor = NeonPurple,
                        onClick = { onAppClick(app.packageName) },
                        onLongClick = { onAppLongClick(app) },
                        badge = "User",
                    )
                }
            }

            // System Apps
            item(span = { GridItemSpan(4) }) {
                SectionHeader("System Apps")
            }

            items(systemApps, key = { it.packageName }) { app ->
                AppGridItem(
                    label = app.appName,
                    icon = when (app.packageName) {
                        "com.android.calculator2" -> Icons.Rounded.Calculate
                        "com.android.contacts" -> Icons.Rounded.Contacts
                        "com.android.deskclock" -> Icons.Rounded.Schedule
                        else -> Icons.Rounded.Apps
                    },
                    iconColor = DimText,
                    onClick = { onAppClick(app.packageName) },
                    onLongClick = { onAppLongClick(app) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = DimText,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSpecial: Boolean = false,
    badge: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSpecial) NeonCyan.copy(alpha = 0.12f)
                    else CardSurface
                )
                .then(
                    if (isSpecial) Modifier.border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(26.dp),
            )

            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonPurple.copy(alpha = 0.2f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        badge,
                        color = NeonPurple,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InstallingModal(taskName: String) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "ShadowCore Installer",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = taskName,
                    color = DimText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AppOptionsDialog(
    app: VirtualApp,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardSurface)
                        .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (app.isSystemApp) Icons.Rounded.Apps else Icons.Rounded.Android,
                        null,
                        tint = if (app.isSystemApp) DimText else NeonPurple,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column {
                    Text(app.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (app.isSystemApp) "System Component" else "Installed Package",
                        color = DimText,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Package: ${app.packageName}",
                    color = DimText,
                    fontSize = 11.sp,
                )
                if (app.versionName.isNotEmpty()) {
                    Text(
                        text = "Version: ${app.versionName}",
                        color = DimText,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    text = "Status: Sandboxed inside container",
                    color = NeonGreen.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan.copy(alpha = 0.2f),
                    contentColor = NeonCyan,
                ),
            ) {
                Text("Launch")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!app.isSystemApp) {
                    TextButton(
                        onClick = onUninstall,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252)),
                    ) {
                        Text("Uninstall")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = DimText)
                }
            }
        },
        containerColor = CardSurface,
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun BootingOverlay(
    bootPhase: String,
    bootProgress: Float,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Animated boot icon
            val rotation by rememberInfiniteTransition(label = "bootSpin").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                ),
                label = "bootRotation",
            )

            Icon(
                Icons.Rounded.Memory,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { rotationZ = rotation },
            )

            Text(
                "Booting Virtual Environment",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                bootPhase,
                color = NeonCyan,
                fontSize = 13.sp,
            )

            LinearProgressIndicator(
                progress = { bootProgress },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = NeonCyan,
                trackColor = CardSurface,
            )
        }
    }
}

@Composable
private fun StoppedOverlay(
    onStart: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = DimText,
                modifier = Modifier.size(72.dp),
            )
            Text(
                "Virtual Environment Stopped",
                color = DimText,
                fontSize = 16.sp,
            )
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan.copy(alpha = 0.15f),
                    contentColor = NeonCyan,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun VirtualNavBar(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecents: () -> Unit,
    isRunning: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StatusBarBg)
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back
        IconButton(onClick = onBack, enabled = isRunning) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = if (isRunning) Color.White.copy(alpha = 0.7f) else DimText.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp),
            )
        }

        // Home
        IconButton(onClick = onHome, enabled = isRunning) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        if (isRunning) Color.White.copy(alpha = 0.7f) else DimText.copy(alpha = 0.3f),
                        CircleShape,
                    ),
            )
        }

        // Recents
        IconButton(onClick = onRecents, enabled = isRunning) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(
                        2.dp,
                        if (isRunning) Color.White.copy(alpha = 0.7f) else DimText.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

/**
 * Data class representing an app installed inside the virtual environment.
 */
data class VirtualApp(
    val packageName: String,
    val appName: String,
    val versionName: String = "",
    val isSystemApp: Boolean = false,
)
