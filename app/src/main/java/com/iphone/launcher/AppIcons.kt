package com.iphone.launcher

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// Icon ki roundness yahan se badlo: bada number = zyada gol
val IconShape = RoundedCornerShape(percent = 28)

// MainActivity me font file milne par yahan set hota hai
var AppFont: FontFamily = FontFamily.Default

val labelStyle: TextStyle
    get() = TextStyle(
        color = Color.White,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        fontFamily = AppFont,
        shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 3f)
    )

object LaunchAnchor {
    var x = 0
    var y = 0
    var w = 0
    var h = 0
    var valid = false
}

fun iosKind(pkg: String): Int {
    val p = pkg.lowercase()
    return when {
        p.contains("dialer") -> 1
        p.contains("messaging") || p.contains("mms") -> 2
        p.contains("camera") -> 3
        p.contains("deskclock") || p.endsWith(".clock") -> 4
        p.contains("calculator") -> 5
        p == "com.android.settings" -> 6
        p == "com.google.android.youtube" -> 7
        p == "com.whatsapp" -> 8
        p == "com.android.chrome" -> 9
        p.contains("photos") || p.contains("gallery") -> 10
        p == "com.google.android.gm" -> 11
        p.contains(".notes") || p == "com.google.android.keep" -> 12
        p == "com.instagram.android" -> 13
        p == "com.facebook.katana" -> 14
        p.contains("telegram") -> 15
        p == "com.spotify.music" -> 16
        p == "com.android.vending" -> 17
        p == "com.google.android.apps.maps" -> 18
        p.contains("contacts") -> 19
        p.contains("calendar") -> 20
        else -> 0
    }
}

