package com.uw.simplegallery.ui.screens.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.viewmodel.GalleryViewModel

/**
 * Main gallery screen displaying a grid of image thumbnails.
 *
 * Can operate in two modes:
 * 1. **Gallery mode** (default): Shows all media from [GalleryViewModel.media]
 *    with a search icon in the top bar.
 * 2. **Album detail mode**: When [albumId] is provided, shows only media from
 *    that album via [GalleryViewModel.currentAlbumMedia], with a back arrow
 *    and the album name as the title.
 *
 * @param viewModel The [GalleryViewModel] providing image data
 * @param onImageClick Callback when an image is tapped, receives the image ID
 * @param albumId Optional album ID to filter media. When non-null, the screen
 *   shows only media from this album with back navigation.
 * @param onNavigateBack Callback for back navigation in album detail mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryGridScreen(
    viewModel: GalleryViewModel,
    onImageClick: (Long) -> Unit,
    albumId: String? = null,
    onNavigateBack: (() -> Unit)? = null
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
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = images,
                        key = { it.id }
                    ) { image ->
                        ImageGridItem(
                            image = image,
                            onClick = { onImageClick(image.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single image card in the gallery grid.
 *
 * @param image The [MediaItem] to display
 * @param onClick Callback when this item is tapped
 */
@Composable
private fun ImageGridItem(
    image: MediaItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
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
