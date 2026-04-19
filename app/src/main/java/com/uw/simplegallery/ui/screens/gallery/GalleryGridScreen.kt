package com.uw.simplegallery.ui.screens.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toIntRect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.uw.simplegallery.R
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.model.MediaType
import com.uw.simplegallery.ui.components.AnimatedFastScrollbar
import com.uw.simplegallery.ui.selection.GallerySelectionBottomBar
import com.uw.simplegallery.ui.selection.IsSelectingTopBar
import com.uw.simplegallery.ui.selection.getAppBarContentTransition
import com.uw.simplegallery.ui.selection.selectAll
import com.uw.simplegallery.ui.selection.selectItem
import com.uw.simplegallery.ui.selection.toggleItem
import com.uw.simplegallery.ui.selection.unselectAll
import com.uw.simplegallery.ui.selection.unselectItem
import com.uw.simplegallery.ui.search.matchesSearchQuery
import com.uw.simplegallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Main gallery screen displaying a date-grouped timeline grid with multi-select support.
 *
 * Multi-select features (adapted from Tulsi Gallery):
 * - Long-press any item to enter selection mode (with haptic feedback)
 * - Tap items to toggle selection during selection mode
 * - Animated scale (0.85x) and border highlight on selected items
 * - Checkmark overlay on selected items
 * - Selection-mode top bar with count and select-all toggle
 * - Floating bottom bar with Share and Delete actions
 * - Persistent timeline section label + draggable fast-scrollbar
 * - Back press clears selection
 *
 * Can operate in two modes:
 * 1. **Gallery mode** (default): Shows all media from [GalleryViewModel.media]
 * 2. **Album detail mode**: When [albumId] is provided, shows only media from
 *    that album via [GalleryViewModel.currentAlbumMedia]
 *
 * @param viewModel The [GalleryViewModel] providing image data
 * @param onImageClick Callback when an image is tapped (not in selection mode)
 * @param selectedItemsList Shared selection state from the nav graph
 * @param albumId Optional album ID to filter media
 * @param onNavigateBack Callback for back navigation in album detail mode
 * @param extraBottomPadding Extra padding at the bottom for floating nav bar clearance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryGridScreen(
    viewModel: GalleryViewModel,
    onImageClick: (Long) -> Unit,
    selectedItemsList: SnapshotStateList<MediaItem>,
    albumId: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    extraBottomPadding: Dp = 0.dp
) {
    val isAlbumMode = albumId != null

    // Apply album filter when in album mode
    LaunchedEffect(albumId) {
        if (albumId != null) {
            viewModel.setAlbumFilter(albumId)
        }
    }

    val images by if (isAlbumMode) {
        viewModel.currentAlbumMedia.collectAsState()
    } else {
        viewModel.media.collectAsState()
    }

    val albumName by viewModel.currentAlbumName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var isSearchMode by rememberSaveable(albumId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(albumId) { mutableStateOf("") }
    val filteredImages by remember(images, searchQuery) {
        derivedStateOf {
            images.filter { it.matchesSearchQuery(searchQuery) }
        }
    }
    val timelineUiModel = remember(filteredImages) { buildTimelineUiModel(filteredImages) }

    val snackbarHostState = remember { SnackbarHostState() }
    val isSelecting by remember { derivedStateOf { selectedItemsList.isNotEmpty() } }
    val selectedItemIds by remember {
        derivedStateOf { selectedItemsList.asSequence().map { it.id }.toHashSet() }
    }

    // Grid state for drag-to-select
    val gridState = rememberLazyGridState()

    // Drag-to-select state
    val scrollSpeed = remember { mutableFloatStateOf(0f) }
    val isDragSelecting = remember { mutableStateOf(false) }
    val localDensity = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // Pinch-to-zoom column count state
    // Column count persists across recompositions but not process death (use rememberSaveable)
    var columnCount by rememberSaveable { mutableIntStateOf(3) }
    var hasUserAdjustedColumns by rememberSaveable { mutableStateOf(false) }
    // Live pinch scale: applied via graphicsLayer during the gesture for real-time feedback.
    // When the user pinches, this smoothly scales the entire grid visually.
    // When the pinch ends or crosses a column-count threshold, it springs back to 1.0.
    val pinchScale = remember { mutableFloatStateOf(1f) }
    // Animatable used only for the spring-back animation when the pinch gesture ends
    val pinchScaleAnimatable = remember { Animatable(1f) }
    // Track cumulative pinch scale within a single gesture (for threshold detection)
    val cumulativeScale = remember { mutableFloatStateOf(1f) }
    // Whether a pinch gesture is currently active
    var isPinching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Sync animatable value back to pinchScale state when spring-back animates
    LaunchedEffect(Unit) {
        snapshotFlow { pinchScaleAnimatable.value }
            .collect { pinchScale.floatValue = it }
    }

    // Auto-scroll coroutine: continuously scrolls when drag is near viewport edges
    LaunchedEffect(scrollSpeed.floatValue) {
        if (scrollSpeed.floatValue != 0f) {
            while (isActive) {
                gridState.scrollBy(scrollSpeed.floatValue)
                delay(10)
            }
        }
    }

    // Back handler: clear selection on back press when in selection mode
    BackHandler(enabled = isSelecting) {
        selectedItemsList.clear()
    }

    // Show error in snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val title = if (isAlbumMode) {
        albumName ?: stringResource(id = R.string.screen_album_fallback_title)
    } else {
        stringResource(id = R.string.screen_gallery_title)
    }
    val searchHint = if (isAlbumMode) {
        stringResource(id = R.string.hint_search_in_album)
    } else {
        stringResource(id = R.string.hint_search_media)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Animated transition between normal top bar and selection top bar
            AnimatedContent(
                targetState = isSelecting,
                transitionSpec = { getAppBarContentTransition(isSelecting) },
                label = "TopBarTransition"
            ) { selecting ->
                if (selecting) {
                    IsSelectingTopBar(
                        selectedItemsList = selectedItemsList,
                        allItems = filteredImages,
                        onClearSelection = { selectedItemsList.clear() }
                    )
                } else {
                    if (isSearchMode) {
                        TopAppBar(
                            title = {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    placeholder = { Text(searchHint) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            navigationIcon = {
                                if (isAlbumMode && onNavigateBack != null) {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Navigate back"
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        isSearchMode = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(id = R.string.action_close_search)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    } else {
                        TopAppBar(
                            title = { Text(title) },
                            navigationIcon = {
                                if (isAlbumMode && onNavigateBack != null) {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Navigate back"
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            actions = {
                                IconButton(onClick = { isSearchMode = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(id = R.string.action_search_images)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && filteredImages.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (filteredImages.isEmpty()) {
                EmptyGalleryState(
                    modifier = Modifier.align(Alignment.Center),
                    isAlbumMode = isAlbumMode,
                    isSearchActive = searchQuery.isNotBlank()
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val adaptiveColumnCount = remember(maxWidth) {
                        (maxWidth / 132.dp).toInt().coerceIn(2, 6)
                    }
                    LaunchedEffect(adaptiveColumnCount, hasUserAdjustedColumns) {
                        if (!hasUserAdjustedColumns) {
                            columnCount = adaptiveColumnCount
                        }
                    }
                    val effectiveColumnCount = columnCount.coerceIn(1, 6)
                    val timelineItems = timelineUiModel.items
                    val currentSectionLabel by remember(gridState, timelineUiModel) {
                        derivedStateOf {
                            timelineUiModel.sectionLabelForTimelineIndex(gridState.firstVisibleItemIndex)
                        }
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(effectiveColumnCount),
                        userScrollEnabled = !isDragSelecting.value && !isPinching,
                        contentPadding = PaddingValues(
                            start = 4.dp,
                            end = 4.dp,
                            top = 4.dp,
                            bottom = 4.dp + if (isSelecting) 80.dp else extraBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = pinchScale.floatValue
                                scaleY = pinchScale.floatValue
                            }
                            .pinchToZoomHandler(
                                currentColumnCount = { columnCount },
                                cumulativeScale = cumulativeScale,
                                pinchScale = pinchScale,
                                onColumnCountChange = { newCount ->
                                    hasUserAdjustedColumns = true
                                    columnCount = newCount
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onPinchStart = { isPinching = true },
                                onPinchEnd = {
                                    isPinching = false
                                    coroutineScope.launch {
                                        pinchScaleAnimatable.snapTo(pinchScale.floatValue)
                                        pinchScaleAnimatable.animateTo(
                                            1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            )
                            .dragSelectionHandler(
                                state = gridState,
                                selectedItemsList = selectedItemsList,
                                allItems = filteredImages,
                                numberOfColumns = effectiveColumnCount,
                                scrollSpeed = scrollSpeed,
                                scrollThreshold = with(localDensity) { 40.dp.toPx() },
                                isDragSelecting = isDragSelecting,
                                onDragSelectStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                    ) {
                        items(
                            items = timelineItems,
                            key = { item ->
                                when (item) {
                                    is TimelineItem.Header -> item.key
                                    is TimelineItem.Photo -> item.mediaItem.id
                                }
                            },
                            span = { item ->
                                when (item) {
                                    is TimelineItem.Header -> GridItemSpan(maxLineSpan)
                                    is TimelineItem.Photo -> GridItemSpan(1)
                                }
                            }
                        ) { item ->
                            when (item) {
                                is TimelineItem.Header -> {
                                    TimelineSectionHeader(
                                        label = item.label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }

                                is TimelineItem.Photo -> {
                                    val image = item.mediaItem
                                    val isItemSelected = image.id in selectedItemIds

                                    SelectableImageGridItem(
                                        image = image,
                                        isSelected = isItemSelected,
                                        onTap = {
                                            if (isSelecting) {
                                                selectedItemsList.toggleItem(image)
                                            } else {
                                                onImageClick(image.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (currentSectionLabel.isNotBlank()) {
                        CurrentTimelineSectionChip(
                            label = currentSectionLabel,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 12.dp, top = 10.dp)
                        )
                    }

                    AnimatedFastScrollbar(
                        state = gridState,
                        totalItems = timelineItems.size,
                        itemIndexForFraction = timelineUiModel::timelineIndexForFraction,
                        sectionLabelForIndex = timelineUiModel::sectionLabelForTimelineIndex,
                        sectionLabelForFraction = timelineUiModel::sectionLabelForFraction,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                top = 8.dp,
                                bottom = if (isSelecting) 88.dp else extraBottomPadding + 8.dp
                            )
                    )
                }
            }

            // Selection mode floating bottom bar with actions
            AnimatedVisibility(
                visible = isSelecting,
                enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GallerySelectionBottomBar(
                    selectedItems = selectedItemsList.toList(),
                    onDeleteConfirmed = { items ->
                        viewModel.deleteMediaItems(items.map { it.id })
                    },
                    onClearSelection = { selectedItemsList.clear() }
                )
            }
        }
    }
}

@Composable
private fun TimelineSectionHeader(
    label: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
private fun CurrentTimelineSectionChip(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(min = 88.dp, max = 220.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * A single image card in the gallery grid with selection support.
 *
 * Adapted from Tulsi's MediaStoreItem:
 * - Long-press to start/toggle selection is handled by the grid-level
 *   [dragSelectionHandler] (which uses [detectDragGesturesAfterLongPress])
 * - Tap to navigate (normal mode) or toggle selection (selection mode)
 * - Scale animation: shrinks to 0.85x when selected
 * - Primary-colored border on selected items
 * - Checkmark overlay on selected items
 *
 * @param image The [MediaItem] to display
 * @param isSelected Whether this item is currently selected
 * @param onTap Callback for single tap
 */
@Composable
private fun SelectableImageGridItem(
    image: MediaItem,
    isSelected: Boolean,
    onTap: () -> Unit
) {
    // Animated scale: 0.85 when selected, 1.0 when not (adapted from Tulsi's 0.8)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "selectionScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        )
                    } else Modifier
                )
                .clickable(onClick = onTap),
            shape = MaterialTheme.shapes.small,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val placeholderPainter = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
            val errorPainter = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.uri.toString())
                    .crossfade(true)
                    .build(),
                placeholder = placeholderPainter,
                error = errorPainter,
                contentDescription = image.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Video indicator: movie icon + duration badge at bottom-left
        if (image.mediaType is MediaType.Video) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_movie_filled),
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                image.duration?.let { durationMs ->
                    val totalSeconds = durationMs / 1000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    Text(
                        text = "%d:%02d".format(minutes, seconds),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Checkmark overlay when selected
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ── Pinch-to-Zoom Column Count ──────────────────────────────────────────
// Two-finger pinch gesture on the grid changes the column count with smooth
// visual feedback:
//
// 1. **Live scale**: During the pinch, the entire grid visually scales via
//    graphicsLayer (scaleX/scaleY), giving immediate, fluid feedback.
// 2. **Threshold snap**: When cumulative scale crosses a threshold (>1.4 to
//    zoom in, <0.71 to zoom out), the column count changes by 1 and the
//    live scale resets — the grid re-layouts at the new column count.
// 3. **Spring settle**: When the pinch ends, pinchScale animates back to 1.0
//    with a spring for a satisfying bounce/settle.
//
// Uses awaitEachGesture to only activate when 2+ pointers are down,
// avoiding conflict with single-finger drag-to-select.

private const val MIN_COLUMNS = 1
private const val MAX_COLUMNS = 6
private const val ZOOM_IN_THRESHOLD = 1.4f   // cumulative scale to decrease columns
private const val ZOOM_OUT_THRESHOLD = 0.71f  // cumulative scale to increase columns
// Clamp live pinch scale so the grid doesn't scale too far before snapping
private const val MAX_PINCH_SCALE = 1.35f
private const val MIN_PINCH_SCALE = 0.75f

/**
 * Modifier that enables pinch-to-zoom column count changes on the grid.
 *
 * Drives [pinchScale] in real-time during the gesture so the grid visually
 * scales as the user pinches. When cumulative scale crosses a threshold,
 * calls [onColumnCountChange] and resets both scales. When the gesture ends,
 * calls [onPinchEnd] so the caller can spring-animate [pinchScale] back to 1.0.
 *
 * @param currentColumnCount Function returning the latest column count
 * @param cumulativeScale Mutable float state tracking cumulative scale within a gesture
 * @param pinchScale Mutable float state controlling the live visual scale of the grid
 * @param onColumnCountChange Callback with the new column count
 * @param onPinchStart Called when a pinch gesture begins
 * @param onPinchEnd Called when the pinch gesture ends (for spring settle animation)
 */
private fun Modifier.pinchToZoomHandler(
    currentColumnCount: () -> Int,
    cumulativeScale: MutableFloatState,
    pinchScale: MutableFloatState,
    onColumnCountChange: (Int) -> Unit,
    onPinchStart: () -> Unit = {},
    onPinchEnd: () -> Unit = {}
) = pointerInput(Unit) {
    awaitEachGesture {
        // Wait for the first pointer down
        awaitFirstDown(requireUnconsumed = false)
        cumulativeScale.floatValue = 1f
        var pinchStarted = false

        do {
            val event = awaitPointerEvent()

            // Only process when 2+ fingers are down (pinch gesture)
            if (event.changes.size >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    if (!pinchStarted) {
                        pinchStarted = true
                        onPinchStart()
                    }

                    cumulativeScale.floatValue *= zoom

                    // Drive the live visual scale (clamped so it doesn't go too far)
                    val newVisualScale = (pinchScale.floatValue * zoom)
                        .coerceIn(MIN_PINCH_SCALE, MAX_PINCH_SCALE)
                    pinchScale.floatValue = newVisualScale

                    val columnCount = currentColumnCount().coerceIn(MIN_COLUMNS, MAX_COLUMNS)

                    // Zoom in (pinch out) → fewer columns
                    if (cumulativeScale.floatValue > ZOOM_IN_THRESHOLD && columnCount > MIN_COLUMNS) {
                        onColumnCountChange(columnCount - 1)
                        cumulativeScale.floatValue = 1f
                        pinchScale.floatValue = 1f // reset visual scale for the new layout
                    }
                    // Zoom out (pinch in) → more columns
                    else if (cumulativeScale.floatValue < ZOOM_OUT_THRESHOLD && columnCount < MAX_COLUMNS) {
                        onColumnCountChange(columnCount + 1)
                        cumulativeScale.floatValue = 1f
                        pinchScale.floatValue = 1f
                    }

                    // Consume to prevent scroll interference during pinch
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        // Gesture ended
        if (pinchStarted) {
            onPinchEnd()
        }
    }
}

// ── Drag-to-Select ──────────────────────────────────────────────────────
// Adapted from Tulsi Gallery's PhotoGridView.kt dragSelectionHandler().
// Uses Compose's pointerInput + detectDragGestures to select/deselect items
// by dragging across the LazyVerticalGrid. Auto-scrolls near viewport edges.

/**
 * Modifier that enables long-press-and-drag to select on a [LazyVerticalGrid].
 *
 * A long press on any item enters selection mode and begins drag-to-select.
 * Dragging across the grid selects (or deselects) all items between the
 * initial long-press position and the current drag position. The grid
 * auto-scrolls when the drag reaches near the top or bottom edges.
 *
 * Uses [detectDragGesturesAfterLongPress] so the initial long-press + drag
 * is handled as a single continuous gesture, allowing multi-select on the
 * very first interaction without requiring a second gesture.
 *
 * Adapted from Tulsi Gallery's `dragSelectionHandler()`.
 *
 * @param state The [LazyGridState] of the grid
 * @param selectedItemsList The selection state list
 * @param allItems All items displayed in the grid (for range sub-listing)
 * @param numberOfColumns Current number of photo columns in the grid
 * @param scrollSpeed Mutable float state controlling auto-scroll speed
 * @param scrollThreshold Distance in pixels from viewport edge to trigger auto-scroll
 * @param isDragSelecting Mutable state tracking whether a drag-select is in progress
 * @param onDragSelectStart Callback invoked when a drag-select begins (for haptic feedback)
 */
private fun Modifier.dragSelectionHandler(
    state: LazyGridState,
    selectedItemsList: SnapshotStateList<MediaItem>,
    allItems: List<MediaItem>,
    numberOfColumns: Int,
    scrollSpeed: MutableFloatState,
    scrollThreshold: Float,
    isDragSelecting: MutableState<Boolean>,
    onDragSelectStart: () -> Unit = {}
) = pointerInput(allItems, numberOfColumns) {
    if (allItems.isEmpty()) return@pointerInput

    var initialIndex: Int? = null
    var currentIndex: Int? = null
    var isSelectingMode = true // true = drag selects; false = drag deselects

    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            isDragSelecting.value = true
            onDragSelectStart()
            val idx = state.getGridItemIndex(offset, allItems, numberOfColumns)
            if (idx != null && idx < allItems.size) {
                val item = allItems[idx]
                initialIndex = idx
                currentIndex = idx
                // Determine mode: if tapped item is already selected, drag deselects
                isSelectingMode = selectedItemsList.none { it.id == item.id }
                if (isSelectingMode) {
                    selectedItemsList.selectItem(item)
                } else {
                    selectedItemsList.unselectItem(item)
                }
            }
        },
        onDrag = { change, _ ->
            if (initialIndex != null) {
                // Auto-scroll when near edges
                val distFromBottom = state.layoutInfo.viewportSize.height - change.position.y
                val distFromTop = change.position.y
                scrollSpeed.floatValue = when {
                    distFromBottom < scrollThreshold -> scrollThreshold - distFromBottom
                    distFromTop < scrollThreshold -> -scrollThreshold + distFromTop
                    else -> 0f
                }

                val newIndex = state.getGridItemIndex(
                    change.position, allItems, numberOfColumns
                )
                if (newIndex != null && newIndex < allItems.size
                    && newIndex != currentIndex && initialIndex != null
                ) {
                    // Undo the previous range
                    val prevStart = minOf(initialIndex!!, currentIndex!!)
                    val prevEnd = maxOf(initialIndex!!, currentIndex!!)
                    val previousItems = allItems.subList(prevStart, (prevEnd + 1).coerceAtMost(allItems.size))
                    if (isSelectingMode) {
                        selectedItemsList.unselectAll(previousItems)
                    } else {
                        selectedItemsList.selectAll(previousItems)
                    }

                    // Apply the new range
                    val newStart = minOf(initialIndex!!, newIndex)
                    val newEnd = maxOf(initialIndex!!, newIndex)
                    val newItems = allItems.subList(newStart, (newEnd + 1).coerceAtMost(allItems.size))
                    if (isSelectingMode) {
                        selectedItemsList.selectAll(newItems)
                    } else {
                        selectedItemsList.unselectAll(newItems)
                    }

                    currentIndex = newIndex
                }
            }
        },
        onDragCancel = {
            initialIndex = null
            currentIndex = null
            scrollSpeed.floatValue = 0f
            isDragSelecting.value = false
        },
        onDragEnd = {
            initialIndex = null
            currentIndex = null
            scrollSpeed.floatValue = 0f
            isDragSelecting.value = false
        }
    )
}

/**
 * Hit-tests the [LazyGridState] to find which item index is at the given [offset].
 *
 * Adapted from Tulsi Gallery's `getGridItemAtOffset()`. Stretches item bounds
 * horizontally to handle incomplete rows (e.g., if a row has fewer items than columns,
 * dragging into the empty space still registers against the last item in that row).
 *
 * @param offset The pointer position relative to the grid's viewport
 * @param allItems The list of all items (used to map keys back to indices)
 * @param numberOfColumns Number of columns in the grid
 * @return The index of the item at the offset, or null if none found
 */
private fun LazyGridState.getGridItemIndex(
    offset: Offset,
    allItems: List<MediaItem>,
    numberOfColumns: Int
): Int? {
    if (allItems.isEmpty()) return null

    // Try to find the item directly under the pointer
    for (stretch in 1..numberOfColumns) {
        val found = layoutInfo.visibleItemsInfo.find { itemInfo ->
            val rect = itemInfo.size.toIntRect().let { r ->
                IntRect(
                    top = r.top,
                    bottom = r.bottom,
                    left = r.left,
                    right = r.right * stretch
                )
            }
            rect.contains(offset.round() - itemInfo.offset)
        }
        if (found != null) {
            // The key is the MediaItem.id (Long), find the index in allItems
            val key = found.key as? Long ?: return null
            val idx = allItems.indexOfFirst { it.id == key }
            return if (idx >= 0) idx else null
        }
    }
    return null
}

/**
 * Empty state displayed when no images are found.
 *
 * @param isAlbumMode Whether the screen is in album detail mode
 */
@Composable
private fun EmptyGalleryState(
    modifier: Modifier = Modifier,
    isAlbumMode: Boolean = false,
    isSearchActive: Boolean = false
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when {
                isSearchActive && isAlbumMode -> stringResource(id = R.string.msg_no_matches_in_album)
                isSearchActive -> stringResource(id = R.string.msg_no_matching_media)
                isAlbumMode -> stringResource(id = R.string.msg_no_images_in_album)
                else -> stringResource(id = R.string.msg_no_images_found)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                isSearchActive -> stringResource(id = R.string.msg_try_search_metadata)
                isAlbumMode -> stringResource(id = R.string.msg_photos_in_album_will_appear)
                else -> stringResource(id = R.string.msg_photos_videos_will_appear)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
