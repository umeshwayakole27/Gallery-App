package com.uw.simplegallery.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
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
    private val _allMediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val allMediaItems = _allMediaItems.asStateFlow()

    /**
     * Loads all media (images and videos) from device storage via MediaStore.
     * Updates the internal [allMediaItems] state with the results.
     *
     * @return List of [MediaItem] found on the device
     */
    suspend fun loadAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        Log.d("MediaManager", "Loading all media from MediaStore...")

        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_TAKEN,
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

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"

        val queryUri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateTakenColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
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
                val dateTaken = cursor.getLongOrNull(dateTakenColumn)
                val size = cursor.getLongOrNull(sizeColumn)
                val mediaTypeValue = cursor.getInt(mediaTypeColumn)
                val relativePath = cursor.getString(relativePathColumn)
                val duration = cursor.getLongOrNull(durationColumn)

                val mediaType = when (mediaTypeValue) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaType.Image
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.Video
                    else -> continue
                }

                val contentUri = MediaStore.Files.getContentUri("external", id)

                mediaItems.add(
                    MediaItem(
                        id = id,
                        name = name,
                        uri = contentUri.toString(),
                        dateTaken = dateTaken,
                        mediaType = mediaType,
                        folderName = relativePath,
                        size = size,
                        duration = if (mediaType == MediaType.Video) duration else null
                    )
                )
            }
        }

        _allMediaItems.value = mediaItems
        Log.d("MediaManager", "Loaded ${mediaItems.size} media items from MediaStore")
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
            .groupBy { it.folderName ?: "Unknown" }
            .map { (folderName, items) ->
                val cleanFolderName = folderName.trimEnd('/')
                val albumName = cleanFolderName.substringAfterLast("/").ifBlank { "Unknown" }
                AlbumItem(
                    id = folderName,
                    name = albumName,
                    mediaItems = items.sortedByDescending { it.dateTaken }
                )
            }
            .sortedByDescending { album ->
                album.mediaItems.maxOfOrNull { it.dateTaken ?: 0L }
            }

        val allPhotosAlbum = AlbumItem(
            id = "ALL_PHOTOS",
            name = "All Photos",
            mediaItems = allMedia.sortedByDescending { it.dateTaken }
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

    /**
     * Deletes a media item from MediaStore by its ID.
     *
     * @param id The MediaStore ID of the item to delete
     * @return true if the item was deleted, false otherwise
     */
    suspend fun deleteMediaItem(id: Long): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val uri = MediaStore.Files.getContentUri("external", id)
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                // Update the in-memory list
                _allMediaItems.value = _allMediaItems.value.filter { it.id != id }
                Log.d("MediaManager", "Deleted media item with id: $id")
                true
            } else {
                Log.w("MediaManager", "No media item found with id: $id")
                false
            }
        } catch (e: SecurityException) {
            // On Android 10+ (API 29), deleting items owned by other apps requires
            // RecoverableSecurityException handling. For now, log and return false.
            Log.e("MediaManager", "SecurityException deleting media: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e("MediaManager", "Error deleting media: ${e.message}", e)
            false
        }
    }

    /**
     * Force reload all media from the OS (clears and re-queries).
     */
    suspend fun forceReload(): List<MediaItem> = withContext(Dispatchers.IO) {
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

                Log.d("MediaManager", "Created album with placeholder image: $folderName $uri")
                true
            } else {
                Log.e("MediaManager", "Failed to create album: $folderName")
                false
            }
        } catch (e: Exception) {
            Log.e("MediaManager", "Error creating album: ${e.message}", e)
            false
        }
    }

    private fun Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (isNull(columnIndex)) null else getLong(columnIndex)
    }
}
