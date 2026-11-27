@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.iphone.launcher

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(
    val label: String,
    val pkg: String,
    val icon: ImageBitmap,
    val category: String,
    val opaque: Boolean,
    val installed: Long,
    val tint: Int
)

sealed interface HomeItem {
    data class AppEntry(val app: AppItem) : HomeItem
    data class FolderEntry(val name: String, val apps: List<AppItem>) : HomeItem
}

fun itemKey(item: HomeItem): String = when (item) {
    is HomeItem.AppEntry -> item.app.pkg
    is HomeItem.FolderEntry -> "f:" + item.name
}

class FastLongPress(private val base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long
        get() = 280L
}

val LIBRARY_ORDER = listOf(
    "Social", "Entertainment", "Productivity", "Creativity", "Games", "Travel", "Utilities"
)

var AppFontStatus = "none"

class MainActivity : ComponentActivity() {
    private val media by lazy { IslandState.media(applicationContext) }
    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppFont = loadAppFont()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        IslandState.launcherView = java.lang.ref.WeakReference(window.decorView)
        setContent {
            val vc = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = AppFont),
                LocalViewConfiguration provides remember(vc) { FastLongPress(vc) }
            ) {
                LauncherScreen(
                    media = media,
                    hasAccess = hasAccess,
                    call = IslandState.call,
                    onNeedAccess = { openAccessSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        IslandState.launcherVisible = true
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (hasAccess) {
            media.start()
            IslandState.service?.ensureOverlay()
        }
    }

    override fun onStart() {
        super.onStart()
        IslandState.launcherVisible = true
    }

    // Floating window / split screen me launcher onPause hota hai par dikhta rehta hai,
    // isliye "dikh raha hai" ka hisaab onStop se rakhte hain (warna do Island dikhte the)
    override fun onStop() {
        IslandState.launcherVisible = false
        super.onStop()
    }

    private fun loadAppFont(): FontFamily {
        // 1) res/font/inter.ttf
        val id = resources.getIdentifier("inter", "font", packageName)
        if (id != 0) {
            try {
                val f = FontFamily(Font(id))
                AppFontStatus = "res"
                return f
            } catch (e: Exception) {
            }
        }
        // 2) assets/fonts/inter.ttf (release build me res shrink ho jaye to ye chalta hai)
        try {
            val f = FontFamily(Font(path = "fonts/inter.ttf", assetManager = assets))
            AppFontStatus = "assets"
            return f
        } catch (e: Exception) {
        }
        AppFontStatus = "none"
        return FontFamily.Default
    }

    private fun openAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Settings me Notification access kholo", Toast.LENGTH_LONG).show()
        }
    }
}

fun categoryOf(pm: PackageManager, pkg: String): String {
    val p = pkg.lowercase()
    fun has(vararg keys: String): Boolean = keys.any { p.contains(it) }
    when {
        has("whatsapp", "instagram", "facebook", "telegram", "snapchat", "twitter", "messenger", "linkedin", "discord", "reddit", "threads", "sharechat", "koo", "moj") -> return "Social"
        has("youtube", "netflix", "spotify", "hotstar", "music", "video", "vlc", "primevideo", "jiocinema", "sonyliv", "zee5", "gaana", "wynk", "saavn") -> return "Entertainment"
        has("camera", "gallery", "photos", "editor", "canva", "snapseed") -> return "Creativity"
        has("maps", "uber", "ola", "irctc", "travel", "booking", "makemytrip", "redbus", "goibibo", "rapido", "zomato", "swiggy") -> return "Travel"
        has("paytm", "phonepe", "bank", "finance", "docs", "sheets", "drive", "office", "gmail", "calendar", "notes", "keep", "upi", "bhim", "gpay", "sbi", "hdfc", "icici", "upstox", "zerodha", "groww") -> return "Productivity"
    }
    return try {
        when (pm.getApplicationInfo(pkg, 0).category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
            ApplicationInfo.CATEGORY_IMAGE -> "Creativity"
            ApplicationInfo.CATEGORY_MAPS -> "Travel"
            ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS -> "Productivity"
            else -> "Utilities"
        }
    } catch (e: Exception) {
        "Utilities"
    }
}

fun renderIcon(d: Drawable): Pair<Bitmap, Boolean> {
    if (d is AdaptiveIconDrawable) {
        val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = d.background
        if (bg != null) {
            bg.setBounds(-40, -40, 200, 200)
            bg.draw(canvas)
        }
        val fg = d.foreground
        if (fg != null) {
            fg.setBounds(-40, -40, 200, 200)
            fg.draw(canvas)
        }
        return Pair(bmp, true)
    }
    return Pair(d.toBitmap(160, 160), false)
}

fun dominantTint(bmp: Bitmap): Int {
    val small = Bitmap.createScaledBitmap(bmp, 12, 12, true)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    for (x in 0 until 12) {
        for (y in 0 until 12) {
            val p = small.getPixel(x, y)
            val a = (p ushr 24) and 0xFF
            if (a > 60) {
                r += ((p shr 16) and 0xFF).toLong()
                g += ((p shr 8) and 0xFF).toLong()
                b += (p and 0xFF).toLong()
                n += 1L
            }
        }
    }
    if (n == 0L) return 0xFF3A3A3C.toInt()
    val rr = (r / n).toInt()
    val gg = (g / n).toInt()
    val bb = (b / n).toInt()
    val lum = (rr * 299 + gg * 587 + bb * 114) / 1000
    // bahut halka (safed) icon ho to gehra background do, warna icon dikhega nahi
    if (lum > 190) return 0xFF48484A.toInt()
    return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
}

fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            val rendered = renderIcon(it.loadIcon(pm))
            AppItem(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                icon = rendered.first.asImageBitmap(),
                category = categoryOf(pm, it.activityInfo.packageName),
                opaque = rendered.second,
                tint = if (rendered.second) 0xFF2C2C2E.toInt() else dominantTint(rendered.first),
                installed = try {
                    pm.getPackageInfo(it.activityInfo.packageName, 0).firstInstallTime
                } catch (e: Exception) {
                    0L
                }
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun parseFolders(s: String): List<Pair<String, List<String>>> =
    s.split(";")
        .filter { it.contains(":") }
        .map {
            val parts = it.split(":", limit = 2)
            parts[0] to parts[1].split(",").filter { x -> x.isNotBlank() }
        }
        .filter { it.second.isNotEmpty() }

fun serializeFolders(f: List<Pair<String, List<String>>>): String =
    f.joinToString(";") { it.first + ":" + it.second.joinToString(",") }

fun folderBadge(apps: List<AppItem>, badges: Map<String, Int>): Int {
    var total = 0
    apps.forEach { total += badges[it.pkg] ?: 0 }
    return total
}

@Composable
fun wiggleState(active: Boolean): State<Float> {
    val zero = remember { mutableStateOf(0f) }
    if (!active) return zero
    val transition = rememberInfiniteTransition(label = "wiggle")
    val anim = transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 110, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggleAngle"
    )
    return anim
}

