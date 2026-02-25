package com.uw.simplegallery.data.repository

import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import kotlinx.coroutines.flow.Flow


interface MediaRepository {
    /**
     * Returns a [Flow] of all media items (images and videos) from local storage.
     * Triggers a fresh load from MediaStore on each collection.
     */
    fun getAllMediaItems(): Flow<List<MediaItem>>

    /**
     * Returns a [Flow] of albums derived by grouping media items by folder name.
     * Includes a synthetic "All Photos" album at the beginning.
     */
    fun getAlbums(): Flow<List<AlbumItem>>

    /**
     * Searches media items whose display name contains the given [query].
     *
     * @param query The search term to filter by name
     * @return A [Flow] of matching media items
     */
    fun searchMedia(query: String): Flow<List<MediaItem>>

    /**
     * Deletes a media item by its MediaStore ID.
     *
     * @param id The MediaStore ID of the item to delete
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteMediaItem(id: Long): Boolean

    /**
     * Creates a new album (folder) with the given name.
     *
     * @param folderName The name of the folder/album to create
     * @return true if creation was successful, false otherwise
     */
    suspend fun createAlbum(folderName: String): Boolean
}
