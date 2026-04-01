package com.uw.simplegallery.data.model

/**
 * Represents an album (folder/bucket) containing media items.
 *
 * @param id Unique identifier for the album (folder path or "ALL_PHOTOS")
 * @param name Display name of the album
 * @param mediaItems List of media items inside this album
 */
data class AlbumItem(
    val id: String,                // Folder path or unique ID
    val name: String,              // Folder display name
    val mediaItems: List<MediaItem> // List of media items inside the album
) {
    companion object {
        const val ALL_PHOTOS_ID = "ALL_PHOTOS"
        const val ALL_PHOTOS_NAME = "All Photos"
        const val UNKNOWN_ALBUM_NAME = "Unknown"
    }

    val mediaCount: Int
        get() = mediaItems.size

    val coverUri: String?
        get() = mediaItems.firstOrNull()?.uri
}
