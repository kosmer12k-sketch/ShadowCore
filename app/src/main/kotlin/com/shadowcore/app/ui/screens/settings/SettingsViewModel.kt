package com.shadowcore.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowcore.app.data.preferences.AppPreferences
import com.shadowcore.app.engine.BlackBoxContainerEngine
import com.shadowcore.app.vm.CapabilityReport
import com.shadowcore.app.vm.VmCapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: Int = 0, // 0=Auto, 1=Light, 2=Dark
    val capabilityReport: CapabilityReport? = null,
    val appVersion: String = "2.0.0-alpha",
    val engineName: String = "ShadowCore Sandbox Engine",
    val isBlackBoxAvailable: Boolean = false,
    val cacheSizeMb: String = "0.0 MB",
    val snackbarMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val capabilityChecker: VmCapabilityChecker,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val isBb = BlackBoxContainerEngine.isAvailable()
    private val hasAvf = capabilityChecker.capabilityReport.hasAvf

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            capabilityReport = capabilityChecker.capabilityReport,
            isBlackBoxAvailable = isBb,
            engineName = when {
                isBb -> "BlackBox Native Virtualization"
                hasAvf -> "AVF Micro-Virtualization Engine"
                else -> "ShadowCore Sandbox Engine"
            },
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        calculateCacheSize()
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            appPreferences.setThemeMode(mode)
        }
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            val sizeBytes = withContext(Dispatchers.IO) {
                try {
                    getDirSize(context.cacheDir)
                } catch (_: Exception) {
                    0L
                }
            }
            val sizeMb = String.format("%.2f MB", sizeBytes / (1024f * 1024f))
            _uiState.update { it.copy(cacheSizeMb = sizeMb) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()
                } catch (_: Exception) {}
            }
            calculateCacheSize()
            _uiState.update { it.copy(snackbarMessage = "Cache cleared successfully") }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun getDirSize(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytes = 0L
        dir.listFiles()?.forEach { file ->
            bytes += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return bytes
    }
}
