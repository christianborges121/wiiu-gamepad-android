package com.cemupad.ui.main

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    modifier: Modifier = Modifier
) {
    var ipAddress by remember { mutableStateOf("127.0.0.1") }
    var clientCount by remember { mutableStateOf(0) }
    var packetsReceived by remember { mutableLongStateOf(0L) }
    var packetsSent by remember { mutableLongStateOf(0L) }
    var videoHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
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

            if (displaySettings.diagnosticsOverlayEnabled || forceDiagnosticsOverlay) {
                val streamStatus = if (isVideoStreaming) {
                    "${videoFps.toInt()} FPS"
                } else {
                    "awaiting"
                }
                Text(
                    text = "$ipAddress:${dsuServer?.port ?: 26760}  $streamStatus  c$clientCount  tx$packetsSent  rx$packetsReceived",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
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
                                .verticalScroll(rememberScrollState())
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Wii U GamePad",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Waiting for Cemu to connect...",
                                color = Color(0xFF8FA3BF),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enter this device's IP in Cemu under\nOptions → GamePad motion source → DSU Client",
                                color = Color(0xFFC5D2E5),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = ipAddress,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Port: ${dsuServer?.port ?: 26760}",
                                color = Color(0xFF8FA3BF),
                                fontSize = 12.sp
                            )

                            if (discoveredServer != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2333)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Found ${discoveredServer.hostname} (${discoveredServer.ip})",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
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
            if (displaySettings.showVirtualControls && gamepadHandler != null) {
                VirtualGamePadOverlay(
                    gamepadHandler = gamepadHandler,
                    onMicBlowChanged = { blowing -> onMicBlowChanged?.invoke(blowing) },
                    micEnabled = displaySettings.micEnabled,
                    opacity = displaySettings.virtualControlsOpacity,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isVideoStreaming && displaySettings.micEnabled) {
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
    }
}

