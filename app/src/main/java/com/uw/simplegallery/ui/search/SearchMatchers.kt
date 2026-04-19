package com.uw.simplegallery.ui.search

import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.model.MediaType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val searchDateFormatter = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.getDefault())

private val searchableImageTokens = listOf("image", "photo")
private val searchableVideoTokens = listOf("video")

fun MediaItem.matchesSearchQuery(query: String): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return true

    val albumDisplayName = folderName
        ?.trimEnd('/')
        ?.substringAfterLast('/')
        .orEmpty()

    val dateTokens = dateTaken?.let { millis ->
        val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        listOf(
            dateTime.toLocalDate().toString(),
            dateTime.year.toString(),
            dateTime.month.name.lowercase(),
            dateTime.monthValue.toString(),
            dateTime.dayOfMonth.toString(),
            dateTime.format(searchDateFormatter).lowercase()
        )
    }.orEmpty()

    val typeLabel = when (mediaType) {
        is MediaType.Image -> searchableImageTokens.joinToString(" ")
        is MediaType.Video -> searchableVideoTokens.joinToString(" ")
    }

    val durationSeconds = duration?.div(1000)?.toString().orEmpty()
    val searchableText = buildString {
        append(name.lowercase())
        append(' ')
        append(mimeType.orEmpty().lowercase())
        append(' ')
        append(albumDisplayName.lowercase())
        append(' ')
        append(folderName.orEmpty().lowercase())
        append(' ')
        append(typeLabel)
        append(' ')
        append(size?.toString().orEmpty())
        append(' ')
        append(durationSeconds)
        append(' ')
        append(tags.joinToString(" "))
        append(' ')
        append(dateTokens.joinToString(" "))
    }

    return normalizedQuery
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }
        .all { token -> searchableText.contains(token) }
}

fun AlbumItem.matchesSearchQuery(query: String): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return true

    val latestDateToken = mediaItems
        .maxOfOrNull { it.dateTaken ?: 0L }
        ?.takeIf { it > 0L }
        ?.let { millis ->
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(searchDateFormatter)
                .lowercase()
        }
        .orEmpty()

    val albumTagText = mediaItems
        .asSequence()
        .flatMap { it.tags.asSequence() }
        .distinct()
        .joinToString(" ")

    val searchableText = buildString {
        append(name.lowercase())
        append(' ')
        append(id.lowercase())
        append(' ')
        append(mediaCount.toString())
        append(' ')
        append(albumTagText)
        append(' ')
        append(latestDateToken)
    }

    return normalizedQuery
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }
        .all { token -> searchableText.contains(token) }
}

fun MediaItem.matchesSelectedTags(selectedTags: Set<String>): Boolean {
    if (selectedTags.isEmpty()) return true
    val ownTags = tags.toSet()
    return selectedTags.all { it in ownTags }
}
