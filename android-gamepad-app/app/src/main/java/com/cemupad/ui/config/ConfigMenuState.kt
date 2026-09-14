package com.cemupad.ui.config

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cemupad.config.DisplayFitMode
import com.cemupad.config.DisplayResolutionPreset
import com.cemupad.config.DisplaySettings
import com.cemupad.config.FramePacingMode
import com.cemupad.config.VideoCodecPreference
import com.cemupad.input.ControllerProfile

enum class ConfigScreen(val title: String) {
    ROOT("Configuration"),
    DISPLAY("Display Settings"),
    INPUT_HAPTICS("Input"),
    AUDIO("Audio Settings"),
    NETWORK("Network Information"),
    DEBUG("Debug & Troubleshooting")
}

enum class ConfigItemType {
    SUBMENU_LINK,
    MULTI_CHOICE,
    SLIDER,
    SWITCH,
    ACTION,
    INFO
}

data class ConfigMenuItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val type: ConfigItemType,
    val valueText: String = "",
    val sliderProgress: Float = 0f,
    val isEnabled: Boolean = true,
    val iconName: String? = null
)

enum class ConfigAction {
    CALIBRATE_GYRO,
    MAP_CONTROLLER,
    EXPORT_DEBUG_BUNDLE,
    RESET_SETTINGS
}

class ConfigMenuState {
    var isOpen by mutableStateOf(false)
    var currentScreen by mutableStateOf(ConfigScreen.ROOT)
    var focusedIndex by mutableIntStateOf(0)

    var requestOpen: (() -> Unit)? = null
    var requestClose: (() -> Unit)? = null

    private var lastNavTimeMs = 0L
    private var stickCentered = true

    companion object {
        private const val NAV_DEBOUNCE_MS = 220L
        private const val STICK_THRESHOLD = 0.55f
        private const val STICK_DEADZONE = 0.20f
    }

    fun open(screen: ConfigScreen = ConfigScreen.ROOT) {
        currentScreen = screen
        focusedIndex = 0
        isOpen = true
        requestOpen?.invoke()
    }

    fun close() {
        isOpen = false
        currentScreen = ConfigScreen.ROOT
        focusedIndex = 0
        requestClose?.invoke()
    }

