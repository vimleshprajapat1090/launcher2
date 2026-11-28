package com.iphone.launcher

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
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

class TimerInfo(
    val title: String,
    val whenMs: Long,
    val countDown: Boolean,
    val intent: PendingIntent?
)

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
    var timer by mutableStateOf<TimerInfo?>(null)
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

private fun boltIcon(color: Int): ImageBitmap {
    val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.color = color
    c.drawRoundRect(android.graphics.RectF(0f, 0f, 96f, 96f), 24f, 24f, p)
    p.color = 0xFFFFFFFF.toInt()
    val path = android.graphics.Path()
    path.moveTo(54f, 12f)
    path.lineTo(24f, 54f)
    path.lineTo(46f, 54f)
    path.lineTo(40f, 84f)
    path.lineTo(72f, 40f)
    path.lineTo(50f, 40f)
    path.close()
    c.drawPath(path, p)
    return bmp.asImageBitmap()
}

class NotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: IslandOverlay? = null

    // Menu ka "Doosre apps ke upar Island" switch yahin se asli me chalta / band hota hai
    private val launcherPrefs: SharedPreferences by lazy {
        getSharedPreferences("launcher", Context.MODE_PRIVATE)
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "overlayisland") handler.post { ensureOverlay() }
    }

    private fun overlayWanted(): Boolean = launcherPrefs.getBoolean("overlayisland", true)

    private var lastPlugged = false
    private var lastLevel = -1

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) handleBattery(intent, false)
        }
    }

    private fun handleBattery(i: Intent, silent: Boolean) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (!silent) {
            if (plugged && !lastPlugged) {
                showBatteryToast("Charging", pct.toString() + "% battery", 0xFF30D158.toInt())
            } else if (plugged && pct >= 100 && lastLevel in 0..99) {
                showBatteryToast("Fully charged", "Battery 100%", 0xFF30D158.toInt())
            } else if (!plugged && pct <= 20 && lastLevel > 20) {
                showBatteryToast("Battery low", pct.toString() + "% bacha hai", 0xFFFF453A.toInt())
            }
        }
        lastPlugged = plugged
        lastLevel = pct
    }

    private fun showBatteryToast(title: String, text: String, color: Int) {
        val icon: ImageBitmap? = try {
            boltIcon(color)
        } catch (e: Exception) {
            null
        }
        val info = ToastInfo("battery" + System.currentTimeMillis(), packageName, title, text, null, icon)
        handler.post { IslandState.toast = info }
        handler.postDelayed({
            if (IslandState.toast?.key == info.key) IslandState.toast = null
        }, 4000L)
    }

    override fun onCreate() {
        super.onCreate()
        IslandState.service = this
        try {
            launcherPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        } catch (e: Exception) {
        }
        try {
            val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (sticky != null) handleBattery(sticky, true)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            launcherPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (e: Exception) {
        }
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
        }
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

    // Switch OFF ho to overlay window poori tarah hata do (Android ka "display over other apps"
    // wala indicator bhi hat jata hai). ON ho aur permission ho to wapas laga do.
    fun ensureOverlay() {
        try {
            if (!overlayWanted()) {
                overlay?.hide()
                return
            }
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
            IslandState.timer = null
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

        val timerInfo: TimerInfo? = if (info != null) {
            null
        } else {
            try {
                activeNotifications
                    ?.firstOrNull { sbn ->
                        val nn = sbn.notification
                        sbn.packageName != packageName &&
                            sbn.isOngoing &&
                            nn.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) &&
                            nn.category != Notification.CATEGORY_CALL &&
                            nn.category != Notification.CATEGORY_TRANSPORT &&
                            !isCallNotification(sbn)
                    }
                    ?.let { sbn ->
                        val nn = sbn.notification
                        TimerInfo(
                            title = nn.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Timer",
                            whenMs = nn.`when`,
                            countDown = nn.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
                            intent = nn.contentIntent
                        )
                    }
            } catch (e: Exception) {
                null
            }
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
            IslandState.timer = timerInfo
        }
    }
}
