@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.iphone.launcher

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.AlarmClock
import android.provider.Settings
import android.os.StatFs
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

val WIDGET_CATALOG = listOf(
    "clock" to "Clock",
    "date" to "Date",
    "calendar" to "Calendar (poora mahina)",
    "weather" to "Weather",
    "battery" to "Battery",
    "storage" to "Storage",
    "notes" to "Notes (likh sakte ho)",
    "music" to "Music",
    "week" to "Week (hafta ek nazar me)",
    "memory" to "RAM (memory)",
    "screentime" to "Screen time (aaj ka)"
)

// ---------------------------------------------------------------
// FREE-FORM WIDGETS
// Har widget kisi bhi page (Today ya home) par kahin bhi rakh sakte ho
// aur kone ke button se jitna chaho utna bada / chhota kar sakte ho.
// ---------------------------------------------------------------

fun isLargeWidget(id: String): Boolean = id == "calendar" || id == "notes"

fun isWideWidget(id: String): Boolean = id == "week"

// page: 0 = Today, 1.. = home pages. x, y, w, h dp me.
class WPlace(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float)

object WidgetStore {
    val places = mutableStateMapOf<String, WPlace>()
    val selected = mutableStateOf<String?>(null)
    private var loaded = false

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val raw = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("wplaces", "") ?: ""
            raw.split(";").forEach { rec ->
                val f = rec.split("|")
                if (f.size == 6) {
                    val pg = f[1].toIntOrNull()
                    val x = f[2].toFloatOrNull()
                    val y = f[3].toFloatOrNull()
                    val w = f[4].toFloatOrNull()
                    val h = f[5].toFloatOrNull()
                    if (pg != null && x != null && y != null && w != null && h != null) {
                        places[f[0]] = WPlace(pg, x, y, w, h)
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    fun save(ctx: Context) {
        try {
            val raw = places.entries.joinToString(";") { e ->
                val p = e.value
                e.key + "|" + p.page + "|" + p.x + "|" + p.y + "|" + p.w + "|" + p.h
            }
            ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putString("wplaces", raw).apply()
        } catch (e: Exception) {
        }
    }
}

// Jin widgets ki jagah abhi tak tay nahi hui, unhe Today page pe saaf tarah se laga do
private fun defaultPlaces(ids: List<String>, availW: Float, startY: Float): Map<String, WPlace> {
    val out = HashMap<String, WPlace>()
    val margin = 16f
    val gap = 12f
    val full = (availW - margin * 2f).coerceAtLeast(120f)
    val half = ((full - gap) / 2f).coerceAtLeast(100f)
    var y = startY
    var col = 0
    for (id in ids) {
        if (isLargeWidget(id) || isWideWidget(id)) {
            if (col == 1) {
                y += 150f + gap
                col = 0
            }
            val h = if (isWideWidget(id)) 128f else 260f
            out[id] = WPlace(0, margin, y, full, h)
            y += h + gap
        } else {
            val x = margin + col * (half + gap)
            out[id] = WPlace(0, x, y, half, 150f)
            if (col == 0) {
                col = 1
            } else {
                col = 0
                y += 150f + gap
            }
        }
    }
    return out
}

@Composable
private fun BarPill(text: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WidgetLayer(
    page: Int,
    enabled: List<String>,
    weather: WeatherNow?,
    battery: Int,
    media: MediaWatcher,
    notes: String,
    onNotes: (String) -> Unit,
    onWeatherClick: () -> Unit,
    onRemove: (String) -> Unit,
    pageCount: Int
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    remember { WidgetStore.ensureLoaded(ctx) }
    val data = rememberWidgetData()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availW = maxWidth.value
        val availH = maxHeight.value
        val places = WidgetStore.places

        val lacking = enabled.filter { places[it] == null }
        var startY = 96f
        for (id in enabled) {
            val p = places[id]
            if (p != null && p.page == 0) startY = maxOf(startY, p.y + p.h + 12f)
        }
        val defaults: Map<String, WPlace> =
            if (page == 0) defaultPlaces(lacking, availW, startY) else emptyMap()

        val shown = ArrayList<Pair<String, WPlace>>()
        for (id in enabled) {
            val st = places[id]
            if (st != null) {
                if (minOf(st.page, pageCount) == page) shown.add(Pair(id, st))
            } else if (page == 0) {
                val d = defaults[id]
                if (d != null) shown.add(Pair(id, d))
            }
        }

        shown.forEach { entry ->
            val id = entry.first
            val place = entry.second
            key(id) {
                val sel = WidgetStore.selected.value == id
                val cur = rememberUpdatedState(place)
                Box(
                    Modifier.offset(x = place.x.dp, y = place.y.dp)
                        .size(width = place.w.dp, height = place.h.dp)
                ) {
                    WidgetCard(
                        Modifier.fillMaxSize()
                            .then(
                                if (sel) {
                                    Modifier.pointerInput(id) {
                                        detectDragGestures(
                                            onDragEnd = { WidgetStore.save(ctx) },
                                            onDragCancel = { WidgetStore.save(ctx) }
                                        ) { change, drag ->
                                            change.consume()
                                            val p = cur.value
                                            val nx = (p.x + drag.x / density).coerceIn(0f, maxOf(0f, availW - p.w))
                                            val ny = (p.y + drag.y / density).coerceIn(0f, maxOf(0f, availH - p.h))
                                            WidgetStore.places[id] = WPlace(p.page, nx, ny, p.w, p.h)
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (WidgetStore.selected.value != id) openWidgetTarget(ctx, id)
                                },
                                onLongClick = {
                                    for ((k, v) in defaults) {
                                        if (WidgetStore.places[k] == null) WidgetStore.places[k] = v
                                    }
                                    WidgetStore.selected.value = id
                                    WidgetStore.save(ctx)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                    ) {
                        WidgetBody(
                            id = id,
                            data = data,
                            weather = weather,
                            battery = battery,
                            media = media,
                            notes = notes,
                            onNotes = onNotes,
                            onWeatherClick = onWeatherClick
                        )
                    }
                    if (sel) {
                        Box(
                            Modifier.fillMaxSize().border(2.dp, Color(0xFF0A84FF), RoundedCornerShape(26.dp))
                        )
                        Box(
                            Modifier.align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(44.dp)
                                .pointerInput(id) {
                                    detectDragGestures(
                                        onDragEnd = { WidgetStore.save(ctx) },
                                        onDragCancel = { WidgetStore.save(ctx) }
                                    ) { change, drag ->
                                        change.consume()
                                        val p = cur.value
                                        val nw = (p.w + drag.x / density).coerceIn(96f, maxOf(96f, availW - p.x))
                                        val nh = (p.h + drag.y / density).coerceIn(80f, maxOf(80f, availH - p.y))
                                        WidgetStore.places[id] = WPlace(p.page, p.x, p.y, nw, nh)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF0A84FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u25E2", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // Chuna hua widget: page badlo / hatao / done
        val selId = WidgetStore.selected.value
        val sp = shown.firstOrNull { it.first == selId }?.second
        if (selId != null && sp != null) {
            val barY = if (sp.y >= 52f) sp.y - 46f else sp.y + sp.h + 6f
            val barX = sp.x.coerceIn(6f, maxOf(6f, availW - 300f))
            val move: (Int) -> Unit = { dir ->
                val np = (page + dir).coerceIn(0, pageCount)
                if (np != page) {
                    WidgetStore.places[selId] = WPlace(np, sp.x, sp.y, sp.w, sp.h)
                    WidgetStore.save(ctx)
                    Toast.makeText(
                        ctx,
                        if (np == 0) "Today page par bheja" else "Home page " + np + " par bheja",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            Row(
                Modifier.offset(x = barX.dp, y = barY.dp)
                    .glass(RoundedCornerShape(22.dp), Color(0x99000000), Color(0xEE1C1C1E))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarPill("\u25C0", Color(0x44FFFFFF)) { move(-1) }
                Text(
                    if (page == 0) "Today" else "Page " + page,
                    color = Color.White,
                    fontSize = 12.sp
                )
                BarPill("\u25B6", Color(0x44FFFFFF)) { move(1) }
                BarPill("Hatao", Color(0xFFFF453A)) {
                    WidgetStore.places.remove(selId)
                    WidgetStore.selected.value = null
                    WidgetStore.save(ctx)
                    onRemove(selId)
                }
                BarPill("Done", Color(0xFF0A84FF)) {
                    WidgetStore.selected.value = null
                }
            }
        }
    }
}

// Today page: upar title, neeche free-form widgets
@Composable
fun TodayPage(
    selected: List<String>,
    weather: WeatherNow?,
    battery: Int,
    media: MediaWatcher,
    notes: String,
    onNotes: (String) -> Unit,
    editing: Boolean,
    drag: WidgetDragState,
    onReorder: (String, Offset) -> Unit,
    onDoneEdit: () -> Unit,
    onPick: () -> Unit,
    onWeatherClick: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: (String) -> Unit = {},
    pageCount: Int = 1
) {
    val data = rememberWidgetData()
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Today", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(data.full, color = Color(0xFFB0B0B5), fontSize = 14.sp)
            }
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Color(0x55FFFFFF))
                    .clickable { onPick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("+ Widget", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (selected.isEmpty()) {
            Text(
                "Koi widget nahi hai. Upar + Widget dabao.",
                color = Color(0xFFB0B0B5),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        WidgetLayer(
            page = 0,
            enabled = selected,
            weather = weather,
            battery = battery,
            media = media,
            notes = notes,
            onNotes = onNotes,
            onWeatherClick = onWeatherClick,
            onRemove = onRemove,
            pageCount = pageCount
        )
    }
}

// Purana home strip ab use nahi hota (widgets seedha home pages pe rakhe jaate hain)
@Composable
fun HomeWidgets(
    selected: List<String>,
    weather: WeatherNow?,
    battery: Int,
    media: MediaWatcher,
    onWeatherClick: () -> Unit,
    onLongPress: () -> Unit
) {
}

@Composable
fun WidgetCard(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.glass(RoundedCornerShape(26.dp), Color(0x40000000), Color(0x66000000))) {
        content()
    }
}

class WidgetData(
    val time: String,
    val ampm: String,
    val weekday: String,
    val dayNum: String,
    val month: String,
    val full: String
)

@Composable
fun rememberWidgetData(): WidgetData {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(10_000)
            value = System.currentTimeMillis()
        }
    }
    val date = Date(now)
    val loc = Locale.getDefault()
    return WidgetData(
        time = SimpleDateFormat(if (is24) "H:mm" else "h:mm", loc).format(date),
        ampm = if (is24) "" else SimpleDateFormat("a", loc).format(date).uppercase(),
        weekday = SimpleDateFormat("EEEE", loc).format(date),
        dayNum = SimpleDateFormat("d", loc).format(date),
        month = SimpleDateFormat("MMMM", loc).format(date),
        full = SimpleDateFormat("EEEE, d MMMM", loc).format(date)
    )
}

@Composable
fun WidgetBody(
    id: String,
    data: WidgetData,
    weather: WeatherNow?,
    battery: Int,
    media: MediaWatcher,
    notes: String,
    onNotes: (String) -> Unit,
    onWeatherClick: () -> Unit
) {
    when (id) {
        "clock" -> {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(data.ampm, color = Color(0xFFFF9F0A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(data.time, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Light)
                Text(data.weekday, color = Color(0xFFB0B0B5), fontSize = 14.sp)
            }
        }
        "date" -> {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(data.weekday.uppercase(), color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(data.dayNum, color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Light)
                Text(data.month, color = Color(0xFFB0B0B5), fontSize = 14.sp)
            }
        }
        "calendar" -> CalendarBody()
        "weather" -> {
            if (weather != null) {
                Column(
                    Modifier.fillMaxSize().clickable { onWeatherClick() }.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(weather.city.uppercase(), color = Color(0xFF64D2FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(weather.temp.toString() + "\u00B0", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
                    Column {
                        Text(weather.desc, color = Color.White, fontSize = 14.sp)
                        Text(
                            "H:" + weather.high + "\u00B0  L:" + weather.low + "\u00B0",
                            color = Color(0xFFB0B0B5),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().clickable { onWeatherClick() }.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("WEATHER", color = Color(0xFF64D2FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("--", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
                    Text("Sheher set karne ke liye tap karo", color = Color(0xFFB0B0B5), fontSize = 13.sp)
                }
            }
        }
        "battery" -> {
            val pct = battery.coerceIn(0, 100)
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("BATTERY", color = Color(0xFF30D158), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (battery >= 0) battery.toString() + "%" else "--",
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Light
                )
                BarMeter(pct / 100f, if (pct <= 20) Color(0xFFFF453A) else Color(0xFF30D158))
            }
        }
        "storage" -> StorageBody()
        "week" -> WeekBody()
        "memory" -> MemoryBody()
        "screentime" -> ScreenTimeBody()
        "notes" -> {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("NOTES", color = Color(0xFFFFD60A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = notes,
                    onValueChange = onNotes,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    decorationBox = { inner ->
                        if (notes.isEmpty()) {
                            Text("Yahan kuch bhi likho...", color = Color(0xFF8E8E93), fontSize = 15.sp)
                        }
                        inner()
                    }
                )
            }
        }
        else -> {
            val info = media.info
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("MUSIC", color = Color(0xFFFF375F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Column {
                    Text(
                        if (info != null) info.title else "Not Playing",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info != null) {
                        Text(
                            info.artist,
                            color = Color(0xFFB0B0B5),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (info != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PlayerIcon(0) { media.prev() }
                        PlayerIcon(if (info.playing) 2 else 1) { media.playPause() }
                        PlayerIcon(3) { media.next() }
                    }
                }
            }
        }
    }
}

@Composable
fun BarMeter(fraction: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth().height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0x44FFFFFF))
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().background(color)
        )
    }
}

@Composable
fun StorageBody() {
    val text = try {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        val used = total - free
        Triple(
            String.format(Locale.getDefault(), "%.1f GB free", free / 1073741824.0),
            String.format(Locale.getDefault(), "%.0f GB total", total / 1073741824.0),
            if (total > 0) used.toFloat() / total.toFloat() else 0f
        )
    } catch (e: Exception) {
        Triple("--", "", 0f)
    }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("STORAGE", color = Color(0xFF64D2FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Column {
            Text(text.first, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Text(text.second, color = Color(0xFFB0B0B5), fontSize = 13.sp)
        }
        BarMeter(text.third, Color(0xFF0A84FF))
    }
}

@Composable
fun CalendarBody() {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    val first = cal.clone() as Calendar
    first.set(Calendar.DAY_OF_MONTH, 1)
    val startOffset = (first.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val names = DateFormatSymbols.getInstance().shortWeekdays

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(monthName.uppercase(), color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (i in 0 until 7) {
                val n = ((cal.firstDayOfWeek - 1 + i) % 7) + 1
                Text(
                    names[n].take(1).uppercase(),
                    color = Color(0xFFB0B0B5),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        for (r in 0 until 6) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until 7) {
                    val day = r * 7 + c + 1 - startOffset
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            if (day == today) {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFFF453A)))
                            }
                            Text(day.toString(), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// Widget pe tap karne par kaunsa app / settings khule
fun openWidgetTarget(context: Context, id: String) {
    try {
        val intent: Intent? = when (id) {
            "clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "date", "calendar", "week" ->
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "memory" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            "screentime" ->
                if (hasUsageAccess(context)) null
                else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            else -> null
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    } catch (e: Exception) {
    }
}

// Is hafte ke 7 din, aaj wala laal gola
@Composable
fun WeekBody() {
    val cal = Calendar.getInstance()
    val todayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    val back = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    val start = cal.clone() as Calendar
    start.add(Calendar.DAY_OF_MONTH, -back)
    val names = DateFormatSymbols.getInstance().shortWeekdays
    val title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time).uppercase()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val d = start.clone() as Calendar
                d.add(Calendar.DAY_OF_MONTH, i)
                val isToday = d.get(Calendar.YEAR) * 1000 + d.get(Calendar.DAY_OF_YEAR) == todayKey
                val wd = d.get(Calendar.DAY_OF_WEEK)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(names[wd].take(1).uppercase(), color = Color(0xFFB0B0B5), fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        if (isToday) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFFF453A)))
                        }
                        Text(
                            d.get(Calendar.DAY_OF_MONTH).toString(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

private fun readMemory(context: Context): Pair<Long, Long>? {
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        Pair(mi.availMem, mi.totalMem)
    } catch (e: Exception) {
        null
    }
}

// RAM kitni khaali hai (har 5 second me update)
@Composable
fun MemoryBody() {
    val context = LocalContext.current
    val mem by produceState<Pair<Long, Long>?>(initialValue = readMemory(context)) {
        while (true) {
            delay(5_000)
            value = readMemory(context)
        }
    }
    val m = mem
    val free = if (m != null) m.first / 1073741824.0 else 0.0
    val total = if (m != null) m.second / 1073741824.0 else 0.0
    val usedFraction = if (m != null && m.second > 0L) 1f - (m.first.toFloat() / m.second.toFloat()) else 0f
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("RAM", color = Color(0xFFBF5AF2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Column {
            Text(
                if (m != null) String.format(Locale.getDefault(), "%.1f GB free", free) else "--",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light
            )
            if (m != null) {
                Text(
                    String.format(Locale.getDefault(), "of %.1f GB", total),
                    color = Color(0xFFB0B0B5),
                    fontSize = 13.sp
                )
            }
        }
        BarMeter(usedFraction, if (usedFraction > 0.85f) Color(0xFFFF453A) else Color(0xFFBF5AF2))
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    return try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }
}

private fun todayScreenTimeMs(context: Context): Long {
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val stats = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
        var total = 0L
        for ((pkg, st) in stats) {
            if (pkg != context.packageName) total += st.totalTimeInForeground
        }
        total
    } catch (e: Exception) {
        0L
    }
}

// Aaj phone kitni der chala (Usage access permission chahiye)
@Composable
fun ScreenTimeBody() {
    val context = LocalContext.current
    val ms by produceState(initialValue = if (hasUsageAccess(context)) todayScreenTimeMs(context) else -1L) {
        while (true) {
            delay(60_000)
            value = if (hasUsageAccess(context)) todayScreenTimeMs(context) else -1L
        }
    }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("SCREEN TIME", color = Color(0xFFFF9F0A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (ms < 0L) {
            Text(
                "Tap karke Usage access allow karo",
                color = Color.White,
                fontSize = 15.sp
            )
            Text("Phir yahan aaj ka time dikhega", color = Color(0xFFB0B0B5), fontSize = 12.sp)
        } else {
            val totalMin = ms / 60000L
            val h = totalMin / 60L
            val m = totalMin % 60L
            Column {
                Text(
                    if (h > 0L) h.toString() + "h " + m.toString() + "m" else m.toString() + "m",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light
                )
                Text("aaj", color = Color(0xFFB0B0B5), fontSize = 13.sp)
            }
            BarMeter((ms.toFloat() / (8f * 3600000f)).coerceIn(0f, 1f), Color(0xFFFF9F0A))
        }
    }
}
