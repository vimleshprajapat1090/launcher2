package com.iphone.launcher

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class CallAction(val title: String, val intent: PendingIntent?)

data class CallInfo(val title: String, val text: String, val actions: List<CallAction>)

class ToastInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val intent: PendingIntent?,
    val icon: ImageBitmap?
)

object IslandState {
    var call by mutableStateOf<CallInfo?>(null)
    var badges by mutableStateOf<Map<String, Int>>(emptyMap())
    var toast by mutableStateOf<ToastInfo?>(null)
    var launcherVisible by mutableStateOf(true)
    var service: NotificationListener? = null
    var launcherView: java.lang.ref.WeakReference<android.view.View>? = null

    // Launcher ki window sach me screen pe dikh rahi hai ya nahi (floating / split window me bhi sahi)
    fun launcherReallyVisible(): Boolean {
        val v = launcherView?.get() ?: return false
        return v.isAttachedToWindow &&
            v.windowVisibility == android.view.View.VISIBLE &&
            v.visibility == android.view.View.VISIBLE
    }

    private var mediaInstance: MediaWatcher? = null

    fun media(context: Context): MediaWatcher {
        val m = mediaInstance
        if (m != null) return m
        val n = MediaWatcher(context.applicationContext)
        mediaInstance = n
        return n
    }
}

class NotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: IslandOverlay? = null

    override fun onCreate() {
        super.onCreate()
        IslandState.service = this
    }

    override fun onDestroy() {
        try {
            overlay?.hide()
        } catch (e: Exception) {
        }
        overlay = null
        IslandState.service = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        try {
            IslandState.media(this).start()
        } catch (e: Exception) {
        }
        ensureOverlay()
        update()
    }

    fun ensureOverlay() {
        try {
            if (Settings.canDrawOverlays(this)) {
                if (overlay == null) overlay = IslandOverlay(this)
                overlay?.show()
            }
        } catch (e: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        update()
        maybeToast(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        update()
    }

    override fun onListenerDisconnected() {
        handler.post {
            IslandState.call = null
            IslandState.badges = emptyMap()
            IslandState.toast = null
        }
    }

    private fun maybeToast(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val n = sbn.notification
            if (sbn.packageName == packageName) return
            if (sbn.isOngoing) return
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            val cat = n.category
            if (cat == Notification.CATEGORY_CALL ||
                cat == Notification.CATEGORY_TRANSPORT ||
                cat == Notification.CATEGORY_PROGRESS ||
                cat == Notification.CATEGORY_SERVICE
            ) return
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (n.extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString() ?: ""
            if (title.isBlank() && text.isBlank()) return
            try {
                val r = Ranking()
                if (currentRanking.getRanking(sbn.key, r) && r.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                    return
                }
            } catch (e: Exception) {
            }
            val icon: ImageBitmap? = try {
                packageManager.getApplicationIcon(sbn.packageName).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
            val info = ToastInfo(sbn.key + sbn.postTime, sbn.packageName, title, text, n.contentIntent, icon)
            handler.post { IslandState.toast = info }
            handler.postDelayed({
                if (IslandState.toast?.key == info.key) IslandState.toast = null
            }, 6000L)
        } catch (e: Exception) {
        }
    }

    private fun isCallNotification(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val byType = n.category == Notification.CATEGORY_CALL ||
            (n.extras.getString(Notification.EXTRA_TEMPLATE) ?: "").endsWith("CallStyle")
        val p = sbn.packageName
        val byApp = p.contains("incallui") || p == "com.android.server.telecom"
        val live = sbn.isOngoing || n.fullScreenIntent != null
        return (byType || byApp) && live
    }

    private fun update() {
        val info: CallInfo? = try {
            activeNotifications
                ?.firstOrNull { isCallNotification(it) }
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

        val counts = HashMap<String, Int>()
        try {
            activeNotifications?.forEach { sbn ->
                val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                if (!sbn.isOngoing && !isSummary) {
                    counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
        }

        handler.post {
            IslandState.call = info
            IslandState.badges = counts
        }
    }
}