    fun getItems(
        screen: ConfigScreen,
        settings: DisplaySettings,
        activeControllerName: String? = null,
        phoneIp: String = "127.0.0.1",
        dsuPort: Int = 26760,
        isCalibrated: Boolean = false
    ): List<ConfigMenuItem> {
        return when (screen) {
            ConfigScreen.ROOT -> listOf(
                ConfigMenuItem(
                    id = "menu_display",
                    title = "Display",
                    subtitle = "${settings.resolutionPreset.label} · ${settings.fitMode.label} · ${settings.videoBitrateMbps} Mbps",
                    type = ConfigItemType.SUBMENU_LINK,
                    iconName = "tv"
                ),
                ConfigMenuItem(
                    id = "menu_input",
                    title = "Input",
                    subtitle = "Vibration ${if (settings.vibrationEnabled) "${(settings.vibrationIntensity * 100).toInt()}%" else "Off"} · ${activeControllerName ?: "Backbone One"}",
                    type = ConfigItemType.SUBMENU_LINK,
                    iconName = "gamepad"
                ),
                ConfigMenuItem(
                    id = "menu_audio",
                    title = "Audio",
                    subtitle = if (settings.audioEnabled) "On (${(settings.audioVolume * 100).toInt()}%) · Mic ${if (settings.micEnabled) "On" else "Off"}" else "Muted",
                    type = ConfigItemType.SUBMENU_LINK,
                    iconName = "volume"
                ),
                ConfigMenuItem(
                    id = "menu_network",
                    title = "Network",
                    subtitle = "$phoneIp · DSU $dsuPort",
                    type = ConfigItemType.SUBMENU_LINK,
                    iconName = "wifi"
                ),
                ConfigMenuItem(
                    id = "menu_debug",
                    title = "Debug & Troubleshooting",
                    subtitle = "Diagnostics ${if (settings.diagnosticsOverlayEnabled) "On" else "Off"} · Export logs",
                    type = ConfigItemType.SUBMENU_LINK,
                    iconName = "bug"
                ),
                ConfigMenuItem(
                    id = "menu_close",
                    title = "Back to Game",
                    subtitle = "Close configuration and resume gameplay",
                    type = ConfigItemType.ACTION,
                    iconName = "close"
                )
            )

            ConfigScreen.DISPLAY -> listOf(
                ConfigMenuItem(
                    id = "display_resolution",
                    title = "Resolution",
                    subtitle = "Stream canvas rendering resolution",
                    type = ConfigItemType.MULTI_CHOICE,
                    valueText = settings.resolutionPreset.label
                ),
                ConfigMenuItem(
                    id = "display_fit",
                    title = "Fit Mode",
                    subtitle = "Aspect ratio scaling mode",
                    type = ConfigItemType.MULTI_CHOICE,
                    valueText = settings.fitMode.label
                ),
                ConfigMenuItem(
                    id = "display_bitrate",
                    title = "Stream Bitrate",
                    subtitle = "Live video encoding bitrate",
                    type = ConfigItemType.MULTI_CHOICE,
                    valueText = "${settings.videoBitrateMbps} Mbps"
                ),
                ConfigMenuItem(
                    id = "display_pacing",
                    title = "Frame Pacing",
                    subtitle = "Choreographer vs immediate presentation",
                    type = ConfigItemType.MULTI_CHOICE,
                    valueText = settings.framePacing.label
                ),
                ConfigMenuItem(
                    id = "display_codec",
                    title = "Video Codec",
                    subtitle = "Hardware video decoding preference",
                    type = ConfigItemType.MULTI_CHOICE,
                    valueText = settings.videoCodec.label
                )
            )

            ConfigScreen.AUDIO -> buildList {
                add(
                    ConfigMenuItem(
                        id = "audio_enabled",
                        title = "GamePad Audio",
                        subtitle = "Stream Wii U GamePad audio to phone speakers",
                        type = ConfigItemType.SWITCH,
                        valueText = if (settings.audioEnabled) "On" else "Off"
                    )
                )
                if (settings.audioEnabled) {
                    add(
                        ConfigMenuItem(
                            id = "audio_volume",
                            title = "Audio Volume",
                            subtitle = "Output volume level",
                            type = ConfigItemType.SLIDER,
                            valueText = "${(settings.audioVolume * 100).toInt()}%",
                            sliderProgress = settings.audioVolume
                        )
                    )
                }
                add(
                    ConfigMenuItem(
                        id = "audio_mic",
                        title = "Microphone",
                        subtitle = "Blow detection and voice chat input",
                        type = ConfigItemType.SWITCH,
                        valueText = if (settings.micEnabled) "On" else "Off"
                    )
                )
            }

            ConfigScreen.INPUT_HAPTICS -> buildList {
                add(
                    ConfigMenuItem(
                        id = "input_remap",
                        title = "Physical Controller",
                        subtitle = activeControllerName ?: "Configure button & stick mapping",
                        type = ConfigItemType.ACTION,
                        valueText = "Map Controls"
                    )
                )
                add(
                    ConfigMenuItem(
                        id = "input_vibration",
                        title = "Vibration & Rumble",
                        subtitle = "Haptic feedback from GamePad rumble events",
                        type = ConfigItemType.SWITCH,
                        valueText = if (settings.vibrationEnabled) "On" else "Off"
                    )
                )
                if (settings.vibrationEnabled) {
                    add(
                        ConfigMenuItem(
                            id = "input_vibration_intensity",
                            title = "Vibration Intensity",
                            subtitle = "Haptic motor strength — Not configurable on all devices",
                            type = ConfigItemType.SLIDER,
                            valueText = if (settings.vibrationIntensity <= 0f) "Off" else "${(settings.vibrationIntensity * 100).toInt()}%",
                            sliderProgress = settings.vibrationIntensity
                        )
                    )
                }
                add(
                    ConfigMenuItem(
                        id = "input_virtual_controls",
                        title = "Virtual Touch Controls",
                        subtitle = "On-screen touch buttons for portable play",
                        type = ConfigItemType.SWITCH,
                        valueText = if (settings.showVirtualControls) "On" else "Off"
                    )
                )
                if (settings.showVirtualControls) {
                    add(
                        ConfigMenuItem(
                            id = "input_virtual_opacity",
                            title = "Controls Opacity",
                            subtitle = "Touch overlay transparency",
                            type = ConfigItemType.SLIDER,
                            valueText = "${(settings.virtualControlsOpacity * 100).toInt()}%",
                            sliderProgress = (settings.virtualControlsOpacity - 0.15f) / (1.0f - 0.15f)
                        )
                    )
                }
                add(
                    ConfigMenuItem(
                        id = "input_deadzone",
                        title = "Stick Deadzone",
                        subtitle = "Inner deadzone radius for analog sticks",
                        type = ConfigItemType.SLIDER,
                        valueText = "${(settings.stickDeadzone * 100).toInt()}%",
                        sliderProgress = (settings.stickDeadzone - 0.02f) / (0.25f - 0.02f)
                    )
                )
                add(
                    ConfigMenuItem(
                        id = "input_gyro_calibrate",
                        title = "Motion & Gyroscope",
                        subtitle = if (isCalibrated) "Calibrated ✓" else "Recalibrate device level position",
                        type = ConfigItemType.ACTION,
                        valueText = if (isCalibrated) "Calibrated ✓" else "Calibrate"
                    )
                )
            }

            ConfigScreen.NETWORK -> listOf(
                ConfigMenuItem(
                    id = "net_ip",
                    title = "Phone IP Address",
                    subtitle = "Local wireless network address",
                    type = ConfigItemType.INFO,
                    valueText = phoneIp
                ),
                ConfigMenuItem(
                    id = "net_dsu",
                    title = "DSU Motion Port",
                    subtitle = "UDP port for cemuhook motion & input packets",
                    type = ConfigItemType.INFO,
                    valueText = "$dsuPort"
                ),
                ConfigMenuItem(
                    id = "net_video",
                    title = "Video Stream Port",
                    subtitle = "TCP port for low-latency H.264/HEVC stream",
                    type = ConfigItemType.INFO,
                    valueText = "26761"
                ),
                ConfigMenuItem(
                    id = "net_audio",
                    title = "Audio Stream Port",
                    subtitle = "UDP port for Opus audio streaming",
                    type = ConfigItemType.INFO,
                    valueText = "26762"
                ),
                ConfigMenuItem(
                    id = "net_discovery",
                    title = "Discovery Broadcast",
                    subtitle = "UDP beacon for automatic Cemu pairing",
                    type = ConfigItemType.INFO,
                    valueText = "26763"
                )
            )

            ConfigScreen.DEBUG -> listOf(
                ConfigMenuItem(
                    id = "debug_overlay",
                    title = "Diagnostics Overlay",
                    subtitle = "Show live stream FPS, client count, and latency stats",
                    type = ConfigItemType.SWITCH,
                    valueText = if (settings.diagnosticsOverlayEnabled) "On" else "Off"
                ),
                ConfigMenuItem(
                    id = "debug_export",
                    title = "Export Debug Bundle",
                    subtitle = "Share logs, screenshots, and system info for support",
                    type = ConfigItemType.ACTION,
                    valueText = "Export"
                )
            )
        }
    }

