package com.uw.simplegallery.ui.screens.albums

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.ui.selection.AlbumsSelectionBottomBar
import com.uw.simplegallery.ui.selection.IsSelectingAlbumsTopBar
import com.uw.simplegallery.ui.selection.getAppBarContentTransition
import com.uw.simplegallery.ui.selection.selectAlbum
import com.uw.simplegallery.ui.selection.toggleAlbum
import com.uw.simplegallery.ui.selection.unselectAlbum
import com.uw.simplegallery.viewmodel.GalleryViewModel

/**
 * Albums screen displaying a grid of album covers with multi-select support.
 *
 * Multi-select features (adapted from Tulsi Gallery):
 * - Long-press any album to enter selection mode (with haptic feedback)
 * - Tap albums to toggle selection during selection mode
 * - Animated scale (0.85x) and border highlight on selected albums
 * - Checkmark overlay on selected albums
 * - Selection-mode top bar with count and select-all toggle
 * - Floating bottom bar with Delete action
 * - Back press clears selection
 *
 * @param viewModel The [GalleryViewModel] providing album data
 * @param onAlbumClick Callback when an album is tapped (not in selection mode)
 * @param selectedAlbumsList Shared album selection state from the nav graph
 * @param extraBottomPadding Extra padding at the bottom of the grid to avoid
 *   content being hidden behind the floating nav bar
 * @param onScrolledDown Called with `true` when the user scrolls away from the top,
 *   `false` when at the top — used to collapse/expand the Extended FAB
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: GalleryViewModel,
    onAlbumClick: (String) -> Unit,
    selectedAlbumsList: SnapshotStateList<AlbumItem>,
    extraBottomPadding: Dp = 0.dp,
    onScrolledDown: ((Boolean) -> Unit)? = null
) {
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val gridState = rememberLazyGridState()
    val isSelecting by remember { derivedStateOf { selectedAlbumsList.isNotEmpty() } }
    val selectedAlbumIds by remember {
        derivedStateOf { selectedAlbumsList.asSequence().map { it.id }.toHashSet() }
    }

    // Report scroll state to parent for FAB expand/collapse
    val isScrolledDown by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 }
    }
    onScrolledDown?.invoke(isScrolledDown)

    // Back handler: clear selection on back press when in selection mode
    BackHandler(enabled = isSelecting) {
        selectedAlbumsList.clear()
    }

    Scaffold(
        topBar = {
            // Animated transition between normal top bar and selection top bar
            AnimatedContent(
                targetState = isSelecting,
                transitionSpec = { getAppBarContentTransition(isSelecting) },
                label = "AlbumsTopBarTransition"
            ) { selecting ->
                if (selecting) {
                    IsSelectingAlbumsTopBar(
                        selectedAlbumsList = selectedAlbumsList,
                        allAlbums = albums,
                        onClearSelection = { selectedAlbumsList.clear() }
                    )
                } else {
                    TopAppBar(
                        title = { Text("Albums") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
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
            if (isLoading && albums.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (albums.isEmpty()) {
                // Empty state
                Text(
                    text = "No albums found",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp + if (isSelecting) 80.dp else extraBottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = albums,
                        key = { it.id }
                    ) { album ->
                        val isItemSelected = album.id in selectedAlbumIds

                        SelectableAlbumGridItem(
                            album = album,
                            isSelected = isItemSelected,
                            onTap = {
                                if (isSelecting) {
                                    selectedAlbumsList.toggleAlbum(album)
                                } else {
                                    onAlbumClick(album.id)
                                }
                            },
                            onLongPress = {
                                if (isItemSelected) {
                                    selectedAlbumsList.unselectAlbum(album)
                                } else {
                                    selectedAlbumsList.selectAlbum(album)
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
                AlbumsSelectionBottomBar(
                    selectedAlbums = selectedAlbumsList.toList(),
                    onDeleteConfirmed = { albums ->
                        // Delete all media items in the selected albums
                        val allMediaIds = albums.flatMap { it.mediaItems.map { m -> m.id } }
                        viewModel.deleteMediaItems(allMediaIds)
                    },
                    onClearSelection = { selectedAlbumsList.clear() }
                )
            }
        }
    }
}

/**
 * A single album card in the albums grid with selection support.
 *
 * Adapted from Tulsi's selection pattern:
 * - Long-press to start/toggle selection (with haptic feedback)
 * - Tap to navigate (normal mode) or toggle selection (selection mode)
 * - Scale animation: shrinks to 0.85x when selected
 * - Primary-colored border on selected items
 * - Checkmark overlay on selected items
 *
 * @param album The [AlbumItem] to display
 * @param isSelected Whether this album is currently selected
 * @param onTap Callback for single tap
 * @param onLongPress Callback for long press
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableAlbumGridItem(
    album: AlbumItem,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Animated scale: 0.85 when selected, 1.0 when not
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "albumSelectionScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.medium
                        )
                    } else Modifier
                )
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                ),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                // Cover image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(album.coverUri.toString())
                            .crossfade(true)
                            .build(),
                        contentDescription = "${album.name} album cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Image count badge
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "${album.mediaCount}",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Album name
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                )
            }
        }

        // Checkmark overlay when selected — placed outside the Card to avoid
        // ColumnScope/BoxScope receiver ambiguity with AnimatedVisibility
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
