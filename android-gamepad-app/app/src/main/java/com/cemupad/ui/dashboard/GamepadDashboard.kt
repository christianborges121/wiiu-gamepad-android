package com.cemupad.ui.dashboard

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cemupad.network.DiscoveredServer
import com.cemupad.dsu.DSUPacket
import com.cemupad.dsu.DSUServer
import com.cemupad.input.GamepadInputHandler
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Interactive Wii U GamePad Dashboard and Live Controller Input Visualizer.
 * Replaces the static/empty waiting state and synthetic bouncing circle with a
 * high-utility, responsive diagnostics dashboard.
 */
@Composable
fun GamepadDashboard(
    dsuServer: DSUServer?,
    gamepadHandler: GamepadInputHandler? = null,
    discoveredServer: DiscoveredServer? = null,
    onConnectToServer: ((String) -> Unit)? = null,
    ipAddress: String = "127.0.0.1",
    clientCount: Int = 0,
    packetsSent: Long = 0L,
    packetsReceived: Long = 0L,
    connectedHost: String? = null,
    isControlConnected: Boolean = false,
    isVideoStreaming: Boolean = false,
    activeControllerName: String? = null,
    onOpenInputMapping: (() -> Unit)? = null,
    onCalibrateGyro: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    virtualControlsEnabled: Boolean = false,
    onToggleVirtualControls: (() -> Unit)? = null,
    onResumeStream: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var controllerState by remember { mutableStateOf(DSUPacket.ControllerState()) }

    // Sample controller state smoothly (~30 FPS) for real-time visualizer responsiveness
    LaunchedEffect(dsuServer) {
        while (true) {
            dsuServer?.let {
                controllerState = it.controllerState
            }
            delay(33)
        }
    }

    val s1 = controllerState.state1
    val s2 = controllerState.state2

    val dpadUp = (s1 and DSUPacket.State1Flags.DPAD_UP) != 0
    val dpadDown = (s1 and DSUPacket.State1Flags.DPAD_DOWN) != 0
    val dpadLeft = (s1 and DSUPacket.State1Flags.DPAD_LEFT) != 0
    val dpadRight = (s1 and DSUPacket.State1Flags.DPAD_RIGHT) != 0
    val minus = (s1 and DSUPacket.State1Flags.SHARE_MINUS) != 0
    val plus = (s1 and DSUPacket.State1Flags.OPTIONS_PLUS) != 0
    val l3 = (s1 and DSUPacket.State1Flags.L3) != 0
    val r3 = (s1 and DSUPacket.State1Flags.R3) != 0

    val btnA = (s2 and DSUPacket.State2Flags.CROSS_A) != 0
    val btnB = (s2 and DSUPacket.State2Flags.CIRCLE_B) != 0
    val btnX = (s2 and DSUPacket.State2Flags.SQUARE_X) != 0
    val btnY = (s2 and DSUPacket.State2Flags.TRIANGLE_Y) != 0
    val btnL = (s2 and DSUPacket.State2Flags.L) != 0
    val btnR = (s2 and DSUPacket.State2Flags.R) != 0
    val btnZL = (s2 and DSUPacket.State2Flags.ZL) != 0 || controllerState.l2Analog > 30
    val btnZR = (s2 and DSUPacket.State2Flags.ZR) != 0 || controllerState.r2Analog > 30
    val home = controllerState.psHome

    // Analog stick normalized values (-1.0 to +1.0)
    val lxNorm = ((controllerState.lx - 128) / 127.0f).coerceIn(-1.0f, 1.0f)
    // Note: DSU Y axis is inverted (255 is UP, 0 is DOWN), so invert for UI rendering
    val lyNorm = -((controllerState.ly - 128) / 127.0f).coerceIn(-1.0f, 1.0f)
    val rxNorm = ((controllerState.rx - 128) / 127.0f).coerceIn(-1.0f, 1.0f)
    val ryNorm = -((controllerState.ry - 128) / 127.0f).coerceIn(-1.0f, 1.0f)

    val zlPressure = (controllerState.l2Analog / 255.0f).coerceIn(0.0f, 1.0f)
    val zrPressure = (controllerState.r2Analog / 255.0f).coerceIn(0.0f, 1.0f)

    val isConnected = isControlConnected || clientCount > 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E17),
                        Color(0xFF0F1522),
                        Color(0xFF0A0E17)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==================== TOP BAR ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Brand & Controller Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "WII U GAMEPAD",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    activeControllerName?.let { name ->
                        Text(
                            text = name,
                            color = Color(0xFF8FA3BF),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                    }
                }

                // Center Connection Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161D2B))
                        .border(1.dp, if (isConnected) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFFFFB300).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isConnected) Color(0xFF00E676) else Color(0xFFFFB300), CircleShape)
                    )
                    Text(
                        text = if (isConnected) {
                            if (!connectedHost.isNullOrEmpty()) "Cemu: $connectedHost" else "Cemu Hooked ($clientCount clients)"
                        } else {
                            "Awaiting Cemu ($ipAddress:${dsuServer?.port ?: 26760})"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Right Quick Action Tools
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isVideoStreaming && onResumeStream != null) {
                        Button(
                            onClick = onResumeStream,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Game Stream", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { onOpenSettings?.invoke() },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF37474F)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC5D2E5))
                    ) {
                        Text("Settings ⚙", fontSize = 11.sp)
                    }
                }
            }

            // ==================== MAIN THREE-WING CONTROLLER AREA ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ------------------ LEFT WING: L/ZL, Left Stick, D-Pad ------------------
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Bumpers / Triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TriggerIndicator(
                            label = "ZL",
                            isPressed = btnZL,
                            pressure = zlPressure,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BumperIndicator(
                            label = "L",
                            isPressed = btnL,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Left Analog Stick Well
                    StickWellVisualizer(
                        label = "Left Stick",
                        normX = lxNorm,
                        normY = lyNorm,
                        isClicked = l3,
                        modifier = Modifier.size(92.dp)
                    )

                    // D-Pad Cross
                    DpadVisualizer(
                        up = dpadUp,
                        down = dpadDown,
                        left = dpadLeft,
                        right = dpadRight,
                        modifier = Modifier.size(86.dp)
                    )
                }

                // ------------------ CENTER WING: Guidance, Motion, Touch, Actions ------------------
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Cemu Video Stream Setup & Guidance Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xDD121824)),
                        border = BorderStroke(1.dp, Color(0xFF232D3F)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isConnected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "🎮 Wii U GamePad Stream Ready",
                                        color = Color(0xFF00E676),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "• Tx: $packetsSent / Rx: $packetsReceived",
                                        color = Color(0xFF8FA3BF),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "In Cemu on your PC: Click Options → Separate GamePad view (or Ctrl+Tab) to stream the display.",
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "Waiting for Cemu Connection",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Run CemuPadServer on your PC and ensure your phone is on the same local network.",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )

                                if (discoveredServer != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Found ${discoveredServer.hostname} (${discoveredServer.ip})",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Button(
                                            onClick = { onConnectToServer?.invoke(discoveredServer.ip) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Connect", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live Diagnostics: 6-Axis Motion Horizon & Touch Coordinates
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Motion / Gyro Spirit Level
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xBB121824)),
                            border = BorderStroke(1.dp, Color(0xFF1F293D)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "6-AXIS MOTION / GYRO",
                                    color = Color(0xFF8FA3BF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )

                                // Miniature Gyro Tilt Indicator
                                GyroSpiritLevel(
                                    accelX = controllerState.accelX,
                                    accelY = controllerState.accelY,
                                    gyroRoll = controllerState.gyroRoll,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                )
                            }
                        }

                        // Touch Screen Status
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xBB121824)),
                            border = BorderStroke(1.dp, Color(0xFF1F293D)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "TOUCH SCREEN FEED",
                                    color = Color(0xFF8FA3BF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )

                                if (controllerState.touch1.active) {
                                    Text(
                                        text = "Active: (${controllerState.touch1.x}, ${controllerState.touch1.y})",
                                        color = Color(0xFF00E676),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Tap screen to test coordinates",
                                        color = Color(0xFF607D8B),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // System Buttons Row: Minus, HOME, Plus
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SystemButtonIndicator(label = "-", isPressed = minus, size = 32)
                        Spacer(modifier = Modifier.width(18.dp))
                        HomeButtonIndicator(isPressed = home, size = 36)
                        Spacer(modifier = Modifier.width(18.dp))
                        SystemButtonIndicator(label = "+", isPressed = plus, size = 32)
                    }

                    // Bottom Action Bar: Remap, Virtual Controls Toggle, Calibrate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onOpenInputMapping?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                        ) {
                            Text("🎮 Map Buttons", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { onToggleVirtualControls?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (virtualControlsEnabled) Color(0xFF00E676) else Color(0xFF455A64)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (virtualControlsEnabled) Color(0xFF00E676) else Color(0xFFB0BEC5)
                            )
                        ) {
                            Text(
                                if (virtualControlsEnabled) "🕹 Virtual: ON" else "🕹 Virtual: OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { onCalibrateGyro?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF455A64)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCFD8DC))
                        ) {
                            Text("🎯 Gyro Cal", fontSize = 11.sp)
                        }
                    }
                }

                // ------------------ RIGHT WING: R/ZR, Face Buttons, Right Stick ------------------
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Right Bumpers / Triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BumperIndicator(
                            label = "R",
                            isPressed = btnR,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TriggerIndicator(
                            label = "ZR",
                            isPressed = btnZR,
                            pressure = zrPressure,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Face Buttons (Diamond Layout)
                    FaceButtonsDiamond(
                        a = btnA,
                        b = btnB,
                        x = btnX,
                        y = btnY,
                        modifier = Modifier.size(92.dp)
                    )

                    // Right Analog Stick Well
                    StickWellVisualizer(
                        label = "Right Stick",
                        normX = rxNorm,
                        normY = ryNorm,
                        isClicked = r3,
                        modifier = Modifier.size(86.dp)
                    )
                }
            }
        }
    }
}

