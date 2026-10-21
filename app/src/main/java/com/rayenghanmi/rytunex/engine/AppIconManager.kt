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

// In-memory LRU icon cache with background batch preloading.
object AppIconManager {

    // LRU cache: ~1000 icons, roughly 20MB for 96x96 bitmaps
    private val iconCache = LruCache<String, ImageBitmap>(1000)

    // Remember failed packages to avoid repeated binder IPC queries
    private val failedPackages = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private var preloadJob: Job? = null

    fun isFailed(packageName: String): Boolean {
        return failedPackages.contains(packageName)
    }

    // Non-blocking cache-only lookup
    fun getCached(packageName: String): ImageBitmap? {
        if (failedPackages.contains(packageName)) return null
        return iconCache.get(packageName)
    }

    // Pauses the background preloader
    fun pausePreload() {
        preloadJob?.cancel()
        preloadJob = null
    }

    // Query flags including disabled components and uninstalled apps on API 24+
    private fun installedAppsQueryFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_UNINSTALLED_PACKAGES or PackageManager.GET_DISABLED_COMPONENTS
    }

    // Loads and caches an icon on Dispatchers.IO
    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (failedPackages.contains(packageName)) return@withContext null
        iconCache.get(packageName)?.let { return@withContext it }

        try {
            val pm = context.packageManager
            val drawable = try {
                val appInfo = pm.getApplicationInfo(packageName, installedAppsQueryFlags())
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

    // Pre-warms the cache for the given package list
    fun preloadIcons(context: Context, packageNames: List<String>) {
        preloadJob?.cancel()
        preloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {}

            val pm = context.packageManager
            val installedAppsMap = try {
                pm.getInstalledApplications(installedAppsQueryFlags()).associateBy { it.packageName }
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

                // Yield between icons to avoid starving other work
                delay(3L)
            }
        }
    }

    // Pre-warms every installed package icon in one bulk query
    fun preloadAllIcons(context: Context) {
        preloadJob?.cancel()
        preloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {}

            val pm = context.packageManager
            val installedAppsMap = try {
                pm.getInstalledApplications(installedAppsQueryFlags()).associateBy { it.packageName }
            } catch (_: Throwable) {
                emptyMap<String, ApplicationInfo>()
            }

            for (pkg in installedAppsMap.keys) {
                if (iconCache.get(pkg) != null || failedPackages.contains(pkg)) continue

                try {
                    val drawable = installedAppsMap[pkg]?.let { it.loadIcon(pm) }
                        ?: pm.getApplicationIcon(pkg)
                    iconCache.put(pkg, drawableToBitmap(drawable).asImageBitmap())
                } catch (_: Throwable) {
                    failedPackages.add(pkg)
                }

                // Yield between icons to avoid starving other work
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
