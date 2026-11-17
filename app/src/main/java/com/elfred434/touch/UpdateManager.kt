package com.elfred434.touch

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.getSystemService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

 data class AppUpdate(val versionCode: Int, val versionName: String, val apkUrl: String)

object UpdateManager {
    fun check(): AppUpdate? {
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Touch-Android-Updater")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val item = assets.getJSONObject(i)
                if (item.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = item.optString("browser_download_url"); break
                }
            }
            val releaseText = json.optString("body")
            val fromBody = Regex("(?i)versionCode\\s*:\\s*(\\d+)").find(releaseText)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val parsed = tag.split('.').lastOrNull()?.takeWhile(Char::isDigit)?.toIntOrNull()
            val versionCode = fromBody ?: parsed ?: return null
            if (apkUrl.isNullOrBlank() || versionCode <= BuildConfig.VERSION_CODE) return null
            AppUpdate(versionCode, tag, apkUrl)
        } finally { connection.disconnect() }
    }

    fun startInstall(context: Context, update: AppUpdate) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Mise à jour de Touch")
            .setDescription("Téléchargement de la version ${update.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "touch-${update.versionName}.apk")
        val manager = context.getSystemService<DownloadManager>() ?: return
        val id = manager.enqueue(request)
        context.getSharedPreferences(TouchPrefs.PREFS, Context.MODE_PRIVATE).edit().putLong("update_download_id", id).apply()
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val saved = context.getSharedPreferences(TouchPrefs.PREFS, Context.MODE_PRIVATE).getLong("update_download_id", -2L)
        if (id != saved) return
        val manager = context.getSystemService<DownloadManager>() ?: return
        val apkUri = manager.getUriForDownloadedFile(id) ?: return
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(install)
    }
}
