package com.elfred434.touch

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val versionCode: Int, val versionName: String, val apkUrl: String)

object UpdateManager {
    fun check(): AppUpdate? {
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
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
                    apkUrl = item.optString("browser_download_url")
                    break
                }
            }
            val codeFromBody = Regex("(?i)versionCode\\s*:\\s*(\\d+)")
                .find(json.optString("body"))?.groupValues?.getOrNull(1)?.toIntOrNull()
            val codeFromTag = tag.split('.').lastOrNull()?.takeWhile(Char::isDigit)?.toIntOrNull()
            val versionCode = codeFromBody ?: codeFromTag ?: return null
            if (apkUrl.isNullOrBlank() || versionCode <= BuildConfig.VERSION_CODE) return null
            AppUpdate(versionCode, tag, apkUrl)
        } finally {
            connection.disconnect()
        }
    }

    /** Open the public APK link in the user's browser; Android's normal installer flow remains in control. */
    fun openDownloadPage(context: Context, update: AppUpdate) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
