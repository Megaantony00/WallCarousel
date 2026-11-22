// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface UpdateState {
  data object Idle : UpdateState
  data object Checking : UpdateState
  data object UpToDate : UpdateState
  data class Available(val release: UpdateRelease) : UpdateState
  data class Downloading(val progress: Int) : UpdateState
  data object ReadyToInstall : UpdateState
  data object NeedsInstallPermission : UpdateState
  data class Failed(val reason: Reason) : UpdateState

  enum class Reason { CHECK, DOWNLOAD, VERIFY, INSTALL }
}

data class UpdateRelease(
  val versionName: String,
  val tag: String,
  val notes: String,
  val downloadUrl: String,
  val assetName: String,
)

/**
 * Checks the public GitHub Releases API, downloads the signed APK into the
 * app's private cache, verifies package name, version and signing key, and
 * opens the system package installer. Android always asks the user for the
 * final install confirmation; there is no silent update.
 */
class UpdateManager(private val context: Context) {
  private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
  val state: StateFlow<UpdateState> = _state.asStateFlow()

  private var availableRelease: UpdateRelease? = null
  private var downloadedFile: File? = null

  suspend fun checkForUpdates(): UpdateState = withContext(Dispatchers.IO) {
    _state.value = UpdateState.Checking
    try {
      val json = JSONObject(fetchText(API_URL))
      val tag = json.optString("tag_name").removePrefix("v").trim()
      if (tag.isBlank()) {
        _state.value = UpdateState.Failed(UpdateState.Reason.CHECK)
        return@withContext _state.value
      }
      val installed = installedVersionName()
      if (!isNewerVersion(tag, installed)) {
        _state.value = UpdateState.UpToDate
        return@withContext _state.value
      }

      val assets = json.optJSONArray("assets")
      val asset = (0 until (assets?.length() ?: 0))
        .mapNotNull { index -> assets?.optJSONObject(index) }
        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
      val assetName = asset?.optString("name").orEmpty()
      val downloadUrl = asset?.optString("browser_download_url").orEmpty()
      if (assetName.isBlank() || downloadUrl.isBlank()) {
        _state.value = UpdateState.Failed(UpdateState.Reason.CHECK)
        return@withContext _state.value
      }

      downloadedFile?.delete()
      downloadedFile = null
      availableRelease = UpdateRelease(
        versionName = tag,
        tag = json.optString("tag_name"),
        notes = json.optString("body"),
        downloadUrl = downloadUrl,
        assetName = assetName,
      )
      _state.value = UpdateState.Available(availableRelease!!)
      _state.value
    } catch (error: Exception) {
      _state.value = UpdateState.Failed(UpdateState.Reason.CHECK)
      _state.value
    }
  }

  suspend fun downloadAndInstall(): UpdateState = withContext(Dispatchers.IO) {
    val release = availableRelease
      ?: return@withContext UpdateState.Failed(UpdateState.Reason.DOWNLOAD)
    _state.value = UpdateState.Downloading(0)
    val directory = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
    directory.listFiles()?.forEach { it.delete() }
    val target = File(directory, "WallCarousel-${release.versionName}.apk")
    val part = File(directory, "${target.name}.part")

    val downloaded = try {
      download(release, part)
    } catch (error: Exception) {
      false
    }
    if (!downloaded || !part.renameTo(target)) {
      part.delete()
      _state.value = UpdateState.Failed(UpdateState.Reason.DOWNLOAD)
      return@withContext _state.value
    }
    if (!verify(target)) {
      target.delete()
      _state.value = UpdateState.Failed(UpdateState.Reason.VERIFY)
      return@withContext _state.value
    }

    downloadedFile = target
    _state.value = UpdateState.ReadyToInstall
    withContext(Dispatchers.Main) { install() }
    _state.value
  }

