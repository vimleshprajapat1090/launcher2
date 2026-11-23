package com.iphone.launcher

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File

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
        val sx = ((pos.x - offX) / scale).coerceIn(0f, blurred.width - 1f)
        val sy = ((pos.y - offY) / scale).coerceIn(0f, blurred.height - 1f)
        val sw = (size.width / scale).coerceIn(1f, blurred.width - sx)
        val sh = (size.height / scale).coerceIn(1f, blurred.height - sy)
        drawImage(
            image = blurred,
            srcOffset = IntOffset(sx.toInt(), sy.toInt()),
            srcSize = IntSize(sw.toInt().coerceAtLeast(1), sh.toInt().coerceAtLeast(1)),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
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
