package com.iphone.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MediaInfo(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap?
)

class MediaWatcher(private val context: Context) {
    var info by mutableStateOf<MediaInfo?>(null)

    private var controller: MediaController? = null
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
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn)
            attach(msm.getActiveSessions(cn))
        } catch (e: Exception) {
            info = null
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
    }

    private fun attach(list: List<MediaController>?) {
        val c = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (c?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = c
            c?.registerCallback(callback)
        }
        refresh()
    }

    private fun refresh() {
        val c = controller
        if (c == null) {
            info = null
            return
        }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (md == null || title.isNullOrBlank()) {
            info = null
            return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val art = (md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART))?.asImageBitmap()
        info = MediaInfo(title, artist, c.playbackState?.state == PlaybackState.STATE_PLAYING, art)
    }

    fun playPause() {
        val c = controller ?: return
        if (info?.playing == true) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun prev() {
        controller?.transportControls?.skipToPrevious()
    }
}

@Composable
fun DynamicIsland(
    media: MediaWatcher,
    call: CallInfo?,
    hasAccess: Boolean,
    onNeedAccess: () -> Unit
) {
    val info = media.info
    var expanded by remember { mutableStateOf(false) }
    val mode = when {
        call != null -> 2
        info != null -> 1
        else -> 0
    }
    val targetW = when (mode) {
        2 -> 320.dp
        1 -> if (expanded) 320.dp else 200.dp
        else -> 112.dp
    }
    val targetH = when (mode) {
        2 -> 128.dp
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
            .clickable {
                if (mode == 0) {
                    if (!hasAccess) onNeedAccess()
                } else if (mode == 1) {
                    expanded = !expanded
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (mode == 1 && info != null) {
            if (expanded) ExpandedMusic(info, media) else CompactMusic(info)
        } else if (mode == 2 && call != null) {
            CallContent(call)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            call.actions.take(3).forEach { a ->
                val t = a.title.lowercase()
                val bad = t.contains("decline") || t.contains("reject") || t.contains("hang") ||
                    t.contains("end") || t.contains("disconnect")
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
