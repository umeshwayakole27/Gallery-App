package com.uw.simplegallery.data.model

import android.net.Uri

/**
 * Represents a single image in the gallery.
 *
 * @param id Unique identifier from MediaStore
 * @param uri Content URI pointing to the image
 * @param name Display name of the image file
 * @param dateTaken Timestamp when the image was taken (millis since epoch)
 * @param size File size in bytes
 * @param width Image width in pixels
 * @param height Image height in pixels
 * @param albumName Name of the album (folder/bucket) this image belongs to
 */
data class ImageItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val albumName: String
) {
    companion object {
        /**
         * Creates placeholder [ImageItem] instances for the prototype UI.
         * Uses picsum.photos for random placeholder images.
         */
        fun placeholders(count: Int = 30): List<ImageItem> {
            return (1..count).map { index ->
                ImageItem(
                    id = index.toLong(),
                    uri = Uri.parse("https://picsum.photos/200/200?random=$index"),
                    name = "image_$index.jpg",
                    dateTaken = System.currentTimeMillis() - (index * 86_400_000L),
                    size = (1024 * 1024 * (1..5).random()).toLong(),
                    width = 200,
                    height = 200,
                    albumName = listOf("Camera", "Downloads", "Screenshots", "Wallpapers").random()
                )
            }
        }
    }
}
