package com.example.data.remote

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val remoteLastModified: Long,
    val remoteLastModifiedFormatted: String,
    val localInstallTime: Long,
    val localInstallTimeFormatted: String,
    val fileSizeFormatted: String,
    val apkUrl: String = AppUpdateManager.APK_URL
)

class AppUpdateManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        const val APK_URL = "https://zonq.eu/api.php?a=apk"
    }

    /**
     * Retrieves the installed package's last update / install timestamp.
     */
    fun getLocalInstallTime(): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val pkgTime = when {
                packageInfo.lastUpdateTime > 0L -> packageInfo.lastUpdateTime
                packageInfo.firstInstallTime > 0L -> packageInfo.firstInstallTime
                else -> 0L
            }
            if (pkgTime > 0L) return pkgTime

            val apkPath = context.applicationInfo?.sourceDir
            if (!apkPath.isNullOrBlank()) {
                val apkFile = java.io.File(apkPath)
                if (apkFile.exists() && apkFile.lastModified() > 0L) {
                    return apkFile.lastModified()
                }
            }

            System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Checks the server for a newer APK version by inspecting headers of https://zonq.eu/api.php?a=apk.
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val localTime = getLocalInstallTime()
            val localFormatted = formatDate(localTime)

            val request = Request.Builder()
                .url(APK_URL)
                .head()
                .header("User-Agent", "zonq/1.0 (Android; Updater)")
                .header("X-Zonq", "1")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code !in 200..399) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val lastModifiedHeader = response.header("Last-Modified")
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L

                val remoteTime = if (!lastModifiedHeader.isNullOrBlank()) {
                    parseHttpDate(lastModifiedHeader) ?: 0L
                } else {
                    0L
                }

                val remoteFormatted = if (remoteTime > 0L) formatDate(remoteTime) else "Ismeretlen"
                val sizeFormatted = formatFileSize(contentLength)

                // Update is available if the remote file modification time is newer than the local installation time
                // (Using a 60 second margin for clock skew / build time alignment)
                val isNewer = remoteTime > 0L && (remoteTime > localTime + 60_000L)

                Result.success(
                    AppUpdateInfo(
                        isUpdateAvailable = isNewer,
                        remoteLastModified = remoteTime,
                        remoteLastModifiedFormatted = remoteFormatted,
                        localInstallTime = localTime,
                        localInstallTimeFormatted = localFormatted,
                        fileSizeFormatted = sizeFormatted,
                        apkUrl = APK_URL
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Initiates APK download via DownloadManager and opens ACTION_VIEW fallback.
     */
    fun startDownload(url: String = APK_URL): Boolean {
        return try {
            val uri = Uri.parse(url)

            // 1. Enqueue download in Android DownloadManager
            try {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (downloadManager != null) {
                    val request = DownloadManager.Request(uri)
                        .setTitle("zonq.apk")
                        .setDescription("Zonq rádió frissítés letöltése")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "zonq.apk")
                        .setMimeType("application/vnd.android.package-archive")
                    downloadManager.enqueue(request)
                }
            } catch (dmEx: Exception) {
                Log.w(TAG, "DownloadManager enqueue failed: ${dmEx.message}")
            }

            // 2. Also trigger system browser / downloader activity intent
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download intent: ${e.message}", e)
            false
        }
    }

    private fun parseHttpDate(dateStr: String): Long? {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd-MMM-yy HH:mm:ss z",
            "EEE MMM dd HH:mm:ss yyyy"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("GMT")
                val parsed = sdf.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}