@Composable
fun IconGrid(
    items: List<HomeItem>,
    baseIndex: Int,
    rowCount: Int,
    cols: Int,
    iconSize: Dp,
    pageIndex: Int,
    showLabels: Boolean,
    editing: Boolean,
    wiggle: State<Float>,
    badges: Map<String, Int>,
    ctrl: DragController,
    onLaunch: (AppItem) -> Unit,
    onLong: (AppItem) -> Unit,
    onRemove: (AppItem) -> Unit,
    onFolder: (String, List<AppItem>) -> Unit,
    onFolderLong: (String) -> Unit,
    onFolderRemove: (String) -> Unit
) {
    val dragging = ctrl.app
    val draggingFolder = ctrl.folderName
    val arr = remember { FloatArray(5) }

    DisposableEffect(pageIndex) {
        ctrl.pageGrids[pageIndex] = arr
        onDispose { ctrl.pageGrids.remove(pageIndex) }
    }

    Column(
        Modifier.fillMaxWidth().onGloballyPositioned {
            val p = it.positionInWindow()
            arr[0] = p.x
            arr[1] = p.y
            arr[2] = it.size.width.toFloat()
            arr[3] = baseIndex.toFloat()
            arr[4] = rowCount.toFloat()
        }
    ) {
        for (r in 0 until rowCount) {
            Row(Modifier.fillMaxWidth().height(100.dp)) {
                for (c in 0 until cols) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        items.getOrNull(r * cols + c)?.let { item ->
                            when (item) {
                                is HomeItem.AppEntry -> AppIcon(
                                    app = item.app,
                                    size = iconSize,
                                    showLabel = showLabels,
                                    editing = editing,
                                    wiggle = if (editing) wiggle else null,
                                    badge = badges[item.app.pkg] ?: 0,
                                    ghost = dragging?.pkg == item.app.pkg,
                                    registry = if (editing) ctrl.bounds else null,
                                    onRemove = { onRemove(item.app) },
                                    onClick = { if (!editing) onLaunch(item.app) },
                                    onLongClick = { if (!editing) onLong(item.app) }
                                )
                                is HomeItem.FolderEntry -> FolderIcon(
                                    name = item.name,
                                    apps = item.apps,
                                    size = iconSize,
                                    badge = folderBadge(item.apps, badges),
                                    editing = editing,
                                    wiggle = if (editing) wiggle else null,
                                    ghost = draggingFolder == item.name,
                                    registry = if (editing) ctrl.bounds else null,
                                    onRemove = { onFolderRemove(item.name) },
                                    onClick = { if (!editing) onFolder(item.name, item.apps) },
                                    onLongClick = { if (!editing) onFolderLong(item.name) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LauncherScreen(
    media: MediaWatcher,
    hasAccess: Boolean,
    call: CallInfo?,
    onNeedAccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    val apps by produceState(initialValue = emptyList<AppItem>(), context) {
        value = withContext(Dispatchers.Default) { loadApps(context) }
    }

    fun readList(key: String): List<String> =
        prefs.getString(key, "")!!.split(",").filter { it.isNotBlank() }

    fun saveList(key: String, list: List<String>) {
        prefs.edit().putString(key, list.joinToString(",")).apply()
    }

    var dockPkgs by remember { mutableStateOf(readList("dock")) }
    var noHome by remember { mutableStateOf(readList("nohome")) }
    var hidden by remember { mutableStateOf(readList("hidden")) }
    var order by remember { mutableStateOf(readList("order")) }
    var widgets by remember {
        mutableStateOf(
            if (prefs.contains("widgets")) readList("widgets")
            else listOf("clock", "date", "weather", "battery", "music")
        )
    }
    var dockMax by remember { mutableStateOf(prefs.getInt("dockmax", 4)) }
    var homeWidgets by remember { mutableStateOf(prefs.getBoolean("homewidgets", false)) }
    var showLabels by remember { mutableStateOf(prefs.getBoolean("labels", true)) }
    var iconStep by remember { mutableStateOf(prefs.getInt("iconstep", 1)) }
    var pageEffect by remember { mutableStateOf(prefs.getInt("pageeffect", 0)) }
    var locked by remember { mutableStateOf(readList("locked")) }
    var pendingLaunch by remember { mutableStateOf<AppItem?>(null) }
    var folders by remember { mutableStateOf(parseFolders(prefs.getString("folders", "")!!)) }
    var weatherCity by remember { mutableStateOf(prefs.getString("city", "")!!) }
    var searching by remember { mutableStateOf(false) }
    var showControl by remember { mutableStateOf(false) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var wallVersion by remember { mutableStateOf(0) }
    var pendingWall by remember { mutableStateOf<Bitmap?>(null) }
    var pendingPage by remember { mutableStateOf(-1) }
    var dockPos by remember { mutableStateOf(Offset.Zero) }
    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var openGroup by remember { mutableStateOf<Pair<String, List<AppItem>>?>(null) }
    var openGroupIsFolder by remember { mutableStateOf(false) }
    var showHideManager by remember { mutableStateOf(false) }
    var widgetEditing by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(prefs.getString("notes", "")!!) }
    val widgetDrag = remember { WidgetDragState() }

    val badges = IslandState.badges
    val wiggle = wiggleState(editing)
    val ctrl = remember { DragController() }
    val haptic = LocalHapticFeedback.current

    val wallpaper by produceState<WallpaperImages?>(initialValue = null, wallVersion) {
        value = withContext(Dispatchers.IO) { loadWallpaper(context) }
    }

    val weather by produceState<WeatherNow?>(
        initialValue = if (weatherCity.isBlank()) null else readWeatherCache(prefs),
        weatherCity
    ) {
        while (true) {
            if (weatherCity.isBlank()) {
                value = null
            } else {
                val f = withContext(Dispatchers.IO) { fetchWeatherNow(weatherCity) }
                if (f != null) {
                    value = f
                    writeWeatherCache(prefs, f)
                }
            }
            delay(30 * 60 * 1000L)
        }
    }

    val battery by produceState(initialValue = -1) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60_000)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val b = withContext(Dispatchers.IO) { decodeForPreview(context, uri) }
                if (b != null) {
                    pendingWall = b
                } else {
                    Toast.makeText(context, "Photo khul nahi payi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val visible = apps.filter { it.pkg !in hidden }
    val hiddenApps = apps.filter { it.pkg in hidden }

    val pinLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            openGroup = "Hidden" to hiddenApps
            openGroupIsFolder = false
        }
    }

    val dock = dockPkgs.mapNotNull { p -> visible.firstOrNull { it.pkg == p } }
        .take(dockMax)
        .ifEmpty { visible.take(4) }
    val dockRef = rememberUpdatedState(dock)
    val folderPkgs = folders.flatMap { it.second }.toSet()
    val homeApps = visible
        .filter { a -> dock.none { it.pkg == a.pkg } && a.pkg !in folderPkgs && a.pkg !in noHome }
        .sortedBy { a ->
            val i = order.indexOf(a.pkg)
            if (i < 0) Int.MAX_VALUE else i
        }
    val folderEntries = folders
        .map { f -> HomeItem.FolderEntry(f.first, f.second.mapNotNull { p -> visible.firstOrNull { it.pkg == p } }) }
        .filter { it.apps.isNotEmpty() }
    val items: List<HomeItem> = (folderEntries + homeApps.map { HomeItem.AppEntry(it) })
        .sortedBy { item ->
            val i = order.indexOf(itemKey(item))
            if (i >= 0) i else if (item is HomeItem.FolderEntry) -1 else Int.MAX_VALUE
        }
    val itemsRef = rememberUpdatedState(items)

    val openApp: (AppItem) -> Unit = { app ->
        val li = context.packageManager.getLaunchIntentForPackage(app.pkg)
        if (li != null) {
            val opts = try {
                val act = context as Activity
                if (LaunchAnchor.valid) {
                    ActivityOptions.makeScaleUpAnimation(
                        act.window.decorView,
                        LaunchAnchor.x,
                        LaunchAnchor.y,
                        LaunchAnchor.w,
                        LaunchAnchor.h
                    ).toBundle()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            context.startActivity(li, opts)
        }
    }

    val lockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val a = pendingLaunch
        pendingLaunch = null
        if (res.resultCode == Activity.RESULT_OK && a != null) {
            openApp(a)
        }
    }

    val launch: (AppItem) -> Unit = { app ->
        if (app.pkg in locked) {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val ci = km.createConfirmDeviceCredentialIntent(app.label, "Phone ka PIN, pattern ya fingerprint daalo")
            if (ci != null) {
                pendingLaunch = app
                lockLauncher.launch(ci)
            } else {
                openApp(app)
            }
        } else {
            openApp(app)
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = exportPrefs(context, prefs, uri)
            Toast.makeText(context, if (ok) "Backup ho gaya" else "Backup nahi hua", Toast.LENGTH_SHORT).show()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (importPrefs(context, prefs, uri)) {
                (context as Activity).recreate()
            } else {
                Toast.makeText(context, "Restore nahi hua", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val callPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Toast.makeText(
            context,
            if (ok) "Call permission mil gayi" else "Call permission nahi mili",
            Toast.LENGTH_SHORT
        ).show()
    }

    val toggleDock: (AppItem) -> Unit = { app ->
        val cur = dock.map { it.pkg }
        val updated = when {
            app.pkg in cur -> cur - app.pkg
            cur.size < dockMax -> cur + app.pkg
            else -> {
                Toast.makeText(context, "Dock full (max " + dockMax + ")", Toast.LENGTH_SHORT).show()
                cur
            }
        }
        dockPkgs = updated
        saveList("dock", updated)
    }

    fun toggleHome(pkg: String) {
        val u = if (pkg in noHome) noHome - pkg else noHome + pkg
        noHome = u
        saveList("nohome", u)
    }

    fun toggleHidden(pkg: String) {
        val u = if (pkg in hidden) hidden - pkg else hidden + pkg
        hidden = u
        saveList("hidden", u)
    }

    fun saveFolders(newList: List<Pair<String, List<String>>>) {
        val clean = newList.filter { it.second.isNotEmpty() }
        folders = clean
        prefs.edit().putString("folders", serializeFolders(clean)).apply()
        val live = clean.map { "f:" + it.first }.toSet()
        val tidy = order.filter { !it.startsWith("f:") || it in live }
        if (tidy.size != order.size) {
            order = tidy
            saveList("order", tidy)
        }
    }

    fun moveToFolder(pkg: String, name: String) {
        val stripped = folders.map { it.first to it.second.filter { p -> p != pkg } }
        val updated = if (stripped.any { it.first == name }) {
            stripped.map { if (it.first == name) it.first to (it.second + pkg) else it }
        } else {
            stripped + (name to listOf(pkg))
        }
        saveFolders(updated)
    }

    fun removeFromFolder(pkg: String) {
        val owner = folders.firstOrNull { pkg in it.second }
        if (owner != null) {
            val keys = items.map { itemKey(it) }.toMutableList()
            val pos = keys.indexOf("f:" + owner.first)
            keys.remove(pkg)
            if (pos >= 0) {
                val left = owner.second.count { it != pkg }
                if (left <= 0) {
                    keys[pos] = pkg
                } else {
                    keys.add(pos + 1, pkg)
                }
                order = keys
                saveList("order", keys)
            }
        }
        saveFolders(folders.map { it.first to it.second.filter { p -> p != pkg } })
    }

    fun renameFolder(old: String, new: String) {
        if (new != old && folders.any { it.first == new }) {
            Toast.makeText(context, "Is naam ka folder pehle se hai", Toast.LENGTH_SHORT).show()
        } else {
            val renamedOrder = order.map { if (it == "f:" + old) "f:" + new else it }
            order = renamedOrder
            saveList("order", renamedOrder)
            saveFolders(folders.map { if (it.first == old) new to it.second else it })
        }
    }

    fun dissolveFolder(name: String) {
        val f = folders.firstOrNull { it.first == name }
        if (f != null) {
            val keys = items.map { itemKey(it) }.toMutableList()
            val pos = keys.indexOf("f:" + name)
            if (pos >= 0) {
                keys.removeAt(pos)
                keys.addAll(pos, f.second)
                order = keys
                saveList("order", keys)
            }
        }
        saveFolders(folders.filter { it.first != name })
    }

    fun moveItem(key: String, index: Int) {
        if (items.isEmpty()) return
        val keys = items.map { itemKey(it) }.toMutableList()
        keys.remove(key)
        keys.add(index.coerceIn(0, keys.size), key)
        order = keys
        saveList("order", keys)
    }

    fun createFolder(a: AppItem, b: AppItem) {
        var name = b.category
        var n = 2
        while (folders.any { it.first == name }) {
            name = b.category + " " + n
            n += 1
        }
        val keys = items.map { itemKey(it) }.toMutableList()
        keys.remove(a.pkg)
        val pos = keys.indexOf(b.pkg)
        if (pos >= 0) {
            keys[pos] = "f:" + name
        } else {
            keys.add("f:" + name)
        }
        order = keys
        saveList("order", keys)
        saveFolders(folders + (name to listOf(b.pkg, a.pkg)))
        Toast.makeText(context, "Folder bana: " + name, Toast.LENGTH_SHORT).show()
    }

    fun addToDock(app: AppItem) {
        val cur = dock.map { it.pkg }
        if (app.pkg in cur) return
        if (cur.size >= dockMax) {
            Toast.makeText(context, "Dock full (max " + dockMax + ")", Toast.LENGTH_SHORT).show()
            return
        }
        val u = cur + app.pkg
        dockPkgs = u
        saveList("dock", u)
    }

    fun removeFromDock(app: AppItem) {
        val u = dock.map { it.pkg } - app.pkg
        dockPkgs = u
        saveList("dock", u)
    }

    fun toggleLock(pkg: String) {
        val u = if (pkg in locked) locked - pkg else locked + pkg
        locked = u
        saveList("locked", u)
    }

    fun reorderWidget(key: String, drop: Offset) {
        var target: String? = null
        for (entry in widgetDrag.bounds.entries) {
            if (entry.key != key && entry.value.contains(drop)) {
                target = entry.key
                break
            }
        }
        if (target == null) return
        val list = widgets.toMutableList()
        val from = list.indexOf(key)
        val to = list.indexOf(target)
        if (from < 0 || to < 0) return
        list.removeAt(from)
        list.add(to, key)
        widgets = list
        saveList("widgets", list)
    }

    fun applyPreset(i: Int) {
        val dm = context.resources.displayMetrics
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                saveWallpaperBitmap(context, makePresetWallpaper(i, dm.widthPixels, dm.heightPixels))
            }
            if (ok) wallVersion += 1
        }
    }

    fun handleDrop(app: AppItem, index: Int, merge: Boolean) {
        val targetItem = items.getOrNull(index)
        if (merge && targetItem is HomeItem.AppEntry && targetItem.app.pkg != app.pkg) {
            createFolder(app, targetItem.app)
        } else if (merge && targetItem is HomeItem.FolderEntry) {
            moveToFolder(app.pkg, targetItem.name)
        } else {
            moveItem(app.pkg, index)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val isTablet = maxWidth > 600.dp
        val cols = if (isTablet) 6 else 4
        val baseIcon = if (isTablet) 66f else 60f
        val iconSize = (baseIcon + (iconStep - 1) * 8f).dp
        val rows = ((maxHeight.value - 290f) / 100f).toInt().coerceIn(2, 8)
        val capacity = cols * rows
        val showHomeWidgets = homeWidgets && widgets.isNotEmpty()
        val firstRows = if (showHomeWidgets) (rows - 2).coerceAtLeast(0) else rows
        val firstCount = cols * firstRows
        val pages: List<List<HomeItem>> =
            listOf(items.take(firstCount)) + items.drop(firstCount).chunked(capacity)
        val pagerState = rememberPagerState(initialPage = 1, pageCount = { pages.size + 2 })

        val dockCount = dock.size.coerceAtLeast(1)
        val dockIcon = minOf(iconSize.value, (maxWidth.value - 64f - 14f * (dockCount - 1)) / dockCount)
            .coerceAtLeast(36f).dp

        val screenDensity = LocalDensity.current
        val cellHpx = with(screenDensity) { 100.dp.toPx() }

        fun finishDrag() {
            val app = ctrl.app
            val folderName = ctrl.folderName
            if (app == null && folderName == null) return
            val p = ctrl.pos
            val wasDock = ctrl.fromDock
            ctrl.app = null
            ctrl.folderName = null
            ctrl.folderApps = emptyList()
            if (p.y >= dockPos.y - 30f) {
                if (app != null && !wasDock) addToDock(app)
                return
            }
            if (app != null && wasDock) removeFromDock(app)
            val g = ctrl.pageGrids[pagerState.currentPage - 1] ?: return
            val gw = g[2]
            val rowsHere = g[4].toInt()
            if (gw <= 0f || rowsHere < 1) return
            val cw = gw / cols
            val relX = (p.x - g[0]) / cw
            val relY = (p.y - g[1]) / cellHpx
            val col = relX.toInt().coerceIn(0, cols - 1)
            val row = relY.toInt().coerceIn(0, rowsHere - 1)
            val fx = relX - col
            val fy = relY - row
            val merge = relX >= 0f && relY >= 0f && fx in 0.2f..0.8f && fy in 0.05f..0.85f
            val idx = g[3].toInt() + row * cols + col
            if (app != null) {
                handleDrop(app, idx, merge)
            } else if (folderName != null) {
                moveItem("f:" + folderName, idx)
            }
        }

        val finishRef = rememberUpdatedState({ finishDrag() })

        LaunchedEffect(ctrl.active) {
            var since = 0L
            while (ctrl.active) {
                val p = ctrl.pos
                val margin = screenW * 0.08f
                val dir = if (p.x < margin) -1 else if (p.x > screenW - margin) 1 else 0
                if (dir != 0) {
                    if (since == 0L) since = System.currentTimeMillis()
                    if (System.currentTimeMillis() - since > 450L) {
                        val target = (pagerState.currentPage + dir).coerceIn(1, pages.size)
                        if (target != pagerState.currentPage) {
                            pagerState.animateScrollToPage(target)
                        }
                        since = 0L
                    }
                } else {
                    since = 0L
                }
                delay(60)
            }
        }

        LaunchedEffect(editing) {
            if (!editing) ctrl.bounds.clear()
        }

        CompositionLocalProvider(LocalFrost provides FrostInfo(wallpaper?.blurred, screenW, screenH)) {
          Box(
            Modifier.fillMaxSize().pointerInput(editing) {
                if (editing) {
                    dragGestures(
                        findKey = { p ->
                            var hit: String? = null
                            for (entry in ctrl.bounds.entries) {
                                if (entry.value.contains(p)) {
                                    hit = entry.key
                                    break
                                }
                            }
                            hit
                        },
                        onStart = { key, p ->
                            if (key.startsWith("f:")) {
                                val fe = itemsRef.value
                                    .filterIsInstance<HomeItem.FolderEntry>()
                                    .firstOrNull { "f:" + it.name == key }
                                if (fe != null) {
                                    ctrl.fromDock = false
                                    ctrl.pos = p
                                    ctrl.folderApps = fe.apps
                                    ctrl.folderName = fe.name
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            } else {
                                val a = apps.firstOrNull { it.pkg == key }
                                if (a != null) {
                                    ctrl.fromDock = dockRef.value.any { it.pkg == key }
                                    ctrl.pos = p
                                    ctrl.app = a
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onMove = { p -> ctrl.pos = p },
                        onEnd = { finishRef.value() }
                    )
                }
            }
          ) {
            wallpaper?.let { w ->
                Image(
                    bitmap = w.full,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 44.dp)
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                    Box(
                        if (pageEffect == 1) {
                            Modifier.fillMaxSize().graphicsLayer {
                                val off = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                val c = off.coerceIn(-1f, 1f)
                                transformOrigin = TransformOrigin(if (c > 0f) 1f else 0f, 0.5f)
                                rotationY = c * 60f
                                cameraDistance = 14f * density
                            }
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ) {
                        if (page == 0) {
                            TodayPage(
                                selected = widgets,
                                weather = weather,
                                battery = battery,
                                media = media,
                                notes = notes,
                                onNotes = { v ->
                                    notes = v
                                    prefs.edit().putString("notes", v).apply()
                                },
                                editing = widgetEditing,
                                drag = widgetDrag,
                                onReorder = { k, p -> reorderWidget(k, p) },
                                onDoneEdit = { widgetEditing = false },
                                onPick = { showWidgetPicker = true },
                                onWeatherClick = { showCityDialog = true },
                                onLongPress = { widgetEditing = true }
                            )
                        } else if (page <= pages.size) {
                            val pageIndex = page - 1
                            Column(
                                Modifier.fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onDoubleTap = { lockScreen(context) })
                                    }
                                    .pointerInput(Unit) {
                                        var total = 0f
                                        detectVerticalDragGestures(
                                            onDragStart = { total = 0f },
                                            onDragEnd = { if (total > 140f) searching = true },
                                            onDragCancel = { total = 0f },
                                            onVerticalDrag = { _, amount -> total += amount }
                                        )
                                    }
                                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                            ) {
                                if (pageIndex == 0 && showHomeWidgets) {
                                    HomeWidgets(
                                        selected = widgets,
                                        weather = weather,
                                        battery = battery,
                                        media = media,
                                        onWeatherClick = { showCityDialog = true },
                                        onLongPress = { showHomeMenu = true }
                                    )
                                }
                                IconGrid(
                                    items = pages[pageIndex],
                                    baseIndex = if (pageIndex == 0) 0 else firstCount + (pageIndex - 1) * capacity,
                                    rowCount = if (pageIndex == 0) firstRows else rows,
                                    cols = cols,
                                    iconSize = iconSize,
                                    pageIndex = pageIndex,
                                    showLabels = showLabels,
                                    editing = editing,
                                    wiggle = wiggle,
                                    badges = badges,
                                    ctrl = ctrl,
                                    onLaunch = launch,
                                    onLong = { app -> menuApp = app },
                                    onRemove = { app -> toggleHome(app.pkg) },
                                    onFolder = { name, list ->
                                        openGroup = name to list
                                        openGroupIsFolder = true
                                    },
                                    onFolderLong = { name -> folderMenu = name },
                                    onFolderRemove = { name -> dissolveFolder(name) }
                                )
                            }
                        } else {
                            LibraryPage(
                                apps = visible,
                                onSearch = { searching = true },
                                onOpen = { name, list ->
                                    openGroup = name to list
                                    openGroupIsFolder = false
                                },
                                onLaunch = launch,
                                onLong = { app -> menuApp = app }
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pagerState.pageCount) { i ->
                        Box(
                            Modifier.padding(3.dp).size(7.dp).clip(CircleShape).background(
                                Color.White.copy(alpha = if (i == pagerState.currentPage) 1f else 0.4f)
                            )
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    if (editing) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0A84FF))
                                .clickable { editing = false }
                                .padding(horizontal = 32.dp, vertical = 8.dp)
                        ) {
                            Text("Done", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                                .combinedClickable(
                                    onClick = { searching = true },
                                    onLongClick = { showHomeMenu = true }
                                )
                                .padding(horizontal = 28.dp, vertical = 8.dp)
                        ) {
                            Text("Search", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.clip(RoundedCornerShape(32.dp))
                            .onGloballyPositioned { dockPos = it.positionInWindow() }
                            .glass(RoundedCornerShape(32.dp), Color(0x22FFFFFF), Color(0x66FFFFFF))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dock.forEach { app ->
                            AppIcon(
                                app = app,
                                size = dockIcon,
                                showLabel = false,
                                badge = badges[app.pkg] ?: 0,
                                editing = editing,
                                wiggle = if (editing) wiggle else null,
                                ghost = ctrl.app?.pkg == app.pkg,
                                registry = if (editing) ctrl.bounds else null,
                                onRemove = { removeFromDock(app) },
                                onClick = { if (!editing) launch(app) },
                                onLongClick = { if (!editing) menuApp = app }
                            )
                        }
                    }
                }
            }

            Box(
                Modifier.align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 2.dp)
                    .width(150.dp)
                    .height(46.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 6f) showControl = true
                        }
                    }
            )
            Box(
                Modifier.align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 2.dp)
                    .width(150.dp)
                    .height(46.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 6f) expandNotifications(context)
                        }
                    }
            )

            if (searching) {
                SearchOverlay(
                    apps = visible,
                    cols = cols,
                    onClose = { searching = false },
                    onLaunch = { app ->
                        searching = false
                        launch(app)
                    }
                )
            }

            openGroup?.let { group ->
                val canRename = openGroupIsFolder && folders.any { it.first == group.first }
                val renameCb: (String) -> Unit = { newName ->
                    val t = newName.trim()
                    if (t.isNotBlank() && t != group.first) {
                        val clash = folders.any { it.first == t }
                        renameFolder(group.first, t)
                        if (!clash) openGroup = t to group.second
                    }
                }
                FolderOverlay(
                    title = group.first,
                    apps = group.second,
                    cols = cols,
                    onClose = { openGroup = null },
                    onLaunch = { app ->
                        openGroup = null
                        launch(app)
                    },
                    onLong = { app -> menuApp = app },
                    onRename = if (canRename) renameCb else null
                )
            }

            if (showHomeMenu) {
                val sizeName = if (iconStep == 0) "Chhota" else if (iconStep == 1) "Normal" else "Bada"
                val overlayOn = Settings.canDrawOverlays(context)
                val callPermOk = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED

                val homeEntries = listOf(
                    MenuEntry("Icons edit karo (khiskao)") { editing = true },
                    MenuEntry("Icon size: " + sizeName, true) {
                        val n = (iconStep + 1) % 3
                        iconStep = n
                        prefs.edit().putInt("iconstep", n).apply()
                    },
                    MenuEntry("Dock me max apps: " + dockMax, true) {
                        val n = if (dockMax >= 6) 4 else dockMax + 1
                        dockMax = n
                        prefs.edit().putInt("dockmax", n).apply()
                    },
                    MenuEntry("App ke naam: " + (if (showLabels) "ON" else "OFF"), true) {
                        val v = !showLabels
                        showLabels = v
                        prefs.edit().putBoolean("labels", v).apply()
                    },
                    MenuEntry("Page effect: " + (if (pageEffect == 1) "3D" else "Normal"), true) {
                        val n = if (pageEffect == 1) 0 else 1
                        pageEffect = n
                        prefs.edit().putInt("pageeffect", n).apply()
                    },
                    MenuEntry("Home pe widgets: " + (if (homeWidgets) "ON" else "OFF"), true) {
                        val v = !homeWidgets
                        homeWidgets = v
                        prefs.edit().putBoolean("homewidgets", v).apply()
                    },
                    MenuEntry("Layout reset (icons ki jagah wapas)") {
                        order = emptyList()
                        noHome = emptyList()
                        saveList("order", emptyList())
                        saveList("nohome", emptyList())
                        saveFolders(emptyList())
                    }
                )

                val widgetEntries = listOf(
                    MenuEntry("Widgets chuno (add / hatao)") { showWidgetPicker = true },
                    MenuEntry("Widgets ki jagah badlo") {
                        widgetEditing = true
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    MenuEntry("Weather ka sheher: " + (if (weatherCity.isBlank()) "set nahi" else weatherCity)) {
                        showCityDialog = true
                    }
                )

                val wallEntries = buildList<MenuEntry> {
                    add(MenuEntry("Photo se wallpaper lagao") { picker.launch("image/*") })
                    PRESET_NAMES.forEachIndexed { i, name ->
                        add(MenuEntry("iPhone style: " + name) { applyPreset(i) })
                    }
                    if (wallpaper != null) {
                        add(MenuEntry("Wallpaper hatao") {
                            deleteWallpaper(context)
                            wallVersion += 1
                        })
                    }
                }

                val appEntries = listOf(
                    MenuEntry("Apps chhupao / dikhao") { showHideManager = true },
                    MenuEntry("Hidden apps dekho (" + hiddenApps.size + ")") {
                        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                        val pinIntent = km.createConfirmDeviceCredentialIntent(
                            "Hidden apps",
                            "Phone ka PIN, pattern ya fingerprint daalo"
                        )
                        if (pinIntent != null) {
                            pinLauncher.launch(pinIntent)
                        } else {
                            openGroup = "Hidden" to hiddenApps
                            openGroupIsFolder = false
                        }
                    }
                )

                var overlayWanted by remember { mutableStateOf(prefs.getBoolean("overlayisland", true)) }
                val islandEntries = buildList<MenuEntry> {
                    add(
                        MenuEntry("Doosre apps ke upar Island: " + (if (overlayWanted) "ON" else "OFF"), true) {
                            val v = !overlayWanted
                            overlayWanted = v
                            prefs.edit().putBoolean("overlayisland", v).apply()
                        }
                    )
                    add(
                        MenuEntry("Island status dekho") {
                            val v = IslandState.launcherView?.get()
                            Toast.makeText(
                                context,
                                "attached=" + (v?.isAttachedToWindow) +
                                    " winVis=" + (v?.windowVisibility) +
                                    " visible=" + IslandState.launcherReallyVisible(),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                    add(
                        MenuEntry(
                            if (overlayOn) "Island doosre apps pe: ON (dobara chalao)"
                            else "Island doosre apps pe chalu karo"
                        ) {
                            if (Settings.canDrawOverlays(context)) {
                                IslandState.service?.ensureOverlay()
                                Toast.makeText(context, "Island doosre apps pe chalu hai", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:" + context.packageName)
                                        )
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Settings nahi khuli", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    if (!hasAccess) {
                        add(MenuEntry("Notification access do (music / call / noti)") { onNeedAccess() })
                    }
                    add(
                        MenuEntry(if (callPermOk) "Call permission: mil chuki hai" else "Call answer / decline permission do") {
                            if (callPermOk) {
                                Toast.makeText(context, "Call permission pehle se hai", Toast.LENGTH_SHORT).show()
                            } else {
                                callPermLauncher.launch(Manifest.permission.ANSWER_PHONE_CALLS)
                            }
                        }
                    )
                    add(
                        MenuEntry("Call island test (8 sec)") {
                            IslandState.call = CallInfo("Test Caller", "Incoming call", emptyList())
                            scope.launch {
                                delay(8000)
                                if (IslandState.call?.title == "Test Caller") IslandState.call = null
                            }
                        }
                    )
                }

                val phoneEntries = listOf(
                    MenuEntry(
                        if (AppFontStatus == "none") "Font: file nahi mili (phone ka font chal raha hai)"
                        else "Font: Inter chal raha hai"
                    ) { },
                    MenuEntry("Double-tap se screen lock") {
                        if (isLockReady(context)) {
                            Toast.makeText(context, "Double-tap lock pehle se chalu hai", Toast.LENGTH_SHORT).show()
                        } else {
                            requestLockAdmin(context)
                        }
                    },
                    MenuEntry("Default launcher banao") {
                        try {
                            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Settings nahi khuli", Toast.LENGTH_SHORT).show()
                        }
                    },
                    MenuEntry("Backup banao") { backupLauncher.launch("iphone_launcher_backup.json") },
                    MenuEntry("Backup se restore karo") { restoreLauncher.launch(arrayOf("*/*")) }
                )

                HomeMenuDialog(
                    sections = listOf(
                        "Home Screen" to homeEntries,
                        "Widgets" to widgetEntries,
                        "Wallpaper" to wallEntries,
                        "Apps (hide / dikhao)" to appEntries,
                        "Island aur Calls" to islandEntries,
                        "Phone" to phoneEntries
                    ),
                    onDismiss = { showHomeMenu = false }
                )
            }

            if (showWidgetPicker) {
                WidgetPickerDialog(
                    selected = widgets,
                    onToggle = { id ->
                        val u = if (id in widgets) widgets - id else widgets + id
                        widgets = u
                        saveList("widgets", u)
                    },
                    onDismiss = { showWidgetPicker = false }
                )
            }

            if (showHideManager) {
                HideManagerDialog(
                    apps = apps,
                    hidden = hidden,
                    onToggle = { pkg -> toggleHidden(pkg) },
                    onDismiss = { showHideManager = false }
                )
            }

            if (showCityDialog) {
                CityDialog(
                    current = weatherCity,
                    onSave = { city ->
                        weatherCity = city
                        prefs.edit().putString("city", city).apply()
                        showCityDialog = false
                    },
                    onDismiss = { showCityDialog = false }
                )
            }

            folderMenu?.let { fname ->
                FolderMenuDialog(
                    name = fname,
                    onRename = { newName ->
                        renameFolder(fname, newName)
                        folderMenu = null
                    },
                    onDissolve = {
                        dissolveFolder(fname)
                        folderMenu = null
                    },
                    onEdit = {
                        folderMenu = null
                        editing = true
                    },
                    onDismiss = { folderMenu = null }
                )
            }

            menuApp?.let { app ->
                AppActionDialog(
                    app = app,
                    inDock = dock.any { it.pkg == app.pkg },
                    inFolder = app.pkg in folderPkgs,
                    inNoHome = app.pkg in noHome,
                    isHidden = app.pkg in hidden,
                    isLocked = app.pkg in locked,
                    folderNames = folders.map { it.first },
                    onDock = {
                        toggleDock(app)
                        menuApp = null
                    },
                    onAddToFolder = { name ->
                        moveToFolder(app.pkg, name)
                        menuApp = null
                        openGroup = null
                    },
                    onRemoveFromFolder = {
                        removeFromFolder(app.pkg)
                        menuApp = null
                        openGroup = null
                    },
                    onToggleHome = {
                        toggleHome(app.pkg)
                        menuApp = null
                    },
                    onToggleHidden = {
                        toggleHidden(app.pkg)
                        menuApp = null
                        openGroup = null
                    },
                    onToggleLock = {
                        toggleLock(app.pkg)
                        menuApp = null
                    },
                    onInfo = {
                        menuApp = null
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "App info nahi khuli", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onUninstall = {
                        menuApp = null
                        try {
                            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Uninstall nahi khula", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEdit = {
                        menuApp = null
                        openGroup = null
                        editing = true
                    },
                    onDismiss = { menuApp = null }
                )
            }

            if (showControl) {
                ControlCenter(media = media, onClose = { showControl = false })
            }

            pendingWall?.let { bmp ->
                WallpaperPreview(
                    image = remember(bmp) { bmp.asImageBitmap() },
                    onApply = { z, x, y ->
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                saveWallpaperBitmap(
                                    context,
                                    renderWallpaper(bmp, screenW.toInt(), screenH.toInt(), z, x, y)
                                )
                            }
                            if (ok) {
                                wallVersion += 1
                            } else {
                                Toast.makeText(context, "Wallpaper nahi lag paya", Toast.LENGTH_SHORT).show()
                            }
                            pendingWall = null
                        }
                    },
                    onApplyLock = { z, x, y ->
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                setLockWallpaper(
                                    context,
                                    renderWallpaper(bmp, screenW.toInt(), screenH.toInt(), z, x, y)
                                )
                            }
                            Toast.makeText(
                                context,
                                if (ok) "Lock screen wallpaper lag gaya" else "Lock screen wallpaper nahi lag paya",
                                Toast.LENGTH_LONG
                            ).show()
                            pendingWall = null
                        }
                    },
                    onCancel = { pendingWall = null }
                )
            }

            Box(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                DynamicIsland(
                    media = media,
                    call = call,
                    hasAccess = hasAccess,
                    onNeedAccess = onNeedAccess,
                    toast = IslandState.toast
                )
            }

            DragOverlay(ctrl, iconSize)
          }
        }
    }
}
