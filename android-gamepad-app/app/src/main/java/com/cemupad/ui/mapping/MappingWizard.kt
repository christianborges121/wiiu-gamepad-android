package com.cemupad.ui.mapping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cemupad.input.DetectionConfidence
import kotlinx.coroutines.delay

/**
 * First-connect input mapping wizard: Detected → Test → Capture → Done.
 * All state lives in MainActivity; this file is pure presentation in the
 * dark drawer card style.
 */

data class MappingTestRow(val label: String, val keyCode: Int, val dpadDir: String? = null)

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
        val historyKeys: Set<Int> = emptySet(),
        val historyDirs: Set<String> = emptySet()
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
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            fontSize = 18.sp,
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
                } else {
                    Text(
                        when (screen) {
                            is MappingWizardScreen.Detected -> "Controller setup"
                            is MappingWizardScreen.Testing -> "Test layout"
                            is MappingWizardScreen.Capturing -> "Map buttons"
                            is MappingWizardScreen.Done -> "Mapping done"
                        },
                        color = Title,
                        fontSize = 18.sp,
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
                            TextButton(onClick = actions.onRemap) { Text("Remap", color = Muted) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = actions.onSaveTest,
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text("Looks right", color = Color(0xFF0B111B))
                            }
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
                    if (screen !is MappingWizardScreen.Capturing) {
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
    Column {
        Text("Press buttons — each should light up. (${screen.profileName})", color = Muted, fontSize = 13.sp)
        screen.lastPressedLabel?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Last pressed: $it", color = LastBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // No inner scroll: the card itself scrolls, so the action buttons
        // below can never be stranded off-screen on short landscape displays.
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (row in screen.rows) {
                val isLast = (row.keyCode != 0 && row.keyCode == screen.lastKeyCode) ||
                    (row.dpadDir != null && row.dpadDir == screen.lastDpadDir)
                val inHistory = (row.keyCode != 0 && screen.historyKeys.contains(row.keyCode)) ||
                    (row.dpadDir != null && screen.historyDirs.contains(row.dpadDir))
                val rowColor = when {
                    isLast -> LastBlue
                    inHistory -> HistoryGreen
                    else -> Title
                }
                Text(
                    (if (isLast || inHistory) "● " else "○ ") + row.label +
                        if (row.keyCode == 0 && row.dpadDir != null) " (hat)" else "",
                    color = rowColor,
                    fontWeight = if (isLast || inHistory) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            }
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
    Column {
        Text(
            if (screen.isStickTarget) "Circle the stick, then Done wiggling:" else "Press for:",
            color = Muted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(screen.targetLabel, color = Title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { screen.done.toFloat() / screen.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
            color = Accent,
            trackColor = Color(0xFF222B3D)
        )
        Text("${screen.done}/${screen.total}", color = Muted, fontSize = 12.sp)
        screen.flash?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp)
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