@Composable
fun IosIcon(kind: Int, modifier: Modifier) {
    Canvas(modifier.clip(IconShape)) {
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
            7 -> {
                drawRect(Color.White)
                drawRoundRect(
                    color = Color(0xFFFF0000),
                    topLeft = Offset(w * 0.12f, h * 0.27f),
                    size = Size(w * 0.76f, h * 0.46f),
                    cornerRadius = CornerRadius(w * 0.14f)
                )
                val tri = Path().apply {
                    moveTo(w * 0.42f, h * 0.38f)
                    lineTo(w * 0.42f, h * 0.62f)
                    lineTo(w * 0.62f, h * 0.5f)
                    close()
                }
                drawPath(tri, Color.White)
            }
            8 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF5FE27A), Color(0xFF1EB854))))
                drawCircle(Color.White, w * 0.3f, c, style = Stroke(width = w * 0.06f))
                val tail = Path().apply {
                    moveTo(w * 0.22f, h * 0.78f)
                    lineTo(w * 0.27f, h * 0.6f)
                    lineTo(w * 0.4f, h * 0.72f)
                    close()
                }
                drawPath(tail, Color.White)
                val r = w * 0.11f
                drawArc(
                    color = Color.White,
                    startAngle = 80f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * 0.52f - r, h * 0.48f - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
                )
            }
            9 -> {
                drawRect(Color.White)
                val r = w * 0.38f
                val tl = Offset(c.x - r, c.y - r)
                val sz = Size(r * 2f, r * 2f)
                drawArc(Color(0xFFEA4335), 210f, 120f, true, tl, sz)
                drawArc(Color(0xFFFBBC05), 330f, 120f, true, tl, sz)
                drawArc(Color(0xFF34A853), 90f, 120f, true, tl, sz)
                drawCircle(Color.White, w * 0.19f, c)
                drawCircle(Color(0xFF4285F4), w * 0.15f, c)
            }
            10 -> {
                drawRect(Color.White)
                val petals = listOf(
                    Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
                    Color(0xFF30B0C7), Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55)
                )
                for (i in 0 until 8) {
                    rotate(i * 45f, c) {
                        drawOval(
                            color = petals[i].copy(alpha = 0.8f),
                            topLeft = Offset(w * 0.4f, h * 0.14f),
                            size = Size(w * 0.2f, h * 0.36f)
                        )
                    }
                }
            }
            11 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF4C9BFF), Color(0xFF1A6CF0))))
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.18f, h * 0.3f),
                    size = Size(w * 0.64f, h * 0.42f),
                    cornerRadius = CornerRadius(w * 0.05f)
                )
                val flap = Path().apply {
                    moveTo(w * 0.2f, h * 0.34f)
                    lineTo(w * 0.5f, h * 0.56f)
                    lineTo(w * 0.8f, h * 0.34f)
                }
                drawPath(flap, Color(0xFF1A6CF0), style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))
            }
            12 -> {
                drawRect(Color.White)
                drawRect(Color(0xFFFFCC00), Offset(0f, 0f), Size(w, h * 0.28f))
                for (i in 0 until 3) {
                    val y = h * (0.45f + 0.15f * i)
                    drawLine(Color(0xFFD1D1D6), Offset(w * 0.15f, y), Offset(w * 0.85f, y), strokeWidth = w * 0.02f)
                }
            }
            13 -> {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045)),
                        start = Offset(0f, h),
                        end = Offset(w, 0f)
                    )
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.22f, h * 0.22f),
                    size = Size(w * 0.56f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.16f),
                    style = Stroke(width = w * 0.05f)
                )
                drawCircle(Color.White, w * 0.13f, c, style = Stroke(width = w * 0.05f))
                drawCircle(Color.White, w * 0.03f, Offset(w * 0.66f, h * 0.34f))
            }
            14 -> {
                drawRect(Color(0xFF1877F2))
                drawLine(Color.White, Offset(w * 0.56f, h * 0.88f), Offset(w * 0.56f, h * 0.4f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
                drawArc(
                    color = Color.White,
                    startAngle = 180f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(w * 0.56f, h * 0.2f),
                    size = Size(w * 0.24f, h * 0.4f),
                    style = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
                )
                drawLine(Color.White, Offset(w * 0.4f, h * 0.52f), Offset(w * 0.74f, h * 0.52f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            15 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF37AEE2), Color(0xFF1E96C8))))
                val plane = Path().apply {
                    moveTo(w * 0.2f, h * 0.48f)
                    lineTo(w * 0.8f, h * 0.24f)
                    lineTo(w * 0.62f, h * 0.78f)
                    lineTo(w * 0.48f, h * 0.6f)
                    lineTo(w * 0.38f, h * 0.7f)
                    lineTo(w * 0.38f, h * 0.56f)
                    close()
                }
                drawPath(plane, Color.White)
            }
            16 -> {
                drawRect(Color(0xFF1ED760))
                for (i in 0 until 3) {
                    val r = w * (0.15f + 0.08f * i)
                    drawArc(
                        color = Color.Black,
                        startAngle = 215f,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = Offset(w * 0.5f - r, h * 0.66f - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                    )
                }
            }
            17 -> {
                drawRect(Color.White)
                val a = Offset(w * 0.28f, h * 0.22f)
                val b = Offset(w * 0.28f, h * 0.78f)
                val tip = Offset(w * 0.78f, h * 0.5f)
                val mid = Offset(w * 0.5f, h * 0.5f)
                fun tri(p1: Offset, p2: Offset, p3: Offset, col: Color) {
                    val path = Path().apply {
                        moveTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }
                    drawPath(path, col)
                }
                tri(a, b, mid, Color(0xFF00A0FF))
                tri(a, tip, mid, Color(0xFF00E676))
                tri(b, tip, mid, Color(0xFFFF3D00))
                tri(Offset(w * 0.62f, h * 0.4f), Offset(w * 0.62f, h * 0.6f), tip, Color(0xFFFFD600))
            }
            18 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFEFF3F8), Color(0xFFD6E4F0))))
                drawLine(Color(0xFFFFC107), Offset(0f, h * 0.8f), Offset(w, h * 0.3f), strokeWidth = w * 0.08f)
                drawLine(Color.White, Offset(0f, h * 0.35f), Offset(w, h * 0.7f), strokeWidth = w * 0.06f)
                drawCircle(Color(0xFFEA4335), w * 0.16f, Offset(w * 0.5f, h * 0.4f))
                val pin = Path().apply {
                    moveTo(w * 0.36f, h * 0.48f)
                    lineTo(w * 0.64f, h * 0.48f)
                    lineTo(w * 0.5f, h * 0.78f)
                    close()
                }
                drawPath(pin, Color(0xFFEA4335))
                drawCircle(Color.White, w * 0.06f, Offset(w * 0.5f, h * 0.4f))
            }
            19 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFB0B0B5), Color(0xFF8E8E93))))
                drawCircle(Color.White, w * 0.14f, Offset(w * 0.5f, h * 0.38f))
                drawArc(
                    color = Color.White,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.26f, h * 0.58f),
                    size = Size(w * 0.48f, h * 0.5f)
                )
            }
            else -> drawRect(Color.Gray)
        }
    }
}

