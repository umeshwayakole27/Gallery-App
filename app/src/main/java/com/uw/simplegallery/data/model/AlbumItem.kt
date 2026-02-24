package com.uw.simplegallery.data.model

import android.net.Uri

/**
 * Represents an album (folder/bucket) containing images.
 *
 * @param id Unique identifier for the album
 * @param name Display name of the album
 * @param coverUri URI of the cover image (typically the most recent image)
 * @param imageCount Total number of images in the album
 */
data class AlbumItem(
    val id: Long,
    val name: String,
    val coverUri: Uri,
    val imageCount: Int
) {
    companion object {
        /**
         * Creates placeholder [AlbumItem] instances for the prototype UI.
         */
        fun placeholders(): List<AlbumItem> {
            val albumNames = listOf("Camera", "Downloads", "Screenshots", "Wallpapers", "WhatsApp", "Edited")
            return albumNames.mapIndexed { index, name ->
                AlbumItem(
                    id = index.toLong(),
                    name = name,
                    coverUri = Uri.parse("https://picsum.photos/300/300?random=${index + 100}"),
                    imageCount = (5..50).random()
                )
            }
        }
    }
}
