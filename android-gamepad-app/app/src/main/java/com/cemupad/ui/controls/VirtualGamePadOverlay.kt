package com.cemupad.ui.controls

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cemupad.input.GamepadInputHandler
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Transparent on-screen touch controls for standalone handheld play without a physical controller.
 */
@Composable
fun VirtualGamePadOverlay(
    gamepadHandler: GamepadInputHandler,
    onMicBlowChanged: (Boolean) -> Unit,
    micEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    opacity: Float = 0.45f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(opacity)
    ) {
        // --- Top Bar: Shoulder & System Buttons ---
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left Bumpers: L and ZL
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VirtualButton(
                    label = "ZL",
                    onPressChanged = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyZL, down) },
                    width = 54, height = 36
                )
                VirtualButton(
                    label = "L",
                    onPressChanged = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyL, down) },
                    width = 50, height = 36
                )
            }

            // Center: -, Home, +, and Mic Blow
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VirtualButton(
                    label = "-",
                    onPressChanged = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyMinus, down) },
                    width = 36, height = 32
                )
                if (micEnabled) {
                    MicIndicatorDot(
                        onPressChanged = { down -> onMicBlowChanged(down) },
                        sizeDp = 34
                    )
                }
                VirtualButton(
                    label = "HOME",
                    onPressChanged = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyHome, down) },
                    width = 48, height = 32,
                    activeColor = Color(0xFF2979FF)
                )
                VirtualButton(
                    label = "+",
                    onPressChanged = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyPlus, down) },
                    width = 36, height = 32
                )
            }

            // Right Bumpers: R and ZR
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VirtualButton(
                    label = "R",
                    onPressChanged = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyR, down) },
                    width = 50, height = 36
                )
                VirtualButton(
                    label = "ZR",
                    onPressChanged = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyZR, down) },
                    width = 54, height = 36
                )
            }
        }

        // --- Bottom Left: D-Pad & Left Stick ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Stick
            VirtualThumbstick(
                onPositionChanged = { x, y -> gamepadHandler.setVirtualStick(isLeftStick = true, normX = x, normY = y) }
            )

            // D-Pad
            VirtualDPad(
                onDirectionChanged = { up, down, left, right ->
                    gamepadHandler.setVirtualButton(KeyEvent.KEYCODE_DPAD_UP, up)
                    gamepadHandler.setVirtualButton(KeyEvent.KEYCODE_DPAD_DOWN, down)
                    gamepadHandler.setVirtualButton(KeyEvent.KEYCODE_DPAD_LEFT, left)
                    gamepadHandler.setVirtualButton(KeyEvent.KEYCODE_DPAD_RIGHT, right)
                }
            )
        }

        // --- Bottom Right: Right Stick & Face Buttons ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Face Buttons (Nintendo Layout: X top, Y left, B bottom, A right)
            VirtualFaceButtons(
                onA = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyA, down) },
                onB = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyB, down) },
                onX = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyX, down) },
                onY = { down -> gamepadHandler.setVirtualButton(gamepadHandler.profile.keyY, down) }
            )

            // Right Stick
            VirtualThumbstick(
                onPositionChanged = { x, y -> gamepadHandler.setVirtualStick(isLeftStick = false, normX = x, normY = y) }
            )
        }
    }
}

