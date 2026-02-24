package com.uw.simplegallery.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.uw.simplegallery.data.model.ImageItem
import com.uw.simplegallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// TODO: Implement pinch-to-zoom using transformable modifier
// TODO: Wire up Share intent to system share sheet
// TODO: Implement Delete with confirmation dialog and ViewModel call
// TODO: Show EXIF metadata (date, resolution, size) in Info bottom sheet
// TODO: Add left/right swipe to navigate between images
// TODO: Add accessibility labels (contentDescription) to all images

/**
 * Full-screen image detail screen with action controls.
 *
 * Features:
 * - Full-screen image display via Coil's AsyncImage
 * - Top app bar with back navigation
 * - Bottom bar with Share, Delete, and Info actions
 * - Delete confirmation dialog
 * - Info bottom sheet with image metadata
 *
 * @param imageId The ID of the image to display
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
    val selectedImage by viewModel.selectedImage.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Select the image when the screen is first displayed
    LaunchedEffect(imageId) {
        viewModel.selectImage(imageId)
    }

    val image = selectedImage

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(image?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Share button
                    IconButton(onClick = {
                        // TODO: Wire up Share intent to system share sheet
                        // Example:
                        // val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        //     type = "image/*"
                        //     putExtra(Intent.EXTRA_STREAM, image?.uri)
                        // }
                        // context.startActivity(Intent.createChooser(shareIntent, "Share image"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share image"
                        )
                    }

                    // Delete button
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete image"
                        )
                    }

                    // Info button
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Image info"
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
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                // TODO: Implement pinch-to-zoom using transformable modifier
                // TODO: Add left/right swipe to navigate between images
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(image.uri.toString())
                        .crossfade(true)
                        .build(),
                    contentDescription = image.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && image != null) {
        // TODO: Implement Delete with confirmation dialog and ViewModel call
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Image") },
            text = { Text("Are you sure you want to delete \"${image.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteImage(image.id)
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
    if (showInfoSheet && image != null) {
        // TODO: Show EXIF metadata (date, resolution, size) in Info bottom sheet
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = sheetState
        ) {
            ImageInfoContent(
                image = image,
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
 * Content for the image info bottom sheet.
 *
 * Displays basic metadata about the selected image.
 * EXIF data is not yet implemented.
 *
 * @param image The [ImageItem] to show info for
 * @param onDismiss Callback to dismiss the bottom sheet
 */
@Composable
private fun ImageInfoContent(
    image: ImageItem,
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
        InfoRow(label = "Resolution", value = "${image.width} x ${image.height}")
        InfoRow(
            label = "Size",
            value = formatFileSize(image.size)
        )
        InfoRow(
            label = "Date Taken",
            value = formatDate(image.dateTaken)
        )
        InfoRow(label = "Album", value = image.albumName)

        // TODO: Show EXIF metadata (date, resolution, size) in Info bottom sheet
        // Additional EXIF fields: camera model, aperture, ISO, GPS coordinates, etc.

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
            .padding(vertical = 4.dp),
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
            fontWeight = FontWeight.Medium
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
        else -> "%.1f MB".format(sizeBytes.toDouble() / (1024 * 1024))
    }
}

/**
 * Formats a timestamp into a readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
