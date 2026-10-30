package com.shadowcore.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowcore.app.domain.model.CapabilityTier
import com.shadowcore.app.domain.model.VeState
import com.shadowcore.app.ui.components.VmCard
import com.shadowcore.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToConsole: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToActivation: () -> Unit = {},
    onNavigateToImageManager: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }
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
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("ShadowCore", fontWeight = FontWeight.Bold)
                        Text(
                            text = state.capabilityTier.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = when (state.capabilityTier) {
                                CapabilityTier.TIER_0_UNSUPPORTED -> TierUnsupported
                                CapabilityTier.TIER_1_CONTAINER -> TierContainer
                                CapabilityTier.TIER_2_AVF -> TierAvf
                                CapabilityTier.TIER_3_HARDWARE_ACCELERATED -> TierHardware
                            },
                        )
                    }
                },
                actions = {
                    // Activation button
                    IconButton(onClick = onNavigateToActivation) {
                        Icon(Icons.Rounded.Security, contentDescription = "Activate")
                    }
                    // Image manager button
                    IconButton(onClick = onNavigateToImageManager) {
                        Icon(Icons.Rounded.Download, contentDescription = "System Images")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New Environment") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.veProfiles.isEmpty()) {
            EmptyState(
                capabilityTier = state.capabilityTier,
                onCreateClick = onNavigateToCreate,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            val runningCount = state.veProfiles.count { it.state is VeState.Running }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Overview Dashboard Card
                item {
                    OverviewDashboardCard(
                        totalCount = state.veProfiles.size,
                        runningCount = runningCount,
                        tier = state.capabilityTier,
                    )
                }

                // Environment Cards
                items(
                    items = state.veProfiles,
                    key = { it.id },
                ) { profile ->
                    VmCard(
                        profile = profile,
                        onClick = {
                            // If running, go to container. Otherwise, go to detail.
                            if (profile.state is VeState.Running) {
                                onNavigateToConsole(profile.id)
                            } else {
                                onNavigateToDetail(profile.id)
                            }
                        },
                        onQuickStart = { onNavigateToConsole(profile.id) },
                        onQuickStop = { viewModel.stopVe(profile.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
                // Bottom spacer for FAB
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun OverviewDashboardCard(
    totalCount: Int,
    runningCount: Int,
    tier: CapabilityTier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "VIRTUAL ENVIRONMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$totalCount Configured",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Running badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (runningCount > 0) VeRunning.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .border(
                        1.dp,
                        if (runningCount > 0) VeRunning.copy(alpha = 0.4f)
                        else Color.Transparent,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (runningCount > 0) {
                    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "p"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(VeRunning.copy(alpha = pulse.value))
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (runningCount > 0) "$runningCount Active" else "All Stopped",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (runningCount > 0) VeRunning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    capabilityTier: CapabilityTier,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Memory,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Virtual Environments Yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (capabilityTier.isSupported)
                "Create your first virtual environment to get started"
            else
                "Your device doesn't support virtualization, but you can explore the interface",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCreateClick) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Environment")
        }
    }
}
