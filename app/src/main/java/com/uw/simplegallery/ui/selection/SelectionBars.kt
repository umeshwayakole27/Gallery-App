package com.uw.simplegallery.ui.selection

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem

/**
 * Top app bar shown when items are selected, replacing the normal top bar.
 * Adapted from Tulsi's IsSelectingTopBar.
 *
 * Shows:
 * - Close button (X) to clear selection
 * - Selected item count
 * - Select All toggle button
 *
 * @param selectedItemsList The current selection state
 * @param allItems All items available for "select all" toggle
 * @param onClearSelection Callback to clear all selections
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsSelectingTopBar(
    selectedItemsList: SnapshotStateList<MediaItem>,
    allItems: List<MediaItem>,
    onClearSelection: () -> Unit
) {
    val selectedCount by remember {
        derivedStateOf { selectedItemsList.size }
    }
    val allSelected by remember {
        derivedStateOf { selectedItemsList.allSelected(allItems) }
    }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.animateContentSize()
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selection"
                    )
                }
                AnimatedContent(
                    targetState = selectedCount,
                    transitionSpec = {
                        (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f))
                            .togetherWith(fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f))
                    },
                    label = "selectedCountAnimation"
                ) { count ->
                    Text(
                        text = "$count selected",
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                }
            }
        },
        actions = {
            // Select All / Deselect All toggle
            IconButton(
                onClick = {
                    if (allSelected) {
                        selectedItemsList.clear()
                    } else {
                        selectedItemsList.selectAll(allItems)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Checklist,
                    contentDescription = if (allSelected) "Deselect all" else "Select all",
                    tint = if (allSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Floating bottom bar container for selection actions.
 * Adapted from Tulsi's FloatingBottomAppBar + IsSelectingBottomAppBar.
 */
@Composable
fun SelectionFloatingBottomBar(
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(76.dp)
                .fillMaxWidth(0.95f)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(percent = 35),
                    spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                .clip(RoundedCornerShape(percent = 35))
                .background(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    shape = RoundedCornerShape(percent = 35)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                content()
            }
        }
    }
}

/**
 * A single action item inside the selection floating bottom bar.
 * Adapted from Tulsi's BottomAppBarItem.
 *
 * @param text Label for the action
 * @param icon Icon for the action
 * @param tint Optional tint for the icon
 * @param onClick Callback when the action is tapped
 */
