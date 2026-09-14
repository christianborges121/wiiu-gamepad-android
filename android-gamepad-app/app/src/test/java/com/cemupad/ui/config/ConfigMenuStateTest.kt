package com.cemupad.ui.config

import android.view.KeyEvent
import com.cemupad.config.DisplayFitMode
import com.cemupad.config.DisplayResolutionPreset
import com.cemupad.config.DisplaySettings
import com.cemupad.config.FramePacingMode
import com.cemupad.config.VideoCodecPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfigMenuStateTest {

    private lateinit var state: ConfigMenuState
    private var currentSettings = DisplaySettings()
    private var lastAction: ConfigAction? = null

    @Before
    fun setup() {
        state = ConfigMenuState()
        currentSettings = DisplaySettings()
        lastAction = null
    }

    @Test
    fun testOpenAndClose() {
        assertFalse(state.isOpen)
        assertEquals(ConfigScreen.ROOT, state.currentScreen)

        state.open()
        assertTrue(state.isOpen)
        assertEquals(ConfigScreen.ROOT, state.currentScreen)
        assertEquals(0, state.focusedIndex)

        state.close()
        assertFalse(state.isOpen)
        assertEquals(ConfigScreen.ROOT, state.currentScreen)
    }

    @Test
    fun testNavigationUpDownWrapAround() {
        state.open()
        val items = state.getItems(state.currentScreen, currentSettings)
        val count = items.size

        assertEquals(0, state.focusedIndex)

        // Up wraps to last item
        state.onUp(items)
        assertEquals(count - 1, state.focusedIndex)

        // Down wraps to first item
        state.onDown(items)
        assertEquals(0, state.focusedIndex)

        // Down moves to index 1
        state.onDown(items)
        assertEquals(1, state.focusedIndex)

        // Up moves back to index 0
        state.onUp(items)
        assertEquals(0, state.focusedIndex)
    }

    @Test
    fun testSubmenuNavigationAndBack() {
        state.open()
        val rootItems = state.getItems(state.currentScreen, currentSettings)

        // Focus on "Display" (index 0) and press A
        state.focusedIndex = 0
        state.onSelectA(rootItems, currentSettings, { currentSettings = it }, { lastAction = it })

        assertEquals(ConfigScreen.DISPLAY, state.currentScreen)
        assertEquals(0, state.focusedIndex)

        // Press B to return to root
        state.onBackB()
        assertEquals(ConfigScreen.ROOT, state.currentScreen)
        assertEquals(0, state.focusedIndex) // returns to Display row

        // Focus on "Input" (index 1) and press A (Display first, Input second per new order)
        val updatedRootItems = state.getItems(state.currentScreen, currentSettings)
        state.focusedIndex = 1
        state.onSelectA(updatedRootItems, currentSettings, { currentSettings = it }, { lastAction = it })

        assertEquals(ConfigScreen.INPUT_HAPTICS, state.currentScreen)
        assertEquals(0, state.focusedIndex)

        // Press B to return to root
        state.onBackB()
        assertEquals(ConfigScreen.ROOT, state.currentScreen)
        assertEquals(1, state.focusedIndex) // returns to Input row

        // Press B on Root closes menu
        state.onBackB()
        assertFalse(state.isOpen)
    }

    @Test
    fun testSwitchToggleOnSelectA() {
        state.open(ConfigScreen.AUDIO)
        val items = state.getItems(state.currentScreen, currentSettings)

        // Audio enabled is index 0
        state.focusedIndex = 0
        assertTrue(currentSettings.audioEnabled)

        state.onSelectA(items, currentSettings, { currentSettings = it }, { lastAction = it })
        assertFalse(currentSettings.audioEnabled)

        state.onSelectA(items, currentSettings, { currentSettings = it }, { lastAction = it })
        assertTrue(currentSettings.audioEnabled)
    }

    @Test
    fun testMultiChoiceCycleLeftRight() {
        state.open(ConfigScreen.DISPLAY)
        val items = state.getItems(state.currentScreen, currentSettings)

        // Focus on Resolution (index 0)
        state.focusedIndex = 0
        assertEquals(DisplayResolutionPreset.NATIVE_854x480, currentSettings.resolutionPreset)

        // Right cycles to next preset
        state.onRight(items, currentSettings) { currentSettings = it }
        assertEquals(DisplayResolutionPreset.HD_1280x720, currentSettings.resolutionPreset)

        // Left cycles back
        state.onLeft(items, currentSettings) { currentSettings = it }
        assertEquals(DisplayResolutionPreset.NATIVE_854x480, currentSettings.resolutionPreset)

        // Left wraps to last preset
        state.onLeft(items, currentSettings) { currentSettings = it }
        assertEquals(DisplayResolutionPreset.DEVICE_AUTO, currentSettings.resolutionPreset)

        // Focus on Bitrate (index 2) — default now 10 Mbps
        state.focusedIndex = 2
        assertEquals(10, currentSettings.videoBitrateMbps)
        state.onRight(items, currentSettings) { currentSettings = it }
        assertEquals(12, currentSettings.videoBitrateMbps)
    }

    @Test
    fun testSliderAdjustment() {
        state.open(ConfigScreen.AUDIO)
        val items = state.getItems(state.currentScreen, currentSettings)

        // Focus on Volume (index 1)
        state.focusedIndex = 1
        assertEquals(1.0f, currentSettings.audioVolume, 0.01f)

        state.onLeft(items, currentSettings) { currentSettings = it }
        assertEquals(0.95f, currentSettings.audioVolume, 0.01f)

        state.onRight(items, currentSettings) { currentSettings = it }
        assertEquals(1.0f, currentSettings.audioVolume, 0.01f)
    }

    @Test
    fun testActionExecution() {
        state.open(ConfigScreen.INPUT_HAPTICS)
        var items = state.getItems(state.currentScreen, currentSettings)

        // Focus on "Physical Controller" now at index 0 per new order
        state.focusedIndex = items.indexOfFirst { it.id == "input_remap" }
        assertTrue(state.focusedIndex >= 0)
        assertEquals("input_remap", items[state.focusedIndex].id)
        state.onSelectA(items, currentSettings, { currentSettings = it }, { lastAction = it })
        assertEquals(ConfigAction.MAP_CONTROLLER, lastAction)

        // Focus on "Motion & Gyroscope" — index shifts when vibration/virtual hidden
        items = state.getItems(state.currentScreen, currentSettings)
        state.focusedIndex = items.indexOfFirst { it.id == "input_gyro_calibrate" }
        assertTrue(state.focusedIndex >= 0)
        assertEquals("input_gyro_calibrate", items[state.focusedIndex].id)
        state.onSelectA(items, currentSettings, { currentSettings = it }, { lastAction = it })
        assertEquals(ConfigAction.CALIBRATE_GYRO, lastAction)
    }

    @Test
    fun testHandleKeyEventSwallowsWhenOpen() {
        state.open()
        val items = state.getItems(state.currentScreen, currentSettings)

        val handled = state.handleKeyEvent(
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN,
            null,
            currentSettings,
            items,
            { currentSettings = it },
            { lastAction = it }
        )

        assertTrue(handled)
        assertEquals(1, state.focusedIndex)

        // Release event is swallowed
        assertTrue(
            state.handleKeyEvent(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                null,
                currentSettings,
                items,
                {},
                {}
            )
        )

        // When closed, returns false (not handled, passes through to game)
        state.close()
        assertFalse(
            state.handleKeyEvent(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_DOWN,
                null,
                currentSettings,
                items,
                {},
                {}
            )
        )
    }
}
