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
    const val KEY_VIRTUAL_CONTROLS = "show_virtual_controls"
    const val KEY_VIRTUAL_CONTROLS_OPACITY = "virtual_controls_opacity"
    const val KEY_AUDIO_ENABLED = "audio_enabled"
    const val KEY_AUDIO_VOLUME = "audio_volume"
    const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    const val KEY_STICK_DEADZONE = "stick_deadzone"
    const val KEY_MIC_ENABLED = "mic_enabled"

    fun decode(
        fitModeName: String?,
        resolutionName: String?,
        diagnosticsOverlayEnabled: Boolean?,
        connectionHelpVisible: Boolean? = null,
        limitTo30Fps: Boolean? = null,
        showVirtualControls: Boolean? = null,
        virtualControlsOpacity: Float? = null,
        audioEnabled: Boolean? = null,
        audioVolume: Float? = null,
        vibrationEnabled: Boolean? = null,
        stickDeadzone: Float? = null,
        micEnabled: Boolean? = null
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
            limitTo30Fps = limitTo30Fps ?: false,
            showVirtualControls = showVirtualControls ?: false,
            virtualControlsOpacity = virtualControlsOpacity ?: 0.5f,
            audioEnabled = audioEnabled ?: true,
            audioVolume = audioVolume ?: 1.0f,
            vibrationEnabled = vibrationEnabled ?: true,
            stickDeadzone = stickDeadzone ?: 0.08f,
            micEnabled = micEnabled ?: true
        )
    }

    fun encode(settings: DisplaySettings): EncodedAppSettings {
        return EncodedAppSettings(
            fitModeName = settings.fitMode.name,
            resolutionName = settings.resolutionPreset.name,
            diagnosticsOverlayEnabled = settings.diagnosticsOverlayEnabled,
            connectionHelpVisible = settings.showConnectionHelp,
            limitTo30Fps = settings.limitTo30Fps,
            showVirtualControls = settings.showVirtualControls,
            virtualControlsOpacity = settings.virtualControlsOpacity,
            audioEnabled = settings.audioEnabled,
            audioVolume = settings.audioVolume,
            vibrationEnabled = settings.vibrationEnabled,
            stickDeadzone = settings.stickDeadzone,
            micEnabled = settings.micEnabled
        )
    }
}

data class EncodedAppSettings(
    val fitModeName: String,
    val resolutionName: String,
    val diagnosticsOverlayEnabled: Boolean,
    val connectionHelpVisible: Boolean,
    val limitTo30Fps: Boolean,
    val showVirtualControls: Boolean,
    val virtualControlsOpacity: Float,
    val audioEnabled: Boolean,
    val audioVolume: Float,
    val vibrationEnabled: Boolean,
    val stickDeadzone: Float,
    val micEnabled: Boolean
)

