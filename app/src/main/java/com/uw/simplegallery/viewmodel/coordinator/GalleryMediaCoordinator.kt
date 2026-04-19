package com.uw.simplegallery.viewmodel.coordinator

import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.repository.MediaManager
import com.uw.simplegallery.data.repository.MediaRepository
import com.uw.simplegallery.usecase.GetAlbumsUseCase
import com.uw.simplegallery.usecase.GetMediaItemsUseCase

internal class GalleryMediaCoordinator(
    private val getMediaItemsUseCase: GetMediaItemsUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val mediaRepository: MediaRepository,
    private val stateCoordinator: GalleryStateCoordinator
) {
    suspend fun loadMedia(forceRefresh: Boolean, reselectMediaId: Long? = null) {
        stateCoordinator.updateMedia(getMediaItemsUseCase(forceRefresh = forceRefresh))
        stateCoordinator.updateAlbums(getAlbumsUseCase(forceRefreshMedia = false))
        stateCoordinator.refreshCurrentAlbumFilter()
        if (reselectMediaId != null) {
            stateCoordinator.selectMedia(reselectMediaId)
        }
    }

    suspend fun searchMedia(query: String): List<MediaItem> {
        return if (query.isBlank()) {
            getMediaItemsUseCase(forceRefresh = false)
        } else {
            mediaRepository.searchMedia(query)
        }
    }

    suspend fun getAllTags(): List<String> {
        return mediaRepository.getAllTags()
    }

    suspend fun addTagToMedia(id: Long, tag: String): List<String> {
        return mediaRepository.addTagToMedia(id, tag)
    }

    suspend fun removeTagFromMedia(id: Long, tag: String): List<String> {
        return mediaRepository.removeTagFromMedia(id, tag)
    }

    suspend fun renameTagGlobally(oldTag: String, newTag: String): Boolean {
        return mediaRepository.renameTagGlobally(oldTag, newTag)
    }

    suspend fun mergeTagsGlobally(sourceTag: String, targetTag: String): Boolean {
        return mediaRepository.mergeTagsGlobally(sourceTag, targetTag)
    }

    suspend fun deleteTagGlobally(tag: String): Boolean {
        return mediaRepository.deleteTagGlobally(tag)
    }

    suspend fun createAlbum(folderName: String): Boolean {
        return mediaRepository.createAlbum(folderName)
    }

    suspend fun renameMedia(id: Long, newName: String): Boolean {
        return mediaRepository.renameMediaItem(id, newName)
    }

    suspend fun deleteMediaItems(ids: List<Long>): MediaManager.DeleteResult {
        return mediaRepository.deleteMediaItems(ids)
    }

    suspend fun removeDeletedItemsFromCache(ids: List<Long>) {
        mediaRepository.removeDeletedItemsFromCache(ids)
    }
}
