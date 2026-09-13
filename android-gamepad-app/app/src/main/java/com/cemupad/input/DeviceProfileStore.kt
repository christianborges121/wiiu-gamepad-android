package com.cemupad.input

import android.content.SharedPreferences
import com.cemupad.config.InputMappingCodec
import com.cemupad.util.Logger

/**
 * Per-device controller profile storage.
 *
 * Resolution order for a descriptor: stored custom profile → detected profile
 * → [ControllerProfile.DEFAULT]. Keys are stable per descriptor so each
 * physical pad keeps its mapping across restarts.
 */
class DeviceProfileStore(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val TAG = "DeviceProfileStore"
        private const val KEY_PREFIX = "input_profile_"

        fun keyFor(descriptor: String): String {
            return KEY_PREFIX + descriptor.hashCode().toString(16)
        }
    }

    fun has(descriptor: String): Boolean {
        if (descriptor.isEmpty()) return false
        return prefs.contains(keyFor(descriptor) + "." + InputMappingCodec.KEY_NAME)
    }

    fun load(descriptor: String): ControllerProfile? {
        if (descriptor.isEmpty()) return null
        if (!has(descriptor)) return null
        val prefix = keyFor(descriptor) + "."
        val values = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(prefix) && value is String) {
                values[key.removePrefix(prefix)] = value
            }
        }
        if (values.isEmpty()) return null
        return InputMappingCodec.decode(values)
    }

    fun save(profile: ControllerProfile) {
        val descriptor = profile.deviceDescriptor
        if (descriptor.isEmpty()) {
            Logger.w(TAG, "Refusing to save profile without device descriptor")
            return
        }
        val prefix = keyFor(descriptor) + "."
        val editor = prefs.edit()
        for ((field, value) in InputMappingCodec.encode(profile)) {
            editor.putString(prefix + field, value)
        }
        editor.apply()
        Logger.i(TAG, "Saved input profile '${profile.name}' for $descriptor")
    }

    fun clear(descriptor: String) {
        if (descriptor.isEmpty()) return
        val prefix = keyFor(descriptor) + "."
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(prefix)) editor.remove(key)
        }
        editor.apply()
    }

    /**
     * Resolves the profile to use: stored → detected → default. The returned
     * profile always carries [descriptor] so a later save binds correctly.
     */
    fun activeFor(descriptor: String, detected: ControllerProfile?): ControllerProfile {
        load(descriptor)?.let { return it }
        if (detected != null) {
            return detected.copy(deviceDescriptor = descriptor)
        }
        return ControllerProfile.DEFAULT.copy(deviceDescriptor = descriptor)
    }
}
