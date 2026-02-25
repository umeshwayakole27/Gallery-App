package com.uw.simplegallery.data.repository

import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Implementation of [MediaRepository] that delegates to [MediaManager]
 * for all MediaStore operations.
 *
 * Each Flow-returning method triggers a fresh [MediaManager.loadAllMedia] call
 * to ensure up-to-date data from the OS, then emits the result.
 */
class MediaRepositoryImpl @Inject constructor(
    private val mediaManager: MediaManager
) : MediaRepository {

    override fun getAllMediaItems(): Flow<List<MediaItem>> = flow {
        mediaManager.loadAllMedia()
        emit(mediaManager.allMediaItems.value)
    }

    override fun getAlbums(): Flow<List<AlbumItem>> = flow {
        // Ensure media is loaded before grouping into albums
        if (mediaManager.allMediaItems.value.isEmpty()) {
            mediaManager.loadAllMedia()
        }
        emit(mediaManager.getAlbums())
    }

    override fun searchMedia(query: String): Flow<List<MediaItem>> = flow {
        // Ensure media is loaded before searching
        if (mediaManager.allMediaItems.value.isEmpty()) {
            mediaManager.loadAllMedia()
        }
        emit(mediaManager.searchMedia(query))
    }

    override suspend fun deleteMediaItem(id: Long): Boolean {
        return mediaManager.deleteMediaItem(id)
    }

    override suspend fun createAlbum(folderName: String): Boolean {
        return mediaManager.createAlbum(folderName)
    }
}
