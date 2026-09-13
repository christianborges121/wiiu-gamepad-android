package com.cemupad.ui.main

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.cemupad.input.GamepadInputHandler
import com.cemupad.network.DiscoveredServer
import com.cemupad.ui.controls.MicIndicatorDot
import com.cemupad.ui.controls.VirtualButton
import com.cemupad.ui.controls.VirtualGamePadOverlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cemupad.config.DisplayBufferSize
import com.cemupad.config.DisplayFitMode
import com.cemupad.config.DisplayLayout
import com.cemupad.config.DisplayResolutionPreset
import com.cemupad.config.DisplaySettings
import com.cemupad.dsu.DSUServer
import com.cemupad.input.TouchInputHandler
import com.cemupad.ui.TouchSurfaceView
import com.cemupad.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    dsuServer: DSUServer?,
    touchHandler: TouchInputHandler?,
    gamepadHandler: GamepadInputHandler? = null,
    discoveredServer: DiscoveredServer? = null,
    onConnectToServer: ((String) -> Unit)? = null,
    onMicBlowChanged: ((Boolean) -> Unit)? = null,
    isVideoStreaming: Boolean = false,
    videoFps: Float = 0f,
    displaySettings: DisplaySettings = DisplaySettings(),
    onDisplaySettingsChanged: (DisplaySettings) -> Unit = {},
    onPreviewVibration: ((Float) -> Unit)? = null,
    onCalibrateGyro: (() -> Unit)? = null,
    onSurfaceAvailable: ((Surface) -> Unit)? = null,
    onSurfaceDestroyed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var ipAddress by remember { mutableStateOf("127.0.0.1") }
    var clientCount by remember { mutableStateOf(0) }
    var packetsReceived by remember { mutableLongStateOf(0L) }
    var packetsSent by remember { mutableLongStateOf(0L) }
    var diagnosticsEnabled by remember { mutableStateOf(displaySettings.diagnosticsOverlayEnabled) }
    var showVirtualControls by remember { mutableStateOf(displaySettings.showVirtualControls) }
    var virtualControlsOpacity by remember { mutableFloatStateOf(displaySettings.virtualControlsOpacity) }
    var audioEnabled by remember { mutableStateOf(displaySettings.audioEnabled) }
    var audioVolume by remember { mutableFloatStateOf(displaySettings.audioVolume) }
    var vibrationEnabled by remember { mutableStateOf(displaySettings.vibrationEnabled) }
    var vibrationIntensity by remember { mutableFloatStateOf(displaySettings.vibrationIntensity) }
    var stickDeadzone by remember { mutableFloatStateOf(displaySettings.stickDeadzone) }
    var micEnabled by remember { mutableStateOf(displaySettings.micEnabled) }
    var selectedFitMode by remember { mutableStateOf(displaySettings.fitMode) }
    var selectedResolution by remember { mutableStateOf(displaySettings.resolutionPreset) }
    var selectedBitrateMbps by remember { mutableStateOf(displaySettings.videoBitrateMbps) }
    var showFitMenu by remember { mutableStateOf(false) }
    var showResolutionMenu by remember { mutableStateOf(false) }
    var showBitrateMenu by remember { mutableStateOf(false) }
    var videoHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(displaySettings) {
        selectedFitMode = displaySettings.fitMode
        selectedResolution = displaySettings.resolutionPreset
        selectedBitrateMbps = displaySettings.videoBitrateMbps
        diagnosticsEnabled = displaySettings.diagnosticsOverlayEnabled
        showVirtualControls = displaySettings.showVirtualControls
        virtualControlsOpacity = displaySettings.virtualControlsOpacity
        audioEnabled = displaySettings.audioEnabled
        audioVolume = displaySettings.audioVolume
        vibrationEnabled = displaySettings.vibrationEnabled
        vibrationIntensity = displaySettings.vibrationIntensity
        stickDeadzone = displaySettings.stickDeadzone
        micEnabled = displaySettings.micEnabled
    }

    fun currentSettings() = DisplaySettings(
        fitMode = selectedFitMode,
        resolutionPreset = selectedResolution,
        videoBitrateMbps = selectedBitrateMbps,
        diagnosticsOverlayEnabled = diagnosticsEnabled,
        showConnectionHelp = true,
        limitTo30Fps = false,
        showVirtualControls = showVirtualControls,
        virtualControlsOpacity = virtualControlsOpacity,
        audioEnabled = audioEnabled,
        audioVolume = audioVolume,
        vibrationEnabled = vibrationEnabled,
        vibrationIntensity = vibrationIntensity,
        stickDeadzone = stickDeadzone,
        micEnabled = micEnabled
    )

    LaunchedEffect(dsuServer) {
        ipAddress = NetworkUtils.getLocalIpAddress()
        while (true) {
            dsuServer?.let {
                clientCount = it.activeClientCount
                packetsReceived = it.packetsReceived.get()
                packetsSent = it.packetsSent.get()
            }
            delay(250)
        }
    }

    BackHandler {
        scope.launch {
            if (drawerState.isOpen) {
                drawerState.close()
            } else {
                drawerState.open()
            }
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen) {
            onDisplaySettingsChanged(currentSettings())
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F141E),
                modifier = Modifier.width(340.dp)
            ) {
                val switchColors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF00B4D8),
                    uncheckedThumbColor = Color(0xFF7A8B9E),
                    uncheckedTrackColor = Color(0xFF222B3D),
                    uncheckedBorderColor = Color.Transparent
                )
                val sliderColors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00B4D8),
                    inactiveTrackColor = Color(0xFF222B3D)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- Drawer Header ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configuration",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { scope.launch { drawerState.close() } }
                        ) {
                            Text("Done", color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // --- DISPLAY SECTION ---
                    Text(
                        text = "DISPLAY",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2B)),
                        border = BorderStroke(1.dp, Color(0xFF222B3D))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Resolution selector (NO description)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Resolution", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                    OutlinedButton(
                                        onClick = { showResolutionMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                                        border = BorderStroke(1.dp, Color(0xFF2A3446))
                                    ) {
                                        Text(selectedResolution.label, fontSize = 13.sp)
                                    }
                                }
                                DropdownMenu(
                                    expanded = showResolutionMenu,
                                    onDismissRequest = { showResolutionMenu = false },
                                    modifier = Modifier.background(Color(0xFF1A2332))
                                ) {
                                    DisplayResolutionPreset.values().forEach { preset ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    preset.label,
                                                    color = if (preset == selectedResolution) Color(0xFF00E5FF) else Color.White
                                                )
                                            },
                                            onClick = {
                                                selectedResolution = preset
                                                showResolutionMenu = false
                                                onDisplaySettingsChanged(currentSettings().copy(resolutionPreset = preset))
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF222B3D))

                            // Fit mode selector
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Fit mode", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                    OutlinedButton(
                                        onClick = { showFitMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                                        border = BorderStroke(1.dp, Color(0xFF2A3446))
                                    ) {
                                        Text(selectedFitMode.label, fontSize = 13.sp)
                                    }
                                }
                                DropdownMenu(
                                    expanded = showFitMenu,
                                    onDismissRequest = { showFitMenu = false },
                                    modifier = Modifier.background(Color(0xFF1A2332))
                                ) {
                                    DisplayFitMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    mode.label,
                                                    color = if (mode == selectedFitMode) Color(0xFF00E5FF) else Color.White
                                                )
                                            },
                                            onClick = {
                                                selectedFitMode = mode
                                                showFitMenu = false
                                                onDisplaySettingsChanged(currentSettings().copy(fitMode = mode))
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF222B3D))

                            // Stream bitrate selector (live encoder control)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Stream bitrate", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                    OutlinedButton(
                                        onClick = { showBitrateMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                                        border = BorderStroke(1.dp, Color(0xFF2A3446))
                                    ) {
                                        Text("$selectedBitrateMbps Mbps", fontSize = 13.sp)
                                    }
                                }
                                DropdownMenu(
                                    expanded = showBitrateMenu,
                                    onDismissRequest = { showBitrateMenu = false },
                                    modifier = Modifier.background(Color(0xFF1A2332))
                                ) {
                                    DisplaySettings.VALID_VIDEO_BITRATES_MBPS.forEach { bitrate ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "$bitrate Mbps",
                                                    color = if (bitrate == selectedBitrateMbps) Color(0xFF00E5FF) else Color.White
                                                )
                                            },
                                            onClick = {
                                                selectedBitrateMbps = bitrate
                                                showBitrateMenu = false
                                                onDisplaySettingsChanged(currentSettings().copy(videoBitrateMbps = bitrate))
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF222B3D))

                            // Diagnostics overlay (NO description)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Diagnostics overlay", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                Switch(
                                    checked = diagnosticsEnabled,
                                    onCheckedChange = { enabled ->
                                        diagnosticsEnabled = enabled
                                        onDisplaySettingsChanged(currentSettings().copy(diagnosticsOverlayEnabled = enabled))
                                    },
                                    colors = switchColors
                                )
                            }
                        }
                    }

                    // --- AUDIO SECTION ---
                    Text(
                        text = "AUDIO",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2B)),
                        border = BorderStroke(1.dp, Color(0xFF222B3D))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // GamePad audio (NO description)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GamePad audio", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                Switch(
                                    checked = audioEnabled,
                                    onCheckedChange = { enabled ->
                                        audioEnabled = enabled
                                        onDisplaySettingsChanged(currentSettings().copy(audioEnabled = enabled))
                                    },
                                    colors = switchColors
                                )
                            }

                            if (audioEnabled) {
                                HorizontalDivider(color = Color(0xFF222B3D))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Volume", color = Color(0xFF9FB0C6), fontSize = 13.sp)
                                        Text("${(audioVolume * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Slider(
                                        value = audioVolume,
                                        onValueChange = {
                                            audioVolume = it
                                            onDisplaySettingsChanged(currentSettings().copy(audioVolume = it))
                                        },
                                        colors = sliderColors,
                                        valueRange = 0f..1f
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF222B3D))

                            // Microphone
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Microphone", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                Switch(
                                    checked = micEnabled,
                                    onCheckedChange = { enabled ->
                                        micEnabled = enabled
                                        onDisplaySettingsChanged(currentSettings().copy(micEnabled = enabled))
                                    },
                                    colors = switchColors
                                )
                            }
                        }
                    }

                    // --- INPUT & HAPTICS SECTION ---
                    Text(
                        text = "INPUT & HAPTICS",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2B)),
                        border = BorderStroke(1.dp, Color(0xFF222B3D))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Vibration (was "Vibration & rumble", NO description)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Vibration", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                Switch(
                                    checked = vibrationEnabled,
                                    onCheckedChange = { enabled ->
                                        vibrationEnabled = enabled
                                        onDisplaySettingsChanged(currentSettings().copy(vibrationEnabled = enabled))
                                    },
                                    colors = switchColors
                                )
                            }

                            if (vibrationEnabled) {
                                HorizontalDivider(color = Color(0xFF222B3D))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Intensity", color = Color(0xFF9FB0C6), fontSize = 13.sp)
                                        Text(
                                            if (vibrationIntensity <= 0f) "Off" else "${(vibrationIntensity * 100).toInt()}%",
                                            color = if (vibrationIntensity <= 0f) Color(0xFF9FB0C6) else Color(0xFF00E5FF),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Slider(
                                        value = vibrationIntensity,
                                        onValueChange = {
                                            vibrationIntensity = it
                                            onDisplaySettingsChanged(currentSettings().copy(vibrationIntensity = it))
                                        },
                                        onValueChangeFinished = {
                                            onPreviewVibration?.invoke(vibrationIntensity)
                                        },
                                        colors = sliderColors,
                                        valueRange = 0f..1f
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF222B3D))

                            // Virtual controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Virtual controls", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                Switch(
                                    checked = showVirtualControls,
                                    onCheckedChange = { enabled ->
                                        showVirtualControls = enabled
                                        onDisplaySettingsChanged(currentSettings().copy(showVirtualControls = enabled))
                                    },
                                    colors = switchColors
                                )
                            }

                            if (showVirtualControls) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Controls opacity", color = Color(0xFF9FB0C6), fontSize = 13.sp)
                                        Text("${(virtualControlsOpacity * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Slider(
                                        value = virtualControlsOpacity,
                                        onValueChange = {
                                            virtualControlsOpacity = it
                                            onDisplaySettingsChanged(currentSettings().copy(virtualControlsOpacity = it))
                                        },
                                        colors = sliderColors,
                                        valueRange = 0.15f..1.0f
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF222B3D))
                            } else {
                                HorizontalDivider(color = Color(0xFF222B3D))
                            }

                            // Stick deadzone
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Stick deadzone", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                    Text("${(stickDeadzone * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Slider(
                                    value = stickDeadzone,
                                    onValueChange = {
                                        stickDeadzone = it
                                        onDisplaySettingsChanged(currentSettings().copy(stickDeadzone = it))
                                    },
                                    colors = sliderColors,
                                    valueRange = 0.02f..0.25f
                                )
                            }

                            HorizontalDivider(color = Color(0xFF222B3D))

                            // Motion & Gyroscope calibration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Motion & gyroscope", color = Color(0xFFEAF2FF), fontSize = 14.sp)
                                var isCalibrated by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = {
                                        onCalibrateGyro?.invoke()
                                        isCalibrated = true
                                        scope.launch {
                                            kotlinx.coroutines.delay(1800)
                                            isCalibrated = false
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isCalibrated) Color(0xFF00E5FF) else Color(0xFFEAF2FF)
                                    ),
                                    border = BorderStroke(1.dp, if (isCalibrated) Color(0xFF00E5FF) else Color(0xFF2A3446))
                                ) {
                                    Text(if (isCalibrated) "Calibrated ✓" else "Calibrate", fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val containerPx = with(density) {
                DisplayBufferSize(maxWidth.roundToPx(), maxHeight.roundToPx())
            }
            fun applySurfaceSize() {
                videoHolder?.let { holder ->
                    val size = DisplayLayout.surfaceBufferSize(
                        preset = selectedResolution,
                        containerWidthPx = containerPx.width,
                        containerHeightPx = containerPx.height
                    )
                    holder.setFixedSize(size.width, size.height)
                }
            }
            // Keyed on both the preset and the holder: surface recreation
            // reuses the same holder instance, so this does not loop, and it
            // always applies the current preset instead of a stale closure.
            LaunchedEffect(selectedResolution, videoHolder) {
                applySurfaceSize()
            }

            val contentModifier = when (selectedFitMode) {
                DisplayFitMode.ASPECT_FIT -> {
                    val dimensions = DisplayLayout.aspectFitDimensions(
                        containerWidth = maxWidth.value,
                        containerHeight = maxHeight.value,
                        targetAspectRatio = 16f / 9f
                    )
                    Modifier
                        .width(dimensions.width.dp)
                        .height(dimensions.height.dp)
                }
                DisplayFitMode.FILL -> {
                    val dimensions = DisplayLayout.aspectFillDimensions(
                        containerWidth = maxWidth.value,
                        containerHeight = maxHeight.value,
                        targetAspectRatio = 16f / 9f
                    )
                    Modifier
                        .width(dimensions.width.dp)
                        .height(dimensions.height.dp)
                }
                DisplayFitMode.STRETCH -> Modifier.fillMaxSize()
            }

            Box(
                modifier = contentModifier,
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    videoHolder = holder
                                    onSurfaceAvailable?.invoke(holder.surface)
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    videoHolder = null
                                    onSurfaceDestroyed?.invoke()
                                }
                            })
                        }
                    }
                )

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TouchSurfaceView(ctx).apply {
                            this.touchHandler = touchHandler
                            this.isVideoStreaming = isVideoStreaming
                            this.displayFitMode = selectedFitMode
                        }
                    },
                    update = { view ->
                        view.touchHandler = touchHandler
                        view.isVideoStreaming = isVideoStreaming
                        view.displayFitMode = selectedFitMode
                    }
                )
            }

            if (diagnosticsEnabled) {
                val streamStatus = if (isVideoStreaming) {
                    "${videoFps.toInt()} FPS"
                } else {
                    "awaiting"
                }
                Text(
                    text = "$ipAddress:${dsuServer?.port ?: 26760}  $streamStatus  c$clientCount  tx$packetsSent  rx$packetsReceived",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    color = Color(0xFFEAF2FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 6f
                        )
                    )
                )
            }

            if (!isVideoStreaming) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 420.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC0F111A)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "CemuPad",
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connect Cemu and wait for the Wii U GamePad stream to load.",
                                color = Color(0xFFEAF2FF),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Local IP: $ipAddress",
                                color = Color(0xFF00E5FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "DSU Port: ${dsuServer?.port ?: 26760}",
                                color = Color(0xFF9FB0C6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Video Port: 26761",
                                color = Color(0xFF9FB0C6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Discovery: broadcast active on UDP 26763",
                                color = Color(0xFF9FB0C6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Use the Back button to open Configuration.",
                                color = Color(0xFF7ED4FF),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (discoveredServer != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16253B)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "🟢 Cemu Found on Network!",
                                            color = Color(0xFF00E5FF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${discoveredServer.hostname} (${discoveredServer.ip})",
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                        Button(
                                            onClick = { onConnectToServer?.invoke(discoveredServer.ip) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                                        ) {
                                            Text("Connect to Cemu", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Virtual GamePad Overlay
            if (showVirtualControls && gamepadHandler != null) {
                VirtualGamePadOverlay(
                    gamepadHandler = gamepadHandler,
                    onMicBlowChanged = { blowing -> onMicBlowChanged?.invoke(blowing) },
                    micEnabled = micEnabled,
                    opacity = virtualControlsOpacity,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isVideoStreaming && micEnabled) {
                // Floating red dot indicator/button when mic is enabled
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    MicIndicatorDot(
                        onPressChanged = { down -> onMicBlowChanged?.invoke(down) }
                    )
                }
            }
        }
    }
}
