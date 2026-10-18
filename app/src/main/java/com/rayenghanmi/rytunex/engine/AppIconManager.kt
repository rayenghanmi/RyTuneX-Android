package com.rayenghanmi.rytunex.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance asynchronous App Icon Manager for RyTuneX.
 * Uses an in-memory LRU cache and background batch preloading to achieve 120 FPS
 * smooth scrolling in the Universal Debloater package list.
 */
object AppIconManager {

    // Cache up to 1000 app icons in memory (~20MB RAM for 72x72 scaled bitmaps)
    private val iconCache = LruCache<String, ImageBitmap>(1000)

    // Remember failed packages to avoid repeated binder IPC queries
    private val failedPackages = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private var preloadJob: Job? = null

    fun isFailed(packageName: String): Boolean {
        return failedPackages.contains(packageName)
    }

    /**
     * Fast non-blocking synchronous cache check for Compose UI thread.
     * Returns immediately in 0ms without performing disk I/O or Binder IPC.
     */
    fun getCached(packageName: String): ImageBitmap? {
        if (failedPackages.contains(packageName)) return null
        return iconCache.get(packageName)
    }

    /**
     * Pause background preloader during active drag gestures if needed.
     */
    fun pausePreload() {
        preloadJob?.cancel()
        preloadJob = null
    }

    /**
     * Asynchronously loads, scales, and caches the app icon on Dispatchers.IO.
     * Never blocks the Main Android UI thread.
     */
    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (failedPackages.contains(packageName)) return@withContext null
        iconCache.get(packageName)?.let { return@withContext it }

        try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_UNINSTALLED_PACKAGES or PackageManager.GET_DISABLED_COMPONENTS
            }
            val drawable = try {
                val appInfo = pm.getApplicationInfo(packageName, flags)
                appInfo.loadIcon(pm)
            } catch (_: Throwable) {
                pm.getApplicationIcon(packageName)
            }
            val bitmap = drawableToBitmap(drawable)
            val imageBitmap = bitmap.asImageBitmap()
            iconCache.put(packageName, imageBitmap)
            imageBitmap
        } catch (_: Exception) {
            failedPackages.add(packageName)
            null
        }
    }

    /**
     * Fast background pre-warming of all installed package icons.
     * Resolves icons using bulk ApplicationInfo to minimize Binder IPC overhead.
     */
    fun preloadIcons(context: Context, packageNames: List<String>) {
        preloadJob?.cancel()
        preloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {}

            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_UNINSTALLED_PACKAGES or PackageManager.GET_DISABLED_COMPONENTS
            }
            val installedAppsMap = try {
                pm.getInstalledApplications(flags).associateBy { it.packageName }
            } catch (_: Throwable) {
                emptyMap<String, ApplicationInfo>()
            }

            for (pkg in packageNames) {
                if (iconCache.get(pkg) != null || failedPackages.contains(pkg)) continue

                try {
                    val appInfo = installedAppsMap[pkg]
                    val drawable = if (appInfo != null) {
                        appInfo.loadIcon(pm)
                    } else {
                        pm.getApplicationIcon(pkg)
                    }
                    val bitmap = drawableToBitmap(drawable)
                    iconCache.put(pkg, bitmap.asImageBitmap())
                } catch (_: Throwable) {
                    failedPackages.add(pkg)
                }

                // Smooth batching to keep CPU cool and prevent thread starvation
                delay(3L)
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val targetSize = 96 // 96x96 px renders crisp icons on high DPI screens with minimal memory
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            val bm = drawable.bitmap
            if (bm.width == targetSize && bm.height == targetSize) {
                return bm
            }
        }
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return bitmap
    }
}
