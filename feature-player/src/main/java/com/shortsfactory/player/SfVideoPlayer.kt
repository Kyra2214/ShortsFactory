package com.shortsfactory.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File

/**
 * Leitor interno para arquivos do app (vídeo importado, prévias e Shorts exportados).
 * Controles próprios em Compose; o vídeo é desenhado por [PlayerView] sem controlador.
 */
@OptIn(UnstableApi::class)
@Composable
fun SfVideoPlayer(
    filePath: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 9f / 16f,
    autoPlay: Boolean = false,
    loop: Boolean = false,
    clipStartMs: Long = 0L,
    clipEndMs: Long? = null,
    fillFrame: Boolean = false
) {
    val context = LocalContext.current
    val allowed = remember(filePath) { PlayablePath.isAllowed(context, filePath) }

    if (!allowed) {
        PlayerMessage("Arquivo de vídeo indisponível.", modifier, aspectRatio)
        return
    }

    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(buildClipItem(filePath, clipStartMs, clipEndMs))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = autoPlay
            prepare()
        }
    }
    var errorMessage by remember(player) { mutableStateOf<String?>(null) }
    var isPlaying by remember(player) { mutableStateOf(false) }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }
    var scrubbing by remember(player) { mutableStateOf(false) }
    var scrubValue by remember(player) { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) positionMs = durationMs
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Não foi possível reproduzir este vídeo neste aparelho."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Trecho alterado: recarrega só o item (mesmo player) e volta ao início do trecho.
    var appliedClip by remember(player) { mutableStateOf(clipStartMs to clipEndMs) }
    LaunchedEffect(player, clipStartMs, clipEndMs) {
        if (appliedClip == clipStartMs to clipEndMs) return@LaunchedEffect
        appliedClip = clipStartMs to clipEndMs
        errorMessage = null
        player.setMediaItem(buildClipItem(filePath, clipStartMs, clipEndMs))
        player.prepare()
        positionMs = 0L
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            if (!scrubbing) positionMs = player.currentPosition
            delay(250)
        }
    }

    val togglePlay = {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(MaterialTheme.shapes.large)
                .background(Color.Black)
                .clickable(onClick = togglePlay),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = if (fillFrame) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.matchParentSize()
            )
            errorMessage?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { togglePlay() }, enabled = errorMessage == null) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir"
                )
            }
            Slider(
                value = if (scrubbing) scrubValue else positionMs.toFloat(),
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    player.seekTo(scrubValue.toLong())
                    positionMs = scrubValue.toLong()
                    scrubbing = false
                },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                enabled = durationMs > 0L && errorMessage == null,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${formatTime(if (scrubbing) scrubValue.toLong() else positionMs)} / ${formatTime(durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp)
            )
        }
    }
}

@Composable
private fun PlayerMessage(message: String, modifier: Modifier, aspectRatio: Float) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp)
        )
    }
}

private fun buildClipItem(filePath: String, clipStartMs: Long, clipEndMs: Long?): MediaItem {
    val builder = MediaItem.Builder().setUri(Uri.fromFile(File(filePath)))
    if (clipStartMs > 0L || clipEndMs != null) {
        builder.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clipStartMs.coerceAtLeast(0L))
                .setEndPositionMs(clipEndMs ?: C.TIME_END_OF_SOURCE)
                .build()
        )
    }
    return builder.build()
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
