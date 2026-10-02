package com.cemupad.ui.main

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.cemupad.ui.config.ConfigAction
import com.cemupad.ui.config.ConfigMenuState
import com.cemupad.ui.config.ConfigMenuView
import com.cemupad.ui.config.ConfigScreen
import com.cemupad.ui.mapping.MappingPromptBanner
import com.cemupad.ui.mapping.MappingPromptUi
import com.cemupad.ui.mapping.MappingWizard
import com.cemupad.ui.mapping.MappingWizardActions
import com.cemupad.ui.mapping.MappingWizardScreen
import com.cemupad.dsu.DSUServer
import com.cemupad.input.TouchInputHandler
import com.cemupad.ui.TouchSurfaceView
import com.cemupad.ui.dashboard.GamepadDashboard
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
    onExportDebug: (() -> Unit)? = null,
    forceDiagnosticsOverlay: Boolean = false,
    mappingPrompt: MappingPromptUi? = null,
    onMappingSetup: (() -> Unit)? = null,
    onMappingDismiss: (() -> Unit)? = null,
    activeControllerName: String? = null,
    onOpenInputMapping: (() -> Unit)? = null,
    wizardScreen: MappingWizardScreen? = null,
    wizardActions: MappingWizardActions = MappingWizardActions(),
    configMenuState: ConfigMenuState? = null,
    pinPromptVisible: Boolean = false,
    pinPromptErrorMessage: String? = null,
    onPinSubmit: ((String) -> Unit)? = null,
    onPinDismiss: (() -> Unit)? = null,
    isControlConnected: Boolean = false,
    connectedHost: String? = null,
    modifier: Modifier = Modifier
) {
    var ipAddress by remember { mutableStateOf("127.0.0.1") }
    var clientCount by remember { mutableStateOf(0) }
    var packetsReceived by remember { mutableLongStateOf(0L) }
    var packetsSent by remember { mutableLongStateOf(0L) }
    var videoHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var showDashboardOverlay by remember { mutableStateOf(false) }
    val menuState = configMenuState ?: remember { ConfigMenuState() }
    val drawerState = rememberDrawerState(
        initialValue = if (menuState.isOpen) DrawerValue.Open else DrawerValue.Closed,
        confirmStateChange = { targetValue ->
            val willBeOpen = (targetValue == DrawerValue.Open)
            menuState.isOpen = willBeOpen
            if (willBeOpen) {
                menuState.focusedIndex = 0
            } else {
                menuState.currentScreen = ConfigScreen.ROOT
            }
            true
        }
    )
    val scope = rememberCoroutineScope()
    var isCalibrated by remember { mutableStateOf(false) }

    DisposableEffect(menuState, scope) {
        menuState.requestOpen = {
            scope.launch { drawerState.open() }
        }
        menuState.requestClose = {
            scope.launch { drawerState.close() }
        }
        onDispose {
            menuState.requestOpen = null
            menuState.requestClose = null
        }
    }

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
        if (menuState.isOpen || drawerState.isOpen) {
            menuState.onBackB()
        } else {
            menuState.open()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                ConfigMenuView(
                    state = menuState,
                    settings = displaySettings,
                    onSettingsChanged = onDisplaySettingsChanged,
                    activeControllerName = activeControllerName,
                    phoneIp = ipAddress,
                    dsuPort = dsuServer?.port ?: 26760,
                    isCalibrated = isCalibrated,
                    onAction = { action ->
                        when (action) {
                            ConfigAction.CALIBRATE_GYRO -> {
                                onCalibrateGyro?.invoke()
                                isCalibrated = true
                                scope.launch {
                                    kotlinx.coroutines.delay(1800)
                                    isCalibrated = false
                                }
                            }
                            ConfigAction.MAP_CONTROLLER -> {
                                scope.launch { drawerState.close() }
                                onOpenInputMapping?.invoke()
                            }
                            ConfigAction.EXPORT_DEBUG_BUNDLE -> onExportDebug?.invoke()
                            ConfigAction.RESET_SETTINGS -> onDisplaySettingsChanged(DisplaySettings())
                        }
                    }
                )
            }
        ) {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
            val density = LocalDensity.current
            val containerPx = with(density) {
                DisplayBufferSize(maxWidth.roundToPx(), maxHeight.roundToPx())
            }
            fun applySurfaceSize() {
                videoHolder?.let { holder ->
                    val size = DisplayLayout.surfaceBufferSize(
                        preset = displaySettings.resolutionPreset,
                        containerWidthPx = containerPx.width,
                        containerHeightPx = containerPx.height
                    )
                    holder.setFixedSize(size.width, size.height)
                }
            }
            // Keyed on both the preset and the holder: surface recreation
            // reuses the same holder instance, so this does not loop, and it
            // always applies the current preset instead of a stale closure.
            LaunchedEffect(displaySettings.resolutionPreset, videoHolder) {
                applySurfaceSize()
            }

            val contentModifier = when (displaySettings.fitMode) {
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
                            this.displayFitMode = displaySettings.fitMode
                        }
                    },
                    update = { view ->
                        view.touchHandler = touchHandler
                        view.isVideoStreaming = isVideoStreaming
                        view.displayFitMode = displaySettings.fitMode
                    }
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isVideoStreaming) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC101622))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { showDashboardOverlay = !showDashboardOverlay }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showDashboardOverlay) "🎮 Back to Stream" else "🎮 Dashboard",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (displaySettings.diagnosticsOverlayEnabled || forceDiagnosticsOverlay) {
                    val streamStatus = if (isVideoStreaming) {
                        "${videoFps.toInt()} FPS"
                    } else {
                        "awaiting"
                    }
                    Text(
                        text = "$ipAddress:${dsuServer?.port ?: 26760}  $streamStatus  c$clientCount  tx$packetsSent  rx$packetsReceived",
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
            }

            if (!isVideoStreaming || showDashboardOverlay) {
                GamepadDashboard(
                    dsuServer = dsuServer,
                    gamepadHandler = gamepadHandler,
                    discoveredServer = discoveredServer,
                    onConnectToServer = onConnectToServer,
                    ipAddress = ipAddress,
                    clientCount = clientCount,
                    packetsSent = packetsSent,
                    packetsReceived = packetsReceived,
                    connectedHost = connectedHost,
                    isControlConnected = isControlConnected,
                    isVideoStreaming = isVideoStreaming,
                    activeControllerName = activeControllerName,
                    onOpenInputMapping = onOpenInputMapping,
                    onCalibrateGyro = onCalibrateGyro,
                    onOpenSettings = {
                        scope.launch { drawerState.open() }
                    },
                    virtualControlsEnabled = displaySettings.showVirtualControls,
                    onToggleVirtualControls = {
                        onDisplaySettingsChanged(
                            displaySettings.copy(showVirtualControls = !displaySettings.showVirtualControls)
                        )
                    },
                    onResumeStream = {
                        showDashboardOverlay = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Virtual GamePad Overlay
            if (displaySettings.showVirtualControls && gamepadHandler != null && (!isVideoStreaming || !showDashboardOverlay)) {
                VirtualGamePadOverlay(
                    gamepadHandler = gamepadHandler,
                    onMicBlowChanged = { blowing -> onMicBlowChanged?.invoke(blowing) },
                    micEnabled = displaySettings.micEnabled,
                    opacity = displaySettings.virtualControlsOpacity,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isVideoStreaming && displaySettings.micEnabled && !showDashboardOverlay) {
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

        // New-controller prompt renders over drawer (outside ModalNavigationDrawer so scrim doesn't hide it)
        if (mappingPrompt != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                MappingPromptBanner(
                    deviceName = mappingPrompt.deviceName,
                    matchLabel = mappingPrompt.matchLabel,
                    onSetup = { onMappingSetup?.invoke() },
                    onDismiss = { onMappingDismiss?.invoke() }
                )
            }
        }

        // Wizard renders LAST so it sits above the open drawer and prompt.
        wizardScreen?.let { MappingWizard(screen = it, actions = wizardActions) }

        if (pinPromptVisible) {
            PinInputDialog(
                errorMessage = pinPromptErrorMessage,
                onSubmit = { pin -> onPinSubmit?.invoke(pin) },
                onDismiss = { onPinDismiss?.invoke() }
            )
        }
    }
}

@Composable
fun PinInputDialog(
    errorMessage: String? = null,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pairing PIN Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the 6-digit PIN shown by CemuPadServer on your PC:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            pinText = input
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    singleLine = true,
                    placeholder = { Text("6-digit PIN") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = pinText.length == 6,
                onClick = { onSubmit(pinText) }
            ) {
                Text("Pair & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

