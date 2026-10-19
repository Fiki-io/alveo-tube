package com.alveo.tube.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.alveo.tube.core.model.StreamResult

class PlaybackManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var attachedPlayerView: PlayerView? = null

    fun attachPlayerView(playerView: PlayerView) {
        attachedPlayerView = playerView
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
        playerView.player = exoPlayer
    }

    fun playStream(
        streamResult: StreamResult,
        onPlayerStateChanged: (isPlaying: Boolean, isBuffering: Boolean, errorMsg: String?) -> Unit
    ) {
        val player = exoPlayer ?: ExoPlayer.Builder(context).build().also {
            exoPlayer = it
            attachedPlayerView?.player = it
        }

        // Build DataSourceFactory with captured session headers
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)

        val headerMap = mutableMapOf<String, String>()
        headerMap["Referer"] = "https://www.youtube.com/"
        streamResult.headers.forEach { (k, v) ->
            if (!k.equals("Host", ignoreCase = true) && !k.equals("Content-Length", ignoreCase = true)) {
                headerMap[k] = v
            }
        }
        httpDataSourceFactory.setDefaultRequestProperties(headerMap)

        val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamResult.streamUrl))

        player.clearMediaItems()
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onPlayerStateChanged(false, true, null)
                    Player.STATE_READY -> onPlayerStateChanged(player.isPlaying, false, null)
                    Player.STATE_ENDED -> onPlayerStateChanged(false, false, null)
                    Player.STATE_IDLE -> onPlayerStateChanged(false, false, null)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayerStateChanged(isPlaying, player.playbackState == Player.STATE_BUFFERING, null)
            }

            override fun onPlayerError(error: PlaybackException) {
                onPlayerStateChanged(false, false, error.message ?: "Playback error")
            }
        })
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun release() {
        attachedPlayerView?.player = null
        exoPlayer?.release()
        exoPlayer = null
        attachedPlayerView = null
    }
}
