package com.cemupad.ui.config

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cemupad.config.DisplaySettings

@Composable
fun ConfigMenuView(
    state: ConfigMenuState,
    settings: DisplaySettings,
    onSettingsChanged: (DisplaySettings) -> Unit,
    activeControllerName: String? = null,
    phoneIp: String = "127.0.0.1",
    dsuPort: Int = 26760,
    isCalibrated: Boolean = false,
    onAction: (ConfigAction) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items = state.getItems(
        screen = state.currentScreen,
        settings = settings,
        activeControllerName = activeControllerName,
        phoneIp = phoneIp,
        dsuPort = dsuPort,
        isCalibrated = isCalibrated
    )

    val listState = rememberLazyListState()

    LaunchedEffect(state.focusedIndex) {
        if (items.isNotEmpty() && state.focusedIndex in items.indices) {
            listState.animateScrollToItem(state.focusedIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(380.dp),
        color = Color(0xFF0D131E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            // --- Header ---
            ConfigMenuHeader(
                currentScreen = state.currentScreen,
                isDoneFocused = state.isHeaderDoneFocused && state.currentScreen == ConfigScreen.ROOT,
                onBack = { state.onBackB() },
                onClose = { state.close() }
            )

            HorizontalDivider(
                color = Color(0xFF1E2838),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // --- Submenu / List Area with Smooth Animated Transition ---
            AnimatedContent(
                targetState = state.currentScreen,
                transitionSpec = {
                    if (targetState != ConfigScreen.ROOT) {
                        (slideInHorizontally { width -> width / 3 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width / 3 } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width / 3 } + fadeOut()
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "ConfigScreenTransition"
            ) { screen ->
                val currentScreenItems = state.getItems(
                    screen = screen,
                    settings = settings,
                    activeControllerName = activeControllerName,
                    phoneIp = phoneIp,
                    dsuPort = dsuPort,
                    isCalibrated = isCalibrated
                )

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(currentScreenItems) { index, item ->
                        ConfigItemCard(
                            item = item,
                            isFocused = index == state.focusedIndex && !state.isHeaderDoneFocused,
                            onClick = {
                                state.isHeaderDoneFocused = false
                                state.focusedIndex = index
                                state.onSelectA(currentScreenItems, settings, onSettingsChanged, onAction)
                            },
                            onLeft = {
                                state.isHeaderDoneFocused = false
                                state.focusedIndex = index
                                state.onLeft(currentScreenItems, settings, onSettingsChanged)
                            },
                            onRight = {
                                state.isHeaderDoneFocused = false
                                state.focusedIndex = index
                                state.onRight(currentScreenItems, settings, onSettingsChanged)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E2838),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // --- Controller Footer Hints ---
            ConfigControllerFooter(
                isRoot = state.currentScreen == ConfigScreen.ROOT
            )
        }
    }
}

@Composable
private fun ConfigMenuHeader(
    currentScreen: ConfigScreen,
    isDoneFocused: Boolean = false,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentScreen == ConfigScreen.ROOT) {
            Column {
                Text(
                    text = "CONFIGURATION",
                    color = Color(0xFF00E5FF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Wii U GamePad & Stream Setup",
                    color = Color(0xFF7A8B9E),
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDoneFocused) Color(0xFF1C273B) else Color(0xFF182232)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                border = BorderStroke(if (isDoneFocused) 2.dp else 1.dp, if (isDoneFocused) Color(0xFF00E5FF) else Color(0xFF26354D))
            ) {
                Text("Done", color = if (isDoneFocused) Color.White else Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBack() }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF1A2436), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "<",
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "BACK",
                        color = Color(0xFF9FB0C6),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = currentScreen.title.uppercase(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color(0xFF7A8B9E), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConfigItemCard(
    item: ConfigMenuItem,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val containerColor = when {
        isFocused -> Color(0xFF1C273B)
        item.isEnabled -> Color(0xFF131A26)
        else -> Color(0xFF0E131C)
    }

    val borderColor = when {
        isFocused -> Color(0xFF00E5FF)
        else -> Color(0xFF202A3B)
    }

    val borderWidth = if (isFocused) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isEnabled) { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title and description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = if (isFocused) Color.White else Color(0xFFEAF2FF),
                    fontSize = 14.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        color = if (isFocused) Color(0xFFB0C4DE) else Color(0xFF7A8B9E),
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }

                // Slider progress bar
                if (item.type == ConfigItemType.SLIDER && item.isEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { item.sliderProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF222B3D)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.valueText,
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right interactive control
            when (item.type) {
                ConfigItemType.SUBMENU_LINK -> {
                    Text(
                        "▶",
                        color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF7A8B9E),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                ConfigItemType.MULTI_CHOICE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFF17202F), RoundedCornerShape(6.dp))
                            .border(1.dp, if (isFocused) Color(0xFF00E5FF) else Color(0xFF2A364A), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onLeft() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("◀", color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF9FB0C6), fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.valueText,
                            color = if (isFocused) Color(0xFF00E5FF) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onRight() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF9FB0C6), fontSize = 11.sp)
                        }
                    }
                }

                ConfigItemType.SWITCH -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFocused) {
                            Text(
                                "A",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .background(Color(0xFF0A2B3D), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Switch(
                            checked = item.valueText == "On",
                            onCheckedChange = { onClick() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00B4D8),
                                uncheckedThumbColor = Color(0xFF7A8B9E),
                                uncheckedTrackColor = Color(0xFF222B3D),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }
                }

                ConfigItemType.SLIDER -> {
                    if (isFocused) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF17202F), RoundedCornerShape(4.dp))
                                    .clickable { onLeft() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("◀", color = Color(0xFF00E5FF), fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF17202F), RoundedCornerShape(4.dp))
                                    .clickable { onRight() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▶", color = Color(0xFF00E5FF), fontSize = 11.sp)
                            }
                        }
                    }
                }

                ConfigItemType.ACTION -> {
                    if (item.valueText.isNotBlank()) {
                        OutlinedButton(
                            onClick = onClick,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isFocused) Color(0xFF00E5FF) else Color(0xFFEAF2FF)
                            ),
                            border = BorderStroke(1.dp, if (isFocused) Color(0xFF00E5FF) else Color(0xFF2A364A)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(item.valueText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ConfigItemType.INFO -> {
                    Text(
                        text = item.valueText,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color(0xFF151E2B), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigControllerFooter(isRoot: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgeHint("▲/▼")
            Spacer(modifier = Modifier.width(4.dp))
            Text("Navigate", color = Color(0xFF7A8B9E), fontSize = 11.sp)
        }

        if (!isRoot) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeHint("◀/▶")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Adjust", color = Color(0xFF7A8B9E), fontSize = 11.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgeHint("A")
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isRoot) "Select" else "Toggle", color = Color(0xFF7A8B9E), fontSize = 11.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgeHint("B")
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isRoot) "Close" else "Back", color = Color(0xFF7A8B9E), fontSize = 11.sp)
        }
    }
}

@Composable
private fun BadgeHint(label: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1A2436), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2C394F), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF00E5FF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}
