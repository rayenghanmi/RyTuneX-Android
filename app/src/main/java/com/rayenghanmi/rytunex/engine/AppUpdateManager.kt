package com.rayenghanmi.rytunex.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSize: Long,
    val isUpdateAvailable: Boolean,
    val isPreRelease: Boolean = false
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Universal GitHub App Update Engine for RyTuneX.
 * Supports configurable repository sources (allowing instant migration to new clean or public repos),
 * pre-release and stable channel switching, intelligent semantic version comparison,
 * live progress downloads with GitHub S3 CDN redirect support,
 * and privileged installation via Shizuku or standard Android FileProvider package installer.
 */
object AppUpdateManager {

    var isChecking by mutableStateOf(false)
        private set

    var latestReleaseInfo by mutableStateOf<AppReleaseInfo?>(null)
        private set

    var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set

    var lastCheckError by mutableStateOf<String?>(null)
        private set

    /**
     * Gets the installed version name from PackageManager.
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }

    /**
     * Compares semantic versions following SemVer 2.0 rules (e.g. "1.0.1" > "1.0.0", "1.0.1" > "1.0.1-rc1").
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val cleanRemote = remote.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")

        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return false

        val remoteMain = cleanRemote.substringBefore("-").substringBefore("+")
        val currentMain = cleanCurrent.substringBefore("-").substringBefore("+")

        val rParts = remoteMain.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = currentMain.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // If main version numbers are equal, check pre-release tags:
        // A release with a pre-release tag has lower precedence than a normal release (SemVer 2.0).
        val remoteHasPre = cleanRemote.contains("-")
        val currentHasPre = cleanCurrent.contains("-")

        if (!remoteHasPre && currentHasPre) {
            // e.g. remote is "1.0.0" and current is "1.0.0-rc1" -> remote is newer
            return true
        }
        if (remoteHasPre && !currentHasPre) {
            // e.g. remote is "1.0.0-rc1" and current is "1.0.0" -> remote is older
            return false
        }
        if (remoteHasPre && currentHasPre) {
            // Both are pre-releases of the same main version, e.g. "1.0.0-rc2" vs "1.0.0-rc1"
            val rPre = cleanRemote.substringAfter("-")
            val cPre = cleanCurrent.substringAfter("-")
            return rPre.compareTo(cPre, ignoreCase = true) > 0
        }

        return false
    }

    /**
     * Checks the GitHub repository (rayenghanmi/RyTuneX-Android) for releases.
     * When includePreReleases is true, searches recent releases including pre-releases; otherwise targets latest stable.
     */
    suspend fun checkLatestUpdate(
        context: Context,
        repo: String = SettingsManager.GITHUB_REPO,
        includePreReleases: Boolean = SettingsManager.includePreReleases
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        isChecking = true
        lastCheckError = null

        try {
            val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix("/")
            val apiUrl = if (includePreReleases) {
                "https://api.github.com/repos/$cleanRepo/releases?per_page=10"
            } else {
                "https://api.github.com/repos/$cleanRepo/releases/latest"
            }

            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RyTuneX-Android-Updater")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val root: JSONObject = if (includePreReleases) {
                    val array = org.json.JSONArray(jsonText)
                    if (array.length() == 0) {
                        val msg = "No releases found on GitHub repo ($cleanRepo)."
                        withContext(Dispatchers.Main) {
                            lastCheckError = msg
                            isChecking = false
                        }
                        return@withContext Result.failure(Exception(msg))
                    }
                    var candidate: JSONObject? = null
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        if (!item.optBoolean("draft", false)) {
                            candidate = item
                            break
                        }
                    }
                    candidate ?: run {
                        val msg = "No published releases found."
                        withContext(Dispatchers.Main) {
                            lastCheckError = msg
                            isChecking = false
                        }
                        return@withContext Result.failure(Exception(msg))
                    }
                } else {
                    JSONObject(jsonText)
                }

                val tagName = root.optString("tag_name", "")
                val versionName = tagName.removePrefix("v").removePrefix("V")
                val releaseTitle = root.optString("name", "Version $tagName")
                val changelog = root.optString("body", "No release notes provided.")
                val htmlUrl = root.optString("html_url", "https://github.com/$cleanRepo/releases")
                val publishedAt = root.optString("published_at", "")
                val isPreRelease = root.optBoolean("prerelease", false)

                // Find the first APK asset
                var apkUrl: String? = null
                var apkName: String? = null
                var apkSize: Long = 0L