    fun onUp(items: List<ConfigMenuItem>) {
        if (items.isEmpty()) return
        focusedIndex = if (focusedIndex <= 0) items.size - 1 else focusedIndex - 1
    }

    fun onDown(items: List<ConfigMenuItem>) {
        if (items.isEmpty()) return
        focusedIndex = if (focusedIndex >= items.size - 1) 0 else focusedIndex + 1
    }

    fun onLeft(
        items: List<ConfigMenuItem>,
        settings: DisplaySettings,
        onSettingsChanged: (DisplaySettings) -> Unit
    ) {
        if (items.isEmpty() || focusedIndex !in items.indices) return
        val item = items[focusedIndex]
        when (item.id) {
            "display_resolution" -> {
                val presets = DisplayResolutionPreset.values()
                val idx = presets.indexOf(settings.resolutionPreset)
                val newIdx = if (idx <= 0) presets.size - 1 else idx - 1
                onSettingsChanged(settings.copy(resolutionPreset = presets[newIdx]))
            }
            "display_fit" -> {
                val modes = DisplayFitMode.values()
                val idx = modes.indexOf(settings.fitMode)
                val newIdx = if (idx <= 0) modes.size - 1 else idx - 1
                onSettingsChanged(settings.copy(fitMode = modes[newIdx]))
            }
            "display_bitrate" -> {
                val rates = DisplaySettings.VALID_VIDEO_BITRATES_MBPS
                val idx = rates.indexOf(settings.videoBitrateMbps)
                val newIdx = if (idx <= 0) rates.size - 1 else idx - 1
                onSettingsChanged(settings.copy(videoBitrateMbps = rates[newIdx]))
            }
            "display_pacing" -> {
                val modes = FramePacingMode.values()
                val idx = modes.indexOf(settings.framePacing)
                val newIdx = if (idx <= 0) modes.size - 1 else idx - 1
                onSettingsChanged(settings.copy(framePacing = modes[newIdx]))
            }
            "display_codec" -> {
                val prefs = VideoCodecPreference.values()
                val idx = prefs.indexOf(settings.videoCodec)
                val newIdx = if (idx <= 0) prefs.size - 1 else idx - 1
                onSettingsChanged(settings.copy(videoCodec = prefs[newIdx]))
            }
            "audio_volume" -> {
                val newVol = (settings.audioVolume - 0.05f).coerceIn(0f, 1.0f)
                onSettingsChanged(settings.copy(audioVolume = (newVol * 100).toInt() / 100f))
            }
            "input_vibration_intensity" -> {
                val newInt = (settings.vibrationIntensity - 0.1f).coerceIn(0f, 1.0f)
                onSettingsChanged(settings.copy(vibrationIntensity = (newInt * 10).toInt() / 10f))
            }
            "input_virtual_opacity" -> {
                val newOp = (settings.virtualControlsOpacity - 0.05f).coerceIn(0.15f, 1.0f)
                onSettingsChanged(settings.copy(virtualControlsOpacity = (newOp * 100).toInt() / 100f))
            }
            "input_deadzone" -> {
                val newDz = (settings.stickDeadzone - 0.01f).coerceIn(0.02f, 0.25f)
                onSettingsChanged(settings.copy(stickDeadzone = (newDz * 100).toInt() / 100f))
            }
        }
    }

