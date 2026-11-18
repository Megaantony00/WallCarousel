// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

val SelectionSetSaver = Saver<Set<String>, ArrayList<String>>(
  save = { ArrayList(it) },
  restore = { it.toSet() },
)

/**
 * Picker-like selection layer for a lazy grid or list.
 *
 * In normal mode it only observes: a tap or a scroll reaches the cards and the
 * lazy container untouched, while a long press starts a selection and keeps
 * tracking the same finger. In selection mode taps toggle an item and dragging
 * selects everything under the finger.
 */
@Composable
fun SelectionGestureLayer(
  selectionMode: Boolean,
  keyAt: (Offset) -> String?,
  onSelect: (String) -> Unit,
  onTap: (String) -> Unit,
  onEdgeScroll: (Float) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val currentMode by rememberUpdatedState(selectionMode)
  val latestKeyAt by rememberUpdatedState(keyAt)
  val latestSelect by rememberUpdatedState(onSelect)
  val latestTap by rememberUpdatedState(onTap)
  val latestScroll by rememberUpdatedState(onEdgeScroll)

  Box(
    modifier = modifier.pointerInput(Unit) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var selecting = currentMode
        var fromLongPress = false

        if (!selecting) {
          val canceled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var stopped = false
            while (!stopped) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id }
              if (change == null || !change.pressed) {
                stopped = true
              } else if (
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
              ) {
                stopped = true
              }
            }
            true
          }
          if (canceled == true) return@awaitEachGesture
          selecting = true
          fromLongPress = true
          latestKeyAt(down.position)?.let(latestSelect)
          down.consume()
        } else {
          down.consume()
        }

        var pressed = true
        var dragged = false
        val edge = 48.dp.toPx()
        while (pressed) {
          val event = awaitPointerEvent()
          val change = event.changes.firstOrNull { it.id == down.id } ?: break
          pressed = change.pressed
          if (!dragged &&
            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
          ) {
            dragged = true
            latestKeyAt(down.position)?.let(latestSelect)
          }
          if (dragged) {
            latestKeyAt(change.position)?.let(latestSelect)
            when {
              change.position.y < edge -> latestScroll(-20f)
              change.position.y > size.height - edge -> latestScroll(20f)
            }
          }
          event.changes.forEach { it.consume() }
        }
        if (selecting && !dragged && !fromLongPress) {
          latestKeyAt(down.position)?.let(latestTap)
        }
      }
    },
    content = content,
  )
}
