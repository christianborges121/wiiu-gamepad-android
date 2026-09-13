package com.cemupad.input

import android.content.SharedPreferences
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileStoreTest {

    private class FakeEditor(
        private val data: MutableMap<String, Any?>,
        private val shadow: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {
        init {
            shadow.putAll(data)
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) shadow.remove(key) else shadow[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            shadow.remove(key)
            return this
        }

        override fun apply() {
            data.clear()
            data.putAll(shadow)
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun clear(): SharedPreferences.Editor {
            shadow.clear()
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
    }

    private class FakePrefs : SharedPreferences {
        val data = mutableMapOf<String, Any?>()
        override fun getAll(): Map<String, Any?> = data.toMap()
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data, mutableMapOf())
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun testSaveLoadRoundTrip() {
        val store = DeviceProfileStore(FakePrefs())
        assertFalse(store.has("pad-1"))
        assertNull(store.load("pad-1"))

        val profile = ControllerProfile.DEFAULT.copy(
            name = "Custom",
            keyA = KeyEvent.KEYCODE_BUTTON_X,
            deviceDescriptor = "pad-1"
        )
        store.save(profile)

        assertTrue(store.has("pad-1"))
        assertEquals(profile, store.load("pad-1"))
    }

    @Test
    fun testResolutionOrder() {
        val store = DeviceProfileStore(FakePrefs())

        // Nothing stored, nothing detected -> default bound to descriptor
        val fallback = store.activeFor("pad-9", null)
        assertEquals(ControllerProfile.DEFAULT.name, fallback.name)
        assertEquals("pad-9", fallback.deviceDescriptor)

        // Detected applies when nothing stored
        val detected = ControllerDetector.NINTENDO_LAYOUT
        val applied = store.activeFor("pad-9", detected)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, applied.keyA)
        assertEquals("pad-9", applied.deviceDescriptor)

        // Stored wins over detected
        store.save(ControllerProfile.DEFAULT.copy(name = "Mine", deviceDescriptor = "pad-9"))
        val winner = store.activeFor("pad-9", detected)
        assertEquals("Mine", winner.name)
    }

    @Test
    fun testClearAndEmptyDescriptor() {
        val store = DeviceProfileStore(FakePrefs())
        store.save(ControllerProfile.DEFAULT.copy(deviceDescriptor = "pad-2"))
        assertTrue(store.has("pad-2"))
        store.clear("pad-2")
        assertFalse(store.has("pad-2"))

        // Empty descriptor never persists
        store.save(ControllerProfile.DEFAULT)
        assertFalse(store.has(""))
        assertNull(store.load(""))
    }

    @Test
    fun testKeysAreStable() {
        assertEquals(DeviceProfileStore.keyFor("abc"), DeviceProfileStore.keyFor("abc"))
    }
}
