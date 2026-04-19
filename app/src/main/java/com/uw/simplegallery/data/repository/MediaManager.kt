package com.uw.simplegallery.data.repository

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaManager"
private const val TAG_PREFS_NAME = "media_tags_store"
private const val TAG_KEY_PREFIX = "media_tags_"

/**
 * Data source responsible for loading media items and albums from device storage
 * via [MediaStore].
 *
 * Annotated with [Singleton] so a single instance is shared app-wide.
 * The [ApplicationContext] qualifier ensures Hilt provides the application
 * context rather than an activity context, preventing memory leaks.
 *
 * @param context Application context used to access ContentResolver
 */
@Singleton
class MediaManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    @Volatile
    private var hasLoadedMediaOnce = false

    private val _allMediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val allMediaItems = _allMediaItems.asStateFlow()

    private val tagsPrefs by lazy {
        context.getSharedPreferences(TAG_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasLoadedMedia(): Boolean = hasLoadedMediaOnce

    /**
     * Loads all media (images and videos) from device storage via MediaStore.
     * Updates the internal [allMediaItems] state with the results.
     *
     * @return List of [MediaItem] found on the device
     */
    suspend fun loadAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {

        val mediaItems = mutableListOf<MediaItem>()
        val resolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Video.VideoColumns.DURATION
        )

        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        // Sort by date taken, adding added/modified as secondary sorts for consistent results
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val queryUri = MediaStore.Files.getContentUri("external")

        resolver.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateTakenColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val dateAddedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mediaTypeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val relativePathColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val durationColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val mimeType = cursor.getString(mimeTypeColumn)

                // Try DATE_TAKEN (millis), fallback to DATE_MODIFIED (secs), then DATE_ADDED (secs)
                val dateTaken = cursor.getLongOrNull(dateTakenColumn)
                    ?: cursor.getLongOrNull(dateModifiedColumn)?.let { it * 1000 }
                    ?: cursor.getLongOrNull(dateAddedColumn)?.let { it * 1000 }

                val size = cursor.getLongOrNull(sizeColumn)
                val mediaTypeValue = cursor.getInt(mediaTypeColumn)
                val relativePath = cursor.getString(relativePathColumn)
                val duration = cursor.getLongOrNull(durationColumn)
                val tags = getTagsForMediaId(id)

                val mediaType = when (mediaTypeValue) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaType.Image
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.Video
                    else -> continue
                }

                val baseUri = if (mediaType == MediaType.Image) {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val contentUri = ContentUris.withAppendedId(baseUri, id)

                mediaItems.add(
                    MediaItem(
                        id = id,
                        name = name,
                        uri = contentUri.toString(),
                        mimeType = mimeType,
                        dateTaken = dateTaken,
                        mediaType = mediaType,
                        folderName = relativePath,
                        size = size,
                        duration = if (mediaType == MediaType.Video) duration else null,
                        tags = tags
                    )
                )
            }
        }

        _allMediaItems.value = mediaItems
        hasLoadedMediaOnce = true
        mediaItems
    }

    /**
     * Get all images from the loaded media.
     */
    fun getImages(): List<MediaItem> {
        return allMediaItems.value.filter { it.mediaType == MediaType.Image }
    }

    /**
     * Get all videos from the loaded media.
     */
    fun getVideos(): List<MediaItem> {
        return allMediaItems.value.filter { it.mediaType == MediaType.Video }
    }

    /**
     * Groups loaded media items by folder into [AlbumItem] list.
     * Includes a synthetic "All Photos" album at the beginning.
     *
     * Must be called after [loadAllMedia] has populated [allMediaItems].
     *
     * @return List of [AlbumItem] grouped by folder
     */
    fun getAlbums(): List<AlbumItem> {
        val allMedia = allMediaItems.value
        if (allMedia.isEmpty()) return emptyList()

        val groupedAlbums = allMedia
            .groupBy { it.folderName ?: AlbumItem.UNKNOWN_ALBUM_NAME }
            .map { (folderName, items) ->
                val cleanFolderName = folderName.trimEnd('/')
                val albumName = cleanFolderName.substringAfterLast("/").ifBlank { AlbumItem.UNKNOWN_ALBUM_NAME }
                AlbumItem(
                    id = folderName,
                    name = albumName,
                    mediaItems = items
                )
            }
            .sortedByDescending { album ->
                album.mediaItems.maxOfOrNull { it.dateTaken ?: 0L }
            }

        val allPhotosAlbum = AlbumItem(
            id = AlbumItem.ALL_PHOTOS_ID,
            name = AlbumItem.ALL_PHOTOS_NAME,
            mediaItems = allMedia
        )

        return listOf(allPhotosAlbum) + groupedAlbums
    }

    /**
     * Searches loaded media items by display name.
     *
     * @param query The search term to match against media item names
     * @return List of matching [MediaItem]s
     */
    fun searchMedia(query: String): List<MediaItem> {
        if (query.isBlank()) return allMediaItems.value
        return allMediaItems.value.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }

    fun getAllTags(): List<String> {
        val allTags = allMediaItems.value
            .asSequence()
            .flatMap { it.tags.asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .toSet()
        return allTags.sorted()
    }

    fun addTagToMedia(id: Long, rawTag: String): List<String> {
        val normalized = normalizeTag(rawTag) ?: return getTagsForMediaId(id)
        val currentTags = getTagsForMediaId(id)
        if (normalized in currentTags) {
            return currentTags
        }
        val updated = (currentTags + normalized).distinct().sorted()
        persistTagsForMediaId(id, updated)
        updateMediaTagsInCache(id, updated)
        return updated
    }

    fun removeTagFromMedia(id: Long, rawTag: String): List<String> {
        val normalized = normalizeTag(rawTag) ?: return getTagsForMediaId(id)
        val updated = getTagsForMediaId(id)
            .filterNot { it == normalized }
            .sorted()
        persistTagsForMediaId(id, updated)
        updateMediaTagsInCache(id, updated)
        return updated
    }

    fun renameTagGlobally(oldTagRaw: String, newTagRaw: String): Boolean {
        val oldTag = normalizeTag(oldTagRaw) ?: return false
        val newTag = normalizeTag(newTagRaw) ?: return false
        if (oldTag == newTag) return false

        var hasChanged = false
        val updatedMedia = _allMediaItems.value.map { item ->
            if (oldTag !in item.tags) {
                item
            } else {
                hasChanged = true
                val nextTags = item.tags
                    .map { if (it == oldTag) newTag else it }
                    .distinct()
                    .sorted()
                persistTagsForMediaId(item.id, nextTags)
                item.copy(tags = nextTags)
            }
        }

        if (hasChanged) {
            _allMediaItems.value = updatedMedia
        }
        return hasChanged
    }

    fun mergeTagsGlobally(sourceTagRaw: String, targetTagRaw: String): Boolean {
        val sourceTag = normalizeTag(sourceTagRaw) ?: return false
        val targetTag = normalizeTag(targetTagRaw) ?: return false
        if (sourceTag == targetTag) return false

        var hasChanged = false
        val updatedMedia = _allMediaItems.value.map { item ->
            if (sourceTag !in item.tags) {
                item
            } else {
                hasChanged = true
                val nextTags = item.tags
                    .filterNot { it == sourceTag }
                    .plus(targetTag)
                    .distinct()
                    .sorted()
                persistTagsForMediaId(item.id, nextTags)
                item.copy(tags = nextTags)
            }
        }

        if (hasChanged) {
            _allMediaItems.value = updatedMedia
        }
        return hasChanged
    }

    fun deleteTagGlobally(tagRaw: String): Boolean {
        val tag = normalizeTag(tagRaw) ?: return false

        var hasChanged = false
        val updatedMedia = _allMediaItems.value.map { item ->
            if (tag !in item.tags) {
                item
            } else {
                hasChanged = true
                val nextTags = item.tags.filterNot { it == tag }.sorted()
                persistTagsForMediaId(item.id, nextTags)
                item.copy(tags = nextTags)
            }
        }

        if (hasChanged) {
            _allMediaItems.value = updatedMedia
        }
        return hasChanged
    }

    /**
     * Result of a delete operation. On Android 11+ (API 30+), the OS requires
     * user confirmation via a system dialog, so we return an [IntentSender]
     * for the UI layer to launch.
     */
    sealed class DeleteResult {
        /** Deletion succeeded directly (API < 30, or app-owned files). */
        data object Success : DeleteResult()
        /** Deletion failed. */
        data object Failure : DeleteResult()
        /**
         * User confirmation required (API 30+ for files not owned by this app).
         * The UI must launch [intentSender] and, upon approval, call
         * [removeDeletedItemsFromCache] to update the in-memory list.
         */
        data class RequiresConfirmation(
            val intentSender: IntentSender,
            val pendingIds: List<Long>
        ) : DeleteResult()
    }

    /**
     * Deletes a single media item. On API 30+ this may return
     * [DeleteResult.RequiresConfirmation] with an [IntentSender] for
     * the system delete-confirmation dialog.
     */
    suspend fun deleteMediaItem(id: Long): DeleteResult =
        deleteMediaItems(listOf(id))

    /**
     * Verifies which URIs still exist in MediaStore using a batch query.
     * This prevents crashes from trying to delete stale/non-existent URIs,
     * including [IllegalArgumentException] from createDeleteRequest on API 30+
     * and [SecurityException] on older APIs.
     *
     * Uses a single batch query instead of per-URI queries for efficiency.
     *
     * IMPORTANT: Returns media-type-specific URIs (Images or Video), NOT generic
     * Files URIs. [MediaStore.createDeleteRequest] requires URIs from
     * [MediaStore.Images.Media] or [MediaStore.Video.Media] — it rejects
     * [MediaStore.Files] URIs with "All requested items must be Media items".
     *
     * @param ids The MediaStore IDs to verify
     * @return Pair of (valid IDs, valid URIs) that still exist in MediaStore
     */
    private fun filterExistingUris(ids: List<Long>): Pair<List<Long>, List<Uri>> {
        if (ids.isEmpty()) return Pair(emptyList(), emptyList())

        val validIds = mutableListOf<Long>()
        val validUris = mutableListOf<Uri>()

        // Batch query: query all IDs at once using IN clause for efficiency
        // Also fetch MEDIA_TYPE so we can build the correct content URI
        // (Images vs Video) required by createDeleteRequest.
        val chunkSize = 500
        for (chunk in ids.chunked(chunkSize)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.Files.FileColumns._ID} IN ($placeholders)"
            val selectionArgs = chunk.map { it.toString() }.toTypedArray()

            try {
                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.MEDIA_TYPE
                    ),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val mediaType = cursor.getInt(mediaTypeColumn)

                        // Build media-type-specific URI required by createDeleteRequest
                        val baseUri = when (mediaType) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else -> {
                                Log.w(TAG, "Skipping non-media item id=$id mediaType=$mediaType")
                                continue
                            }
                        }
                        validIds.add(id)
                        validUris.add(ContentUris.withAppendedId(baseUri, id))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying MediaStore for IDs: ${e.message}")
                // Fallback: use Images URI as default (most common case)
                for (id in chunk) {
                    validIds.add(id)
                    validUris.add(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    ))
                }
            }
        }

        return Pair(validIds, validUris)
    }

    /**
     * Deletes multiple media items by their MediaStore IDs.
     *
     * Handles all Android versions from API 28 (Android 9) through API 36 (Android 16):
     *
     * - **API 30+ (Android 11–16)**: Always uses [MediaStore.createDeleteRequest] to
     *   produce a system confirmation dialog. Returns [DeleteResult.RequiresConfirmation].
     *   This is the recommended approach for Android 11+ and is the pattern used by
     *   the Tulsi gallery app. The system handles MANAGE_MEDIA permissions automatically —
     *   when the app has MANAGE_MEDIA, the dialog auto-approves or shows a simpler prompt.
     *   Direct contentResolver.delete() is NOT used on API 30+ because it can silently
     *   return 0 rows on some OEMs/API levels even when MANAGE_MEDIA is granted.
     *
     * - **API 29 (Android 10)**: Attempts direct delete per-URI. On
     *   [RecoverableSecurityException], collects failed URIs and uses the exception's
     *   IntentSender for user confirmation.
     *
     * - **API 28 (Android 9)**: Direct [ContentResolver.delete] with
     *   WRITE_EXTERNAL_STORAGE permission.
     *
     * All paths verify URIs exist before attempting deletion to prevent crashes
     * from stale MediaStore IDs.
     *
     * @param ids The MediaStore IDs of the items to delete
     * @return [DeleteResult] indicating success, failure, or user-confirmation needed
     */
    suspend fun deleteMediaItems(ids: List<Long>): DeleteResult = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) {
            return@withContext DeleteResult.Failure
        }

        // Verify URIs exist in MediaStore before attempting deletion.
        val (validIds, validUris) = filterExistingUris(ids)
        if (validIds.isEmpty()) {
            // All items were already deleted from MediaStore — clean up cache
            _allMediaItems.value = _allMediaItems.value.filter { it.id !in ids }
            ids.forEach(::clearTagsForMediaId)
            return@withContext DeleteResult.Success
        }

        // ── Android 11+ (API 30+) ───────────────────────────────────────
        // Covers Android 11, 12, 13, 14, 15, and 16.
        //
        // Always use createDeleteRequest — this is the Tulsi gallery pattern.
        // The system handles MANAGE_MEDIA automatically: when granted, the dialog
        // may auto-approve or show a simplified confirmation. We never try direct
        // contentResolver.delete() on API 30+ because:
        // 1. It can silently return 0 on some devices even with MANAGE_MEDIA
        // 2. createDeleteRequest is the official recommended API
        // 3. It provides a consistent user experience with system-level confirmation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    validUris
                )
                DeleteResult.RequiresConfirmation(pendingIntent.intentSender, validIds)
            } catch (e: IllegalArgumentException) {
                // URIs are invalid — this should not happen now that we use
                // media-type-specific URIs, but log it clearly if it does.
                Log.e(TAG, "Invalid URIs for delete request: ${e.message}", e)
                DeleteResult.Failure
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException creating delete request: ${e.message}", e)
                DeleteResult.Failure
            } catch (e: Exception) {
                Log.e(TAG, "Error creating delete request: ${e.message}", e)
                DeleteResult.Failure
            }
        }

        // ── Android 10 (API 29) ─────────────────────────────────────────
        // Scoped storage introduced. Direct delete works for app-owned files.
        // Non-owned files throw RecoverableSecurityException.
        // We collect all failures and request user confirmation via the exception's
        // IntentSender. On Q, each RecoverableSecurityException only covers one URI,
        // so for multiple non-owned files the user may need to approve multiple times.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            return@withContext try {
                var deletedCount = 0
                val failedUris = mutableListOf<Uri>()
                val failedIds = mutableListOf<Long>()
                var lastRecoverableException: RecoverableSecurityException? = null

                for (i in validUris.indices) {
                    try {
                        val rows = context.contentResolver.delete(validUris[i], null, null)
                        if (rows > 0) {
                            deletedCount++
                        } else {
                            failedUris.add(validUris[i])
                            failedIds.add(validIds[i])
                        }
                    } catch (e: RecoverableSecurityException) {
                        lastRecoverableException = e
                        failedUris.add(validUris[i])
                        failedIds.add(validIds[i])
                    } catch (e: SecurityException) {
                        Log.w(TAG, "SecurityException on Q for ${validUris[i]}: ${e.message}")
                        failedUris.add(validUris[i])
                        failedIds.add(validIds[i])
                    }
                }

                // Update cache for successfully deleted items
                if (deletedCount > 0) {
                    val successIds = validIds.filterNot { it in failedIds }
                    _allMediaItems.value = _allMediaItems.value.filter { it.id !in successIds }
                    successIds.forEach(::clearTagsForMediaId)
                }

                when {
                    failedIds.isEmpty() -> {
                        DeleteResult.Success
                    }
                    lastRecoverableException != null -> {
                        // Use the exception's IntentSender for user confirmation.
                        // On Q this only handles the last failed file.
                        DeleteResult.RequiresConfirmation(
                            lastRecoverableException.userAction.actionIntent.intentSender,
                            failedIds
                        )
                    }
                    else -> {
                        Log.w(TAG, "Delete returned 0 rows without exception for ${failedIds.size} items")
                        DeleteResult.Failure
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting media on Q: ${e.message}", e)
                DeleteResult.Failure
            }
        }

        // ── Android 9 and below (API 28) ────────────────────────────────
        // Legacy storage model. Direct delete with WRITE_EXTERNAL_STORAGE permission.
        return@withContext try {
            var deletedCount = 0
            for (uri in validUris) {
                try {
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) deletedCount++
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException deleting $uri: ${e.message}")
                }
            }
            if (deletedCount > 0) {
                _allMediaItems.value = _allMediaItems.value.filter { it.id !in validIds }
                validIds.forEach(::clearTagsForMediaId)
                DeleteResult.Success
            } else {
                Log.w(TAG, "Failed to delete any items on API ${Build.VERSION.SDK_INT}")
                DeleteResult.Failure
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting media: ${e.message}", e)
            DeleteResult.Failure
        }
    }

    /**
     * Removes items from the in-memory cache after the user confirms deletion
     * via the system dialog. Called by the UI layer after [DeleteResult.RequiresConfirmation]
     * is handled successfully.
     *
     * Also verifies with MediaStore that items are actually gone, to handle cases
     * where the user approved deletion of some items but not all (partial approval).
     */
    fun removeDeletedItemsFromCache(ids: List<Long>) {
        // First, check which IDs are actually still in MediaStore
        // This handles partial approvals correctly
        val (stillExistingIds, _) = filterExistingUris(ids)
        val actuallyDeletedIds = ids.filterNot { it in stillExistingIds }

        if (actuallyDeletedIds.isNotEmpty()) {
            _allMediaItems.value = _allMediaItems.value.filter { it.id !in actuallyDeletedIds }
            actuallyDeletedIds.forEach(::clearTagsForMediaId)
        }
    }

    /**
     * Force reload all media from the OS (clears and re-queries).
     */
    suspend fun forceReload(): List<MediaItem> = withContext(Dispatchers.IO) {
        hasLoadedMediaOnce = false
        _allMediaItems.value = emptyList()
        loadAllMedia()
    }

    /**
     * Creates a new album (folder) by inserting a placeholder image into MediaStore.
     *
     * @param folderName The name of the folder to create
     * @return true if the album was created successfully
     */
    suspend fun createAlbum(folderName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "album_placeholder.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName/")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                // Write a minimal 1x1 transparent PNG file
                resolver.openOutputStream(uri)?.use { outputStream ->
                    val pngBytes = byteArrayOf(
                        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(),
                        0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
                        0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                        0x37.toByte(), 0x6E.toByte(), 0xF9.toByte(), 0x24.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0C.toByte(),
                        0x49.toByte(), 0x44.toByte(), 0x41.toByte(), 0x54.toByte(),
                        0x78.toByte(), 0x9C.toByte(), 0x62.toByte(), 0x60.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(),
                        0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                        0x49.toByte(), 0x45.toByte(), 0x4E.toByte(), 0x44.toByte(),
                        0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte()
                    )
                    outputStream.write(pngBytes)
                }

                // Commit the file by setting IS_PENDING to 0
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Log.d(TAG, "Created album with placeholder image: $folderName $uri")
                true
            } else {
                Log.e(TAG, "Failed to create album: $folderName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating album: ${e.message}", e)
            false
        }
    }

    /**
     * Renames a media item in MediaStore.
     *
     * @param id The MediaStore ID of the item
     * @param newName The new display name (including extension)
     * @return true if successful
     */
    suspend fun renameMediaItem(id: Long, newName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val (validIds, validUris) = filterExistingUris(listOf(id))
            if (validIds.isEmpty()) return@withContext false

            val uri = validUris[0]
            val contentValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, newName)
            }

            val rows = context.contentResolver.update(uri, contentValues, null, null)
            if (rows > 0) {
                // Update cache
                _allMediaItems.value = _allMediaItems.value.map {
                    if (it.id == id) it.copy(name = newName) else it
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming media item: ${e.message}", e)
            false
        }
    }

    private fun Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (isNull(columnIndex)) null else getLong(columnIndex)
    }

    private fun getTagsForMediaId(id: Long): List<String> {
        return tagsPrefs
            .getStringSet("$TAG_KEY_PREFIX$id", emptySet())
            .orEmpty()
            .asSequence()
            .mapNotNull(::normalizeTag)
            .distinct()
            .sorted()
            .toList()
    }

    private fun persistTagsForMediaId(id: Long, tags: List<String>) {
        if (tags.isEmpty()) {
            clearTagsForMediaId(id)
            return
        }
        tagsPrefs.edit().putStringSet("$TAG_KEY_PREFIX$id", tags.toSet()).apply()
    }

    private fun clearTagsForMediaId(id: Long) {
        tagsPrefs.edit().remove("$TAG_KEY_PREFIX$id").apply()
    }

    private fun updateMediaTagsInCache(id: Long, tags: List<String>) {
        _allMediaItems.value = _allMediaItems.value.map { item ->
            if (item.id == id) item.copy(tags = tags) else item
        }
    }

    private fun normalizeTag(tag: String): String? {
        val normalized = tag.trim().lowercase().replace("\\s+".toRegex(), " ")
        return normalized.takeIf { it.isNotBlank() }
    }
}
