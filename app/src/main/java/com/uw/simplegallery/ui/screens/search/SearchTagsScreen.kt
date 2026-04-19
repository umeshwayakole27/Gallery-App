package com.uw.simplegallery.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.uw.simplegallery.R
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.model.MediaType
import com.uw.simplegallery.ui.search.matchesSearchQuery
import com.uw.simplegallery.ui.search.matchesSelectedTags
import com.uw.simplegallery.viewmodel.GalleryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchTagsScreen(
    viewModel: GalleryViewModel,
    onImageClick: (Long) -> Unit,
    onAlbumClick: (String) -> Unit,
    onOpenTagManager: () -> Unit,
    extraBottomPadding: Dp = 0.dp
) {
    val media by viewModel.media.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }
    val selectedTagSet by remember {
        derivedStateOf { selectedTags.toSet() }
    }

    val filteredMedia by remember(media, query, selectedTagSet) {
        derivedStateOf {
            media.filter { item ->
                item.matchesSearchQuery(query) && item.matchesSelectedTags(selectedTagSet)
            }
        }
    }

    val filteredAlbums by remember(albums, query, selectedTagSet) {
        derivedStateOf {
            albums.filter { album ->
                album.matchesSearchQuery(query) && (
                    selectedTagSet.isEmpty() ||
                        album.mediaItems.any { it.matchesSelectedTags(selectedTagSet) }
                    )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.screen_search_tags_title)) },
                actions = {
                    IconButton(onClick = onOpenTagManager) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.action_open_tag_manager)
                        )
                    }
                    if (query.isNotBlank() || selectedTags.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                selectedTags.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.action_clear_filters)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = extraBottomPadding + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(id = R.string.hint_search_media_and_albums)) },
                    placeholder = { Text(stringResource(id = R.string.hint_search_metadata)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (allTags.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.label_tags),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTags.forEach { tag ->
                            val isSelected = tag in selectedTagSet
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        selectedTags.remove(tag)
                                    } else {
                                        selectedTags.add(tag)
                                    }
                                },
                                label = { Text(stringResource(id = R.string.label_hash_tag, tag)) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.label_media_count, filteredMedia.size),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (filteredMedia.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.msg_no_media_matches_filters),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredMedia, key = { it.id }) { mediaItem ->
                    SearchMediaRow(
                        item = mediaItem,
                        onClick = { onImageClick(mediaItem.id) }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.label_albums_count, filteredAlbums.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (filteredAlbums.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.msg_no_albums_match_filters),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredAlbums, key = { it.id }) { albumItem ->
                    SearchAlbumRow(
                        album = albumItem,
                        onClick = { onAlbumClick(albumItem.id) }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TagManagerScreen(
    viewModel: GalleryViewModel,
    onNavigateBack: () -> Unit,
    extraBottomPadding: Dp = 0.dp
) {
    val allTags by viewModel.allTags.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    var renameSourceTag by remember { mutableStateOf<String?>(null) }
    var mergeSourceTag by remember { mutableStateOf<String?>(null) }
    var deleteSourceTag by remember { mutableStateOf<String?>(null) }

    var renameTarget by remember { mutableStateOf("") }
    var mergeTarget by remember { mutableStateOf("") }

    val filteredTags by remember(allTags, query) {
        derivedStateOf {
            if (query.isBlank()) {
                allTags
            } else {
                val q = query.trim().lowercase()
                allTags.filter { it.contains(q) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.screen_tag_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = extraBottomPadding + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.msg_tag_manager_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(id = R.string.label_tags)) },
                    placeholder = { Text(stringResource(id = R.string.hint_search_albums)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (filteredTags.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.msg_tag_manager_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredTags, key = { it }) { tag ->
                    TagManagerRow(
                        tag = tag,
                        onRename = {
                            renameSourceTag = tag
                            renameTarget = tag
                        },
                        onMerge = {
                            mergeSourceTag = tag
                            mergeTarget = ""
                        },
                        onDelete = { deleteSourceTag = tag }
                    )
                }
            }
        }
    }

    renameSourceTag?.let { sourceTag ->
        AlertDialog(
            onDismissRequest = { renameSourceTag = null },
            title = { Text(stringResource(id = R.string.dialog_rename_tag_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(id = R.string.dialog_rename_tag_message, sourceTag))
                    OutlinedTextField(
                        value = renameTarget,
                        onValueChange = { renameTarget = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(id = R.string.hint_rename_tag)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameTagGlobally(sourceTag, renameTarget)
                        renameSourceTag = null
                    },
                    enabled = renameTarget.trim().isNotBlank() && renameTarget.trim() != sourceTag
                ) {
                    Text(stringResource(id = R.string.action_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameSourceTag = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    mergeSourceTag?.let { sourceTag ->
        AlertDialog(
            onDismissRequest = { mergeSourceTag = null },
            title = { Text(stringResource(id = R.string.dialog_merge_tag_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(id = R.string.dialog_merge_tag_message, sourceTag))
                    OutlinedTextField(
                        value = mergeTarget,
                        onValueChange = { mergeTarget = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(id = R.string.hint_merge_target_tag)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.mergeTagsGlobally(sourceTag, mergeTarget)
                        mergeSourceTag = null
                    },
                    enabled = mergeTarget.trim().isNotBlank() && mergeTarget.trim() != sourceTag
                ) {
                    Text(stringResource(id = R.string.action_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = { mergeSourceTag = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    deleteSourceTag?.let { sourceTag ->
        AlertDialog(
            onDismissRequest = { deleteSourceTag = null },
            title = { Text(stringResource(id = R.string.dialog_delete_tag_title)) },
            text = { Text(stringResource(id = R.string.dialog_delete_tag_message, sourceTag)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTagGlobally(sourceTag)
                        deleteSourceTag = null
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSourceTag = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TagManagerRow(
    tag: String,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(id = R.string.label_hash_tag, tag),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRename) {
                    Text(stringResource(id = R.string.action_rename))
                }
                TextButton(onClick = onMerge) {
                    Text(stringResource(id = R.string.action_merge))
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(id = R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMediaRow(
    item: MediaItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.searchSubtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.tags.isNotEmpty()) {
                    Text(
                        text = item.tags.joinToString(prefix = "#", separator = " #"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAlbumRow(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(id = R.string.format_items_count, album.mediaCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val albumTags = album.mediaItems
                    .asSequence()
                    .flatMap { it.tags.asSequence() }
                    .distinct()
                    .take(3)
                    .toList()
                if (albumTags.isNotEmpty()) {
                    Text(
                        text = albumTags.joinToString(prefix = "#", separator = " #"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaItem.searchSubtitle(): String {
    val mediaLabelRes = when (mediaType) {
        is MediaType.Image -> R.string.label_media_type_image
        is MediaType.Video -> R.string.label_media_type_video
    }
    val mediaLabel = stringResource(id = mediaLabelRes)
    val albumName = folderName
        ?.trimEnd('/')
        ?.substringAfterLast('/')
        ?.ifBlank { stringResource(id = R.string.label_unknown) }
        ?: stringResource(id = R.string.label_unknown)
    val dateLabel = dateTaken?.let {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))
    } ?: stringResource(id = R.string.label_unknown_date)
    return stringResource(
        id = R.string.format_media_subtitle,
        mediaLabel,
        albumName,
        dateLabel
    )
}
