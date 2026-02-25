package com.uw.simplegallery.usecase

import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to retrieve all media items from local storage.
 * Returns a [Flow] of all images and videos sorted by date taken (descending).
 */
class GetMediaItemsUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(): Flow<List<MediaItem>> {
        return mediaRepository.getAllMediaItems()
    }
}
