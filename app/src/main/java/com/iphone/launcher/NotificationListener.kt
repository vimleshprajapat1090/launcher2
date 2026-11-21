package com.iphone.launcher

import android.app.Notification
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class CallAction(val title: String, val intent: PendingIntent?)

data class CallInfo(val title: String, val text: String, val actions: List<CallAction>)

object IslandState {
    var call by mutableStateOf<CallInfo?>(null)
}

class NotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        update()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        update()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        update()
    }

    override fun onListenerDisconnected() {
        handler.post { IslandState.call = null }
    }

    private fun update() {
        val info: CallInfo? = try {
            activeNotifications
                ?.firstOrNull { it.notification.category == Notification.CATEGORY_CALL }
                ?.let { sbn ->
                    val n = sbn.notification
                    CallInfo(
                        title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Call",
                        text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                        actions = (n.actions ?: emptyArray()).map {
                            CallAction(it.title?.toString() ?: "", it.actionIntent)
                        }
                    )
                }
        } catch (e: Exception) {
            null
        }
        handler.post { IslandState.call = info }
    }
}
