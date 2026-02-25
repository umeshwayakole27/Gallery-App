package com.uw.simplegallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.repository.MediaRepository
import com.uw.simplegallery.usecase.GetAlbumsUseCase
import com.uw.simplegallery.usecase.GetMediaItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Gallery app, managing UI state for media items and albums.
 *
 * Follows Clean Architecture by injecting use cases and the repository interface
 * rather than accessing [MediaManager] directly. Data is loaded from the device's
 * local storage via [MediaStore] through the repository layer.
 *
 * Exposes reactive [StateFlow] properties for the UI layer to collect.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMediaItemsUseCase: GetMediaItemsUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    /** All media items (images and videos) available in the gallery. */
    val media: StateFlow<List<MediaItem>> = _media.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    /** All albums available in the gallery. */
    val albums: StateFlow<List<AlbumItem>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    /** Whether a loading operation is in progress. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Error message to display, or null if no error. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedMedia = MutableStateFlow<MediaItem?>(null)
    /** The currently selected media item for detail view. */
    val selectedMedia: StateFlow<MediaItem?> = _selectedMedia.asStateFlow()

    private val _currentAlbumMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    /** Media items filtered for the currently viewed album. */
    val currentAlbumMedia: StateFlow<List<MediaItem>> = _currentAlbumMedia.asStateFlow()

    private val _currentAlbumName = MutableStateFlow<String?>(null)
    /** Display name of the currently viewed album, or null if not in album view. */
    val currentAlbumName: StateFlow<String?> = _currentAlbumName.asStateFlow()

    init {
        loadMedia()
    }

    /**
     * Loads all media items from local storage via MediaStore.
     * Updates [media], [isLoading], and [errorMessage] states.
     * Also triggers album loading once media is loaded.
     */
    fun loadMedia() {
        viewModelScope.launch {
            getMediaItemsUseCase()
                .onStart {
                    _isLoading.value = true
                    _errorMessage.value = null
                }
                .catch { e ->
                    _errorMessage.value = e.message ?: "Failed to load media"
                    _isLoading.value = false
                }
                .collect { mediaItems ->
                    _media.value = mediaItems
                    _isLoading.value = false
                    // Load albums after media is available
                    loadAlbums()
                }
        }
    }

    /**
     * Loads all albums by grouping media items by folder.
     * Updates [albums] state.
     */
    private fun loadAlbums() {
        viewModelScope.launch {
            getAlbumsUseCase()
                .catch { e ->
                    _errorMessage.value = e.message ?: "Failed to load albums"
                }
                .collect { albumItems ->
                    _albums.value = albumItems
                }
        }
    }

    /**
     * Sets the album filter to show only media from the specified album.
     * Finds the album by its ID and populates [currentAlbumMedia] with its items.
     *
     * @param albumId The album ID (folder path or "ALL_PHOTOS")
     */
    fun setAlbumFilter(albumId: String) {
        val album = _albums.value.find { it.id == albumId }
        if (album != null) {
            _currentAlbumMedia.value = album.mediaItems
            _currentAlbumName.value = album.name
        } else {
            // Fallback: filter from all media by folder path
            _currentAlbumMedia.value = if (albumId == "ALL_PHOTOS") {
                _media.value
            } else {
                _media.value.filter { it.folderName == albumId }
            }
            _currentAlbumName.value = if (albumId == "ALL_PHOTOS") {
                "All Photos"
            } else {
                albumId.trimEnd('/').substringAfterLast('/').ifBlank { "Album" }
            }
        }
    }

    /**
     * Clears the album filter, resetting [currentAlbumMedia] and [currentAlbumName].
     */
    fun clearAlbumFilter() {
        _currentAlbumMedia.value = emptyList()
        _currentAlbumName.value = null
    }

    /**
     * Deletes a media item by its MediaStore ID and refreshes the lists.
     *
     * @param id MediaStore ID of the media item to delete
     */
    fun deleteMedia(id: Long) {
        viewModelScope.launch {
            try {
                val success = mediaRepository.deleteMediaItem(id)
                if (success) {
                    // Refresh both media and albums after deletion
                    loadMedia()
                } else {
                    _errorMessage.value = "Failed to delete media item"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error deleting media item"
            }
        }
    }

    /**
     * Searches media items matching the given query string.
     *
     * @param query The search term to filter media by name
     */
    fun searchMedia(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            mediaRepository.searchMedia(query)
                .catch { e ->
                    _errorMessage.value = e.message ?: "Search failed"
                    _isLoading.value = false
                }
                .collect { results ->
                    _media.value = results
                    _isLoading.value = false
                }
        }
    }

    /**
     * Selects a media item by its ID for viewing in the detail screen.
     * Searches in the current album media first, then falls back to all media.
     *
     * @param mediaId The ID of the media item to select
     */
    fun selectMedia(mediaId: Long) {
        // Try current album media first, then fall back to all media
        val item = _currentAlbumMedia.value.find { it.id == mediaId }
            ?: _media.value.find { it.id == mediaId }
        _selectedMedia.value = item
    }

    /**
     * Creates a new album (folder) with the given name via MediaStore.
     * Refreshes media and albums on success.
     *
     * @param folderName The name of the album to create
     */
    fun createAlbum(folderName: String) {
        viewModelScope.launch {
            try {
                val success = mediaRepository.createAlbum(folderName)
                if (success) {
                    loadMedia()
                } else {
                    _errorMessage.value = "Failed to create album"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error creating album"
            }
        }
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
