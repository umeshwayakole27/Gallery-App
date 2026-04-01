package com.uw.simplegallery.usecase

import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.repository.MediaRepository
import javax.inject.Inject

/**
 * Use case to retrieve all media items from local storage.
 * Returns all images and videos sorted by date taken (descending).
 */
class GetMediaItemsUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = true): List<MediaItem> {
        return mediaRepository.getAllMediaItems(forceRefresh)
    }
}
