package com.iphone.launcher

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.telecom.TelecomManager
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class MediaInfo(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap?,
    val pkg: String = ""
)

class MediaWatcher(private val context: Context) {
    var info by mutableStateOf<MediaInfo?>(null)
    var dismissed by mutableStateOf(false)

    private var controller: MediaController? = null
    private var started = false
    private val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val cn = ComponentName(context, NotificationListener::class.java)

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            refresh()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            refresh()
        }

        override fun onSessionDestroyed() {
            refresh()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list)
    }

    fun start() {
        if (started) return
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn)
            started = true
            attach(msm.getActiveSessions(cn))
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        try {
            controller?.unregisterCallback(callback)
        } catch (e: Exception) {
        }
        controller = null
        started = false
    }

    private fun hasTitle(c: MediaController): Boolean {
        val t = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        return !t.isNullOrBlank()
    }

    private fun attach(list: List<MediaController>?) {
        val usable = list?.filter { hasTitle(it) } ?: emptyList()
        val best = usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: usable.firstOrNull()
        if (best == null) {
            try {
                controller?.unregisterCallback(callback)
            } catch (e: Exception) {
            }
            controller = null
        } else if (best.sessionToken != controller?.sessionToken) {
            try {
                controller?.unregisterCallback(callback)
            } catch (e: Exception) {
            }
            controller = best
            best.registerCallback(callback)
        }
        refresh()
    }

    private fun refresh() {
        val c = controller
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c != null && md != null && !title.isNullOrBlank()) {
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: ""
            val art = (md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART))?.asImageBitmap()
            info = MediaInfo(title, artist, playing, art, c.packageName ?: "")
            if (playing) dismissed = false
        } else {
            val last = info
            if (last != null && last.playing) {
                info = last.copy(playing = false)
            }
        }
    }

    private fun sendKey(code: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (e: Exception) {
        }
    }

    fun dismiss() {
        dismissed = true
    }

    fun playPause() {
        val c = controller
        val state = c?.playbackState?.state
        if (c != null && state != null &&
            state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_STOPPED
        ) {
            if (state == PlaybackState.STATE_PLAYING) {
                c.transportControls.pause()
            } else {
                c.transportControls.play()
            }
        } else {
            sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        dismissed = false
    }

    fun next() {
        val c = controller
        if (c != null) c.transportControls.skipToNext() else sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun prev() {
        val c = controller
        if (c != null) c.transportControls.skipToPrevious() else sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }
}

@Composable
fun DynamicIsland(
    media: MediaWatcher,
    call: CallInfo?,
    hasAccess: Boolean,
    onNeedAccess: () -> Unit,
    toast: ToastInfo? = null,
    showIdle: Boolean = true,
    timer: TimerInfo? = null
) {
    val info = if (media.dismissed) null else media.info
    var expanded by remember { mutableStateOf(false) }
    val dragSum = remember { floatArrayOf(0f) }
    val mode = when {
        call != null -> 2
        toast != null -> 3
        timer != null -> 4
        info != null -> 1
        else -> 0
    }
    if (mode == 0 && !showIdle) return

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(5000)
            expanded = false
        }
    }

    val targetW = when (mode) {
        2 -> 320.dp
        3 -> 320.dp
        4 -> 200.dp
        1 -> if (expanded) 320.dp else 200.dp
        else -> 112.dp
    }
    val targetH = when (mode) {
        2 -> 128.dp
        3 -> 88.dp
        4 -> 36.dp
        1 -> if (expanded) 124.dp else 36.dp
        else -> 32.dp
    }
    val w by animateDpAsState(targetW, label = "islandW")
    val h by animateDpAsState(targetH, label = "islandH")
    val radius = if (h > 60.dp) 36.dp else 20.dp

    Box(
        Modifier.width(w)
            .height(h)
            .clip(RoundedCornerShape(radius))
            .background(Color.Black)
            .pointerInput(mode, expanded) {
                detectVerticalDragGestures(
                    onDragStart = { dragSum[0] = 0f },
                    onDragEnd = {
                        val d = dragSum[0]
                        if (d < -30f) {
                            if (mode == 1) {
                                if (expanded) expanded = false else media.dismiss()
                            } else if (mode == 3) {
                                IslandState.toast = null
                            }
                        } else if (d > 30f) {
                            if (mode == 1) expanded = true
                        }
                        dragSum[0] = 0f
                    },
                    onVerticalDrag = { _, dy -> dragSum[0] += dy }
                )
            }
            .clickable {
                if (mode == 0) {
                    if (!hasAccess) onNeedAccess()
                } else if (mode == 1) {
                    expanded = !expanded
                } else if (mode == 3 && toast != null) {
                    try {
                        toast.intent?.send()
                    } catch (e: Exception) {
                    }
                    IslandState.toast = null
                } else if (mode == 4 && timer != null) {
                    try {
                        timer.intent?.send()
                    } catch (e: Exception) {
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (mode == 1 && info != null) {
            if (expanded) ExpandedMusic(info, media) else CompactMusic(info)
        } else if (mode == 2 && call != null) {
            CallContent(call)
        } else if (mode == 3 && toast != null) {
            ToastContent(toast)
        } else if (mode == 4 && timer != null) {
            TimerContent(timer)
        }
    }
}

@Composable
fun TimerContent(t: TimerInfo) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t.whenMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500L)
        }
    }
    val ms = if (t.countDown) t.whenMs - now else now - t.whenMs
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    val label = if (h > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
    val orange = Color(0xFFFF9F0A)
    Row(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(20.dp)) {
            drawCircle(orange, radius = size.minDimension / 2f - 2f, style = Stroke(width = 3f))
            drawLine(orange, center, Offset(center.x, center.y - size.height * 0.3f), strokeWidth = 3f)
            drawLine(orange, center, Offset(center.x + size.width * 0.22f, center.y), strokeWidth = 3f)
        }
        Text(
            t.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(label, color = orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ToastContent(t: ToastInfo) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (t.icon != null) {
            Image(
                bitmap = t.icon,
                contentDescription = null,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                t.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                t.text,
                color = Color(0xFFB0B0B5),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompactMusic(info: MediaInfo) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtImage(info, 24)
        Text(
            info.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            val bar = if (info.playing) Color(0xFFFF9F0A) else Color(0xFF8E8E93)
            Box(Modifier.padding(horizontal = 1.dp).width(3.dp).height(8.dp).background(bar))
            Box(Modifier.padding(horizontal = 1.dp).width(3.dp).height(14.dp).background(bar))
            Box(Modifier.padding(horizontal = 1.dp).width(3.dp).height(10.dp).background(bar))
        }
    }
}

@Composable
fun ArtImage(info: MediaInfo, sizeDp: Int) {
    if (info.art != null) {
        Image(
            bitmap = info.art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A3A3C)))
    }
}

@Composable
fun ExpandedMusic(info: MediaInfo, media: MediaWatcher) {
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtImage(info, 48)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    info.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    info.artist,
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "X",
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { media.dismiss() }.padding(8.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PlayerIcon(0) { media.prev() }
            PlayerIcon(if (info.playing) 2 else 1) { media.playPause() }
            PlayerIcon(3) { media.next() }
        }
    }
}

