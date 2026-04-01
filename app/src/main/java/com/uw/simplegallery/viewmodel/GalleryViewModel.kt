package com.uw.simplegallery.viewmodel

import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.repository.MediaRepository
import com.uw.simplegallery.usecase.GetAlbumsUseCase
import com.uw.simplegallery.usecase.GetMediaItemsUseCase
import com.uw.simplegallery.viewmodel.coordinator.GalleryDeleteCoordinator
import com.uw.simplegallery.viewmodel.coordinator.GalleryMediaCoordinator
import com.uw.simplegallery.viewmodel.coordinator.GalleryStateCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Gallery app.
 *
 * Uses small coordinators to keep responsibilities separated:
 * - [GalleryStateCoordinator] owns UI-facing state
 * - [GalleryMediaCoordinator] handles media/album load + mutations
 * - [GalleryDeleteCoordinator] handles delete workflow outcomes
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

    private val stateCoordinator = GalleryStateCoordinator()
    private val mediaCoordinator = GalleryMediaCoordinator(
        getMediaItemsUseCase = getMediaItemsUseCase,
        getAlbumsUseCase = getAlbumsUseCase,
        mediaRepository = mediaRepository,
        stateCoordinator = stateCoordinator
    )
    private val deleteCoordinator = GalleryDeleteCoordinator(mediaCoordinator)

    val media: StateFlow<List<MediaItem>> = stateCoordinator.media
    val albums: StateFlow<List<AlbumItem>> = stateCoordinator.albums
    val isLoading: StateFlow<Boolean> = stateCoordinator.isLoading
    val errorMessage: StateFlow<String?> = stateCoordinator.errorMessage
    val selectedMedia: StateFlow<MediaItem?> = stateCoordinator.selectedMedia
    val currentAlbumMedia: StateFlow<List<MediaItem>> = stateCoordinator.currentAlbumMedia
    val currentAlbumName: StateFlow<String?> = stateCoordinator.currentAlbumName

    private var loadMediaJob: Job? = null

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

    fun loadMedia(forceRefresh: Boolean = true, reselectMediaId: Long? = null) {
        loadMediaJob?.cancel()
        loadMediaJob = viewModelScope.launch {
            stateCoordinator.setLoading(true)
            stateCoordinator.clearError()
            try {
                mediaCoordinator.loadMedia(
                    forceRefresh = forceRefresh,
                    reselectMediaId = reselectMediaId
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                stateCoordinator.setError(e.message ?: "Failed to load media")
            } finally {
                stateCoordinator.setLoading(false)
            }
        }
    }

    private fun requestMediaRefresh(forceRefresh: Boolean = true, reselectMediaId: Long? = null) {
        loadMedia(forceRefresh = forceRefresh, reselectMediaId = reselectMediaId)
    }

    fun setAlbumFilter(albumId: String) {
        stateCoordinator.setAlbumFilter(albumId)
    }

    fun clearAlbumFilter() {
        stateCoordinator.clearAlbumFilter()
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
                when (val outcome = deleteCoordinator.delete(ids)) {
                    GalleryDeleteCoordinator.DeleteOutcome.Completed -> {
                        requestMediaRefresh(forceRefresh = true)
                    }
                    GalleryDeleteCoordinator.DeleteOutcome.Failed -> {
                        stateCoordinator.setError("Failed to delete media items")
                    }
                    GalleryDeleteCoordinator.DeleteOutcome.Cancelled -> {
                        Unit
                    }
                    is GalleryDeleteCoordinator.DeleteOutcome.RequiresConfirmation -> {
                        _deleteConfirmationChannel.send(DeleteConfirmationEvent(
                            intentSender = outcome.intentSender,
                            pendingIds = outcome.pendingIds
                        ))
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Exception in deleteMediaItems: ${e.message}", e)
                stateCoordinator.setError(e.message ?: "Error deleting media items")
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
        viewModelScope.launch {
            try {
                when (deleteCoordinator.handleConfirmation(approved = approved, ids = ids)) {
                    GalleryDeleteCoordinator.DeleteOutcome.Completed -> {
                        requestMediaRefresh(forceRefresh = false)
                    }
                    GalleryDeleteCoordinator.DeleteOutcome.Cancelled -> Unit
                    GalleryDeleteCoordinator.DeleteOutcome.Failed -> {
                        stateCoordinator.setError("Failed to delete media items")
                    }
                    is GalleryDeleteCoordinator.DeleteOutcome.RequiresConfirmation -> Unit
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Exception in onDeleteConfirmationResult: ${e.message}", e)
                stateCoordinator.setError(e.message ?: "Error deleting media items")
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
            stateCoordinator.setLoading(true)
            stateCoordinator.clearError()
            try {
                stateCoordinator.updateMedia(mediaCoordinator.searchMedia(query))
                stateCoordinator.refreshCurrentAlbumFilter()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                stateCoordinator.setError(e.message ?: "Search failed")
            } finally {
                stateCoordinator.setLoading(false)
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
        stateCoordinator.selectMedia(mediaId)
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
                val success = mediaCoordinator.createAlbum(folderName)
                if (success) {
                    requestMediaRefresh(forceRefresh = true)
                } else {
                    stateCoordinator.setError("Failed to create album")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                stateCoordinator.setError(e.message ?: "Error creating album")
            }
        }
    }

    /**
     * Renames a media item with the given ID to a new name.
     * Refreshes media and selected item on success.
     *
     * @param id The MediaStore ID of the item to rename
     * @param newName The new name for the media item
     */
    fun renameMedia(id: Long, newName: String) {
        viewModelScope.launch {
            try {
                val success = mediaCoordinator.renameMedia(id, newName)
                if (success) {
                    requestMediaRefresh(forceRefresh = true, reselectMediaId = id)
                } else {
                    stateCoordinator.setError("Failed to rename media")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                stateCoordinator.setError(e.message ?: "Error renaming media")
            }
        }
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        stateCoordinator.clearError()
    }
}
