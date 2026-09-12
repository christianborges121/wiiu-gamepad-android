package com.cemupad.config

/**
 * Pure encode/decode for persisted UI settings so unit tests do not need
 * SharedPreferences. Unknown or missing values fall back to defaults:
 * diagnostics overlay off, aspect fit, native 854x480.
 */
object AppSettingsCodec {
    const val KEY_FIT_MODE = "display_fit_mode"
    const val KEY_RESOLUTION = "display_resolution"
    const val KEY_DIAGNOSTICS_OVERLAY = "diagnostics_overlay_enabled"
    const val KEY_CONNECTION_HELP = "connection_help_visible"
    const val KEY_LIMIT_30_FPS = "limit_to_30_fps"

    fun decode(
        fitModeName: String?,
        resolutionName: String?,
        diagnosticsOverlayEnabled: Boolean?,
        connectionHelpVisible: Boolean? = null,
        limitTo30Fps: Boolean? = null
    ): DisplaySettings {
        val fitMode = fitModeName
            ?.let { name -> DisplayFitMode.values().firstOrNull { it.name == name } }
            ?: DisplayFitMode.ASPECT_FIT
        val resolution = resolutionName
            ?.let { name -> DisplayResolutionPreset.values().firstOrNull { it.name == name } }
            ?: DisplayResolutionPreset.NATIVE_854x480
        return DisplaySettings(
            fitMode = fitMode,
            resolutionPreset = resolution,
            diagnosticsOverlayEnabled = diagnosticsOverlayEnabled ?: false,
            showConnectionHelp = connectionHelpVisible ?: true,
            limitTo30Fps = limitTo30Fps ?: true
        )
    }

    fun encode(settings: DisplaySettings): EncodedAppSettings {
        return EncodedAppSettings(
            fitModeName = settings.fitMode.name,
            resolutionName = settings.resolutionPreset.name,
            diagnosticsOverlayEnabled = settings.diagnosticsOverlayEnabled,
            connectionHelpVisible = settings.showConnectionHelp,
            limitTo30Fps = settings.limitTo30Fps
        )
    }
}

data class EncodedAppSettings(
    val fitModeName: String,
    val resolutionName: String,
    val diagnosticsOverlayEnabled: Boolean,
    val connectionHelpVisible: Boolean,
    val limitTo30Fps: Boolean
)
