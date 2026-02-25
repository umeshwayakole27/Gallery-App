package com.uw.simplegallery.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen image detail screen with enhanced features.
 *
 * Features:
 * - Horizontal pager for swiping between images
 * - Pinch-to-zoom and pan gestures
 * - Double-tap to zoom in/reset
 * - Tap to toggle top/bottom bar visibility
 * - Image position counter (e.g. "3 / 24")
 * - Share, Delete, and Info actions
 * - Delete confirmation dialog
 * - Info bottom sheet with image metadata
 *
 * @param imageId The ID of the initial image to display
 * @param viewModel The [GalleryViewModel] providing image data
 * @param onNavigateBack Callback when the back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailScreen(
    imageId: Long,
    viewModel: GalleryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Determine which image list to use: album media if available, otherwise all media
    val albumMedia by viewModel.currentAlbumMedia.collectAsState()
    val allMedia by viewModel.media.collectAsState()
    val imageList = if (albumMedia.isNotEmpty()) albumMedia else allMedia

    // Find the initial page index for the tapped image
    val initialIndex = remember(imageId, imageList) {
        imageList.indexOfFirst { it.id == imageId }.coerceAtLeast(0)
    }

    // Pager state for swiping between images
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageList.size }
    )

    // Current media item based on pager position
    val currentMedia = imageList.getOrNull(pagerState.currentPage)

    // Update selected media in ViewModel when page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                imageList.getOrNull(page)?.let { item ->
                    viewModel.selectMedia(item.id)
                }
            }
    }

    // UI state
    var showBars by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (imageList.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = Color.White
            )
        } else {
            // Horizontal pager for swiping between images
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { imageList.getOrNull(it)?.id ?: it }
            ) { page ->
                val mediaItem = imageList.getOrNull(page)
                if (mediaItem != null) {
                    ZoomableImage(
                        mediaItem = mediaItem,
                        onTap = { showBars = !showBars }
                    )
                }
            }

            // Top bar with back button and image name
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = currentMedia?.name ?: "",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (imageList.size > 1) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${imageList.size}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Bottom bar with actions
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Share button
                    IconButton(onClick = {
                        currentMedia?.let { item ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share Media via")
                            )
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share image",
                            tint = Color.White
                        )
                    }

                    // Delete button
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete image",
                            tint = Color.White
                        )
                    }

                    // Info button
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Image info",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && currentMedia != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Image") },
            text = {
                Text(
                    "Are you sure you want to delete \"${currentMedia.name}\"? " +
                            "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedia(currentMedia.id)
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Info bottom sheet
    if (showInfoSheet && currentMedia != null) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = sheetState
        ) {
            ImageInfoContent(
                image = currentMedia,
                onDismiss = {
                    scope.launch {
                        sheetState.hide()
                        showInfoSheet = false
                    }
                }
            )
        }
    }
}

/**
 * A zoomable and pannable image composable.
 *
 * Supports:
 * - Pinch-to-zoom (1x to 5x)
 * - Pan/drag when zoomed in
 * - Double-tap to zoom to 2.5x or reset to 1x
 * - Single tap to toggle bar visibility
 *
 * @param mediaItem The [MediaItem] to display
 * @param onTap Callback when the image is single-tapped
 */
@Composable
private fun ZoomableImage(
    mediaItem: MediaItem,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset zoom state when the media item changes
    LaunchedEffect(mediaItem.id) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(mediaItem.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale

                    if (newScale > 1f) {
                        // Calculate max offset to keep image within bounds
                        val maxOffsetX = (newScale - 1f) * size.width / 2f
                        val maxOffsetY = (newScale - 1f) * size.height / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(mediaItem.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.1f) {
                            // Reset to 1x
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            // Zoom to 2.5x centered on tap point
                            val newScale = 2.5f
                            scale = newScale
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val maxOffsetX = (newScale - 1f) * size.width / 2f
                            val maxOffsetY = (newScale - 1f) * size.height / 2f
                            offsetX =
                                ((centerX - tapOffset.x) * (newScale - 1f)).coerceIn(
                                    -maxOffsetX,
                                    maxOffsetX
                                )
                            offsetY =
                                ((centerY - tapOffset.y) * (newScale - 1f)).coerceIn(
                                    -maxOffsetY,
                                    maxOffsetY
                                )
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(mediaItem.uri)
                .crossfade(true)
                .build(),
            contentDescription = mediaItem.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

/**
 * Content for the image info bottom sheet.
 *
 * Displays metadata about the selected image including name, size,
 * date taken, and album/folder information.
 *
 * @param image The [MediaItem] to show info for
 * @param onDismiss Callback to dismiss the bottom sheet
 */
@Composable
private fun ImageInfoContent(
    image: MediaItem,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Image Details",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        InfoRow(label = "Name", value = image.name)
        image.size?.let { size ->
            InfoRow(label = "Size", value = formatFileSize(size))
        }
        image.dateTaken?.let { date ->
            InfoRow(label = "Date Taken", value = formatDate(date))
        }
        image.folderName?.let { folder ->
            InfoRow(label = "Album", value = folder.trimEnd('/').substringAfterLast('/'))
        }
        InfoRow(
            label = "Type",
            value = when (image.mediaType) {
                is com.uw.simplegallery.data.model.MediaType.Image -> "Image"
                is com.uw.simplegallery.data.model.MediaType.Video -> "Video"
            }
        )
        image.duration?.let { duration ->
            InfoRow(label = "Duration", value = formatDuration(duration))
        }

        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Close")
        }
    }
}

/**
 * A labeled row displaying a single piece of metadata.
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

/**
 * Formats a file size in bytes into a human-readable string.
 */
private fun formatFileSize(sizeBytes: Long): String {
    return when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        sizeBytes < 1024L * 1024 * 1024 -> "%.1f MB".format(sizeBytes.toDouble() / (1024 * 1024))
        else -> "%.2f GB".format(sizeBytes.toDouble() / (1024L * 1024 * 1024))
    }
}

/**
 * Formats a timestamp into a readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Formats a duration in milliseconds into a readable time string (e.g. "1:23" or "1:02:30").
 */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
