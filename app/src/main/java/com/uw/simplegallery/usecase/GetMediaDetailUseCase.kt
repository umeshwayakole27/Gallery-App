package com.uw.simplegallery.usecase

import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case to retrieve a single media item by its ID.
 * Useful for the detail screen to find a specific item from the full list.
 */
class GetMediaDetailUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(mediaId: Long): Flow<MediaItem?> {
        return mediaRepository.getAllMediaItems()
            .map { mediaItems ->
                mediaItems.find { it.id == mediaId }
            }
    }
}
