// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.wallcarousel.app.R
import com.wallcarousel.app.WallCarouselApplication
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.wallpaper.ApplyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quick Settings tile that applies the next wallpaper for one target. */
abstract class NextWallpaperTileService(
  private val target: ApplyTarget,
) : TileService() {
  override fun onStartListening() {
    super.onStartListening()
    updateTileState()
  }

  override fun onClick() {
    super.onClick()
    val application = application as? WallCarouselApplication ?: return
    application.container.appScope.launch {
      val message = try {
        when (val result = application.container.applier.applyNext(target)) {
          is ApplyResult.Applied -> getString(R.string.tile_applied)
          ApplyResult.NoCarousel, ApplyResult.NoWallpapers, ApplyResult.Skipped ->
            getString(R.string.tile_assign_first)
          ApplyResult.NotSupported -> getString(R.string.tile_not_supported)
          is ApplyResult.Failed -> getString(R.string.apply_failed)
        }
      } catch (error: Exception) {
        getString(R.string.apply_failed)
      }
      withContext(Dispatchers.Main) {
        Toast.makeText(this@NextWallpaperTileService, message, Toast.LENGTH_SHORT).show()
        updateTileState()
      }
    }
  }

  private fun updateTileState() {
    val application = application as? WallCarouselApplication ?: return
    val config = application.container.repository.state.value?.config(target)
    qsTile?.apply {
      state = if (config?.carouselId != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
      updateTile()
    }
  }
}

class NextHomeTileService : NextWallpaperTileService(ApplyTarget.HOME)

class NextLockTileService : NextWallpaperTileService(ApplyTarget.LOCK)