// ==================== VISUALIZER SUB-COMPONENTS ====================

@Composable
private fun StickWellVisualizer(
    label: String,
    normX: Float,
    normY: Float,
    isClicked: Boolean,
    modifier: Modifier = Modifier
) {
    val wellBorder = if (isClicked) Color(0xFF00E5FF) else Color(0xFF263238)
    val nubColor = if (isClicked) Color(0xFF00E5FF) else Color(0xFF455A64)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(Color(0xFF101622))
                .border(2.dp, wellBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Crosshair guidelines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(Color(0x22FFFFFF), Offset(cx, 4f), Offset(cx, size.height - 4f), strokeWidth = 1f)
                drawLine(Color(0x22FFFFFF), Offset(4f, cy), Offset(size.width - 4f, cy), strokeWidth = 1f)
            }

            // Deflecting stick nub
            val maxTravel = 22.dp
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (normX * maxTravel.toPx()).roundToInt(),
                            (normY * maxTravel.toPx()).roundToInt()
                        )
                    }
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(nubColor)
                    .border(1.5.dp, if (isClicked) Color.White else Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isClicked) {
                    Text("PRESS", color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            text = "$label (${(normX * 100).toInt()}%, ${(-normY * 100).toInt()}%)",
            color = Color(0xFF8FA3BF),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DpadVisualizer(
    up: Boolean,
    down: Boolean,
    left: Boolean,
    right: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Up Arm
        DpadArm(
            isPressed = up,
            label = "▲",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 28.dp, height = 30.dp)
        )
        // Down Arm
        DpadArm(
            isPressed = down,
            label = "▼",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 28.dp, height = 30.dp)
        )
        // Left Arm
        DpadArm(
            isPressed = left,
            label = "◀",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 30.dp, height = 28.dp)
        )
        // Right Arm
        DpadArm(
            isPressed = right,
            label = "▶",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 30.dp, height = 28.dp)
        )
        // Center cross intersection
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(0xFF141A26))
        )
    }
}

