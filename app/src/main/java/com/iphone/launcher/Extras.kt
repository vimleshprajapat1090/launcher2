package com.iphone.launcher

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import java.io.File
import org.json.JSONObject

class LockAdmin : DeviceAdminReceiver()

fun isLockReady(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isAdminActive(ComponentName(context, LockAdmin::class.java))
}

fun requestLockAdmin(context: Context) {
    try {
        val admin = ComponentName(context, LockAdmin::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Double tap se screen lock karne ke liye ye permission chahiye"
            )
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Device admin screen nahi khuli", Toast.LENGTH_SHORT).show()
    }
}

fun lockScreen(context: Context) {
    if (isLockReady(context)) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
        } catch (e: Exception) {
        }
    } else {
        requestLockAdmin(context)
    }
}

class WallpaperImages(val full: ImageBitmap, val blurred: ImageBitmap)

fun saveWallpaper(context: Context, uri: Uri): Boolean {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (bmp == null) {
            false
        } else {
            File(context.filesDir, "wallpaper.jpg").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            true
        }
    } catch (e: Exception) {
        false
    }
}

fun deleteWallpaper(context: Context) {
    File(context.filesDir, "wallpaper.jpg").delete()
}

fun loadWallpaper(context: Context): WallpaperImages? {
    val f = File(context.filesDir, "wallpaper.jpg")
    if (!f.exists()) return null
    val bmp = BitmapFactory.decodeFile(f.path) ?: return null
    val w = (bmp.width / 14).coerceAtLeast(16)
    val h = (bmp.height / 14).coerceAtLeast(16)
    val small = Bitmap.createScaledBitmap(bmp, w, h, true)
    return WallpaperImages(bmp.asImageBitmap(), small.asImageBitmap())
}

private val GlassSaturation = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

fun Modifier.frosted(
    blurred: ImageBitmap?,
    screenW: Float,
    screenH: Float,
    pos: Offset,
    tint: Color
): Modifier = this.drawBehind {
    if (blurred != null && size.width > 0f && size.height > 0f) {
        val scale = maxOf(screenW / blurred.width, screenH / blurred.height)
        val offX = (screenW - blurred.width * scale) / 2f
        val offY = (screenH - blurred.height * scale) / 2f
        var sx = (pos.x - offX) / scale
        var sy = (pos.y - offY) / scale
        var sw = size.width / scale
        var sh = size.height / scale
        // lens jaisa halka zoom: glass ke andar peeche ka hissa thoda bada dikhta hai
        sx += sw * 0.04f
        sy += sh * 0.04f
        sw *= 0.92f
        sh *= 0.92f
        sx = sx.coerceIn(0f, blurred.width - 1f)
        sy = sy.coerceIn(0f, blurred.height - 1f)
        sw = sw.coerceIn(1f, blurred.width - sx)
        sh = sh.coerceIn(1f, blurred.height - sy)
        drawImage(
            image = blurred,
            srcOffset = IntOffset(sx.toInt(), sy.toInt()),
            srcSize = IntSize(sw.toInt().coerceAtLeast(1), sh.toInt().coerceAtLeast(1)),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            colorFilter = GlassSaturation,
            filterQuality = FilterQuality.High
        )
    }
    drawRect(tint)
}

class FrostInfo(val blurred: ImageBitmap?, val w: Float, val h: Float)

val LocalFrost = compositionLocalOf<FrostInfo?> { null }

fun Modifier.frostedAuto(tint: Color, fallback: Color): Modifier = composed {
    val info = LocalFrost.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos = it.positionInWindow() }
        .frosted(
            info?.blurred,
            info?.w ?: 1f,
            info?.h ?: 1f,
            pos,
            if (info?.blurred != null) tint else fallback
        )
}

fun decodeForPreview(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (e: Exception) {
        null
    }
}

fun renderWallpaper(src: Bitmap, sw: Int, sh: Int, zoom: Float, tx: Float, ty: Float): Bitmap {
    val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val s0 = maxOf(sw.toFloat() / src.width, sh.toFloat() / src.height)
    val m = Matrix()
    m.postScale(s0, s0)
    m.postTranslate((sw - src.width * s0) / 2f, (sh - src.height * s0) / 2f)
    m.postScale(zoom, zoom, sw / 2f, sh / 2f)
    m.postTranslate(tx, ty)
    canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

fun saveWallpaperBitmap(context: Context, bmp: Bitmap): Boolean {
    return try {
        File(context.filesDir, "wallpaper.jpg").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun setLockWallpaper(context: Context, bmp: Bitmap): Boolean {
    return try {
        val wm = android.app.WallpaperManager.getInstance(context)
        wm.setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_LOCK)
        true
    } catch (e: Exception) {
        false
    }
}

fun exportPrefs(context: Context, prefs: SharedPreferences, uri: Uri): Boolean {
    return try {
        val obj = JSONObject()
        for ((k, v) in prefs.all) {
            if (v is String || v is Int || v is Boolean || v is Long || v is Float) {
                obj.put(k, v)
            }
        }
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(obj.toString().toByteArray())
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun importPrefs(context: Context, prefs: SharedPreferences, uri: Uri): Boolean {
    return try {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return false
        val obj = JSONObject(text)
        val e = prefs.edit()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.get(k)
            if (v is String) {
                e.putString(k, v)
            } else if (v is Int) {
                e.putInt(k, v)
            } else if (v is Boolean) {
                e.putBoolean(k, v)
            } else if (v is Long) {
                e.putLong(k, v)
            } else if (v is Double) {
                e.putFloat(k, v.toFloat())
            }
        }
        e.apply()
        true
    } catch (e: Exception) {
        false
    }
}

fun Modifier.glass(shape: Shape, tint: Color = Color(0x40000000), fallback: Color = Color(0x4DFFFFFF)): Modifier =
    this.clip(shape)
        .frostedAuto(tint, fallback)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.24f),
                    Color.White.copy(alpha = 0.04f),
                    Color.Black.copy(alpha = 0.10f)
                )
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.45f)
                )
            ),
            shape
        )

