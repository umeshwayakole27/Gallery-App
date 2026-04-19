package com.uw.simplegallery.viewmodel.coordinator

import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class GalleryStateCoordinator {
    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albums: StateFlow<List<AlbumItem>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedMedia = MutableStateFlow<MediaItem?>(null)
    val selectedMedia: StateFlow<MediaItem?> = _selectedMedia.asStateFlow()

    private val _currentAlbumMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val currentAlbumMedia: StateFlow<List<MediaItem>> = _currentAlbumMedia.asStateFlow()

    private val _currentAlbumName = MutableStateFlow<String?>(null)
    val currentAlbumName: StateFlow<String?> = _currentAlbumName.asStateFlow()

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags.asStateFlow()

    private var currentAlbumId: String? = null

    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    fun setError(message: String?) {
        _errorMessage.value = message
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun updateMedia(mediaItems: List<MediaItem>) {
        _media.value = mediaItems
        syncAllTagsFromMedia()
    }

    fun updateAlbums(albumItems: List<AlbumItem>) {
        _albums.value = albumItems
    }

    fun updateAllTags(tags: List<String>) {
        _allTags.value = tags
    }

    fun refreshTagsFromCurrentMedia() {
        syncAllTagsFromMedia()
    }

    fun updateMediaTags(mediaId: Long, tags: List<String>) {
        _media.value = _media.value.map { item ->
            if (item.id == mediaId) item.copy(tags = tags) else item
        }
        _albums.value = _albums.value.map { album ->
            album.copy(
                mediaItems = album.mediaItems.map { item ->
                    if (item.id == mediaId) item.copy(tags = tags) else item
                }
            )
        }
        _currentAlbumMedia.value = _currentAlbumMedia.value.map { item ->
            if (item.id == mediaId) item.copy(tags = tags) else item
        }
        _selectedMedia.value = _selectedMedia.value?.let { selected ->
            if (selected.id == mediaId) selected.copy(tags = tags) else selected
        }
        syncAllTagsFromMedia()
    }

    fun setAlbumFilter(albumId: String) {
        currentAlbumId = albumId
        applyAlbumFilter(albumId = albumId, preferAlbumCache = true)
    }

    fun clearAlbumFilter() {
        currentAlbumId = null
        _currentAlbumMedia.value = emptyList()
        _currentAlbumName.value = null
    }

    fun refreshCurrentAlbumFilter() {
        val albumId = currentAlbumId ?: return
        applyAlbumFilter(albumId = albumId, preferAlbumCache = false)
    }

    fun selectMedia(mediaId: Long) {
        _selectedMedia.value = _currentAlbumMedia.value.find { it.id == mediaId }
            ?: _media.value.find { it.id == mediaId }
    }

    private fun applyAlbumFilter(albumId: String, preferAlbumCache: Boolean) {
        val (albumMedia, albumName) = resolveAlbumFilter(
            albumId = albumId,
            preferAlbumCache = preferAlbumCache
        )
        _currentAlbumMedia.value = albumMedia
        _currentAlbumName.value = albumName
    }

    private fun resolveAlbumFilter(
        albumId: String,
        preferAlbumCache: Boolean
    ): Pair<List<MediaItem>, String> {
        val album = _albums.value.find { it.id == albumId }
        if (preferAlbumCache && album != null) {
            return album.mediaItems to album.name
        }

        if (albumId == AlbumItem.ALL_PHOTOS_ID) {
            return _media.value to AlbumItem.ALL_PHOTOS_NAME
        }

        val fallbackName = album?.name
            ?: albumId.trimEnd('/').substringAfterLast('/').ifBlank { "Album" }
        val filteredMedia = _media.value.filter { it.folderName == albumId }
        return filteredMedia to fallbackName
    }

    private fun syncAllTagsFromMedia() {
        _allTags.value = _media.value
            .asSequence()
            .flatMap { it.tags.asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    }
}
