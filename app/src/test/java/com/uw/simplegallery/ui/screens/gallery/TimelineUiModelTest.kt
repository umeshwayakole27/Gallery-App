package com.uw.simplegallery.ui.screens.gallery

import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class TimelineUiModelTest {

    private val zoneId: ZoneId = ZoneId.of("UTC")

    @Test
    fun buildTimelineUiModel_groupsAndSortsPhotosByDate() {
        val mar01Morning = timestampOf(2026, 3, 1, 10)
        val mar01Early = timestampOf(2026, 3, 1, 6)
        val feb28Late = timestampOf(2026, 2, 28, 22)

        val model = buildTimelineUiModel(
            mediaItems = listOf(
                media(id = 3, dateTaken = feb28Late),
                media(id = 2, dateTaken = mar01Early),
                media(id = 1, dateTaken = mar01Morning)
            ),
            zoneId = zoneId,
            locale = Locale.US,
            nowDate = LocalDate.of(2026, 3, 10)
        )

        assertEquals(5, model.items.size)
        assertTrue(model.items[0] is TimelineItem.Header)
        assertEquals("Mar 1", (model.items[0] as TimelineItem.Header).label)
        assertEquals(1L, (model.items[1] as TimelineItem.Photo).mediaItem.id)
        assertEquals(2L, (model.items[2] as TimelineItem.Photo).mediaItem.id)
        assertEquals("Feb 28", (model.items[3] as TimelineItem.Header).label)
        assertEquals(3L, (model.items[4] as TimelineItem.Photo).mediaItem.id)

        assertEquals(listOf(1, 2, 4), model.photoTimelineIndices.toList())
        assertEquals(1, model.timelineIndexForFraction(0f))
        assertEquals(4, model.timelineIndexForFraction(1f))
        assertEquals("Mar 1", model.sectionLabelForTimelineIndex(2))
        assertEquals("Feb 28", model.sectionLabelForTimelineIndex(4))
    }

    @Test
    fun buildTimelineUiModel_handlesUnknownDatesInSeparateSection() {
        val mar03 = timestampOf(2026, 3, 3, 11)
        val model = buildTimelineUiModel(
            mediaItems = listOf(
                media(id = 10, dateTaken = null),
                media(id = 11, dateTaken = mar03)
            ),
            zoneId = zoneId,
            locale = Locale.US,
            nowDate = LocalDate.of(2026, 3, 10)
        )

        assertEquals(4, model.items.size)
        assertEquals("Mar 3", (model.items[0] as TimelineItem.Header).label)
        assertEquals("Unknown date", (model.items[2] as TimelineItem.Header).label)
        assertEquals("Unknown date", model.sectionLabelForFraction(1f))
    }

    private fun media(id: Long, dateTaken: Long?): MediaItem {
        return MediaItem(
            id = id,
            name = "img_$id.jpg",
            uri = "content://media/$id",
            mimeType = "image/jpeg",
            dateTaken = dateTaken,
            mediaType = MediaType.Image,
            folderName = "Camera",
            size = 1_024L
        )
    }

    private fun timestampOf(year: Int, month: Int, day: Int, hour: Int): Long {
        return LocalDate.of(year, month, day)
            .atTime(hour, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
