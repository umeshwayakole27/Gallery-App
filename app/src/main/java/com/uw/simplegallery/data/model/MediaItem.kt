package com.uw.simplegallery.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a single media item (image or video) in the gallery.
 *
 * @param id Unique identifier from MediaStore
 * @param name Display name of the media file
 * @param uri Content URI pointing to the media item
 * @param dateTaken Timestamp when the media was captured (millis since epoch)
 * @param mediaType Whether this item is an image or video
 * @param folderName Name of the album (folder/bucket) this item belongs to
 * @param size File size in bytes
 * @param duration Duration in milliseconds (only relevant for videos)
 */
@Serializable
data class MediaItem(
    val id: Long,
    val name: String,
    val uri: String,
    val mimeType: String?,
    val dateTaken: Long?,
    val mediaType: MediaType,
    val folderName: String?,
    val size: Long?,
    val duration: Long? = null,
    val tags: List<String> = emptyList()
)

@Serializable
sealed interface MediaType {
    @Serializable
    data object Image : MediaType
    @Serializable
    data object Video : MediaType
}