@Composable
fun PlayerIcon(kind: Int, onClick: () -> Unit) {
    Canvas(
        Modifier.size(36.dp).clickable { onClick() }.padding(6.dp)
    ) {
        val w = size.width
        val h = size.height
        when (kind) {
            0 -> {
                drawRect(Color.White, Offset(0f, 0f), Size(w * 0.15f, h))
                val p = Path().apply {
                    moveTo(w, 0f)
                    lineTo(w * 0.2f, h / 2f)
                    lineTo(w, h)
                    close()
                }
                drawPath(p, Color.White)
            }
            1 -> {
                val p = Path().apply {
                    moveTo(w * 0.1f, 0f)
                    lineTo(w, h / 2f)
                    lineTo(w * 0.1f, h)
                    close()
                }
                drawPath(p, Color.White)
            }
            2 -> {
                drawRect(Color.White, Offset(w * 0.12f, 0f), Size(w * 0.28f, h))
                drawRect(Color.White, Offset(w * 0.6f, 0f), Size(w * 0.28f, h))
            }
            else -> {
                drawRect(Color.White, Offset(w * 0.85f, 0f), Size(w * 0.15f, h))
                val p = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.8f, h / 2f)
                    lineTo(0f, h)
                    close()
                }
                drawPath(p, Color.White)
            }
        }
    }
}

@Composable
fun CallContent(call: CallInfo) {
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Call", color = Color(0xFF8E8E93), fontSize = 11.sp)
            Text(
                call.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (call.text.isNotBlank()) {
                Text(
                    call.text,
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val ctx = LocalContext.current
        if (call.actions.isEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(Color(0xFF30D158))
                        .clickable { answerCall(ctx) }
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text("Answer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(Color(0xFFFF3B30))
                        .clickable { endCall(ctx) }
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text("End", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            call.actions.take(3).forEach { a ->
                val t = a.title.lowercase()
                val bad = t.contains("decline") || t.contains("reject") || t.contains("hang") ||
                    t.contains("end") || t.contains("disconnect") || t.contains("cut")
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (bad) Color(0xFFFF3B30) else Color(0xFF30D158))
                        .clickable {
                            try {
                                a.intent?.send()
                            } catch (e: Exception) {
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(a.title, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

fun answerCall(context: Context) {
    try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.acceptRingingCall()
    } catch (e: SecurityException) {
        Toast.makeText(context, "Home menu me Island aur Calls se call permission do", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
    }
}

fun endCall(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= 28) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Home menu me Island aur Calls se call permission do", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
    }
}
