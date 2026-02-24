package com.uw.simplegallery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.ImageItem
import com.uw.simplegallery.data.repository.MediaStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// TODO: Inject MediaStore repository via Hilt
// TODO: Add unit tests for GalleryViewModel

/**
 * ViewModel for the Gallery app, managing UI state for images and albums.
 *
 * Uses [AndroidViewModel] to access application context for [MediaStoreRepository].
 * Exposes reactive [StateFlow] properties for the UI layer to collect.
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.applicationContext)

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    /** All images available in the gallery. */
    val images: StateFlow<List<ImageItem>> = _images.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    /** All albums available in the gallery. */
    val albums: StateFlow<List<AlbumItem>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    /** Whether a loading operation is in progress. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Error message to display, or null if no error. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedImage = MutableStateFlow<ImageItem?>(null)
    /** The currently selected image for detail view. */
    val selectedImage: StateFlow<ImageItem?> = _selectedImage.asStateFlow()

    init {
        loadImages()
        loadAlbums()
    }

    /**
     * Loads all images from the repository.
     * Updates [images], [isLoading], and [errorMessage] states.
     */
    fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _images.value = repository.loadImages()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load images"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Loads all albums from the repository.
     * Updates [albums] state.
     */
    fun loadAlbums() {
        viewModelScope.launch {
            try {
                _albums.value = repository.loadAlbums()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load albums"
            }
        }
    }

    /**
     * Deletes an image by its ID and refreshes the image list.
     *
     * @param id MediaStore ID of the image to delete
     */
    fun deleteImage(id: Long) {
        // TODO: Implement deleteImage with coroutine + RecoverableSecurityException handling (Android 10+)
        viewModelScope.launch {
            try {
                val success = repository.deleteImage(id)
                if (success) {
                    // Refresh the list after deletion
                    loadImages()
                    loadAlbums()
                } else {
                    _errorMessage.value = "Failed to delete image"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error deleting image"
            }
        }
    }

    /**
     * Searches images matching the given query string.
     *
     * @param query The search term to filter images by name
     */
    fun searchImages(query: String) {
        // TODO: Implement search with debounce using Flow operators
        // Example with debounce:
        // searchQueryFlow
        //     .debounce(300)
        //     .distinctUntilChanged()
        //     .flatMapLatest { repository.searchImages(it) }
        //     .collect { _images.value = it }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _images.value = repository.searchImages(query)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Search failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Selects an image by its ID for viewing in the detail screen.
     *
     * @param imageId The ID of the image to select
     */
    fun selectImage(imageId: Long) {
        _selectedImage.value = _images.value.find { it.id == imageId }
    }

    // TODO: Add sorting options (date, name, size)
    // fun sortImages(sortBy: SortOption) { ... }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
