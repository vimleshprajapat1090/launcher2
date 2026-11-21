package com.iphone.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val labelStyle = TextStyle(
    color = Color.White,
    fontSize = 11.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 3f)
)

fun iosKind(pkg: String): Int {
    val p = pkg.lowercase()
    return when {
        p.contains("dialer") -> 1
        p.contains("messaging") || p.contains("mms") -> 2
        p.contains("camera") -> 3
        p.contains("deskclock") || p.endsWith(".clock") -> 4
        p.contains("calculator") -> 5
        p == "com.android.settings" -> 6
        else -> 0
    }
}

@Composable
fun IosIcon(kind: Int, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(percent = 22))) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h / 2f)
        when (kind) {
            1 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF65E57A), Color(0xFF21B83B))))
                val r = w * 0.27f
                drawArc(
                    color = Color.White,
                    startAngle = 80f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * 0.58f - r, h * 0.42f - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = w * 0.17f, cap = StrokeCap.Round)
                )
            }
            2 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF65E57A), Color(0xFF21B83B))))
                drawOval(Color.White, Offset(w * 0.15f, h * 0.2f), Size(w * 0.7f, h * 0.52f))
                val tail = Path().apply {
                    moveTo(w * 0.28f, h * 0.62f)
                    lineTo(w * 0.2f, h * 0.8f)
                    lineTo(w * 0.45f, h * 0.68f)
                    close()
                }
                drawPath(tail, Color.White)
            }
            3 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFD1D1D6), Color(0xFF8E8E93))))
                drawRoundRect(
                    color = Color(0xFF2C2C2E),
                    topLeft = Offset(w * 0.14f, h * 0.3f),
                    size = Size(w * 0.72f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.08f)
                )
                drawRoundRect(
                    color = Color(0xFF2C2C2E),
                    topLeft = Offset(w * 0.32f, h * 0.22f),
                    size = Size(w * 0.22f, h * 0.12f),
                    cornerRadius = CornerRadius(w * 0.03f)
                )
                drawCircle(Color(0xFF8E8E93), w * 0.17f, Offset(w * 0.5f, h * 0.54f))
                drawCircle(Color(0xFF1C1C1E), w * 0.11f, Offset(w * 0.5f, h * 0.54f))
            }
            4 -> {
                drawRect(Color(0xFF1C1C1E))
                drawCircle(Color.White, w * 0.38f, c)
                for (i in 0 until 12) {
                    rotate(i * 30f, c) {
                        drawLine(
                            Color.Black,
                            Offset(w * 0.5f, h * 0.14f),
                            Offset(w * 0.5f, h * 0.19f),
                            strokeWidth = w * 0.015f
                        )
                    }
                }
                drawLine(Color.Black, c, Offset(w * 0.5f, h * 0.28f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
                drawLine(Color(0xFFFF9500), c, Offset(w * 0.66f, h * 0.58f), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
            }
            5 -> {
                drawRect(Color(0xFF1C1C1E))
                drawRoundRect(
                    color = Color(0xFF3A3A3C),
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.76f, h * 0.2f),
                    cornerRadius = CornerRadius(w * 0.03f)
                )
                for (r in 0 until 4) {
                    for (cc in 0 until 4) {
                        val col = if (cc == 3) Color(0xFFFF9F0A) else if (r == 0) Color(0xFF8E8E93) else Color(0xFF505052)
                        drawCircle(col, w * 0.075f, Offset(w * (0.2f + 0.2f * cc), h * (0.43f + 0.14f * r)))
                    }
                }
            }
            6 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFB8B8BD), Color(0xFF6E6E73))))
                val dark = Color(0xFF3A3A3C)
                for (i in 0 until 8) {
                    rotate(i * 45f, c) {
                        drawRoundRect(
                            color = dark,
                            topLeft = Offset(w * 0.45f, h * 0.14f),
                            size = Size(w * 0.1f, h * 0.2f),
                            cornerRadius = CornerRadius(w * 0.02f)
                        )
                    }
                }
                drawCircle(dark, w * 0.24f, c)
                drawCircle(Color(0xFF8E8E93), w * 0.1f, c)
            }
            else -> drawRect(Color.Gray)
        }
    }
}

@Composable
fun IconImage(app: AppItem, modifier: Modifier) {
    val kind = remember(app.pkg) { iosKind(app.pkg) }
    if (kind != 0) {
        IosIcon(kind, modifier)
    } else {
        Box(modifier.clip(RoundedCornerShape(percent = 22)).background(Color(0xFFF2F2F7))) {
            Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    size: Dp = 60.dp,
    showLabel: Boolean = true
) {
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconImage(app, Modifier.size(size))
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(app.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderIcon(name: String, apps: List<AppItem>, onClick: () -> Unit, size: Dp = 60.dp) {
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = {})
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(size)
                .clip(RoundedCornerShape(percent = 22))
                .background(Color.White.copy(alpha = 0.3f))
                .padding(size * 0.08f)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in 0 until 3) {
                            val a = apps.getOrNull(r * 3 + c)
                            Box(Modifier.size(size * 0.24f)) {
                                if (a != null) IconImage(a, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
