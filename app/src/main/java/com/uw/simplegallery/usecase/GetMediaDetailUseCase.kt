package com.uw.simplegallery.usecase

import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.repository.MediaRepository
import javax.inject.Inject

/**
 * Use case to retrieve a single media item by its ID.
 * Useful for the detail screen to find a specific item from the full list.
 */
class GetMediaDetailUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(mediaId: Long, forceRefresh: Boolean = false): MediaItem? {
        return mediaRepository.getAllMediaItems(forceRefresh)
            .find { it.id == mediaId }
    }
}
