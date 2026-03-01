package com.uw.simplegallery.ui.screens.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.uw.simplegallery.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import androidx.media3.common.MediaItem as Media3MediaItem

/**
 * Video player composable adapted from Tulsi Gallery.
 *
 * Features:
 * - ExoPlayer-based video playback with Media3
 * - Custom controls: play/pause, seek back/forward (5s), mute toggle, timeline slider
 * - Double-tap to seek (left = rewind, right = forward) with animated overlay
 * - Single tap to toggle controls visibility
 * - Auto-hide controls after 5 seconds
 * - Lifecycle-aware: pauses on lifecycle pause, releases on destroy
 * - Keep screen awake during playback
 *
 * @param videoUri The content URI of the video to play
 * @param videoName The display name for the video
 * @param shouldAutoPlay Whether to auto-play when the video loads
 * @param onControlsVisibilityChanged Callback for when controls visibility changes
 *        (used by parent to toggle top/bottom bars)
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUri: Uri,
    videoName: String,
    shouldAutoPlay: Boolean = true,
    onControlsVisibilityChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current

    val isPlaying = rememberSaveable { mutableStateOf(false) }
    val isMuted = rememberSaveable { mutableStateOf(false) }

    /** Current position in seconds */
    val currentVideoPosition = rememberSaveable { mutableFloatStateOf(0f) }
    val duration = rememberSaveable { mutableFloatStateOf(0f) }

    val exoPlayer = rememberExoPlayerWithLifecycle(
        videoSource = videoUri,
        isPlaying = isPlaying,
        duration = duration,
        currentVideoPosition = currentVideoPosition
    )
    val playerView = rememberPlayerView(exoPlayer, context as Activity)

    val controlsVisible = remember { mutableStateOf(true) }
    var showVideoPlayerControlsTimeout by remember { mutableIntStateOf(0) }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showVideoPlayerControlsTimeout) {
        delay(5000)
        controlsVisible.value = false
        onControlsVisibilityChanged?.invoke(false)
        showVideoPlayerControlsTimeout = 0
    }

    // Sync play/pause state with ExoPlayer + position tracking
    LaunchedEffect(key1 = isPlaying.value) {
        if (!isPlaying.value) {
            controlsVisible.value = true
            onControlsVisibilityChanged?.invoke(true)
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }

        currentVideoPosition.floatValue = exoPlayer.currentPosition / 1000f

        // Loop back to beginning when video ends
        if (kotlin.math.ceil(currentVideoPosition.floatValue) >= kotlin.math.ceil(duration.floatValue)
            && duration.floatValue != 0f && !isPlaying.value
        ) {
            delay(1000)
            exoPlayer.pause()
            exoPlayer.seekTo(0)
            currentVideoPosition.floatValue = 0f
            isPlaying.value = false
        }

        // Periodically update position while playing
        while (isPlaying.value) {
            currentVideoPosition.floatValue = exoPlayer.currentPosition / 1000f
            delay(1000)
        }
    }

    LaunchedEffect(controlsVisible.value) {
        if (controlsVisible.value) showVideoPlayerControlsTimeout += 1
        onControlsVisibilityChanged?.invoke(controlsVisible.value)
    }

    // Mute/unmute handling
    LaunchedEffect(isMuted.value) {
        exoPlayer.volume = if (isMuted.value) 0f else 1f
        exoPlayer.setAudioAttributes(
            AudioAttributes.Builder().apply {
                setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            }.build(),
            !isMuted.value
        )
    }

    // Auto-play handling
    LaunchedEffect(shouldAutoPlay) {
        exoPlayer.playWhenReady = shouldAutoPlay
    }

    // Keep screen on during playback
    val activity = context as? Activity
    LaunchedEffect(isPlaying.value) {
        if (isPlaying.value) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // ExoPlayer video surface
        AndroidView(
            factory = { playerView },
            modifier = Modifier.align(Alignment.Center)
        )

        // Double-tap seek overlay
        var doubleTapDisplayTimeMillis by remember { mutableIntStateOf(0) }
        val seekBackBackgroundColor by animateColorAsState(
            targetValue = if (doubleTapDisplayTimeMillis < 0)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else Color.Transparent,
            animationSpec = tween(durationMillis = 350),
            label = "seekBackBg"
        )
        val seekForwardBackgroundColor by animateColorAsState(
            targetValue = if (doubleTapDisplayTimeMillis > 0)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else Color.Transparent,
            animationSpec = tween(durationMillis = 350),
            label = "seekForwardBg"
        )

        LaunchedEffect(doubleTapDisplayTimeMillis) {
            delay(1000)
            doubleTapDisplayTimeMillis = 0
        }

        // Tap gesture layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { position ->
                            if (doubleTapDisplayTimeMillis == 0) {
                                controlsVisible.value = !controlsVisible.value
                            } else {
                                // Consecutive taps during seek indicator → keep seeking
                                if (position.x < size.width / 2) {
                                    if (doubleTapDisplayTimeMillis > 0) doubleTapDisplayTimeMillis = 0
                                    doubleTapDisplayTimeMillis -= 1000
                                    val prev = isPlaying.value
                                    exoPlayer.seekBack()
                                    isPlaying.value = prev
                                } else {
                                    if (doubleTapDisplayTimeMillis < 0) doubleTapDisplayTimeMillis = 0
                                    doubleTapDisplayTimeMillis += 1000
                                    val prev = isPlaying.value
                                    exoPlayer.seekForward()
                                    isPlaying.value = prev
                                }
                                showVideoPlayerControlsTimeout += 1
                            }
                        },
                        onDoubleTap = { position ->
                            if (position.x < size.width / 2) {
                                if (doubleTapDisplayTimeMillis > 0) doubleTapDisplayTimeMillis = 0
                                doubleTapDisplayTimeMillis -= 1000
                                val prev = isPlaying.value
                                exoPlayer.seekBack()
                                isPlaying.value = prev
                            } else {
                                if (doubleTapDisplayTimeMillis < 0) doubleTapDisplayTimeMillis = 0
                                doubleTapDisplayTimeMillis += 1000
                                val prev = isPlaying.value
                                exoPlayer.seekForward()
                                isPlaying.value = prev
                            }
                            showVideoPlayerControlsTimeout += 1
                        }
                    )
                }
        )

        // Seek direction indicator overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
        ) {
            // Rewind indicator (left half)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(0, 100, 100, 0))
                    .background(seekBackBackgroundColor)
            ) {
                AnimatedVisibility(
                    visible = doubleTapDisplayTimeMillis < 0,
                    enter = fadeIn(tween(300)) + scaleIn(
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                    ),
                    exit = fadeOut(tween(300)) + scaleOut(
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                    ),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fast_rewind),
                        contentDescription = "Seeking backward",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Forward indicator (right half)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(100, 0, 0, 100))
                    .background(seekForwardBackgroundColor)
            ) {
                AnimatedVisibility(
                    visible = doubleTapDisplayTimeMillis > 0,
                    enter = fadeIn(tween(300)) + scaleIn(
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                    ),
                    exit = fadeOut(tween(300)) + scaleOut(
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                    ),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fast_forward),
                        contentDescription = "Seeking forward",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Player controls overlay (animated show/hide)
        AnimatedVisibility(
            visible = controlsVisible.value,
            enter = expandIn(tween(350)) + fadeIn(tween(350)),
            exit = shrinkOut(tween(350)) + fadeOut(tween(350)),
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            VideoPlayerControls(
                exoPlayer = exoPlayer,
                isPlaying = isPlaying,
                isMuted = isMuted,
                currentVideoPosition = currentVideoPosition,
                duration = duration,
                onAnyTap = { showVideoPlayerControlsTimeout += 1 },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Custom video player controls with play/pause, seek, mute, and timeline slider.
 *
 * Layout:
 * - Center row: rewind 5s, play/pause, forward 5s
 * - Bottom row: current time, timeline slider, total duration, mute button
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerControls(
    exoPlayer: ExoPlayer,
    isPlaying: MutableState<Boolean>,
    isMuted: MutableState<Boolean>,
    currentVideoPosition: MutableFloatState,
    duration: MutableFloatState,
    modifier: Modifier = Modifier,
    onAnyTap: () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.background(Color.Transparent)
    ) {
        // Bottom controls row: time | slider | duration | mute
        Row(
            modifier = Modifier
                .height(172.dp)
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp, 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val currentDurationFormatted =
                    currentVideoPosition.floatValue.roundToInt().seconds.formatVideoDuration()

                // Current position pill
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .width(if (currentDurationFormatted.second) 72.dp else 48.dp)
                        .clip(RoundedCornerShape(1000.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(4.dp, 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentDurationFormatted.first,
                        style = TextStyle(
                            fontSize = TextUnit(12f, TextUnitType.Sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Slider drag interaction tracking
                val interactionSource = remember { MutableInteractionSource() }
                var isDraggingSlider by remember { mutableStateOf(false) }

                LaunchedEffect(interactionSource) {
                    interactionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is DragInteraction.Start -> isDraggingSlider = true
                            is DragInteraction.Stop, is DragInteraction.Cancel ->
                                isDraggingSlider = false
                        }
                    }
                }

                duration.floatValue = duration.floatValue.coerceAtLeast(0f)

                // Timeline slider
                Slider(
                    value = currentVideoPosition.floatValue,
                    valueRange = 0f..duration.floatValue,
                    onValueChange = { pos ->
                        onAnyTap()
                        val prev = isPlaying.value
                        exoPlayer.seekTo(
                            (pos * 1000f).coerceAtMost(duration.floatValue * 1000f).toLong()
                        )
                        isPlaying.value = prev
                    },
                    steps = (duration.floatValue.roundToInt() - 1).coerceAtLeast(0),
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            thumbSize = DpSize(6.dp, 16.dp),
                        )
                    },
                    track = { sliderState ->
                        val colors = SliderDefaults.colors()
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            trackInsideCornerSize = 8.dp,
                            colors = colors.copy(
                                activeTickColor = colors.activeTrackColor,
                                inactiveTickColor = colors.inactiveTrackColor,
                                disabledActiveTickColor = colors.disabledActiveTrackColor,
                                disabledInactiveTickColor = colors.disabledInactiveTrackColor,
                                activeTrackColor = colors.activeTrackColor,
                                inactiveTrackColor = colors.inactiveTrackColor,
                                disabledThumbColor = colors.activeTrackColor,
                                thumbColor = colors.activeTrackColor
                            ),
                            thumbTrackGapSize = 4.dp,
                            drawTick = { _, _ -> },
                            modifier = Modifier.height(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val formattedDuration =
                    duration.floatValue.roundToInt().seconds.formatVideoDuration()

                // Total duration pill
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .width(if (formattedDuration.second) 72.dp else 48.dp)
                        .clip(RoundedCornerShape(1000.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(4.dp, 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formattedDuration.first,
                        style = TextStyle(
                            fontSize = TextUnit(12f, TextUnitType.Sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Mute button
                FilledTonalIconButton(
                    onClick = {
                        isMuted.value = !isMuted.value
                        onAnyTap()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isMuted.value) R.drawable.ic_volume_mute
                            else R.drawable.ic_volume_max
                        ),
                        contentDescription = if (isMuted.value) "Unmute" else "Mute",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Center controls row: rewind | play/pause | forward
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Rewind 5s
            FilledTonalIconButton(
                onClick = {
                    val prev = isPlaying.value
                    exoPlayer.seekBack()
                    isPlaying.value = prev
                    onAnyTap()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_fast_rewind),
                    contentDescription = "Rewind 5 seconds",
                    modifier = Modifier.padding(end = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(48.dp))

            // Play/Pause
            FilledTonalIconButton(
                onClick = {
                    isPlaying.value = !isPlaying.value
                    onAnyTap()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (!isPlaying.value) R.drawable.ic_play_arrow
                        else R.drawable.ic_pause
                    ),
                    contentDescription = if (isPlaying.value) "Pause" else "Play"
                )
            }

            Spacer(modifier = Modifier.width(48.dp))

            // Forward 5s
            FilledTonalIconButton(
                onClick = {
                    val prev = isPlaying.value
                    exoPlayer.seekForward()
                    isPlaying.value = prev
                    onAnyTap()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_fast_forward),
                    contentDescription = "Forward 5 seconds",
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

// ── ExoPlayer Lifecycle Management ──────────────────────────────────────

/**
 * Creates and remembers an ExoPlayer instance that is lifecycle-aware.
 * Pauses on lifecycle ON_PAUSE, releases on ON_DESTROY.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberExoPlayerWithLifecycle(
    videoSource: Uri,
    isPlaying: MutableState<Boolean>,
    duration: MutableFloatState,
    currentVideoPosition: MutableFloatState
): ExoPlayer {
    val context = LocalContext.current

    val exoPlayer = remember {
        createExoPlayer(
            videoSource = videoSource,
            context = context,
            isPlaying = isPlaying,
            currentVideoPosition = currentVideoPosition,
            duration = duration
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner.lifecycle.currentStateAsState().value) {
        val lifecycleObserver = getExoPlayerLifecycleObserver(exoPlayer, isPlaying, context as Activity)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    return exoPlayer
}

/**
 * Builds an ExoPlayer with optimized buffer settings, 5s seek increments,
 * and a ProgressiveMediaSource for the given video URI.
 */
@androidx.annotation.OptIn(UnstableApi::class)
fun createExoPlayer(
    videoSource: Uri,
    context: Context,
    isPlaying: MutableState<Boolean>,
    currentVideoPosition: MutableFloatState,
    duration: MutableFloatState
): ExoPlayer {
    val exoPlayer = ExoPlayer.Builder(context).apply {
        setLoadControl(
            DefaultLoadControl.Builder().apply {
                setBufferDurationsMs(1000, 5000, 1000, 1000)
                setBackBuffer(1000, false)
                setPrioritizeTimeOverSizeThresholds(false)
            }.build()
        )
        setSeekBackIncrementMs(5000)
        setSeekForwardIncrementMs(5000)
        setPauseAtEndOfMediaItems(true)
        setAudioAttributes(
            AudioAttributes.Builder().apply {
                setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            }.build(),
            false
        )
        setHandleAudioBecomingNoisy(true)
    }.build().apply {
        videoScalingMode = VIDEO_SCALING_MODE_SCALE_TO_FIT
        repeatMode = ExoPlayer.REPEAT_MODE_ONE

        val defaultDataSourceFactory = DefaultDataSource.Factory(context)
        val dataSourceFactory = DefaultDataSource.Factory(context, defaultDataSourceFactory)
        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Media3MediaItem.fromUri(videoSource))

        setMediaSource(source)
        prepare()
    }

    val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            if (playbackState == ExoPlayer.STATE_READY) {
                duration.floatValue = exoPlayer.duration / 1000f
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            currentVideoPosition.floatValue = newPosition.positionMs / 1000f
        }

        override fun onIsPlayingChanged(playerIsPlaying: Boolean) {
            super.onIsPlayingChanged(playerIsPlaying)
            isPlaying.value = playerIsPlaying
        }
    }
    exoPlayer.addListener(listener)

    return exoPlayer
}

/**
 * Creates a lifecycle observer that pauses ExoPlayer on ON_PAUSE
 * and releases it on ON_DESTROY (unless configuration change).
 */
@UnstableApi
fun getExoPlayerLifecycleObserver(
    exoPlayer: ExoPlayer,
    isPlaying: MutableState<Boolean>,
    activity: Activity
): LifecycleEventObserver =
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                exoPlayer.playWhenReady = false
                isPlaying.value = false
            }

            Lifecycle.Event.ON_DESTROY -> {
                isPlaying.value = false
                if (!activity.isChangingConfigurations) {
                    exoPlayer.stop()
                    exoPlayer.release()
                }
            }

            else -> {}
        }
    }

/**
 * Creates and remembers a PlayerView with custom controller disabled
 * (we use our own Compose controls).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberPlayerView(
    exoPlayer: ExoPlayer,
    activity: Activity
): PlayerView {
    val context = LocalContext.current

    val playerView = remember {
        PlayerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = false
            player = exoPlayer
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!activity.isChangingConfigurations) {
                playerView.player = null
                exoPlayer.release()
            }
        }
    }

    return playerView
}

// ── Duration Formatting Utility ─────────────────────────────────────────

/**
 * Formats a Duration into a human-readable video timestamp.
 *
 * @return A [Pair] where:
 *   - `first` is the formatted string (e.g. "01:23" or "01:02:30")
 *   - `second` is `true` if the duration is longer than 60 minutes (for wider display)
 */
fun Duration.formatVideoDuration(): Pair<String, Boolean> {
    val isLong = this > 60.minutes
    val formatted = if (isLong) {
        this.toComponents { hours, minutes, seconds, _ ->
            String.format(java.util.Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    } else {
        this.toComponents { minutes, seconds, _ ->
            String.format(java.util.Locale.ENGLISH, "%02d:%02d", minutes, seconds)
        }
    }
    return Pair(formatted, isLong)
}
