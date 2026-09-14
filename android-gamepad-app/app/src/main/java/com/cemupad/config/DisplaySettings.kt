package com.cemupad.config

import kotlin.math.max
import kotlin.math.min

enum class DisplayFitMode(val label: String) {
    ASPECT_FIT("Aspect fit"),
    FILL("Screen fill"),
    STRETCH("Stretch");
}

enum class DisplayResolutionPreset(
    val label: String,
    val width: Int,
    val height: Int
) {
    NATIVE_854x480("Native 854x480", 854, 480),
    HD_1280x720("Wide 1280x720", 1280, 720),
    FULL_HD_1920x1080("Full HD 1920x1080", 1920, 1080),
    DEVICE_AUTO("Device auto", 0, 0);

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            16f / 9f
        }

    fun resolveForDevice(screenWidthPx: Int, screenHeightPx: Int, bitrateMbps: Int): DisplayResolutionPreset {
        if (this != DEVICE_AUTO) return this
        val maxDim = maxOf(screenWidthPx, screenHeightPx)
        val minDim = minOf(screenWidthPx, screenHeightPx)
        return if (maxDim >= 1920 && minDim >= 1080 && bitrateMbps >= 12) {
            FULL_HD_1920x1080
        } else if (maxDim >= 1280 && minDim >= 720) {
            HD_1280x720
        } else {
            NATIVE_854x480
        }
    }
}

enum class FramePacingMode(val label: String) {
    IMMEDIATE("Lowest Latency (Immediate)"),
    VSYNC("Smooth VSync (Choreographer)");
}

enum class VideoCodecPreference(val label: String, val mimeType: String) {
    AUTO("Auto", "video/avc"),
    H264("H.264 (AVC)", "video/avc"),
    HEVC("HEVC (H.265)", "video/hevc");
}

data class DisplaySettings(
    val fitMode: DisplayFitMode = DisplayFitMode.ASPECT_FIT,
    val resolutionPreset: DisplayResolutionPreset = DisplayResolutionPreset.NATIVE_854x480,
    val videoBitrateMbps: Int = VIDEO_BITRATE_DEFAULT_MBPS,
    val diagnosticsOverlayEnabled: Boolean = false,
    val showConnectionHelp: Boolean = true,
    val limitTo30Fps: Boolean = false,
    val showVirtualControls: Boolean = false,
    val virtualControlsOpacity: Float = 0.5f,
    val audioEnabled: Boolean = true,
    val audioVolume: Float = 1.0f,
    val vibrationEnabled: Boolean = false,
    val vibrationIntensity: Float = 1.0f,
    val stickDeadzone: Float = 0.08f,
    val micEnabled: Boolean = false,
    val framePacing: FramePacingMode = FramePacingMode.IMMEDIATE,
    val videoCodec: VideoCodecPreference = VideoCodecPreference.AUTO
) {
    companion object {
        const val VIDEO_BITRATE_DEFAULT_MBPS = 10
        val VALID_VIDEO_BITRATES_MBPS = listOf(4, 6, 8, 10, 12)

        fun sanitizeBitrateMbps(value: Int): Int {
            return if (VALID_VIDEO_BITRATES_MBPS.contains(value)) value else VIDEO_BITRATE_DEFAULT_MBPS
        }
    }
}

data class DisplayDimensions(val width: Float, val height: Float)

data class DisplayBufferSize(val width: Int, val height: Int)

object DisplayLayout {
    /**
     * Returns the largest rectangle with [targetAspectRatio] that fits entirely
     * within the supplied container. This avoids measuring from width alone,
     * which can make a 16:9 surface taller than a landscape display.
     */
    fun aspectFitDimensions(
        containerWidth: Float,
        containerHeight: Float,
        targetAspectRatio: Float
    ): DisplayDimensions {
        require(containerWidth > 0f)
        require(containerHeight > 0f)
        require(targetAspectRatio > 0f)

        val widthFromHeight = containerHeight * targetAspectRatio
        val width = min(containerWidth, widthFromHeight)
        return DisplayDimensions(width = width, height = width / targetAspectRatio)
    }

    /**
     * Returns the smallest target-aspect rectangle that covers the entire
     * container. The overflow is intentionally cropped by the parent.
     */
    fun aspectFillDimensions(
        containerWidth: Float,
        containerHeight: Float,
        targetAspectRatio: Float
    ): DisplayDimensions {
        require(containerWidth > 0f)
        require(containerHeight > 0f)
        require(targetAspectRatio > 0f)

        val widthFromHeight = containerHeight * targetAspectRatio
        val width = max(containerWidth, widthFromHeight)
        return DisplayDimensions(width = width, height = width / targetAspectRatio)
    }

    /**
     * Returns the SurfaceView buffer size for [preset]. Explicit presets use
     * their own dimensions; Device Auto follows the container so the surface
     * matches the laid-out view size.
     */
    fun surfaceBufferSize(
        preset: DisplayResolutionPreset,
        containerWidthPx: Int,
        containerHeightPx: Int
    ): DisplayBufferSize {
        require(containerWidthPx > 0)
        require(containerHeightPx > 0)
        return if (preset.width > 0 && preset.height > 0) {
            DisplayBufferSize(width = preset.width, height = preset.height)
        } else {
            DisplayBufferSize(width = containerWidthPx, height = containerHeightPx)
        }
    }
}
