package com.uw.simplegallery.ui.screens.gallery

import com.uw.simplegallery.data.model.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Timeline item model used by the timeline grid.
 */
sealed interface TimelineItem {
    data class Header(
        val epochDay: Long?,
        val label: String,
        val key: String
    ) : TimelineItem

    data class Photo(
        val mediaItem: MediaItem,
        val epochDay: Long?,
        val sectionLabel: String
    ) : TimelineItem
}

/**
 * Section metadata used for fast-scroll mapping.
 */
data class TimelineSection(
    val label: String,
    val startTimelineIndex: Int,
    val startPhotoIndex: Int,
    val endPhotoIndex: Int
)

/**
 * Complete timeline mapping consumed by the UI and fast-scroll component.
 */
data class TimelineUiModel(
    val items: List<TimelineItem>,
    val sections: List<TimelineSection>,
    val photoTimelineIndices: IntArray
) {
    val hasPhotos: Boolean = photoTimelineIndices.isNotEmpty()

    fun timelineIndexForFraction(fraction: Float): Int {
        if (photoTimelineIndices.isEmpty()) return 0
        val photoIndex = ((photoTimelineIndices.size - 1) * fraction.coerceIn(0f, 1f)).roundToInt()
        return photoTimelineIndices[photoIndex]
    }

    fun sectionLabelForFraction(fraction: Float): String {
        if (photoTimelineIndices.isEmpty() || sections.isEmpty()) return ""
        val photoIndex = ((photoTimelineIndices.size - 1) * fraction.coerceIn(0f, 1f)).roundToInt()
        return sectionLabelForPhotoIndex(photoIndex)
    }

    fun sectionLabelForTimelineIndex(index: Int): String {
        if (sections.isEmpty()) return ""
        val clampedIndex = index.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        var low = 0
        var high = sections.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val sectionStart = sections[mid].startTimelineIndex
            if (sectionStart <= clampedIndex) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return sections[result].label
    }

    private fun sectionLabelForPhotoIndex(photoIndex: Int): String {
        var low = 0
        var high = sections.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val section = sections[mid]
            when {
                photoIndex < section.startPhotoIndex -> high = mid - 1
                photoIndex > section.endPhotoIndex -> low = mid + 1
                else -> return section.label
            }
            if (section.startPhotoIndex <= photoIndex) {
                result = mid
            }
        }
        return sections[result].label
    }
}

fun buildTimelineUiModel(
    mediaItems: List<MediaItem>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    nowDate: LocalDate = LocalDate.now(zoneId)
): TimelineUiModel {
    if (mediaItems.isEmpty()) {
        return TimelineUiModel(
            items = emptyList(),
            sections = emptyList(),
            photoTimelineIndices = IntArray(0)
        )
    }

    val sortedMedia = mediaItems.sortedWith(
        compareByDescending<MediaItem> { it.dateTaken ?: Long.MIN_VALUE }
            .thenByDescending { it.id }
    )

    val groupedByDate = sortedMedia.groupBy { item ->
        item.dateTaken?.let { millis ->
            Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
        }
    }

    val timelineItems = ArrayList<TimelineItem>(sortedMedia.size + groupedByDate.size)
    val sections = ArrayList<TimelineSection>(groupedByDate.size)
    val photoIndices = ArrayList<Int>(sortedMedia.size)

    groupedByDate.forEach { (localDate, photos) ->
        val epochDay = localDate?.toEpochDay()
        val sectionLabel = localDate?.toTimelineHeaderLabel(nowDate = nowDate, locale = locale)
            ?: "Unknown date"
        val headerIndex = timelineItems.size

        timelineItems += TimelineItem.Header(
            epochDay = epochDay,
            label = sectionLabel,
            key = "timeline_header_${epochDay ?: "unknown"}"
        )

        val startPhotoIndex = photoIndices.size
        photos.forEach { photo ->
            timelineItems += TimelineItem.Photo(
                mediaItem = photo,
                epochDay = epochDay,
                sectionLabel = sectionLabel
            )
            photoIndices += timelineItems.lastIndex
        }

        sections += TimelineSection(
            label = sectionLabel,
            startTimelineIndex = headerIndex,
            startPhotoIndex = startPhotoIndex,
            endPhotoIndex = photoIndices.lastIndex
        )
    }

    return TimelineUiModel(
        items = timelineItems,
        sections = sections,
        photoTimelineIndices = photoIndices.toIntArray()
    )
}

private fun LocalDate.toTimelineHeaderLabel(nowDate: LocalDate, locale: Locale): String {
    val formatter = if (year == nowDate.year) {
        DateTimeFormatter.ofPattern("MMM d", locale)
    } else {
        DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    }
    return format(formatter)
}