@Composable
private fun DpadArm(
    isPressed: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    val bg = if (isPressed) Color(0xFF00E5FF) else Color(0xFF1B2332)
    val textCol = if (isPressed) Color.Black else Color(0xFF90A4AE)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, if (isPressed) Color.White else Color(0xFF2C394F), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = textCol, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FaceButtonsDiamond(
    a: Boolean,
    b: Boolean,
    x: Boolean,
    y: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // X Button (Top)
        FaceButton(
            label = "X",
            isPressed = x,
            activeColor = Color(0xFF00E5FF),
            modifier = Modifier.align(Alignment.TopCenter)
        )
        // A Button (Right)
        FaceButton(
            label = "A",
            isPressed = a,
            activeColor = Color(0xFFFF5252),
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        // B Button (Bottom)
        FaceButton(
            label = "B",
            isPressed = b,
            activeColor = Color(0xFFFFD740),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        // Y Button (Left)
        FaceButton(
            label = "Y",
            isPressed = y,
            activeColor = Color(0xFF69F0AE),
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

@Composable
private fun FaceButton(
    label: String,
    isPressed: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val bg = if (isPressed) activeColor else Color(0xFF161E2C)
    val textColor = if (isPressed) Color.Black else Color.White
    val borderCol = if (isPressed) Color.White else activeColor.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, borderCol, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BumperIndicator(
    label: String,
    isPressed: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isPressed) Color(0xFF00E5FF) else Color(0xFF141A26)
    val textColor = if (isPressed) Color.Black else Color(0xFFCFD8DC)

    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, if (isPressed) Color.White else Color(0xFF2C394F), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TriggerIndicator(
    label: String,
    isPressed: Boolean,
    pressure: Float,
    modifier: Modifier = Modifier
) {
    val bg = if (isPressed) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF141A26)
    val borderColor = if (isPressed) Color(0xFF00E5FF) else Color(0xFF2C394F)

    Column(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = if (isPressed) Color(0xFF00E5FF) else Color(0xFFCFD8DC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "${(pressure * 100).toInt()}%", color = Color(0xFF8FA3BF), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(
            progress = { pressure },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFF00E5FF),
            trackColor = Color(0xFF263238)
        )
    }
}

@Composable
private fun SystemButtonIndicator(
    label: String,
    isPressed: Boolean,
    size: Int = 30
) {
    val bg = if (isPressed) Color(0xFF00E5FF) else Color(0xFF161E2C)
    val textCol = if (isPressed) Color.Black else Color(0xFF90A4AE)

    Box(
        modifier = Modifier
            .size(width = (size + 4).dp, height = (size - 6).dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, if (isPressed) Color.White else Color(0xFF263238), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = textCol, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeButtonIndicator(
    isPressed: Boolean,
    size: Int = 36
) {
    val bg = if (isPressed) Color(0xFF00E5FF) else Color(0xFF121926)
    val ringColor = if (isPressed) Color.White else Color(0xFF00E5FF).copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "⌂", color = if (isPressed) Color.Black else Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GyroSpiritLevel(
    accelX: Float,
    accelY: Float,
    gyroRoll: Float,
    modifier: Modifier = Modifier
) {
    // Clamped deflection of the spirit bubble
    val bubbleDeflectionX = (accelX * 0.4f).coerceIn(-1.0f, 1.0f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0E131C))
            .border(1.dp, Color(0xFF202B3D), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // Center target notch
            drawLine(Color(0x5500E5FF), Offset(cx, 2f), Offset(cx, size.height - 2f), strokeWidth = 2f)
            drawLine(Color(0x33FFFFFF), Offset(cx - 20f, cy), Offset(cx + 20f, cy), strokeWidth = 1f)

            // Dynamic bubble
            val travelRange = (size.width / 2f) - 14f
            val bubbleX = cx + (bubbleDeflectionX * travelRange)
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 6.dp.toPx(),
                center = Offset(bubbleX, cy)
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(bubbleX, cy)
            )
        }
    }
}
