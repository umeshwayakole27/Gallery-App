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
    return if (contains(item)) {
        remove(item)
        false
    } else {
        add(item)
        true
    }
}

/** Select a single item if not already selected. */
fun SnapshotStateList<MediaItem>.selectItem(item: MediaItem) {
    if (!contains(item)) add(item)
}

/** Unselect a single item if present. */
fun SnapshotStateList<MediaItem>.unselectItem(item: MediaItem) {
    remove(item)
}

/**
 * Select all items from the given list that are not already selected.
 */
fun SnapshotStateList<MediaItem>.selectAll(items: List<MediaItem>) {
    val toAdd = items.filter { !contains(it) }
    addAll(toAdd)
}

/**
 * Unselect all items from the given list.
 */
fun SnapshotStateList<MediaItem>.unselectAll(items: List<MediaItem>) {
    removeAll(items.toSet())
}

/**
 * Returns true if every item in [items] is currently selected.
 */
fun SnapshotStateList<MediaItem>.allSelected(items: List<MediaItem>): Boolean {
    return items.isNotEmpty() && items.all { contains(it) }
}

// ── AlbumItem selection ─────────────────────────────────────────────────

/** Toggle the selection of a single album. Returns true if the album is now selected. */
fun SnapshotStateList<AlbumItem>.toggleAlbum(album: AlbumItem): Boolean {
    return if (contains(album)) {
        remove(album)
        false
    } else {
        add(album)
        true
    }
}

/** Select a single album if not already selected. */
fun SnapshotStateList<AlbumItem>.selectAlbum(album: AlbumItem) {
    if (!contains(album)) add(album)
}

/** Unselect a single album if present. */
fun SnapshotStateList<AlbumItem>.unselectAlbum(album: AlbumItem) {
    remove(album)
}

/**
 * Select all albums from the given list that are not already selected.
 */
fun SnapshotStateList<AlbumItem>.selectAllAlbums(albums: List<AlbumItem>) {
    val toAdd = albums.filter { !contains(it) }
    addAll(toAdd)
}

/**
 * Returns true if every album in [albums] is currently selected.
 */
fun SnapshotStateList<AlbumItem>.allAlbumsSelected(albums: List<AlbumItem>): Boolean {
    return albums.isNotEmpty() && albums.all { contains(it) }
}