@Composable
fun CalendarIcon(modifier: Modifier) {
    val cal = java.util.Calendar.getInstance()
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH).toString()
    val wd = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(cal.time).uppercase()
    Column(
        modifier.background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(wd, color = Color(0xFFFF3B30), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(day, color = Color.Black, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun IconImage(app: AppItem, modifier: Modifier) {
    val kind = remember(app.pkg) { iosKind(app.pkg) }
    Box(modifier.clip(IconShape)) {
        if (kind == 20) {
            CalendarIcon(Modifier.fillMaxSize())
        } else if (kind != 0) {
            IosIcon(kind, Modifier.fillMaxSize())
        } else if (app.opaque) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F7)).padding(6.dp)) {
                Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent))
            )
        )
        Box(Modifier.matchParentSize().border(0.5.dp, Color.White.copy(alpha = 0.2f), IconShape))
    }
}

@Composable
fun BadgeDot(count: Int, modifier: Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF3B30))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    size: Dp = 60.dp,
    showLabel: Boolean = true,
    editing: Boolean = false,
    wiggle: State<Float>? = null,
    onRemove: () -> Unit = {},
    badge: Int = 0,
    ghost: Boolean = false,
    registry: HashMap<String, Rect>? = null
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    val coordsHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }

    DisposableEffect(app.pkg, registry) {
        onDispose { registry?.remove(app.pkg) }
    }

    Box(
        Modifier.graphicsLayer {
            rotationZ = wiggle?.value ?: 0f
            alpha = if (ghost) 0.25f else 1f
        }
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .onGloballyPositioned {
                    coordsHolder[0] = it
                    if (registry != null) {
                        registry[app.pkg] = it.boundsInWindow()
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = {
                        val cc = coordsHolder[0]
                        if (cc != null && cc.isAttached) {
                            val b = cc.boundsInWindow()
                            LaunchAnchor.x = b.left.toInt()
                            LaunchAnchor.y = b.top.toInt()
                            LaunchAnchor.w = b.width.toInt()
                            LaunchAnchor.h = b.height.toInt()
                            LaunchAnchor.valid = true
                        }
                        onClick()
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconImage(app, Modifier.size(size))
            if (showLabel) {
                Spacer(Modifier.height(4.dp))
                Text(app.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (badge > 0 && !editing) {
            BadgeDot(badge, Modifier.align(Alignment.TopEnd))
        }
        if (editing) {
            Box(
                Modifier.align(Alignment.TopStart)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderIcon(
    name: String,
    apps: List<AppItem>,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    size: Dp = 60.dp,
    badge: Int = 0
) {
    val haptic = LocalHapticFeedback.current
    Box {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(size)
                    .clip(IconShape)
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
        if (badge > 0) {
            BadgeDot(badge, Modifier.align(Alignment.TopEnd))
        }
    }
}