val PRESET_NAMES = listOf(
    "Blue Wave", "Sunset", "Aurora", "Pink Bloom", "Mint", "Purple Night", "Ocean", "Graphite", "Silk"
)

private fun argb(v: Long): Int = v.toInt()

private fun presetColors(index: Int): IntArray {
    return when (index) {
        0 -> intArrayOf(argb(0xFF0A2A66), argb(0xFF1E6BFF), argb(0xCC5AC8FA), argb(0xCC7B61FF), argb(0xCC0A84FF))
        1 -> intArrayOf(argb(0xFF3A1C71), argb(0xFFD76D77), argb(0xCCFFAF7B), argb(0xCCFF5E62), argb(0xCCFFD86F))
        2 -> intArrayOf(argb(0xFF05161F), argb(0xFF0B3D4A), argb(0xCC30D158), argb(0xCC64D2FF), argb(0xCCBF5AF2))
        3 -> intArrayOf(argb(0xFF5B1E5E), argb(0xFFFF6FA8), argb(0xCCFF9AC8), argb(0xCCFFC371), argb(0xCCB57BFF))
        4 -> intArrayOf(argb(0xFF0B5D5A), argb(0xFF58E0B8), argb(0xCC9FFFE0), argb(0xCC30B0C7), argb(0xCCDFFF8A))
        5 -> intArrayOf(argb(0xFF120B2E), argb(0xFF3B1E8F), argb(0xCC7D5BFF), argb(0xCCFF4FD8), argb(0xCC3A7BFF))
        6 -> intArrayOf(argb(0xFF021B3A), argb(0xFF065A82), argb(0xCC00B4D8), argb(0xCC0077B6), argb(0xCC90E0EF))
        7 -> intArrayOf(argb(0xFF0B0B0F), argb(0xFF2B2B33), argb(0xCC8E8E93), argb(0xCC48484A), argb(0xCC636366))
        else -> intArrayOf(argb(0xFF6B4A32), argb(0xFFD8C3AE), argb(0xCCF2E6D8), argb(0xCCB38B6D), argb(0xCCE9D5C0))
    }
}

// iPhone jaise rangeen, naram wallpapers (asli Apple wallpaper nahi, wahi style)
fun makePresetWallpaper(index: Int, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val pal = presetColors(index)
    val wf = w.toFloat()
    val hf = h.toFloat()
    val m = maxOf(wf, hf)

    val base = Paint(Paint.ANTI_ALIAS_FLAG)
    base.shader = android.graphics.LinearGradient(
        0f, 0f, wf * 0.5f, hf, intArrayOf(pal[0], pal[1]), null, android.graphics.Shader.TileMode.CLAMP
    )
    c.drawRect(0f, 0f, wf, hf, base)

    val blobs = listOf(
        floatArrayOf(0.15f, 0.2f, 0.7f),
        floatArrayOf(0.9f, 0.35f, 0.65f),
        floatArrayOf(0.4f, 0.95f, 0.8f)
    )
    for (k in 0 until 3) {
        val b = blobs[k]
        val color = pal[2 + k]
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.RadialGradient(
            wf * b[0], hf * b[1], m * b[2],
            intArrayOf(color, color and 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP
        )
        c.drawCircle(wf * b[0], hf * b[1], m * b[2], p)
    }

    val wave = Paint(Paint.ANTI_ALIAS_FLAG)
    wave.style = Paint.Style.FILL
    for (k in 0 until 3) {
        val y = hf * (0.35f + 0.2f * k)
        val path = android.graphics.Path()
        path.moveTo(0f, y)
        path.cubicTo(wf * 0.3f, y - hf * 0.18f, wf * 0.65f, y + hf * 0.2f, wf, y - hf * 0.05f)
        path.lineTo(wf, hf)
        path.lineTo(0f, hf)
        path.close()
        wave.color = if (k % 2 == 0) 0x1AFFFFFF else 0x12000000
        c.drawPath(path, wave)
    }
    return bmp
}