@Composable
fun BottomBarActionItem(
    text: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * The complete selection-mode bottom bar with Share, Move, Copy, Delete actions.
 * Adapted from Tulsi's MainAppSelectingBottomBar.
 *
 * Always shows a custom confirmation dialog before deleting. On API 30+ with
 * MANAGE_MEDIA, the system's createDeleteRequest auto-approves, so this dialog
 * is the user's only chance to cancel.
 *
 * @param selectedItems The currently selected items (filtered to actual media, no sections)
 * @param onDeleteConfirmed Callback when delete is confirmed
 * @param onClearSelection Callback to clear selection after an action completes
 */
@Composable
fun GallerySelectionBottomBar(
    selectedItems: List<MediaItem>,
    onDeleteConfirmed: (List<MediaItem>) -> Unit,
    onClearSelection: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    SelectionFloatingBottomBar {
        // Share
        BottomBarActionItem(
            text = "Share",
            icon = Icons.Default.Share,
            onClick = {
                shareMediaItems(context, selectedItems)
            }
        )

        // Delete
        BottomBarActionItem(
            text = "Delete",
            icon = Icons.Default.Delete,
            tint = MaterialTheme.colorScheme.error,
            onClick = {
                showDeleteDialog = true
            }
        )
    }

    // Custom delete confirmation dialog — shown on ALL API levels
    if (showDeleteDialog) {
        val count = selectedItems.size
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${if (count == 1) "item" else "$count items"}?") },
            text = {
                Text(
                    "Are you sure you want to delete ${if (count == 1) "this item" else "these $count items"}? " +
                            "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteConfirmed(selectedItems)
                        // On API < 30, deletion is direct — clear selection immediately.
                        // On API 30+, the nav graph clears selection after system confirmation.
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                            onClearSelection()
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
}

/**
 * Share multiple media items via Android's share sheet.
 */
private fun shareMediaItems(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return

    val intent = Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        type = if (items.any { it.mediaType is com.uw.simplegallery.data.model.MediaType.Video }) {
            "video/*"
        } else {
            "image/*"
        }
    }

    val fileUris = ArrayList<Uri>()
    items.forEach { item ->
        fileUris.add(Uri.parse(item.uri))
    }

    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    context.startActivity(Intent.createChooser(intent, "Share via"))
}

/**
 * Top app bar shown when albums are selected, replacing the normal Albums top bar.
 *
 * Shows:
 * - Close button (X) to clear selection
 * - Selected album count
 * - Select All toggle button
 *
 * @param selectedAlbumsList The current album selection state
 * @param allAlbums All albums available for "select all" toggle
 * @param onClearSelection Callback to clear all album selections
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsSelectingAlbumsTopBar(
    selectedAlbumsList: SnapshotStateList<AlbumItem>,
    allAlbums: List<AlbumItem>,
    onClearSelection: () -> Unit
) {
    val selectedCount by remember {
        derivedStateOf { selectedAlbumsList.size }
    }
    val allSelected by remember {
        derivedStateOf { selectedAlbumsList.allAlbumsSelected(allAlbums) }
    }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.animateContentSize()
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selection"
                    )
                }
                AnimatedContent(
                    targetState = selectedCount,
                    transitionSpec = {
                        (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f))
                            .togetherWith(fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f))
                    },
                    label = "albumSelectedCountAnimation"
                ) { count ->
                    Text(
                        text = "$count selected",
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = {
                    if (allSelected) {
                        selectedAlbumsList.clear()
                    } else {
                        selectedAlbumsList.selectAllAlbums(allAlbums)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Checklist,
                    contentDescription = if (allSelected) "Deselect all" else "Select all",
                    tint = if (allSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Selection-mode bottom bar for albums with Delete action.
 *
 * Always shows a custom confirmation dialog before deleting. On API 30+ with
 * MANAGE_MEDIA, the system's createDeleteRequest auto-approves, so this dialog
 * is the user's only chance to cancel.
 *
 * @param selectedAlbums The currently selected albums
 * @param onDeleteConfirmed Callback when delete is confirmed
 * @param onClearSelection Callback to clear selection after an action completes
 */
@Composable
fun AlbumsSelectionBottomBar(
    selectedAlbums: List<AlbumItem>,
    onDeleteConfirmed: (List<AlbumItem>) -> Unit,
    onClearSelection: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    SelectionFloatingBottomBar {
        BottomBarActionItem(
            text = "Delete",
            icon = Icons.Default.Delete,
            tint = MaterialTheme.colorScheme.error,
            onClick = {
                showDeleteDialog = true
            }
        )
    }

    // Custom delete confirmation dialog — shown on ALL API levels
    if (showDeleteDialog) {
        val count = selectedAlbums.size
        val totalMedia = selectedAlbums.sumOf { it.mediaCount }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${if (count == 1) "album" else "$count albums"}?") },
            text = {
                Text(
                    "Are you sure you want to delete ${if (count == 1) "this album" else "these $count albums"} " +
                            "and their $totalMedia media items? This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteConfirmed(selectedAlbums)
                        // On API < 30, deletion is direct — clear selection immediately.
                        // On API 30+, the nav graph clears selection after system confirmation.
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                            onClearSelection()
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
}

/**
 * Animated transition helper for switching between normal and selection app bars.
 * Adapted from Tulsi's getAppBarContentTransition.
 */
fun getAppBarContentTransition(isSelecting: Boolean) =
    if (isSelecting) {
        (slideInVertically(tween(300)) { it } + fadeIn(tween(300)))
            .togetherWith(slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)))
    } else {
        (slideInVertically(tween(300)) { -it } + fadeIn(tween(300)))
            .togetherWith(slideOutVertically(tween(200)) { it } + fadeOut(tween(200)))
    }
