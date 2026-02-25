package com.uw.simplegallery.viewmodel

import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.repository.MediaManager
import com.uw.simplegallery.data.repository.MediaRepository
import com.uw.simplegallery.usecase.GetAlbumsUseCase
import com.uw.simplegallery.usecase.GetMediaItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
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

    companion object {
        private const val TAG = "GalleryViewModel"
    }

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

    /** Tracks the active album filter ID so it can be re-applied after media refresh. */
    private var _currentAlbumId: String? = null

    private val _currentAlbumName = MutableStateFlow<String?>(null)
    /** Display name of the currently viewed album, or null if not in album view. */
    val currentAlbumName: StateFlow<String?> = _currentAlbumName.asStateFlow()

    /**
     * Emitted when the delete operation requires user confirmation via a
     * system dialog (API 30+). The UI layer must observe this and launch the
     * [IntentSender] via an [ActivityResultLauncher].
     *
     * Uses a [Channel] instead of [StateFlow] because delete confirmations are
     * one-shot events that must be delivered exactly once. StateFlow can drop
     * events if the value is cleared before the collector processes it.
     */
    private val _deleteConfirmationChannel = Channel<DeleteConfirmationEvent>(Channel.BUFFERED)
    val deleteConfirmationEvent = _deleteConfirmationChannel.receiveAsFlow()

    /**
     * Event data for a delete confirmation request.
     * @param intentSender The system intent to launch for user approval
     * @param pendingIds The IDs that will be deleted upon approval
     */
    data class DeleteConfirmationEvent(
        val intentSender: IntentSender,
        val pendingIds: List<Long>
    )

    init {
        loadMedia()
    }

    /**
     * Loads all media items from local storage via MediaStore.
     * Updates [media], [isLoading], and [errorMessage] states.
     * Also triggers album loading once media is loaded, and refreshes
     * the current album filter if one is active.
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
                    // Refresh the current album filter if one is active,
                    // so _currentAlbumMedia reflects deletions/changes.
                    refreshCurrentAlbumFilter()
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
        _currentAlbumId = albumId
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
        _currentAlbumId = null
        _currentAlbumMedia.value = emptyList()
        _currentAlbumName.value = null
    }

    /**
     * Re-applies the current album filter using the latest [_media] data.
     * Called after [loadMedia] to ensure [currentAlbumMedia] reflects
     * any deletions or changes. No-op if no album filter is active.
     */
    private fun refreshCurrentAlbumFilter() {
        val albumId = _currentAlbumId ?: return
        // Re-derive currentAlbumMedia from the fresh _media data.
        // First try finding the album in the albums list (may not be updated yet),
        // then fall back to filtering from _media directly.
        _currentAlbumMedia.value = if (albumId == "ALL_PHOTOS") {
            _media.value
        } else {
            _media.value.filter { it.folderName == albumId }
        }
    }

    /**
     * Deletes a single media item by its MediaStore ID.
     * On API 30+, this emits a [DeleteConfirmationEvent] for the UI to handle.
     *
     * @param id MediaStore ID of the media item to delete
     */
    fun deleteMedia(id: Long) {
        deleteMediaItems(listOf(id))
    }

    /**
     * Deletes multiple media items by their MediaStore IDs.
     * On API 30+, this emits a [DeleteConfirmationEvent] for the UI to handle.
     *
     * @param ids List of MediaStore IDs to delete
     */
    fun deleteMediaItems(ids: List<Long>) {
        viewModelScope.launch {
            try {
                when (val result = mediaRepository.deleteMediaItems(ids)) {
                    is MediaManager.DeleteResult.Success -> {
                        loadMedia()
                    }
                    is MediaManager.DeleteResult.RequiresConfirmation -> {
                        _deleteConfirmationChannel.send(DeleteConfirmationEvent(
                            intentSender = result.intentSender,
                            pendingIds = result.pendingIds
                        ))
                    }
                    is MediaManager.DeleteResult.Failure -> {
                        _errorMessage.value = "Failed to delete media items"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in deleteMediaItems: ${e.message}", e)
                _errorMessage.value = e.message ?: "Error deleting media items"
            }
        }
    }

    /**
     * Called by the UI after the user confirms or denies the system delete dialog.
     * On approval, verifies with MediaStore which items were actually deleted
     * (handles partial approvals), updates the cache, and refreshes media.
     *
     * @param approved true if the user approved the deletion
     * @param ids the IDs that were pending deletion
     */
    fun onDeleteConfirmationResult(approved: Boolean, ids: List<Long>) {
        if (approved) {
            viewModelScope.launch {
                mediaRepository.removeDeletedItemsFromCache(ids)
                loadMedia()
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
