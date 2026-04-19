package com.uw.simplegallery.data.repository

import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import javax.inject.Inject

/**
 * Implementation of [MediaRepository] that delegates to [MediaManager]
 * for all MediaStore operations.
 */
class MediaRepositoryImpl @Inject constructor(
    private val mediaManager: MediaManager
) : MediaRepository {

    override suspend fun getAllMediaItems(forceRefresh: Boolean): List<MediaItem> {
        return if (forceRefresh || !mediaManager.hasLoadedMedia()) {
            mediaManager.loadAllMedia()
        } else {
            mediaManager.allMediaItems.value
        }
    }

    override suspend fun getAlbums(forceRefreshMedia: Boolean): List<AlbumItem> {
        getAllMediaItems(forceRefresh = forceRefreshMedia)
        return mediaManager.getAlbums()
    }

    override suspend fun searchMedia(query: String): List<MediaItem> {
        getAllMediaItems(forceRefresh = !mediaManager.hasLoadedMedia())
        return mediaManager.searchMedia(query)
    }

    override suspend fun deleteMediaItems(ids: List<Long>): MediaManager.DeleteResult {
        return mediaManager.deleteMediaItems(ids)
    }

    override suspend fun removeDeletedItemsFromCache(ids: List<Long>) {
        mediaManager.removeDeletedItemsFromCache(ids)
    }

    override suspend fun createAlbum(folderName: String): Boolean {
        return mediaManager.createAlbum(folderName)
    }

    override suspend fun renameMediaItem(id: Long, newName: String): Boolean {
        return mediaManager.renameMediaItem(id, newName)
    }

    override suspend fun getAllTags(): List<String> {
        getAllMediaItems(forceRefresh = !mediaManager.hasLoadedMedia())
        return mediaManager.getAllTags()
    }

    override suspend fun addTagToMedia(id: Long, tag: String): List<String> {
        getAllMediaItems(forceRefresh = !mediaManager.hasLoadedMedia())
        return mediaManager.addTagToMedia(id, tag)
    }

    override suspend fun removeTagFromMedia(id: Long, tag: String): List<String> {
        getAllMediaItems(forceRefresh = !mediaManager.hasLoadedMedia())
        return mediaManager.removeTagFromMedia(id, tag)
    }

    override suspend fun renameTagGlobally(oldTag: String, newTag: String): Boolean {
        getAllMediaItems(forceRefresh = !mediaManager.hasLoadedMedia())
        return mediaManager.renameTagGlobally(oldTag, newTag)
    }

    override suspend fun mergeTagsGlobally(sourceTag: String, targetTag: String): Boolean {
        getAllMediaItems(forceRefresh = !mediaManager.hasLoadedMedia())
        return mediaManager.mergeTagsGlobally(sourceTag, targetTag)
    }

    override suspend fun deleteTagGlobally(tag: String): Boolean {
        getAllMediaItems(forceRefresh = !mediaManager.hasLoadedMedia())
        return mediaManager.deleteTagGlobally(tag)
    }
}
