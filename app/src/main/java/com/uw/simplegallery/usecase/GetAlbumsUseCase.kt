package com.uw.simplegallery.usecase

import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.repository.MediaRepository
import javax.inject.Inject

/**
 * Use case to retrieve all albums from local storage.
 * Albums are derived by grouping media items by their folder path,
 * with a synthetic "All Photos" album prepended.
 */
class GetAlbumsUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(forceRefreshMedia: Boolean = false): List<AlbumItem> {
        return mediaRepository.getAlbums(forceRefreshMedia)
    }
}
