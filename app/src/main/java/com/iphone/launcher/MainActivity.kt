@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.iphone.launcher

import android.content.ApplicationInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(
    val label: String,
    val pkg: String,
    val icon: ImageBitmap,
    val category: String
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

fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            AppItem(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap(160, 160).asImageBitmap(),
                category = categoryOf(pm, it.activityInfo.packageName)
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

    var dockPkgs by remember {
        mutableStateOf(prefs.getString("dock", "")!!.split(",").filter { it.isNotBlank() })
    }
    var folders by remember { mutableStateOf(parseFolders(prefs.getString("folders", "")!!)) }
    var searching by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var openGroup by remember { mutableStateOf<Pair<String, List<AppItem>>?>(null) }

    val dock = dockPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
        .ifEmpty { apps.take(4) }
    val folderPkgs = folders.flatMap { it.second }.toSet()
    val homeApps = apps.filter { a -> dock.none { it.pkg == a.pkg } && a.pkg !in folderPkgs }
    val folderEntries = folders
        .map { f -> HomeItem.FolderEntry(f.first, f.second.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }) }
        .filter { it.apps.isNotEmpty() }
    val items: List<HomeItem> = folderEntries + homeApps.map { HomeItem.AppEntry(it) }

    val launch: (AppItem) -> Unit = { app ->
        context.packageManager.getLaunchIntentForPackage(app.pkg)?.let { context.startActivity(it) }
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
        prefs.edit().putString("dock", updated.joinToString(",")).apply()
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
        val isTablet = maxWidth > 600.dp
        val cols = if (isTablet) 6 else 4
        val iconSize = if (isTablet) 66.dp else 60.dp
        val rows = ((maxHeight.value - 310f) / 100f).toInt().coerceIn(2, 8)
        val perPage = cols * rows
        val pages = items.chunked(perPage).ifEmpty { listOf(emptyList()) }
        val pagerState = rememberPagerState(pageCount = { pages.size + 1 })

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 44.dp)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                if (page < pages.size) {
                    Column(Modifier.fillMaxSize().padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
                        for (r in 0 until rows) {
                            Row(Modifier.fillMaxWidth().height(100.dp)) {
                                for (c in 0 until cols) {
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        pages[page].getOrNull(r * cols + c)?.let { item ->
                                            when (item) {
                                                is HomeItem.AppEntry -> AppIcon(
                                                    app = item.app,
                                                    size = iconSize,
                                                    onClick = { launch(item.app) },
                                                    onLongClick = { menuApp = item.app }
                                                )
                                                is HomeItem.FolderEntry -> FolderIcon(
                                                    name = item.name,
                                                    apps = item.apps,
                                                    size = iconSize,
                                                    onClick = { openGroup = item.name to item.apps }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LibraryPage(apps = apps, cols = cols, onOpen = { name, list -> openGroup = name to list })
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
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .clickable { searching = true }
                        .padding(horizontal = 28.dp, vertical = 8.dp)
                ) {
                    Text("Search", color = Color.White, fontSize = 13.sp)
                }
            }

            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 84.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.28f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dock.forEach { app ->
                    AppIcon(
                        app = app,
                        size = iconSize,
                        showLabel = false,
                        onClick = { launch(app) },
                        onLongClick = { menuApp = app }
                    )
                }
            }
        }

        if (searching) {
            SearchOverlay(
                apps = apps,
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

        menuApp?.let { app ->
            AppActionDialog(
                app = app,
                inDock = dock.any { it.pkg == app.pkg },
                inFolder = app.pkg in folderPkgs,
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
                onDismiss = { menuApp = null }
            )
        }

        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            DynamicIsland(media = media, call = call, hasAccess = hasAccess, onNeedAccess = onNeedAccess)
        }
    }
}

@Composable
fun LibraryPage(apps: List<AppItem>, cols: Int, onOpen: (String, List<AppItem>) -> Unit) {
    val groups = LIBRARY_ORDER.mapNotNull { cat ->
        val list = apps.filter { it.category == cat }
        if (list.isEmpty()) null else cat to list
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "App Library",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (cols > 4) 3 else 2),
            modifier = Modifier.fillMaxSize()
        ) {
            items(groups) { group ->
                val name = group.first
                val list = group.second
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .clickable { onOpen(name, list) }
                            .padding(12.dp)
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                            for (r in 0 until 2) {
                                Row(
                                    Modifier.fillMaxWidth().weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (c in 0 until 2) {
                                        val a = list.getOrNull(r * 2 + c)
                                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
                                            if (a != null) IconImage(a, Modifier.fillMaxSize())
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(name, style = labelStyle, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
fun FolderOverlay(
    title: String,
    apps: List<AppItem>,
    cols: Int,
    onClose: () -> Unit,
    onLaunch: (AppItem) -> Unit,
    onLong: (AppItem) -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xCC3A3A3C))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(16.dp)
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (cols > 4) 4 else cols),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(apps) { app ->
                    Box(Modifier.pa
