// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

val SelectionSetSaver = Saver<Set<String>, ArrayList<String>>(
  save = { ArrayList(it) },
  restore = { it.toSet() },
)

/**
 * Transparent layer used while a selection is active. Dragging over the list
 * selects every item under the finger; taps are left to the cards below.
 */
@Composable
fun DragSelectOverlay(
  keyAt: (Offset) -> String?,
  onSelect: (String) -> Unit,
  onTap: (String) -> Unit = {},
  onEdgeScroll: (Float) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.pointerInput(Unit) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragged = false
        var pressed = true
        var canceled = false
        val edge = 48.dp.toPx()
        down.consume()
        while (pressed) {
          val event = awaitPointerEvent()
          val change = event.changes.firstOrNull { it.id == down.id }
          if (change == null) {
            canceled = true
            break
          }
          pressed = change.pressed
          if (!dragged &&
            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
          ) {
            dragged = true
            keyAt(down.position)?.let(onSelect)
          }
          if (dragged) {
            keyAt(change.position)?.let(onSelect)
            when {
              change.position.y < edge -> onEdgeScroll(-20f)
              change.position.y > size.height - edge -> onEdgeScroll(20f)
            }
          }
          event.changes.forEach { it.consume() }
        }
        if (!dragged && !canceled) {
          keyAt(down.position)?.let(onTap)
        }
      }
    },
  )
}
