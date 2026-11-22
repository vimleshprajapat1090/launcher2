@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.iphone.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(
    val label: String,
    val pkg: String,
    val icon: ImageBitmap,
    val category: String,
    val opaque: Boolean
)

sealed interface HomeItem {
    data class AppEntry(val app: AppItem) : HomeItem
    data class FolderEntry(val name: String, val apps: List<AppItem>) : HomeItem
}

val LIBRARY_ORDER = listOf(
    "Social", "Entertainment", "Productivity", "Creativity", "Games", "Travel", "Utilities"
)

class MainActivity : ComponentActivity() {
    private val media by lazy { MediaWatcher(this) }
    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LauncherScreen(
                media = media,
                hasAccess = hasAccess,
                call = IslandState.call,
                onNeedAccess = { openAccessSettings() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        media.stop()
        if (hasAccess) media.start()
    }

    override fun onDestroy() {
        media.stop()
        super.onDestroy()
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
        has("whatsapp", "instagram", "facebook", "telegram", "snapchat", "twitter", "messenger", "linkedin", "discord", "reddit", "threads") -> return "Social"
        has("youtube", "netflix", "spotify", "hotstar", "music", "video", "vlc") -> return "Entertainment"
        has("camera", "gallery", "photos", "editor") -> return "Creativity"
        has("maps", "uber", "irctc", "travel", "booking") -> return "Travel"
        has("paytm", "phonepe", "bank", "finance", "docs", "sheets", "drive", "office", "gmail", "calendar", "notes", "keep") -> return "Productivity"
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
                opaque = rendered.second
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
    rowCount: Int,
    cols: Int,
    iconSize: Dp,
    editing: Boolean,
    wiggle: State<Float>,
    onLaunch: (AppItem) -> Unit,
    onLong: (AppItem) -> Unit,
    onRemove: (AppItem) -> Unit,
    onFolder: (String, List<AppItem>) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
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
                                    editing = editing,
                                    wiggle = if (editing) wiggle else null,
                                    onRemove = { onRemove(item.app) },
                                    onClick = { if (!editing) onLaunch(item.app) },
                                    onLongClick = { if (!editing) onLong(item.app) }
                                )
                                is HomeItem.FolderEntry -> FolderIcon(
                                    name = item.name,
                                    apps = item.apps,
                                    size = iconSize,
                                    onClick = { if (!editing) onFolder(item.name, item.apps) }
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
    var folders by remember { mutableStateOf(parseFolders(prefs.getString("folders", "")!!)) }
    var searching by remember { mutableStateOf(false) }
    var showControl by remember { mutableStateOf(false) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var wallVersion by remember { mutableStateOf(0) }
    var dockPos by remember { mutableStateOf(Offset.Zero) }
    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var openGroup by remember { mutableStateOf<Pair<String, List<AppItem>>?>(null) }

    val wiggle = wiggleState(editing)

    val wallpaper by produceState<WallpaperImages?>(initialValue = null, wallVersion) {
        value = withContext(Dispatchers.IO) { loadWallpaper(context) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            if (saveWallpaper(context, uri)) {
                wallVersion += 1
            } else {
                Toast.makeText(context, "Wallpaper nahi lag paya", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val visible = apps.filter { it.pkg !in hidden }
    val hiddenApps = apps.filter { it.pkg in hidden }
    val dock = dockPkgs.mapNotNull { p -> visible.firstOrNull { it.pkg == p } }
        .ifEmpty { visible.take(4) }
    val folderPkgs = folders.flatMap { it.second }.toSet()
    val homeApps = visible.filter { a ->
        dock.none { it.pkg == a.pkg } && a.pkg !in folderPkgs && a.pkg !in noHome
    }
    val folderEntries = folders
        .map { f -> HomeItem.FolderEntry(f.first, f.second.mapNotNull { p -> visible.firstOrNull { it.pkg == p } }) }
        .filter { it.apps.isNotEmpty() }
    val items: List<HomeItem> = folderEntries + homeApps.map { HomeItem.AppEntry(it) }

    val launch: (AppItem) -> Unit = { app ->
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

    val toggleDock: (AppItem) -> Unit = { app ->
        val cur = dock.map { it.pkg }
        val updated = when {
            app.pkg in cur -> cur - app.pkg
            cur.size < 4 -> cur + app.pkg
            else -> {
                Toast.makeText(context, "Dock full (max 4)", Toast.LENGTH_SHORT).show()
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
        saveFolders(folders.map { it.first to it.second.filter { p -> p != pkg } })
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val isTablet = maxWidth > 600.dp
        val cols = if (isTablet) 6 else 4
        val iconSize = if (isTablet) 66.dp else 60.dp
        val rows = ((maxHeight.value - 310f) / 100f).toInt().coerceIn(2, 8)
        val firstRows = (rows - 2).coerceAtLeast(0)
        val firstCount = cols * firstRows
        val pages: List<List<HomeItem>> =
            listOf(items.take(firstCount)) + items.drop(firstCount).chunked(cols * rows)
        val pagerState = rememberPagerState(pageCount = { pages.size + 1 })

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
                if (page < pages.size) {
                    Column(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { lockScreen(context) })
                            }
                            .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                    ) {
                        if (page == 0) {
                            Box(Modifier.fillMaxWidth().height(170.dp)) {
                                ClockWidgets(onLongPress = { showHomeMenu = true })
                            }
                        }
                        IconGrid(
                            items = pages[page],
                            rowCount = if (page == 0) firstRows else rows,
                            cols = cols,
                            iconSize = iconSize,
                            editing = editing,
                            wiggle = wiggle,
                            onLaunch = launch,
                            onLong = { app -> menuApp = app },
                            onRemove = { app -> toggleHome(app.pkg) },
                            onFolder = { name, list -> openGroup = name to list }
                        )
                    }
                } else {
                    LibraryPage(
                        apps = visible,
                        onSearch = { searching = true },
                        onOpen = { name, list -> openGroup = name to list }
                    )
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
                        .frosted(
                            wallpaper?.blurred,
                            screenW,
                            screenH,
                            dockPos,
                            Color.White.copy(alpha = if (wallpaper != null) 0.14f else 0.22f)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dock.forEach { app ->
                        AppIcon(
                            app = app,
                            size = iconSize,
                            showLabel = false,
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
            FolderOverlay(
                title = group.first,
                apps = group.second,
                cols = cols,
                onClose = { openGroup = null },
                onLaunch = { app ->
                    openGroup = null
                    launch(app)
                },
                onLong = { app -> menuApp = app }
            )
        }

        if (showHomeMenu) {
            HomeMenuDialog(
                hasHidden = hiddenApps.isNotEmpty(),
                hasWallpaper = wallpaper != null,
                lockReady = isLockReady(context),
                onWallpaper = {
                    showHomeMenu = false
                    picker.launch("image/*")
                },
                onResetWallpaper = {
                    deleteWallpaper(context)
                    wallVersion += 1
                    showHomeMenu = false
                },
                onEdit = {
                    editing = true
                    showHomeMenu = false
                },
                onHidden = {
                    openGroup = "Hidden" to hiddenApps
                    showHomeMenu = false
                },
                onLockSetup = {
                    showHomeMenu = false
                    if (isLockReady(context)) {
                        Toast.makeText(context, "Double-tap lock pehle se chalu hai", Toast.LENGTH_SHORT).show()
                    } else {
                        requestLockAdmin(context)
                    }
                },
                onDismiss = { showHomeMenu = false }
            )
        }

        menuApp?.let { app ->
            AppActionDialog(
                app = app,
                inDock = dock.any { it.pkg == app.pkg },
                inFolder = app.pkg in folderPkgs,
                inNoHome = app.pkg in noHome,
                isHidden = app.pkg in hidden,
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
                onDismiss = { menuApp = null }
            )
        }

        if (showControl) {
            ControlCenter(media = media, onClose = { showControl = false })
        }

        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            DynamicIsland(media = media, call = call, hasAccess = hasAccess, onNeedAccess = onNeedAccess)
        }
    }
}
