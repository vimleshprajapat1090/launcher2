@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.iphone.launcher

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

val WIDGET_CATALOG = listOf(
    "clock" to "Clock",
    "date" to "Date",
    "weather" to "Weather",
    "battery" to "Battery",
    "music" to "Music"
)

@Composable
fun WidgetCard(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Color(0x99000000))) {
        content()
    }
}

@Composable
fun TodayPage(
    selected: List<String>,
    weather: WeatherInfo?,
    battery: Int,
    media: MediaWatcher,
    onWeatherClick: () -> Unit,
    onLongPress: () -> Unit
) {
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
    val time = SimpleDateFormat(if (is24) "H:mm" else "h:mm", loc).format(date)
    val ampm = if (is24) "" else SimpleDateFormat("a", loc).format(date).uppercase()
    val weekday = SimpleDateFormat("EEEE", loc).format(date)
    val dayNum = SimpleDateFormat("d", loc).format(date)
    val month = SimpleDateFormat("MMMM", loc).format(date)
    val full = SimpleDateFormat("EEEE, d MMMM", loc).format(date)

    val ids = WIDGET_CATALOG.map { it.first }.filter { it in selected }

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
        Text("Today", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(full, color = Color(0xFFB0B0B5), fontSize = 14.sp)
        if (ids.isEmpty()) {
            Text(
                "Koi widget nahi hai. Yahan long-press karke Widgets chuno dabao.",
                color = Color(0xFFB0B0B5),
                fontSize = 14.sp
            )
        }
        ids.chunked(2).forEach { rowIds ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowIds.forEach { id ->
                    WidgetCard(Modifier.weight(1f).height(160.dp)) {
                        WidgetBody(
                            id = id,
                            time = time,
                            ampm = ampm,
                            weekday = weekday,
                            dayNum = dayNum,
                            month = month,
                            weather = weather,
                            battery = battery,
                            media = media,
                            onWeatherClick = onWeatherClick
                        )
                    }
                }
                if (rowIds.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun WidgetBody(
    id: String,
    time: String,
    ampm: String,
    weekday: String,
    dayNum: String,
    month: String,
    weather: WeatherInfo?,
    battery: Int,
    media: MediaWatcher,
    onWeatherClick: () -> Unit
) {
    when (id) {
        "clock" -> {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(ampm, color = Color(0xFFFF9F0A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(time, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Light)
                Text(weekday, color = Color(0xFFB0B0B5), fontSize = 14.sp)
            }
        }
        "date" -> {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(weekday.uppercase(), color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(dayNum, color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Light)
                Text(month, color = Color(0xFFB0B0B5), fontSize = 14.sp)
            }
        }
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
                Box(
                    Modifier.fillMaxWidth().height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0x44FFFFFF))
                ) {
                    Box(
                        Modifier.fillMaxWidth(pct / 100f).fillMaxHeight()
                            .background(if (pct <= 20) Color(0xFFFF453A) else Color(0xFF30D158))
                    )
                }
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
