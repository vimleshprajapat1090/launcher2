package com.iphone.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

class IslandOverlay(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedController = SavedStateRegistryController.create(this)
    private var view: ComposeView? = null
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override val lifecycle: Lifecycle
        get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedController.savedStateRegistry

    init {
        savedController.performAttach()
        savedController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show() {
        if (view != null) return
        val v = ComposeView(context)
        v.setViewTreeLifecycleOwner(this)
        v.setViewTreeSavedStateRegistryOwner(this)
        v.setContent { OverlayIsland() }
        if (!registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (6 * context.resources.displayMetrics.density).toInt()
        try {
            wm.addView(v, lp)
            view = v
        } catch (e: Exception) {
            view = null
        }
    }

    fun hide() {
        val v = view
        if (v != null) {
            try {
                wm.removeView(v)
            } catch (e: Exception) {
            }
        }
        view = null
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }
}

@Composable
fun OverlayIsland() {
    val context = LocalContext.current
    val media = remember { IslandState.media(context) }
    val prefs = remember { context.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var launcherUp by remember { mutableStateOf(IslandState.launcherReallyVisible()) }
    var overlayOn by remember { mutableStateOf(prefs.getBoolean("overlayisland", true)) }
    LaunchedEffect(Unit) {
        while (true) {
            launcherUp = IslandState.launcherReallyVisible()
            overlayOn = prefs.getBoolean("overlayisland", true)
            delay(400)
        }
    }
    if (overlayOn && !launcherUp) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = AppFont)
        ) {
            DynamicIsland(
                media = media,
                call = IslandState.call,
                hasAccess = true,
                onNeedAccess = {},
                toast = IslandState.toast,
                showIdle = false,
                timer = IslandState.timer
            )
        }
    }
}
