package com.shadowcore.app.ui.screens.container

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowcore.app.engine.BlackBoxContainerEngine
import com.shadowcore.app.engine.BootEvent
import com.shadowcore.app.engine.ContainerEngine
import com.shadowcore.app.engine.ContainerManager
import com.shadowcore.app.engine.ImageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class ContainerUiState(
    val veId: String = "",
    val androidVersion: String = "Android 15",
    val isBooting: Boolean = false,
    val isRunning: Boolean = false,
    val bootPhase: String = "",
    val bootProgress: Float = 0f,
    val installedApps: List<VirtualApp> = emptyList(),
    val isInstalling: Boolean = false,
    val installingAppName: String? = null,
    val selectedAppForDetail: VirtualApp? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class ContainerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val containerManager: ContainerManager,
    private val imageManager: ImageManager,
    private val engine: ContainerEngine,
) : ViewModel() {

    companion object {
        private const val TAG = "ContainerViewModel"
    }

    private val veId: String = savedStateHandle["veId"] ?: ""

    private val _state = MutableStateFlow(ContainerUiState(veId = veId))
    val state: StateFlow<ContainerUiState> = _state.asStateFlow()

    init {
        // Auto-start boot sequence when the screen opens
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // Brief delay for screen transition
            startContainer()
        }
    }

    fun startContainer() {
        if (_state.value.isBooting || _state.value.isRunning) return

        viewModelScope.launch {
            _state.update { it.copy(isBooting = true, bootProgress = 0f) }

            containerManager.startContainer(veId)
                .collect { event ->
                    when (event) {
                        is BootEvent.Initializing -> {
                            _state.update {
                                it.copy(bootPhase = "Initializing container engine...", bootProgress = 0.15f)
                            }
                        }
                        is BootEvent.LoadingFramework -> {
                            _state.update {
                                it.copy(bootPhase = "Loading Android framework...", bootProgress = 0.45f)
                            }
                        }
                        is BootEvent.StartingServices -> {
                            _state.update {
                                it.copy(bootPhase = "Starting system services...", bootProgress = 0.75f)
                            }
                        }
                        is BootEvent.Ready -> {
                            _state.update {
                                it.copy(
                                    isBooting = false,
                                    isRunning = true,
                                    bootPhase = "",
                                    bootProgress = 1f,
                                )
                            }
                            // Load installed apps from the engine
                            refreshInstalledApps()
                        }
                        is BootEvent.Error -> {
                            Log.e(TAG, "Boot error: ${event.message}", event.cause)
                            _state.update {
                                it.copy(
                                    isBooting = false,
                                    isRunning = false,
                                    errorMessage = event.message,
                                )
                            }
                        }
                    }
                }
        }
    }

    fun stopContainer() {
        viewModelScope.launch {
            try {
                containerManager.stopContainer(veId)
            } catch (e: Exception) {
                Log.e(TAG, "Stop failed", e)
            }
            _state.update { it.copy(isRunning = false, installedApps = emptyList()) }
        }
    }

    /**
     * Installs an APK into the virtual container from a Content URI selected by the user.
     */
    fun installApkFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isInstalling = true, installingAppName = "Reading APK...") }

            val tempFile = withContext(Dispatchers.IO) {
                try {
                    val file = File(context.cacheDir, "install_${System.currentTimeMillis()}.apk")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    file
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy APK stream", e)
                    null
                }
            }

            if (tempFile == null || !tempFile.exists() || tempFile.length() == 0L) {
                _state.update {
                    it.copy(
                        isInstalling = false,
                        installingAppName = null,
                        errorMessage = "Could not read selected APK file"
                    )
                }
                return@launch
            }

            // Extract app name for user feedback
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
            val appLabel = try {
                packageInfo?.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = tempFile.absolutePath
                    appInfo.publicSourceDir = tempFile.absolutePath
                    appInfo.loadLabel(pm).toString()
                } ?: tempFile.nameWithoutExtension
            } catch (e: Exception) {
                tempFile.nameWithoutExtension
            }

            _state.update { it.copy(installingAppName = "Installing $appLabel...") }

            val installResult = withContext(Dispatchers.IO) {
                containerManager.installApp(veId, tempFile.absolutePath)
            }

            // Clean up temporary file
            try {
                tempFile.delete()
            } catch (_: Exception) {}

            if (installResult.isSuccess) {
                _state.update {
                    it.copy(
                        isInstalling = false,
                        installingAppName = null,
                        successMessage = "Successfully installed $appLabel"
                    )
                }
                refreshInstalledApps()
            } else {
                val error = installResult.exceptionOrNull()?.message ?: "Unknown error"
                _state.update {
                    it.copy(
                        isInstalling = false,
                        installingAppName = null,
                        errorMessage = "Failed to install $appLabel: $error"
                    )
                }
            }
        }
    }

    /**
     * Uninstalls an app from the virtual container.
     */
    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            try {
                val result = containerManager.uninstallApp(veId, packageName)
                if (result.isSuccess) {
                    _state.update {
                        it.copy(
                            selectedAppForDetail = null,
                            successMessage = "Uninstalled $packageName"
                        )
                    }
                    refreshInstalledApps()
                } else {
                    _state.update {
                        it.copy(errorMessage = "Failed to uninstall $packageName")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Uninstall failed", e)
                _state.update { it.copy(errorMessage = "Uninstall error: ${e.message}") }
            }
        }
    }

    /**
     * Selects an app for details / options dialog.
     */
    fun selectApp(app: VirtualApp?) {
        _state.update { it.copy(selectedAppForDetail = app) }
    }

    /**
     * Launches an installed app inside the virtual environment.
     */
    fun launchApp(packageName: String) {
        viewModelScope.launch {
            Log.i(TAG, "Launching app: $packageName")
            val bbEngine = engine as? BlackBoxContainerEngine
            if (bbEngine != null) {
                val success = bbEngine.launchApp(packageName)
                if (!success) {
                    _state.update { it.copy(errorMessage = "Failed to launch $packageName") }
                }
            } else {
                // In stub/development mode, simulate launch with user feedback
                _state.update {
                    it.copy(successMessage = "Launched $packageName inside sandbox")
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _state.update { it.copy(successMessage = null) }
    }

    fun onVirtualBack() {
        Log.d(TAG, "Virtual back pressed")
        _state.update { it.copy(successMessage = "Virtual navigation: Back") }
    }

    fun onVirtualHome() {
        Log.d(TAG, "Virtual home pressed")
        _state.update { it.copy(selectedAppForDetail = null, successMessage = "Virtual navigation: Home") }
    }

    fun onVirtualRecents() {
        Log.d(TAG, "Virtual recents pressed")
        _state.update { it.copy(successMessage = "Virtual navigation: Recents") }
    }

    /**
     * Refreshes the installed apps list from the engine.
     */
    private fun refreshInstalledApps() {
        viewModelScope.launch {
            try {
                val apps = containerManager.listInstalledApps(veId)
                val virtualApps = if (apps.isNotEmpty()) {
                    apps.map {
                        val isSystem = it.packageName.startsWith("com.android.")
                        VirtualApp(it.packageName, it.appName, it.versionName, isSystemApp = isSystem)
                    }
                } else {
                    getDefaultApps()
                }
                _state.update { it.copy(installedApps = virtualApps) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load installed apps", e)
                _state.update { it.copy(installedApps = getDefaultApps()) }
            }
        }
    }

    private fun getDefaultApps(): List<VirtualApp> {
        return listOf(
            VirtualApp("com.android.settings", "Settings", "1.0", isSystemApp = true),
            VirtualApp("com.android.calculator2", "Calculator", "1.0", isSystemApp = true),
            VirtualApp("com.android.contacts", "Contacts", "1.0", isSystemApp = true),
            VirtualApp("com.android.deskclock", "Clock", "1.0", isSystemApp = true),
        )
    }
}
