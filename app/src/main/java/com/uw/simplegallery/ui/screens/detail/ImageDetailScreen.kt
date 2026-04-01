package com.uw.simplegallery.ui.screens.detail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import com.uw.simplegallery.ui.components.searchWithGoogleLens
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import java.io.File
import com.uw.simplegallery.R
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.model.MediaType
import com.uw.simplegallery.ui.components.FloatingPill
import com.uw.simplegallery.ui.screens.video.VideoPlayer
import com.uw.simplegallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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

    // Navigate back if the current media item was deleted
    LaunchedEffect(imageList) {
        if (imageList.isEmpty()) {
            onNavigateBack()
        }
    }

    // UI state
    var showBars by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
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
            // Determine if current page is a video
            val isCurrentPageVideo by remember {
                derivedStateOf {
                    imageList.getOrNull(pagerState.currentPage)?.mediaType is MediaType.Video
                }
            }

            // Horizontal pager for swiping between images and videos
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { imageList.getOrNull(it)?.id ?: it },
                userScrollEnabled = !isCurrentPageVideo
            ) { page ->
                val mediaItem = imageList.getOrNull(page)
                if (mediaItem != null) {
                    if (mediaItem.mediaType is MediaType.Video) {
                        // Determine if this page should be playing
                        val shouldPlay by remember(pagerState) {
                            derivedStateOf {
                                abs(pagerState.currentPageOffsetFraction) < 0.5f
                                        && pagerState.currentPage == page
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            VideoPlayer(
                                videoUri = mediaItem.uri.toUri(),
                                videoName = mediaItem.name,
                                shouldAutoPlay = shouldPlay,
                                onControlsVisibilityChanged = { visible ->
                                    showBars = visible
                                }
                            )
                        }
                    } else {
                        ZoomableImage(
                            mediaItem = mediaItem,
                            onTap = { showBars = !showBars }
                        )
                    }
                }
            }

            // Top bar with back button and image name
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(350)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(400)
                ) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
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
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                currentMedia?.let {
                                    newName = it.name
                                    showRenameDialog = true
                                }
                            }
                            .padding(4.dp)
                    ) {
                        Text(
                            text = currentMedia?.name ?: "",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
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

                    // Google Lens button (only for images)
                    if (currentMedia?.mediaType is MediaType.Image) {
                        IconButton(onClick = {
                            currentMedia.let {
                                searchWithGoogleLens(it.uri, context)
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.lens),
                                contentDescription = "Search with Google Lens",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More actions",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Use as") },
                                    onClick = {
                                        showOverflowMenu = false
                                        currentMedia?.let { mediaItem ->
                                            launchUseAsChooser(context, mediaItem)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom bar with actions (Floating Pill)
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(250)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                FloatingPill(
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    // Share button
                    DetailBottomBarItem(
                        text = "Share",
                        icon = Icons.Default.Share,
                        onClick = {
                            currentMedia?.let { item ->
                                val mimeType = when (item.mediaType) {
                                    is MediaType.Video -> "video/*"
                                    is MediaType.Image -> "image/*"
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share via")
                                )
                            }
                        }
                    )

                    // Edit button
                    DetailBottomBarItem(
                        text = "Edit with",
                        icon = Icons.Default.Edit,
                        onClick = {
                            currentMedia?.let { item ->
                                // Use the generic MIME type to match more editing apps
                                val genericMimeType = when (item.mediaType) {
                                    is MediaType.Video -> "video/*"
                                    is MediaType.Image -> "image/*"
                                }

                                val editUri = Uri.parse(item.uri)

                                // ACTION_EDIT is the correct action for editing.
                                // It should now work because we've added <queries> to the manifest.
                                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                    setDataAndType(editUri, genericMimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(editIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No app found to edit this file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    // Delete button
                    DetailBottomBarItem(
                        text = "Delete",
                        icon = Icons.Default.Delete,
                        onClick = { showDeleteDialog = true }
                    )

                    // Info button
                    DetailBottomBarItem(
                        text = "Info",
                        icon = Icons.Default.Info,
                        onClick = { showInfoSheet = true }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog — shown on ALL API levels.
    // On API 30+ with MANAGE_MEDIA, the system's createDeleteRequest auto-approves,
    // so this custom dialog is the user's only chance to cancel.
    if (showDeleteDialog && currentMedia != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                val mediaLabel = when (currentMedia.mediaType) {
                    is MediaType.Video -> "Video"
                    is MediaType.Image -> "Image"
                }
                Text("Delete $mediaLabel")
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${currentMedia.name}\"? " +
                            "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteMedia(currentMedia.id)
                        // On API < 30, deletion is direct — navigate back immediately.
                        // On API 30+, the nav graph handles navigation after system confirmation.
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                            onNavigateBack()
                        }
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

    // Rename dialog
    if (showRenameDialog && currentMedia != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Media") },
            text = {
                Column {
                    Text("Enter a new name for the file:", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != currentMedia.name) {
                            viewModel.renameMedia(currentMedia.id, newName)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
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

private fun launchUseAsChooser(context: Context, mediaItem: MediaItem) {
    val mediaUri = Uri.parse(mediaItem.uri)
    val mimeType = mediaItem.mimeType?.takeIf { it.startsWith("image/") } ?: "image/*"

    val useAsIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(mediaUri, mimeType)
        putExtra("mimeType", mimeType)
        putExtra(Intent.EXTRA_STREAM, mediaUri)
        clipData = ClipData.newUri(context.contentResolver, mediaItem.name, mediaUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(useAsIntent, "Use as"))
    } catch (_: ActivityNotFoundException) {
        val setWallpaperIntent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
            setDataAndType(mediaUri, mimeType)
            putExtra(Intent.EXTRA_STREAM, mediaUri)
            clipData = ClipData.newUri(context.contentResolver, mediaItem.name, mediaUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(Intent.createChooser(setWallpaperIntent, "Set wallpaper"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app found for Use as", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Individual action item for the detail screen's floating bottom bar.
 */
@Composable
private fun DetailBottomBarItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
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
            text = when (image.mediaType) {
                is MediaType.Video -> "Video Details"
                is MediaType.Image -> "Image Details"
            },
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
