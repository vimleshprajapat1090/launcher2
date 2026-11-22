@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.iphone.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun LibraryPage(
    apps: List<AppItem>,
    hidden: List<AppItem>,
    onSearch: () -> Unit,
    onOpen: (String, List<AppItem>) -> Unit
) {
    val groups = LIBRARY_ORDER.mapNotNull { cat ->
        val list = apps.filter { it.category == cat }
        if (list.isEmpty()) null else cat to list
    }
    val all = if (hidden.isEmpty()) groups else groups + ("Hidden" to hidden)

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Box(
            Modifier.fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.25f))
                .clickable { onSearch() }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("App Library", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
        }
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
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color.White.copy(alpha = 0.22f))
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
                                        val a = list.getOrNull(r * 2 + c)
                                        Box(Modifier.weight(1f).aspectRatio(1f).padding(4.dp)) {
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
                    Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                        AppIcon(app = app, onClick = { onLaunch(app) }, onLongClick = { onLong(app) })
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

@Composable
fun AppActionDialog(
    app: AppItem,
    inDock: Boolean,
    inFolder: Boolean,
    inNoHome: Boolean,
    isHidden: Boolean,
    folderNames: List<String>,
    onDock: () -> Unit,
    onAddToFolder: (String) -> Unit,
    onRemoveFromFolder: () -> Unit,
    onToggleHome: () -> Unit,
    onToggleHidden: () -> Unit,
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
                MenuItem(if (inDock) "Dock se hatao" else "Dock me daalo", onDock)
                MenuItem("Folder me daalo") { step = 1 }
                if (inFolder) MenuItem("Folder se bahar nikalo", onRemoveFromFolder)
                MenuItem(
                    if (inNoHome) "Home Screen pe wapas lao" else "Home Screen se hatao (Library me rahega)",
                    onToggleHome
                )
                MenuItem(if (isHidden) "Hidden se wapas lao" else "App chhupao (Hide)", onToggleHidden)
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
                        AppIcon(app = app, onClick = { onLaunch(app) })
                    }
                }
            }
        }
    }
}