    fun onRight(
        items: List<ConfigMenuItem>,
        settings: DisplaySettings,
        onSettingsChanged: (DisplaySettings) -> Unit
    ) {
        if (items.isEmpty() || focusedIndex !in items.indices) return
        val item = items[focusedIndex]
        when (item.id) {
            "display_resolution" -> {
                val presets = DisplayResolutionPreset.values()
                val idx = presets.indexOf(settings.resolutionPreset)
                val newIdx = if (idx >= presets.size - 1) 0 else idx + 1
                onSettingsChanged(settings.copy(resolutionPreset = presets[newIdx]))
            }
            "display_fit" -> {
                val modes = DisplayFitMode.values()
                val idx = modes.indexOf(settings.fitMode)
                val newIdx = if (idx >= modes.size - 1) 0 else idx + 1
                onSettingsChanged(settings.copy(fitMode = modes[newIdx]))
            }
            "display_bitrate" -> {
                val rates = DisplaySettings.VALID_VIDEO_BITRATES_MBPS
                val idx = rates.indexOf(settings.videoBitrateMbps)
                val newIdx = if (idx >= rates.size - 1) 0 else idx + 1
                onSettingsChanged(settings.copy(videoBitrateMbps = rates[newIdx]))
            }
            "display_pacing" -> {
                val modes = FramePacingMode.values()
                val idx = modes.indexOf(settings.framePacing)
                val newIdx = if (idx >= modes.size - 1) 0 else idx + 1
                onSettingsChanged(settings.copy(framePacing = modes[newIdx]))
            }
            "display_codec" -> {
                val prefs = VideoCodecPreference.values()
                val idx = prefs.indexOf(settings.videoCodec)
                val newIdx = if (idx >= prefs.size - 1) 0 else idx + 1
                onSettingsChanged(settings.copy(videoCodec = prefs[newIdx]))
            }
            "audio_volume" -> {
                val newVol = (settings.audioVolume + 0.05f).coerceIn(0f, 1.0f)
                onSettingsChanged(settings.copy(audioVolume = (newVol * 100).toInt() / 100f))
            }
            "input_vibration_intensity" -> {
                val newInt = (settings.vibrationIntensity + 0.1f).coerceIn(0f, 1.0f)
                onSettingsChanged(settings.copy(vibrationIntensity = (newInt * 10).toInt() / 10f))
            }
            "input_virtual_opacity" -> {
                val newOp = (settings.virtualControlsOpacity + 0.05f).coerceIn(0.15f, 1.0f)
                onSettingsChanged(settings.copy(virtualControlsOpacity = (newOp * 100).toInt() / 100f))
            }
            "input_deadzone" -> {
                val newDz = (settings.stickDeadzone + 0.01f).coerceIn(0.02f, 0.25f)
                onSettingsChanged(settings.copy(stickDeadzone = (newDz * 100).toInt() / 100f))
            }
        }
    }

