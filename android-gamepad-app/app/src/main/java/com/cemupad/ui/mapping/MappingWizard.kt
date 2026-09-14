package com.cemupad.ui.mapping

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cemupad.input.DetectionConfidence
import kotlinx.coroutines.delay

/**
 * First-connect input mapping wizard: Detected → Test → Capture → Done.
 * All state lives in MainActivity; this file is pure presentation in the
 * dark drawer card style.
 */

data class MappingTestRow(val label: String, val keyCode: Int, val dpadDir: String? = null, val stickDir: String? = null)

/** Non-blocking first-sight prompt state (owned by MainActivity). */
data class MappingPromptUi(
    val descriptor: String,
    val deviceName: String,
    val matchLabel: String?,
    val confidence: DetectionConfidence?
)

sealed interface MappingWizardScreen {
    data class Detected(
        val deviceName: String,
        val matchLabel: String?,
        val confidence: DetectionConfidence?
    ) : MappingWizardScreen

    data class Testing(
        val profileName: String,
        val rows: List<MappingTestRow>,
        val lastKeyCode: Int?,
        val lastPressedLabel: String? = null,
        val lastDpadDir: String? = null,
        val lastStickDir: String? = null,
        val historyKeys: Set<Int> = emptySet(),
        val historyDirs: Set<String> = emptySet(),
        val historyStickDirs: Set<String> = emptySet()
    ) : MappingWizardScreen

    data class Capturing(
        val targetLabel: String,
        val done: Int,
        val total: Int,
        val flash: String?,
        val isStickTarget: Boolean
    ) : MappingWizardScreen

    data class Done(val assigned: Int, val skipped: Int) : MappingWizardScreen
}

data class MappingWizardActions(
    val onConfirmDetected: () -> Unit = {},
    val onRemap: () -> Unit = {},
    val onSaveTest: () -> Unit = {},
    val onWizardClose: () -> Unit = {},
    val onResetDefault: () -> Unit = {},
    val onCaptureSkip: () -> Unit = {},
    val onCaptureBack: () -> Unit = {},
    val onCaptureCancel: () -> Unit = {},
    val onCaptureConfirmStick: () -> Unit = {},
    val onCaptureTick: () -> Unit = {},
    val onSaveCapture: () -> Unit = {},
    val onDiscardCapture: () -> Unit = {}
)

private val CardColor = Color(0xFF161D2B)
private val Accent = Color(0xFF00E5FF)
private val LastBlue = Color(0xFF3D8BFF)
private val HistoryGreen = Color(0xFF35C759)
private val Muted = Color(0xFF9FB0C6)
private val Title = Color(0xFFEAF2FF)

@Composable
fun MappingPromptBanner(
    deviceName: String,
    matchLabel: String?,
    onSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "🎮 New controller: $deviceName",
                color = Title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (matchLabel != null) {
                Text(text = "Looks like $matchLabel — verify the layout", color = Muted, fontSize = 12.sp)
            } else {
                Text(text = "Layout unknown — manual setup recommended", color = Muted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Set up", color = Color(0xFF0B111B))
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = Muted)
                }
            }
        }
    }
}

