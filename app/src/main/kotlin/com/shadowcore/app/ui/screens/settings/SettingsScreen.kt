package com.shadowcore.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowcore.app.domain.model.CapabilityTier
import com.shadowcore.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Engine Architecture
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Virtualization Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()

                    SettingInfoRow("Active Backend", state.engineName, MaterialTheme.colorScheme.primary)
                    SettingInfoRow("BlackBox Core", if (state.isBlackBoxAvailable) "✓ Linked" else "○ Sandbox Fallback")
                    SettingInfoRow("Hardware Mode", if (state.capabilityReport?.hasAvf == true) "AVF Supported" else "Standard Isolation")
                }
            }

            // Device Capability
            state.capabilityReport?.let { report ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.secondary)
                            Text("Device Capability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()

                        val tierColor = when (report.tier) {
                            CapabilityTier.TIER_0_UNSUPPORTED -> TierUnsupported
                            CapabilityTier.TIER_1_CONTAINER -> TierContainer
                            CapabilityTier.TIER_2_AVF -> TierAvf
                            CapabilityTier.TIER_3_HARDWARE_ACCELERATED -> TierHardware
                        }

                        SettingInfoRow("Tier", report.tier.displayName, tierColor)
                        SettingInfoRow("Device", report.deviceModel)
                        SettingInfoRow("Android API", "${report.androidVersion}")
                        SettingInfoRow("KVM", if (report.hasKvm) "✓ Available" else "✗ Not available")
                        SettingInfoRow("AVF", if (report.hasAvf) "✓ Available" else "✗ Not available")
                        SettingInfoRow("GPU Passthrough", if (report.hasGpuPassthrough) "✓ Available" else "✗ Not available")
                        SettingInfoRow("RAM", "${report.availableRamMb}MB / ${report.totalRamMb}MB")
                    }
                }
            }

            // Storage & Maintenance
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.CleaningServices, null, tint = Color(0xFFFFB74D))
                        Text("Storage & Maintenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()

                    SettingInfoRow("Temporary Cache", state.cacheSizeMb)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearCache() },
                        ) {
                            Text("Clear Cache")
                        }
                    }
                }
            }

            // Appearance — Theme Mode
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    val options = listOf(
                        0 to "Auto (System)",
                        1 to "Light",
                        2 to "Dark",
                    )

                    options.forEach { (mode, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                            )
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            // About
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    SettingInfoRow("Version", state.appVersion)
                    Text(
                        "ShadowCore — Virtual Android. Real Power.\nZero ads. Zero tracking. Zero interruptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}