    fun onSelectA(
        items: List<ConfigMenuItem>,
        settings: DisplaySettings,
        onSettingsChanged: (DisplaySettings) -> Unit,
        onAction: (ConfigAction) -> Unit
    ) {
        if (items.isEmpty() || focusedIndex !in items.indices) return
        val item = items[focusedIndex]

        when (item.type) {
            ConfigItemType.SUBMENU_LINK -> {
                when (item.id) {
                    "menu_display" -> { currentScreen = ConfigScreen.DISPLAY; focusedIndex = 0 }
                    "menu_audio" -> { currentScreen = ConfigScreen.AUDIO; focusedIndex = 0 }
                    "menu_input" -> { currentScreen = ConfigScreen.INPUT_HAPTICS; focusedIndex = 0 }
                    "menu_network" -> { currentScreen = ConfigScreen.NETWORK; focusedIndex = 0 }
                    "menu_debug" -> { currentScreen = ConfigScreen.DEBUG; focusedIndex = 0 }
                }
            }
            ConfigItemType.SWITCH -> {
                when (item.id) {
                    "audio_enabled" -> {
                        val newSettings = settings.copy(audioEnabled = !settings.audioEnabled)
                        onSettingsChanged(newSettings)
                        // Clamp focus if Audio Volume row was removed/added.
                        val newSize = getItems(currentScreen, newSettings).size
                        if (focusedIndex >= newSize) focusedIndex = (newSize - 1).coerceAtLeast(0)
                    }
                    "audio_mic" -> onSettingsChanged(settings.copy(micEnabled = !settings.micEnabled))
                    "input_vibration" -> {
                        val newSettings = settings.copy(vibrationEnabled = !settings.vibrationEnabled)
                        onSettingsChanged(newSettings)
                        val newSize = getItems(currentScreen, newSettings).size
                        if (focusedIndex >= newSize) focusedIndex = (newSize - 1).coerceAtLeast(0)
                    }
                    "input_virtual_controls" -> {
                        val newSettings = settings.copy(showVirtualControls = !settings.showVirtualControls)
                        onSettingsChanged(newSettings)
                        val newSize = getItems(currentScreen, newSettings).size
                        if (focusedIndex >= newSize) focusedIndex = (newSize - 1).coerceAtLeast(0)
                    }
                    "debug_overlay" -> onSettingsChanged(settings.copy(diagnosticsOverlayEnabled = !settings.diagnosticsOverlayEnabled))
                }
            }
            ConfigItemType.MULTI_CHOICE -> {
                onRight(items, settings, onSettingsChanged)
            }
            ConfigItemType.ACTION -> {
                when (item.id) {
                    "menu_close" -> close()
                    "input_gyro_calibrate" -> onAction(ConfigAction.CALIBRATE_GYRO)
                    "input_remap" -> onAction(ConfigAction.MAP_CONTROLLER)
                    "debug_export" -> onAction(ConfigAction.EXPORT_DEBUG_BUNDLE)
                }
            }
            ConfigItemType.SLIDER -> {
                onRight(items, settings, onSettingsChanged)
            }
            ConfigItemType.INFO -> {}
        }
    }

