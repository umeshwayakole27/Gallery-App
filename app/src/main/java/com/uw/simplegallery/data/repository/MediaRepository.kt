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
     * Deletes media items by their MediaStore IDs. On API 30+ this may
     * return [MediaManager.DeleteResult.RequiresConfirmation] containing
     * an IntentSender for the system confirmation dialog.
     *
     * @param ids The MediaStore IDs of the items to delete
     * @return [MediaManager.DeleteResult] indicating success, failure, or user-confirmation needed
     */
    suspend fun deleteMediaItems(ids: List<Long>): MediaManager.DeleteResult

    /**
     * Removes items from the in-memory cache after the user approves deletion
     * via the system dialog (API 30+). Verifies with MediaStore which items
     * were actually deleted to handle partial approvals.
     */
    suspend fun removeDeletedItemsFromCache(ids: List<Long>)

    /**
     * Creates a new album (folder) with the given name.
     *
     * @param folderName The name of the folder/album to create
     * @return true if creation was successful, false otherwise
     */
    suspend fun createAlbum(folderName: String): Boolean
}
