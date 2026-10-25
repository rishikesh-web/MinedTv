package com.example.minedtv.ui.components

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.minedtv.util.Logger

/** Plays a backend HLS playlist (.m3u8). [seekToMs] jumps straight to a search match. */
@OptIn(UnstableApi::class)
@Composable
fun HlsPlayer(url: String, modifier: Modifier = Modifier, seekToMs: Long = 0) {
    val context = LocalContext.current
    val player = remember(url, seekToMs) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            if (seekToMs > 0) seekTo(seekToMs)
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Logger.e("Playback failed: ${error.errorCodeName}", error)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) Logger.d("Playback ready (seek=${seekToMs}ms)")
                }
            })
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; useController = true } },
        update = { it.player = player },
        modifier = modifier
    )
}
