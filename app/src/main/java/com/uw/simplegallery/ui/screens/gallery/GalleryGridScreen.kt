package com.uw.simplegallery.ui.screens.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.ui.selection.GallerySelectionBottomBar
import com.uw.simplegallery.ui.selection.IsSelectingTopBar
import com.uw.simplegallery.ui.selection.getAppBarContentTransition
import com.uw.simplegallery.ui.selection.selectAll
import com.uw.simplegallery.ui.selection.selectItem
import com.uw.simplegallery.ui.selection.toggleItem
import com.uw.simplegallery.ui.selection.unselectAll
import com.uw.simplegallery.ui.selection.unselectItem
import com.uw.simplegallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Main gallery screen displaying a grid of image thumbnails with multi-select support.
 *
 * Multi-select features (adapted from Tulsi Gallery):
 * - Long-press any item to enter selection mode (with haptic feedback)
 * - Tap items to toggle selection during selection mode
 * - Animated scale (0.85x) and border highlight on selected items
 * - Checkmark overlay on selected items
 * - Selection-mode top bar with count and select-all toggle
 * - Floating bottom bar with Share and Delete actions
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

    val snackbarHostState = remember { SnackbarHostState() }
    val isSelecting by remember { derivedStateOf { selectedItemsList.isNotEmpty() } }

    // Grid state for drag-to-select
    val gridState = rememberLazyGridState()

    // Drag-to-select state
    val scrollSpeed = remember { mutableFloatStateOf(0f) }
    val isDragSelecting = remember { mutableStateOf(false) }
    val localDensity = LocalDensity.current
    val haptic = LocalHapticFeedback.current

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

    val title = if (isAlbumMode) albumName ?: "Album" else "Gallery"

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
                        allItems = images,
                        onClearSelection = { selectedItemsList.clear() }
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
                            if (!isAlbumMode) {
                                IconButton(onClick = { /* TODO: Open search */ }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search images"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && images.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (images.isEmpty()) {
                EmptyGalleryState(
                    modifier = Modifier.align(Alignment.Center),
                    isAlbumMode = isAlbumMode
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    // Disable user scroll during drag-select to prevent gesture conflict
                    userScrollEnabled = !isDragSelecting.value,
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
                        .dragSelectionHandler(
                            state = gridState,
                            selectedItemsList = selectedItemsList,
                            allItems = images,
                            scrollSpeed = scrollSpeed,
                            scrollThreshold = with(localDensity) { 40.dp.toPx() },
                            isDragSelecting = isDragSelecting,
                            onDragSelectStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                ) {
                    items(
                        items = images,
                        key = { it.id }
                    ) { image ->
                        val isItemSelected by remember(selectedItemsList.size) {
                            derivedStateOf { selectedItemsList.contains(image) }
                        }

                        SelectableImageGridItem(
                            image = image,
                            isSelected = isItemSelected,
                            isSelectionMode = isSelecting,
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
 * @param isSelectionMode Whether we're in multi-select mode
 * @param onTap Callback for single tap
 */
@Composable
private fun SelectableImageGridItem(
    image: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.uri.toString())
                    .crossfade(true)
                    .build(),
                contentDescription = image.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
 * @param scrollSpeed Mutable float state controlling auto-scroll speed
 * @param scrollThreshold Distance in pixels from viewport edge to trigger auto-scroll
 * @param isDragSelecting Mutable state tracking whether a drag-select is in progress
 * @param onDragSelectStart Callback invoked when a drag-select begins (for haptic feedback)
 */
private fun Modifier.dragSelectionHandler(
    state: LazyGridState,
    selectedItemsList: SnapshotStateList<MediaItem>,
    allItems: List<MediaItem>,
    scrollSpeed: MutableFloatState,
    scrollThreshold: Float,
    isDragSelecting: MutableState<Boolean>,
    onDragSelectStart: () -> Unit = {}
) = pointerInput(allItems) {
    if (allItems.isEmpty()) return@pointerInput

    var initialIndex: Int? = null
    var currentIndex: Int? = null
    var isSelectingMode = true // true = drag selects; false = drag deselects

    // Determine grid column count from visible items
    val itemWidth = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.width
    val numberOfColumns = itemWidth?.let {
        (state.layoutInfo.viewportSize.width / it).coerceAtLeast(1)
    } ?: 3

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
                isSelectingMode = !selectedItemsList.contains(item)
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
    isAlbumMode: Boolean = false
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAlbumMode) "No images in this album" else "No images found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isAlbumMode) "Photos added to this album will appear here"
            else "Your photos and videos will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
