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

fun makePresetWallpaper(index: Int, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val colors = when (index) {
        0 -> intArrayOf(0xFF1B1464.toInt(), 0xFF6A11CB.toInt(), 0xFFFF6FD8.toInt())
        1 -> intArrayOf(0xFFFF512F.toInt(), 0xFFF09819.toInt(), 0xFFFFD86F.toInt())
        2 -> intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt(), 0xFF4CA1AF.toInt())
        else -> intArrayOf(0xFF0B0B0F.toInt(), 0xFF232526.toInt(), 0xFF414345.toInt())
    }
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = android.graphics.LinearGradient(
        0f, 0f, w * 0.6f, h.toFloat(), colors, null, android.graphics.Shader.TileMode.CLAMP
    )
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    val blob = Paint(Paint.ANTI_ALIAS_FLAG)
    blob.shader = android.graphics.RadialGradient(
        w * 0.2f, h * 0.25f, w * 0.6f, intArrayOf(0x66FFFFFF, 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP
    )
    c.drawCircle(w * 0.2f, h * 0.25f, w * 0.6f, blob)
    val blob2 = Paint(Paint.ANTI_ALIAS_FLAG)
    blob2.shader = android.graphics.RadialGradient(
        w * 0.85f, h * 0.8f, w * 0.7f, intArrayOf(0x44FFFFFF, 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP
    )
    c.drawCircle(w * 0.85f, h * 0.8f, w * 0.7f, blob2)
    return bmp
}
