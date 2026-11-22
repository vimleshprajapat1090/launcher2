package com.iphone.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun WidgetCard(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Color(0x99000000))) {
        content()
    }
}

@Composable
fun ClockWidgets() {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(10_000)
            value = System.currentTimeMillis()
        }
    }
    val date = Date(now)
    val loc = Locale.getDefault()
    val time = SimpleDateFormat("h:mm", loc).format(date)
    val ampm = SimpleDateFormat("a", loc).format(date).uppercase()
    val weekday = SimpleDateFormat("EEEE", loc).format(date)
    val dayNum = SimpleDateFormat("d", loc).format(date)
    val month = SimpleDateFormat("MMMM", loc).format(date)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WidgetCard(Modifier.weight(1f).height(160.dp)) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(ampm, color = Color(0xFFFF9F0A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(time, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Light)
                Text(weekday, color = Color(0xFFB0B0B5), fontSize = 14.sp)
            }
        }
        WidgetCard(Modifier.weight(1f).height(160.dp)) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(weekday.uppercase(), color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(dayNum, color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Light)
                Text(month, color = Color(0xFFB0B0B5), fontSize = 14.sp)
            }
        }
    }
}