  fun install() {
    val file = downloadedFile
    if (file == null || !file.exists()) {
      _state.value = UpdateState.Failed(UpdateState.Reason.INSTALL)
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      !context.packageManager.canRequestPackageInstalls()
    ) {
      _state.value = UpdateState.NeedsInstallPermission
      return
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(flags)
    }
    try {
      context.startActivity(viewIntent)
    } catch (error: ActivityNotFoundException) {
      val fallback = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(flags)
      }
      try {
        context.startActivity(fallback)
      } catch (fallbackError: ActivityNotFoundException) {
        _state.value = UpdateState.Failed(UpdateState.Reason.INSTALL)
      }
    }
  }

  fun openInstallPermissionSettings() {
    val intent = Intent(
      Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
      Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun cleanup() {
    File(context.cacheDir, UPDATE_DIRECTORY).listFiles()?.forEach { it.delete() }
    downloadedFile = null
  }

  private fun download(release: UpdateRelease, target: File): Boolean {
    val connection = openConnection(release.downloadUrl, acceptGitHubJson = false)
    return try {
      if (connection.responseCode !in 200..299) return false
      val total = connection.contentLengthLong
      if (total > MAX_APK_BYTES) return false
      var downloaded = 0L
      var lastProgress = -1
      connection.inputStream.use { input ->
        target.outputStream().use { output ->
          val buffer = ByteArray(16 * 1024)
          while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            if (downloaded > MAX_APK_BYTES) return false
            val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
            if (progress != lastProgress) {
              lastProgress = progress
              _state.value = UpdateState.Downloading(progress.coerceIn(0, 100))
            }
          }
        }
      }
      true
    } finally {
      connection.disconnect()
    }
  }

  private fun verify(file: File): Boolean {
    val manager = context.packageManager
    val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val archive = manager.getPackageArchiveInfo(
      file.absolutePath,
      PackageManager.GET_SIGNING_CERTIFICATES,
    ) ?: return false
    if (archive.packageName != context.packageName) return false
    if (archive.longVersionCode < installed.longVersionCode) return false
    val installedSigners = installed.signingInfo?.apkContentsSigners ?: return false
    val archiveSigners = archive.signingInfo?.apkContentsSigners ?: return false
    if (installedSigners.isEmpty() || archiveSigners.isEmpty()) return false
    return installedSigners[0].toByteArray().contentEquals(archiveSigners[0].toByteArray())
  }

  private fun fetchText(url: String): String {
    val connection = openConnection(url, acceptGitHubJson = true)
    return try {
      if (connection.responseCode !in 200..299) {
        throw IOException("HTTP ${connection.responseCode}")
      }
      connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }

  private fun openConnection(url: String, acceptGitHubJson: Boolean): HttpURLConnection {
    val parsed = URL(url)
    if (parsed.protocol != "https") {
      throw IOException("Unsupported URL scheme")
    }
    return (parsed.openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 30_000
      instanceFollowRedirects = true
      requestMethod = "GET"
      setRequestProperty("User-Agent", "WallCarousel/${installedVersionName()}")
      if (acceptGitHubJson) setRequestProperty("Accept", "application/vnd.github+json")
    }
  }

  private fun installedVersionName(): String {
    @Suppress("DEPRECATION")
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
  }

  private companion object {
    const val API_URL = "https://api.github.com/repos/Megaantony00/WallCarousel/releases/latest"
    const val UPDATE_DIRECTORY = "updates"
    const val MAX_APK_BYTES = 80L * 1024 * 1024
  }
}

internal fun isNewerVersion(remote: String, installed: String): Boolean {
  val remoteParts = versionParts(remote)
  val installedParts = versionParts(installed)
  for (index in 0 until maxOf(remoteParts.size, installedParts.size)) {
    val remotePart = remoteParts.getOrElse(index) { 0 }
    val installedPart = installedParts.getOrElse(index) { 0 }
    if (remotePart != installedPart) return remotePart > installedPart
  }
  return false
}

internal fun versionParts(value: String): List<Int> = value
  .trim()
  .removePrefix("v")
  .substringBefore('-')
  .substringBefore('+')
  .split('.')
  .map { it.toIntOrNull() ?: 0 }