                val assets = root.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            apkName = name
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val currentVersion = getCurrentVersion(context)
                val updateAvailable = isNewerVersion(versionName, currentVersion)

                val info = AppReleaseInfo(
                    tagName = tagName,
                    versionName = versionName,
                    title = releaseTitle,
                    changelog = changelog,
                    htmlUrl = htmlUrl,
                    publishedAt = publishedAt,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName,
                    apkSize = apkSize,
                    isUpdateAvailable = updateAvailable,
                    isPreRelease = isPreRelease
                )

                withContext(Dispatchers.Main) {
                    latestReleaseInfo = info
                    isChecking = false
                }
                Result.success(info)
            } else if (responseCode == 404) {
                val msg = if (includePreReleases) {
                    "No releases found on GitHub repo ($cleanRepo)."
                } else {
                    "No stable public releases found on GitHub repo ($cleanRepo). Enable 'Include Pre-Releases' in Settings if this repo publishes pre-releases."
                }
                withContext(Dispatchers.Main) {
                    lastCheckError = msg
                    isChecking = false
                }
                Result.failure(Exception(msg))
            } else {
                val msg = "GitHub API returned HTTP $responseCode"
                withContext(Dispatchers.Main) {
                    lastCheckError = msg
                    isChecking = false
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Failed to check GitHub releases"
            withContext(Dispatchers.Main) {
                lastCheckError = msg
                isChecking = false
            }
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file to the app's cache directory, following GitHub S3 CDN redirects.
     */
    suspend fun downloadApk(
        context: Context,
        releaseInfo: AppReleaseInfo,
        onProgress: ((DownloadState) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadUrl = releaseInfo.apkDownloadUrl
        if (downloadUrl.isNullOrBlank()) {
            val err = "No APK download URL available for this release."
            withContext(Dispatchers.Main) {
                downloadState = DownloadState.Error(err)
            }
            return@withContext Result.failure(Exception(err))
        }

        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val fileName = releaseInfo.apkFileName ?: "RyTuneX-${releaseInfo.versionName}.apk"
            val targetFile = File(updatesDir, fileName)

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirects = 0

            // Follow HTTP redirects (GitHub releases redirect to AWS S3 CDN)
            while (true) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "RyTuneX-Android-Updater")

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    currentUrl = connection.getHeaderField("Location")
                    redirects++
                    if (redirects > 5) throw Exception("Too many HTTP redirects while downloading APK")
                    continue
                }
                break
            }

            val totalBytes = connection.contentLengthLong.let { if (it > 0) it else releaseInfo.apkSize }
            var downloadedBytes = 0L

            connection.inputStream.use { input: InputStream ->
                FileOutputStream(targetFile).use { output: FileOutputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                        val state = DownloadState.Downloading(percent, downloadedBytes, totalBytes)
                        withContext(Dispatchers.Main) {
                            downloadState = state
                            onProgress?.invoke(state)
                        }
                    }
                }
            }

            val completed = DownloadState.Completed(targetFile)
            withContext(Dispatchers.Main) {
                downloadState = completed
                onProgress?.invoke(completed)
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            val msg = "Download failed: ${e.message}"
            withContext(Dispatchers.Main) {
                downloadState = DownloadState.Error(msg)
                onProgress?.invoke(DownloadState.Error(msg))
            }
            Result.failure(e)
        }
    }

    /**
     * Copies the downloaded APK into Shizuku shell's environment via process stdin.
     */
    private fun stageApkToShell(apkFile: File, dstPath: String): Boolean {
        var process: Process? = null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", "cat > \"$dstPath\""), null, null) as Process
            process = proc

            apkFile.inputStream().use { input ->
                proc.outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        } finally {
            process?.let { p ->
                try {
                    if (p.isAlive) p.destroyForcibly()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Writes arbitrary script content to the shell environment.
     */
    private fun writeScriptToShell(content: String, dstPath: String): Boolean {
        var process: Process? = null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", "cat > \"$dstPath\""), null, null) as Process
            process = proc

            proc.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(content)
                writer.flush()
            }
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        } finally {
            process?.let { p ->
                try {
                    if (p.isAlive) p.destroyForcibly()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Installs the downloaded APK by uninstalling the older build first (purging any certificate/hash
     * mismatch) and installing the new APK, preserving preferences across the installation,
     * then relaunching RyTuneX.
     */
    suspend fun installApk(
        context: Context,
        apkFile: File,
        onLogUpdate: ((String) -> Unit)? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            return@withContext Result.failure(Exception("APK file does not exist on disk."))
        }

        // 1. Privileged Shizuku uninstall-then-install update
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            try {
                onLogUpdate?.invoke("[UPDATE]: Staging update APK to shell environment...")
                val stagedApkPath = "/data/local/tmp/rytunex_update.apk"
                val scriptPath = "/data/local/tmp/install_rytunex.sh"

                val stageSuccess = stageApkToShell(apkFile, stagedApkPath)
                if (!stageSuccess) {
                    onLogUpdate?.invoke("[UPDATE WARN]: Direct stream staging failed, attempting fallback copy...")
                    ShellEngine.exec("cp \"${apkFile.absolutePath}\" \"$stagedApkPath\" 2>/dev/null")
                }
                ShellEngine.exec("chmod 644 \"$stagedApkPath\"")

                // Always uninstall old version and install new APK to eliminate certificate/hash conflicts
                onLogUpdate?.invoke("[UPDATE]: Preparing updater worker script (uninstall previous & install update)...")
                val scriptContent = """
                    #!/system/bin/sh
                    sleep 1
                    # Backup preferences if present
                    cp /data/data/com.rayenghanmi.rytunex/shared_prefs/rytunex_preferences.xml /data/local/tmp/rytunex_prefs.xml 2>/dev/null

                    # Clean uninstall to purge mismatched signature/hash record
                    pm uninstall com.rayenghanmi.rytunex

                    # Clean install downloaded release APK with auto-granted runtime permissions
                    pm install -g -d /data/local/tmp/rytunex_update.apk
                    rm -f /data/local/tmp/rytunex_update.apk

                    # Restore preferences to the new app installation
                    if [ -f /data/local/tmp/rytunex_prefs.xml ]; then
                        mkdir -p /data/data/com.rayenghanmi.rytunex/shared_prefs 2>/dev/null
                        cp /data/local/tmp/rytunex_prefs.xml /data/data/com.rayenghanmi.rytunex/shared_prefs/rytunex_preferences.xml 2>/dev/null
                        NEW_UID=${'$'}(stat -c '%u' /data/data/com.rayenghanmi.rytunex 2>/dev/null)
                        if [ -n "${'$'}NEW_UID" ]; then
                            chown -R ${'$'}NEW_UID:${'$'}NEW_UID /data/data/com.rayenghanmi.rytunex/shared_prefs 2>/dev/null
                            chmod 700 /data/data/com.rayenghanmi.rytunex/shared_prefs 2>/dev/null
                            chmod 660 /data/data/com.rayenghanmi.rytunex/shared_prefs/rytunex_preferences.xml 2>/dev/null
                        fi
                        rm -f /data/local/tmp/rytunex_prefs.xml 2>/dev/null
                    fi

                    # Relaunch RyTuneX
                    am start -n com.rayenghanmi.rytunex/.MainActivity || monkey -p com.rayenghanmi.rytunex -c android.intent.category.LAUNCHER 1
                    rm -f /data/local/tmp/install_rytunex.sh
                """.trimIndent()

                writeScriptToShell(scriptContent, scriptPath)
                ShellEngine.exec("chmod 755 \"$scriptPath\"")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Reinstalling update to resolve signature mismatch... RyTuneX will restart momentarily.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // Launch detached in background so it executes after RyTuneX process terminates
                ShellEngine.exec("(nohup /system/bin/sh \"$scriptPath\" >/data/local/tmp/rytunex_install.log 2>&1 & || /system/bin/sh \"$scriptPath\" >/data/local/tmp/rytunex_install.log 2>&1 &)")
                onLogUpdate?.invoke("[UPDATE]: Clean reinstall worker launched in background.")
                return@withContext Result.success(true)

            } catch (e: Exception) {
                onLogUpdate?.invoke("[UPDATE NOTICE]: Shizuku reinstall attempt error: ${e.message}. Using system installer.")
            }
        }

        // 2. Standard Android Package Installer via FileProvider fallback
        try {
            // Backup APK to public Downloads folder so user does not lose it if they manually uninstall
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDownloads.exists() || publicDownloads.mkdirs()) {
                    val publicApk = File(publicDownloads, apkFile.name)
                    apkFile.copyTo(publicApk, overwrite = true)
                }
            } catch (_: Exception) {}

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Result.success(true)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Could not open installer: ${e.message}", Toast.LENGTH_LONG).show()
            }
            Result.failure(e)
        }
    }


    /**
     * Resets the download state back to Idle.
     */
    fun resetDownloadState() {
        downloadState = DownloadState.Idle
    }
}