    fun onBackB() {
        if (currentScreen != ConfigScreen.ROOT) {
            val returnIndex = when (currentScreen) {
                ConfigScreen.DISPLAY -> 0
                ConfigScreen.INPUT_HAPTICS -> 1
                ConfigScreen.AUDIO -> 2
                ConfigScreen.NETWORK -> 3
                ConfigScreen.DEBUG -> 4
                ConfigScreen.ROOT -> 0
            }
            currentScreen = ConfigScreen.ROOT
            focusedIndex = returnIndex
        } else {
            close()
        }
    }

    fun handleKeyEvent(
        event: KeyEvent,
        profile: ControllerProfile?,
        settings: DisplaySettings,
        items: List<ConfigMenuItem>,
        onSettingsChanged: (DisplaySettings) -> Unit,
        onAction: (ConfigAction) -> Unit
    ): Boolean {
        return handleKeyEvent(event.action, event.keyCode, profile, settings, items, onSettingsChanged, onAction)
    }

    fun handleKeyEvent(
        action: Int,
        keyCode: Int,
        profile: ControllerProfile?,
        settings: DisplaySettings,
        items: List<ConfigMenuItem>,
        onSettingsChanged: (DisplaySettings) -> Unit,
        onAction: (ConfigAction) -> Unit
    ): Boolean {
        if (!isOpen) return false
        if (action != KeyEvent.ACTION_DOWN) return true
        // Menu navigation uses fixed physical keys (south=A/east=B) so remapped
        // Wii U profiles don't swap confirm/back. profile is ignored intentionally.
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                onSelectA(items, settings, onSettingsChanged, onAction)
                true
            }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                onBackB()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                onUp(items)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                onDown(items)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                onLeft(items, settings, onSettingsChanged)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                onRight(items, settings, onSettingsChanged)
                true
            }
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE -> {
                true
            }
            else -> true
        }
    }

    fun handleMotionEvent(
        event: MotionEvent,
        profile: ControllerProfile?,
        settings: DisplaySettings,
        items: List<ConfigMenuItem>,
        onSettingsChanged: (DisplaySettings) -> Unit
    ): Boolean {
        if (!isOpen) return false

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val axisLX = profile?.axisLX ?: MotionEvent.AXIS_X
        val axisLY = profile?.axisLY ?: MotionEvent.AXIS_Y
        val stickX = event.getAxisValue(axisLX)
        val stickY = event.getAxisValue(axisLY)

        val navX = if (kotlin.math.abs(hatX) > STICK_THRESHOLD) hatX else stickX
        val navY = if (kotlin.math.abs(hatY) > STICK_THRESHOLD) hatY else stickY

        val isDeflected = kotlin.math.abs(navX) > STICK_THRESHOLD || kotlin.math.abs(navY) > STICK_THRESHOLD
        val isCentered = kotlin.math.abs(navX) < STICK_DEADZONE && kotlin.math.abs(navY) < STICK_DEADZONE

        if (isCentered) {
            stickCentered = true
        }

        val now = System.currentTimeMillis()
        if (isDeflected && (stickCentered || now - lastNavTimeMs >= NAV_DEBOUNCE_MS)) {
            stickCentered = false
            lastNavTimeMs = now

            if (kotlin.math.abs(navY) >= kotlin.math.abs(navX)) {
                if (navY < -STICK_THRESHOLD) onUp(items)
                else if (navY > STICK_THRESHOLD) onDown(items)
            } else {
                if (navX < -STICK_THRESHOLD) onLeft(items, settings, onSettingsChanged)
                else if (navX > STICK_THRESHOLD) onRight(items, settings, onSettingsChanged)
            }
        }
        return true
    }
}