@Composable
fun VirtualButton(
    label: String,
    onPressChanged: (Boolean) -> Unit,
    width: Int = 44,
    height: Int = 44,
    activeColor: Color = Color(0xFF00E5FF)
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) activeColor.copy(alpha = 0.7f) else Color(0x771E2433))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPressChanged(true)
                    waitForUpOrCancellation()
                    isPressed = false
                    onPressChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MicIndicatorDot(
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPressChanged(true)
                    waitForUpOrCancellation()
                    isPressed = false
                    onPressChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer dark subtle halo for contrast against game graphics
        Box(
            modifier = Modifier
                .size(if (isPressed) 30.dp else 24.dp)
                .clip(CircleShape)
                .background(if (isPressed) Color(0x99FF1744) else Color(0x77161D2B))
                .border(
                    width = 1.dp,
                    color = if (isPressed) Color(0xFFFF5252) else Color(0x44FFFFFF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner vibrant red dot
            Box(
                modifier = Modifier
                    .size(if (isPressed) 12.dp else 9.dp)
                    .clip(CircleShape)
                    .background(if (isPressed) Color.White else Color(0xFFFF1744))
            )
        }
    }
}

@Composable
fun VirtualThumbstick(
    onPositionChanged: (Float, Float) -> Unit,
    sizeDp: Int = 110
) {
    var sizePx by remember { mutableStateOf(0f) }
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .onSizeChanged { sizePx = it.width.toFloat() }
            .clip(CircleShape)
            .background(Color(0x551E2433))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val radius = if (sizePx > 0f) sizePx / 2f else 100f
                        val center = Offset(radius, radius)
                        val delta = offset - center
                        val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                        val maxDist = radius * 0.7f
                        val clampedDist = dist.coerceAtMost(maxDist)
                        val angle = Math.atan2(delta.y.toDouble(), delta.x.toDouble())
                        val nx = if (maxDist > 0f) (clampedDist * cos(angle) / maxDist).toFloat() else 0f
                        val ny = if (maxDist > 0f) (clampedDist * sin(angle) / maxDist).toFloat() else 0f
                        thumbOffset = Offset(nx * maxDist, ny * maxDist)
                        onPositionChanged(nx.coerceIn(-1f, 1f), ny.coerceIn(-1f, 1f))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val radius = if (sizePx > 0f) sizePx / 2f else 100f
                        val maxDist = radius * 0.7f
                        val newOffset = thumbOffset + dragAmount
                        val dist = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                        val clampedOffset = if (dist > maxDist && dist > 0f) {
                            Offset(newOffset.x * maxDist / dist, newOffset.y * maxDist / dist)
                        } else {
                            newOffset
                        }
                        thumbOffset = clampedOffset
                        val nx = if (maxDist > 0f) (clampedOffset.x / maxDist).coerceIn(-1f, 1f) else 0f
                        val ny = if (maxDist > 0f) (clampedOffset.y / maxDist).coerceIn(-1f, 1f) else 0f
                        onPositionChanged(nx, ny)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onPositionChanged(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onPositionChanged(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Thumb knob
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size((sizeDp * 0.45f).dp)
                .clip(CircleShape)
                .background(Color(0xAA455A64))
        )
    }
}

@Composable
fun VirtualDPad(
    onDirectionChanged: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit,
    sizeDp: Int = 110
) {
    var sizePx by remember { mutableStateOf(0f) }
    var up by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    fun updateTouch(pos: Offset, isUp: Boolean) {
        if (isUp) {
            up = false; down = false; left = false; right = false
        } else {
            val center = if (sizePx > 0f) sizePx / 2f else 100f
            val dx = pos.x - center
            val dy = pos.y - center
            val dist = sqrt(dx * dx + dy * dy)
            val deadzone = center * 0.22f
            val threshold = center * 0.28f
            val newUp: Boolean
            val newDown: Boolean
            val newLeft: Boolean
            val newRight: Boolean
            if (dist < deadzone) {
                newUp = false; newDown = false; newLeft = false; newRight = false
            } else {
                newUp = dy < -threshold
                newDown = dy > threshold
                newLeft = dx < -threshold
                newRight = dx > threshold
            }
            if ((!up && newUp) || (!down && newDown) || (!left && newLeft) || (!right && newRight)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            up = newUp
            down = newDown
            left = newLeft
            right = newRight
        }
        onDirectionChanged(up, down, left, right)
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .onSizeChanged { sizePx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> updateTouch(pos, false) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateTouch(change.position, false)
                    },
                    onDragEnd = { updateTouch(Offset.Zero, true) },
                    onDragCancel = { updateTouch(Offset.Zero, true) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Cross background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barW = sizeDp.dp.toPx() * 0.34f
            val len = sizeDp.dp.toPx()
            val off = (len - barW) / 2f
            // Vertical bar
            drawRoundRect(
                color = Color(0x661E2433),
                topLeft = Offset(off, 0f),
                size = androidx.compose.ui.geometry.Size(barW, len),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
            )
            // Horizontal bar
            drawRoundRect(
                color = Color(0x661E2433),
                topLeft = Offset(0f, off),
                size = androidx.compose.ui.geometry.Size(len, barW),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
            )
        }

        // Direction labels
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("▲", color = if (up) Color(0xFF00E5FF) else Color(0x99FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            Text("▼", color = if (down) Color(0xFF00E5FF) else Color(0x99FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("◀", color = if (left) Color(0xFF00E5FF) else Color(0x99FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
            Text("▶", color = if (right) Color(0xFF00E5FF) else Color(0x99FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
fun VirtualFaceButtons(
    onA: (Boolean) -> Unit,
    onB: (Boolean) -> Unit,
    onX: (Boolean) -> Unit,
    onY: (Boolean) -> Unit,
    sizeDp: Int = 110
) {
    Box(
        modifier = Modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        // Top: X
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            FaceButton("X", onX, Color(0xFF00E5FF))
        }
        // Bottom: B
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            FaceButton("B", onB, Color(0xFFFFD600))
        }
        // Left: Y
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            FaceButton("Y", onY, Color(0xFF00E676))
        }
        // Right: A
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            FaceButton("A", onA, Color(0xFFFF1744))
        }
    }
}

@Composable
private fun FaceButton(
    label: String,
    onPressChanged: (Boolean) -> Unit,
    accentColor: Color
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isPressed) accentColor.copy(alpha = 0.8f) else Color(0x771E2433))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPressChanged(true)
                    waitForUpOrCancellation()
                    isPressed = false
                    onPressChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
