@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.iphone.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun LibraryPage(
    apps: List<AppItem>,
    onSearch: () -> Unit,
    onOpen: (String, List<AppItem>) -> Unit,
    onLaunch: (AppItem) -> Unit,
    onLong: (AppItem) -> Unit
) {
    var mode by remember { mutableStateOf(0) }
    val recent = apps.sortedByDescending { it.installed }.take(12)
    val groups = LIBRARY_ORDER.mapNotNull { cat ->
        val list = apps.filter { it.category == cat }
        if (list.isEmpty()) null else cat to list
    }
    val all = if (recent.isEmpty()) groups else listOf("Recently added" to recent) + groups

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable { onSearch() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("App Library", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
            }
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (mode == 0) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.25f))
                    .clickable { mode = 0 }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("Boxes", color = Color.White, fontSize = 13.sp)
            }
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (mode == 1) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.25f))
                    .clickable { mode = 1 }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("A-Z", color = Color.White, fontSize = 13.sp)
            }
        }
        if (mode == 0) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(all) { group ->
                    val name = group.first
                    val list = group.second
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(150.dp)
                                .glass(RoundedCornerShape(30.dp), Color(0x33FFFFFF), Color(0x55FFFFFF))
                                .clickable { onOpen(name, list) }
                                .padding(10.dp)
                        ) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                                for (r in 0 until 2) {
                                    Row(
                                        Modifier.fillMaxWidth().weight(1f),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (c in 0 until 2) {
                                            val a2 = list.getOrNull(r * 2 + c)
                                            Box(Modifier.weight(1f).aspectRatio(1f).padding(4.dp)) {
                                                if (a2 != null) IconImage(a2, Modifier.fillMaxSize())
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
        } else {
            val sorted = apps.sortedBy { it.label.lowercase() }
            val byLetter = sorted.groupBy {
                val ch = it.label.trim().take(1).uppercase()
                if (ch.isNotEmpty() && ch[0].isLetter()) ch else "#"
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 90.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                byLetter.forEach { entry ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            entry.key,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 8.dp)
                        )
                    }
                    items(entry.value) { app ->
                        Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                            AppIcon(app = app, onClick = { onLaunch(app) }, onLongClick = { onLong(app) })
                        }
                    }
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
    onLong: (AppItem) -> Unit,
    onRename: ((String) -> Unit)? = null
) {
    BackHandler(enabled = true) { onClose() }
    var renaming by remember { mutableStateOf(false) }
    var text by remember(title) { mutableStateOf(title) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "folderPop"
    )
    val fade by animateFloatAsState(targetValue = if (shown) 1f else 0f, label = "folderFade")
    Box(
        Modifier.fillMaxSize()
            .imePadding()
            .background(Color.Black.copy(alpha = 0.5f * fade))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp)
                .widthIn(max = 480.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                    alpha = fade
                }
                .glass(RoundedCornerShape(32.dp), Color(0x66000000), Color(0xCC3A3A3C))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(16.dp)
        ) {
            if (onRename != null && renaming) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                TextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { ch -> ch != ':' && ch != ';' && ch != ',' } },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0x33FFFFFF),
                        unfocusedContainerColor = Color(0x33FFFFFF),
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val t = text.trim()
                            renaming = false
                            if (t.isNotBlank() && t != title) onRename(t)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).focusRequester(focus)
                )
            } else {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .then(
                            if (onRename != null) {
                                Modifier.clickable {
                                    text = title
                                    renaming = true
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(bottom = 12.dp)
                )
            }
            if (apps.isEmpty()) {
                Text(
                    "Yahan koi app nahi hai",
                    color = Color(0xFFB0B0B5),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (cols > 4) 4 else cols),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(apps) { app ->
                        Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                            AppIcon(app = app, onClick = { onLaunch(app) }, onLongClick = { onLong(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color(0xFF0A84FF),
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp)
    )
}

class MenuEntry(val label: String, val keepOpen: Boolean = false, val action: () -> Unit)

@Composable
fun HomeMenuDialog(sections: List<Pair<String, List<MenuEntry>>>, onDismiss: () -> Unit) {
    var open by remember { mutableStateOf(-1) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(Color(0xF22C2C2E))
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (open < 0 || open >= sections.size) {
                Text("Quick settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                sections.forEachIndexed { i, sec ->
                    MenuItem(sec.first + "  \u203A") { open = i }
                }
            } else {
                val sec = sections[open]
                Text(
                    "\u2039  " + sec.first,
                    color = Color(0xFF0A84FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.clickable { open = -1 }.padding(bottom = 6.dp)
                )
                sec.second.forEach { e ->
                    MenuItem(e.label) {
                        e.action()
                        if (!e.keepOpen) onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
fun HideManagerDialog(
    apps: List<AppItem>,
    hidden: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(Color(0xF22C2C2E))
                .padding(20.dp)
        ) {
            Text("Apps chhupao / dikhao", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "ON wale apps home, library aur search se gayab rahenge",
                color = Color(0xFFB0B0B5),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(apps) { app ->
                    val on = app.pkg in hidden
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onToggle(app.pkg) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconImage(app, Modifier.size(34.dp))
                        Text(
                            app.label,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        )
                        Text(
                            if (on) "HIDDEN" else "dikh raha",
                            color = if (on) Color(0xFFFF453A) else Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            MenuItem("Done", onDismiss)
        }
    }
}

@Composable
fun WidgetPickerDialog(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(Color(0xF22C2C2E))
                .padding(20.dp)
        ) {
            Text("Widgets (Today page)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            WIDGET_CATALOG.forEach { entry ->
                val on = entry.first in selected
                MenuItem((if (on) "ON   " else "OFF  ") + entry.second) { onToggle(entry.first) }
            }
            MenuItem("Done", onDismiss)
        }
    }
}

@Composable
fun WallpaperPreview(
    image: ImageBitmap,
    onApply: (Float, Float, Float) -> Unit,
    onApplyLock: (Float, Float, Float) -> Unit,
    onCancel: () -> Unit
) {
    var zoom by remember { mutableStateOf(1f) }
    var tx by remember { mutableStateOf(0f) }
    var ty by remember { mutableStateOf(0f) }
    BackHandler(enabled = true) { onCancel() }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, z, _ ->
                    zoom = (zoom * z).coerceIn(1f, 5f)
                    tx += pan.x
                    ty += pan.y
                }
            }
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = tx
                translationY = ty
            }
        )
        Text(
            "9:41",
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 80.dp)
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 110.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .size(width = 300.dp, height = 84.dp)
        )
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Do ungli se zoom karo, ungli se khiskao",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp))
                        .background(Color(0x88000000))
                        .clickable { onCancel() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Cancel", color = Color.White, fontSize = 15.sp)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp))
                        .background(Color(0x88000000))
                        .clickable { onApplyLock(zoom, tx, ty) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Lock screen pe", color = Color.White, fontSize = 15.sp)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0A84FF))
                        .clickable { onApply(zoom, tx, ty) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Home pe lagao", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CityDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(Color(0xF22C2C2E))
                .padding(20.dp)
        ) {
            Text("Weather ka sheher", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Jaise: Jaipur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            MenuItem("Save") { onSave(text.trim()) }
            MenuItem("Weather hatao") { onSave("") }
        }
    }
}

@Composable
fun FolderMenuDialog(
    name: String,
    onRename: (String) -> Unit,
    onDissolve: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {}
) {
    var text by remember { mutableStateOf(name) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(Color(0xF22C2C2E))
                .padding(20.dp)
        ) {
            Text("Folder: " + name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            TextField(
                value = text,
                onValueChange = { v -> text = v.filter { ch -> ch != ':' && ch != ';' && ch != ',' } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            MenuItem("Naam badlo") {
                if (text.isNotBlank()) onRename(text.trim())
            }
            MenuItem("Folder ko khiskao (edit mode)", onEdit)
            MenuItem("Folder hatao (apps wapas home pe)", onDissolve)
        }
    }
}

@Composable
fun AppActionDialog(
    app: AppItem,
    inDock: Boolean,
    inFolder: Boolean,
    inNoHome: Boolean,
    isHidden: Boolean,
    isLocked: Boolean,
    folderNames: List<String>,
    onDock: () -> Unit,
    onAddToFolder: (String) -> Unit,
    onRemoveFromFolder: () -> Unit,
    onToggleHome: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleLock: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var newName by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(Color(0xF22C2C2E))
                .padding(20.dp)
        ) {
            Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            if (step == 0) {
                MenuItem("Icons edit karo (khiskane ke liye)", onEdit)
                MenuItem(if (inDock) "Dock se hatao" else "Dock me daalo", onDock)
                MenuItem("Folder me daalo") { step = 1 }
                if (inFolder) MenuItem("Folder se bahar nikalo", onRemoveFromFolder)
                MenuItem(
                    if (inNoHome) "Home Screen pe wapas lao" else "Home Screen se hatao (Library me rahega)",
                    onToggleHome
                )
                MenuItem(if (isHidden) "Hidden se wapas lao" else "App chhupao (Hide)", onToggleHidden)
                MenuItem(if (isLocked) "App lock band karo" else "App lock lagao (PIN/fingerprint)", onToggleLock)
                MenuItem("App info", onInfo)
                MenuItem("Uninstall", onUninstall)
            } else {
                folderNames.forEach { n -> MenuItem(n) { onAddToFolder(n) } }
                TextField(
                    value = newName,
                    onValueChange = { v -> newName = v.filter { ch -> ch != ':' && ch != ';' && ch != ',' } },
                    placeholder = { Text("Naya folder ka naam") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MenuItem("Naya folder banao") {
                    if (newName.isNotBlank()) onAddToFolder(newName.trim())
                }
            }
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

    Box(
        Modifier.fillMaxSize()
            .frostedAuto(Color(0xCC000000), Color(0xF2101014))
            .imePadding()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0x33FFFFFF),
                        unfocusedContainerColor = Color(0x33FFFFFF),
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedPlaceholderColor = Color(0xFFB0B0B5),
                        unfocusedPlaceholderColor = Color(0xFFB0B0B5)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        val first = results.firstOrNull()
                        if (first != null) onLaunch(first)
                    }),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Text(
                                "X",
                                color = Color(0xFFB0B0B5),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { query = "" }.padding(12.dp)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
                Text(
                    "Cancel",
                    color = Color(0xFF0A84FF),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { onClose() }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            if (results.isEmpty()) {
                Text(
                    "Koi app nahi mila",
                    color = Color(0xFFB0B0B5),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = Modifier.fillMaxSize()) {
                    items(results) { app ->
                        Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                            AppIcon(app = app, onClick = { onLaunch(app) })
                        }
                    }
                }
            }
        }
    }
}
