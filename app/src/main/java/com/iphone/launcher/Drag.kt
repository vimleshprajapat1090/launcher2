package com.iphone.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

class WidgetDragState {
    var key by mutableStateOf<String?>(null)
    var pos by mutableStateOf(Offset.Zero)
    val bounds = HashMap<String, Rect>()
}

class DragController {
    var app by mutableStateOf<AppItem?>(null)
    var pos by mutableStateOf(Offset.Zero)
    var fromDock = false
    val bounds = HashMap<String, Rect>()
    val pageGrids = HashMap<Int, FloatArray>()
}

suspend fun PointerInputScope.dragGestures(
    findKey: (Offset) -> String?,
    onStart: (String, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val target = findKey(down.position)
        if (target == null) return@awaitEachGesture
        var started = false
        var total = Offset.Zero
        val slop = viewConfiguration.touchSlop
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (started) onEnd()
                break
            }
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                if (started) {
                    change.consume()
                    onEnd()
                }
                break
            }
            total += change.positionChange()
            if (!started && total.getDistance() > slop) {
                started = true
                onStart(target, change.position)
            }
            if (started) {
                change.consume()
                onMove(change.position)
            }
        }
    }
}

@Composable
fun DragOverlay(ctrl: DragController, size: Dp) {
    val app = ctrl.app
    if (app != null) {
        val px = with(LocalDensity.current) { size.toPx() }
        Box(
            Modifier
                .offset { IntOffset((ctrl.pos.x - px / 2f).roundToInt(), (ctrl.pos.y - px / 2f).roundToInt()) }
                .size(size)
                .graphicsLayer {
                    scaleX = 1.15f
                    scaleY = 1.15f
                    alpha = 0.95f
                }
        ) {
            IconImage(app, Modifier.size(size))
        }
    }
}
