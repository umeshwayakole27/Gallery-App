package com.uw.simplegallery.ui.selection

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem

/**
 * Extension functions for managing media item selection in a [SnapshotStateList].
 *
 * Adapted from Tulsi Gallery's Selection.kt. The timeline UI may include date
 * headers, but selection still tracks only concrete [MediaItem] entries.
 * Items are added or removed directly without section tracking state.
 *
 * The [SnapshotStateList] itself serves as the selection state — any mutations
 * trigger Compose recomposition automatically.
 */

// ── MediaItem selection ─────────────────────────────────────────────────

/** Toggle the selection of a single item. Returns true if the item is now selected. */
fun SnapshotStateList<MediaItem>.toggleItem(item: MediaItem): Boolean {
    val existingIndex = indexOfFirst { it.id == item.id }
    return if (existingIndex >= 0) {
        removeAt(existingIndex)
        false
    } else {
        add(item)
        true
    }
}

/** Select a single item if not already selected. */
fun SnapshotStateList<MediaItem>.selectItem(item: MediaItem) {
    if (none { it.id == item.id }) {
        add(item)
    }
}

/** Unselect a single item if present. */
fun SnapshotStateList<MediaItem>.unselectItem(item: MediaItem) {
    val existingIndex = indexOfFirst { it.id == item.id }
    if (existingIndex >= 0) {
        removeAt(existingIndex)
    }
}

/**
 * Select all items from the given list that are not already selected.
 */
fun SnapshotStateList<MediaItem>.selectAll(items: List<MediaItem>) {
    if (items.isEmpty()) return
    val selectedIds = asSequence().map { it.id }.toHashSet()
    val toAdd = items.filter { it.id !in selectedIds }
    addAll(toAdd)
}

/**
 * Unselect all items from the given list.
 */
fun SnapshotStateList<MediaItem>.unselectAll(items: List<MediaItem>) {
    if (items.isEmpty()) return
    val idsToRemove = items.asSequence().map { it.id }.toHashSet()
    removeAll { it.id in idsToRemove }
}

/**
 * Returns true if every item in [items] is currently selected.
 */
fun SnapshotStateList<MediaItem>.allSelected(items: List<MediaItem>): Boolean {
    if (items.isEmpty()) return false
    val selectedIds = asSequence().map { it.id }.toHashSet()
    return items.all { it.id in selectedIds }
}

// ── AlbumItem selection ─────────────────────────────────────────────────

/** Toggle the selection of a single album. Returns true if the album is now selected. */
fun SnapshotStateList<AlbumItem>.toggleAlbum(album: AlbumItem): Boolean {
    val existingIndex = indexOfFirst { it.id == album.id }
    return if (existingIndex >= 0) {
        removeAt(existingIndex)
        false
    } else {
        add(album)
        true
    }
}

/** Select a single album if not already selected. */
fun SnapshotStateList<AlbumItem>.selectAlbum(album: AlbumItem) {
    if (none { it.id == album.id }) {
        add(album)
    }
}

/** Unselect a single album if present. */
fun SnapshotStateList<AlbumItem>.unselectAlbum(album: AlbumItem) {
    val existingIndex = indexOfFirst { it.id == album.id }
    if (existingIndex >= 0) {
        removeAt(existingIndex)
    }
}

/**
 * Select all albums from the given list that are not already selected.
 */
fun SnapshotStateList<AlbumItem>.selectAllAlbums(albums: List<AlbumItem>) {
    if (albums.isEmpty()) return
    val selectedIds = asSequence().map { it.id }.toHashSet()
    val toAdd = albums.filter { it.id !in selectedIds }
    addAll(toAdd)
}

/**
 * Returns true if every album in [albums] is currently selected.
 */
fun SnapshotStateList<AlbumItem>.allAlbumsSelected(albums: List<AlbumItem>): Boolean {
    if (albums.isEmpty()) return false
    val selectedIds = asSequence().map { it.id }.toHashSet()
    return albums.all { it.id in selectedIds }
}
