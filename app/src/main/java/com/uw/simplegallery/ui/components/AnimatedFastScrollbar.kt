package com.uw.simplegallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Draggable fast-scrollbar for LazyVerticalGrid-based timeline screens.
 */
@Composable
fun AnimatedFastScrollbar(
    state: LazyGridState,
    totalItems: Int,
    itemIndexForFraction: (Float) -> Int,
    sectionLabelForIndex: (Int) -> String,
    sectionLabelForFraction: (Float) -> String = { fraction ->
        sectionLabelForIndex(itemIndexForFraction(fraction))
    },
    modifier: Modifier = Modifier,
    hideDelayMillis: Long = 900L
) {
    if (totalItems <= 1) return

    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var lastScrubbedIndex by remember { mutableIntStateOf(-1) }

    val firstVisibleIndex by remember(state, totalItems) {
        derivedStateOf {
            val firstVisible = state.layoutInfo.visibleItemsInfo.minByOrNull { it.index }?.index
                ?: state.firstVisibleItemIndex
            firstVisible.coerceIn(0, totalItems - 1)
        }
    }
    val currentFraction by remember(firstVisibleIndex, totalItems) {
        derivedStateOf {
            if (totalItems <= 1) 0f else firstVisibleIndex.toFloat() / (totalItems - 1).toFloat()
        }
    }
    val activeFraction by remember(currentFraction, dragFraction, isDragging) {
        derivedStateOf { if (isDragging) dragFraction else currentFraction }
    }
    val activeLabel by remember(firstVisibleIndex, dragFraction, isDragging) {
        derivedStateOf {
            if (isDragging) sectionLabelForFraction(dragFraction) else sectionLabelForIndex(firstVisibleIndex)
        }
    }

    LaunchedEffect(state.isScrollInProgress, isDragging) {
        if (state.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(hideDelayMillis)
            if (!state.isScrollInProgress && !isDragging) {
                isVisible = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(56.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Timeline fast scrollbar"
            }
    ) {
        val thumbHeight = 52.dp
        val density = LocalDensity.current
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val maxThumbTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbOffsetYPx = (activeFraction * maxThumbTravelPx).roundToInt()

        fun scrubTo(positionY: Float) {
            val fraction = ((positionY - (thumbHeightPx / 2f)) / maxThumbTravelPx).coerceIn(0f, 1f)
            dragFraction = fraction
            val targetIndex = itemIndexForFraction(fraction)
            if (targetIndex != lastScrubbedIndex) {
                lastScrubbedIndex = targetIndex
                scope.launch {
                    state.scrollToItem(targetIndex)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 9.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .onSizeChanged { trackHeightPx = it.height.toFloat() }
            )

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(120)) + scaleIn(
                    animationSpec = tween(120),
                    transformOrigin = TransformOrigin(1f, 0.5f)
                ),
                exit = fadeOut(tween(180)) + scaleOut(
                    animationSpec = tween(180),
                    transformOrigin = TransformOrigin(1f, 0.5f)
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffsetYPx) }
                    .padding(end = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = thumbHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            AnimatedVisibility(
                visible = isVisible && (isDragging || state.isScrollInProgress),
                enter = fadeIn(tween(120)) + scaleIn(tween(120)),
                exit = fadeOut(tween(140)) + scaleOut(tween(140)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-58.dp.roundToPx(), (thumbOffsetYPx - 4.dp.roundToPx()).coerceAtLeast(0)) }
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp
                ) {
                    Text(
                        text = activeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(40.dp)
                    .pointerInput(totalItems, trackHeightPx) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                scrubTo(offset.y)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                scrubTo(change.position.y)
                            },
                            onDragEnd = {
                                isDragging = false
                                lastScrubbedIndex = -1
                            },
                            onDragCancel = {
                                isDragging = false
                                lastScrubbedIndex = -1
                            }
                        )
                    }
            )
        }
    }
}
