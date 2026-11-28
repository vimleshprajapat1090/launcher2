package com.iphone.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ---------------------------------------------------------------
// SEARCH + HOME PACK
// Screens.kt me SearchOverlay, HomeMenuDialog, HideManagerDialog ke
// naam ke aage "Old" laga do. Baaki code me kuch nahi badalna.
// ---------------------------------------------------------------

private val AccentColors = listOf(
    Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFF30D158),
    Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFF64D2FF)
)

// iOS jaisa on/off switch
@Composable
fun SwitchPill(on: Boolean) {
    Box(
        Modifier.width(46.dp).height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) Color(0xFF30D158) else Color(0xFF636366))
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun SearchHomeRow(
    title: String,
    showDivider: Boolean,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            trailing()
        }
        if (showDivider) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(start = 14.dp)
                    .height(0.5.dp)
                    .background(Color(0x33FFFFFF))
            )
        }
    }
}

// ---------------------------------------------------------------
// QUICK SETTINGS MENU (search button long-press)
// ---------------------------------------------------------------
@Composable
fun HomeMenuDialog(sections: List<Pair<String, List<MenuEntry>>>, onDismiss: () -> Unit) {
    var open by remember { mutableStateOf(-1) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 600.dp)
                .glass(RoundedCornerShape(30.dp), Color(0x66000000), Color(0xF21C1C1E))
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            if (open < 0 || open >= sections.size) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Quick settings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Done",
                        color = Color(0xFF0A84FF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDismiss() }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x2EFFFFFF))) {
                    sections.forEachIndexed { i, sec ->
                        SearchHomeRow(
                            title = sec.first,
                            showDivider = i < sections.size - 1,
                            leading = {
                                Box(
                                    Modifier.size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(AccentColors[i % AccentColors.size]),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        sec.first.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            },
                            trailing = {
                                Text(
                                    sec.second.size.toString() + "  \u203A",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 15.sp
                                )
                            },
                            onClick = { open = i }
                        )
                    }
                }
            } else {
                val sec = sections[open]
                Text(
                    "\u2039  Quick settings",
                    color = Color(0xFF0A84FF),
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { open = -1 }.padding(bottom = 8.dp)
                )
                Text(sec.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x2EFFFFFF))) {
                    sec.second.forEachIndexed { i, e ->
                        val isOn = e.label.endsWith(": ON")
                        val isOff = e.label.endsWith(": OFF")
                        val title = if (isOn || isOff) e.label.substringBeforeLast(":") else e.label
                        SearchHomeRow(
                            title = title,
                            showDivider = i < sec.second.size - 1,
                            trailing = {
                                if (isOn || isOff) SwitchPill(isOn)
                            },
                            onClick = {
                                e.action()
                                if (!e.keepOpen) onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// APPS CHHUPAO / DIKHAO
// ---------------------------------------------------------------
@Composable
fun HideManagerDialog(
    apps: List<AppItem>,
    hidden: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val list = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .glass(RoundedCornerShape(30.dp), Color(0x66000000), Color(0xF21C1C1E))
                .padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Apps chhupao / dikhao",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Done",
                    color = Color(0xFF0A84FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDismiss() }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                )
            }
            Text(
                "Switch ON wale apps home, library aur search se gayab. Abhi hidden: " + hidden.size,
                color = Color(0xFFB0B0B5),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            Box(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), Color(0x33000000), Color(0x44FFFFFF))
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("App dhundo") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedPlaceholderColor = Color(0xFFB0B0B5),
                        unfocusedPlaceholderColor = Color(0xFFB0B0B5)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(list) { app ->
                    val on = app.pkg in hidden
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onToggle(app.pkg) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconImage(app, Modifier.size(36.dp))
                        Text(
                            app.label,
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        SwitchPill(on)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// SEARCH
// ---------------------------------------------------------------
private fun searchScore(app: AppItem, q: String): Int {
    val l = app.label.lowercase()
    if (l == q) return 0
    if (l.startsWith(q)) return 1
    val words = l.split(" ", "-", ".", "_").filter { it.isNotEmpty() }
    if (words.any { it.startsWith(q) }) return 2
    // initials: "Google Maps" -> "gm"
    val initials = words.map { it.first() }.joinToString("")
    if (initials.startsWith(q)) return 3
    if (l.contains(q)) return 4
    if (app.category.lowercase().contains(q)) return 5
    if (app.pkg.lowercase().contains(q)) return 6
    return -1
}

private fun openWebSearch(context: Context, q: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    } catch (e: Exception) {
        Toast.makeText(context, "Browser nahi khula", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SearchOverlay(apps: List<AppItem>, cols: Int, onClose: () -> Unit, onLaunch: (AppItem) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler(enabled = true) { onClose() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = query.trim().lowercase()
    val results: List<AppItem> = if (q.isEmpty()) {
        apps
    } else {
        apps.map { it to searchScore(it, q) }
            .filter { it.second >= 0 }
            .sortedWith(compareBy<Pair<AppItem, Int>>({ it.second }, { it.first.label.lowercase() }))
            .map { it.first }
    }

    Box(
        Modifier.fillMaxSize()
            .frostedAuto(Color(0xCC000000), Color(0xF2101014))
            .imePadding()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f)
                        .glass(RoundedCornerShape(18.dp), Color(0x33000000), Color(0x44FFFFFF))
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search apps") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedPlaceholderColor = Color(0xFFB0B0B5),
                            unfocusedPlaceholderColor = Color(0xFFB0B0B5)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val first = results.firstOrNull()
                            if (q.isNotEmpty() && first != null) {
                                onLaunch(first)
                            } else if (q.isNotEmpty()) {
                                onClose()
                                openWebSearch(context, query.trim())
                            }
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
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                Text(
                    "Cancel",
                    color = Color(0xFF0A84FF),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { onClose() }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(14.dp))

            LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = Modifier.fillMaxSize()) {
                if (q.isNotEmpty() && results.isNotEmpty()) {
                    val top = results.first()
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "Top Hit",
                                color = Color(0xFFB0B0B5),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth()
                                    .glass(RoundedCornerShape(22.dp), Color(0x33000000), Color(0x44FFFFFF))
                                    .clickable { onLaunch(top) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconImage(top, Modifier.size(54.dp))
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(
                                        top.label,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(top.category, color = Color(0xFF8E8E93), fontSize = 12.sp, maxLines = 1)
                                }
                                Text("Open", color = Color(0xFF0A84FF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            if (results.size > 1) {
                                Text(
                                    "Apps",
                                    color = Color(0xFFB0B0B5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }
                    items(results.drop(1)) { app ->
                        Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                            AppIcon(app = app, onClick = { onLaunch(app) })
                        }
                    }
                } else if (q.isEmpty()) {
                    items(results) { app ->
                        Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                            AppIcon(app = app, onClick = { onLaunch(app) })
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Koi app nahi mila",
                            color = Color(0xFFB0B0B5),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (q.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(top = 12.dp)
                                .glass(RoundedCornerShape(18.dp), Color(0x33000000), Color(0x44FFFFFF))
                                .clickable {
                                    onClose()
                                    openWebSearch(context, query.trim())
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Google par \"" + query.trim() + "\" search karo",
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text("\u203A", color = Color(0xFF8E8E93), fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
