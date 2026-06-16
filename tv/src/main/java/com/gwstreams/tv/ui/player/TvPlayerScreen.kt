package com.gwstreams.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gwstreams.app.data.repo.AppSettings
import com.gwstreams.app.data.repo.DecoderMode
import com.gwstreams.app.data.repo.Session
import com.gwstreams.app.data.repo.StreamFormat
import com.gwstreams.app.data.repo.VideoFit
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection

/**
 * Fullscreen player. Warm ExoPlayer with URL swap for fast zapping (#9).
 * Honors settings: stream format (#1), video fit (#2), decoder mode (#3), buffer (#9).
 */
@UnstableApi
@Composable
fun TvPlayerScreen(
    item: TvContentItem,
    settings: AppSettings,
    onLastChannel: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current

    fun urlFor(it: TvContentItem): String = when (it.section) {
        TvSection.LIVE -> Session.liveUrl(it.id, settings.streamFormat == StreamFormat.HLS)
        TvSection.MOVIES -> Session.vodUrl(it.id, it.containerExt)
        TvSection.SERIES -> Session.seriesUrl(it.id, it.containerExt)
        TvSection.SETTINGS -> ""
    }

    val player = remember {
        val bufMs = settings.bufferSeconds * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufMs.coerceAtLeast(5000),
                (bufMs * 2).coerceAtLeast(15000),
                2500,
                5000
            )
            .build()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("GWStreams")
        // Decoder mode (#3): software prefers extension/software decoders.
        val renderers = DefaultRenderersFactory(context).apply {
            val mode = if (settings.decoderMode == DecoderMode.SOFTWARE)
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            setExtensionRendererMode(mode)
        }
        ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    LaunchedEffect(item.id, settings.streamFormat) {
        val isLive = item.section == TvSection.LIVE
        val mediaItem = MediaItem.Builder()
            .setUri(urlFor(item))
            .apply { if (isLive && settings.streamFormat == StreamFormat.TS) setMimeType(MimeTypes.VIDEO_MP2T) }
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) { onDispose { player.release() } }
    BackHandler { onBack() }

    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val resize = when (settings.videoFit) {
        VideoFit.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        VideoFit.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoFit.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp &&
                    (e.key == androidx.compose.ui.input.key.Key.ChannelDown ||
                        e.key == androidx.compose.ui.input.key.Key.MediaPrevious)
                ) {
                    onLastChannel(); true
                } else false
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    resizeMode = resize
                    keepScreenOn = true
                }
            },
            update = { it.resizeMode = resize },
            modifier = Modifier.fillMaxSize()
        )
    }
}
