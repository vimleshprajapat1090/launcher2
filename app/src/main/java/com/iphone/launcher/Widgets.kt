@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.iphone.launcher

import android.app.ActivityManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
    "memory" to "RAM (memory)"
)

fun isLargeWidget(id: String): Boolean = id == "calendar" || id == "notes"

// Wide widget poori row leta hai par kam unchaai ka hota hai
fun isWideWidget(id: String): Boolean = id == "week"

fun isFullRow(id: String): Boolean = isLargeWidget(id) || isWideWidget(id)

fun widgetHeight(id: String): Dp = when {
    isLargeWidget(id) -> 260.dp
    isWideWidget(id) -> 128.dp
    else -> 176.dp
}

fun widgetRows(ids: List<String>): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var pending: String? = null
    for (id in ids) {
        val p = pending
        if (isFullRow(id)) {
            if (p != null) {
                rows.add(listOf(p))
                pending = null
            }
            rows.add(listOf(id))
        } else if (p == null) {
            pending = id
        } else {
            rows.add(listOf(p, id))
            pending = null
        }
    }
    val last = pending
    if (last != null) rows.add(listOf(last))
    return rows
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
fun WidgetRow(
    ids: List<String>,
    data: WidgetData,
    weather: WeatherNow?,
    battery: Int,
    media: MediaWatcher,
    notes: String,
    onNotes: (String) -> Unit,
    onWeatherClick: () -> Unit,
    editing: Boolean = false,
    drag: WidgetDragState? = null,
    onLongPress: () -> Unit = {}
) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ids.forEach { id ->
            DisposableEffect(id, editing) {
                onDispose { drag?.bounds?.remove(id) }
            }
            WidgetCard(
                Modifier.weight(1f)
                    .height(widgetHeight(id))
                    .graphicsLayer { alpha = if (drag != null && drag.key == id) 0.3f else 1f }
                    .onGloballyPositioned {
                        if (editing && drag != null) drag.bounds[id] = it.boundsInWindow()
                    }
            ) {
                // Tap = us widget ka app khule, long-press = home menu (edit mode me kuch nahi)
                Box(
                    Modifier.fillMaxSize().then(
                        if (!editing) {
                            Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { openWidgetTarget(ctx, id) },
                                onLongClick = onLongPress
                            )
                        } else {
                            Modifier
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
            }
        }
    }
}

@Composable
fun HomeWidgets(
    selected: List<String>,
    weather: WeatherNow?,
    battery: Int,
    media: MediaWatcher,
    onWeatherClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val data = rememberWidgetData()
    val ids = selected.filter { !isFullRow(it) }.take(2)
    Box(
        Modifier.fillMaxWidth()
            .height(180.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onLongPress
            )
    ) {
        WidgetRow(ids, data, weather, battery, media, "", {}, onWeatherClick, onLongPress = onLongPress)
    }
}

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
    onLongPress: () -> Unit
) {
    val data = rememberWidgetData()
    val origin = remember { FloatArray(2) }
    val reorderRef = rememberUpdatedState(onReorder)
    val rows = widgetRows(selected)

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned {
                val p = it.positionInWindow()
                origin[0] = p.x
                origin[1] = p.y
            }
            .pointerInput(editing) {
                if (editing) {
                    dragGestures(
                        findKey = { p ->
                            val w = Offset(p.x + origin[0], p.y + origin[1])
                            var hit: String? = null
                            for (entry in drag.bounds.entries) {
                                if (entry.value.contains(w)) {
                                    hit = entry.key
                                    break
                                }
                            }
                            hit
                        },
                        onStart = { k, p ->
                            drag.pos = Offset(p.x + origin[0], p.y + origin[1])
                            drag.key = k
                        },
                        onMove = { p -> drag.pos = Offset(p.x + origin[0], p.y + origin[1]) },
                        onEnd = {
                            val k = drag.key
                            drag.key = null
                            if (k != null) reorderRef.value(k, drag.pos)
                        }
                    )
                }
            }
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onLongPress
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(data.full, color = Color(0xFFB0B0B5), fontSize = 14.sp)
                }
                if (editing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color(0x55FFFFFF))
                                .clickable { onPick() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("+ Widgets", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0A84FF))
                                .clickable { onDoneEdit() }
                                .padding(horizontal = 22.dp, vertical = 8.dp)
                        ) {
                            Text("Done", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (editing) {
                Text(
                    "Widget ko pakad ke dusre widget ke upar chhodo, jagah badal jayegi",
                    color = Color(0xFFB0B0B5),
                    fontSize = 12.sp
                )
            }
            if (rows.isEmpty()) {
                Text(
                    "Koi widget nahi hai. Yahan long-press karke Widgets chuno.",
                    color = Color(0xFFB0B0B5),
                    fontSize = 14.sp
                )
            }
            rows.forEach { rowIds ->
                WidgetRow(rowIds, data, weather, battery, media, notes, onNotes, onWeatherClick, editing, drag, onLongPress)
            }
        }
        val k = drag.key
        if (k != null) {
            val label = WIDGET_CATALOG.firstOrNull { it.first == k }?.second ?: k
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (drag.pos.x - origin[0]).roundToInt() - 90,
                            (drag.pos.y - origin[1]).roundToInt() - 26
                        )
                    }
                    .glass(RoundedCornerShape(20.dp), Color(0x80000000), Color(0xCC2C2C2E))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
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
