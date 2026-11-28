package com.iphone.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------
// LOOK PACK: shiny icons + glossy badge + Liquid Glass (sab jagah)
// AppIcons.kt me IconImage aur BadgeDot, Extras.kt me glass ka naam
// purana (Old) kar do, yeh file naye wale deti hai.
// ---------------------------------------------------------------

private val IconSaturation = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.12f) })

// Icon ke upar chamak: top gloss + specular oval + neeche halki roshni
@Composable
fun IconShine(modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            // 1) upar se neeche gloss
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    0.45f to Color.White.copy(alpha = 0.04f),
                    1f to Color.Black.copy(alpha = 0.16f)
                )
            )
            // 2) upar ka chamakta oval (shishe jaisa reflection)
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0f)),
                    startY = 0f,
                    endY = h * 0.5f
                ),
                topLeft = Offset(-w * 0.1f, -h * 0.35f),
                size = Size(w * 1.2f, h * 0.85f)
            )
            // 3) neeche ka halka glow
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.12f)
                )
            )
        }
    )
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
                colorFilter = IconSaturation,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val base = Color(app.tint)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(Color.White, base, 0.35f),
                            base,
                            lerp(base, Color.Black, 0.25f)
                        )
                    )
                )
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = 0.72f
                        scaleY = 0.72f
                    }
                )
            }
        }

        IconShine(Modifier.matchParentSize())

        // glass jaisa kinara: upar chamakta, neeche halka gehra
        Box(
            Modifier.matchParentSize().border(
                0.9.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.60f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Black.copy(alpha = 0.22f)
                    )
                ),
                IconShape
            )
        )
    }
}

@Composable
fun BadgeDot(count: Int, modifier: Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFF6B5E), Color(0xFFFF3B30))))
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
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

// Liquid Glass: frosted blur + upar-left ka sheen + chamakta kinara
fun Modifier.glass(
    shape: Shape,
    tint: Color = Color(0x40000000),
    fallback: Color = Color(0x4DFFFFFF)
): Modifier =
    this.clip(shape)
        .frostedAuto(tint, fallback)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.05f),
                    Color.Black.copy(alpha = 0.12f)
                )
            )
        )
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.maxDimension * 0.9f
                )
            )
        }
        .border(
            1.2.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.40f),
                    Color.White.copy(alpha = 0.75f)
                )
            ),
            shape
        )
