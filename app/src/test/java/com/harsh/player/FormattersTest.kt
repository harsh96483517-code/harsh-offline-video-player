package com.harsh.player

import com.harsh.player.data.model.VideoItem
import com.harsh.player.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for formatting and lightweight model logic. */
class FormattersTest {

    @Test
    fun duration_formatsMinutesAndSeconds() {
        assertEquals("01:30", Formatters.duration(90_000))
        assertEquals("1:00:00", Formatters.duration(3_600_000))
        assertEquals("--:--", Formatters.duration(0))
    }

    @Test
    fun resolution_labelsCommonHeights() {
        assertTrue(Formatters.resolution(1920, 1080).startsWith("1080p"))
        assertTrue(Formatters.resolution(1280, 720).startsWith("720p"))
        assertEquals("Unknown", Formatters.resolution(0, 0))
    }

    @Test
    fun fileSize_usesSensibleUnits() {
        assertTrue(Formatters.fileSize(1_500_000_000).contains("GB"))
        assertTrue(Formatters.fileSize(5_000_000).contains("MB"))
    }

    @Test
    fun seriesDetection() {
        val series = VideoItem(
            id = 1, uri = android.net.Uri.EMPTY, title = "Show S01E03.mkv",
            durationMs = 0, sizeBytes = 0, width = 0, height = 0,
            relativePath = "", dateAddedSec = 0, bucketName = "b"
        )
        val movie = series.copy(title = "Holiday.mp4")
        assertTrue(series.isSeriesLike)
        assertFalse(movie.isSeriesLike)
        assertTrue(movie.isMovieLike)
    }
}
