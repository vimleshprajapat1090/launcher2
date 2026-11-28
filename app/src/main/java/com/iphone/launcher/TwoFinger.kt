package com.iphone.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope

// Do ungliyon se upar swipe karo to onTrigger chalta hai.
// Touch consume nahi hota, to baaki gestures (pager, scroll) waise hi chalte hain.
suspend fun PointerInputScope.detectTwoFingerSwipeUp(onTrigger: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startY = HashMap<PointerId, Float>()
        var fired = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.filter { it.pressed }
            if (down.isEmpty()) break
            if (down.size >= 2) {
                for (c in down) {
                    if (!startY.containsKey(c.id)) startY[c.id] = c.position.y
                }
                if (!fired) {
                    val moved = down.take(2).map { (startY[it.id] ?: it.position.y) - it.position.y }
                    if (moved.all { it > 110f }) {
                        fired = true
                        onTrigger()
                    }
                }
            }
        }
    }
}
