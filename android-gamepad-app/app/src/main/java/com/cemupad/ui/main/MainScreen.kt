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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    isVideoStreaming: Boolean = false,
    videoFps: Float = 0f,
    displaySettings: DisplaySettings = DisplaySettings(),
    onDisplaySettingsChanged: (DisplaySettings) -> Unit = {},
    onSurfaceAvailable: ((Surface) -> Unit)? = null,
    onSurfaceDestroyed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var ipAddress by remember { mutableStateOf("127.0.0.1") }
    var clientCount by remember { mutableStateOf(0) }
    var packetsReceived by remember { mutableLongStateOf(0L) }
    var packetsSent by remember { mutableLongStateOf(0L) }
    var diagnosticsEnabled by remember { mutableStateOf(displaySettings.diagnosticsOverlayEnabled) }
    var showHelp by remember { mutableStateOf(displaySettings.showConnectionHelp) }
    var limitFps by remember { mutableStateOf(displaySettings.limitTo30Fps) }
    var selectedFitMode by remember { mutableStateOf(displaySettings.fitMode) }
    var selectedResolution by remember { mutableStateOf(displaySettings.resolutionPreset) }
    var showFitMenu by remember { mutableStateOf(false) }
    var showResolutionMenu by remember { mutableStateOf(false) }
    var videoHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(displaySettings) {
        selectedFitMode = displaySettings.fitMode
        selectedResolution = displaySettings.resolutionPreset
        diagnosticsEnabled = displaySettings.diagnosticsOverlayEnabled
        showHelp = displaySettings.showConnectionHelp
        limitFps = displaySettings.limitTo30Fps
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
        scope.launch {
            if (drawerState.isOpen) {
                drawerState.close()
            } else {
                drawerState.open()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF141922),
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Configuration",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Divider(color = Color(0xFF2A3348))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diagnostics overlay",
                            color = Color(0xFFEAF2FF),
                            fontSize = 15.sp
                        )
                        Switch(
                            checked = diagnosticsEnabled,
                            onCheckedChange = { enabled ->
                                diagnosticsEnabled = enabled
                                onDisplaySettingsChanged(
                                    DisplaySettings(
                                        fitMode = selectedFitMode,
                                        resolutionPreset = selectedResolution,
                                        diagnosticsOverlayEnabled = enabled,
                                        showConnectionHelp = showHelp,
                                        limitTo30Fps = limitFps
                                    )
                                )
                            }
                        )
                    }

                    Text(
                        text = "Toggle the small status overlay for local IP, packet counters, and video FPS.",
                        color = Color(0xFF9FB0C6),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connection help",
                            color = Color(0xFFEAF2FF),
                            fontSize = 15.sp
                        )
                        Switch(
                            checked = showHelp,
                            onCheckedChange = { enabled ->
                                showHelp = enabled
                                onDisplaySettingsChanged(
                                    DisplaySettings(
                                        fitMode = selectedFitMode,
                                        resolutionPreset = selectedResolution,
                                        diagnosticsOverlayEnabled = diagnosticsEnabled,
                                        showConnectionHelp = enabled,
                                        limitTo30Fps = limitFps
                                    )
                                )
                            }
                        )
                    }

                    Text(
                        text = "Show the startup card with local IP and ports until the video stream loads.",
                        color = Color(0xFF9FB0C6),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Limit to 30 FPS",
                            color = Color(0xFFEAF2FF),
                            fontSize = 15.sp
                        )
                        Switch(
                            checked = limitFps,
                            onCheckedChange = { enabled ->
                                limitFps = enabled
                                onDisplaySettingsChanged(
                                    DisplaySettings(
                                        fitMode = selectedFitMode,
                                        resolutionPreset = selectedResolution,
                                        diagnosticsOverlayEnabled = diagnosticsEnabled,
                                        showConnectionHelp = showHelp,
                                        limitTo30Fps = enabled
                                    )
                                )
                            }
                        )
                    }

                    Text(
                        text = "Limit to 30 FPS needs Cemu encoder support (coming soon) and is not enforced yet.",
                        color = Color(0xFF9FB0C6),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { showFitMenu = true },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Text("Fit mode: ${selectedFitMode.label}")
                        }
                        DropdownMenu(
                            expanded = showFitMenu,
                            onDismissRequest = { showFitMenu = false }
                        ) {
                            DisplayFitMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        selectedFitMode = mode
                                        showFitMenu = false
                                        onDisplaySettingsChanged(
                                            DisplaySettings(
                                                fitMode = mode,
                                                resolutionPreset = selectedResolution,
                                                diagnosticsOverlayEnabled = diagnosticsEnabled,
                                                showConnectionHelp = showHelp,
                                                limitTo30Fps = limitFps
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { showResolutionMenu = true },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Text("Resolution: ${selectedResolution.label}")
                        }
                        DropdownMenu(
                            expanded = showResolutionMenu,
                            onDismissRequest = { showResolutionMenu = false }
                        ) {
                            DisplayResolutionPreset.values().forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.label) },
                                    onClick = {
                                        selectedResolution = preset
                                        showResolutionMenu = false
                                        onDisplaySettingsChanged(
                                            DisplaySettings(
                                                fitMode = selectedFitMode,
                                                resolutionPreset = preset,
                                                diagnosticsOverlayEnabled = diagnosticsEnabled,
                                                showConnectionHelp = showHelp,
                                                limitTo30Fps = limitFps
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "These settings affect how the GamePad video is scaled and framed on the Android screen.",
                        color = Color(0xFF9FB0C6),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    TextButton(
                        onClick = { scope.launch { drawerState.close() } },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
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

            if (!isVideoStreaming && showHelp) {
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
                                text = "Use the Back button to open Configuration.",
                                color = Color(0xFF7ED4FF),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