@Composable
fun MappingWizard(
    screen: MappingWizardScreen,
    actions: MappingWizardActions
) {
    // The wizard is touch-driven; controller input goes to capture, never to
    // focus traversal. Clear focus on every screen change so no button carries
    // a highlight ring that stray keys could activate.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(screen) {
        focusManager.clearFocus(force = true)
    }
    // In-window overlay (NOT AlertDialog): Compose dialogs open a separate
    // Window whose key events bypass Activity.dispatchKeyEvent entirely, which
    // silently broke capture. Rendered in MainScreen's content Box, every
    // controller key/motion event reaches the activity's capture routing.
    // No scrim-tap dismiss — explicit buttons only, so pads can't exit by stray input.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC06080E)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 740.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = if (screen is MappingWizardScreen.Capturing) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (screen is MappingWizardScreen.Capturing) 6.dp else 10.dp)
            ) {
                if (screen is MappingWizardScreen.Testing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Test layout",
                            color = Title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = actions.onRemap,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Remap", color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            Button(
                                onClick = actions.onSaveTest,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Looks right", color = Color(0xFF0B111B), fontSize = 12.sp, maxLines = 1)
                            }
                            TextButton(
                                onClick = actions.onWizardClose,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Close", color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                } else if (screen !is MappingWizardScreen.Capturing) {
                    Text(
                        when (screen) {
                            is MappingWizardScreen.Detected -> "Controller setup"
                            is MappingWizardScreen.Done -> "Mapping done"
                        },
                        color = Title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                when (screen) {
                    is MappingWizardScreen.Detected -> DetectedBody(screen)
                    is MappingWizardScreen.Testing -> TestingBody(screen)
                    is MappingWizardScreen.Capturing -> CapturingBody(screen, actions)
                    is MappingWizardScreen.Done -> DoneBody(screen)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (screen) {
                        is MappingWizardScreen.Detected -> {
                            TextButton(onClick = actions.onResetDefault) { Text("Reset", color = Muted) }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = actions.onRemap) { Text("Remap", color = Muted) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = actions.onConfirmDetected,
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Test & Confirm", color = Color(0xFF0B111B))
                            }
                        }
                        is MappingWizardScreen.Testing -> {
                            // Bottom actions removed — top bar (Remap / Looks right / Close) is sole affordance
                        }
                        is MappingWizardScreen.Capturing -> {
                            TextButton(onClick = actions.onCaptureBack) { Text("Back", color = Muted) }
                            TextButton(onClick = actions.onCaptureSkip) { Text("Skip", color = Muted) }
                            TextButton(onClick = actions.onCaptureCancel) { Text("Cancel", color = Muted) }
                            if (screen.isStickTarget) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = actions.onCaptureConfirmStick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Text("Done wiggling", color = Color(0xFF0B111B))
                                }
                            }
                        }
                        is MappingWizardScreen.Done -> {
                            TextButton(onClick = actions.onDiscardCapture) { Text("Discard", color = Muted) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = actions.onSaveCapture,
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Save", color = Color(0xFF0B111B))
                            }
                        }
                    }
                    if (screen is MappingWizardScreen.Detected || screen is MappingWizardScreen.Done) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = actions.onWizardClose) { Text("Close", color = Muted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectedBody(screen: MappingWizardScreen.Detected) {
    Column {
        Text("Device: ${screen.deviceName}", color = Title, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        when (screen.confidence) {
            DetectionConfidence.EXACT ->
                Text("Recognized as ${screen.matchLabel}.", color = Accent, fontSize = 14.sp)
            DetectionConfidence.HEURISTIC ->
                Text(
                    "Looks like ${screen.matchLabel ?: "a standard pad"} — please verify on the test screen.",
                    color = Muted,
                    fontSize = 14.sp
                )
            null ->
                Text(
                    "No layout on file for this controller. Test the default, or remap each button manually.",
                    color = Muted,
                    fontSize = 14.sp
                )
        }
    }
}

@Composable
private fun TestingBody(screen: MappingWizardScreen.Testing) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Press buttons — each should light up. (${screen.profileName})",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
            // Reserve fixed 12sp line height on right so diagram never shifts when lastPressed appears.
            Box(modifier = Modifier.padding(start = 12.dp)) {
                if (screen.lastPressedLabel != null) {
                    Text(
                        "Last pressed: ${screen.lastPressedLabel}",
                        color = LastBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                } else {
                    // Invisible placeholder keeps row height stable (no shift on first press).
                    Text(" ", color = Color.Transparent, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        ControllerLineDiagram(
            getHighlight = { label -> highlightForTesting(label, screen) },
            targetLabel = null
        )
        Text("● Last  ● Tested  ○ Untouched", color = Muted, fontSize = 11.sp)
    }
}

private fun normalizeControl(name: String): String = when (name.trim()) {
    "A" -> "A"
    "B" -> "B"
    "X" -> "X"
    "Y" -> "Y"
    "L" -> "L"
    "R" -> "R"
    "ZL" -> "ZL"
    "ZR" -> "ZR"
    "Minus", "Minus (-)", "MINUS", "−" -> "MINUS"
    "Plus", "Plus (+)", "PLUS", "+" -> "PLUS"
    "Home", "HOME", "⌂" -> "HOME"
    "D-Pad Up", "Dpad Up", "DPAD_UP" -> "DPAD_UP"
    "D-Pad Down", "Dpad Down", "DPAD_DOWN" -> "DPAD_DOWN"
    "D-Pad Left", "Dpad Left", "DPAD_LEFT" -> "DPAD_LEFT"
    "D-Pad Right", "Dpad Right", "DPAD_RIGHT" -> "DPAD_RIGHT"
    "Stick L Up", "STICK_L_UP" -> "STICK_L_UP"
    "Stick L Down", "STICK_L_DOWN" -> "STICK_L_DOWN"
    "Stick L Left", "STICK_L_LEFT" -> "STICK_L_LEFT"
    "Stick L Right", "STICK_L_RIGHT" -> "STICK_L_RIGHT"
    "Left Stick Press", "Stick L Press", "STICK_L_PRESS", "L3" -> "STICK_L_PRESS"
    "Stick R Up", "STICK_R_UP" -> "STICK_R_UP"
    "Stick R Down", "STICK_R_DOWN" -> "STICK_R_DOWN"
    "Stick R Left", "STICK_R_LEFT" -> "STICK_R_LEFT"
    "Stick R Right", "STICK_R_RIGHT" -> "STICK_R_RIGHT"
    "Right Stick Press", "Stick R Press", "STICK_R_PRESS", "R3" -> "STICK_R_PRESS"
    else -> name.uppercase()
}

private fun matchesControl(labelOrKey: String, targetLabel: String?): Boolean {
    if (targetLabel == null) return false
    val targetNorm = normalizeControl(targetLabel)
    val keyNorm = normalizeControl(labelOrKey)
    if (targetNorm == keyNorm) return true
    if (targetLabel == "Stick L Move" && keyNorm.startsWith("STICK_L_")) return true
    if (targetLabel == "Stick R Move" && keyNorm.startsWith("STICK_R_")) return true
    return false
}

private fun highlightForTesting(label: String, screen: MappingWizardScreen.Testing): Color {
    val norm = normalizeControl(label)
    val isLast = when (norm) {
        "STICK_L_UP" -> screen.lastStickDir == "L_UP"
        "STICK_L_DOWN" -> screen.lastStickDir == "L_DOWN"
        "STICK_L_LEFT" -> screen.lastStickDir == "L_LEFT"
        "STICK_L_RIGHT" -> screen.lastStickDir == "L_RIGHT"
        "STICK_R_UP" -> screen.lastStickDir == "R_UP"
        "STICK_R_DOWN" -> screen.lastStickDir == "R_DOWN"
        "STICK_R_LEFT" -> screen.lastStickDir == "R_LEFT"
        "STICK_R_RIGHT" -> screen.lastStickDir == "R_RIGHT"
        "DPAD_UP" -> screen.lastDpadDir == "UP"
        "DPAD_DOWN" -> screen.lastDpadDir == "DOWN"
        "DPAD_LEFT" -> screen.lastDpadDir == "LEFT"
        "DPAD_RIGHT" -> screen.lastDpadDir == "RIGHT"
        else -> {
            screen.rows.find { normalizeControl(it.label) == norm }?.let { row ->
                row.keyCode != 0 && row.keyCode == screen.lastKeyCode
            } ?: false
        }
    }
    val inHistory = when (norm) {
        "STICK_L_UP" -> screen.historyStickDirs.contains("L_UP")
        "STICK_L_DOWN" -> screen.historyStickDirs.contains("L_DOWN")
        "STICK_L_LEFT" -> screen.historyStickDirs.contains("L_LEFT")
        "STICK_L_RIGHT" -> screen.historyStickDirs.contains("L_RIGHT")
        "STICK_R_UP" -> screen.historyStickDirs.contains("R_UP")
        "STICK_R_DOWN" -> screen.historyStickDirs.contains("R_DOWN")
        "STICK_R_LEFT" -> screen.historyStickDirs.contains("R_LEFT")
        "STICK_R_RIGHT" -> screen.historyStickDirs.contains("R_RIGHT")
        "DPAD_UP" -> screen.historyDirs.contains("UP")
        "DPAD_DOWN" -> screen.historyDirs.contains("DOWN")
        "DPAD_LEFT" -> screen.historyDirs.contains("LEFT")
        "DPAD_RIGHT" -> screen.historyDirs.contains("RIGHT")
        else -> {
            screen.rows.find { normalizeControl(it.label) == norm }?.let { row ->
                row.keyCode != 0 && screen.historyKeys.contains(row.keyCode)
            } ?: false
        }
    }
    return when {
        isLast -> LastBlue
        inHistory -> HistoryGreen
        else -> Color(0xFF1E283A)
    }
}

@Composable
private fun ControllerLineDiagram(
    getHighlight: (String) -> Color,
    targetLabel: String?
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val wDp = 400.dp
        val hDp = 225.dp
        val wPx = with(density) { wDp.toPx() }
        val hPx = with(density) { hDp.toPx() }

        Box(
            modifier = Modifier.size(wDp, hDp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                val bodyPath = Path().apply {
                    // Top center bridge
                    moveTo(canvasW * 0.35f, canvasH * 0.14f)
                    // Left trigger hump
                    cubicTo(canvasW * 0.28f, canvasH * 0.14f, canvasW * 0.25f, canvasH * 0.04f, canvasW * 0.20f, canvasH * 0.04f)
                    cubicTo(canvasW * 0.13f, canvasH * 0.04f, canvasW * 0.10f, canvasH * 0.12f, canvasW * 0.09f, canvasH * 0.22f)
                    // Left grip outer edge
                    cubicTo(canvasW * 0.06f, canvasH * 0.38f, canvasW * 0.02f, canvasH * 0.62f, canvasW * 0.05f, canvasH * 0.84f)
                    // Left grip tip
                    cubicTo(canvasW * 0.07f, canvasH * 0.96f, canvasW * 0.15f, canvasH * 1.00f, canvasW * 0.21f, canvasH * 0.92f)
                    // Left inner grip rising
                    cubicTo(canvasW * 0.26f, canvasH * 0.82f, canvasW * 0.28f, canvasH * 0.74f, canvasW * 0.34f, canvasH * 0.72f)
                    // Bottom center arch
                    cubicTo(canvasW * 0.42f, canvasH * 0.78f, canvasW * 0.58f, canvasH * 0.78f, canvasW * 0.66f, canvasH * 0.72f)
                    // Right inner grip descending
                    cubicTo(canvasW * 0.72f, canvasH * 0.74f, canvasW * 0.74f, canvasH * 0.82f, canvasW * 0.79f, canvasH * 0.92f)
                    // Right grip tip
                    cubicTo(canvasW * 0.85f, canvasH * 1.00f, canvasW * 0.93f, canvasH * 0.96f, canvasW * 0.95f, canvasH * 0.84f)
                    // Right grip outer edge
                    cubicTo(canvasW * 0.98f, canvasH * 0.62f, canvasW * 0.94f, canvasH * 0.38f, canvasW * 0.91f, canvasH * 0.22f)
                    cubicTo(canvasW * 0.90f, canvasH * 0.12f, canvasW * 0.87f, canvasH * 0.04f, canvasW * 0.80f, canvasH * 0.04f)
                    cubicTo(canvasW * 0.75f, canvasH * 0.04f, canvasW * 0.72f, canvasH * 0.14f, canvasW * 0.65f, canvasH * 0.14f)
                    close()
                }

                // Chassis background
                drawPath(bodyPath, color = Color(0xFF0F1522))
                // Controller silhouette outline line art
                drawPath(bodyPath, color = Color(0xFF2E4059), style = Stroke(width = 2.dp.toPx()))

                // Grip accent seam lines
                val leftGripSeam = Path().apply {
                    moveTo(canvasW * 0.24f, canvasH * 0.24f)
                    cubicTo(canvasW * 0.20f, canvasH * 0.50f, canvasW * 0.18f, canvasH * 0.72f, canvasW * 0.15f, canvasH * 0.88f)
                }
                drawPath(leftGripSeam, color = Color(0xFF1C2738), style = Stroke(width = 1.5.dp.toPx()))

                val rightGripSeam = Path().apply {
                    moveTo(canvasW * 0.76f, canvasH * 0.24f)
                    cubicTo(canvasW * 0.80f, canvasH * 0.50f, canvasW * 0.82f, canvasH * 0.72f, canvasW * 0.85f, canvasH * 0.88f)
                }
                drawPath(rightGripSeam, color = Color(0xFF1C2738), style = Stroke(width = 1.5.dp.toPx()))

                // Wells for stick, dpad, face button clusters
                val wellColor = Color(0xFF131A28)
                val wellBorder = Color(0xFF223046)
                val wellBorderWidth = 1.dp.toPx()

                // Left stick well — directly under L (shifted down to 0.33 to clear bumper, radius 18)
                drawCircle(color = wellColor, radius = 18.dp.toPx(), center = Offset(canvasW * 0.22f, canvasH * 0.33f))
                drawCircle(color = wellBorder, radius = 18.dp.toPx(), center = Offset(canvasW * 0.22f, canvasH * 0.33f), style = Stroke(wellBorderWidth))

                // D-Pad well — slightly up (0.65 → 0.60), radius 18
                drawCircle(color = wellColor, radius = 18.dp.toPx(), center = Offset(canvasW * 0.33f, canvasH * 0.60f))
                drawCircle(color = wellBorder, radius = 18.dp.toPx(), center = Offset(canvasW * 0.33f, canvasH * 0.60f), style = Stroke(wellBorderWidth))

                // Right stick well — directly under R (radius 18)
                drawCircle(color = wellColor, radius = 18.dp.toPx(), center = Offset(canvasW * 0.78f, canvasH * 0.33f))
                drawCircle(color = wellBorder, radius = 18.dp.toPx(), center = Offset(canvasW * 0.78f, canvasH * 0.33f), style = Stroke(wellBorderWidth))

                // Face buttons well — slightly up (radius 18)
                drawCircle(color = wellColor, radius = 18.dp.toPx(), center = Offset(canvasW * 0.67f, canvasH * 0.60f))
                drawCircle(color = wellBorder, radius = 18.dp.toPx(), center = Offset(canvasW * 0.67f, canvasH * 0.60f), style = Stroke(wellBorderWidth))
            }

            @Composable
            fun Badge(
                controlKey: String,
                symbol: String,
                rx: Float,
                ry: Float,
                w: androidx.compose.ui.unit.Dp,
                h: androidx.compose.ui.unit.Dp,
                shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp),
                fontSize: androidx.compose.ui.unit.TextUnit = 9.sp
            ) {
                val isTarget = matchesControl(controlKey, targetLabel)
                val color = getHighlight(controlKey)
                val bgColor = if (isTarget) Accent else color
                val textColor = if (isTarget || color == Accent || color == Title) Color(0xFF0B111B) else Color.White
                val borderColor = if (isTarget) Color(0xFF00E5FF) else Color(0xFF2C3E56)

                Box(
                    modifier = Modifier
                        .offset {
                            val centerX = rx * wPx
                            val centerY = ry * hPx
                            val bw = w.toPx()
                            val bh = h.toPx()
                            IntOffset((centerX - bw / 2f).roundToInt(), (centerY - bh / 2f).roundToInt())
                        }
                        .size(w, h)
                        .background(bgColor, shape)
                        .border(if (isTarget) 1.5.dp else 0.5.dp, borderColor, shape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = symbol,
                        color = textColor,
                        fontSize = fontSize,
                        fontWeight = if (isTarget) FontWeight.ExtraBold else FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                            lineHeight = fontSize
                        )
                    )
                }
            }

            // 1. Shoulders & Triggers — ZL/L/ZR/R +50% width, slightly thinner (20→14dp)
            Badge("ZL", "ZL", 0.20f, 0.02f, 46.dp, 14.dp, RoundedCornerShape(5.dp), 11.sp)
            Badge("L", "L", 0.20f, 0.13f, 44.dp, 14.dp, RoundedCornerShape(5.dp), 12.sp)
            Badge("ZR", "ZR", 0.80f, 0.02f, 46.dp, 14.dp, RoundedCornerShape(5.dp), 11.sp)
            Badge("R", "R", 0.80f, 0.13f, 44.dp, 14.dp, RoundedCornerShape(5.dp), 12.sp)

            // 2. Center Buttons (Minus, Home, Plus) — +10%
            Badge("MINUS", "−", 0.43f, 0.28f, 22.dp, 19.dp, RoundedCornerShape(7.dp), 12.sp)
            Badge("HOME", "⌂", 0.50f, 0.40f, 22.dp, 22.dp, CircleShape, 13.sp)
            Badge("PLUS", "+", 0.57f, 0.28f, 22.dp, 19.dp, RoundedCornerShape(7.dp), 12.sp)

            // 3. Left Stick — directly under L (22,33) — +10%
            Badge("STICK_L_PRESS", "L3", 0.22f, 0.33f, 18.dp, 18.dp, CircleShape, 10.sp)
            Badge("STICK_L_UP", "▲", 0.22f, 0.24f, 13.dp, 11.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("STICK_L_DOWN", "▼", 0.22f, 0.42f, 13.dp, 11.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("STICK_L_LEFT", "◀", 0.17f, 0.33f, 11.dp, 13.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("STICK_L_RIGHT", "▶", 0.27f, 0.33f, 11.dp, 13.dp, RoundedCornerShape(3.dp), 8.sp)

            // 4. D-Pad — tightened left/right gap +10%
            Badge("DPAD_UP", "▲", 0.33f, 0.51f, 13.dp, 11.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("DPAD_DOWN", "▼", 0.33f, 0.69f, 13.dp, 11.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("DPAD_LEFT", "◀", 0.28f, 0.60f, 11.dp, 13.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("DPAD_RIGHT", "▶", 0.38f, 0.60f, 11.dp, 13.dp, RoundedCornerShape(3.dp), 8.sp)

            // 5. Right Stick — directly under R (78,33) — +10%
            Badge("STICK_R_PRESS", "R3", 0.78f, 0.33f, 18.dp, 18.dp, CircleShape, 10.sp)
            Badge("STICK_R_UP", "▲", 0.78f, 0.24f, 13.dp, 11.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("STICK_R_DOWN", "▼", 0.78f, 0.42f, 13.dp, 11.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("STICK_R_LEFT", "◀", 0.73f, 0.33f, 11.dp, 13.dp, RoundedCornerShape(3.dp), 8.sp)
            Badge("STICK_R_RIGHT", "▶", 0.83f, 0.33f, 11.dp, 13.dp, RoundedCornerShape(3.dp), 8.sp)

            // 6. Face Buttons ABXY — Y/A 40dp, X/B matched to same 40dp +10% scale
            Badge("X", "X", 0.67f, 0.51f, 22.dp, 22.dp, CircleShape, 12.sp)
            Badge("Y", "Y", 0.62f, 0.60f, 20.dp, 20.dp, CircleShape, 11.sp)
            Badge("A", "A", 0.72f, 0.60f, 20.dp, 20.dp, CircleShape, 11.sp)
            Badge("B", "B", 0.67f, 0.69f, 22.dp, 22.dp, CircleShape, 12.sp)
        }
    }
}

@Composable
private fun CapturingBody(screen: MappingWizardScreen.Capturing, actions: MappingWizardActions) {
    // Timeout ticker: engine auto-skips idle targets.
    LaunchedEffect(screen.targetLabel, screen.done) {
        while (true) {
            delay(500)
            actions.onCaptureTick()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // TOP ROW: "Press for: [Target]" + duplicate set of Back / Skip / Cancel / Done wiggling
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (screen.isStickTarget) "Move stick for:" else "Press for:",
                    color = Muted,
                    fontSize = 13.sp
                )
                Text(
                    screen.targetLabel,
                    color = Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    onClick = actions.onCaptureBack,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Back", color = Muted, fontSize = 12.sp)
                }
                TextButton(
                    onClick = actions.onCaptureSkip,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Skip", color = Muted, fontSize = 12.sp)
                }
                TextButton(
                    onClick = actions.onCaptureCancel,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Cancel", color = Muted, fontSize = 12.sp)
                }
            }
        }

        // COMPACT CONTROLLER LINE DIAGRAM (135dp height)
        ControllerLineDiagram(
            getHighlight = { label ->
                if (matchesControl(label, screen.targetLabel)) Accent
                else Color(0xFF1E283A)
            },
            targetLabel = screen.targetLabel
        )

        // PROGRESS ROW
        LinearProgressIndicator(
            progress = { screen.done.toFloat() / screen.total.coerceAtLeast(1) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = Accent,
            trackColor = Color(0xFF222B3D)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${screen.done}/${screen.total}", color = Muted, fontSize = 11.sp)
            screen.flash?.let {
                Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DoneBody(screen: MappingWizardScreen.Done) {
    Column {
        Text(
            "Mapped ${screen.assigned} controls${if (screen.skipped > 0) " (${screen.skipped} skipped)" else ""}.",
            color = Title,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("Save to use this layout every time this controller connects.", color = Muted, fontSize = 13.sp)
    }
}
