@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.iphone.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { LauncherScreen() }
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
                icon = it.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private val labelStyle = TextStyle(
    color = Color.White,
    fontSize = 11.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 3f)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppItem,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp))
        )
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(app.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    val apps by produceState(initialValue = emptyList<AppItem>(), context) {
        value = withContext(Dispatchers.Default) { loadApps(context) }
    }

    var dockPkgs by remember {
        mutableStateOf(prefs.getString("dock", "")!!.split(",").filter { it.isNotBlank() })
    }
    var searching by remember { mutableStateOf(false) }

    val dock = dockPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
        .ifEmpty { apps.take(4) }
    val homeApps = apps.filter { a -> dock.none { it.pkg == a.pkg } }

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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = if (maxWidth > 600.dp) 6 else 4
        val rows = ((maxHeight.value - 260f) / 100f).toInt().coerceIn(2, 8)
        val perPage = cols * rows
        val pages = homeApps.chunked(perPage).ifEmpty { listOf(emptyList()) }
        val pagerState = rememberPagerState(pageCount = { pages.size + 1 })

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                if (page < pages.size) {
                    Column(Modifier.fillMaxSize().padding(top = 16.dp, start = 12.dp, end = 12.dp)) {
                        for (r in 0 until rows) {
                            Row(Modifier.fillMaxWidth().height(100.dp)) {
                                for (c in 0 until cols) {
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        pages[page].getOrNull(r * cols + c)?.let { app ->
                                            AppIcon(app, onClick = { launch(app) }, onLongClick = { toggleDock(app) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        Text(
                            "App Library",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                        LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = Modifier.fillMaxSize()) {
                            items(apps) { app ->
                                Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                                    AppIcon(app, onClick = { launch(app) }, onLongClick = { toggleDock(app) })
                                }
                            }
                        }
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
                    AppIcon(app, showLabel = false, onClick = { launch(app) }, onLongClick = { toggleDock(app) })
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
    }
}

@Composable
fun SearchOverlay(apps: List<AppItem>, cols: Int, onClose: () -> Unit, onLaunch: (AppItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler(enabled = true) { onClose() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).imePadding()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = Modifier.fillMaxSize()) {
                items(results) { app ->
                    Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                        AppIcon(app, onClick = { onLaunch(app) })
                    }
                }
            }
        }
    }
}
