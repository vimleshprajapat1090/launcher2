package com.iphone.launcher

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (e: Exception) {
        Toast.makeText(context, "Settings nahi khuli", Toast.LENGTH_SHORT).show()
    }
}

fun expandNotifications(context: Context) {
    try {
        val sbs = context.getSystemService("statusbar")
        val cls = Class.forName("android.app.StatusBarManager")
        cls.getMethod("expandNotificationsPanel").invoke(sbs)
    } catch (e: Exception) {
    }
}

fun readBrightness(context: Context): Float {
    return try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) {
        0.5f
    }
}

fun setBrightness(context: Context, v: Float) {
    try {
        val cr = context.contentResolver
        Settings.System.putInt(
            cr,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255f).toInt().coerceIn(1, 255))
    } catch (e: Exception) {
    }
}

fun openWriteSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Settings nahi khuli", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CCCircle(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp)
            .clip(CircleShape)
            .background(if (on) Color(0xFF0A84FF) else Color(0x55FFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CCCard(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xCC2C2C2E)),
        content = content
    )
}

@Composable
fun ControlCenter(media: MediaWatcher, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(enabled = true) { onClose() }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var bright by remember { mutableStateOf(readBrightness(context).coerceIn(0.05f, 1f)) }
    var asked by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    val info = media.info

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.statusBarsPadding()
                .padding(top = 56.dp)
                .padding(16.dp)
                .widthIn(max = 420.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CCCard(Modifier.weight(1f).aspectRatio(1f)) {
                    Column(
                        Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            CCCircle("Wi-Fi", true) { openSettings(context, Settings.ACTION_WIFI_SETTINGS) }
                            CCCircle("BT", false) { openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            CCCircle("Plane", false) { openSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
                            CCCircle("Data", false) { openSettings(context, Settings.ACTION_WIRELESS_SETTINGS) }
                        }
                    }
                }
                CCCard(Modifier.weight(1f).aspectRatio(1f)) {
                    Column(
                        Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                if (info != null) info.title else "Not Playing",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (info != null) {
                                Text(
                                    info.artist,
                                    color = Color(0xFFB0B0B5),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            PlayerIcon(0) { media.prev() }
                            PlayerIcon(if (info?.playing == true) 2 else 1) { media.playPause() }
                            PlayerIcon(3) { media.next() }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CCCard(Modifier.size(90.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CCCircle("Torch", torch) {
                            try {
                                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                                val id = cm.cameraIdList.firstOrNull {
                                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                                }
                                if (id != null) {
                                    torch = !torch
                                    cm.setTorchMode(id, torch)
                                } else {
                                    Toast.makeText(context, "Is device me flash nahi hai", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Torch chalu nahi hui", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                CCCard(Modifier.weight(1f).height(90.dp)) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Volume", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = volume,
                            onValueChange = { v ->
                                volume = v
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, v.toInt(), 0)
                            },
                            valueRange = 0f..maxVol,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color(0x55FFFFFF)
                            )
                        )
                    }
                }
            }
            CCCard(Modifier.fillMaxWidth().height(90.dp)) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Brightness", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = bright,
                        onValueChange = { v ->
                            if (Settings.System.canWrite(context)) {
                                bright = v
                                setBrightness(context, v)
                            } else if (!asked) {
                                asked = true
                                openWriteSettings(context)
                            } else {
                                bright = bright
                            }
                        },
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        )
                    )
                }
            }
        }
    }
}
