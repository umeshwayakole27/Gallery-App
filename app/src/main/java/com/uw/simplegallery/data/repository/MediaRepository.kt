package com.uw.simplegallery.data.repository

import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem

interface MediaRepository {
    /**
     * Returns all media items (images and videos) from local storage.
     *
     * @param forceRefresh Whether to force a fresh load from MediaStore even when
     * cached data is available in memory.
     */
    suspend fun getAllMediaItems(forceRefresh: Boolean = true): List<MediaItem>

    /**
     * Returns albums derived by grouping media items by folder name.
     *
     * @param forceRefreshMedia Whether to force a fresh MediaStore load before
     * grouping albums.
     * Includes a synthetic "All Photos" album at the beginning.
     */
    suspend fun getAlbums(forceRefreshMedia: Boolean = false): List<AlbumItem>

    /**
     * Searches media items whose display name contains the given [query].
     *
     * @param query The search term to filter by name
     * @return Matching media items
     */
    suspend fun searchMedia(query: String): List<MediaItem>

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

    /**
     * Renames a media item by its ID.
     *
     * @param id The ID of the media item
     * @param newName The new name for the media item
     * @return true if successful
     */
    suspend fun renameMediaItem(id: Long, newName: String): Boolean

    /**
     * Returns all unique tags used across media items.
     */
    suspend fun getAllTags(): List<String>

    /**
     * Adds a tag to the given media item and returns the updated tag list.
     */
    suspend fun addTagToMedia(id: Long, tag: String): List<String>

    /**
     * Removes a tag from the given media item and returns the updated tag list.
     */
    suspend fun removeTagFromMedia(id: Long, tag: String): List<String>

    /**
     * Renames a tag across all media items.
     */
    suspend fun renameTagGlobally(oldTag: String, newTag: String): Boolean

    /**
     * Merges source tag into target tag across all media items.
     */
    suspend fun mergeTagsGlobally(sourceTag: String, targetTag: String): Boolean

    /**
     * Deletes a tag from all media items.
     */
    suspend fun deleteTagGlobally(tag: String): Boolean
}
