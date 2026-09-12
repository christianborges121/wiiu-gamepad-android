package com.cemupad.config

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayLayoutTest {

    @Test
    fun `aspect fit constrains a 16 by 9 surface by height on a wide display`() {
        val dimensions = DisplayLayout.aspectFitDimensions(
            containerWidth = 2340f,
            containerHeight = 1080f,
            targetAspectRatio = 16f / 9f
        )

        assertEquals(1920f, dimensions.width, 0.01f)
        assertEquals(1080f, dimensions.height, 0.01f)
    }

    @Test
    fun `aspect fit constrains a 16 by 9 surface by width on a narrow display`() {
        val dimensions = DisplayLayout.aspectFitDimensions(
            containerWidth = 1080f,
            containerHeight = 2340f,
            targetAspectRatio = 16f / 9f
        )

        assertEquals(1080f, dimensions.width, 0.01f)
        assertEquals(607.5f, dimensions.height, 0.01f)
    }

    @Test
    fun `screen fill covers a wide display while preserving 16 by 9`() {
        val dimensions = DisplayLayout.aspectFillDimensions(
            containerWidth = 2340f,
            containerHeight = 1080f,
            targetAspectRatio = 16f / 9f
        )

        assertEquals(2340f, dimensions.width, 0.01f)
        assertEquals(1316.25f, dimensions.height, 0.01f)
    }

    @Test
    fun `explicit resolution preset uses its own buffer size`() {
        val size = DisplayLayout.surfaceBufferSize(
            preset = DisplayResolutionPreset.HD_1280x720,
            containerWidthPx = 2340,
            containerHeightPx = 1080
        )

        assertEquals(DisplayBufferSize(width = 1280, height = 720), size)
    }

    @Test
    fun `device auto follows the container buffer size`() {
        val size = DisplayLayout.surfaceBufferSize(
            preset = DisplayResolutionPreset.DEVICE_AUTO,
            containerWidthPx = 1920,
            containerHeightPx = 1080
        )

        assertEquals(DisplayBufferSize(width = 1920, height = 1080), size)
    }
}
