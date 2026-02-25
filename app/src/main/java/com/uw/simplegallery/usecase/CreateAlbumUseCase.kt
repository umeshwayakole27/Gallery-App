package com.uw.simplegallery.usecase

import com.uw.simplegallery.data.repository.MediaRepository
import javax.inject.Inject

/**
 * Use case to create a new album (folder) in local storage.
 */
class CreateAlbumUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(folderName: String): Boolean {
        return mediaRepository.createAlbum(folderName)
    }
}
