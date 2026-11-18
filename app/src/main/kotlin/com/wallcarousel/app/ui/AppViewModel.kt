// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wallcarousel.app.WallCarouselApplication
import com.wallcarousel.app.data.ImportedImage
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.data.model.Carousel
import com.wallcarousel.app.data.model.Orientation
import com.wallcarousel.app.data.model.Schedule
import com.wallcarousel.app.data.model.ThemeMode
import com.wallcarousel.app.data.model.Variant
import com.wallcarousel.app.data.model.Wallpaper
import com.wallcarousel.app.wallpaper.ApplyResult
import com.wallcarousel.app.wallpaper.ThumbnailLoader
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiMessage {
  data class Imported(val count: Int) : UiMessage
  data object ImportFailed : UiMessage
  data class Applied(val target: ApplyTarget) : UiMessage
  data object ApplyFailed : UiMessage
  data object ApplyUnavailable : UiMessage
  data object NotSupported : UiMessage
  data object Exported : UiMessage
  data object ExportFailed : UiMessage
  data object DataDeleted : UiMessage
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
  private val container = (application as WallCarouselApplication).container
  private val repository = container.repository

  val state = repository.state

  private val _busy = MutableStateFlow(false)
  val busy = _busy.asStateFlow()

  private val _message = MutableStateFlow<UiMessage?>(null)
  val message = _message.asStateFlow()

  fun imageFile(fileName: String): File = container.imageStore.file(fileName)

  fun clearMessage() {
    _message.value = null
  }

  fun importImages(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      _busy.value = true
      try {
        val imported = uris.mapNotNull { container.imageStore.import(it) }
        if (imported.isEmpty()) {
          _message.value = UiMessage.ImportFailed
          return@launch
        }
        val now = System.currentTimeMillis()
        repository.update { current ->
          val usedSourceNames = current.wallpapers
            .map { it.sourceName.ifBlank { it.name } }
            .toMutableList()
          val additions = imported.mapIndexed { index, image ->
            val sourceName = uniqueName(image.displayName, usedSourceNames)
            usedSourceNames += sourceName
            image.toWallpaper(now + index, sourceName)
          }
          current.copy(wallpapers = current.wallpapers + additions)
        }
        _message.value = UiMessage.Imported(imported.size)
      } catch (error: Exception) {
        _message.value = UiMessage.ImportFailed
      } finally {
        _busy.value = false
      }
    }
  }

  fun addVariant(wallpaperId: String, orientation: Orientation, uri: Uri) {
    viewModelScope.launch {
      val wallpaper = repository.ensureLoaded().wallpaper(wallpaperId) ?: return@launch
      _busy.value = true
      try {
        val imported = container.imageStore.import(uri)
        if (imported == null) {
          _message.value = UiMessage.ImportFailed
          return@launch
        }
        val previous = wallpaper.variant(orientation)
        val otherOrientation = if (orientation == Orientation.PORTRAIT) {
          Orientation.LANDSCAPE
        } else {
          Orientation.PORTRAIT
        }
        val other = wallpaper.variant(otherOrientation)
        val variant = imported.toVariant()
        repository.update { current ->
          current.copy(
            wallpapers = current.wallpapers.map { item ->
              if (item.id != wallpaperId) {
                item
              } else when (orientation) {
                Orientation.PORTRAIT -> item.copy(portrait = variant)
                Orientation.LANDSCAPE -> item.copy(landscape = variant)
              }
            },
          )
        }
        if (previous != null && previous.fileName != other?.fileName) {
          container.imageStore.delete(previous.fileName)
        }
        ThumbnailLoader.clear()
        _message.value = UiMessage.Imported(1)
      } catch (error: Exception) {
        _message.value = UiMessage.ImportFailed
      } finally {
        _busy.value = false
      }
    }
  }

  fun updateWallpaper(
    wallpaperId: String,
    name: String,
    orientation: Orientation,
    variant: Variant?,
  ) {
    viewModelScope.launch {
      repository.update { current ->
        current.copy(
          wallpapers = current.wallpapers.map { wallpaper ->
            if (wallpaper.id != wallpaperId) {
              wallpaper
            } else {
              val renamed = wallpaper.copy(name = name.trim())
              if (variant == null) {
                renamed
              } else when (orientation) {
                Orientation.PORTRAIT -> renamed.copy(portrait = variant)
                Orientation.LANDSCAPE -> renamed.copy(landscape = variant)
              }
            }
          },
        )
      }
    }
  }

  fun deleteWallpaper(wallpaperId: String) {
    deleteWallpapers(setOf(wallpaperId))
  }

  fun deleteWallpapers(wallpaperIds: Set<String>) {
    if (wallpaperIds.isEmpty()) return
    viewModelScope.launch {
      val current = repository.ensureLoaded()
      val files = current.wallpapers
        .filter { it.id in wallpaperIds }
        .flatMap { listOfNotNull(it.portrait, it.landscape) }
        .map { it.fileName }
        .distinct()

      val updated = repository.update { state ->
        state.copy(
          wallpapers = state.wallpapers.filterNot { it.id in wallpaperIds },
          carousels = state.carousels.map { carousel ->
            carousel.copy(wallpaperIds = carousel.wallpaperIds.filterNot { it in wallpaperIds })
          },
        )
      }
      files.forEach(container.imageStore::delete)
      ThumbnailLoader.clear()
      container.scheduler.sync(updated)
    }
  }

  fun createCarousel(name: String) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      repository.update { current ->
        current.copy(
          carousels = current.carousels + Carousel(
            id = UUID.randomUUID().toString(),
            name = uniqueName(clean, current.carousels.map { it.name }),
            createdAt = System.currentTimeMillis(),
          ),
        )
      }
    }
  }

  fun deleteCarousel(carouselId: String) {
    viewModelScope.launch {
      val updated = repository.update { state ->
        state.copy(
          carousels = state.carousels.filterNot { it.id == carouselId },
          home = state.home.let { config ->
            if (config.carouselId == carouselId) config.copy(carouselId = null, schedule = Schedule()) else config
          },
          lock = state.lock.let { config ->
            if (config.carouselId == carouselId) config.copy(carouselId = null, schedule = Schedule()) else config
          },
        )
      }
      container.scheduler.sync(updated)
    }
  }

  fun addWallpapersToCarousel(carouselId: String, wallpaperIds: List<String>) {
    if (wallpaperIds.isEmpty()) return
    viewModelScope.launch {
      repository.update { state ->
        state.copy(
          carousels = state.carousels.map { carousel ->
            if (carousel.id != carouselId) {
              carousel
            } else {
              carousel.copy(wallpaperIds = (carousel.wallpaperIds + wallpaperIds).distinct())
            }
          },
        )
      }
    }
  }

  fun removeWallpaperFromCarousel(carouselId: String, wallpaperId: String) {
    removeWallpapersFromCarousel(carouselId, setOf(wallpaperId))
  }

  fun removeWallpapersFromCarousel(carouselId: String, wallpaperIds: Set<String>) {
    if (wallpaperIds.isEmpty()) return
    viewModelScope.launch {
      repository.update { state ->
        state.copy(
          carousels = state.carousels.map { carousel ->
            if (carousel.id != carouselId) {
              carousel
            } else {
              carousel.copy(wallpaperIds = carousel.wallpaperIds.filterNot { it in wallpaperIds })
            }
          },
        )
      }
    }
  }

  fun moveWallpaper(carouselId: String, wallpaperId: String, up: Boolean) {
    viewModelScope.launch {
      repository.update { state ->
        state.copy(
          carousels = state.carousels.map { carousel ->
            if (carousel.id != carouselId) return@map carousel
            val ids = carousel.wallpaperIds.toMutableList()
            val index = ids.indexOf(wallpaperId)
            if (index < 0) return@map carousel
            val target = if (up) index - 1 else index + 1
            if (target !in ids.indices) return@map carousel
            val item = ids.removeAt(index)
            ids.add(target, item)
            carousel.copy(wallpaperIds = ids)
          },
        )
      }
    }
  }

  fun assignCarousel(target: ApplyTarget, carouselId: String?) {
    viewModelScope.launch {
      val updated = repository.update { state ->
        val config = state.config(target)
        val next = if (carouselId == null) {
          config.copy(carouselId = null, schedule = Schedule(), cursor = 0)
        } else {
          config.copy(carouselId = carouselId, cursor = 0)
        }
        state.withConfig(target, next)
      }
      container.scheduler.sync(updated)
    }
  }

  fun setSchedule(target: ApplyTarget, schedule: Schedule) {
    viewModelScope.launch {
      val updated = repository.update { state ->
        state.withConfig(target, state.config(target).copy(schedule = schedule))
      }
      container.scheduler.sync(updated)
    }
  }

  fun applyNow(target: ApplyTarget) {
    viewModelScope.launch {
      _busy.value = true
      try {
        _message.value = when (val result = container.applier.applyNext(target)) {
          is ApplyResult.Applied -> UiMessage.Applied(target)
          ApplyResult.NoCarousel, ApplyResult.NoWallpapers, ApplyResult.Skipped ->
            UiMessage.ApplyUnavailable
          ApplyResult.NotSupported -> UiMessage.NotSupported
          is ApplyResult.Failed -> UiMessage.ApplyFailed
        }
      } catch (error: Exception) {
        _message.value = UiMessage.ApplyFailed
      } finally {
        _busy.value = false
      }
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { repository.update { it.copy(themeMode = mode) } }
  }

  fun setDynamicColor(enabled: Boolean) {
    viewModelScope.launch { repository.update { it.copy(dynamicColor = enabled) } }
  }

  fun deleteAllData() {
    viewModelScope.launch {
      _busy.value = true
      try {
        container.scheduler.cancelAll()
        repository.reset()
        container.imageStore.deleteAll()
        ThumbnailLoader.clear()
        _message.value = UiMessage.DataDeleted
      } finally {
        _busy.value = false
      }
    }
  }

  fun exportData(uri: Uri) {
    viewModelScope.launch {
      _busy.value = true
      try {
        val success = container.backupManager.export(uri)
        _message.value = if (success) UiMessage.Exported else UiMessage.ExportFailed
      } finally {
        _busy.value = false
      }
    }
  }

  suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
    container.imageStore.totalBytes()
  }

  private fun ImportedImage.toVariant(): Variant = Variant(
    fileName = fileName,
    width = width,
    height = height,
  )

  private fun ImportedImage.toWallpaper(importedAt: Long, sourceName: String): Wallpaper {
    val variant = toVariant()
    return Wallpaper(
      id = UUID.randomUUID().toString(),
      name = "",
      portrait = variant,
      landscape = variant,
      importedAt = importedAt,
      sourceName = sourceName,
    )
  }

  private fun uniqueName(base: String, existing: List<String>): String {
    val clean = base.trim().ifBlank { "Wallpaper" }
    if (clean !in existing) return clean
    var index = 2
    while ("$clean ($index)" in existing) index++
    return "$clean ($index)"
  }
}
