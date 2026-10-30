package com.shadowcore.app.engine

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of [ContainerEngine] for development.
 *
 * Simulates the boot/stop lifecycle and manages simulated app installations.
 * Parses real APK metadata (package name, app label) when installing APK files.
 */
@Singleton
class StubContainerEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ContainerEngine {

    companion object {
        private const val TAG = "StubContainerEngine"
    }

    private val runningContainers = mutableSetOf<String>()

    private val installedAppsMap = ConcurrentHashMap<String, MutableList<InstalledApp>>()

    private fun getDefaultApps(): MutableList<InstalledApp> = mutableListOf(
        InstalledApp("com.android.settings", "Settings", null, "1.0"),
        InstalledApp("com.android.calculator2", "Calculator", null, "1.0"),
        InstalledApp("com.android.contacts", "Contacts", null, "1.0"),
    )

    override fun boot(veId: String, imagePath: String): Flow<BootEvent> = flow {
        Log.i(TAG, "Booting container $veId from image: $imagePath")

        emit(BootEvent.Initializing)
        delay(500)

        emit(BootEvent.LoadingFramework)
        delay(800)

        emit(BootEvent.StartingServices)
        delay(600)

        runningContainers.add(veId)
        emit(BootEvent.Ready)
        Log.i(TAG, "Container $veId boot complete")
    }

    override suspend fun stop(veId: String) {
        Log.i(TAG, "Stopping container $veId")
        runningContainers.remove(veId)
    }

    override fun isRunning(veId: String): Boolean =
        veId in runningContainers

    override suspend fun installApp(veId: String, apkPath: String): Result<String> {
        Log.i(TAG, "Stub: installing APK $apkPath into container $veId")
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, 0)

            val packageName = packageInfo?.packageName ?: "com.app.${System.currentTimeMillis()}"
            val appLabel = try {
                packageInfo?.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = apkPath
                    appInfo.publicSourceDir = apkPath
                    appInfo.loadLabel(pm).toString()
                } ?: File(apkPath).nameWithoutExtension
            } catch (e: Exception) {
                File(apkPath).nameWithoutExtension
            }
            val versionName = packageInfo?.versionName ?: "1.0.0"

            val appList = installedAppsMap.getOrPut(veId) { getDefaultApps() }
            // Replace if already installed or append
            appList.removeAll { it.packageName == packageName }
            appList.add(InstalledApp(packageName, appLabel, null, versionName))

            Log.i(TAG, "Successfully installed $appLabel ($packageName) v$versionName into $veId")
            Result.success(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK $apkPath", e)
            Result.failure(e)
        }
    }

    override suspend fun uninstallApp(veId: String, packageName: String): Result<Unit> {
        Log.i(TAG, "Stub: uninstalling $packageName from $veId")
        val appList = installedAppsMap[veId]
        if (appList != null) {
            appList.removeAll { it.packageName == packageName }
        }
        return Result.success(Unit)
    }

    override suspend fun listInstalledApps(veId: String): List<InstalledApp> {
        return installedAppsMap.getOrPut(veId) { getDefaultApps() }.toList()
    }
}
